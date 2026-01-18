/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2026 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.addons.taxreport;

enum Column {
    TYPE("Type"),
    BUY_VOLUME("Buy"),
    BUY_CURRENCY("Cur."),
    SELL_VOLUME("Sell"),
    SELL_CURRENCY("Cur."),
    FEE_VOLUME("Fee"),
    FEE_CURRENCY("Cur."),
    EXCHANGE("Exchange"),
    GROUP("Group"),
    COMMENT("Comment"),
    DATE("Date");

    private final String label;

    Column(String label) {
        this.label = label;
    }

    String getLabel() {
        return label;
    }
}
