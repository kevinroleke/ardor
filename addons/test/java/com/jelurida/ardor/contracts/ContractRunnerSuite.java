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

package com.jelurida.ardor.contracts;

import com.jelurida.ardor.contracts.trading.AccountBalanceNotifierTest;
import com.jelurida.ardor.contracts.trading.CoinExchangeTradingBotLegacyTest;
import com.jelurida.ardor.contracts.trading.CoinExchangeTradingBotTest;
import nxt.SafeShutdownSuite;
import nxt.addons.ParamInvocationHandlerTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        HelloWorldTest.class,
        HelloWorldForwarderTest.class,
        AllForOnePaymentTest.class,
        ChildToParentExchangeTest.class,
        LeaseRenewalTest.class,
        RandomPaymentTest.class,
        SplitPaymentTest.class,
        PropertyBasedLotteryTest.class,
        IgnisArdorRatesTest.class,
        NewAccountFaucetTest.class,
        ForgingRewardTest.class,
        ContractUnderAccountControlTest.class,
        VersionComparisonTest.class,
        ContractManagerTest.class,
        ContractLoaderTest.class,
        AllowedActionsTest.class,
        ForbiddenActionsTest.class,
        DatabaseAccessTest.class,
        LiberlandCitizenRegistryTest.class,
        ParamInvocationHandlerTest.class,
        ContractWithInnerInterfaceTest.class,
        GetRandomNumberTest.class,
        CoinExchangeOperationsTest.class,
        CoinExchangeTradingBotTest.class,
        CoinExchangeTradingBotLegacyTest.class,
        ContractWithInnerClassParameterTest.class,
        AccountBalanceNotifierTest.class,
        WhaleAlertTest.class,
        ContractRunnerFeeCalculationTest.class,
        ReferencedTransactionDepositTest.class,
        DeadlineTest.class,
        ReadOnlyContractTest.class,
})
public class ContractRunnerSuite extends SafeShutdownSuite {
}
