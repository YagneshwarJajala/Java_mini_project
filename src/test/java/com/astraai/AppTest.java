package com.astraai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AppTest {
    @Test
    void applicationMetadataDefinesThePassScopeIdentity() {
        assertEquals("PassScope", AppMetadata.WINDOW_TITLE);
        assertEquals("PASSSCOPE", AppMetadata.HEADING);
        assertEquals("Password Security & Hash Analysis", AppMetadata.SUBTITLE);
        assertFalse(AppMetadata.STATUS.isBlank());
    }
}
