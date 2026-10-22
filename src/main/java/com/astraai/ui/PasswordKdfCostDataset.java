package com.astraai.ui;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** KDF configuration schemas kept separate from actual benchmark observations. */
public final class PasswordKdfCostDataset {
    public enum AlgorithmId {
        ARGON2ID,
        BCRYPT,
        SCRYPT,
        PBKDF2,
        UNSPECIFIED_SHA3_KDF
    }

    public enum SupportStatus {
        CONFIGURATION_SCHEMA_DEFINED_PROVIDER_NOT_SELECTED,
        UNSUPPORTED_OR_UNDERSPECIFIED
    }

    public enum ValidationStatus {
        GENERIC_INPUTS_VALID_PROVIDER_SPECIFIC_LIMITS_UNRESOLVED
    }

    public enum SaltHandling {
        GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT,
        EXPLICIT_SALT_INPUT
    }

    public record ParameterRequirement(String name, String unit, String validationRule) {
        public ParameterRequirement {
            requireText(name, "name");
            requireText(unit, "unit");
            requireText(validationRule, "validationRule");
        }
    }

    public record Entry(AlgorithmId algorithmId, String algorithmName, String variant,
            List<ParameterRequirement> requiredParameters, SupportStatus supportStatus,
            String limitation) {
        public Entry {
            Objects.requireNonNull(algorithmId, "algorithmId");
            requireText(algorithmName, "algorithmName");
            requireText(variant, "variant");
            requiredParameters = List.copyOf(Objects.requireNonNull(requiredParameters, "requiredParameters"));
            if (requiredParameters.isEmpty()) {
                throw new IllegalArgumentException("requiredParameters must not be empty");
            }
            Objects.requireNonNull(supportStatus, "supportStatus");
            requireText(limitation, "limitation");
        }
    }

    public record Provider(String name, String version) {
        public Provider {
            requireText(name, "provider.name");
            requireText(version, "provider.version");
        }
    }

    public sealed interface Configuration permits Argon2idConfiguration, BcryptConfiguration,
            ScryptConfiguration, Pbkdf2Configuration {
        Provider provider();
    }

    public record Argon2idConfiguration(String algorithmVersion, long memoryBytes, int timeCost,
            int parallelism, int saltLengthBytes, int outputLengthBytes, Provider provider)
            implements Configuration {
        public Argon2idConfiguration {
            requireText(algorithmVersion, "algorithmVersion");
            requirePositive(memoryBytes, "memoryBytes");
            requirePositive(timeCost, "timeCost");
            requirePositive(parallelism, "parallelism");
            requirePositive(saltLengthBytes, "saltLengthBytes");
            requirePositive(outputLengthBytes, "outputLengthBytes");
            Objects.requireNonNull(provider, "provider");
        }
    }

    public record BcryptConfiguration(int workFactor, SaltHandling saltHandling, int saltLengthBytes,
            String outputFormat, Provider provider) implements Configuration {
        public BcryptConfiguration {
            requirePositive(workFactor, "workFactor");
            Objects.requireNonNull(saltHandling, "saltHandling");
            requirePositive(saltLengthBytes, "saltLengthBytes");
            requireText(outputFormat, "outputFormat");
            Objects.requireNonNull(provider, "provider");
        }
    }

    public record ScryptConfiguration(long n, int r, int p, long memoryCostBytes,
            String memoryCostContext, int saltLengthBytes, int outputLengthBytes, Provider provider)
            implements Configuration {
        public ScryptConfiguration {
            if (n <= 1 || (n & (n - 1)) != 0) {
                throw new IllegalArgumentException("scrypt N must be a power of two greater than 1");
            }
            requirePositive(r, "r");
            requirePositive(p, "p");
            requirePositive(memoryCostBytes, "memoryCostBytes");
            requireText(memoryCostContext, "memoryCostContext");
            requirePositive(saltLengthBytes, "saltLengthBytes");
            requirePositive(outputLengthBytes, "outputLengthBytes");
            Objects.requireNonNull(provider, "provider");
        }
    }

