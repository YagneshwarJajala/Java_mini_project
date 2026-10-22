package com.astraai.ui;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.CharacterCodingException;
import java.util.Optional;

/** Explicit file-path loader for validating a local KDF benchmark report. */
public final class KdfBenchmarkReportLoader {
    private static final long MAX_REPORT_BYTES = 1_048_576;

    private KdfBenchmarkReportLoader() {
    }

    /**
     * Loads only the supplied path and validates it against the selected algorithm and configuration.
     * This method does not scan directories, run a benchmark, or retain report data.
     */
    public static KdfBenchmarkResultState load(Path reportPath, AlgorithmInfo selectedAlgorithm,
            PasswordKdfCostDataset.Configuration selectedConfiguration) {
        if (reportPath == null) {
            return KdfBenchmarkResultState.reportUnavailable("No report file path was supplied.");
        }
        if (selectedAlgorithm == null) {
            return KdfBenchmarkResultState.reportUnavailable("No expected algorithm was supplied.");
        }
        if ("SHA-3-based KDFs".equals(selectedAlgorithm.name())) {
            return CrackTimeEstimator.benchmarkState(selectedAlgorithm, null, Optional.empty(), null);
        }
        if (selectedConfiguration == null) {
            return KdfBenchmarkResultState.reportRequired(
                    "An explicit supported configuration is required to validate report provenance.");
        }

        KdfBenchmarkResultState baseline =
                CrackTimeEstimator.benchmarkState(selectedAlgorithm, selectedConfiguration, Optional.empty(), null);
        if (baseline.status() == KdfBenchmarkResultState.Status.BENCHMARK_UNAVAILABLE
                || baseline.status() == KdfBenchmarkResultState.Status.NOT_ESTIMABLE) {
            return baseline;
        }

        KdfVerificationBenchmark.RuntimeMetadata runtime;
        try {
            PasswordKdfCostDataset.validate(selectedConfiguration);
            runtime = KdfVerificationBenchmark.currentRuntimeMetadata(selectedConfiguration);
        } catch (IllegalArgumentException exception) {
            return KdfBenchmarkResultState.reportUnavailable(
                    "The selected configuration or provider is unsupported; the report cannot be validated.");
        }

        Path normalizedPath;
        try {
            normalizedPath = reportPath.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedPath) || !Files.isReadable(normalizedPath)) {
                return KdfBenchmarkResultState.reportUnavailable(
                        "The selected report file is missing, unreadable, or not a regular file.");
            }
            long size = Files.size(normalizedPath);
            if (size <= 0 || size > MAX_REPORT_BYTES) {
                return KdfBenchmarkResultState.reportUnavailable(
                        "The selected report file is empty or exceeds the supported 1 MiB size limit.");
            }
            byte[] content;
            try (var input = Files.newInputStream(normalizedPath)) {
                content = input.readNBytes((int) MAX_REPORT_BYTES + 1);
            }
            if (content.length > MAX_REPORT_BYTES) {
                return KdfBenchmarkResultState.reportUnavailable(
                        "The selected report file exceeds the supported 1 MiB size limit.");
            }
            String json;
            try {
                json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            } catch (CharacterCodingException exception) {
                return KdfBenchmarkResultState.reportUnavailable(
                        "The selected report file is not valid UTF-8 JSON.");
            }
            KdfBenchmarkReport report = KdfBenchmarkReport.parseCompletedJson(json);
            return CrackTimeEstimator.benchmarkState(
                    selectedAlgorithm, selectedConfiguration, Optional.of(report), runtime);
        } catch (IOException | SecurityException exception) {
            return KdfBenchmarkResultState.reportUnavailable(
                    "The selected report file could not be read.");
        } catch (IllegalArgumentException exception) {
            return KdfBenchmarkResultState.reportUnavailable(
                    "The selected report is malformed, incomplete, unsupported, or has unvalidated metadata.");
        }
    }

}
