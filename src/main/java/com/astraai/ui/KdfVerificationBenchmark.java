package com.astraai.ui;

import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Instant;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.bouncycastle.crypto.generators.SCrypt;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Explicit, bounded local KDF verification benchmark. This class has no JavaFX dependency.
 */
public final class KdfVerificationBenchmark {
    private static final String BOUNCY_CASTLE_NAME = "Bouncy Castle bcprov-jdk18on";
    private static final String BOUNCY_CASTLE_VERSION = "1.86";
    private static final int MAX_WARMUPS = 5;
    private static final int MAX_SAMPLES = 31;
    private static final long MIN_DURATION_MILLIS = 1_000;
    private static final long MAX_DURATION_MILLIS = 60_000;
    private static final long MAX_MEMORY_BYTES = 256L * 1024 * 1024;
    private static final int MAX_COST_ITERATIONS = 2_000_000;
    private static final int MAX_OUTPUT_BYTES = 1024;
    private static final int MAX_SALT_BYTES = 1024;
    private static final String PASSWORD_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private KdfVerificationBenchmark() {
    }

    public record Settings(int warmupCount, int sampleCount, long maximumDurationMillis) {
        public Settings {
            if (warmupCount < 1 || warmupCount > MAX_WARMUPS) {
                throw new IllegalArgumentException("warmup-count must be between 1 and " + MAX_WARMUPS);
            }
            if (sampleCount < 3 || sampleCount > MAX_SAMPLES) {
                throw new IllegalArgumentException("sample-count must be between 3 and " + MAX_SAMPLES);
            }
            if (maximumDurationMillis < MIN_DURATION_MILLIS
                    || maximumDurationMillis > MAX_DURATION_MILLIS) {
                throw new IllegalArgumentException("max-duration-ms must be between "
                        + MIN_DURATION_MILLIS + " and " + MAX_DURATION_MILLIS);
            }
        }
    }

    public record RuntimeMetadata(String javaRuntime, String operatingSystem, String architecture,
            String providerVersion, String cpuIdentifier, boolean jvmInputArgumentsAvailable,
            List<String> jvmInputArguments) {
        public RuntimeMetadata(String javaRuntime, String operatingSystem, String architecture,
                String providerVersion) {
            this(javaRuntime, operatingSystem, architecture, providerVersion,
                    "unavailable (no reliable portable local CPU identifier)", false, List.of());
        }

        public RuntimeMetadata {
            requireMetadata(javaRuntime, "javaRuntime");
            requireMetadata(operatingSystem, "operatingSystem");
            requireMetadata(architecture, "architecture");
            requireMetadata(providerVersion, "providerVersion");
            requireMetadata(cpuIdentifier, "cpuIdentifier");
            jvmInputArguments = List.copyOf(Objects.requireNonNull(jvmInputArguments, "jvmInputArguments"));
        }

        private static RuntimeMetadata capture(String providerVersion) {
            List<String> jvmArguments;
            boolean jvmArgumentsAvailable;
            try {
                jvmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
                jvmArgumentsAvailable = jvmArguments != null;
                if (jvmArguments == null) {
                    jvmArguments = List.of();
                }
            } catch (SecurityException | UnsupportedOperationException exception) {
                jvmArguments = List.of();
                jvmArgumentsAvailable = false;
            }
            return new RuntimeMetadata(
                    systemProperty("java.runtime.name") + " " + systemProperty("java.runtime.version"),
                    systemProperty("os.name") + " " + systemProperty("os.version"),
                    systemProperty("os.arch"),
                    providerVersion,
                    "unavailable (no reliable portable local CPU identifier)",
                    jvmArgumentsAvailable, jvmArguments);
        }

        private static String systemProperty(String name) {
            try {
                String value = System.getProperty(name);
                return value == null || value.isBlank() ? "unavailable" : value;
            } catch (SecurityException exception) {
                return "unavailable";
            }
        }
    }

