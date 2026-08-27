package com.berkeleybikebuilders.tubewinder.gcode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Number formatting for G-code words: fixed decimals, trailing zeros stripped, no exponents. */
final class Num {

    /** Decimal places emitted. v7 used 3 for traverse rotations and 2 for the index angle. */
    static final int DECIMALS = 3;

    private Num() {
    }

    static String fmt(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value)
                .setScale(DECIMALS, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        if (rounded.scale() < 0) {
            rounded = rounded.setScale(0);
        }
        String s = rounded.toPlainString();
        // Avoid emitting "-0", which some controllers parse but nobody wants to read.
        return "-0".equals(s) ? "0" : s;
    }

    /** True if the value rounds to zero at the emitted precision, i.e. the word can be dropped. */
    static boolean isZero(double value) {
        return "0".equals(fmt(value));
    }
}
