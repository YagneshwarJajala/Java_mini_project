package com.astraai.ui;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Educational candidate-space estimate; never an observation of an actual attack. */
public record CrackTimeEstimate(Status status, String displayText, String explanation,
        Optional<BigDecimal> candidateSpace, Optional<BigDecimal> expectedGuesses,
        Optional<BigDecimal> assumedGuessesPerSecond) {
    public enum Status { ESTIMATED, UNAVAILABLE, NOT_ESTIMABLE }

    public CrackTimeEstimate {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(displayText, "displayText");
        Objects.requireNonNull(explanation, "explanation");
        candidateSpace = Objects.requireNonNull(candidateSpace, "candidateSpace");
        expectedGuesses = Objects.requireNonNull(expectedGuesses, "expectedGuesses");
        assumedGuessesPerSecond = Objects.requireNonNull(assumedGuessesPerSecond, "assumedGuessesPerSecond");
        if (displayText.isBlank() || explanation.isBlank()) {
            throw new IllegalArgumentException("display text and explanation must not be blank");
        }
    }
}
