package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.security.InvalidKeyException;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javafx.application.Platform;

class KdfBenchmarkReportLoaderTest {
    private static final Instant TIMESTAMP = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void loadsOnlyExplicitFileAndReturnsExactMatchObservation(@TempDir Path directory) throws IOException {
        Fixture fixture = fixture();
        Path reportPath = directory.resolve("selected-report.json");
        Files.writeString(reportPath, fixture.completedJson());

        KdfBenchmarkResultState state =
                KdfBenchmarkReportLoader.load(reportPath, fixture.algorithm(), fixture.configuration());

        assertEquals(KdfBenchmarkResultState.Status.LOCAL_VERIFICATION_COST_OBSERVATION,
                state.status(), state.reason());
        assertTrue(state.medianVerificationMilliseconds().isPresent());
        assertTrue(state.displayText().startsWith("Median local KDF verification:"));
        assertTrue(state.displayText().endsWith("(not crack time)"));
        assertTrue(PasswordKdfCostDataset.observations().isEmpty(),
                "loading a report must not run a benchmark or mutate the observations dataset");
    }

    @Test
    void loadingAReportDoesNotExecuteTheConfiguredKdf(@TempDir Path directory) throws IOException {
        CountingProvider provider = new CountingProvider();
        Security.addProvider(provider);
        try {
            PasswordKdfCostDataset.Pbkdf2Configuration configuration =
                    new PasswordKdfCostDataset.Pbkdf2Configuration(
                            "HmacSHA256", 10_000, 16, 32,
                            new PasswordKdfCostDataset.Provider(
                                    provider.getName(), provider.getVersionStr()));
            String json = completedJsonFor(configuration);
            Path reportPath = directory.resolve("explicit-report.json");
            Files.writeString(reportPath, json);
            CountingPbkdf2Factory.GENERATE_CALLS.set(0);

            KdfBenchmarkResultState state = KdfBenchmarkReportLoader.load(
                    reportPath, AlgorithmCatalog.all().get(3), configuration);

            assertEquals(KdfBenchmarkResultState.Status.LOCAL_VERIFICATION_COST_OBSERVATION,
                    state.status(), state.reason());
            assertEquals(0, CountingPbkdf2Factory.GENERATE_CALLS.get());
        } finally {
            Security.removeProvider(provider.getName());
        }
    }

