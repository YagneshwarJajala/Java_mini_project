package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrackTimeEstimatorTest {
    @Test
    void calculatesExpectedWorkFromACharacterClassModelAndFormatsIt() {
        PasswordAnalysis analysis = PasswordInputPolicy.analyzeAndErase("a".toCharArray()).orElseThrow();
        CrackTimeEstimate estimate = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0), analysis);

        assertEquals(CrackTimeEstimate.Status.ESTIMATED, estimate.status());
        assertEquals("About 1 second", estimate.displayText());
        assertEquals(new BigDecimal("26"), estimate.candidateSpace().orElseThrow());
        assertEquals(new BigDecimal("14"), estimate.expectedGuesses().orElseThrow());
        assertEquals(new BigDecimal("10"), estimate.assumedGuessesPerSecond().orElseThrow());
        assertTrue(estimate.explanation().contains("half the modeled space"));
        assertTrue(estimate.explanation().contains("not measured"));
    }

    @Test
    void searchSpaceAndPatternHeuristicsChangeTheEstimateWithoutRetainingText() {
        PasswordAnalysis lowercase = PasswordInputPolicy.analyzeAndErase("abcdefgh".toCharArray()).orElseThrow();
        PasswordAnalysis mixed = PasswordInputPolicy.analyzeAndErase("Abcd123!".toCharArray()).orElseThrow();
        PasswordAnalysis repeated = PasswordInputPolicy.analyzeAndErase("aaaaaaaa".toCharArray()).orElseThrow();

        CrackTimeEstimate lowerEstimate = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(1), lowercase);
        CrackTimeEstimate mixedEstimate = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(1), mixed);
        CrackTimeEstimate repeatedEstimate = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(1), repeated);
        assertTrue(mixedEstimate.candidateSpace().orElseThrow()
                .compareTo(lowerEstimate.candidateSpace().orElseThrow()) > 0);
        assertEquals(new BigDecimal("26"), repeatedEstimate.candidateSpace().orElseThrow());
        assertNotEquals(lowerEstimate.displayText(), mixedEstimate.displayText());
        assertEquals(CrackTimeEstimate.Status.ESTIMATED, repeatedEstimate.status());
    }

    @Test
    void algorithmSpecificAssumptionRatesChangeDisplayedEstimate() {
        PasswordAnalysis analysis = PasswordInputPolicy.analyzeAndErase("a".toCharArray()).orElseThrow();
        CrackTimeAssumptionDataset slow = new CrackTimeAssumptionDataset(Map.of(
                CrackTimeAssumptionDataset.Algorithm.ARGON2ID,
                new CrackTimeAssumptionDataset.Profile("test config", new BigDecimal("1"))));
        CrackTimeAssumptionDataset fast = new CrackTimeAssumptionDataset(Map.of(
                CrackTimeAssumptionDataset.Algorithm.ARGON2ID,
                new CrackTimeAssumptionDataset.Profile("test config", new BigDecimal("100"))));

        assertEquals("About 14 seconds",
                CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0), analysis, slow).displayText());
        assertEquals("Less than a second",
                CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0), analysis, fast).displayText());
        assertEquals("Estimate unavailable",
                CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(1), analysis, slow).displayText());
    }

    @Test
    void formatsHumanReadableUnitBoundariesAndExtremeValues() {
        assertEquals("Less than a second", CrackTimeEstimator.formatDuration(new BigDecimal("0.5")));
        assertEquals("About 1 second", CrackTimeEstimator.formatDuration(BigDecimal.ONE));
        assertEquals("About 1 minute", CrackTimeEstimator.formatDuration(BigDecimal.valueOf(60)));
        assertEquals("About 1 hour", CrackTimeEstimator.formatDuration(BigDecimal.valueOf(3_600)));
        assertEquals("About 1 day", CrackTimeEstimator.formatDuration(BigDecimal.valueOf(86_400)));
        assertEquals("About 1 month", CrackTimeEstimator.formatDuration(BigDecimal.valueOf(2_629_800)));
        assertEquals("About 1 year", CrackTimeEstimator.formatDuration(BigDecimal.valueOf(31_557_600)));
        assertEquals("About 1 minute", CrackTimeEstimator.formatDuration(new BigDecimal("59.8")));
        assertEquals("About 1 hour", CrackTimeEstimator.formatDuration(new BigDecimal("3599.8")));
        assertEquals("Over 1,000 years", CrackTimeEstimator.formatDuration(
                BigDecimal.valueOf(31_557_600_001L)));
        assertEquals("Less than a second", CrackTimeEstimator.formatDuration(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> CrackTimeEstimator.formatDuration(BigDecimal.valueOf(-1)));
    }

    @Test
    void veryLongInputsAndExtremeRatesRemainFiniteAndUseHonestBounds() {
        char[] chars = new char[256];
        java.util.Arrays.fill(chars, 'a');
        chars[255] = 'b';
        PasswordAnalysis longAnalysis = PasswordInputPolicy.analyzeAndErase(chars).orElseThrow();
        CrackTimeAssumptionDataset tinyRate = new CrackTimeAssumptionDataset(Map.of(
                CrackTimeAssumptionDataset.Algorithm.ARGON2ID,
                new CrackTimeAssumptionDataset.Profile("extreme test config", new BigDecimal("1E-1000"))));
        CrackTimeEstimate longEstimate = CrackTimeEstimator.estimate(
                AlgorithmCatalog.all().get(0), longAnalysis, tinyRate);

        assertEquals(CrackTimeEstimate.Status.ESTIMATED, longEstimate.status());
        assertTrue(longEstimate.candidateSpace().orElseThrow().signum() > 0);
        assertEquals("Over 1,000 years", longEstimate.displayText());
        assertEquals("Less than a second",
                CrackTimeEstimator.formatDuration(new BigDecimal("1E-1000")));
        CrackTimeAssumptionDataset hugeRate = new CrackTimeAssumptionDataset(Map.of(
                CrackTimeAssumptionDataset.Algorithm.ARGON2ID,
                new CrackTimeAssumptionDataset.Profile("extreme test config", new BigDecimal("1E1000"))));
        PasswordAnalysis shortAnalysis = PasswordInputPolicy.analyzeAndErase("a".toCharArray()).orElseThrow();
        assertEquals("Less than a second", CrackTimeEstimator.estimate(
                AlgorithmCatalog.all().get(0), shortAnalysis, hugeRate).displayText());
    }

    @Test
    void unavailableUnsupportedAndUnicodeInputsNeverProduceInventedTimes() {
        CrackTimeEstimate noInput = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0), null);
        PasswordAnalysis unicode = PasswordInputPolicy.analyzeAndErase("pássword".toCharArray()).orElseThrow();
        CrackTimeEstimate unsupported = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0), unicode);
        CrackTimeEstimate sha3 = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(4), unicode);

        assertEquals(CrackTimeEstimate.Status.UNAVAILABLE, noInput.status());
        assertEquals(CrackTimeEstimate.Status.UNAVAILABLE, unsupported.status());
        assertTrue(unsupported.explanation().contains("printable ASCII"));
        assertEquals(CrackTimeEstimate.Status.NOT_ESTIMABLE, sha3.status());
        assertEquals("Not estimable", sha3.displayText());
    }

    @Test
    void modelApiDoesNotAcceptSimulationOrBenchmarkInputs() throws Exception {
        assertEquals(List.of(AlgorithmInfo.class, PasswordAnalysis.class),
                java.util.Arrays.stream(CrackTimeEstimator.class.getMethod("estimate",
                        AlgorithmInfo.class, PasswordAnalysis.class).getParameterTypes()).toList());
    }

    @Test
    void argon2idNamesAllMissingScenarioInputs() {
        ModeledCrackTimeEstimate estimate = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0));

        assertEquals(ModeledCrackTimeEstimate.AlgorithmId.ARGON2ID, estimate.algorithmId());
        assertEquals(ModeledCrackTimeEstimate.Status.NOT_ESTIMATED, estimate.status());
        assertEquals("Estimate unavailable", estimate.status().displayText());
        assertEquals(List.of(
                ModeledCrackTimeEstimate.MissingInput.ALGORITHM_VARIANT,
                ModeledCrackTimeEstimate.MissingInput.MEMORY_TIME_PARALLELISM,
                ModeledCrackTimeEstimate.MissingInput.IMPLEMENTATION_HARDWARE_CONTEXT,
                ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA,
                ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION), estimate.missingInputs());
        assertTrue(estimate.reason().contains("synthetic-search throughput is unrelated and is not used"));
    }

    @Test
    void bcryptScryptAndPbkdf2RequireAlgorithmSpecificConfiguration() {
        List<ModeledCrackTimeEstimate> estimates = AlgorithmCatalog.all().subList(1, 4).stream()
                .map(CrackTimeEstimator::estimate)
                .toList();

        assertEquals(List.of(
                ModeledCrackTimeEstimate.MissingInput.BCRYPT_WORK_FACTOR,
                ModeledCrackTimeEstimate.MissingInput.SCRYPT_N_R_P,
                ModeledCrackTimeEstimate.MissingInput.PBKDF2_PRF),
                estimates.stream().map(estimate -> estimate.missingInputs().get(0)).toList());
        for (ModeledCrackTimeEstimate estimate : estimates) {
            assertEquals(ModeledCrackTimeEstimate.Status.NOT_ESTIMATED, estimate.status());
            assertTrue(estimate.missingInputs().contains(
                    ModeledCrackTimeEstimate.MissingInput.IMPLEMENTATION_HARDWARE_CONTEXT));
            assertTrue(estimate.missingInputs().contains(
                    ModeledCrackTimeEstimate.MissingInput.TRACEABLE_COST_DATA));
            assertTrue(estimate.missingInputs().contains(
                    ModeledCrackTimeEstimate.MissingInput.CANDIDATE_SPACE_ASSUMPTION));
        }
        assertTrue(estimates.get(1).missingInputs().contains(ModeledCrackTimeEstimate.MissingInput.SCRYPT_MEMORY));
        assertTrue(estimates.get(2).missingInputs().contains(ModeledCrackTimeEstimate.MissingInput.PBKDF2_ITERATIONS));
    }

    @Test
    void unspecifiedSha3EntryIsNotEstimableUntilConcreteKdfAndParametersAreKnown() {
        ModeledCrackTimeEstimate estimate = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(4));

        assertEquals(ModeledCrackTimeEstimate.AlgorithmId.UNSPECIFIED_SHA3_KDF, estimate.algorithmId());
        assertEquals(ModeledCrackTimeEstimate.Status.NOT_ESTIMABLE, estimate.status());
        assertEquals("Not estimable", estimate.status().displayText());
        assertEquals(ModeledCrackTimeEstimate.MissingInput.CONCRETE_PASSWORD_KDF, estimate.missingInputs().get(0));
        assertTrue(estimate.reason().contains("Plain SHA-3 is not a password-storage KDF"));
    }

    @Test
    void scenarioMetadataIsLogicalOnlyAndHasNoConfiguredRateOrScaling() {
        ModeledCrackTimeEstimate estimate = CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0));
        ModeledCrackTimeEstimate.ScenarioMetadata scenario = estimate.scenario();

        assertEquals(100, scenario.configuredLogicalAgentCount());
        assertEquals(ModeledCrackTimeEstimate.AgentCountMeaning.LOGICAL_AGENTS_ONLY,
                scenario.agentCountMeaning());
        assertEquals(ModeledCrackTimeEstimate.RateScope.NOT_CONFIGURED, scenario.rateScope());
        assertFalse(estimate.reason().contains("multiplied by"));
    }

    @Test
    void scenarioMetadataRejectsInvalidAgentCountsAndMissingRateScope() {
        assertThrows(IllegalArgumentException.class, () -> new ModeledCrackTimeEstimate.ScenarioMetadata(
                0, ModeledCrackTimeEstimate.AgentCountMeaning.LOGICAL_AGENTS_ONLY,
                ModeledCrackTimeEstimate.RateScope.NOT_CONFIGURED));
        assertThrows(IllegalArgumentException.class, () -> new ModeledCrackTimeEstimate.ScenarioMetadata(
                -100, ModeledCrackTimeEstimate.AgentCountMeaning.LOGICAL_AGENTS_ONLY,
                ModeledCrackTimeEstimate.RateScope.NOT_CONFIGURED));
        assertThrows(NullPointerException.class, () -> new ModeledCrackTimeEstimate.ScenarioMetadata(
                100, ModeledCrackTimeEstimate.AgentCountMeaning.LOGICAL_AGENTS_ONLY, null));
    }

    @Test
    void doesNotInventEstimatesForUnknownAlgorithmsOrNullInputs() {
        AlgorithmInfo unknown = new AlgorithmInfo("Unknown", "Unknown category", "Reference only");
        ModeledCrackTimeEstimate estimate = CrackTimeEstimator.estimate(unknown);
        assertEquals(ModeledCrackTimeEstimate.AlgorithmId.UNKNOWN, estimate.algorithmId());
        assertEquals(ModeledCrackTimeEstimate.Status.NOT_ESTIMATED, estimate.status());
        assertEquals(ModeledCrackTimeEstimate.MissingInput.ALGORITHM_CONFIGURATION,
                estimate.missingInputs().get(0));
        assertThrows(NullPointerException.class, () -> CrackTimeEstimator.estimate(null));
    }

    @Test
    void classifiesBenchmarkAvailabilityWithoutCalculatingCrackTime() {
        AlgorithmInfo argon = AlgorithmCatalog.all().get(0);
        PasswordKdfCostDataset.Provider supportedProvider =
                new PasswordKdfCostDataset.Provider("Bouncy Castle bcprov-jdk18on", "1.86");
        PasswordKdfCostDataset.Argon2idConfiguration configuration =
                new PasswordKdfCostDataset.Argon2idConfiguration(
                        "1.3", 8L * 1024 * 1024, 2, 1, 16, 32, supportedProvider);

        KdfBenchmarkResultState required =
                CrackTimeEstimator.benchmarkState(argon, configuration, Optional.empty(), null);
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED, required.status());
        assertTrue(required.medianVerificationMilliseconds().isEmpty());

        KdfBenchmarkResultState unavailable = CrackTimeEstimator.benchmarkState(argon,
                new PasswordKdfCostDataset.Argon2idConfiguration("1.0",
                        8L * 1024 * 1024, 2, 1, 16, 32, supportedProvider),
                Optional.empty(), null);
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE, unavailable.status());
        assertTrue(unavailable.medianVerificationMilliseconds().isEmpty());

        AlgorithmInfo sha3 = AlgorithmCatalog.all().get(4);
        KdfBenchmarkResultState notEstimable =
                CrackTimeEstimator.benchmarkState(sha3, null, Optional.empty(), null);
        assertEquals(KdfBenchmarkResultState.Status.NOT_ESTIMABLE, notEstimable.status());

        ModeledCrackTimeEstimate estimate = CrackTimeEstimator.estimate(argon);
        assertEquals("Estimate unavailable", estimate.status().displayText());
        assertFalse(estimate.reason().matches("(?s).*\\d+(?:\\.\\d+)?\\s*(?:ms|s|minutes|hours).*"));
    }

    @Test
    void rejectsTestFixtureAndFailedReportsAsUsableObservations() {
        AlgorithmInfo argon = AlgorithmCatalog.all().get(0);
        PasswordKdfCostDataset.Provider provider =
                new PasswordKdfCostDataset.Provider("Bouncy Castle bcprov-jdk18on", "1.86");
        PasswordKdfCostDataset.Argon2idConfiguration configuration =
                new PasswordKdfCostDataset.Argon2idConfiguration(
                        "1.3", 8L * 1024 * 1024, 2, 1, 16, 32, provider);
        KdfVerificationBenchmark.RuntimeMetadata runtime =
                new KdfVerificationBenchmark.RuntimeMetadata(
                        "OpenJDK Runtime Environment 21", "Windows 11", "x86_64",
                        "1.86", "unavailable", false, List.of());
        KdfBenchmarkReport fixture = KdfBenchmarkReport.testFixture(Instant.parse("2026-01-01T00:00:00Z"),
                configuration, runtime, KdfBenchmarkStatistics.fromSamples(List.of(100L, 200L, 300L)));
        KdfBenchmarkReport failed = KdfBenchmarkReport.failed(Instant.parse("2026-01-01T00:00:00Z"),
                "No successful observation.");

        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED,
                CrackTimeEstimator.benchmarkState(argon, configuration, Optional.of(fixture), runtime).status());
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED,
                CrackTimeEstimator.benchmarkState(argon, configuration, Optional.of(failed), runtime).status());
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED,
                CrackTimeEstimator.benchmarkState(argon, configuration, Optional.of(fixture), null).status(),
                "missing current-runtime metadata must prevent report acceptance");
        assertTrue(CrackTimeEstimator.benchmarkState(argon, configuration, Optional.of(fixture), runtime)
                .medianVerificationMilliseconds().isEmpty(),
                "a test fixture must never expose its placeholder statistics as an observation");
    }

    @Test
    void noReportStateContainsOrCalculatesLatencyOrCrackTime() {
        KdfBenchmarkResultState result = CrackTimeEstimator.benchmarkState(
                AlgorithmCatalog.all().get(0), null, Optional.empty(), null);

        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED, result.status());
        assertTrue(result.medianVerificationMilliseconds().isEmpty());
        assertFalse(result.displayText().matches(".*\\d+.*"));
        assertFalse(CrackTimeEstimator.estimate(AlgorithmCatalog.all().get(0))
                .reason().matches("(?s).*\\d+(?:\\.\\d+)?\\s*(?:ms|s|minutes|hours).*"));
    }
}
