package com.astraai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AppTest {
    @Test
    void applicationMetadataDefinesTheExpectedStarterIdentity() {
        assertEquals("Astra AI", AppMetadata.WINDOW_TITLE);
        assertEquals("ASTRA AI", AppMetadata.HEADING);
        assertEquals("Security Simulation Lab", AppMetadata.SUBTITLE);
        assertFalse(AppMetadata.STATUS.isBlank());
    }
}
