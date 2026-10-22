package com.astraai.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit educational assumptions, not benchmark observations. Rates are configurable by
 * constructing a dataset with different algorithm-specific profiles.
 */
public final class CrackTimeAssumptionDataset {
    public static final String VERSION = "educational-reference-v1";
    public static final LocalDate AS_OF = LocalDate.of(2026, 10, 21);
    public static final String PROVENANCE = "Illustrative local assumptions authored for this educational model; "
            + "not externally sourced, measured, or representative of a particular attack rig.";

    public enum Algorithm { ARGON2ID, BCRYPT, SCRYPT, PBKDF2 }

    public record Profile(String variantAndParameters, BigDecimal guessesPerSecond) {
        public Profile {
            Objects.requireNonNull(variantAndParameters, "variantAndParameters");
            Objects.requireNonNull(guessesPerSecond, "guessesPerSecond");
            if (variantAndParameters.isBlank() || guessesPerSecond.signum() <= 0) {
                throw new IllegalArgumentException("profile parameters and rate must be positive and nonblank");
            }
        }
    }

    private final Map<Algorithm, Profile> profiles;

    public CrackTimeAssumptionDataset(Map<Algorithm, Profile> profiles) {
        this.profiles = Map.copyOf(Objects.requireNonNull(profiles, "profiles"));
    }

    /** Single hypothetical offline attacker, one verifier at a time; values are deliberately illustrative. */
    public static CrackTimeAssumptionDataset educationalDefaults() {
        return new CrackTimeAssumptionDataset(Map.of(
                Algorithm.ARGON2ID, new Profile("Argon2id v1.3, m=65,536 KiB, t=3, p=1", rate("10")),
                Algorithm.BCRYPT, new Profile("bcrypt 2b, cost=12", rate("10")),
                Algorithm.SCRYPT, new Profile("scrypt N=131,072, r=8, p=1", rate("5")),
                Algorithm.PBKDF2, new Profile("PBKDF2-HMAC-SHA-256, 600,000 iterations", rate("50"))));
    }

    public Profile profile(Algorithm algorithm) {
        return profiles.get(Objects.requireNonNull(algorithm, "algorithm"));
    }

    public Map<Algorithm, Profile> profiles() {
        return profiles;
    }

    private static BigDecimal rate(String value) {
        return new BigDecimal(value);
    }
}
