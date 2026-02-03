package com.example.timertrigger;

import java.time.Duration;

public final class DurationParser {
    private DurationParser() {
    }

    public static Duration parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration value is required");
        }
        String trimmed = value.trim().toLowerCase();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Invalid duration: " + value);
        }
        char unit = trimmed.charAt(trimmed.length() - 1);
        String numberPart = trimmed.substring(0, trimmed.length() - 1);
        long amount;
        try {
            amount = Long.parseLong(numberPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration: " + value);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Duration must be positive: " + value);
        }
        switch (unit) {
            case 's':
                return Duration.ofSeconds(amount);
            case 'm':
                return Duration.ofMinutes(amount);
            case 'h':
                return Duration.ofHours(amount);
            case 'd':
                return Duration.ofDays(amount);
            default:
                throw new IllegalArgumentException("Invalid duration unit: " + value);
        }
    }
}
