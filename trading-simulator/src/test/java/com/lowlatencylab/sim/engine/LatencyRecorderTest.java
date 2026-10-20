package com.lowlatencylab.sim.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyRecorderTest {
    @Test
    void reportsLatencyDistributionStatistics() {
        LatencyRecorder recorder = LatencyRecorder.enabled();
        for (long value = 1; value <= 100; value++) {
            recorder.record(LatencyRecorder.Stage.MATCHING, value);
        }

        LatencyRecorder.Summary summary = recorder.summaries().get(LatencyRecorder.Stage.MATCHING);

        assertEquals(100, summary.samples());
        assertEquals(1, summary.minimumNanos());
        assertEquals(50.5, summary.averageNanos());
        assertEquals(50, summary.medianNanos());
        assertEquals(95, summary.p95Nanos());
        assertEquals(99, summary.p99Nanos());
        assertEquals(100, summary.maximumNanos());
    }
}
