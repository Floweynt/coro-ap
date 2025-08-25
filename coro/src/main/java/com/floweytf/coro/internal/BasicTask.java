package com.floweytf.coro.internal;

import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Continuation;
import com.floweytf.coro.concepts.CoroutineExecutor;
import com.floweytf.coro.concepts.Task;
import com.floweytf.coro.support.Result;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;

@SuppressWarnings({"unchecked", "rawtypes", "unused", "FieldMayBeFinal"})
@ApiStatus.Internal
public abstract class BasicTask<T> implements Task<T> {
    private static final class Entry<T> {
        public Entry<T> next;
        public final Consumer<Result<T>> handler;

        private Entry(final Consumer<Result<T>> handler) {
            this.handler = handler;
        }
    }

    private static final class ResumeContinuation<T> implements Continuation.Coroutine<T> {
        private final Awaitable<T> awaitable;
        private final BasicTask<T> self;
        private final int resumeState;

        private boolean flag;

        private ResumeContinuation(final Awaitable<T> awaitable, final BasicTask<T> self, final int resumeState) {
            this.awaitable = awaitable;
            this.self = self;
            this.resumeState = resumeState;
        }

        private void resumeCommon() {
            if ((boolean) RESUME_CONTINUATION_FLAG.getAndSet(this, true)) {
                throw new IllegalStateException("Coroutine may not be resumed twice on the same suspension point!");
            }

            self.suspendPointId = -1;
            self.currentAwaitable = null;
        }

        @Override
        public void submitError(final Throwable error) {
            resumeCommon();
            self.getExecutor().onResumeExceptionally(self, awaitable, error);
            self.myExecutor.executeTask(() -> self.run(resumeState, true, error));
        }

        @Override
        public void submit(final T value) {
            resumeCommon();
            self.getExecutor().onResume(self, awaitable, value);
            self.myExecutor.executeTask(() -> self.run(resumeState, false, value));
        }

        @Override
        public Task<T> theTask() {
            return self;
        }

        @Override
        public StackTraceElement calleeLocation() {
            final var declaringClass = self.getMetadata().declaringClass();
            final var moduleDesc = declaringClass.getModule().getDescriptor();

            return new StackTraceElement(
                declaringClass.getClassLoader().getName(),
                declaringClass.getModule().getName(),
                moduleDesc != null ? moduleDesc.version().map(ModuleDescriptor.Version::toString).orElse(null) : null,
                declaringClass.getName(),
                self.getMetadata().methodName(),
                self.getMetadata().fileName(),
                self.getMetadata().suspendPointLineNo()[resumeState - 1]
            );
        }
    }

    private static final Entry NIL = new Entry<>(ignored -> {
    });

    private volatile Entry<T> completeStack = NIL;
    private volatile CoroutineExecutor myExecutor;
    private volatile Result<T> result;

    private static final VarHandle COMPLETE_STACK;
    private static final VarHandle MY_EXECUTOR;
    private static final VarHandle RESULT;
    private static final VarHandle RESUME_CONTINUATION_FLAG;

    private int suspendPointId = -1;
    private Awaitable<?> currentAwaitable;

    // info about the current suspend points

    static {
        final var lookup = MethodHandles.lookup();

        try {
            COMPLETE_STACK = lookup.findVarHandle(BasicTask.class, "completeStack", Entry.class);
            MY_EXECUTOR = lookup.findVarHandle(BasicTask.class, "myExecutor", CoroutineExecutor.class);
            RESULT = lookup.findVarHandle(BasicTask.class, "result", Result.class);
            RESUME_CONTINUATION_FLAG = lookup.findVarHandle(ResumeContinuation.class, "flag", boolean.class);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Task<T> begin(final CoroutineExecutor executor) {
        // Need non-weak CAS here, since this absolutely cannot fail (no loop)
        // if it's nonnull, that means begin() was already called and there's no need to start it again
        if (MY_EXECUTOR.compareAndSet(this, null, executor)) {
            executor.executeTask(() -> run(0, false, null));
        }

        return this;
    }

    @Override
    public void onComplete(final Consumer<Result<T>> resume) {
        final var newNode = new Entry<T>(x -> myExecutor.executeTask(() -> resume.accept(x)));

        while (true) {
            // Read the head pointer from the completion list
            final var head = COMPLETE_STACK.getAcquire(this);

            // If it is null, this task has already been completed, and we need to invoke the continuation immediately.
            if (head == null) {
                // We don't need to busy loop here, since we used getAcquire for the head variable.
                resume.accept((Result<T>) RESULT.get(this));
                return;
            }

            // We should modify the next pointer of the new head so it points to the old head (standard linked list
            // insert). We modify here instead of creating a new instance to reduce allocation.
            newNode.next = (Entry<T>) head;

            // This must be a compare and set to ensure that the head variable has not changed.
            // This is to eliminate any possible data race:
            // - onComplete can race with itself, which means the head we previously obtained is no longer up-to-date.
            // - onComplete can race with complete, which means it's possible that we should be directly invoking the
            //   callback here
            // By doing compare and set, we can be sure that the head pointer has not changed at all between the get
            // operation and now. This doesn't suffer from the ABA problem, since every new entry is unique.
            if (COMPLETE_STACK.weakCompareAndSet(this, head, newNode)) {
                return;
            }
        }
    }

    protected void complete(final Result<T> result) {
        final var oldResult = RESULT.getAndSet(this, result);

        if (oldResult != null) {
            return;
        }

        // Exchange the completion stack to null to notify the completion of this Task.
        var head = (Entry<T>) COMPLETE_STACK.getAndSetRelease(this, null); // atomic exchange with null

        // head shouldn't be null here
        // Execute all pending completion task.
        while (head != NIL) {
            head.handler.accept(result);
            head = head.next;
        }
    }

    @Override
    public void execute(final CoroutineExecutor executor, final Continuation<T> resume) {
        begin(executor);
        onComplete(tResult -> tResult.match(resume::submit, resume::submitError));
    }

    protected static <T, U> void suspendHelper(final Awaitable<T> awaitable, final BasicTask<U> self,
                                               final int resumeState) {
        self.suspendPointId = resumeState;
        self.currentAwaitable = awaitable;

        self.getExecutor().onSuspend(self, awaitable);
        awaitable.execute(self.getExecutor(), new ResumeContinuation(awaitable, self, resumeState));
    }

    protected static <T> void completeSuccess(final T val, final BasicTask<T> self) {
        self.complete(Result.value(val));
    }

    protected static <T> void completeError(final Throwable val, final BasicTask<T> self) {
        self.complete(Result.error(val));
    }

    protected CoroutineExecutor getExecutor() {
        return (CoroutineExecutor) MY_EXECUTOR.get(this);
    }

    protected abstract CoroutineMetadata getMetadata();

    protected abstract void run(int state, boolean isExceptional, Object resVal);

    @Override
    public String toString() {
        final var meta = getMetadata();
        return "Task[%s %s.%s(%s)]".formatted(
            Modifier.toString(meta.access()),
            getMetadata().declaringClass().getSimpleName(),
            getMetadata().methodName(),
            Arrays.stream(getMetadata().argTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "))
        );
    }
}
