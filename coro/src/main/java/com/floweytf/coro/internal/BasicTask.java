package com.floweytf.coro.internal;

import com.floweytf.coro.support.Result;
import com.floweytf.coro.concepts.CoroutineExecutor;
import com.floweytf.coro.concepts.Task;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;

@SuppressWarnings({"unchecked", "rawtypes"})
@ApiStatus.Internal
public abstract class BasicTask<T> implements Task<T> {
    private static final class Entry<T> {
        public Entry<T> next;
        public Consumer<Result<T>> handler;

        private Entry(Consumer<Result<T>> handler) {
            this.handler = handler;
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

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        try {
            COMPLETE_STACK = lookup.findVarHandle(BasicTask.class, "completeStack", Entry.class);
            MY_EXECUTOR = lookup.findVarHandle(BasicTask.class, "myExecutor", CoroutineExecutor.class);
            RESULT = lookup.findVarHandle(BasicTask.class, "result", Result.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void begin(CoroutineExecutor executor) {
        // Need non-weak CAS here, since this absolutely cannot fail (no loop)
        if (!MY_EXECUTOR.compareAndSet(this, executor, null)) {
            // already started, don't need to worry about it anymore
            return;
        }

        executor.executeTask(this::begin0);
    }

    @Override
    public void onComplete(Consumer<Result<T>> resume) {
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
            if (COMPLETE_STACK.weakCompareAndSet(head, newNode, head)) {
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void complete(Result<T> result) {
        var oldResult = RESULT.getAndSet(this, result);

        if (oldResult == null) {
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

    protected abstract void begin0();
}
