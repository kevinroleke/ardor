/*
 * Copyright © 2021-2023 Jelurida IP B.V.
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
package nxt.util;

import nxt.util.bbh.ObjectRw;
import org.json.simple.JSONObject;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

public final class FixedPrecisionPercentage {
    private static final int DECIMAL_PLACES = 7;
    private static final int ONE_PERCENT = 10000000;
    private static final int MIN_VALUE = 0;
    private static final int MAX_VALUE = 100 * ONE_PERCENT;
    public static final ObjectRw<FixedPrecisionPercentage> RW = new ObjectRw<FixedPrecisionPercentage>() {
        @Override
        public int getSize(FixedPrecisionPercentage fixedPrecisionPercentage) {
            return 4;
        }

        @Override
        public FixedPrecisionPercentage readFromBuffer(ByteBuffer buffer) {
            return new FixedPrecisionPercentage(buffer.getInt());
        }

        @Override
        public void writeToBuffer(FixedPrecisionPercentage fixedPrecisionPercentage, ByteBuffer buffer) {
            buffer.putInt(fixedPrecisionPercentage.value);
        }

        @Override
        public boolean validate(FixedPrecisionPercentage fixedPrecisionPercentage) {
            return fixedPrecisionPercentage.value >= MIN_VALUE && fixedPrecisionPercentage.value <= MAX_VALUE;
        }
    };

    private final int value;

    private FixedPrecisionPercentage(int value) {
        this.value = value;
    }

    public static FixedPrecisionPercentage fromBigDecimal(BigDecimal decimalVal) {
        FixedPrecisionPercentage result = new FixedPrecisionPercentage(decimalVal.movePointRight(DECIMAL_PLACES).intValueExact());
        if (!result.validate()) {
            throw new ArithmeticException("Percentage out of range [0, 100] " + decimalVal);
        }
        return result;
    }

    public static FixedPrecisionPercentage read(JSONObject json, String field) {
        return fromBigDecimal(new BigDecimal((String) json.get(field)));
    }

    public static FixedPrecisionPercentage read(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        if (rs.wasNull()) {
            return null;
        }
        return new FixedPrecisionPercentage(value);
    }

    public void write(JSONObject json, String field) {
        json.put(field, toString());
    }

    public static int setToPreparedStatement(FixedPrecisionPercentage percentage, PreparedStatement pstmt, int i) throws SQLException {
        if (percentage == null) {
            pstmt.setNull(++i, Types.INTEGER);
        } else {
            pstmt.setInt(++i, percentage.value);
        }
        return i;
    }

    public int compareTo(int other) {
        return Integer.compare(value, other * ONE_PERCENT);
    }

    public boolean validate() {
        return RW.validate(this);
    }

    public long calcPercentageOfAmount(long amount) {
        return Convert.longValueExact(BigDecimal.valueOf(value).
                multiply(BigDecimal.valueOf(amount)).
                movePointLeft(DECIMAL_PLACES + 2).toBigInteger());
    }

    BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(value).movePointLeft(DECIMAL_PLACES);
    }

    @Override
    public String toString() {
        return toBigDecimal().stripTrailingZeros().toPlainString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixedPrecisionPercentage that = (FixedPrecisionPercentage) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
