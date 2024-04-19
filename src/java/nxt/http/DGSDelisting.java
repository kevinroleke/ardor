/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
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

package nxt.http;

import nxt.NxtException;
import nxt.account.Account;
import nxt.blockchain.Attachment;
import nxt.dgs.DelistingAttachment;
import nxt.dgs.DigitalGoodsHome;
import nxt.dgs.DigitalGoodsTransactionType;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static nxt.http.JSONResponses.UNKNOWN_GOODS;

public final class DGSDelisting extends CreateTransaction {

    static final DGSDelisting instance = new DGSDelisting();

    private DGSDelisting() {
        super(DigitalGoodsTransactionType.DELISTING, new APITag[] {APITag.DGS, APITag.CREATE_TRANSACTION}, "goods");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {
        Account account = ParameterParser.getSenderAccount(req);
        DigitalGoodsHome.Goods goods = ParameterParser.getGoods(req);
        if (goods.isDelisted() || goods.getSellerId() != account.getId()) {
            return UNKNOWN_GOODS;
        }
        Attachment attachment = new DelistingAttachment(goods.getId());
        return createTransaction(req, account, attachment);
    }

}
