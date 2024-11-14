package com.floweytf.coro.internal;

import com.floweytf.coro.concepts.Generator;
import com.floweytf.coro.support.Result;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Stack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class BasicGenerator<T> implements Generator<T> {
    private static class GeneratorIterator<T> implements Iterator<T> {
        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
            throw (E) e;
        }

        private final Stack<BasicGenerator<T>> generators = new Stack<>();
        private Result<T> result;
        private boolean hasPushedGenerator = false;

        private GeneratorIterator(BasicGenerator<T> generator) {
            push(generator);
            generate();
        }

        private void push(BasicGenerator<T> generator) {
            generators.push(generator);
            generator.iterator = this;
            hasPushedGenerator = true;
        }

        private void generate() {
            result = null;
            hasPushedGenerator = false;
            Throwable currentEx = null;

            while (!generators.isEmpty()) {
                final var gen = generators.peek();
                gen.run(gen.newState, currentEx);

                // if we yielded a sub-generator
                if(hasPushedGenerator) {
                    hasPushedGenerator = false;
                    continue;
                }

                // this implies that the latest generator has returned with Co.ret()
                // continue...
                if (result == null) {
                    generators.pop();
                    continue;
                }

                // if it has a value, we can return, since we have successfully generated something
                if (result.hasValue()) {
                    return;
                }

                // handle exceptions
                if (result.hasError()) {
                    generators.pop();
                    currentEx = result.error().orElseThrow();
                }
            }
        }

        @Override
        public boolean hasNext() {
            return result != null;
        }

        @Override
        public T next() {
            if (result == null) {
                throw new NoSuchElementException();
            }

            final var res = result.mapBoth(
                x -> x,
                e -> {
                    sneakyThrow(e);
                    return null;
                }
            );

            generate();

            return res;
        }
    }

    private GeneratorIterator<T> iterator;
    private int newState;

    protected static <T> void yieldGenerator(Generator<T> generator, BasicGenerator<T> self, int newState) {
        self.iterator.push((BasicGenerator<T>) generator);
        self.newState = newState;
    }

    protected static <T> void yieldValue(T value, BasicGenerator<T> self, int newState) {
        self.iterator.result = Result.value(value);
        self.newState = newState;
    }

    protected static <T> void completeError(Throwable value, BasicGenerator<T> self) {
        self.iterator.result = Result.error(value);
    }

    protected abstract void run(int state, Throwable ex);

    @Override
    public @NotNull java.util.Iterator<T> iterator() {
        if(iterator == null) {
            iterator = new GeneratorIterator<>(this);
        }

        return iterator;
    }
}