package com.astraai.simulation;

import java.util.Objects;
import java.util.Optional;

/** Outcome of the runner's bounded executor shutdown attempt. */
public record CleanupReport(boolean terminationConfirmed, boolean interruptionRequested,
        Optional<String> diagnostic) {
    public CleanupReport {
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (terminationConfirmed && diagnostic.isPresent()) {
            throw new IllegalArgumentException("confirmed termination must not have a cleanup diagnostic");
        }
    }
}
