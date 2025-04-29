package nxt.http.atomictxs;

import nxt.BlockchainTest;
import nxt.Constants;
import nxt.Nxt;
import nxt.Tester;
import nxt.addons.JO;
import nxt.blockchain.FxtChain;
import nxt.blockchain.TransactionProcessorTest;
import nxt.http.APICall;
import nxt.http.PhasingParamsBuilder;
import nxt.http.PhasingParamsHelper;
import nxt.http.callers.ApproveTransactionCall;
import nxt.http.callers.BroadcastTransactionCall;
import nxt.http.callers.BundleTransactionsCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SignTransactionCall;
import nxt.voting.VoteWeighting;
import org.junit.Assert;
import org.junit.Test;

import static nxt.blockchain.ChildChain.IGNIS;

public class AtomicTransactionsTest extends BlockchainTest {
    @Test
    public void testSuccess() {
        JO parent = createParent().callNoError();

        JO child = createChild(parent).callNoError();

        signAndBroadcastParent(parent, child);

        generateBlock();

        Assert.assertEquals(9 * IGNIS.ONE_COIN, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-11 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }


    @Test
    public void testChildCannotBeBundled() {
        JO parent = createParent()
                .callNoError();

        JO child = createChild(parent).callNoError();

        APICall.InvocationError err = BundleTransactionsCall.create(FxtChain.FXT.getId())
                .secretPhrase(CHUCK.getSecretPhrase())
                .transactionFullHash(child.getString("fullHash"))
                .build().invokeWithError();

        Assert.assertEquals("ChildBlock contains atomic child(ren) without atomic parent: [chain: IGNIS, full hash: "
                + child.getString("fullHash") + "]", err.getErrorDescription());
    }

    @Test
    public void testExpiringOrphan() {
        JO parent = createParent()
                .deadline(1440)
                .callNoError();


        int orphanDeadline = 200;
        createChild(parent)
                .atomicOrphanUnconfirmedPoolDeadline(orphanDeadline)
                .callNoError();

        int timestamp = parent.getJo("transactionJSON").getInt("timestamp");
        int expirationTime = timestamp + orphanDeadline;

        Assert.assertEquals(-21 * IGNIS.ONE_COIN, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));

        generateBlock();

        TransactionProcessorTest.processWaitingTransactions();

        Assert.assertEquals(-21 * IGNIS.ONE_COIN, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));

        while(Nxt.getEpochTime() <= expirationTime) {
            Nxt.getEpochTime();
        }

        generateBlock();

        TransactionProcessorTest.processWaitingTransactions();

        Assert.assertEquals(0, ALICE.getChainUnconfirmedBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testZeroFeeChild() {
        //This one should not be bundled
        SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .amountNQT(10 * IGNIS.ONE_COIN)
                .recipient(BOB.getId())
                .feeNQT(0)
                .callNoError();
        generateBlock();

        JO parent = createParent().feeNQT(2 * IGNIS.ONE_COIN).callNoError();

        JO child = createChild(parent).feeNQT(0).callNoError();

        signAndBroadcastParent(parent, child);

        generateBlock();

        Assert.assertEquals(10 * IGNIS.ONE_COIN, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-12 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testZeroFeeParent() {
        //This one should not be bundled
        SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(BOB.getSecretPhrase())
                .amountNQT(10 * IGNIS.ONE_COIN)
                .recipient(ALICE.getId())
                .feeNQT(0)
                .callNoError();

        JO parent = createParent().feeNQT(0).callNoError();

        JO child = createChild(parent).feeNQT(2 * IGNIS.ONE_COIN).callNoError();

        signAndBroadcastParent(parent, child);

        generateBlock();

        Assert.assertEquals(8 * IGNIS.ONE_COIN, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-10 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
    }

    @Test
    public void testChainOfThree() {
        //Bob to Alice 30
        JO parent = createParent().callNoError();
        //Chuck to Bob 20
        JO intermediate = createChild(parent)
                .secretPhrase(null)
                .publicKey(CHUCK.getPublicKeyStr())
                .atomicChildFullHash(Constants.ATOMIC_CHILD_HASH_TO_BE_DETERMINED)
                .callNoError();
        //Alice to Chuck 20
        JO child = createChild(intermediate)
                .recipient(CHUCK.getId()).callNoError();

        JO signedIntermediate = signAndBroadcastParent(intermediate, child, CHUCK.getSecretPhrase());
        signAndBroadcastParent(parent, signedIntermediate);
        generateBlock();

        Assert.assertEquals(9 * IGNIS.ONE_COIN, ALICE.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-11 * IGNIS.ONE_COIN, BOB.getChainBalanceDiff(IGNIS.getId()));
        Assert.assertEquals(-IGNIS.ONE_COIN, CHUCK.getChainBalanceDiff(IGNIS.getId()));
    }

    private static SendMoneyCall createParent() {
        return SendMoneyCall.create(IGNIS.getId())
                .publicKey(BOB.getPublicKeyStr())
                .amountNQT(30 * IGNIS.ONE_COIN)
                .recipient(ALICE.getId())
                .feeNQT(IGNIS.ONE_COIN)
                .atomicChildFullHash(Constants.ATOMIC_CHILD_HASH_TO_BE_DETERMINED);
    }

    private static SendMoneyCall createChild(JO parent) {
        return SendMoneyCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .amountNQT(20 * IGNIS.ONE_COIN)
                .recipient(BOB.getId())
                .atomicParentUnsignedHash(parent.getString("unsignedBytesHash"))
                .timestamp(parent.getJo("transactionJSON").getInt("timestamp"))
                .deadline(parent.getJo("transactionJSON").getInt("deadline"))
                .feeNQT(IGNIS.ONE_COIN);
    }

    private static JO signAndBroadcastParent(JO parent, JO child) {
        return signAndBroadcastParent(parent, child, BOB.getSecretPhrase());
    }

    private static JO signAndBroadcastParent(JO parent, JO child, String secret) {
        JO signedParent = SignTransactionCall.create()
                .unsignedTransactionBytes(parent.getString("unsignedTransactionBytes"))
                .atomicChildFullHash(child.getString("fullHash"))
                .validate(true)
                .secretPhrase(secret).callNoError();
        BroadcastTransactionCall.create().
                transactionBytes(signedParent.getString("transactionBytes")).
                callNoError();
        return signedParent;
    }
}
