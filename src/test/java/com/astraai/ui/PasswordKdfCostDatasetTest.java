package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordKdfCostDatasetTest {
    private static final PasswordKdfCostDataset.Provider PROVIDER =
            new PasswordKdfCostDataset.Provider("test provider", "test version");

    @Test
    void definesExactlyFiveSchemasAndDoesNotIncludeMeasurements() {
        List<PasswordKdfCostDataset.Entry> entries = PasswordKdfCostDataset.entries();

        assertEquals(List.of("Argon2id", "bcrypt", "scrypt", "PBKDF2", "SHA-3-based KDF"),
                entries.stream().map(PasswordKdfCostDataset.Entry::algorithmName).toList());
        assertEquals(5, entries.size());
        assertTrue(PasswordKdfCostDataset.observations().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> entries.add(entries.get(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> PasswordKdfCostDataset.observations().add(null));
        for (PasswordKdfCostDataset.Entry entry : entries) {
            assertFalse(entry.requiredParameters().isEmpty());
            assertTrue(entry.requiredParameters().stream().allMatch(parameter ->
                    !parameter.name().isBlank() && !parameter.unit().isBlank()
                            && !parameter.validationRule().isBlank()));
        }
    }

    @Test
    void recordsRequiredAlgorithmFieldsAndHonestSupportStates() {
        assertFields(PasswordKdfCostDataset.AlgorithmId.ARGON2ID,
                "algorithmVersion", "memoryBytes", "timeCost", "parallelism",
                "saltLengthBytes", "outputLengthBytes", "provider");
        assertFields(PasswordKdfCostDataset.AlgorithmId.BCRYPT,
                "workFactor", "saltHandling", "saltLengthBytes", "outputFormat", "provider");
        assertFields(PasswordKdfCostDataset.AlgorithmId.SCRYPT,
                "N", "r", "p", "memoryCostBytes", "memoryCostContext",
                "saltLengthBytes", "outputLengthBytes", "provider");
        assertFields(PasswordKdfCostDataset.AlgorithmId.PBKDF2,
                "prfHashVariant", "iterationCount", "saltLengthBytes", "outputLengthBytes", "provider");

        for (PasswordKdfCostDataset.AlgorithmId id : List.of(
                PasswordKdfCostDataset.AlgorithmId.ARGON2ID,
                PasswordKdfCostDataset.AlgorithmId.BCRYPT,
                PasswordKdfCostDataset.AlgorithmId.SCRYPT,
                PasswordKdfCostDataset.AlgorithmId.PBKDF2)) {
            assertEquals(PasswordKdfCostDataset.SupportStatus.CONFIGURATION_SCHEMA_DEFINED_PROVIDER_NOT_SELECTED,
                    entry(id).supportStatus());
            assertTrue(entry(id).limitation().contains("provider-specific"));
        }
    }

    @Test
    void acceptsExplicitConfigurationsWithoutClaimingProviderRangeValidation() {
        List<PasswordKdfCostDataset.Configuration> configurations = List.of(
                new PasswordKdfCostDataset.Argon2idConfiguration(
                        "argon2id-version-explicit", 65_536, 3, 2, 16, 32, PROVIDER),
                new PasswordKdfCostDataset.BcryptConfiguration(
                        12, PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
                        16, "provider-specific encoded format", PROVIDER),
                new PasswordKdfCostDataset.ScryptConfiguration(
                        16_384, 8, 1, 16_777_216, "explicit memory budget/context", 16, 32, PROVIDER),
                new PasswordKdfCostDataset.Pbkdf2Configuration(
                        "HmacSHA256", 10_000, 16, 32, PROVIDER));

        for (PasswordKdfCostDataset.Configuration configuration : configurations) {
            PasswordKdfCostDataset.ConfigurationValidation validation =
                    PasswordKdfCostDataset.validate(configuration);
            assertEquals(
                    PasswordKdfCostDataset.ValidationStatus.GENERIC_INPUTS_VALID_PROVIDER_SPECIFIC_LIMITS_UNRESOLVED,
                    validation.status());
            assertFalse(validation.unresolvedConstraints().isEmpty());
            assertTrue(validation.unresolvedConstraints().get(0).contains("depend on the selected provider"));
        }
    }

    @Test
    void rejectsMissingAndInvalidRequiredConfigurationValues() {
        assertThrows(NullPointerException.class, () -> new PasswordKdfCostDataset.Argon2idConfiguration(
                null, 1, 1, 1, 1, 1, PROVIDER));
        assertThrows(NullPointerException.class, () -> new PasswordKdfCostDataset.Argon2idConfiguration(
                "version", 1, 1, 1, 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Provider("", "v"));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Provider("provider", " "));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Argon2idConfiguration(
                "version", 0, 1, 1, 1, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Argon2idConfiguration(
                "version", 1, -1, 1, 1, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Argon2idConfiguration(
                "version", 1, 1, 1, 0, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.BcryptConfiguration(
                0, PasswordKdfCostDataset.SaltHandling.EXPLICIT_SALT_INPUT, 16, "format", PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.BcryptConfiguration(
                1, PasswordKdfCostDataset.SaltHandling.EXPLICIT_SALT_INPUT, 0, "format", PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.BcryptConfiguration(
                1, PasswordKdfCostDataset.SaltHandling.EXPLICIT_SALT_INPUT, 16, "", PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.ScryptConfiguration(
                3, 1, 1, 1, "context", 1, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.ScryptConfiguration(
                4, 0, 1, 1, "context", 1, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.ScryptConfiguration(
                4, 1, 1, 1, " ", 1, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Pbkdf2Configuration(
                "", 1, 1, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Pbkdf2Configuration(
                "HmacSHA256", 0, 1, 1, PROVIDER));
        assertThrows(IllegalArgumentException.class, () -> new PasswordKdfCostDataset.Pbkdf2Configuration(
                "HmacSHA256", 1, 1, 0, PROVIDER));
        assertThrows(NullPointerException.class, () -> PasswordKdfCostDataset.validate(null));
    }

    @Test
    void keepsSha3BasedEntryUnsupportedAndUnderspecified() {
        PasswordKdfCostDataset.Entry sha3 =
                entry(PasswordKdfCostDataset.AlgorithmId.UNSPECIFIED_SHA3_KDF);

        assertEquals(PasswordKdfCostDataset.SupportStatus.UNSUPPORTED_OR_UNDERSPECIFIED,
                sha3.supportStatus());
        assertTrue(sha3.limitation().contains("plain SHA-3 digest"));
        assertTrue(sha3.requiredParameters().stream()
                .anyMatch(parameter -> parameter.name().equals("construction")));
    }

    private static void assertFields(PasswordKdfCostDataset.AlgorithmId id, String... fields) {
        assertEquals(List.of(fields), entry(id).requiredParameters().stream()
                .map(PasswordKdfCostDataset.ParameterRequirement::name).toList());
    }

    private static PasswordKdfCostDataset.Entry entry(PasswordKdfCostDataset.AlgorithmId id) {
        return PasswordKdfCostDataset.find(id).orElseThrow();
    }
}
