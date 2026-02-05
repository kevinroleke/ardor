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

package com.jelurida.ardor.contracts;

import nxt.addons.AbstractContract;
import nxt.addons.InitializationContext;

/**
 * This contract confirms fix to ContractLoader which allows usage of sub-class methods accepting sub-class instances
 * as parameters.
 */
public class ContractWithInnerClassParameter extends AbstractContract<Object, Object> {
    private IMethod a;

    @Override
    public void init(InitializationContext context) {
        a = new MethodWithParam();
    }

    public static class Param {
    }

    public interface IMethod {
        void doSomething(Param param);
    }

    public static class MethodWithParam implements IMethod {
        @Override
        public void doSomething(Param param) {
        }
    }
}
