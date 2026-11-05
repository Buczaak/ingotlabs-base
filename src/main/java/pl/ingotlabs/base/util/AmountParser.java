package pl.ingotlabs.base.util;

import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.Money;

import java.math.BigDecimal;
import java.util.Locale;

public final class AmountParser {
    private AmountParser() {
    }

    /**
     * Parses "100", "2.5", "1.5k", "2m", "1b", "1t" into units.
     * Returns -1 when invalid (including more decimals than the currency allows).
     */
    public static long parse(String input, Settings settings) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String t = input.trim().toLowerCase(Locale.ROOT);
        BigDecimal mult = BigDecimal.ONE;
        char last = t.charAt(t.length() - 1);
        switch (last) {
            case 'k' -> mult = BigDecimal.valueOf(1_000L);
            case 'm' -> mult = BigDecimal.valueOf(1_000_000L);
            case 'b' -> mult = BigDecimal.valueOf(1_000_000_000L);
            case 't' -> mult = BigDecimal.valueOf(1_000_000_000_000L);
            default -> {
            }
        }
        if (mult.compareTo(BigDecimal.ONE) != 0) {
            t = t.substring(0, t.length() - 1);
        }
        if (!t.matches("\\d{1,15}(\\.\\d{1,4})?")) {
            return -1;
        }
        try {
            BigDecimal v = new BigDecimal(t).multiply(mult).stripTrailingZeros();
            if (v.scale() > settings.decimals()) {
                return -1;
            }
            return Money.toUnits(v);
        } catch (ArithmeticException e) {
            return -1;
        }
    }
}
