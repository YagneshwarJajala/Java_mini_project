package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KdfBenchmarkReportTest {
    private static final Instant TIMESTAMP = Instant.parse("2026-02-03T04:05:06Z");
    private static final PasswordKdfCostDataset.Provider PROVIDER =
            new PasswordKdfCostDataset.Provider("Bouncy Castle bcprov-jdk18on", "1.86");
    private static final PasswordKdfCostDataset.Argon2idConfiguration CONFIGURATION =
            new PasswordKdfCostDataset.Argon2idConfiguration("1.3",
                    16L * 1024 * 1024, 3, 2, 16, 32, PROVIDER);

    @Test
    void serializesVersionedMetadataAndActualConfiguredParameters() {
        KdfBenchmarkReport report = testReport(CONFIGURATION, runtimeWithAvailableMetadata());
        String json = report.toJson();

        assertEquals(json, report.toJson());
        assertTrue(json.startsWith("{\"schemaVersion\":\"1.0\",\"status\":\"TEST_FIXTURE\""));
        assertTrue(json.contains("\"timestamp\":\"2026-02-03T04:05:06Z\""));
        assertTrue(json.contains("\"observationType\":\"local_kdf_verification_cost\""));
        assertTrue(json.contains("\"algorithm\":\"Argon2id\""));
        assertTrue(json.contains("\"variant\":\"Argon2id v1.3\""));
        assertTrue(json.contains("\"memoryBytes\":16777216"));
        assertTrue(json.contains("\"timeCost\":3"));
        assertTrue(json.contains("\"parallelism\":2"));
        assertTrue(json.contains("\"saltLengthBytes\":16"));
        assertTrue(json.contains("\"outputLengthBytes\":32"));
        assertTrue(json.contains("\"name\":\"Bouncy Castle bcprov-jdk18on\",\"version\":\"1.86\""));
        assertTrue(json.contains("\"name\":\"OpenJDK Runtime Environment\",\"version\":\"21.0.6\""));
        assertTrue(json.contains("\"name\":\"Windows\",\"version\":\"11\",\"architecture\":\"x86_64\""));
        assertTrue(json.contains("\"cpuIdentifier\":{\"availability\":\"unavailable\""));
        assertTrue(json.contains("\"jvmInputArguments\":{\"availability\":\"available\",\"values\":[\"-Xmx512m\"]}"));
        assertTrue(json.contains("\"warmupCount\":1"));
        assertTrue(json.contains("\"sampleCount\":5"));
        assertTrue(json.contains("\"measuredDuration\":{\"value\":123456789,\"unit\":\"nanoseconds\""));
        assertTrue(json.contains("\"median\":300,\"p90\":500,\"p90Method\":\"nearest-rank\","
                + "\"unit\":\"nanoseconds per verification\""));
        assertTrue(json.contains("\"maximumDurationMillis\":10000"));
        assertTrue(json.contains("TEST FIXTURE ONLY"));
    }

    @Test
    void representsUnavailableMetadataExplicitly() {
        KdfVerificationBenchmark.RuntimeMetadata runtime =
                new KdfVerificationBenchmark.RuntimeMetadata(
                        "unavailable", "unavailable", "unavailable", "1.86",
                        "unavailable", false, List.of());
        String json = testReport(CONFIGURATION, runtime).toJson();

        assertTrue(json.contains("\"cpuIdentifier\":{\"availability\":\"unavailable\",\"value\":\"unavailable\"}"));
        assertTrue(json.contains("\"jvmInputArguments\":{\"availability\":\"unavailable\",\"values\":[\"unavailable\"]}"));
        assertTrue(json.contains("\"name\":\"unavailable\",\"version\":\"unavailable\""));
    }

    @Test
    void serializesParametersForEachSupportedKdf() {
        PasswordKdfCostDataset.BcryptConfiguration bcrypt =
                new PasswordKdfCostDataset.BcryptConfiguration(10,
                        PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
                        16, "OpenBSDBCrypt-2y-60-char", PROVIDER);
        String bcryptJson = testReport(bcrypt, runtimeWithAvailableMetadata()).toJson();
        assertTrue(bcryptJson.contains("\"workFactor\":10"));
        assertTrue(bcryptJson.contains("\"saltHandling\":\"GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT\""));
        assertTrue(bcryptJson.contains("\"outputFormat\":\"OpenBSDBCrypt-2y-60-char\""));

        PasswordKdfCostDataset.ScryptConfiguration scrypt =
                new PasswordKdfCostDataset.ScryptConfiguration(16_384, 8, 1,
                        16_777_216, "128*N*r bytes of scrypt working memory",
                        16, 32, PROVIDER);
        String scryptJson = testReport(scrypt, runtimeWithAvailableMetadata()).toJson();
        assertTrue(scryptJson.contains("\"N\":16384,\"r\":8,\"p\":1"));
        assertTrue(scryptJson.contains("\"memoryCostBytes\":16777216"));
        assertTrue(scryptJson.contains("\"memoryCostContext\":\"128*N*r bytes of scrypt working memory\""));

        PasswordKdfCostDataset.Pbkdf2Configuration pbkdf2 =
                new PasswordKdfCostDataset.Pbkdf2Configuration(
                        "HmacSHA256", 100_000, 16, 32, PROVIDER);
        String pbkdf2Json = testReport(pbkdf2, runtimeWithAvailableMetadata()).toJson();
        assertTrue(pbkdf2Json.contains("\"prfHashVariant\":\"HmacSHA256\""));
        assertTrue(pbkdf2Json.contains("\"iterationCount\":100000"));
        assertTrue(pbkdf2Json.contains("\"name\":\"Bouncy Castle bcprov-jdk18on\",\"version\":\"1.86\""));
    }

    @Test
    void exactConfigurationAndRuntimeMustMatchTheReportProvenance() {
        KdfVerificationBenchmark.RuntimeMetadata runtime = runtimeWithAvailableMetadata();
        KdfBenchmarkReport fixture = testReport(CONFIGURATION, runtime);

        assertTrue(fixture.matchesConfigurationAndRuntime(CONFIGURATION, "1.86", runtime));
        assertFalse(fixture.matches(CONFIGURATION, "1.86", runtime),
                "test fixture data cannot be accepted as a completed observation");

        assertFalse(fixture.matchesConfigurationAndRuntime(
                new PasswordKdfCostDataset.BcryptConfiguration(10,
                        PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
                        16, "OpenBSDBCrypt-2y-60-char", PROVIDER),
                "1.86", runtime), "algorithm mismatch must be rejected");
        assertFalse(fixture.matchesConfigurationAndRuntime(
                new PasswordKdfCostDataset.Argon2idConfiguration("1.0",
                        CONFIGURATION.memoryBytes(), CONFIGURATION.timeCost(), CONFIGURATION.parallelism(),
                        CONFIGURATION.saltLengthBytes(), CONFIGURATION.outputLengthBytes(), PROVIDER),
                "1.86", runtime), "variant mismatch must be rejected");
        assertFalse(fixture.matchesConfigurationAndRuntime(
                new PasswordKdfCostDataset.Argon2idConfiguration("1.3",
                        CONFIGURATION.memoryBytes() * 2, CONFIGURATION.timeCost(), CONFIGURATION.parallelism(),
                        CONFIGURATION.saltLengthBytes(), CONFIGURATION.outputLengthBytes(), PROVIDER),
                "1.86", runtime), "parameter mismatch must be rejected");
        assertFalse(fixture.matchesConfigurationAndRuntime(
                new PasswordKdfCostDataset.Argon2idConfiguration("1.3",
                        CONFIGURATION.memoryBytes(), CONFIGURATION.timeCost(), CONFIGURATION.parallelism(),
                        CONFIGURATION.saltLengthBytes(), CONFIGURATION.outputLengthBytes(),
                        new PasswordKdfCostDataset.Provider("other provider", "1.86")),
                "1.86", runtime), "provider mismatch must be rejected");
        assertFalse(fixture.matchesConfigurationAndRuntime(CONFIGURATION, "different-version", runtime),
                "provider-version mismatch must be rejected");
        assertFalse(fixture.matchesConfigurationAndRuntime(CONFIGURATION, "1.86",
                new KdfVerificationBenchmark.RuntimeMetadata(
                        "Other Java 21", "Windows 11", "x86_64", "1.86",
                        "unavailable", true, List.of("-Xmx512m"))),
                "machine/runtime mismatch must be rejected");
    }

    @Test
    void doesNotSerializeFixtureSecretsOrRawExceptionDetails() {
        String json = testReport(CONFIGURATION, runtimeWithAvailableMetadata()).toJson();

        assertFalse(json.contains("fixture-password-secret"));
        assertFalse(json.contains("fixture-salt-secret"));
        assertFalse(json.contains("fixture-derived-hash-secret"));
        assertFalse(json.contains("raw exception"));

        String failed = KdfBenchmarkReport.failed(TIMESTAMP,
                "Benchmark failed before all samples completed; no successful observation was recorded.")
                .toJson();
        assertTrue(failed.contains("\"status\":\"FAILED\""));
        assertTrue(failed.contains("\"failureReason\":\"Benchmark failed"));
        assertTrue(failed.contains("\"sampleCount\":null"));
        assertTrue(failed.contains("\"value\":null"));
        assertFalse(failed.contains("\"status\":\"COMPLETED\""));
    }

    @Test
    void serializesStringsAsEscapedJson() {
        PasswordKdfCostDataset.ScryptConfiguration scrypt =
                new PasswordKdfCostDataset.ScryptConfiguration(16_384, 8, 1,
                        16_777_216, "context \"quoted\" \\ escaped\nline",
                        16, 32, PROVIDER);
        String json = testReport(scrypt, runtimeWithAvailableMetadata()).toJson();

        assertTrue(json.contains("\"memoryCostContext\":\"context \\\"quoted\\\" \\\\ escaped\\nline\""));
    }

    @Test
    void writesNewReportsAndNeverOverwritesExistingPaths(@TempDir Path directory) throws IOException {
        Path output = directory.resolve("report.json");
        KdfBenchmarkReport report = testReport(CONFIGURATION, runtimeWithAvailableMetadata());

        report.writeNew(output);
        String original = Files.readString(output);
        assertEquals(report.toJson(), original);
        assertThrows(IOException.class, () -> report.writeNew(output));
        assertEquals(original, Files.readString(output));
        assertThrows(IOException.class,
                () -> report.writeNew(directory.resolve("missing").resolve("report.json")));
    }

    @Test
    void documentationDistinguishesMetadataCompatibilityFromAuthenticity() throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        assertTrue(readme.contains("Reports use the existing JSON schema and are unsigned."));
        assertTrue(readme.contains("Metadata matching establishes compatibility only; it does not establish authenticity or tamper resistance."));
        assertTrue(readme.contains("cannot independently prove"));
        assertTrue(readme.contains("Only load files from a source you trust."));
    }

    private static KdfBenchmarkReport testReport(
            PasswordKdfCostDataset.Configuration configuration,
            KdfVerificationBenchmark.RuntimeMetadata runtime) {
        return KdfBenchmarkReport.testFixture(TIMESTAMP, configuration, runtime,
                KdfBenchmarkStatistics.fromSamples(List.of(100L, 200L, 300L, 400L, 500L)));
    }

    private static KdfVerificationBenchmark.RuntimeMetadata runtimeWithAvailableMetadata() {
        return new KdfVerificationBenchmark.RuntimeMetadata(
                "OpenJDK Runtime Environment 21.0.6",
                "Windows 11", "x86_64", "1.86", "unavailable", true, List.of("-Xmx512m"));
    }
}
