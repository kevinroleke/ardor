/*
 * Copyright © 2013-2016 The Nxt Core Developers.
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

package nxt.ms;

import nxt.BlockchainTest;
import nxt.DeleteFileRule;
import nxt.Tester;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.dbschema.Db;
import nxt.http.APICall;
import nxt.http.callers.SetAccountPropertyCall;
import nxt.http.callers.TransferCurrencyCall;
import nxt.http.monetarysystem.TestCurrencyIssuance;
import nxt.util.JSONAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;

@Ignore("test fails because ChildChain.AEUR already has snapshot file")
public class CurrencyMigrateMonitorTest extends BlockchainTest {
    @Rule
    public final DeleteFileRule deleteFileRule = new DeleteFileRule();

    private final ChildChain targetChain = ChildChain.AEUR;
    private final long chuckQnt = 3;
    private final long bobQnt = 2;

    private Tester currencyOwner;

    @Before
    public void setUp() {
        currencyOwner = TestCurrencyIssuance.CREATOR;
    }

    @Test
    public void migratesCurrencyToChildChainWithoutSnapshot() {
        Currency currency = createCurrencyWithBalances();

        generateBlock();

        int freezeHeight = getHeight();
        setCurrencyFreezeHeight(currency, freezeHeight);
        setCurrencyMigrationHeight(currency, getHeight() + 1);
        generateBlock();

        assertTokensDistributed();
    }

    @Test
    public void migratesCurrencyToChildChainTriggeredByAlias() {
        Currency currency = createCurrencyWithBalances();

        generateBlock();

        int freezeHeight = getHeight();
        setCurrencyFreezeHeight(currency, freezeHeight);
        setCurrencyMigrationHeight(currency, 0);
        setCurrencyMigrationHeightAccountProperty(currency, freezeHeight + 2);

        generateBlock();
        generateBlock();

        assertTokensDistributed();
    }

    private void setCurrencyMigrationHeightAccountProperty(Currency currency, int height) {
        SetAccountPropertyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(ALICE.getId())
                .feeNQT(3 * ChildChain.IGNIS.ONE_COIN)
                .property(Currency.CURRENCY_MIGRATE_HEIGHT_PROPERTY_PREFIX + Long.toUnsignedString(currency.getId()))
                .value(Integer.toString(height))
                .feeNQT(ChildChain.IGNIS.ONE_COIN)
                .callNoError();
    }

    @Test
    public void migratesCurrencyToChildChainUsingSnapshot() throws URISyntaxException {
        Currency currency = createCurrency();
        generateBlock();

        int freezeHeight = getHeight();
        setCurrencyFreezeHeight(currency, freezeHeight);
        createFakeSnapshot(currency, freezeHeight);
        setCurrencyMigrationHeight(currency, getHeight() + 1);
        generateBlock();

        assertTokensDistributed();
    }

    private void createFakeSnapshot(Currency currency, int freezeHeight) throws URISyntaxException {
        HashMap<String, Long> snapshot = new HashMap<>();
        snapshot.put(Long.toUnsignedString(BOB.getId()), bobQnt);
        snapshot.put(Long.toUnsignedString(CHUCK.getId()), chuckQnt);
        File file = new CurrencySnapshot().writeSnapshot(snapshot, currency.getId(), freezeHeight);
        deleteFileRule.addFile(file);
        deleteFileRule.moveToTestClasspath(file);
    }

    private void assertTokensDistributed() {
        Assert.assertEquals(bobQnt, BOB.getChainBalanceDiff(targetChain.getId()));
        Assert.assertEquals(chuckQnt, CHUCK.getChainBalanceDiff(targetChain.getId()));
    }

    private void setCurrencyMigrationHeight(Currency currency, int height) {
        Db.db.runInDbTransaction(() -> {
            CurrencyMigrateMonitor.enableMigration(currency.getId(), targetChain, 0, height);
        });
    }

    private APICall createTransferCurrencyCall(Tester recipient, Currency currency, long value) {
        return TransferCurrencyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(currencyOwner.getSecretPhrase())
                .currency(currency.getId())
                .recipient(recipient.getStrId())
                .unitsQNT(value)
                .feeNQT(ChildChain.IGNIS.ONE_COIN)
                .build();
    }

    private void setCurrencyFreezeHeight(Currency currency, int height) {
        Db.db.runInDbTransaction(() -> CurrencyFreezeMonitor.enableFreeze(currency.getId(), 1, height));
    }

    private Currency createCurrencyWithBalances() {
        Currency currency = createCurrency();
        distributeCurrency(currency);
        return currency;
    }

    private void distributeCurrency(Currency currency) {
        createTransferCurrencyCall(BOB, currency, bobQnt).invokeNoError();
        createTransferCurrencyCall(CHUCK, currency, chuckQnt).invokeNoError();
        generateBlock();
    }

    private Currency createCurrency() {
        JO jsonObject = TestCurrencyIssuance.builder().callNoError();
        String currencyId = Tester.hexFullHashToStringId(new JSONAssert(jsonObject).str("fullHash"));
        generateBlock();
        return Currency.getCurrency(Long.parseUnsignedLong(currencyId));
    }
}