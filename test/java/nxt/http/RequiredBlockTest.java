package nxt.http;

import nxt.BlockchainTest;
import nxt.Nxt;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.blockchain.FxtChain;
import nxt.http.callers.BundleTransactionsCall;
import nxt.http.callers.GetAccountBlocksCall;
import nxt.http.callers.GetBlockCall;
import nxt.http.callers.GetBlocksCall;
import nxt.http.callers.GetFxtTransactionCall;
import nxt.http.callers.GetTransactionCall;
import nxt.http.callers.ParseTransactionCall;
import nxt.http.callers.SendMoneyCall;
import nxt.http.callers.SignTransactionCall;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RequiredBlockTest extends BlockchainTest {

    @Before
    public void setUp() {
        generateBlocks(10);
    }

    @Test
    public void testGetBlock() {
        GetBlockCall.create()
                .requireBlock(Nxt.getBlockchain().getBlockAtHeight(3).getStringId())
                .height(4)
                .callNoError();
    }

    @Test
    public void testGetBlocks() {
        JO jo = GetBlocksCall.create()
                .requireBlock(Nxt.getBlockchain().getBlockAtHeight(3).getStringId())
                .callNoError();
        Assert.assertEquals(12, jo.getArray("blocks").size());
    }

    @Test
    public void testGetAccountBlocks() {
        JO jo = GetAccountBlocksCall.create()
                .requireBlock(Nxt.getBlockchain().getBlockAtHeight(3).getStringId())
                .account(FORGY.getId())
                .callNoError();

        Assert.assertEquals(10, jo.getArray("blocks").size());
    }

    @Test
    public void testGetFxtTransaction() {

        JO jo = SendMoneyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getId()).amountNQT(2 * ChildChain.IGNIS.ONE_COIN)
                .feeNQT(ChildChain.IGNIS.ONE_COIN).callNoError();
        String fullHash = jo.getString("fullHash");
        generateBlock();
        JO child = GetTransactionCall.create(ChildChain.IGNIS.getId()).fullHash(fullHash).callNoError();

        JO parent = GetFxtTransactionCall.create()
                .requireBlock(Nxt.getBlockchain().getBlockAtHeight(3).getStringId())
                .transaction(child.getString("fxtTransaction"))
                .includeChildTransactions(true)
                .callNoError();
        Assert.assertEquals(1, parent.getArray("childTransactions").size());
    }

    @Test
    public void testSignChildBlockTransactionWithValidation() {
        JO child = SendMoneyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getId())
                .amountNQT(100 * ChildChain.IGNIS.ONE_COIN)
                .feeNQT(0).callNoError();

        JO unsigned = BundleTransactionsCall.create(FxtChain.FXT.getId())
                .publicKey(ALICE.getPublicKeyStr())
                .transactionFullHash(child.getString("fullHash")).callNoError();

        JO attachment = unsigned.getJo("transactionJSON").getJo("attachment");
        SignTransactionCall.create()
                .validate(true)
                .requireBlock(Nxt.getBlockchain().getBlockAtHeight(3).getStringId())
                .secretPhrase(ALICE.getSecretPhrase())
                .unsignedTransactionBytes(unsigned.getString("unsignedTransactionBytes"))
                .prunableAttachmentJSON(attachment.toJSONString())
                .callNoError();
    }

    @Test
    public void testParseChildBlockTransactionWithValidation() {
        JO child = SendMoneyCall.create(ChildChain.IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getId())
                .amountNQT(100 * ChildChain.IGNIS.ONE_COIN)
                .feeNQT(0).callNoError();

        JO unsigned = BundleTransactionsCall.create(FxtChain.FXT.getId())
                .publicKey(ALICE.getPublicKeyStr())
                .transactionFullHash(child.getString("fullHash")).callNoError();

        JO attachment = unsigned.getJo("transactionJSON").getJo("attachment");

        ParseTransactionCall.create()
                .setParamValidation(true)
                .requireBlock(Nxt.getBlockchain().getBlockAtHeight(3).getStringId())
                .transactionBytes(unsigned.getString("unsignedTransactionBytes"))
                .prunableAttachmentJSON(attachment.toJSONString())
                .callNoError();
    }
}