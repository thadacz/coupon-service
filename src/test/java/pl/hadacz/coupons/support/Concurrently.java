package pl.hadacz.coupons.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/// Runs tasks on virtual threads and releases them together through one start gate,
/// so they hit the code under test at the same moment instead of one after another.
public final class Concurrently {

    private Concurrently() {
    }

    public static <T> List<T> run(int tasks, IntFunction<T> task) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>(tasks);
            for (int i = 0; i < tasks; i++) {
                int taskIndex = i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return task.apply(taskIndex);
                }));
            }
            startGate.countDown();

            List<T> results = new ArrayList<>(tasks);
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
