package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.Provider;
import java.security.Security;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KdfVerificationBenchmarkTest {
    private static final PasswordKdfCostDataset.Provider BC =
            new PasswordKdfCostDataset.Provider("Bouncy Castle bcprov-jdk18on", "1.86");

    @Test
    void acceptsExplicitSupportedConfigurationsAndIdentifiesTheirProvider() {
        PasswordKdfCostDataset.Argon2idConfiguration argon =
                new PasswordKdfCostDataset.Argon2idConfiguration("1.3",
                        8L * 1024 * 1024, 2, 1, 16, 32, BC);
        PasswordKdfCostDataset.BcryptConfiguration bcrypt =
                new PasswordKdfCostDataset.BcryptConfiguration(10,
                        PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
                        16, "OpenBSDBCrypt-2y-60-char", BC);
        PasswordKdfCostDataset.ScryptConfiguration scrypt =
                new PasswordKdfCostDataset.ScryptConfiguration(16_384, 8, 1,
                        128L * 16_384 * 8, "128*N*r bytes of scrypt working memory",
                        16, 32, BC);

        assertEquals("1.86", KdfVerificationBenchmark.validateSupportedConfiguration(argon));
        assertEquals("1.86", KdfVerificationBenchmark.validateSupportedConfiguration(bcrypt));
        assertEquals("1.86", KdfVerificationBenchmark.validateSupportedConfiguration(scrypt));
    }

    @Test
    void validatesSelectedPbkdfProviderAndPrfWithoutRunningKdf() {
        Provider provider = Security.getProvider("SunJCE");
        PasswordKdfCostDataset.Pbkdf2Configuration configuration =
                new PasswordKdfCostDataset.Pbkdf2Configuration(
                        "HmacSHA256", 10_000, 16, 32,
                        new PasswordKdfCostDataset.Provider(provider.getName(), provider.getVersionStr()));

        assertEquals(provider.getVersionStr(),
                KdfVerificationBenchmark.validateSupportedConfiguration(configuration));
    }

    @Test
    void rejectsUnsupportedOrUnsafeProviderConfigurations() {
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.validateSupportedConfiguration(
                        new PasswordKdfCostDataset.Argon2idConfiguration("1.0",
                                8L * 1024 * 1024, 2, 1, 16, 32, BC)));
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.validateSupportedConfiguration(
                        new PasswordKdfCostDataset.Argon2idConfiguration("1.3",
                                8L * 1024 * 1024, 2, 1, 16, 32,
                                new PasswordKdfCostDataset.Provider("unknown", "1"))));
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.validateSupportedConfiguration(
                        new PasswordKdfCostDataset.BcryptConfiguration(17,
                                PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
                                16, "OpenBSDBCrypt-2y-60-char", BC)));
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.validateSupportedConfiguration(
                        new PasswordKdfCostDataset.BcryptConfiguration(10,
                                PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
                                15, "OpenBSDBCrypt-2y-60-char", BC)));
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.validateSupportedConfiguration(
                        new PasswordKdfCostDataset.ScryptConfiguration(16_384, 8, 1,
                                1, "incorrect memory cost", 16, 32, BC)));
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.validateSupportedConfiguration(
                        new PasswordKdfCostDataset.Pbkdf2Configuration(
                                "UnsupportedHash", 1_000, 16, 32,
                                new PasswordKdfCostDataset.Provider("SunJCE", "21"))));
        assertThrows(NullPointerException.class,
                () -> KdfVerificationBenchmark.run(null, new KdfVerificationBenchmark.Settings(1, 3, 1000)));
        assertThrows(NullPointerException.class,
                () -> KdfVerificationBenchmark.run(
                        new PasswordKdfCostDataset.Argon2idConfiguration("1.3",
                                8L * 1024 * 1024, 2, 1, 16, 32, BC), null));
    }

    @Test
    void rejectsMissingRuntimeMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new KdfVerificationBenchmark.RuntimeMetadata("", "OS", "x64", "1.86"));
        assertThrows(IllegalArgumentException.class,
                () -> new KdfVerificationBenchmark.RuntimeMetadata("Java", "OS", "x64", " "));
    }

    @Test
    void rejectsUnspecifiedSha3AndUnknownCommandOptions() {
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.configurationFrom(Map.of("algorithm", "SHA3")));
        assertThrows(IllegalArgumentException.class,
                () -> KdfVerificationBenchmark.configurationFrom(Map.of(
                        "algorithm", "PBKDF2", "unrecognized", "value")));
    }

    @Test
    void acceptsExplicitOutputPathAndCreatesSafeTimestampedDefaultPath() {
        PasswordKdfCostDataset.Configuration configuration =
                new PasswordKdfCostDataset.Argon2idConfiguration("1.3",
                        8L * 1024 * 1024, 2, 1, 16, 32, BC);
        Instant timestamp = Instant.parse("2026-02-03T04:05:06Z");

        assertEquals(Path.of("reports", "local.json"),
                KdfVerificationBenchmark.outputPath(Map.of("output", "reports/local.json"),
                        timestamp, configuration));
        assertEquals(Path.of("kdf-verification-argon2id-20260203T040506000Z.json"),
                KdfVerificationBenchmark.outputPath(Map.of(), timestamp, configuration));
    }

    @Test
    void commandConfigurationRequiresBcryptSaltAndOutputMetadata() {
        PasswordKdfCostDataset.BcryptConfiguration configuration =
                (PasswordKdfCostDataset.BcryptConfiguration) KdfVerificationBenchmark.configurationFrom(Map.of(
                        "algorithm", "BCRYPT",
                        "work-factor", "10",
                        "salt-handling", "GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT",
                        "salt-length-bytes", "16",
                        "output-format", "OpenBSDBCrypt-2y-60-char"));

        assertEquals(16, configuration.saltLengthBytes());
        assertEquals("OpenBSDBCrypt-2y-60-char", configuration.outputFormat());
    }
}
