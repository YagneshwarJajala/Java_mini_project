package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class UiFormattingTest {
    @Test
    void formatsMeasuredDurationsAtMillisecondPrecision() {
        assertEquals("00:00.047", UiFormatting.formatDuration(Duration.ofMillis(47)));
        assertEquals("01:02.003", UiFormatting.formatDuration(Duration.ofMillis(62_003)));
        assertEquals("1:01:02.003", UiFormatting.formatDuration(Duration.ofMillis(3_662_003)));
    }
}
