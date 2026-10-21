package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordSecurityGuidanceTest {
    @Test
    void reportsEvidenceBoundFindingsAndRelevantAttacksWithoutCrackTimeClaims() {
        PasswordAnalysis analysis = PasswordInputPolicy.analyzeAndErase("abcd".toCharArray()).orElseThrow();
        PasswordSecurityGuidance guidance = PasswordSecurityGuidance.from(analysis);
        String text = guidance.assessmentText().toLowerCase();
        String guessing = guidance.guessingText().toLowerCase();

        assertTrue(text.contains("ascending character sequence"));
        assertTrue(guessing.contains("pattern-based guessing"));
        assertTrue(guessing.contains("dictionary guessing"));
        assertTrue(guessing.contains("brute-force guessing"));
        assertTrue(guessing.contains("offline guessing"));
        assertTrue(guessing.contains("no dictionary lookup was performed"));
        assertTrue(guessing.contains("does not estimate crack time"));
        assertFalse(guessing.contains("cracked in"));
        assertTrue(guidance.recommendationText().contains("randomly generated"));
    }

    @Test
    void avoidsClaimingPatternDetectionForAnInputWithoutRecordedPatterns() {
        PasswordAnalysis analysis = PasswordInputPolicy.analyzeAndErase("Moon!River#92".toCharArray()).orElseThrow();
        PasswordSecurityGuidance guidance = PasswordSecurityGuidance.from(analysis);
        String text = guidance.assessmentText().toLowerCase();
        String guessing = guidance.guessingText().toLowerCase();

        assertFalse(guessing.contains("pattern-based guessing"));
        assertTrue(text.contains("no repeated-character or simple consecutive sequence was detected"));
        assertTrue(text.contains("strength is separate from storage security"));
    }
}