    public record Pbkdf2Configuration(String prfHashVariant, int iterationCount, int saltLengthBytes,
            int outputLengthBytes, Provider provider) implements Configuration {
        public Pbkdf2Configuration {
            requireText(prfHashVariant, "prfHashVariant");
            requirePositive(iterationCount, "iterationCount");
            requirePositive(saltLengthBytes, "saltLengthBytes");
            requirePositive(outputLengthBytes, "outputLengthBytes");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /** Configuration validation is distinct from benchmark evidence and provider support. */
    public record ConfigurationValidation(ValidationStatus status, List<String> unresolvedConstraints) {
        public ConfigurationValidation {
            Objects.requireNonNull(status, "status");
            unresolvedConstraints = List.copyOf(
                    Objects.requireNonNull(unresolvedConstraints, "unresolvedConstraints"));
            if (unresolvedConstraints.isEmpty()) {
                throw new IllegalArgumentException("provider-specific constraints must be stated as unresolved");
            }
        }
    }

    /** A benchmark observation is data, not configuration; none are present in this dataset. */
    public static final class MeasurementObservation {
        private final AlgorithmId algorithmId;
        private final Configuration configuration;
        private final BigDecimal medianDurationMilliseconds;
        private final BigDecimal p90DurationMilliseconds;
        private final int sampleCount;
        private final MeasurementProvenance provenance;

        private MeasurementObservation(AlgorithmId algorithmId, Configuration configuration,
                BigDecimal medianDurationMilliseconds, BigDecimal p90DurationMilliseconds,
                int sampleCount, MeasurementProvenance provenance) {
            this.algorithmId = Objects.requireNonNull(algorithmId, "algorithmId");
            this.configuration = Objects.requireNonNull(configuration, "configuration");
            this.medianDurationMilliseconds = Objects.requireNonNull(
                    medianDurationMilliseconds, "medianDurationMilliseconds");
            this.p90DurationMilliseconds = Objects.requireNonNull(
                    p90DurationMilliseconds, "p90DurationMilliseconds");
            requirePositive(sampleCount, "sampleCount");
            this.sampleCount = sampleCount;
            this.provenance = Objects.requireNonNull(provenance, "provenance");
            if (medianDurationMilliseconds.signum() <= 0 || p90DurationMilliseconds.signum() <= 0) {
                throw new IllegalArgumentException("duration statistics must be positive");
            }
            if (algorithmIdFor(configuration) != algorithmId) {
                throw new IllegalArgumentException("algorithmId must match the measured configuration");
            }
        }

        public AlgorithmId algorithmId() {
            return algorithmId;
        }

        public Configuration configuration() {
            return configuration;
        }

        public BigDecimal medianDurationMilliseconds() {
            return medianDurationMilliseconds;
        }

        public BigDecimal p90DurationMilliseconds() {
            return p90DurationMilliseconds;
        }

        public int sampleCount() {
            return sampleCount;
        }

        public MeasurementProvenance provenance() {
            return provenance;
        }
    }

    /** Provenance attached only to an actual measured observation. */
    public record MeasurementProvenance(String sourceDescription, Optional<URI> sourceUri,
            LocalDate measurementDate, String hardware, String operatingSystem, String javaRuntime,
            String implementationVersion, String methodology, String measurementUnit) {
        public MeasurementProvenance {
            requireText(sourceDescription, "sourceDescription");
            Objects.requireNonNull(sourceUri, "sourceUri");
            Objects.requireNonNull(measurementDate, "measurementDate");
            requireText(hardware, "hardware");
            requireText(operatingSystem, "operatingSystem");
            requireText(javaRuntime, "javaRuntime");
            requireText(implementationVersion, "implementationVersion");
            requireText(methodology, "methodology");
            requireText(measurementUnit, "measurementUnit");
            sourceUri.ifPresent(uri -> {
                if (!uri.isAbsolute() || uri.getHost() == null
                        || !"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new IllegalArgumentException("sourceUri must be an absolute HTTPS URI with a host");
                }
            });
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            entry(AlgorithmId.ARGON2ID, "Argon2id", "Argon2id; version/provider not selected", List.of(
                    parameter("algorithmVersion", "version identifier", "Required; provider-supported versions unresolved."),
                    parameter("memoryBytes", "bytes", "Required positive value; provider-specific range unresolved."),
                    parameter("timeCost", "iterations", "Required positive value; provider-specific range unresolved."),
                    parameter("parallelism", "lanes/threads", "Required positive value; provider-specific range unresolved."),
                    parameter("saltLengthBytes", "bytes", "Required positive value; provider-specific limits unresolved."),
                    parameter("outputLengthBytes", "bytes", "Required positive value; provider-specific limits unresolved."),
                    parameter("provider", "name and version", "Required; no provider selected."))),
            entry(AlgorithmId.BCRYPT, "bcrypt", "bcrypt; provider/version not selected", List.of(
                    parameter("workFactor", "cost factor", "Required positive value; supported range is provider-specific and unresolved."),
                    parameter("saltHandling", "handling mode", "Required explicit choice; no salt default is substituted."),
                    parameter("saltLengthBytes", "bytes", "Required positive value; selected implementation constraints apply."),
                    parameter("outputFormat", "format identifier", "Required explicit format; no format is assumed."),
                    parameter("provider", "name and version", "Required; no provider selected."))),
            entry(AlgorithmId.SCRYPT, "scrypt", "scrypt; provider/version not selected", List.of(
                    parameter("N", "dimensionless cost parameter", "Required power of two greater than 1."),
                    parameter("r", "block-size parameter", "Required positive value; provider-specific range unresolved."),
                    parameter("p", "parallelization parameter", "Required positive value; provider-specific range unresolved."),
                    parameter("memoryCostBytes", "bytes", "Required positive memory context; no value is assumed."),
                    parameter("memoryCostContext", "description", "Required explanation of the memory-cost value."),
                    parameter("saltLengthBytes", "bytes", "Required positive value; provider-specific limits unresolved."),
                    parameter("outputLengthBytes", "bytes", "Required positive value; provider-specific limits unresolved."),
                    parameter("provider", "name and version", "Required; no provider selected."))),
            entry(AlgorithmId.PBKDF2, "PBKDF2", "PBKDF2; PRF/provider/version not selected", List.of(
                    parameter("prfHashVariant", "algorithm identifier", "Required exact PRF/hash variant."),
                    parameter("iterationCount", "iterations", "Required positive value; provider-specific range unresolved."),
                    parameter("saltLengthBytes", "bytes", "Required positive value; provider-specific limits unresolved."),
                    parameter("outputLengthBytes", "bytes", "Required positive value; provider-specific limits unresolved."),
                    parameter("provider", "name and version", "Required; no provider selected."))),
            new Entry(AlgorithmId.UNSPECIFIED_SHA3_KDF, "SHA-3-based KDF",
                    "No concrete password-KDF construction is identified",
                    List.of(parameter("construction", "algorithm identifier",
                                    "Unsupported until a concrete password KDF is selected and specified."),
                            parameter("parameters", "algorithm-specific",
                                    "Unsupported until requirements for a concrete KDF are known.")),
                    SupportStatus.UNSUPPORTED_OR_UNDERSPECIFIED,
                    "A plain SHA-3 digest is not a password-storage KDF; no construction is defined."));

    private PasswordKdfCostDataset() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> find(AlgorithmId algorithmId) {
        Objects.requireNonNull(algorithmId, "algorithmId");
        return ENTRIES.stream().filter(entry -> entry.algorithmId() == algorithmId).findFirst();
    }

    public static List<MeasurementObservation> observations() {
        return List.of();
    }

    static MeasurementObservation measurementObservation(KdfVerificationBenchmark.Result result) {
        Objects.requireNonNull(result, "result");
        KdfBenchmarkStatistics statistics = result.statistics();
        BigDecimal nanosecondsPerMillisecond = BigDecimal.valueOf(1_000_000);
        BigDecimal medianMillis = BigDecimal.valueOf(statistics.medianNanoseconds())
                .divide(nanosecondsPerMillisecond, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal p90Millis = BigDecimal.valueOf(statistics.p90Nanoseconds())
                .divide(nanosecondsPerMillisecond, 6, java.math.RoundingMode.HALF_UP);
        MeasurementProvenance provenance = new MeasurementProvenance(
                "Local verification-cost benchmark completed by KdfVerificationBenchmark",
                Optional.empty(),
                result.completedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                "unavailable (CPU identifier not collected)",
                result.operatingSystem() + "; " + result.architecture(),
                result.javaRuntime(),
                result.providerVersion(),
                "Runtime-generated local fixture; " + result.settings().warmupCount()
                        + " warm-ups; " + statistics.sampleCount() + " timed verifications; System.nanoTime()",
                "milliseconds per verification");
        return new MeasurementObservation(algorithmIdFor(result.configuration()), result.configuration(),
                medianMillis, p90Millis, statistics.sampleCount(), provenance);
    }

    public static ConfigurationValidation validate(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new ConfigurationValidation(
                ValidationStatus.GENERIC_INPUTS_VALID_PROVIDER_SPECIFIC_LIMITS_UNRESOLVED,
                List.of("Supported parameter ranges and combinations depend on the selected provider, "
                        + "which is not selected or identified by this catalog."));
    }

    private static Entry entry(AlgorithmId id, String name, String variant,
            List<ParameterRequirement> parameters) {
        return new Entry(id, name, variant, parameters,
                SupportStatus.CONFIGURATION_SCHEMA_DEFINED_PROVIDER_NOT_SELECTED,
                "Configuration fields are defined, but no implementation/provider or version is selected; "
                        + "provider-specific supported ranges remain unresolved. No measurements are available.");
    }

    private static ParameterRequirement parameter(String name, String unit, String rule) {
        return new ParameterRequirement(name, unit, rule);
    }

    private static AlgorithmId algorithmIdFor(Configuration configuration) {
        if (configuration instanceof Argon2idConfiguration) {
            return AlgorithmId.ARGON2ID;
        }
        if (configuration instanceof BcryptConfiguration) {
            return AlgorithmId.BCRYPT;
        }
        if (configuration instanceof ScryptConfiguration) {
            return AlgorithmId.SCRYPT;
        }
        if (configuration instanceof Pbkdf2Configuration) {
            return AlgorithmId.PBKDF2;
        }
        throw new IllegalArgumentException("configuration type is unsupported");
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
