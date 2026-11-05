package pl.ingotlabs.base.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money is stored internally as long "units" with a fixed scale of 4 decimal places. */
public final class Money {
    public static final int SCALE = 4;
    public static final long FACTOR = 10_000L;

    private Money() {
    }

    public static long toUnits(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP).unscaledValue().longValueExact();
    }

    public static BigDecimal toDecimal(long units) {
        return BigDecimal.valueOf(units, SCALE);
    }

    public static double toDouble(long units) {
        return units / (double) FACTOR;
    }
}
