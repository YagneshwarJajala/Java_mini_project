package com.astraai.ui;

import java.util.Optional;
import java.security.Provider;
import java.security.Security;
import javax.crypto.SecretKeyFactory;

/** Explicit opt-in integration smoke; intentionally has no JUnit test method. */
public final class KdfBenchmarkResultStateSmoke {
    private KdfBenchmarkResultStateSmoke() {
    }

    public static void main(String[] args) {
        Provider provider = Security.getProvider("SunJCE");
        if (provider == null) {
            throw new IllegalStateException("SunJCE is unavailable for the PBKDF2 state smoke");
        }
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", provider);
        } catch (Exception exception) {
            throw new IllegalStateException("SunJCE PBKDF2WithHmacSHA256 is unavailable");
        }
        PasswordKdfCostDataset.Provider selectedProvider =
                new PasswordKdfCostDataset.Provider(provider.getName(), provider.getVersionStr());
        PasswordKdfCostDataset.Pbkdf2Configuration configuration =
                new PasswordKdfCostDataset.Pbkdf2Configuration(
                        "HmacSHA256", 10_000, 16, 32, selectedProvider);
        KdfVerificationBenchmark.Result result = KdfVerificationBenchmark.run(configuration,
                new KdfVerificationBenchmark.Settings(1, 3, 10_000));
        KdfBenchmarkReport report = KdfBenchmarkReport.completed(result);
        AlgorithmInfo algorithm = AlgorithmCatalog.all().get(3);
        KdfBenchmarkResultState state = CrackTimeEstimator.benchmarkState(
                algorithm, configuration, Optional.of(report), result.runtimeMetadata());
        if (report.status() != KdfBenchmarkReport.Status.COMPLETED
                || state.status() != KdfBenchmarkResultState.Status.LOCAL_VERIFICATION_COST_OBSERVATION
                || state.medianVerificationMilliseconds().isEmpty()
                || !state.displayText().contains("not crack time")) {
            throw new IllegalStateException("A completed exact-match report was not accepted as local verification evidence");
        }

        PasswordKdfCostDataset.Pbkdf2Configuration mismatchedConfiguration =
                new PasswordKdfCostDataset.Pbkdf2Configuration(
                        "HmacSHA256", 10_001, 16, 32, selectedProvider);
        KdfBenchmarkResultState mismatched = CrackTimeEstimator.benchmarkState(
                algorithm, mismatchedConfiguration, Optional.of(report), result.runtimeMetadata());
        if (mismatched.status() != KdfBenchmarkResultState.Status.BENCHMARK_REQUIRED
                || mismatched.medianVerificationMilliseconds().isPresent()) {
            throw new IllegalStateException("A report for different parameters was accepted");
        }
        System.out.println("KDF benchmark result-state smoke passed; no fixture secrets were printed.");
    }
}
