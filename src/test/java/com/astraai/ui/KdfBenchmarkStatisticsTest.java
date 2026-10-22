package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class KdfBenchmarkStatisticsTest {
    @Test
    void calculatesMedianAndNearestRankP90FromUnorderedSamples() {
        KdfBenchmarkStatistics statistics =
                KdfBenchmarkStatistics.fromSamples(List.of(50L, 10L, 40L, 20L, 30L));

        assertEquals(5, statistics.sampleCount());
        assertEquals(30L, statistics.medianNanoseconds());
        assertEquals(50L, statistics.p90Nanoseconds());
    }

    @Test
    void calculatesEvenMedianWithoutAddingLargeValues() {
        KdfBenchmarkStatistics statistics = KdfBenchmarkStatistics.fromSamples(
                List.of(Long.MAX_VALUE - 1, Long.MAX_VALUE));

        assertEquals(Long.MAX_VALUE - 1, statistics.medianNanoseconds());
        assertEquals(Long.MAX_VALUE, statistics.p90Nanoseconds());
    }

    @Test
    void rejectsMissingOrInvalidSamplesAndSettings() {
        assertThrows(NullPointerException.class, () -> KdfBenchmarkStatistics.fromSamples(null));
        assertThrows(IllegalArgumentException.class, () -> KdfBenchmarkStatistics.fromSamples(List.of()));
        assertThrows(IllegalArgumentException.class, () -> KdfBenchmarkStatistics.fromSamples(List.of(0L)));
        assertThrows(IllegalArgumentException.class, () -> KdfBenchmarkStatistics.fromSamples(List.of(-1L)));
        assertThrows(IllegalArgumentException.class, () -> KdfBenchmarkStatistics.fromSamples(
                java.util.Arrays.asList(1L, null)));
        assertThrows(IllegalArgumentException.class, () -> new KdfVerificationBenchmark.Settings(-1, 3, 1000));
        assertThrows(IllegalArgumentException.class, () -> new KdfVerificationBenchmark.Settings(0, 3, 1000));
        assertThrows(IllegalArgumentException.class, () -> new KdfVerificationBenchmark.Settings(0, 2, 1000));
        assertThrows(IllegalArgumentException.class, () -> new KdfVerificationBenchmark.Settings(0, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new KdfVerificationBenchmark.Settings(6, 32, 1000));
    }
}
