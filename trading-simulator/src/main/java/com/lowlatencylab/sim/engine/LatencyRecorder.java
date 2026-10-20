package com.lowlatencylab.sim.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class LatencyRecorder {
    public enum Stage {
        ORDER_SUBMISSION("order submission"),
        RISK_VALIDATION("risk validation"),
        BOOK_LOOKUP("book lookup"),
        MATCHING("matching"),
        POSITION_UPDATE("position update");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class Summary {
        private final int samples;
        private final long minimumNanos;
        private final double averageNanos;
        private final long medianNanos;
        private final long p95Nanos;
        private final long p99Nanos;
        private final long maximumNanos;

        private Summary(List<Long> measurements) {
            List<Long> sorted = new ArrayList<>(measurements);
            Collections.sort(sorted);
            this.samples = sorted.size();
            this.minimumNanos = sorted.get(0);
            this.averageNanos = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
            this.medianNanos = percentile(sorted, 0.50);
            this.p95Nanos = percentile(sorted, 0.95);
            this.p99Nanos = percentile(sorted, 0.99);
            this.maximumNanos = sorted.get(sorted.size() - 1);
        }

        public int samples() {
            return samples;
        }

        public long minimumNanos() {
            return minimumNanos;
        }

        public double averageNanos() {
            return averageNanos;
        }

        public long medianNanos() {
            return medianNanos;
        }

        public long p95Nanos() {
            return p95Nanos;
        }

        public long p99Nanos() {
            return p99Nanos;
        }

        public long maximumNanos() {
            return maximumNanos;
        }

        @Override
        public String toString() {
            return "samples=" + samples +
                ", min=" + minimumNanos + " ns" +
                ", avg=" + String.format("%.1f", averageNanos) + " ns" +
                ", median=" + medianNanos + " ns" +
                ", p95=" + p95Nanos + " ns" +
                ", p99=" + p99Nanos + " ns" +
                ", max=" + maximumNanos + " ns";
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }

    private final boolean enabled;
    private final Map<Stage, List<Long>> samples = new EnumMap<>(Stage.class);

    private LatencyRecorder(boolean enabled) {
        this.enabled = enabled;
        for (Stage stage : Stage.values()) {
            samples.put(stage, new ArrayList<>());
        }
    }

    public static LatencyRecorder enabled() {
        return new LatencyRecorder(true);
    }

    public static LatencyRecorder disabled() {
        return new LatencyRecorder(false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void record(Stage stage, long elapsedNanos) {
        if (enabled) {
            samples.get(stage).add(elapsedNanos);
        }
    }

    public Map<Stage, Summary> summaries() {
        Map<Stage, Summary> summaries = new EnumMap<>(Stage.class);
        for (Map.Entry<Stage, List<Long>> entry : samples.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                summaries.put(entry.getKey(), new Summary(entry.getValue()));
            }
        }
        return summaries;
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder("LATENCY (nanoseconds)\n");
        for (Map.Entry<Stage, Summary> entry : summaries().entrySet()) {
            output.append(entry.getKey().label())
                .append(": ")
                .append(entry.getValue())
                .append('\n');
        }
        return output.toString();
    }
}