    /** Created only after setup, every verification, all samples, and aggregation succeed. */
    public static final class Result {
        private final PasswordKdfCostDataset.Configuration configuration;
        private final KdfBenchmarkStatistics statistics;
        private final Instant completedAt;
        private final long elapsedMillis;
        private final long elapsedNanoseconds;
        private final Settings settings;
        private final RuntimeMetadata runtimeMetadata;

        private Result(PasswordKdfCostDataset.Configuration configuration,
                KdfBenchmarkStatistics statistics, Instant completedAt, long elapsedMillis,
                long elapsedNanoseconds, Settings settings, RuntimeMetadata runtimeMetadata) {
            this.configuration = configuration;
            this.statistics = statistics;
            this.completedAt = completedAt;
            this.elapsedMillis = elapsedMillis;
            this.elapsedNanoseconds = elapsedNanoseconds;
            this.settings = settings;
            this.runtimeMetadata = runtimeMetadata;
        }

        public PasswordKdfCostDataset.Configuration configuration() {
            return configuration;
        }

        public KdfBenchmarkStatistics statistics() {
            return statistics;
        }

        public Instant completedAt() {
            return completedAt;
        }

        public long elapsedMillis() {
            return elapsedMillis;
        }

        public long elapsedNanoseconds() {
            return elapsedNanoseconds;
        }

        public Settings settings() {
            return settings;
        }

        public String javaRuntime() {
            return runtimeMetadata.javaRuntime();
        }

        public String operatingSystem() {
            return runtimeMetadata.operatingSystem();
        }

        public String architecture() {
            return runtimeMetadata.architecture();
        }

        public String providerVersion() {
            return runtimeMetadata.providerVersion();
        }

        public RuntimeMetadata runtimeMetadata() {
            return runtimeMetadata;
        }

        public PasswordKdfCostDataset.MeasurementObservation observation() {
            return PasswordKdfCostDataset.measurementObservation(this);
        }
    }

    @FunctionalInterface
    private interface Verifier {
        boolean verify() throws Exception;
    }

