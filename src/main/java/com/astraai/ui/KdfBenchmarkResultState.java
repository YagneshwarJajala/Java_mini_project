package com.astraai.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/** Benchmark evidence state kept separate from the status-only crack-time estimate. */
public final class KdfBenchmarkResultState {
    public enum Status {
        BENCHMARK_UNAVAILABLE("Benchmark unavailable"),
        BENCHMARK_REQUIRED("Benchmark required"),
        LOCAL_VERIFICATION_COST_OBSERVATION("Local verification-cost observation"),
        NOT_ESTIMABLE("Not estimable");

        private final String displayText;

        Status(String displayText) {
            this.displayText = displayText;
        }

        public String displayText() {
            return displayText;
        }
    }

    private final Status status;
    private final String reason;
    private final Optional<BigDecimal> medianVerificationMilliseconds;

    private KdfBenchmarkResultState(Status status, String reason,
            Optional<BigDecimal> medianVerificationMilliseconds) {
        this.status = Objects.requireNonNull(status, "status");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.medianVerificationMilliseconds = Objects.requireNonNull(
                medianVerificationMilliseconds, "medianVerificationMilliseconds");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if ((status == Status.LOCAL_VERIFICATION_COST_OBSERVATION)
                != medianVerificationMilliseconds.isPresent()) {
            throw new IllegalArgumentException(
                    "only a local verification-cost observation may carry a measured latency");
        }
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public Optional<BigDecimal> medianVerificationMilliseconds() {
        return medianVerificationMilliseconds;
    }

    public String displayText() {
        if (status != Status.LOCAL_VERIFICATION_COST_OBSERVATION) {
            return status.displayText();
        }
        String milliseconds = medianVerificationMilliseconds.orElseThrow()
                .stripTrailingZeros().toPlainString();
        return "Median local KDF verification: " + milliseconds + " ms (not crack time)";
    }

    static KdfBenchmarkResultState evaluate(AlgorithmInfo algorithm,
            PasswordKdfCostDataset.Configuration configuration,
            Optional<KdfBenchmarkReport> report,
            KdfVerificationBenchmark.RuntimeMetadata currentRuntime) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(report, "report");
        if ("SHA-3-based KDFs".equals(algorithm.name())) {
            return state(Status.NOT_ESTIMABLE,
                    "No concrete password-KDF construction is defined; plain SHA-3 is unsupported.");
        }
        if (configuration == null) {
            return state(Status.BENCHMARK_REQUIRED,
                    "Select a complete supported configuration and provide a matching completed local report.");
        }
        if (!configurationMatchesAlgorithm(algorithm, configuration)) {
            return state(Status.BENCHMARK_UNAVAILABLE,
                    "The selected configuration does not match a supported implementation for this algorithm.");
        }

        String actualProviderVersion;
        try {
            PasswordKdfCostDataset.validate(configuration);
            actualProviderVersion = KdfVerificationBenchmark.validateSupportedConfiguration(configuration);
        } catch (IllegalArgumentException exception) {
            return state(Status.BENCHMARK_UNAVAILABLE,
                    "The selected configuration or provider is unsupported by the local benchmark runner.");
        }
        if (report.isEmpty() || currentRuntime == null) {
            return state(Status.BENCHMARK_REQUIRED,
                    "The configuration is supported, but no matching completed report with current-machine provenance was supplied.");
        }

        KdfBenchmarkReport observation = report.orElseThrow();
        if (!observation.matches(configuration, actualProviderVersion, currentRuntime)) {
            return state(Status.BENCHMARK_REQUIRED,
                    "No completed report matches this algorithm, exact configuration, provider version, and current runtime.");
        }
        BigDecimal medianMilliseconds = BigDecimal.valueOf(observation.medianVerificationLatencyNanoseconds())
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        return new KdfBenchmarkResultState(Status.LOCAL_VERIFICATION_COST_OBSERVATION,
                "Completed local verification-cost observation for the exact selected configuration. "
                        + "It is not a crack-time estimate or attack rate.",
                Optional.of(medianMilliseconds));
    }

    private static boolean configurationMatchesAlgorithm(
            AlgorithmInfo algorithm, PasswordKdfCostDataset.Configuration configuration) {
        return switch (algorithm.name()) {
            case "Argon2id" -> configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration;
            case "bcrypt" -> configuration instanceof PasswordKdfCostDataset.BcryptConfiguration;
            case "scrypt" -> configuration instanceof PasswordKdfCostDataset.ScryptConfiguration;
            case "PBKDF2" -> configuration instanceof PasswordKdfCostDataset.Pbkdf2Configuration;
            default -> false;
        };
    }

    private static KdfBenchmarkResultState state(Status status, String reason) {
        return new KdfBenchmarkResultState(status, reason, Optional.empty());
    }

    static KdfBenchmarkResultState reportUnavailable(String reason) {
        return state(Status.BENCHMARK_UNAVAILABLE, reason);
    }

    static KdfBenchmarkResultState reportRequired(String reason) {
        return state(Status.BENCHMARK_REQUIRED, reason);
    }
}
