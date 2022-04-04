/*
 * Copyright © 2021-2022 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */
package nxt.ae;

import nxt.NxtException;
import nxt.blockchain.TransactionType;
import org.json.simple.JSONArray;

import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum AssetControlTxTypesEnum {
    ASSET_TRANSFER(0, AssetExchangeTransactionType.ASSET_TRANSFER),
    ASK_ORDER_PLACEMENT(1, AssetExchangeTransactionType.ASK_ORDER_PLACEMENT),
    BID_ORDER_PLACEMENT(2, AssetExchangeTransactionType.BID_ORDER_PLACEMENT),
    ASK_ORDER_CANCELLATION(3, AssetExchangeTransactionType.ASK_ORDER_CANCELLATION),
    BID_ORDER_CANCELLATION(4, AssetExchangeTransactionType.BID_ORDER_CANCELLATION),
    DIVIDEND_PAYMENT(5, AssetExchangeTransactionType.DIVIDEND_PAYMENT),
    ASSET_DELETE(6, AssetExchangeTransactionType.ASSET_DELETE),
    ASSET_INCREASE(7, AssetExchangeTransactionType.ASSET_INCREASE),
    SET_ASSET_CONTROL(8, AssetExchangeTransactionType.SET_PHASING_CONTROL),
    ASSET_PROPERTY_SET(9, AssetExchangeTransactionType.ASSET_PROPERTY_SET),
    ASSET_PROPERTY_DELETE(10, AssetExchangeTransactionType.ASSET_PROPERTY_DELETE),
    SET_ASSET_TRADE_ROYALTIES(11, AssetExchangeTransactionType.SET_ASSET_TRADE_ROYALTIES);

    /**
     * These are the transaction types to which the first version of the Asset Control, introduced in 2.0.4, was applied.
     */
    public final static Set<AssetControlTxTypesEnum> DEFAULT_TYPES = Collections.unmodifiableSet(
            EnumSet.of(ASSET_TRANSFER, ASSET_DELETE, ASK_ORDER_PLACEMENT, BID_ORDER_PLACEMENT, DIVIDEND_PAYMENT));
    private final static Map<TransactionType, AssetControlTxTypesEnum> typeToControlTypeMap;
    private final int code;
    private final TransactionType transactionType;

    static {
        Map<TransactionType, AssetControlTxTypesEnum> map = new HashMap<>();
        for (AssetControlTxTypesEnum controlType: values()) {
            map.put(controlType.transactionType, controlType);
        }
        typeToControlTypeMap = Collections.unmodifiableMap(map);
    }

    AssetControlTxTypesEnum(int code, TransactionType transactionType) {
        this.code = code;
        this.transactionType = transactionType;
    }

    public int getCode() {
        return code;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public long toLongBitmask() {
        return 1L << code;
    }

    public static AssetControlTxTypesEnum getByTransactionType(TransactionType type) {
        return typeToControlTypeMap.get(type);
    }

    public static EnumSet<AssetControlTxTypesEnum> longBitmaskToSet(long longBitmask) {
        EnumSet<AssetControlTxTypesEnum> result = EnumSet.noneOf(AssetControlTxTypesEnum.class);
        for (AssetControlTxTypesEnum t : values()) {
            if ((longBitmask & (1L << t.code)) != 0) {
                result.add(t);
            }
        }
        return result;
    }

    public static long longBitmaskFromSet(Set<AssetControlTxTypesEnum> enumSet) {
        long result = 0;
        for (AssetControlTxTypesEnum t : enumSet) {
            result |= t.toLongBitmask();
        }
        return result;
    }

    public static EnumSet<AssetControlTxTypesEnum> bitSetToSet(BitSet bs) throws NxtException.NotValidException {
        EnumSet<AssetControlTxTypesEnum> result = EnumSet.noneOf(AssetControlTxTypesEnum.class);
        for (AssetControlTxTypesEnum t : values()) {
            if (bs.get(t.code)) {
                result.add(t);
                bs.clear(t.code);
            }
        }
        if (!bs.isEmpty()) {
            throw new NxtException.NotValidException("Unknown bits set in asset control transaction types bitmask: " + bs);
        }
        return result;
    }

    public static BitSet bitSetFromSet(Set<AssetControlTxTypesEnum> enumSet) {
        BitSet bitSet = new BitSet();
        for (AssetControlTxTypesEnum t : enumSet) {
            bitSet.set(t.code);
        }
        return bitSet;
    }

    public static JSONArray jsonArrayFromSet(Set<AssetControlTxTypesEnum> enumSet) {
        JSONArray result = new JSONArray();
        for (AssetControlTxTypesEnum t : enumSet) {
            result.add(t.toString());
        }
        return result;
    }
}
