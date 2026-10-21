package com.astraai.ui;

import java.time.Duration;

/** Formatting for measured values shown in the result report. */
final class UiFormatting {
    private UiFormatting() { }

    static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        long hours = millis / 3_600_000;
        long minutes = (millis / 60_000) % 60;
        long seconds = (millis / 1_000) % 60;
        long remainder = millis % 1_000;
        return hours > 0
                ? "%d:%02d:%02d.%03d".formatted(hours, minutes, seconds, remainder)
                : "%02d:%02d.%03d".formatted(minutes, seconds, remainder);
    }
}
