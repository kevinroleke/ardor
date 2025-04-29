/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
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

package nxt.http.client;

import nxt.Tester;
import nxt.addons.JO;
import nxt.http.callers.IssueAssetCall;

import static nxt.blockchain.ChildChain.IGNIS;

public class IssueAssetBuilder {

    public static final int ASSET_QNT = 10000000;
    public static final int ASSET_DECIMALS = 4;
    public static final long ASSET_ISSUE_FEE_NQT = 1000 * IGNIS.ONE_COIN;

    private final String secretPhrase;
    private final String name;
    private String description = "asset testing";
    private long quantityQNT = ASSET_QNT;
    private int decimals = ASSET_DECIMALS;
    private long feeNQT = ASSET_ISSUE_FEE_NQT;
    private int deadline = 1440;

    public IssueAssetBuilder(Tester creator, String name) {
        secretPhrase = creator.getSecretPhrase();
        this.name = name;
    }

    public IssueAssetResult issueAsset() {
        return new IssueAssetResult(invokeNoErr());
    }

    private JO invokeNoErr() {
        return IssueAssetCall.create(IGNIS.getId())
                .secretPhrase(secretPhrase)
                .name(name)
                .description(description)
                .quantityQNT(quantityQNT)
                .decimals(decimals)
                .feeNQT(feeNQT)
                .deadline(deadline)
                .callNoError();
    }

    public IssueAssetBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public IssueAssetBuilder setQuantityQNT(long quantityQNT) {
        this.quantityQNT = quantityQNT;
        return this;
    }

    public IssueAssetBuilder setDecimals(int decimals) {
        this.decimals = decimals;
        return this;
    }

    public IssueAssetBuilder setFeeNQT(long feeNQT) {
        this.feeNQT = feeNQT;
        return this;
    }

    public IssueAssetBuilder setDeadline(int deadline) {
        this.deadline = deadline;
        return this;
    }

    public static class IssueAssetResult {
        private final JO jsonObject;

        IssueAssetResult(JO jsonObject) {
            this.jsonObject = jsonObject;
        }

        public long getAssetId() {
            return Long.parseUnsignedLong(getAssetIdString());
        }

        public String getAssetIdString() {
            return Tester.responseToStringId(jsonObject);
        }
    }
}