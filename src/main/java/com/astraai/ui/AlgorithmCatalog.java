package com.astraai.ui;

import java.util.List;

/** Static, non-measured educational references shown in the final report. */
public final class AlgorithmCatalog {
    private static final List<AlgorithmInfo> ALGORITHMS = List.of(
            new AlgorithmInfo("Argon2id", "Memory-hard password hashing",
                    "Memory-hard password hashing · no benchmark performed",
                    "Purpose: store password verifiers using a dedicated, configurable password-hashing function.",
                    "Advantage: its configurable memory and time costs make each guess more resource-intensive when set appropriately.",
                    "Trade-off: memory, iteration, and parallelism settings affect both verification latency and server capacity.",
                    "Use case: the preferred starting point for new password-storage systems. Argon2id is a strong modern choice when maintained library support and its resource requirements fit the deployment; calibrate memory, iteration, and parallelism parameters to current guidance and capacity. Generate a unique random salt for every password and store the salt and parameters with the verifier."),
            new AlgorithmInfo("bcrypt", "Adaptive password hashing",
                    "Adaptive password hashing · no benchmark performed",
                    "Purpose: store password verifiers using an adaptive password-hashing function.",
                    "Advantage: the configurable cost can be raised over time, increasing the work per offline guess.",
                    "Trade-off: it is not memory-hard, and implementations may have password-length or format constraints.",
                    "Use case: a practical choice where mature bcrypt support or compatibility is important. Fit depends on the implementation's input limits and the deployment's acceptable verification cost. Use a unique salt and current library guidance."),
            new AlgorithmInfo("scrypt", "Memory-hard key derivation",
                    "Memory-hard key derivation · no benchmark performed",
                    "Purpose: derive a password verifier or key with configurable CPU and memory costs.",
                    "Advantage: its memory requirement can make highly parallel guessing more resource-intensive.",
                    "Trade-off: parameter choices affect memory use, latency, and concurrency; incorrect configuration can reduce protection or strain the service.",
                    "Use case: a memory-hard alternative when a maintained implementation is available. Choose it when its memory profile fits the deployment, use a unique salt, and follow current parameter guidance."),
            new AlgorithmInfo("PBKDF2", "Password-based key derivation",
                    "Iteration-based key derivation · no benchmark performed",
                    "Purpose: derive a password verifier or key by repeatedly applying a pseudorandom function with a salt.",
                    "Advantage: the iteration count is configurable and the construction has broad platform support.",
                    "Trade-off: increasing iterations raises computation per guess but does not provide the memory-hard cost of Argon2id or scrypt.",
                    "Use case: a reasonable option when platform support or compliance requirements favor it. Fit depends on current iteration guidance and the service's latency budget; use a unique random salt per password."),
            new AlgorithmInfo("SHA-3-based KDFs", "Unspecified SHA-3 KDF proposal",
                    "General-purpose hash / KDF distinction · no benchmark performed",
                    "Purpose: SHA-3 is a family of general-purpose cryptographic hashes; this catalog does not identify a concrete password KDF.",
                    "Advantage: a separately specified, vetted KDF may use a hash within a construction designed for password-derived keys.",
                    "Trade-off: plain SHA-256 and SHA-3 digests are fast general-purpose hashes. Neither is suitable for direct password storage; a salt alone does not make a fast hash expensive to guess.",
                    "Use case: this entry is insufficiently specified for password storage. Do not use a plain SHA-3 digest; prefer a dedicated password-hashing scheme unless a concrete, parameterized KDF is identified."));

    private AlgorithmCatalog() {
    }

    public static List<AlgorithmInfo> all() {
        return ALGORITHMS;
    }
}
