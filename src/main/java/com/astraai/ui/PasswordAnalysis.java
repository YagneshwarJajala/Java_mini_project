package com.astraai.ui;

import java.util.Objects;
import java.util.List;

/** Immutable local heuristic result; it contains only derived counts, never the entered text. */
public record PasswordAnalysis(int length, int uppercaseCount, int lowercaseCount, int digitCount,
        int symbolCount, Strength strength, String explanation, List<String> predictablePatterns) {
    public enum Strength {
        WEAK("Weak", "#c63e37", 2),
        MODERATE("Moderate", "#d28b13", 4),
        STRONG("Strong", "#18834c", 5);

        private final String label;
        private final String color;
        private final int filledSegments;

        Strength(String label, String color, int filledSegments) {
            this.label = label;
            this.color = color;
            this.filledSegments = filledSegments;
        }

        public String label() { return label; }
        public String color() { return color; }
        public int filledSegments() { return filledSegments; }
    }

    public PasswordAnalysis {
        if (length < 1 || uppercaseCount < 0 || lowercaseCount < 0 || digitCount < 0 || symbolCount < 0
                || uppercaseCount + lowercaseCount + digitCount + symbolCount != length) {
            throw new IllegalArgumentException("character counts must add up to the analyzed length");
        }
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(explanation, "explanation");
        predictablePatterns = List.copyOf(Objects.requireNonNull(predictablePatterns, "predictablePatterns"));
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
    }

    public PasswordAnalysis(int length, int uppercaseCount, int lowercaseCount, int digitCount,
            int symbolCount, Strength strength, String explanation) {
        this(length, uppercaseCount, lowercaseCount, digitCount, symbolCount, strength, explanation, List.of());
    }

    public int characterClassCount() {
        return (uppercaseCount > 0 ? 1 : 0) + (lowercaseCount > 0 ? 1 : 0)
                + (digitCount > 0 ? 1 : 0) + (symbolCount > 0 ? 1 : 0);
    }
}
