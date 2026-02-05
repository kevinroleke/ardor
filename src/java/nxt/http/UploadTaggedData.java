/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt.http;

import nxt.NxtException;
import nxt.account.Account;
import nxt.taggeddata.TaggedDataAttachment;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

import static nxt.taggeddata.TaggedDataTransactionType.TAGGED_DATA_UPLOAD;

public final class UploadTaggedData extends CreateTransaction {

    static final UploadTaggedData instance = new UploadTaggedData();

    private UploadTaggedData() {
        super(Collections.singletonList(TAGGED_DATA_UPLOAD), Collections.singletonList("file"),
                new APITag[] {APITag.DATA, APITag.CREATE_TRANSACTION},
                "name", "description", "tags", "type", "channel", "isText", "filename", "data");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        Account account = ParameterParser.getSenderAccount(req);
        TaggedDataAttachment taggedDataUpload = ParameterParser.getTaggedData(req);
        return createTransaction(req, account, taggedDataUpload);

    }

}
