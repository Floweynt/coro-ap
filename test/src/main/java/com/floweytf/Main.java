
package com.floweytf;

import com.floweytf.coro.Co;
import static com.floweytf.coro.Co.await;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Task;
import java.util.function.Consumer;

public class Main {
    @Coroutine
    public synchronized static Task<Void> coroTest() {

        Co.await(null);

        new Consumer<>() {

            @Override
            public void accept(Object o) {
                Co.await(null);
            }
        };

        Consumer<String> z = s -> System.out.println(s);

        await(null);
        Co.ret();

        return Co.ret();
    }

    @Coroutine
    public synchronized static void coroTest2() {
    }

    public static void main(String[] args) {
        System.out.println("Hello world!" + coroTest());
    }
}