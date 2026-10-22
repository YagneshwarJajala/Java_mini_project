package com.astraai.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.DateTimeException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned JSON representation of a completed local observation or a failed run. */
public final class KdfBenchmarkReport {
    public static final String SCHEMA_VERSION = "1.0";
    private static final String UNAVAILABLE = "unavailable";

    public enum Status {
        COMPLETED,
        FAILED,
        TEST_FIXTURE
    }

    private final Status status;
    private final String failureReason;
    private final Instant timestamp;
    private final String algorithm;
    private final String variant;
    private final Map<String, Object> parameters;
    private final String providerName;
    private final String providerVersion;
    private final String javaRuntimeName;
    private final String javaRuntimeVersion;
    private final String operatingSystemName;
    private final String operatingSystemVersion;
    private final String architecture;
    private final String cpuIdentifier;
    private final boolean jvmArgumentsAvailable;
    private final List<String> jvmInputArguments;
    private final Integer warmupCount;
    private final Integer sampleCount;
    private final Long measuredDurationNanoseconds;
    private final String measuredDurationDefinition;
    private final Long medianVerificationLatencyNanoseconds;
    private final Long p90VerificationLatencyNanoseconds;
    private final Long maximumDurationMillis;
    private final List<String> limitations;

    private KdfBenchmarkReport(Status status, String failureReason, Instant timestamp,
            String algorithm, String variant, Map<String, Object> parameters,
            String providerName, String providerVersion, String javaRuntimeName,
            String javaRuntimeVersion, String operatingSystemName, String operatingSystemVersion,
            String architecture, String cpuIdentifier, boolean jvmArgumentsAvailable,
            List<String> jvmInputArguments, Integer warmupCount, Integer sampleCount,
            Long measuredDurationNanoseconds, String measuredDurationDefinition,
            Long medianVerificationLatencyNanoseconds, Long p90VerificationLatencyNanoseconds,
            Long maximumDurationMillis, List<String> limitations) {
        this.status = Objects.requireNonNull(status, "status");
        this.failureReason = failureReason;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.algorithm = requireText(algorithm, "algorithm");
        this.variant = requireText(variant, "variant");
        this.parameters = java.util.Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(parameters, "parameters")));
        this.providerName = requireText(providerName, "providerName");
        this.providerVersion = requireText(providerVersion, "providerVersion");
        this.javaRuntimeName = requireText(javaRuntimeName, "javaRuntimeName");
        this.javaRuntimeVersion = requireText(javaRuntimeVersion, "javaRuntimeVersion");
        this.operatingSystemName = requireText(operatingSystemName, "operatingSystemName");
        this.operatingSystemVersion = requireText(operatingSystemVersion, "operatingSystemVersion");
        this.architecture = requireText(architecture, "architecture");
        this.cpuIdentifier = requireText(cpuIdentifier, "cpuIdentifier");
        this.jvmArgumentsAvailable = jvmArgumentsAvailable;
        this.jvmInputArguments = List.copyOf(Objects.requireNonNull(jvmInputArguments, "jvmInputArguments"));
        this.warmupCount = warmupCount;
        this.sampleCount = sampleCount;
        this.measuredDurationNanoseconds = measuredDurationNanoseconds;
        this.measuredDurationDefinition = requireText(measuredDurationDefinition, "measuredDurationDefinition");
        this.medianVerificationLatencyNanoseconds = medianVerificationLatencyNanoseconds;
        this.p90VerificationLatencyNanoseconds = p90VerificationLatencyNanoseconds;
        this.maximumDurationMillis = maximumDurationMillis;
        this.limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations"));
        if (status == Status.COMPLETED && (failureReason != null || sampleCount == null
                || measuredDurationNanoseconds == null || medianVerificationLatencyNanoseconds == null
                || p90VerificationLatencyNanoseconds == null)) {
            throw new IllegalArgumentException("completed reports require all measurement fields and no failure reason");
        }
        if (status == Status.FAILED && (failureReason == null || failureReason.isBlank()
                || sampleCount != null || measuredDurationNanoseconds != null)) {
            throw new IllegalArgumentException("failed reports require a safe reason and cannot claim measurements");
        }
    }

    static KdfBenchmarkReport completed(KdfVerificationBenchmark.Result result) {
        Objects.requireNonNull(result, "result");
        PasswordKdfCostDataset.Configuration configuration = result.configuration();
        PasswordKdfCostDataset.Provider provider = configuration.provider();
        KdfVerificationBenchmark.RuntimeMetadata runtime = result.runtimeMetadata();
        String javaRuntime = runtime.javaRuntime();
        int javaSeparator = javaRuntime.lastIndexOf(' ');
        String javaName = javaSeparator < 0 ? javaRuntime : javaRuntime.substring(0, javaSeparator);
        String javaVersion = javaSeparator < 0 ? UNAVAILABLE : javaRuntime.substring(javaSeparator + 1);
        String os = runtime.operatingSystem();
        int osSeparator = os.lastIndexOf(' ');
        String osName = osSeparator < 0 ? os : os.substring(0, osSeparator);
        String osVersion = osSeparator < 0 ? UNAVAILABLE : os.substring(osSeparator + 1);
        return new KdfBenchmarkReport(Status.COMPLETED, null, result.completedAt(),
                algorithmName(configuration), variant(configuration), parameters(configuration),
                provider.name(), runtime.providerVersion(), javaName, javaVersion, osName, osVersion,
                runtime.architecture(), runtime.cpuIdentifier(), runtime.jvmInputArgumentsAvailable(),
                runtime.jvmInputArguments(), result.settings().warmupCount(),
                result.statistics().sampleCount(), result.elapsedNanoseconds(),
                "From fixture generation through initial hash derivation, warm-ups, timed verifications, "
                        + "and statistics aggregation; measured with System.nanoTime().",
                result.statistics().medianNanoseconds(), result.statistics().p90Nanoseconds(),
                result.settings().maximumDurationMillis(),
                List.of("Local verification-cost observation; not a universal performance claim.",
                        "Verification latency is not password-cracking time or attack throughput.",
                        "Results depend on hardware, JVM, provider, parameters, and run conditions.",
                        "The run deadline is checked between KDF operations; one operation can exceed it.",
                        "CPU identity is explicitly unavailable; fixture secrets are not recorded."));
    }

    static KdfBenchmarkReport failed(Instant timestamp, String safeFailureReason) {
        return new KdfBenchmarkReport(Status.FAILED, requireText(safeFailureReason, "safeFailureReason"),
                timestamp, UNAVAILABLE, UNAVAILABLE, Map.of(), UNAVAILABLE, UNAVAILABLE,
                UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE,
                false, List.of(), null, null, null,
                "No complete measurement; no duration is reported.", null, null, null,
                List.of("Failed or partial run; no successful observation was recorded.",
                        "Failure details are sanitized and do not include fixture values."));
    }

    static KdfBenchmarkReport testFixture(Instant timestamp,
            PasswordKdfCostDataset.Configuration configuration,
            KdfVerificationBenchmark.RuntimeMetadata runtime, KdfBenchmarkStatistics statistics) {
        String javaRuntime = runtime.javaRuntime();
        int javaSeparator = javaRuntime.lastIndexOf(' ');
        String javaName = javaSeparator < 0 ? javaRuntime : javaRuntime.substring(0, javaSeparator);
        String javaVersion = javaSeparator < 0 ? UNAVAILABLE : javaRuntime.substring(javaSeparator + 1);
        String operatingSystem = runtime.operatingSystem();
        int osSeparator = operatingSystem.lastIndexOf(' ');
        String osName = osSeparator < 0 ? operatingSystem : operatingSystem.substring(0, osSeparator);
        String osVersion = osSeparator < 0 ? UNAVAILABLE : operatingSystem.substring(osSeparator + 1);
        return new KdfBenchmarkReport(Status.TEST_FIXTURE, null, timestamp,
                algorithmName(configuration), variant(configuration), parameters(configuration),
                configuration.provider().name(), runtime.providerVersion(),
                javaName, javaVersion, osName, osVersion,
                runtime.architecture(), runtime.cpuIdentifier(), runtime.jvmInputArgumentsAvailable(),
                runtime.jvmInputArguments(), 1, statistics.sampleCount(), 123_456_789L,
                "TEST FIXTURE ONLY; not a benchmark observation.",
                statistics.medianNanoseconds(), statistics.p90Nanoseconds(), 10_000L,
                List.of("TEST FIXTURE ONLY; no KDF benchmark was run."));
    }

    public Status status() {
        return status;
    }

    static KdfBenchmarkReport parseCompletedJson(String json) {
        Map<String, Object> root = object(StrictJsonParser.parse(json), "report");
        if (!SCHEMA_VERSION.equals(text(root, "schemaVersion"))) {
            throw invalidReport();
        }
        if (!"COMPLETED".equals(text(root, "status"))) {
            throw invalidReport();
        }
        if (required(root, "failureReason") != null
                || !"local_kdf_verification_cost".equals(text(root, "observationType"))) {
            throw invalidReport();
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(text(root, "timestamp"));
        } catch (DateTimeException exception) {
            throw invalidReport();
        }
        String algorithm = text(root, "algorithm");
        String variant = text(root, "variant");
        Map<String, Object> parameters = object(required(root, "parameters"), "parameters");
        validateParameterShape(algorithm, parameters);

        Map<String, Object> provider = object(required(root, "provider"), "provider");
        String providerName = text(provider, "name");
        String providerVersion = text(provider, "version");
        if (isUnavailable(providerName) || isUnavailable(providerVersion)) {
            throw invalidReport();
        }

        Map<String, Object> javaRuntime = object(required(root, "javaRuntime"), "javaRuntime");
        String javaRuntimeName = knownMetadata(text(javaRuntime, "name"));
        String javaRuntimeVersion = knownMetadata(text(javaRuntime, "version"));
        Map<String, Object> operatingSystem = object(required(root, "operatingSystem"), "operatingSystem");
        String operatingSystemName = knownMetadata(text(operatingSystem, "name"));
        String operatingSystemVersion = knownMetadata(text(operatingSystem, "version"));
        String architecture = knownMetadata(text(operatingSystem, "architecture"));

        Map<String, Object> cpu = object(required(root, "cpuIdentifier"), "cpuIdentifier");
        String cpuAvailability = text(cpu, "availability");
        String cpuIdentifier = text(cpu, "value");
        if (!("available".equals(cpuAvailability) || "unavailable".equals(cpuAvailability))
                || ("unavailable".equals(cpuAvailability) && !isUnavailable(cpuIdentifier))
                || ("available".equals(cpuAvailability) && isUnavailable(cpuIdentifier))) {
            throw invalidReport();
        }
        Map<String, Object> jvm = object(required(root, "jvmInputArguments"), "jvmInputArguments");
        String jvmAvailability = text(jvm, "availability");
        List<String> jvmArguments = stringList(required(jvm, "values"), "jvmInputArguments.values");
        boolean jvmArgumentsAvailable;
        if ("unavailable".equals(jvmAvailability)) {
            if (!jvmArguments.equals(List.of(UNAVAILABLE))) {
                throw invalidReport();
            }
            jvmArgumentsAvailable = false;
        } else if ("available".equals(jvmAvailability)) {
            if (jvmArguments.stream().anyMatch(KdfBenchmarkReport::isUnavailable)) {
                throw invalidReport();
            }
            jvmArgumentsAvailable = true;
        } else {
            throw invalidReport();
        }

        int warmupCount = integer(root, "warmupCount");
        int sampleCount = integer(root, "sampleCount");
        long maximumDurationMillis = positiveLong(root, "maximumDurationMillis");
        try {
            new KdfVerificationBenchmark.Settings(warmupCount, sampleCount, maximumDurationMillis);
        } catch (IllegalArgumentException exception) {
            throw invalidReport();
        }
        Map<String, Object> measuredDuration = object(required(root, "measuredDuration"), "measuredDuration");
        long durationNanoseconds = positiveLong(measuredDuration, "value");
        if (!"nanoseconds".equals(text(measuredDuration, "unit"))) {
            throw invalidReport();
        }
        String durationDefinition = text(measuredDuration, "definition");
        if (!durationDefinition.contains("System.nanoTime")) {
            throw invalidReport();
        }
        long maximumNanoseconds;
        try {
            maximumNanoseconds = Math.multiplyExact(maximumDurationMillis, 1_000_000L);
        } catch (ArithmeticException exception) {
            throw invalidReport();
        }
        if (durationNanoseconds > maximumNanoseconds) {
            throw invalidReport();
        }
        Map<String, Object> latency = object(required(root, "verificationLatency"), "verificationLatency");
        long medianNanoseconds = positiveLong(latency, "median");
        long p90Nanoseconds = positiveLong(latency, "p90");
        if (p90Nanoseconds < medianNanoseconds
                || medianNanoseconds > durationNanoseconds
                || p90Nanoseconds > durationNanoseconds
                || !"nearest-rank".equals(text(latency, "p90Method"))
                || !"nanoseconds per verification".equals(text(latency, "unit"))) {
            throw invalidReport();
        }
        List<String> limitations = stringList(required(root, "limitations"), "limitations");
        if (limitations.isEmpty()) {
            throw invalidReport();
        }
        return new KdfBenchmarkReport(Status.COMPLETED, null, timestamp,
                algorithm, variant, parameters, providerName, providerVersion,
                javaRuntimeName, javaRuntimeVersion, operatingSystemName, operatingSystemVersion,
                architecture, cpuIdentifier, jvmArgumentsAvailable, jvmArguments,
                warmupCount, sampleCount, durationNanoseconds, durationDefinition,
                medianNanoseconds, p90Nanoseconds, maximumDurationMillis, limitations);
    }

    long medianVerificationLatencyNanoseconds() {
        return medianVerificationLatencyNanoseconds == null ? 0 : medianVerificationLatencyNanoseconds;
    }

    boolean matches(PasswordKdfCostDataset.Configuration configuration, String actualProviderVersion,
            KdfVerificationBenchmark.RuntimeMetadata runtime) {
        if (status != Status.COMPLETED || failureReason != null
                || configuration == null || actualProviderVersion == null || runtime == null
                || warmupCount == null || warmupCount < 1
                || sampleCount == null || sampleCount < 3
                || measuredDurationNanoseconds == null || measuredDurationNanoseconds <= 0
                || medianVerificationLatencyNanoseconds == null || medianVerificationLatencyNanoseconds <= 0
                || p90VerificationLatencyNanoseconds == null || p90VerificationLatencyNanoseconds <= 0
                || medianVerificationLatencyNanoseconds > measuredDurationNanoseconds
                || p90VerificationLatencyNanoseconds > measuredDurationNanoseconds
                || maximumDurationMillis == null || maximumDurationMillis <= 0
                || !matchesConfigurationAndRuntime(configuration, actualProviderVersion, runtime)) {
            return false;
        }
        return true;
    }

    boolean matchesConfigurationAndRuntime(PasswordKdfCostDataset.Configuration configuration,
            String actualProviderVersion, KdfVerificationBenchmark.RuntimeMetadata runtime) {
        if (configuration == null || actualProviderVersion == null || runtime == null) {
            return false;
        }
        String expectedJavaRuntime = javaRuntimeName + " " + javaRuntimeVersion;
        String expectedOperatingSystem = operatingSystemName + " " + operatingSystemVersion;
        return algorithm.equals(algorithmName(configuration))
                && variant.equals(variant(configuration))
                && jsonValuesEqual(parameters, parameters(configuration))
                && providerName.equals(configuration.provider().name())
                && providerVersion.equals(actualProviderVersion)
                && providerVersion.equals(configuration.provider().version())
                && expectedJavaRuntime.equals(runtime.javaRuntime())
                && expectedOperatingSystem.equals(runtime.operatingSystem())
                && architecture.equals(runtime.architecture())
                && cpuIdentifier.equals(runtime.cpuIdentifier())
                && jvmArgumentsAvailable == runtime.jvmInputArgumentsAvailable()
                && jvmInputArguments.equals(runtime.jvmInputArguments());
    }

    private static void validateParameterShape(String algorithm, Map<String, Object> parameters) {
        List<String> expected = switch (algorithm) {
            case "Argon2id" -> List.of("algorithmVersion", "memoryBytes", "timeCost", "parallelism",
                    "saltLengthBytes", "outputLengthBytes");
            case "bcrypt" -> List.of("workFactor", "saltHandling", "saltLengthBytes", "outputFormat");
            case "scrypt" -> List.of("N", "r", "p", "memoryCostBytes", "memoryCostContext",
                    "saltLengthBytes", "outputLengthBytes");
            case "PBKDF2" -> List.of("prfHashVariant", "iterationCount", "saltLengthBytes",
                    "outputLengthBytes");
            default -> throw invalidReport();
        };
        if (!parameters.keySet().equals(new java.util.LinkedHashSet<>(expected))) {
            throw invalidReport();
        }
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            if (List.of("algorithmVersion", "saltHandling", "outputFormat",
                    "memoryCostContext", "prfHashVariant").contains(parameter.getKey())) {
                if (!(parameter.getValue() instanceof String value) || value.isBlank()) {
                    throw invalidReport();
                }
            } else if (!(parameter.getValue() instanceof BigDecimal value)
                    || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
                throw invalidReport();
            }
        }
    }

    private static boolean jsonValuesEqual(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            try {
                return new BigDecimal(leftNumber.toString()).compareTo(new BigDecimal(rightNumber.toString())) == 0;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            if (!leftMap.keySet().equals(rightMap.keySet())) {
                return false;
            }
            for (Object key : leftMap.keySet()) {
                if (!jsonValuesEqual(leftMap.get(key), rightMap.get(key))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(left, right);
    }

    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalidReport();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalidReport();
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static Object required(Map<String, Object> values, String key) {
        if (!values.containsKey(key)) {
            throw invalidReport();
        }
        return values.get(key);
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = required(values, key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidReport();
        }
        return text;
    }

    private static String knownMetadata(String value) {
        if (isUnavailable(value) || "unknown".equalsIgnoreCase(value)
                || value.toLowerCase(java.util.Locale.ROOT).startsWith("unknown ")) {
            throw invalidReport();
        }
        return value;
    }

    private static boolean isUnavailable(String value) {
        return value.equalsIgnoreCase(UNAVAILABLE) || value.toLowerCase(java.util.Locale.ROOT)
                .startsWith(UNAVAILABLE + " ");
    }

    private static int integer(Map<String, Object> values, String key) {
        long value = positiveLong(values, key);
        if (value > Integer.MAX_VALUE) {
            throw invalidReport();
        }
        return (int) value;
    }

    private static long positiveLong(Map<String, Object> values, String key) {
        Object value = required(values, key);
        if (!(value instanceof BigDecimal number) || number.signum() <= 0
                || number.stripTrailingZeros().scale() > 0) {
            throw invalidReport();
        }
        try {
            return number.longValueExact();
        } catch (ArithmeticException exception) {
            throw invalidReport();
        }
    }

    private static List<String> stringList(Object value, String field) {
        if (!(value instanceof List<?> values)) {
            throw invalidReport();
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw invalidReport();
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static IllegalArgumentException invalidReport() {
        return new IllegalArgumentException("report is invalid, incomplete, unsupported, or has unknown metadata");
    }

    public String toJson() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", SCHEMA_VERSION);
        fields.put("status", status.name());
        fields.put("failureReason", failureReason);
        fields.put("timestamp", timestamp.toString());
        fields.put("observationType", "local_kdf_verification_cost");
        fields.put("algorithm", algorithm);
        fields.put("variant", variant);
        fields.put("parameters", parameters);
        fields.put("provider", orderedMap("name", providerName, "version", providerVersion));
        fields.put("javaRuntime", orderedMap("name", javaRuntimeName, "version", javaRuntimeVersion));
        fields.put("operatingSystem", orderedMap(
                "name", operatingSystemName, "version", operatingSystemVersion, "architecture", architecture));
        fields.put("cpuIdentifier", orderedMap(
                "availability", cpuIdentifier.startsWith("unavailable") ? "unavailable" : "available",
                "value", cpuIdentifier));
        fields.put("jvmInputArguments", orderedMap(
                "availability", jvmArgumentsAvailable ? "available" : "unavailable",
                "values", jvmArgumentsAvailable ? jvmInputArguments : List.of(UNAVAILABLE)));
        fields.put("warmupCount", warmupCount);
        fields.put("sampleCount", sampleCount);
        Map<String, Object> duration = new LinkedHashMap<>();
        duration.put("value", measuredDurationNanoseconds);
        duration.put("unit", "nanoseconds");
        duration.put("definition", measuredDurationDefinition);
        fields.put("measuredDuration", duration);
        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("median", medianVerificationLatencyNanoseconds);
        latency.put("p90", p90VerificationLatencyNanoseconds);
        latency.put("p90Method", "nearest-rank");
        latency.put("unit", "nanoseconds per verification");
        fields.put("verificationLatency", latency);
        fields.put("maximumDurationMillis", maximumDurationMillis);
        fields.put("limitations", limitations);
        return Json.encode(fields) + "\n";
    }

    public void writeNew(Path outputPath) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath");
        Path absolutePath = outputPath.toAbsolutePath().normalize();
        Path parent = absolutePath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Report output directory does not exist");
        }
        Path temporary = Files.createTempFile(parent, ".kdf-report-", ".tmp");
        try {
            Files.writeString(temporary, toJson(), StandardCharsets.UTF_8);
            Files.move(temporary, absolutePath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String algorithmName(PasswordKdfCostDataset.Configuration configuration) {
        if (configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration) {
            return "Argon2id";
        }
        if (configuration instanceof PasswordKdfCostDataset.BcryptConfiguration) {
            return "bcrypt";
        }
        if (configuration instanceof PasswordKdfCostDataset.ScryptConfiguration) {
            return "scrypt";
        }
        if (configuration instanceof PasswordKdfCostDataset.Pbkdf2Configuration) {
            return "PBKDF2";
        }
        return UNAVAILABLE;
    }

    private static String variant(PasswordKdfCostDataset.Configuration configuration) {
        if (configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration argon) {
            return "Argon2id v" + argon.algorithmVersion();
        }
        if (configuration instanceof PasswordKdfCostDataset.BcryptConfiguration bcrypt) {
            return bcrypt.outputFormat();
        }
        if (configuration instanceof PasswordKdfCostDataset.ScryptConfiguration) {
            return "scrypt (RFC 7914 construction)";
        }
        if (configuration instanceof PasswordKdfCostDataset.Pbkdf2Configuration pbkdf2) {
            return "PBKDF2-" + pbkdf2.prfHashVariant();
        }
        return UNAVAILABLE;
    }

    private static Map<String, Object> parameters(PasswordKdfCostDataset.Configuration configuration) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (configuration instanceof PasswordKdfCostDataset.Argon2idConfiguration argon) {
            values.put("algorithmVersion", argon.algorithmVersion());
            values.put("memoryBytes", argon.memoryBytes());
            values.put("timeCost", argon.timeCost());
            values.put("parallelism", argon.parallelism());
            values.put("saltLengthBytes", argon.saltLengthBytes());
            values.put("outputLengthBytes", argon.outputLengthBytes());
        } else if (configuration instanceof PasswordKdfCostDataset.BcryptConfiguration bcrypt) {
            values.put("workFactor", bcrypt.workFactor());
            values.put("saltHandling", bcrypt.saltHandling().name());
            values.put("saltLengthBytes", bcrypt.saltLengthBytes());
            values.put("outputFormat", bcrypt.outputFormat());
        } else if (configuration instanceof PasswordKdfCostDataset.ScryptConfiguration scrypt) {
            values.put("N", scrypt.n());
            values.put("r", scrypt.r());
            values.put("p", scrypt.p());
            values.put("memoryCostBytes", scrypt.memoryCostBytes());
            values.put("memoryCostContext", scrypt.memoryCostContext());
            values.put("saltLengthBytes", scrypt.saltLengthBytes());
            values.put("outputLengthBytes", scrypt.outputLengthBytes());
        } else if (configuration instanceof PasswordKdfCostDataset.Pbkdf2Configuration pbkdf2) {
            values.put("prfHashVariant", pbkdf2.prfHashVariant());
            values.put("iterationCount", pbkdf2.iterationCount());
            values.put("saltLengthBytes", pbkdf2.saltLengthBytes());
            values.put("outputLengthBytes", pbkdf2.outputLengthBytes());
        }
        return values;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Map<String, Object> orderedMap(Object... fields) {
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("JSON object fields must be key-value pairs");
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            ordered.put((String) fields[i], fields[i + 1]);
        }
        return ordered;
    }

    private static final class Json {
        private Json() {
        }

        private static String encode(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof String text) {
                return quote(text);
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            if (value instanceof Map<?, ?> map) {
                List<String> members = new ArrayList<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    members.add(quote(entry.getKey().toString()) + ":" + encode(entry.getValue()));
                }
                return "{" + String.join(",", members) + "}";
            }
            if (value instanceof Iterable<?> items) {
                List<String> encoded = new ArrayList<>();
                for (Object item : items) {
                    encoded.add(encode(item));
                }
                return "[" + String.join(",", encoded) + "]";
            }
            throw new IllegalArgumentException("Unsupported JSON value type");
        }

        private static String quote(String value) {
            StringBuilder quoted = new StringBuilder("\"");
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                switch (character) {
                    case '"' -> quoted.append("\\\"");
                    case '\\' -> quoted.append("\\\\");
                    case '\b' -> quoted.append("\\b");
                    case '\f' -> quoted.append("\\f");
                    case '\n' -> quoted.append("\\n");
                    case '\r' -> quoted.append("\\r");
                    case '\t' -> quoted.append("\\t");
                    default -> {
                        if (character < 0x20) {
                            quoted.append(String.format("\\u%04x", (int) character));
                        } else {
                            quoted.append(character);
                        }
                    }
                }
            }
            return quoted.append('"').toString();
        }
    }
}
