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

package nxt.addons;

import nxt.http.APICall;
import nxt.http.responses.BlockResponse;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;

public class ShutdownContext extends AbstractContractContext {

    public ShutdownContext(ContractRunnerConfig config, ContractAndSetupParameters contractAndSetupParameters, EventSource source) {
        super(config, contractAndSetupParameters.getName());
        this.contractSetupParameters = contractAndSetupParameters.getParamsRo();
        this.source = source;
    }

    @Override
    public BlockResponse getBlock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JO createTransaction(APICall.Builder builder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public JO createTransaction(APICall.Builder builder, boolean reduceFeeFromAmount) {
        throw new UnsupportedOperationException();
    }

    public void shutdown(ExecutorService executorService) {
        AccessController.doPrivileged((PrivilegedAction<Void>)() -> {
            executorService.shutdown();
            return null;
        });
    }
}
