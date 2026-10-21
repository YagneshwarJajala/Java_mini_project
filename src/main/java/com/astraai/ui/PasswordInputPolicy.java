package com.astraai.ui;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Validates a transient demo input without retaining it. The challenge never receives this value. */
final class PasswordInputPolicy {
    static final int MAX_INPUT_LENGTH = 256;

    private PasswordInputPolicy() {
    }

    static boolean acceptsAndErase(char[] input) {
        return analyzeAndErase(input).isPresent();
    }

    static Optional<PasswordAnalysis> analyzeAndErase(char[] input) {
        if (input == null) {
            return Optional.empty();
        }
        try {
            if (input.length == 0 || input.length > MAX_INPUT_LENGTH) {
                return Optional.empty();
            }
            int length = Character.codePointCount(input, 0, input.length);
            int uppercase = 0;
            int lowercase = 0;
            int digits = 0;
            int symbols = 0;
            boolean allSame = length > 1;
            boolean ascending = length >= 4;
            boolean descending = length >= 4;
            boolean nonWhitespace = false;
            int previous = -1;
            int first = -1;
            int seen = 0;
            for (int index = 0; index < input.length;) {
                int character = Character.codePointAt(input, index);
                index += Character.charCount(character);
                seen++;
                if (Character.isUpperCase(character)) {
                    uppercase++;
                } else if (Character.isLowerCase(character)) {
                    lowercase++;
                } else if (Character.isDigit(character)) {
                    digits++;
                } else {
                    symbols++;
                }
                nonWhitespace |= !Character.isWhitespace(character);
                if (seen == 1) {
                    first = character;
                } else {
                    allSame &= character == first;
                    ascending &= character == previous + 1;
                    descending &= character == previous - 1;
                }
                previous = character;
            }
            if (!nonWhitespace) {
                return Optional.empty();
            }

            boolean obviousPattern = allSame || ascending || descending;
            List<String> patterns = new ArrayList<>();
            if (allSame) {
                patterns.add("The same character is repeated throughout the input.");
            }
            if (ascending) {
                patterns.add("The input follows a simple ascending character sequence.");
            }
            if (descending) {
                patterns.add("The input follows a simple descending character sequence.");
            }
            PasswordAnalysis.Strength strength;
            String explanation;
            int classes = (uppercase > 0 ? 1 : 0) + (lowercase > 0 ? 1 : 0)
                    + (digits > 0 ? 1 : 0) + (symbols > 0 ? 1 : 0);
            if (length < 8 || obviousPattern) {
                strength = PasswordAnalysis.Strength.WEAK;
                explanation = obviousPattern
                        ? "A repeated-character or simple sequential pattern was detected."
                        : "The length is under eight characters.";
            } else if (length >= 12 && classes >= 3) {
                strength = PasswordAnalysis.Strength.STRONG;
                explanation = "Length is at least 12 characters and the input uses at least three character classes.";
            } else {
                strength = PasswordAnalysis.Strength.MODERATE;
                explanation = length < 12
                        ? "Length is at least eight characters; this simple heuristic favors longer inputs."
                        : "The input uses fewer than three character classes under this simple heuristic.";
            }
            return Optional.of(new PasswordAnalysis(length, uppercase, lowercase, digits, symbols,
                    strength, explanation, patterns));
        } finally {
            Arrays.fill(input, '\0');
        }
    }
}
