package dev.prathamesh.load;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class Report {

    private final String scenario;
    private final int tasks;
    private final long wallNanos;
    private final long[] sortedLatency;
    private final String[] outcomes;
    private final Map<String, Long> distribution = new TreeMap<>();
    private final Map<String, Boolean> invariants = new LinkedHashMap<>();

    Report(String scenario, int tasks, long wallNanos, long[] latencyNanos, String[] outcomes) {
        this.scenario = scenario;
        this.tasks = tasks;
        this.wallNanos = wallNanos;
        this.outcomes = outcomes;
        this.sortedLatency = latencyNanos.clone();
        Arrays.sort(this.sortedLatency);
        for (String o : outcomes) {
            distribution.merge(String.valueOf(o), 1L, Long::sum);
        }
    }

    // ---------- metrics ----------

    public double wallSeconds() { return wallNanos / 1e9; }

    public double throughput() { return tasks / wallSeconds(); }

    public double avgMs() { return Arrays.stream(sortedLatency).average().orElse(0) / 1e6; }

    public double maxMs() { return sortedLatency[sortedLatency.length - 1] / 1e6; }

    public double pctMs(double p) {
        int idx = (int) Math.ceil(p / 100.0 * sortedLatency.length) - 1;
        idx = Math.max(0, Math.min(idx, sortedLatency.length - 1));
        return sortedLatency[idx] / 1e6;
    }

    // ---------- outcomes ----------

    public String outcomeAt(int i) { return outcomes[i]; }

    /** Number of tasks whose outcome label starts with the given prefix. */
    public long count(String prefix) {
        return distribution.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    // ---------- invariants ----------

    public Report check(String name, boolean ok) {
        invariants.put(name, ok);
        return this;
    }

    public void assertPassed() {
        List<String> failed = invariants.entrySet().stream()
                .filter(e -> !e.getValue()).map(Map.Entry::getKey).toList();
        if (!failed.isEmpty()) {
            throw new AssertionError(scenario + " violated: " + failed);
        }
    }

    // ---------- output ----------

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%n=== %s ===%n", scenario));
        sb.append(String.format(Locale.ROOT, "threads=%d  wall=%.2fs  throughput=%.0f req/s%n",
                tasks, wallSeconds(), throughput()));
        sb.append(String.format(Locale.ROOT,
                "latency ms: avg=%.1f p50=%.1f p90=%.1f p95=%.1f p99=%.1f max=%.1f%n",
                avgMs(), pctMs(50), pctMs(90), pctMs(95), pctMs(99), maxMs()));
        distribution.forEach((k, v) -> sb.append("  outcome ").append(k).append(" = ").append(v).append('\n'));
        invariants.forEach((k, v) -> sb.append(v ? "  [PASS] " : "  [FAIL] ").append(k).append('\n'));
        return sb.toString();
    }

    public static String matrix(List<Report> reports, String environment) {
        StringBuilder sb = new StringBuilder();
        sb.append("Environment: ").append(environment).append("\n\n");
        sb.append("| Scenario | Threads | Wall (s) | Req/s | Avg ms | p50 | p90 | p95 | p99 | Max ms | Exceptions | Invariants |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Report r : reports) {
            long passed = r.invariants.values().stream().filter(b -> b).count();
            sb.append(String.format(Locale.ROOT,
                    "| %s | %d | %.2f | %.0f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %d | %d/%d |%n",
                    r.scenario, r.tasks, r.wallSeconds(), r.throughput(), r.avgMs(),
                    r.pctMs(50), r.pctMs(90), r.pctMs(95), r.pctMs(99), r.maxMs(),
                    r.count("EXCEPTION"), passed, r.invariants.size()));
        }
        return sb.toString();
    }

    public static String csv(List<Report> reports) {
        StringBuilder sb = new StringBuilder("scenario,threads,wall_s,req_per_s,avg_ms,p50_ms,p90_ms,p95_ms,p99_ms,max_ms,exceptions\n");
        for (Report r : reports) {
            sb.append(String.format(Locale.ROOT, "\"%s\",%d,%.3f,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d%n",
                    r.scenario, r.tasks, r.wallSeconds(), r.throughput(), r.avgMs(),
                    r.pctMs(50), r.pctMs(90), r.pctMs(95), r.pctMs(99), r.maxMs(), r.count("EXCEPTION")));
        }
        return sb.toString();
    }
}