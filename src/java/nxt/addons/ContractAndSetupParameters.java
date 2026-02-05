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

package nxt.addons;

import nxt.util.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ContractAndSetupParameters {
    private final String name;
    private final Contract<?,?> contract;
    private final JO paramsRo;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public ContractAndSetupParameters(String name, Contract<?,?> contract, JO params) {
        this.name = name;
        this.contract = contract;
        this.paramsRo = JO.unmodifiable(params);
    }

    public String getName() {
        return name;
    }

    public <I,O> Contract<I,O> getContract() {
        return (Contract<I,O>) contract;
    }

    /**
     * Returns a {@link JO#copy(JO) copy} of the contract parameters
     * @return A JSON object
     */
    public JO getParams() {
        return JO.copy(paramsRo);
    }

    /**
     * Returns an {@link JO#unmodifiable(JO) unmodifiable} version of the contract parameters
     * @return An unmodifiable JSON object
     */
    public JO getParamsRo() {
        return paramsRo;
    }

    void init(Supplier<InitializationContext> contextSupplier) {
        boolean wasInitialized = initialized.getAndSet(true);
        if (!wasInitialized) {
            Logger.logInfoMessage("Initializing contract %s with object identity %s", name, System.identityHashCode(contract));
            try {
                contract.init(contextSupplier.get());
            } catch (Throwable t) {
                Logger.logWarningMessage("Initialization failed for contract " + name, t);
            }
        }
    }

    void shutdown(Supplier<ShutdownContext> contextSupplier) {
        boolean wasInitialized = initialized.getAndSet(false);
        if (wasInitialized) {
            try {
                contract.shutdown(contextSupplier.get());
            } catch (Throwable t) {
                Logger.logWarningMessage("Shutdown failed for contract " + name, t);
            }
            Logger.logInfoMessage("Contract %s with object identity %s was shutdown", name, System.identityHashCode(contract));
        }
    }
}
