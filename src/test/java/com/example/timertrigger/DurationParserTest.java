package com.example.timertrigger;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {
    @Test
    void parsesSupportedUnits() {
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s"));
        assertEquals(Duration.ofMinutes(15), DurationParser.parse("15m"));
        assertEquals(Duration.ofHours(2), DurationParser.parse("2h"));
        assertEquals(Duration.ofDays(1), DurationParser.parse("1d"));
    }

    @Test
    void rejectsInvalidDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("0m"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("10"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("5w"));
    }
}
