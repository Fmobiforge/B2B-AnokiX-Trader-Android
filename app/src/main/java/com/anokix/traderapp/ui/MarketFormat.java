package com.anokix.traderapp.ui;

import java.util.Locale;

/** Number formatting shared by the marketplace screens (matches Product.formattedPrice). */
final class MarketFormat {

    private MarketFormat() {
    }

    /** "R" + 1500.5 → "R1,500.50". */
    static String money(String symbol, double value) {
        return (symbol == null ? "" : symbol) + String.format(Locale.US, "%,.2f", value);
    }

    /** 1500 → "1,500". */
    static String count(int value) {
        return String.format(Locale.US, "%,d", value);
    }
}
