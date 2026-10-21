package com.astraai.simulation;

import java.util.Objects;

/** Immutable independent validation decision and concise explanation. */
public record ValidationReport(boolean valid, String explanation) {
    public ValidationReport {
        Objects.requireNonNull(explanation, "explanation");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
    }

    public static ValidationReport accepted(String explanation) {
        return new ValidationReport(true, explanation);
    }

    public static ValidationReport rejected(String explanation) {
        return new ValidationReport(false, explanation);
    }
}
