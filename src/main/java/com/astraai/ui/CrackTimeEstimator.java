package com.astraai.ui;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reports whether the catalog has enough defensible inputs for an estimate.
 * It intentionally does not calculate password-guessing rates or attack times.
 */
public final class CrackTimeEstimator {
    private static final CrackTimeAssumptionDataset EDUCATIONAL_ASSUMPTIONS =
            CrackTimeAssumptionDataset.educationalDefaults();
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3_600);
    private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(86_400);
    private static final BigDecimal SECONDS_PER_MONTH = BigDecimal.valueOf(2_629_800);
    private static final BigDecimal SECONDS_PER_YEAR = BigDecimal.valueOf(31_557_600);
    private static final ModeledCrackTimeEstimate.ScenarioMetadata SCENARIO =
            new ModeledCrackTimeEstimate.ScenarioMetadata(100,
                    ModeledCrackTimeEstimate.AgentCountMeaning.LOGICAL_AGENTS_ONLY,
                    ModeledCrackTimeEstimate.RateScope.NOT_CONFIGURED);

    private CrackTimeEstimator() {
    }

    public static ModeledCrackTimeEstimate estimate(AlgorithmInfo algorithm) {
        Objects.requireNonNull(algorithm, "algorithm");
        return switch (algorithm.name()) {
            case "Argon2id" -> unavailable(ModeledCrackTimeEstimate.AlgorithmId.ARGON2ID,
                    ModeledCrackTimeEstimate.Status.NOT_ESTIMATED,
                    ModeledCrackTimeEstimate.MissingInput.ALGORITHM_VARIANT,
                    ModeledCrackTimeEstimate.MissingInput.MEMORY_TIME_PARALLELISM,
                    ModeledCrackTimeEstimate.MissingInput.IMPLEMENTATION_HARDWARE_CONTEXT,
                    ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA,
                    ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION);
            case "bcrypt" -> unavailable(ModeledCrackTimeEstimate.AlgorithmId.BCRYPT,
                    ModeledCrackTimeEstimate.Status.NOT_ESTIMATED,
                    ModeledCrackTimeEstimate.MissingInput.BCRYPT_WORK_FACTOR,
                    ModeledCrackTimeEstimate.MissingInput.IMPLEMENTATION_HARDWARE_CONTEXT,
                    ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA,
                    ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION);
            case "scrypt" -> unavailable(ModeledCrackTimeEstimate.AlgorithmId.SCRYPT,
                    ModeledCrackTimeEstimate.Status.NOT_ESTIMATED,
                    ModeledCrackTimeEstimate.MissingInput.SCRYPT_N_R_P,
                    ModeledCrackTimeEstimate.MissingInput.SCRYPT_MEMORY,
                    ModeledCrackTimeEstimate.MissingInput.IMPLEMENTATION_HARDWARE_CONTEXT,
                    ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA,
                    ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION);
            case "PBKDF2" -> unavailable(ModeledCrackTimeEstimate.AlgorithmId.PBKDF2,
                    ModeledCrackTimeEstimate.Status.NOT_ESTIMATED,
                    ModeledCrackTimeEstimate.MissingInput.PBKDF2_PRF,
                    ModeledCrackTimeEstimate.MissingInput.PBKDF2_ITERATIONS,
                    ModeledCrackTimeEstimate.MissingInput.IMPLEMENTATION_HARDWARE_CONTEXT,
                    ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA,
                    ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION);
            case "SHA-3-based KDFs" -> unavailable(ModeledCrackTimeEstimate.AlgorithmId.UNSPECIFIED_SHA3_KDF,
                    ModeledCrackTimeEstimate.Status.NOT_ESTIMABLE,
                    ModeledCrackTimeEstimate.MissingInput.CONCRETE_PASSWORD_KDF,
                    ModeledCrackTimeEstimate.MissingInput.ALGORITHM_CONFIGURATION,
                    ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA,
                    ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION);
            default -> unavailable(ModeledCrackTimeEstimate.AlgorithmId.UNKNOWN,
                    ModeledCrackTimeEstimate.Status.NOT_ESTIMATED,
                    ModeledCrackTimeEstimate.MissingInput.ALGORITHM_CONFIGURATION,
                    ModeledCrackTimeEstimate.MissingInput.IMPLEMENTATION_HARDWARE_CONTEXT,
                    ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA,
                    ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION);
        };
    }

    /** Calculates an educational expected-work estimate from derived counts only; it never receives a password. */
    public static CrackTimeEstimate estimate(AlgorithmInfo algorithm, PasswordAnalysis analysis) {
        return estimate(algorithm, analysis, EDUCATIONAL_ASSUMPTIONS);
    }

    /** Allows callers and tests to supply a clearly labeled alternative assumption dataset. */
    public static CrackTimeEstimate estimate(AlgorithmInfo algorithm, PasswordAnalysis analysis,
            CrackTimeAssumptionDataset assumptions) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(assumptions, "assumptions");
        if (algorithm.name().equals("SHA-3-based KDFs")) {
            return unavailable(CrackTimeEstimate.Status.NOT_ESTIMABLE, "Not estimable",
                    "No concrete password KDF or parameters are specified; plain SHA-3 is not modeled as password storage.");
        }
        if (analysis == null) {
            return unavailable(CrackTimeEstimate.Status.UNAVAILABLE, "Estimate unavailable",
                    "A local password-characteristic result is required; no raw password is retained by this model.");
        }
        if (!analysis.printableAsciiOnly()) {
            return unavailable(CrackTimeEstimate.Status.UNAVAILABLE, "Estimate unavailable",
                    "This educational candidate-space model supports printable ASCII input only.");
        }
        CrackTimeAssumptionDataset.Algorithm id = switch (algorithm.name()) {
            case "Argon2id" -> CrackTimeAssumptionDataset.Algorithm.ARGON2ID;
            case "bcrypt" -> CrackTimeAssumptionDataset.Algorithm.BCRYPT;
            case "scrypt" -> CrackTimeAssumptionDataset.Algorithm.SCRYPT;
            case "PBKDF2" -> CrackTimeAssumptionDataset.Algorithm.PBKDF2;
            default -> null;
        };
        if (id == null) {
            return unavailable(CrackTimeEstimate.Status.UNAVAILABLE, "Estimate unavailable",
                    "No educational rate profile is configured for this algorithm.");
        }
        CrackTimeAssumptionDataset.Profile profile = assumptions.profile(id);
        if (profile == null) {
            return unavailable(CrackTimeEstimate.Status.UNAVAILABLE, "Estimate unavailable",
                    "No algorithm-specific offline rate assumption is configured.");
        }
        int alphabetSize = (analysis.lowercaseCount() > 0 ? 26 : 0)
                + (analysis.uppercaseCount() > 0 ? 26 : 0)
                + (analysis.digitCount() > 0 ? 10 : 0)
                + (analysis.symbolCount() > 0 ? 33 : 0);
        if (alphabetSize == 0 || analysis.length() <= 0) {
            return unavailable(CrackTimeEstimate.Status.UNAVAILABLE, "Estimate unavailable",
                    "The heuristic did not provide a supported non-empty character-class profile.");
        }
        BigDecimal candidateSpace;
        if (analysis.predictablePatterns().contains("The same character is repeated throughout the input.")) {
            candidateSpace = BigDecimal.valueOf(alphabetSize);
        } else if (analysis.predictablePatterns().contains("The input follows a simple ascending character sequence.")
                || analysis.predictablePatterns().contains("The input follows a simple descending character sequence.")) {
            candidateSpace = BigDecimal.valueOf(alphabetSize).multiply(TWO);
        } else {
            candidateSpace = BigDecimal.valueOf(alphabetSize).pow(analysis.length());
        }
        BigDecimal expectedGuesses = candidateSpace.add(BigDecimal.ONE).divide(TWO, 0, RoundingMode.CEILING);
        BigDecimal seconds = expectedGuesses.divide(profile.guessesPerSecond(), 18, RoundingMode.HALF_UP);
        String detail = "Educational model " + CrackTimeAssumptionDataset.VERSION + " (" + CrackTimeAssumptionDataset.AS_OF
                + "): " + profile.variantAndParameters() + ", assumed offline rate "
                + profile.guessesPerSecond().stripTrailingZeros().toPlainString() + " guesses/s. "
                + "Candidate space uses observed character classes (26 lowercase, 26 uppercase, 10 digits, 33 other "
                + "printable ASCII); repeated-character and simple-sequence flags use their explicit small pattern "
                + "families. Expected work is half the modeled space, rounded up. This is not true entropy, actual "
                + "attacker ordering, or a prediction; rates are illustrative, not measured. "
                + CrackTimeAssumptionDataset.PROVENANCE;
        return new CrackTimeEstimate(CrackTimeEstimate.Status.ESTIMATED, formatDuration(seconds), detail,
                java.util.Optional.of(candidateSpace), java.util.Optional.of(expectedGuesses),
                java.util.Optional.of(profile.guessesPerSecond()));
    }

    public static String formatDuration(BigDecimal seconds) {
        Objects.requireNonNull(seconds, "seconds");
        if (seconds.signum() < 0) {
            throw new IllegalArgumentException("seconds must not be negative");
        }
        if (seconds.compareTo(SECONDS_PER_YEAR.multiply(BigDecimal.valueOf(1_000))) > 0) {
            return "Over 1,000 years";
        }
        if (seconds.compareTo(BigDecimal.ONE) < 0) return "Less than a second";
        if (seconds.compareTo(SECONDS_PER_MINUTE) < 0) {
            return roundedCount(seconds, BigDecimal.ONE) >= 60
                    ? about(seconds, SECONDS_PER_MINUTE, "minute") : about(seconds, BigDecimal.ONE, "second");
        }
        if (seconds.compareTo(SECONDS_PER_HOUR) < 0) {
            return roundedCount(seconds, SECONDS_PER_MINUTE) >= 60
                    ? about(seconds, SECONDS_PER_HOUR, "hour") : about(seconds, SECONDS_PER_MINUTE, "minute");
        }
        if (seconds.compareTo(SECONDS_PER_DAY) < 0) {
            return roundedCount(seconds, SECONDS_PER_HOUR) >= 24
                    ? about(seconds, SECONDS_PER_DAY, "day") : about(seconds, SECONDS_PER_HOUR, "hour");
        }
        if (seconds.compareTo(SECONDS_PER_MONTH) < 0) {
            return roundedCount(seconds, SECONDS_PER_DAY) >= 30
                    ? about(seconds, SECONDS_PER_MONTH, "month") : about(seconds, SECONDS_PER_DAY, "day");
        }
        if (seconds.compareTo(SECONDS_PER_YEAR) < 0) {
            return roundedCount(seconds, SECONDS_PER_MONTH) >= 12
                    ? about(seconds, SECONDS_PER_YEAR, "year") : about(seconds, SECONDS_PER_MONTH, "month");
        }
        return about(seconds, SECONDS_PER_YEAR, "year");
    }

    private static String about(BigDecimal seconds, BigDecimal unitSeconds, String unit) {
        long count = Math.max(1, roundedCount(seconds, unitSeconds));
        return "About " + count + " " + unit + (count == 1 ? "" : "s");
    }

    private static long roundedCount(BigDecimal seconds, BigDecimal unitSeconds) {
        return seconds.divide(unitSeconds, 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static CrackTimeEstimate unavailable(CrackTimeEstimate.Status status, String display, String reason) {
        return new CrackTimeEstimate(status, display, reason, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty());
    }

    public static KdfBenchmarkResultState benchmarkState(AlgorithmInfo algorithm,
            PasswordKdfCostDataset.Configuration configuration,
            Optional<KdfBenchmarkReport> report,
            KdfVerificationBenchmark.RuntimeMetadata currentRuntime) {
        return KdfBenchmarkResultState.evaluate(algorithm, configuration, report, currentRuntime);
    }

    private static ModeledCrackTimeEstimate unavailable(ModeledCrackTimeEstimate.AlgorithmId algorithmId,
            ModeledCrackTimeEstimate.Status status,
            ModeledCrackTimeEstimate.MissingInput... missingInputs) {
        List<ModeledCrackTimeEstimate.MissingInput> missing = List.of(missingInputs);
        String missingDescription = missing.stream()
                .map(ModeledCrackTimeEstimate.MissingInput::description)
                .collect(Collectors.joining(", "));
        String reason = "Estimate unavailable. Missing: " + missingDescription
                + ". No traceable aggregate rate is configured; synthetic-search throughput is unrelated and is not used.";
        if (algorithmId == ModeledCrackTimeEstimate.AlgorithmId.UNSPECIFIED_SHA3_KDF) {
            reason += " Plain SHA-3 is not a password-storage KDF.";
        }
        return new ModeledCrackTimeEstimate(status, reason, missing, SCENARIO, algorithmId);
    }
}
