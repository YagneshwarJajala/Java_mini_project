package com.astraai.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Evidence-bounded educational guidance derived only from the transient analysis counts. */
public record PasswordSecurityGuidance(List<String> strengths, List<String> weaknesses,
        List<String> attackExplanations, List<String> recommendations) {
    public PasswordSecurityGuidance {
        strengths = List.copyOf(Objects.requireNonNull(strengths, "strengths"));
        weaknesses = List.copyOf(Objects.requireNonNull(weaknesses, "weaknesses"));
        attackExplanations = List.copyOf(Objects.requireNonNull(attackExplanations, "attackExplanations"));
        recommendations = List.copyOf(Objects.requireNonNull(recommendations, "recommendations"));
    }

    public static PasswordSecurityGuidance from(PasswordAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>(analysis.predictablePatterns());
        if (analysis.length() >= 12) {
            strengths.add("Length is at least 12 characters under this local heuristic.");
        } else {
            weaknesses.add("Length is below 12 characters; longer unique passwords are generally preferable.");
        }
        if (analysis.characterClassCount() >= 3) {
            strengths.add("The input includes " + analysis.characterClassCount() + " character classes.");
        } else {
            weaknesses.add("Only " + analysis.characterClassCount()
                    + " character classes were observed; character variety is one heuristic signal.");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("No repeated-character or simple consecutive sequence was detected by this limited check.");
        }
        if (strengths.isEmpty()) {
            strengths.add("The analysis recorded " + analysis.length() + " characters and "
                    + analysis.characterClassCount() + " character classes.");
        }

        List<String> attacks = new ArrayList<>();
        if (!analysis.predictablePatterns().isEmpty()) {
            attacks.add("Pattern-based guessing often tries repetitions and simple sequences; this analysis detected the pattern category listed above.");
        }
        if (analysis.length() < 12 || !analysis.predictablePatterns().isEmpty()) {
            attacks.add("Dictionary guessing tests common words and phrases. No dictionary lookup was performed, so this analysis cannot say whether the input is a dictionary entry.");
        }
        attacks.add("Brute-force guessing enumerates candidate combinations. Length and character classes affect the search space, but this heuristic does not estimate crack time or guarantee resistance.");
        attacks.add("Offline guessing is possible when an attacker obtains password verifiers; a slow, salted password-hashing scheme makes each guess more costly. This application did not hash or benchmark the input.");

        List<String> recommendations = new ArrayList<>();
        if (!analysis.predictablePatterns().isEmpty()) {
            recommendations.add("Replace repeated or sequential structure with a longer, randomly generated password or unrelated passphrase.");
        } else if (analysis.length() < 12 || analysis.characterClassCount() < 3) {
            recommendations.add("Prefer a longer, unique password or randomly generated passphrase; do not rely on predictable substitutions.");
        } else {
            recommendations.add("Keep it unique to this account; consider a password manager to generate and store distinct passwords.");
        }
        recommendations.add("Enable multi-factor authentication where available. This local check does not test reuse, breaches, or dictionary membership.");
        return new PasswordSecurityGuidance(strengths, weaknesses, attacks, recommendations);
    }

    public String assessmentText() {
        return "Strengths: " + String.join(" ", strengths)
                + "\nWeaknesses and limits: " + String.join(" ", weaknesses)
                + "\nStrength is separate from storage security: this application did not store or hash the input.";
    }

    public String guessingText() {
        return String.join(" ", attackExplanations);
    }

    public String recommendationText() {
        return String.join(" ", recommendations);
    }
}