    @Test
    void rejectsMalformedMissingAndUnsupportedReportsWithoutLeakingInput(@TempDir Path directory)
            throws IOException {
        Fixture fixture = fixture();
        Path reportPath = directory.resolve("report.json");
        List<String> invalidReports = List.of(
                "fixture-secret-that-must-not-be-echoed {",
                fixture.completedJson().replaceFirst("\\{", "{\"status\":\"COMPLETED\","),
                fixture.completedJson() + " unexpected",
                fixture.completedJson().replace("\"schemaVersion\":\"1.0\"", "\"schemaVersion\":\"99.0\""),
                fixture.completedJson().replace("\"algorithm\":\"PBKDF2\"", "\"algorithm\":\"Mystery\""),
                fixture.completedJson().replace("\"sampleCount\":5,", ""),
                fixture.completedJson().replace("\"cpuIdentifier\":{", "\"cpuIdentifierMissing\":{"),
                fixture.completedJson().replace("\"version\":\"" + fixture.configuration().provider().version()
                        + "\"", "\"providerVersionMissing\":\"" + fixture.configuration().provider().version() + "\""),
                fixture.completedJson().replace("\"operatingSystem\":{", "\"operatingSystemMissing\":{"),
                fixture.completedJson().replace("\"status\":\"COMPLETED\"", "\"status\":\"FAILED\""),
                fixture.fixtureJson());

        for (String invalid : invalidReports) {
            Files.writeString(reportPath, invalid);
            KdfBenchmarkResultState state =
                    KdfBenchmarkReportLoader.load(reportPath, fixture.algorithm(), fixture.configuration());
            assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE, state.status());
            assertFalse(state.reason().contains("fixture-secret-that-must-not-be-echoed"));
            assertFalse(state.reason().contains(reportPath.toString()));
            assertFalse(state.reason().contains(invalid));
        }
    }

    @Test
    void rejectsInvalidMeasurementValues(@TempDir Path directory) throws IOException {
        Fixture fixture = fixture();
        Path reportPath = directory.resolve("report.json");
        List<String> invalidReports = List.of(
                fixture.completedJson().replace("\"median\":300", "\"median\":-1"),
                fixture.completedJson().replace("\"median\":300", "\"median\":NaN"),
                fixture.completedJson().replace("\"median\":300", "\"median\":Infinity"),
                fixture.completedJson().replace("\"median\":300", "\"median\":0"),
                fixture.completedJson().replace("\"median\":300", "\"median\":\"300\""),
                fixture.completedJson().replace("\"median\":300", "\"median\":300.5"),
                fixture.completedJson().replace("\"p90\":500", "\"p90\":200"),
                fixture.completedJson().replace("\"value\":123456789", "\"value\":-1"),
                fixture.completedJson().replace("\"value\":123456789", "\"value\":100"));

        for (String invalid : invalidReports) {
            Files.writeString(reportPath, invalid);
            assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE,
                    KdfBenchmarkReportLoader.load(
                            reportPath, fixture.algorithm(), fixture.configuration()).status());
        }
    }

    @Test
    void requiresExactAlgorithmVariantParametersProviderAndRuntime(@TempDir Path directory)
            throws IOException {
        Fixture fixture = fixture();
        Path reportPath = directory.resolve("report.json");
        List<String> mismatches = List.of(
                completedJsonFor(new PasswordKdfCostDataset.BcryptConfiguration(10,
                        PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
                        16, "OpenBSDBCrypt-2y-60-char",
                        new PasswordKdfCostDataset.Provider("Bouncy Castle bcprov-jdk18on", "1.86"))),
                fixture.completedJson().replaceFirst("\"variant\":\"[^\"]+\"",
                        "\"variant\":\"PBKDF2-HmacSHA512\""),
                fixture.completedJson().replace("\"iterationCount\":10000", "\"iterationCount\":10001"),
                fixture.completedJson().replace("\"name\":\"SunJCE\"", "\"name\":\"DifferentProvider\""),
                fixture.completedJson().replace("\"version\":\"" + fixture.configuration().provider().version()
                                + "\"",
                        "\"version\":\"DifferentVersion\""),
                fixture.completedJson().replaceFirst(
                        "\"javaRuntime\":\\{\"name\":\"[^\"]+\"",
                        "\"javaRuntime\":{\"name\":\"Different Java runtime\""),
                fixture.completedJson().replace(
                        "\"" + jsonEscape(osName(fixture.runtime())) + "\"", "\"Different OS\""),
                fixture.completedJson().replace(
                        "\"" + jsonEscape(fixture.runtime().architecture()) + "\"", "\"Different architecture\""),
                fixture.completedJson().replaceFirst(
                        "\"cpuIdentifier\":\\{\"availability\":\"[^\"]+\",\"value\":\"[^\"]+\"\\}",
                        "\"cpuIdentifier\":{\"availability\":\"available\","
                                + "\"value\":\"different CPU identifier\"}"),
                mismatchedJvmMetadata(fixture),
                mismatchedJvmValues(fixture));

        for (int index = 0; index < mismatches.size(); index++) {
            String mismatch = mismatches.get(index);
            Files.writeString(reportPath, mismatch);
            KdfBenchmarkResultState state =
                    KdfBenchmarkReportLoader.load(reportPath, fixture.algorithm(), fixture.configuration());
            assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED,
                    state.status(), "mismatch case " + index);
            assertTrue(state.medianVerificationMilliseconds().isEmpty());
        }
    }

    @Test
    void reportsMissingOrUnreadableFilesAndDoesNotSelectAnotherFile(@TempDir Path directory)
            throws IOException {
        Fixture fixture = fixture();
        Path otherReport = directory.resolve("latest-looking-report.json");
        Files.writeString(otherReport, fixture.completedJson());
        Path absent = directory.resolve("not-selected.json");

        KdfBenchmarkResultState absentState =
                KdfBenchmarkReportLoader.load(absent, fixture.algorithm(), fixture.configuration());
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE, absentState.status());
        assertFalse(absentState.reason().contains(absent.toString()));

        KdfBenchmarkResultState directoryState =
                KdfBenchmarkReportLoader.load(directory, fixture.algorithm(), fixture.configuration());
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE, directoryState.status());

        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE,
                KdfBenchmarkReportLoader.load(null, fixture.algorithm(), fixture.configuration()).status());
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED,
                KdfBenchmarkReportLoader.load(otherReport, fixture.algorithm(), null).status());
    }

    @Test
    void keepsSha3NotEstimableAndRejectsUnsupportedConfiguration(@TempDir Path directory)
            throws IOException {
        Fixture fixture = fixture();
        Path reportPath = directory.resolve("report.json");
        Files.writeString(reportPath, fixture.completedJson());

        assertEquals(KdfBenchmarkResultState.Status.NOT_ESTIMABLE,
                KdfBenchmarkReportLoader.load(reportPath, AlgorithmCatalog.all().get(4), null).status());
        assertEquals(KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE,
                KdfBenchmarkReportLoader.load(reportPath, AlgorithmCatalog.all().get(0),
                        new PasswordKdfCostDataset.Argon2idConfiguration("1.0",
                                8L * 1024 * 1024, 2, 1, 16, 32,
                                new PasswordKdfCostDataset.Provider(
                                        "Bouncy Castle bcprov-jdk18on", "1.86"))).status());
    }

    @Test
    void resultCardStillNeverShowsVerificationLatencyAsCrackTime() throws Exception {
        runOnFxThread(() -> {
            for (AlgorithmInfo algorithm : AlgorithmCatalog.all()) {
                AlgorithmInfoCard card = new AlgorithmInfoCard(algorithm, 1);
                assertFalse(card.modeledTimeText().contains("Median local KDF verification"));
                assertFalse(card.modeledTimeText().matches(".*\\d+(?:\\.\\d+)?\\s*ms.*"));
            }
            return null;
        });
    }

    private static <T> T runOnFxThread(java.util.concurrent.Callable<T> action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
            assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit should start");
        } catch (IllegalStateException alreadyStarted) {
            // Another test or application bootstrap already initialized JavaFX.
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(60, TimeUnit.SECONDS);
    }

    private static Fixture fixture() {
        java.security.Provider jcaProvider = java.security.Security.getProvider("SunJCE");
        if (jcaProvider == null) {
            throw new IllegalStateException("SunJCE required to generate local report test fixture");
        }
        PasswordKdfCostDataset.Pbkdf2Configuration configuration =
                new PasswordKdfCostDataset.Pbkdf2Configuration(
                        "HmacSHA256", 10_000, 16, 32,
                        new PasswordKdfCostDataset.Provider(
                                jcaProvider.getName(), jcaProvider.getVersionStr()));
        KdfVerificationBenchmark.RuntimeMetadata runtime =
                KdfVerificationBenchmark.currentRuntimeMetadata(configuration);
        KdfBenchmarkReport testReport = KdfBenchmarkReport.testFixture(TIMESTAMP,
                configuration, runtime, KdfBenchmarkStatistics.fromSamples(
                        List.of(100L, 200L, 300L, 400L, 500L)));
        String fixtureJson = testReport.toJson();
        String completedJson = fixtureJson
                .replace("\"status\":\"TEST_FIXTURE\"", "\"status\":\"COMPLETED\"")
                .replace("TEST FIXTURE ONLY; not a benchmark observation.",
                        "Test-generated placeholder measurements; measuredDuration uses System.nanoTime().")
                .replace("TEST FIXTURE ONLY; no KDF benchmark was run.",
                        "Test-generated schema fixture; no benchmark is run by the loader.");
        return new Fixture(configuration, runtime, AlgorithmCatalog.all().get(3),
                fixtureJson, completedJson);
    }

    private static String completedJsonFor(PasswordKdfCostDataset.Configuration configuration) {
        KdfVerificationBenchmark.RuntimeMetadata runtime =
                KdfVerificationBenchmark.currentRuntimeMetadata(configuration);
        return KdfBenchmarkReport.testFixture(TIMESTAMP, configuration, runtime,
                KdfBenchmarkStatistics.fromSamples(List.of(100L, 200L, 300L, 400L, 500L)))
                .toJson()
                .replace("\"status\":\"TEST_FIXTURE\"", "\"status\":\"COMPLETED\"")
                .replace("TEST FIXTURE ONLY; not a benchmark observation.",
                        "Test-generated placeholder measurements; measuredDuration uses System.nanoTime().")
                .replace("TEST FIXTURE ONLY; no KDF benchmark was run.",
                        "Test-generated schema fixture; no benchmark is run by the loader.");
    }

    private static String mismatchedJvmMetadata(Fixture fixture) {
        String alternate = fixture.runtime().jvmInputArgumentsAvailable()
                ? "\"availability\":\"unavailable\",\"values\":[\"unavailable\"]"
                : "\"availability\":\"available\",\"values\":[]";
        return fixture.completedJson().replaceFirst(
                "\"jvmInputArguments\":\\{[^}]*\\}",
                "\"jvmInputArguments\":{" + alternate + "}");
    }

    private static String mismatchedJvmValues(Fixture fixture) {
        String replacement = fixture.runtime().jvmInputArgumentsAvailable()
                ? "\"availability\":\"available\",\"values\":[\"different JVM argument\"]"
                : "\"availability\":\"available\",\"values\":[]";
        return fixture.completedJson().replaceFirst(
                "\"jvmInputArguments\":\\{[^}]*\\}",
                "\"jvmInputArguments\":{" + replacement + "}");
    }

    private static String osName(KdfVerificationBenchmark.RuntimeMetadata runtime) {
        String os = runtime.operatingSystem();
        return os.substring(0, os.lastIndexOf(' '));
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Fixture(PasswordKdfCostDataset.Pbkdf2Configuration configuration,
            KdfVerificationBenchmark.RuntimeMetadata runtime, AlgorithmInfo algorithm,
            String fixtureJson, String completedJson) {
    }

    private static final class CountingProvider extends Provider {
        private static final long serialVersionUID = 1L;

        private CountingProvider() {
            super("AstraLoaderTestProvider", "1.0", "KDF-loader test provider");
            putService(new Service(this, "SecretKeyFactory", "PBKDF2WithHmacSHA256",
                    CountingPbkdf2Factory.class.getName(), List.of(), java.util.Map.of()) {
                @Override
                public Object newInstance(Object parameter) {
                    return new CountingPbkdf2Factory();
                }
            });
        }
    }

    public static final class CountingPbkdf2Factory extends SecretKeyFactorySpi {
        private static final AtomicInteger GENERATE_CALLS = new AtomicInteger();

        @Override
        protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
            GENERATE_CALLS.incrementAndGet();
            return new SecretKeySpec(new byte[32], "PBKDF2");
        }

        @Override
        protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec)
                throws InvalidKeySpecException {
            throw new InvalidKeySpecException("Not implemented for loader test.");
        }

        @Override
        protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
            throw new InvalidKeyException("Not implemented for loader test.");
        }
    }
}
