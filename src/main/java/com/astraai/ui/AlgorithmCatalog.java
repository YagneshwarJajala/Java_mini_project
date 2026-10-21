package com.astraai.ui;

import java.util.List;

/** Static, non-measured educational references shown in the final report. */
public final class AlgorithmCatalog {
    private static final List<AlgorithmInfo> ALGORITHMS = List.of(
            new AlgorithmInfo("Argon2id", "Memory-hard password hashing",
                    "Memory-hard password hashing · no benchmark performed",
                    "Combines data-independent and data-dependent memory access in a configurable memory-hard function.",
                    "Memory and time costs raise the work required for large-scale guessing when configured adequately.",
                    "Security and resource use depend on chosen memory, iteration, and parallelism parameters.",
                    "Preferred starting point for modern password storage. Use a maintained implementation, a unique random salt per password, and memory, iteration, and parallelism parameters calibrated to deployment resources and current library guidance."),
            new AlgorithmInfo("bcrypt", "Adaptive password hashing",
                    "Adaptive password hashing · no benchmark performed",
                    "Uses a configurable cost factor to make each password verification deliberately expensive.",
                    "The cost can be increased over time, increasing the work of offline guessing.",
                    "It is not memory-hard and its output format and supported password length need attention.",
                    "A practical alternative where broad compatibility is important. Use a maintained implementation, a unique salt, and a cost calibrated for the target system; check the implementation's password-length behavior."),
            new AlgorithmInfo("scrypt", "Memory-hard key derivation",
                    "Memory-hard key derivation · no benchmark performed",
                    "A memory-hard password-based derivation function with configurable CPU and memory costs.",
                    "Its memory requirement can make highly parallel guessing more resource-intensive.",
                    "Parameters determine resource use; poor choices or implementation mistakes weaken protection.",
                    "A memory-hard alternative where mature implementation support exists. Use a unique salt and choose parameters for the deployment's memory and latency limits."),
            new AlgorithmInfo("PBKDF2", "Password-based key derivation",
                    "Iteration-based key derivation · no benchmark performed",
                    "Applies a pseudorandom function repeatedly using a salt and configurable iteration count.",
                    "Increasing the iteration count increases the computation required per guess.",
                    "Its standard construction does not impose the memory cost of memory-hard schemes.",
                    "An alternative where platform support or compliance requirements favor it. Use a unique random salt per password and configure the iteration count according to current guidance and deployment capacity."),
            new AlgorithmInfo("SHA-3-based KDFs", "SHA-3-based key derivation",
                    "General-purpose hash / KDF distinction · no benchmark performed",
                    "SHA-3 names a family of general-purpose hash functions; a KDF requires a dedicated construction.",
                    "A properly designed KDF can add a salt and configurable work factor appropriate to its design.",
                    "Plain SHA-256 and SHA-3 hashes are fast general-purpose hashes, not suitable direct password storage. Adding a salt alone does not make a fast hash expensive to guess.",
                    "Use a dedicated adaptive password-hashing scheme such as Argon2id, scrypt, bcrypt, or PBKDF2 rather than storing a plain SHA-256 or SHA-3 digest."));

    private AlgorithmCatalog() {
    }

    public static List<AlgorithmInfo> all() {
        return ALGORITHMS;
    }
}