    public static Result run(PasswordKdfCostDataset.Configuration configuration, Settings settings) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(settings, "settings");
        PasswordKdfCostDataset.validate(configuration);
        String actualProviderVersion = validateSupportedConfiguration(configuration);
        long startNanos = System.nanoTime();
        char[] password = createRandomPassword();
        byte[] salt = new byte[saltLength(configuration)];
        new SecureRandom().nextBytes(salt);
        byte[] passwordBytes = toAsciiBytes(password);
        byte[] expected = null;
        Argon2Parameters argonParameters = null;
        try {
            Verifier verifier;
            if (configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration argon) {
                argonParameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withMemoryAsKB(Math.toIntExact(argon.memoryBytes() / 1024))
                        .withIterations(argon.timeCost())
                        .withParallelism(argon.parallelism())
                        .withSalt(salt)
                        .build();
                Argon2Parameters parameters = argonParameters;
                expected = new byte[argon.outputLengthBytes()];
                deriveArgon2(passwordBytes, expected, parameters);
                byte[] storedHash = expected;
                verifier = () -> {
                    byte[] actual = new byte[argon.outputLengthBytes()];
                    try {
                        deriveArgon2(passwordBytes, actual, parameters);
                        return MessageDigest.isEqual(storedHash, actual);
                    } finally {
                        Arrays.fill(actual, (byte) 0);
                    }
                };
            } else if (configuration instanceof PasswordKdfCostDataset.BcryptConfiguration bcrypt) {
                String encodedHash = OpenBSDBCrypt.generate(password, salt, bcrypt.workFactor());
                verifier = () -> OpenBSDBCrypt.checkPassword(encodedHash, password);
            } else if (configuration instanceof PasswordKdfCostDataset.ScryptConfiguration scrypt) {
                expected = SCrypt.generate(passwordBytes, salt, Math.toIntExact(scrypt.n()),
                        scrypt.r(), scrypt.p(), scrypt.outputLengthBytes());
                byte[] storedHash = expected;
                verifier = () -> {
                    byte[] actual = SCrypt.generate(passwordBytes, salt, Math.toIntExact(scrypt.n()),
                            scrypt.r(), scrypt.p(), scrypt.outputLengthBytes());
                    try {
                        return MessageDigest.isEqual(storedHash, actual);
                    } finally {
                        Arrays.fill(actual, (byte) 0);
                    }
                };
            } else if (configuration instanceof PasswordKdfCostDataset.Pbkdf2Configuration pbkdf2) {
                String factoryAlgorithm = "PBKDF2With" + pbkdf2.prfHashVariant();
                SecretKeyFactory factory = SecretKeyFactory.getInstance(factoryAlgorithm, pbkdf2.provider().name());
                expected = derivePbkdf2(factory, password, salt, pbkdf2);
                byte[] storedHash = expected;
                verifier = () -> {
                    byte[] actual = derivePbkdf2(factory, password, salt, pbkdf2);
                    try {
                        return MessageDigest.isEqual(storedHash, actual);
                    } finally {
                        Arrays.fill(actual, (byte) 0);
                    }
                };
            } else {
                throw new IllegalArgumentException("No local verifier is implemented for this configuration");
            }

            ensureWithinDeadline(startNanos, settings);
            List<Long> samples = new ArrayList<>(settings.sampleCount());
            for (int i = 0; i < settings.warmupCount(); i++) {
                ensureWithinDeadline(startNanos, settings);
                if (!verifier.verify()) {
                    throw new IllegalStateException("Known local test fixture verification failed during warm-up");
                }
            }
            for (int i = 0; i < settings.sampleCount(); i++) {
                ensureWithinDeadline(startNanos, settings);
                long sampleStart = System.nanoTime();
                boolean verified = verifier.verify();
                long elapsed = System.nanoTime() - sampleStart;
                if (!verified) {
                    throw new IllegalStateException("Known local test fixture verification failed");
                }
                if (elapsed <= 0) {
                    throw new IllegalStateException("Monotonic timer returned a nonpositive sample");
                }
                samples.add(elapsed);
            }
            ensureWithinDeadline(startNanos, settings);
            KdfBenchmarkStatistics statistics = KdfBenchmarkStatistics.fromSamples(samples);
            ensureWithinDeadline(startNanos, settings);
            long totalElapsed = System.nanoTime() - startNanos;
            return new Result(configuration, statistics,
                    Instant.now(), TimeUnit.NANOSECONDS.toMillis(totalElapsed), totalElapsed, settings,
                    RuntimeMetadata.capture(actualProviderVersion));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Local KDF verification benchmark failed ("
                    + exception.getClass().getSimpleName() + ")");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(passwordBytes, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            if (expected != null) {
                Arrays.fill(expected, (byte) 0);
            }
            if (argonParameters != null) {
                argonParameters.clear();
            }
        }
    }

