package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PasswordInputPolicyTest {
    @Test
    void acceptsOnlyNonWhitespaceBoundedInputAndAlwaysErasesIt() {
        char[] accepted = "demo text".toCharArray();
        assertTrue(PasswordInputPolicy.acceptsAndErase(accepted));
        assertArrayEquals(new char[accepted.length], accepted);

        char[] whitespace = " \t\n".toCharArray();
        assertFalse(PasswordInputPolicy.acceptsAndErase(whitespace));
        assertArrayEquals(new char[whitespace.length], whitespace);

        char[] oversized = new char[PasswordInputPolicy.MAX_INPUT_LENGTH + 1];
        Arrays.fill(oversized, 'x');
        assertFalse(PasswordInputPolicy.acceptsAndErase(oversized));
        assertArrayEquals(new char[oversized.length], oversized);
    }

    @Test
    void derivesOnlyCharacterCountsAndHeuristicBeforeErasingRawInput() {
        char[] input = "Abcd123!xy".toCharArray();
        PasswordAnalysis analysis = PasswordInputPolicy.analyzeAndErase(input).orElseThrow();

        assertEquals(10, analysis.length());
        assertEquals(1, analysis.uppercaseCount());
        assertEquals(5, analysis.lowercaseCount());
        assertEquals(3, analysis.digitCount());
        assertEquals(1, analysis.symbolCount());
        assertEquals(PasswordAnalysis.Strength.MODERATE, analysis.strength());
        assertArrayEquals(new char[input.length], input);
    }

    @Test
    void assignsWeakAndStrongUsingDocumentedLocalRules() {
        char[] sequential = "abcd".toCharArray();
        PasswordAnalysis weak = PasswordInputPolicy.analyzeAndErase(sequential).orElseThrow();
        assertEquals(PasswordAnalysis.Strength.WEAK, weak.strength());
        assertTrue(weak.explanation().contains("sequential"));

        PasswordAnalysis strong = PasswordInputPolicy.analyzeAndErase("M0on!River#92".toCharArray()).orElseThrow();
        assertEquals(PasswordAnalysis.Strength.STRONG, strong.strength());
        assertEquals(4, strong.characterClassCount());
    }

    @Test
    void recordsOnlyTheSpecificRepeatedOrSequentialPatternCategories() {
        PasswordAnalysis repeated = PasswordInputPolicy.analyzeAndErase("aaaaaaaaaaaa".toCharArray()).orElseThrow();
        assertTrue(repeated.predictablePatterns().contains("The same character is repeated throughout the input."));

        PasswordAnalysis ascending = PasswordInputPolicy.analyzeAndErase("abcdefgh".toCharArray()).orElseThrow();
        assertTrue(ascending.predictablePatterns().contains("The input follows a simple ascending character sequence."));
        assertFalse(ascending.predictablePatterns().stream().anyMatch(value -> value.contains("descending")));
    }
}
