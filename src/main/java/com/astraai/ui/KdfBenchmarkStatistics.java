package com.astraai.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure aggregation for successful local KDF verification latency samples. */
public record KdfBenchmarkStatistics(int sampleCount, long medianNanoseconds, long p90Nanoseconds) {
    public KdfBenchmarkStatistics {
        if (sampleCount <= 0 || medianNanoseconds <= 0 || p90Nanoseconds <= 0) {
            throw new IllegalArgumentException("sample count and latency statistics must be positive");
        }
    }

    public static KdfBenchmarkStatistics fromSamples(List<Long> samplesNanoseconds) {
        Objects.requireNonNull(samplesNanoseconds, "samplesNanoseconds");
        if (samplesNanoseconds.isEmpty()) {
            throw new IllegalArgumentException("at least one successful sample is required");
        }
        List<Long> sorted = new ArrayList<>(samplesNanoseconds.size());
        for (Long sample : samplesNanoseconds) {
            if (sample == null || sample <= 0) {
                throw new IllegalArgumentException("sample latencies must be positive");
            }
            sorted.add(sample);
        }
        sorted.sort(Long::compareTo);
        int size = sorted.size();
        long median;
        if ((size & 1) == 1) {
            median = sorted.get(size / 2);
        } else {
            long lower = sorted.get(size / 2 - 1);
            long upper = sorted.get(size / 2);
            median = lower + (upper - lower) / 2;
        }
        int p90Index = (int) Math.ceil(size * 0.90d) - 1;
        return new KdfBenchmarkStatistics(size, median, sorted.get(p90Index));
    }
}