    /** Captures provider and runtime metadata without performing a KDF operation. */
    public static RuntimeMetadata currentRuntimeMetadata(
            PasswordKdfCostDataset.Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        String actualProviderVersion = validateSupportedConfiguration(configuration);
        return RuntimeMetadata.capture(actualProviderVersion);
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            printUsage();
            return;
        }
        Map<String, String> options = null;
        java.nio.file.Path reportPath = null;
        try {
            options = parseOptions(args);
            PasswordKdfCostDataset.Configuration configuration = configurationFrom(options);
            Settings settings = new Settings(
                    integer(options, "warmup-count"),
                    integer(options, "sample-count"),
                    longValue(options, "max-duration-ms"));
            Result result = run(configuration, settings);
            KdfBenchmarkReport report = KdfBenchmarkReport.completed(result);
            reportPath = outputPath(options, result.completedAt(), result.configuration());
            report.writeNew(reportPath);
            printResult(result, reportPath);
        } catch (RuntimeException exception) {
            String message = "Benchmark unavailable or failed: " + safeFailureReason(exception);
            System.err.println(message);
            try {
                Instant failureTime = Instant.now();
                java.nio.file.Path outputPath = outputPath(options, failureTime, null);
                KdfBenchmarkReport.failed(failureTime, safeFailureReason(exception)).writeNew(outputPath);
                System.err.println("Failure report written to: " + outputPath.toAbsolutePath().normalize());
            } catch (Exception outputException) {
                System.err.println("Could not write failure report: " + safeOutputFailure(outputException));
            }
            System.exit(2);
        } catch (java.io.IOException exception) {
            System.err.println("Could not write benchmark report"
                    + (reportPath == null ? "" : " to " + reportPath.toAbsolutePath().normalize())
                    + ": " + safeOutputFailure(exception));
            System.exit(2);
        }
    }

    static String validateSupportedConfiguration(
            PasswordKdfCostDataset.Configuration configuration) {
        if (configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration argon) {
            validateBouncyCastle(argon.provider());
            if (!"1.3".equals(argon.algorithmVersion())) {
                throw new IllegalArgumentException("Bouncy Castle benchmark supports only Argon2 version 1.3");
            }
            if (argon.memoryBytes() % 1024 != 0 || argon.memoryBytes() > MAX_MEMORY_BYTES
                    || argon.timeCost() > 6 || argon.parallelism() > 8
                    || argon.saltLengthBytes() > MAX_SALT_BYTES
                    || argon.outputLengthBytes() > MAX_OUTPUT_BYTES) {
                throw new IllegalArgumentException("Argon2id configuration exceeds benchmark safety limits");
            }
            if (argon.memoryBytes() < 8L * 1024 * argon.parallelism()) {
                throw new IllegalArgumentException("Argon2id memory must be at least 8 KiB per lane");
            }
            return BOUNCY_CASTLE_VERSION;
        }
        if (configuration instanceof PasswordKdfCostDataset.BcryptConfiguration bcrypt) {
            validateBouncyCastle(bcrypt.provider());
            if (bcrypt.workFactor() < 4 || bcrypt.workFactor() > 16) {
                throw new IllegalArgumentException("bcrypt benchmark work factor must be between 4 and 16");
            }
            if (bcrypt.saltLengthBytes() != 16) {
                throw new IllegalArgumentException("Bouncy Castle bcrypt requires a 16-byte salt");
            }
            if (bcrypt.saltHandling()
                    != PasswordKdfCostDataset.SaltHandling.GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT) {
                throw new IllegalArgumentException("bcrypt benchmark requires salt embedded in its encoded hash");
            }
            if (!"OpenBSDBCrypt-2y-60-char".equals(bcrypt.outputFormat())) {
                throw new IllegalArgumentException("bcrypt output format must be OpenBSDBCrypt-2y-60-char");
            }
            return BOUNCY_CASTLE_VERSION;
        }
        if (configuration instanceof PasswordKdfCostDataset.ScryptConfiguration scrypt) {
            validateBouncyCastle(scrypt.provider());
            long requiredMemory;
            try {
                requiredMemory = Math.multiplyExact(Math.multiplyExact(128L, scrypt.n()), scrypt.r());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("scrypt memory calculation overflows supported integer range");
            }
            if (requiredMemory != scrypt.memoryCostBytes()) {
                throw new IllegalArgumentException("scrypt memoryCostBytes must equal 128 * N * r");
            }
            if (requiredMemory > MAX_MEMORY_BYTES || scrypt.p() > 4
                    || scrypt.saltLengthBytes() > MAX_SALT_BYTES
                    || scrypt.outputLengthBytes() > MAX_OUTPUT_BYTES) {
                throw new IllegalArgumentException("scrypt configuration exceeds benchmark safety limits");
            }
            return BOUNCY_CASTLE_VERSION;
        }
        if (configuration instanceof PasswordKdfCostDataset.Pbkdf2Configuration pbkdf2) {
            if (pbkdf2.iterationCount() > MAX_COST_ITERATIONS
                    || pbkdf2.saltLengthBytes() > MAX_SALT_BYTES
                    || pbkdf2.outputLengthBytes() > MAX_OUTPUT_BYTES) {
                throw new IllegalArgumentException("PBKDF2 configuration exceeds benchmark safety limits");
            }
            Provider provider = Security.getProvider(pbkdf2.provider().name());
            if (provider == null || !provider.getVersionStr().equals(pbkdf2.provider().version())) {
                throw new IllegalArgumentException("Selected PBKDF2 provider is not installed at the stated version");
            }
            try {
                SecretKeyFactory.getInstance("PBKDF2With" + pbkdf2.prfHashVariant(), provider);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Selected provider does not support the requested PBKDF2 PRF");
            }
            return provider.getVersionStr();
        }
        throw new IllegalArgumentException("Unsupported KDF configuration; SHA-3-based KDF is unavailable");
    }

    private static void validateBouncyCastle(PasswordKdfCostDataset.Provider provider) {
        if (!BOUNCY_CASTLE_NAME.equals(provider.name())
                || !BOUNCY_CASTLE_VERSION.equals(provider.version())) {
            throw new IllegalArgumentException("This benchmark requires "
                    + BOUNCY_CASTLE_NAME + " version " + BOUNCY_CASTLE_VERSION);
        }
    }

    private static byte[] derivePbkdf2(SecretKeyFactory factory, char[] password, byte[] salt,
            PasswordKdfCostDataset.Pbkdf2Configuration configuration) throws Exception {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, configuration.iterationCount(),
                Math.multiplyExact(configuration.outputLengthBytes(), Byte.SIZE));
        try {
            return factory.generateSecret(keySpec).getEncoded();
        } finally {
            keySpec.clearPassword();
        }
    }

    private static void deriveArgon2(byte[] password, byte[] output, Argon2Parameters parameters) {
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        generator.generateBytes(password, output);
    }

    private static int saltLength(PasswordKdfCostDataset.Configuration configuration) {
        if (configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration argon) {
            return argon.saltLengthBytes();
        }
        if (configuration instanceof PasswordKdfCostDataset.BcryptConfiguration bcrypt) {
            return bcrypt.saltLengthBytes();
        }
        if (configuration instanceof PasswordKdfCostDataset.ScryptConfiguration scrypt) {
            return scrypt.saltLengthBytes();
        }
        return ((PasswordKdfCostDataset.Pbkdf2Configuration) configuration).saltLengthBytes();
    }

    private static char[] createRandomPassword() {
        SecureRandom random = new SecureRandom();
        char[] password = new char[32];
        for (int i = 0; i < password.length; i++) {
            password[i] = PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length()));
        }
        return password;
    }

    private static byte[] toAsciiBytes(char[] password) {
        byte[] bytes = new byte[password.length];
        for (int i = 0; i < password.length; i++) {
            bytes[i] = (byte) password[i];
        }
        return bytes;
    }

    private static void ensureWithinDeadline(long startNanos, Settings settings) {
        long maximumNanos = TimeUnit.MILLISECONDS.toNanos(settings.maximumDurationMillis());
        if (System.nanoTime() - startNanos >= maximumNanos) {
            throw new IllegalStateException("Benchmark exceeded max-duration-ms; no observation was recorded");
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String argument : args) {
            if (!argument.startsWith("--") || !argument.contains("=")) {
                throw new IllegalArgumentException("Options must use --name=value syntax");
            }
            int separator = argument.indexOf('=');
            String key = argument.substring(2, separator);
            String value = argument.substring(separator + 1);
            if (key.isBlank() || value.isBlank() || options.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Option is blank or repeated: " + key);
            }
        }
        return options;
    }

    static PasswordKdfCostDataset.Configuration configurationFrom(Map<String, String> options) {
        String algorithm = required(options, "algorithm");
        validateOptionNames(options, algorithm);
        return switch (algorithm) {
            case "ARGON2ID" -> new PasswordKdfCostDataset.Argon2idConfiguration(
                    required(options, "version"), longValue(options, "memory-bytes"),
                    integer(options, "time-cost"), integer(options, "parallelism"),
                    integer(options, "salt-length-bytes"), integer(options, "output-length-bytes"),
                    bouncyCastleProvider());
            case "BCRYPT" -> new PasswordKdfCostDataset.BcryptConfiguration(
                    integer(options, "work-factor"),
                    PasswordKdfCostDataset.SaltHandling.valueOf(required(options, "salt-handling")),
                    integer(options, "salt-length-bytes"),
                    required(options, "output-format"), bouncyCastleProvider());
            case "SCRYPT" -> new PasswordKdfCostDataset.ScryptConfiguration(
                    longValue(options, "N"), integer(options, "r"), integer(options, "p"),
                    longValue(options, "memory-bytes"), required(options, "memory-context"),
                    integer(options, "salt-length-bytes"), integer(options, "output-length-bytes"),
                    bouncyCastleProvider());
            case "PBKDF2" -> {
                String providerName = required(options, "provider-name");
                Provider provider = Security.getProvider(providerName);
                if (provider == null) {
                    throw new IllegalArgumentException("PBKDF2 provider is not installed: " + providerName);
                }
                yield new PasswordKdfCostDataset.Pbkdf2Configuration(
                        required(options, "prf-hash-variant"), integer(options, "iterations"),
                        integer(options, "salt-length-bytes"), integer(options, "output-length-bytes"),
                        new PasswordKdfCostDataset.Provider(provider.getName(), provider.getVersionStr()));
            }
            default -> throw new IllegalArgumentException(
                    "Supported algorithms are ARGON2ID, BCRYPT, SCRYPT, and PBKDF2; SHA-3 is unsupported");
        };
    }

    private static PasswordKdfCostDataset.Provider bouncyCastleProvider() {
        return new PasswordKdfCostDataset.Provider(BOUNCY_CASTLE_NAME, BOUNCY_CASTLE_VERSION);
    }

    private static void validateOptionNames(Map<String, String> options, String algorithm) {
        List<String> algorithmOptions = switch (algorithm) {
            case "ARGON2ID" -> List.of("version", "memory-bytes", "time-cost", "parallelism",
                    "salt-length-bytes", "output-length-bytes");
            case "BCRYPT" -> List.of(
                    "work-factor", "salt-handling", "salt-length-bytes", "output-format");
            case "SCRYPT" -> List.of("N", "r", "p", "memory-bytes", "memory-context",
                    "salt-length-bytes", "output-length-bytes");
            case "PBKDF2" -> List.of("prf-hash-variant", "iterations", "salt-length-bytes",
                    "output-length-bytes", "provider-name");
            default -> throw new IllegalArgumentException(
                    "Supported algorithms are ARGON2ID, BCRYPT, SCRYPT, and PBKDF2; SHA-3 is unsupported");
        };
        List<String> allowed = new ArrayList<>(algorithmOptions);
        allowed.addAll(List.of("algorithm", "warmup-count", "sample-count", "max-duration-ms", "output"));
        for (String key : options.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Option --" + key + " is not valid for " + algorithm);
            }
        }
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required option is missing: --" + key);
        }
        return value;
    }

    private static int integer(Map<String, String> options, String key) {
        try {
            return Integer.parseInt(required(options, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option --" + key + " must be an integer");
        }
    }

    private static long longValue(Map<String, String> options, String key) {
        try {
            return Long.parseLong(required(options, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option --" + key + " must be an integer");
        }
    }

    private static void printResult(Result result, java.nio.file.Path outputPath) {
        KdfBenchmarkStatistics statistics = result.statistics();
        System.out.println("LOCAL KDF VERIFICATION-COST OBSERVATION — machine-specific, not universal");
        System.out.println("Status: COMPLETED");
        System.out.println("Algorithm configuration: " + result.configuration());
        System.out.println("Completed at: " + result.completedAt());
        System.out.println("Provider version: " + result.providerVersion());
        System.out.println("Java runtime: " + result.javaRuntime());
        System.out.println("OS: " + result.operatingSystem());
        System.out.println("Architecture: " + result.architecture());
        System.out.println("CPU identifier: " + result.runtimeMetadata().cpuIdentifier());
        System.out.println("JVM input arguments: "
                + (result.runtimeMetadata().jvmInputArgumentsAvailable()
                        ? result.runtimeMetadata().jvmInputArguments() : "unavailable"));
        System.out.println("Method: runtime-generated local fixture; verify only known fixture; System.nanoTime()");
        System.out.println("Warm-up count: " + result.settings().warmupCount());
        System.out.println("Sample count: " + statistics.sampleCount());
        System.out.println("Median verification latency (ns): " + statistics.medianNanoseconds());
        System.out.println("P90 verification latency (ns): " + statistics.p90Nanoseconds());
        System.out.println("Measured duration (ns): " + result.elapsedNanoseconds()
                + " (fixture generation, initial derivation, warm-ups, timed verifications, and aggregation)");
        System.out.println("Structured report: " + outputPath.toAbsolutePath().normalize());
        System.out.println("Fixture password, salt, and derived hash were not recorded.");
    }

    private static void printUsage() {
        System.out.println("Pass required --name=value options for one supported algorithm.");
        System.out.println("Common: --algorithm=ARGON2ID|BCRYPT|SCRYPT|PBKDF2 "
                + "--warmup-count=1..5 --sample-count=3..31 --max-duration-ms=1000..60000");
        System.out.println("SHA-3-based KDF is unsupported. No values are inferred or defaulted.");
    }

    private static void requireMetadata(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be available or explicitly marked unknown");
        }
    }

    static java.nio.file.Path outputPath(Map<String, String> options, Instant timestamp,
            PasswordKdfCostDataset.Configuration configuration) {
        String specifiedPath = options == null ? null : options.get("output");
        if (specifiedPath != null && !specifiedPath.isBlank()) {
            return java.nio.file.Path.of(specifiedPath);
        }
        String algorithm = configuration == null ? "unavailable" : algorithmName(configuration);
        String time = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
                .withZone(java.time.ZoneOffset.UTC).format(timestamp);
        return java.nio.file.Path.of("kdf-verification-" + algorithm + "-" + time + ".json");
    }

    private static String algorithmName(PasswordKdfCostDataset.Configuration configuration) {
        if (configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration) {
            return "argon2id";
        }
        if (configuration instanceof PasswordKdfCostDataset.BcryptConfiguration) {
            return "bcrypt";
        }
        if (configuration instanceof PasswordKdfCostDataset.ScryptConfiguration) {
            return "scrypt";
        }
        if (configuration instanceof PasswordKdfCostDataset.Pbkdf2Configuration) {
            return "pbkdf2";
        }
        return "unavailable";
    }

    private static String safeFailureReason(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            return "Invalid, incomplete, or unsupported configuration; no benchmark observation was recorded.";
        }
        return "Benchmark failed before all samples completed; no successful observation was recorded.";
    }

    private static String safeOutputFailure(Exception exception) {
        if (exception instanceof java.nio.file.FileAlreadyExistsException) {
            return "the path already exists and was not overwritten";
        }
        if (exception instanceof java.nio.file.NoSuchFileException) {
            return "the output directory or path does not exist";
        }
        return "output failed (" + exception.getClass().getSimpleName() + ")";
    }
}
