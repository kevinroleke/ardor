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

import nxt.Constants;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class FixedPrecisionPercentageTest {

    @Test
    public void testCompareTo() {
        FixedPrecisionPercentage percentage = FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(10.5));
        Assert.assertTrue(percentage.compareTo(11) < 0);
        Assert.assertTrue(percentage.compareTo(10) > 0);
        assertEquals(0, FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(99)).compareTo(99));
        assertEquals(0, FixedPrecisionPercentage.fromBigDecimal(BigDecimal.ZERO).compareTo(0));
    }

    @Test
    public void testPercentageOfAmount() {
        Assert.assertEquals( 830004, FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(83.0004)).calcPercentageOfAmount(1000000));
        Assert.assertEquals( 0, FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(1)).calcPercentageOfAmount(0));
        Assert.assertEquals( Constants.MAX_BALANCE_NQT, FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(100)).calcPercentageOfAmount(Constants.MAX_BALANCE_NQT));
        Assert.assertEquals( Constants.ONE_FXT, FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(0.0000001)).calcPercentageOfAmount(Constants.MAX_BALANCE_NQT));
        try {
            Assert.assertEquals( Constants.ONE_FXT, FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(0.00000001)).calcPercentageOfAmount(Constants.MAX_BALANCE_NQT));
            Assert.fail("should throw");
        } catch (ArithmeticException e) {
            Assert.assertEquals("Rounding necessary", e.getMessage());
        }
    }

    @Test
    public void testJsonWriteRead() throws ParseException {
        BigDecimal percentage = BigDecimal.valueOf(23.1);
        Assert.assertEquals(percentage, writeAndReadToJson(percentage));

        percentage = new BigDecimal("23.0000001");
        Assert.assertEquals(percentage, writeAndReadToJson(percentage));

        percentage = new BigDecimal("0.000000");
        Assert.assertEquals(new BigDecimal("0"), writeAndReadToJson(percentage));

        percentage = new BigDecimal("100");
        Assert.assertEquals(new BigDecimal("100"), writeAndReadToJson(percentage));

        percentage = new BigDecimal("50.000");
        Assert.assertEquals(new BigDecimal("50"), writeAndReadToJson(percentage));
    }

    private BigDecimal writeAndReadToJson(BigDecimal percentage) throws ParseException {
        JSONObject json = new JSONObject();
        String fieldName = "percentage";
        FixedPrecisionPercentage.fromBigDecimal(percentage).write(json, fieldName);

        String jsonString = json.toJSONString();

        JSONObject parsedJson = (JSONObject) JSONValue.parseWithException(jsonString);
        FixedPrecisionPercentage readPercentage = FixedPrecisionPercentage.read(parsedJson, fieldName);

        BigDecimal jsonPercentage = new BigDecimal((String)parsedJson.get(fieldName));
        Assert.assertEquals(0, jsonPercentage.compareTo(readPercentage.toBigDecimal()));
        return jsonPercentage;
    }

    @Test
    public void testValidate() {
        try {
            FixedPrecisionPercentage.fromBigDecimal(BigDecimal.valueOf(100.004));
            Assert.fail("exception expected");
        } catch (ArithmeticException ignore) {}

        Assert.assertTrue(FixedPrecisionPercentage.fromBigDecimal(new BigDecimal("100.0000")).validate());
        Assert.assertTrue(FixedPrecisionPercentage.fromBigDecimal(new BigDecimal("0.0000")).validate());
        Assert.assertTrue(FixedPrecisionPercentage.fromBigDecimal(new BigDecimal("0.0001")).validate());
        try {
            FixedPrecisionPercentage.fromBigDecimal(new BigDecimal("-0.0001"));
            Assert.fail("exception expected");
        } catch (ArithmeticException ignore) {}
    }

    @Test
    public void testPrecisionLoss() {
        try {
            Assert.assertTrue(FixedPrecisionPercentage.fromBigDecimal(new BigDecimal("100.00000001")).validate());
            Assert.fail("exception expected");
        } catch (ArithmeticException ignore) {}
        try {
            Assert.assertTrue(FixedPrecisionPercentage.fromBigDecimal(new BigDecimal("0.00000005")).validate());
            Assert.fail("exception expected");
        } catch (ArithmeticException ignore) {}

    }
}