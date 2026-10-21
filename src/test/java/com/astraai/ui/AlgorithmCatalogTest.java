package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AlgorithmCatalogTest {
    @Test
    void providesFiveInformationalReferencesAndNoMeasurementClaims() {
        assertEquals(5, AlgorithmCatalog.all().size());
        assertEquals(java.util.List.of("Argon2id", "bcrypt", "scrypt", "PBKDF2", "SHA-3-based KDFs"),
                AlgorithmCatalog.all().stream().map(AlgorithmInfo::name).toList());
        AlgorithmCatalog.all().forEach(info -> {
            assertFalse(info.guidanceLabel().isBlank());
            assertEquals("Informational · not executed", info.statusLabel());
            assertEquals("Benchmark · not performed", info.benchmarkLabel());
            org.junit.jupiter.api.Assertions.assertAll(
                    () -> org.junit.jupiter.api.Assertions.assertFalse(info.howItWorks().isBlank()),
                    () -> org.junit.jupiter.api.Assertions.assertFalse(info.resistance().isBlank()),
                    () -> org.junit.jupiter.api.Assertions.assertFalse(info.limitations().isBlank()),
                    () -> org.junit.jupiter.api.Assertions.assertFalse(info.recommendation().isBlank()));
        });
        assertThrows(UnsupportedOperationException.class,
                () -> AlgorithmCatalog.all().add(new AlgorithmInfo("extra", "test", "test")));
    }

    @Test
    void recommendsDefensiblePasswordStorageWithoutClaimingAnApplicationBenchmark() {
        AlgorithmInfo argon2id = AlgorithmCatalog.all().get(0);
        assertTrue(argon2id.recommendation().toLowerCase().contains("preferred starting point"));
        assertTrue(argon2id.recommendation().toLowerCase().contains("unique random salt"));
        assertEquals("Preferred starting point", argon2id.guidanceLabel());

        AlgorithmInfo pbkdf2 = AlgorithmCatalog.all().get(3);
        assertTrue(pbkdf2.recommendation().toLowerCase().contains("compliance"));
        AlgorithmInfo hashReference = AlgorithmCatalog.all().get(4);
        assertTrue(hashReference.limitations().contains("SHA-256"));
        assertTrue(hashReference.recommendation().contains("plain SHA-256"));
        AlgorithmCatalog.all().forEach(info -> assertEquals("Benchmark · not performed", info.benchmarkLabel()));
    }
}
