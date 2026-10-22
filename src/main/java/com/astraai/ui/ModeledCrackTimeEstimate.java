package com.astraai.ui;

import java.util.Objects;
import java.util.List;

/** Status and missing inputs for a hypothetical scenario; no attack rate or time is calculated. */
public record ModeledCrackTimeEstimate(Status status, String reason, List<MissingInput> missingInputs,
        ScenarioMetadata scenario, AlgorithmId algorithmId) {
    public enum AlgorithmId {
        ARGON2ID,
        BCRYPT,
        SCRYPT,
        PBKDF2,
        UNSPECIFIED_SHA3_KDF,
        UNKNOWN
    }

    public enum Status {
        NOT_ESTIMATED("Estimate unavailable"),
        NOT_ESTIMABLE("Not estimable");

        private final String displayText;

        Status(String displayText) {
            this.displayText = displayText;
        }

        public String displayText() {
            return displayText;
        }
    }

    public enum MissingInput {
        CONCRETE_PASSWORD_KDF("a concrete password KDF"),
        ALGORITHM_VARIANT("the algorithm variant"),
        MEMORY_TIME_PARALLELISM("memory, time, and parallelism parameters"),
        BCRYPT_WORK_FACTOR("the bcrypt work factor"),
        SCRYPT_N_R_P("scrypt N/r/p parameters"),
        SCRYPT_MEMORY("scrypt memory requirements"),
        PBKDF2_PRF("the PBKDF2 PRF/hash variant"),
        PBKDF2_ITERATIONS("the PBKDF2 iteration count"),
        IMPLEMENTATION_HARDWARE_CONTEXT("documented implementation and hardware context"),
        TRACEABLE_COST_DATA("traceable, algorithm-specific cost data"),
        CANDIDATE_SPACE_ASSUMPTION("an explicitly defined candidate-space assumption"),
        ALGORITHM_CONFIGURATION("a recognized algorithm configuration");

        private final String description;

        MissingInput(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    public enum RateScope {
        NOT_CONFIGURED
    }

    public enum AgentCountMeaning {
        LOGICAL_AGENTS_ONLY
    }

    /** Scenario metadata only; logical agents do not imply physical workers or models. */
    public record ScenarioMetadata(int configuredLogicalAgentCount, AgentCountMeaning agentCountMeaning,
            RateScope rateScope) {
        public ScenarioMetadata {
            if (configuredLogicalAgentCount <= 0) {
                throw new IllegalArgumentException("logical agent count must be positive");
            }
            Objects.requireNonNull(agentCountMeaning, "agentCountMeaning");
            Objects.requireNonNull(rateScope, "rateScope");
        }
    }

    public ModeledCrackTimeEstimate {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        missingInputs = List.copyOf(Objects.requireNonNull(missingInputs, "missingInputs"));
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(algorithmId, "algorithmId");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
