package dev.prathamesh.load;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

public final class LoadRunner {

    private LoadRunner() {}

    /**
     * Starts one virtual thread per task, releases them all at once, and measures
     * per-task latency plus total wall time.
     *
     * @param maxInFlight how many tasks may run at the same time. Pass {@code tasks} for
     *                    a pure burst (latency then includes queueing). Pass a smaller
     *                    number to get realistic per-request latency.
     * @param op          returns an outcome label such as "SUCCESS" for each task index
     */
    public static Report run(String scenario, int tasks, int maxInFlight,
                             long timeoutSeconds, IntFunction<String> op) throws Exception {

        long[] latency = new long[tasks];
        String[] outcome = new String[tasks];
        CountDownLatch ready = new CountDownLatch(tasks);
        CountDownLatch go = new CountDownLatch(1);
        Semaphore permits = new Semaphore(Math.max(1, Math.min(maxInFlight, tasks)));
        List<Future<?>> futures = new ArrayList<>(tasks);

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < tasks; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    permits.acquire();
                } catch (InterruptedException e) {
                    outcome[idx] = "EXCEPTION:Interrupted";
                    return;
                }
                long t0 = System.nanoTime();
                try {
                    outcome[idx] = op.apply(idx);
                } catch (Throwable t) {
                    outcome[idx] = "EXCEPTION:" + t.getClass().getSimpleName() + ": " + shorten(t.getMessage());
                } finally {
                    latency[idx] = System.nanoTime() - t0;
                    permits.release();
                }
            }));
        }

        ready.await();                       // every thread exists and is parked at the gate
        long start = System.nanoTime();
        go.countDown();                      // fire

        long deadline = start + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        try {
            for (Future<?> f : futures) {
                f.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
        } catch (TimeoutException e) {
            pool.shutdownNow();
            throw new AssertionError(scenario + ": not finished within " + timeoutSeconds
                    + "s (deadlock or exhausted pool?)");
        }
        long wall = System.nanoTime() - start;
        pool.shutdown();

        return new Report(scenario, tasks, wall, latency, outcome);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replace('\n', ' ');
        return s.length() > 60 ? s.substring(0, 60) : s;
    }
}