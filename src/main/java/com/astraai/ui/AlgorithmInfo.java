package com.astraai.ui;

import java.util.Objects;

/** Informational algorithm reference; the application does not execute or benchmark it. */
public record AlgorithmInfo(String name, String category, String description,
        String howItWorks, String resistance, String limitations, String recommendation) {
    public AlgorithmInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(howItWorks, "howItWorks");
        Objects.requireNonNull(resistance, "resistance");
        Objects.requireNonNull(limitations, "limitations");
        Objects.requireNonNull(recommendation, "recommendation");
        if (name.isBlank() || category.isBlank() || description.isBlank() || howItWorks.isBlank()
                || resistance.isBlank() || limitations.isBlank() || recommendation.isBlank()) {
            throw new IllegalArgumentException("algorithm reference fields must not be blank");
        }
    }

    public AlgorithmInfo(String name, String category, String description) {
        this(name, category, description, description,
                "Security depends on the scheme's design and its correct configuration.",
                "This application does not execute or benchmark the algorithm.",
                "Use a maintained implementation and follow its current configuration guidance.");
    }

    public String statusLabel() {
        return "Informational · not executed";
    }

    public String benchmarkLabel() {
        return "Benchmark · not performed";
    }

    /** Short, non-measured guidance shown in the existing collapsed card header. */
    public String guidanceLabel() {
        return switch (name) {
            case "Argon2id" -> "Preferred starting point";
            case "bcrypt" -> "Compatibility alternative";
            case "scrypt" -> "Memory-hard alternative";
            case "PBKDF2" -> "Platform and compliance option";
            case "SHA-3-based KDFs" -> "Use a dedicated password KDF";
            default -> "Educational reference";
        };
    }
}
