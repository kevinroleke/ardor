/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2021 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of this software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.http;

import nxt.BlockchainTest;
import nxt.Constants;
import nxt.Nxt;
import nxt.RequireNonePermissionPolicyTestsCategory;
import nxt.account.Account;
import nxt.addons.JO;
import nxt.blockchain.ChildChain;
import nxt.blockchain.Fee;
import nxt.crypto.Crypto;
import nxt.crypto.EncryptedData;
import nxt.http.callers.GetAccountCall;
import nxt.http.callers.ReadMessageCall;
import nxt.http.callers.SendMessageCall;
import nxt.messaging.MessagingTransactionType.MessageEvent;
import nxt.util.Convert;
import nxt.util.JSONAssert;
import nxt.util.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collections;

import static nxt.blockchain.ChildChain.IGNIS;

public class SendMessageTest extends BlockchainTest {
    @Rule
    public final MessageListenerRule messageListenerRule = new MessageListenerRule();

    public static final String NON_EXISTENT_ACCOUNT_SECRET = "NonExistentAccount.jkgdkjgdjkfgfkjgfjkdfgkjjdk";
    public static final byte[] NON_EXISTENT_ACCOUNT_PRIVATE_KEY = Crypto.getPrivateKey(NON_EXISTENT_ACCOUNT_SECRET);
    public static final String ANOTHER_ACCOUNT_SECRET = "another" + NON_EXISTENT_ACCOUNT_SECRET;
    public static final byte[] ANOTHER_ACCOUNT_PRIVATE_KEY = Crypto.getPrivateKey(ANOTHER_ACCOUNT_SECRET);

    @Test
    public void sendMessage() {
        JO response = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                message("hello world").
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMessage: " + response);
        String transaction = response.getString("fullHash");
        JO attachment = response.getJo("transactionJSON").getJo("attachment");
        Assert.assertEquals("hello world", attachment.get("message"));
        generateBlock();
        response = ReadMessageCall.create().
                secretPhrase(BOB.getSecretPhrase()).
                transactionFullHash(transaction).
                callNoError();
        Logger.logDebugMessage("readMessage: " + response);
        Assert.assertEquals("hello world", response.get("message"));
    }

    @Test
    public void sendEncryptedMessage() {
        JO response = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                messageToEncrypt("hello world").
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMessage: " + response);
        String transaction = response.getString("fullHash");
        JO attachment = response.getJo("transactionJSON").getJo("attachment");
        JO encryptedMessage = attachment.getJo("encryptedMessage");
        Assert.assertNotEquals(64, encryptedMessage.getString("data").length());
        Assert.assertNotEquals(32, encryptedMessage.getString("nonce").length());
        generateBlock();
        response = ReadMessageCall.create().
                secretPhrase(BOB.getSecretPhrase()).
                transactionFullHash(transaction).
                callNoError();
        Logger.logDebugMessage("readMessage: " + response);
        Assert.assertEquals("hello world", response.get("decryptedMessage"));
    }

    @Test
    public void sendClientEncryptedMessage() {
        EncryptedData encryptedData = BOB.getAccount().encryptTo(ALICE.getPrivateKey(), Convert.toBytes("hello world"), true);
        JO response = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                encryptedMessageData(encryptedData.getData()).
                encryptedMessageNonce(encryptedData.getNonce()).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMessage: " + response);
        String transaction = response.getString("fullHash");
        JO attachment = response.getJo("transactionJSON").getJo("attachment");
        JO encryptedMessage = attachment.getJo("encryptedMessage");
        Assert.assertNotEquals(64, encryptedMessage.getString("data").length());
        Assert.assertNotEquals(32, encryptedMessage.getString("nonce").length());
        generateBlock();
        response = ReadMessageCall.create().
                secretPhrase(BOB.getSecretPhrase()).
                transactionFullHash(transaction).
                callNoError();
        Logger.logDebugMessage("readMessage: " + response);
        Assert.assertEquals("hello world", response.get("decryptedMessage"));
    }

    @Test
    public void sendEncryptedMessageToSelf() {
        JO response = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                messageToEncryptToSelf("hello world").
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMessage: " + response);
        String transaction = response.getString("fullHash");
        JO attachment = response.getJo("transactionJSON").getJo("attachment");
        JO encryptedMessage = attachment.getJo("encryptToSelfMessage");
        Assert.assertNotEquals(64, encryptedMessage.getString("data").length());
        Assert.assertNotEquals(32, encryptedMessage.getString("nonce").length());
        generateBlock();
        response = ReadMessageCall.create().
                secretPhrase(ALICE.getSecretPhrase()).
                transactionFullHash(transaction).
                callNoError();
        Logger.logDebugMessage("readMessage: " + response);
        Assert.assertEquals("hello world", response.get("decryptedMessageToSelf"));
    }

    @Test
    public void sendClientEncryptedMessageToSelf() {
        EncryptedData encryptedData = ALICE.getAccount().encryptTo(ALICE.getPrivateKey(), Convert.toBytes("hello world"),  true);
        JO response = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(BOB.getStrId()).
                encryptToSelfMessageData(encryptedData.getData()).
                encryptToSelfMessageNonce(encryptedData.getNonce()).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMessage: " + response);
        String transaction = response.getString("fullHash");
        JO attachment = response.getJo("transactionJSON").getJo("attachment");
        JO encryptedMessage = attachment.getJo("encryptToSelfMessage");
        Assert.assertEquals(64 + 32 /* data + hash */, encryptedMessage.getString("data").length());
        Assert.assertEquals(64, encryptedMessage.getString("nonce").length());
        generateBlock();
        response = ReadMessageCall.create().
                secretPhrase(ALICE.getSecretPhrase()).
                transactionFullHash(transaction).
                callNoError();
        Logger.logDebugMessage("readMessage: " + response);
        Assert.assertEquals("hello world", response.get("decryptedMessageToSelf"));
    }

    @Category(RequireNonePermissionPolicyTestsCategory.class)
    @Test
    public void publicKeyAnnouncement() {
        byte[] publicKey = Crypto.getPublicKey(Crypto.getPrivateKey(NON_EXISTENT_ACCOUNT_SECRET));
        String publicKeyStr = Convert.toHexString(publicKey);
        long id = Account.getId(publicKey);
        String rsAccount = Convert.rsAccount(id);

        JO response = GetAccountCall.create().
                account(rsAccount).
                call();
        Logger.logDebugMessage("getAccount: " + response);
        Assert.assertEquals(5, response.getLong("errorCode"));

        response = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(ALICE.getSecretPhrase()).
                recipient(rsAccount).
                recipientPublicKey(publicKeyStr).
                feeNQT(IGNIS.ONE_COIN).
                callNoError();
        Logger.logDebugMessage("sendMessage: " + response);
        generateBlock();

        response = GetAccountCall.create().
                account(rsAccount).
                callNoError();
        Logger.logDebugMessage("getAccount: " + response);
        Assert.assertEquals(publicKeyStr, response.get("publicKey"));
    }

    @Category(RequireNonePermissionPolicyTestsCategory.class)
    @Test
    public void sendFromNotExistingToExistingAccount() {
        Assert.assertFalse(Account.hasAccount(Account.getId(Crypto.getPublicKey(NON_EXISTENT_ACCOUNT_PRIVATE_KEY)), Nxt.getBlockchain().getHeight()));
        SendMessageCall builder = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(NON_EXISTENT_ACCOUNT_SECRET).
                message("hello world").
                recipient(ALICE.getRsAccount()).
                feeNQT(ChildChain.IGNIS.ONE_COIN);
        JSONAssert result = new JSONAssert(builder.call());
        Assert.assertEquals("Not enough funds", result.str("errorDescription"));

        builder.feeNQT(0);
        result = new JSONAssert(builder.call());
        Logger.logDebugMessage("response" + result.getJson());
        Assert.assertEquals(Fee.NEW_ACCOUNT_FEE + Constants.ONE_FXT / 100, Convert.parseLong(result.getJson().get("minimumFeeFQT")));
        bundleTransactions(Collections.singletonList(result.fullHash()));

        generateBlock();
    }

    @Category(RequireNonePermissionPolicyTestsCategory.class)
    @Test
    public void sendFromNotExistingAccountToSelf() {
        Assert.assertFalse(Account.hasAccount(Account.getId(Crypto.getPublicKey(NON_EXISTENT_ACCOUNT_PRIVATE_KEY)), Nxt.getBlockchain().getHeight()));
        SendMessageCall builder = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(NON_EXISTENT_ACCOUNT_SECRET).
                message("hello world").
                recipient(Account.getId(Crypto.getPublicKey(NON_EXISTENT_ACCOUNT_PRIVATE_KEY))).
                feeNQT(ChildChain.IGNIS.ONE_COIN);
        JSONAssert result = new JSONAssert(builder.call());
        Assert.assertEquals("Not enough funds", result.str("errorDescription"));

        builder.feeNQT(0);
        result = new JSONAssert(builder.call());
        Logger.logDebugMessage("response" + result.getJson());
        Assert.assertEquals(Fee.NEW_ACCOUNT_FEE + Constants.ONE_FXT / 100, Convert.parseLong(result.getJson().get("minimumFeeFQT")));
        bundleTransactions(Collections.singletonList(result.fullHash()));

        generateBlock();
    }

    @Category(RequireNonePermissionPolicyTestsCategory.class)
    @Test
    public void sendFromNotExistingToNotExistingAccount() {
        Assert.assertFalse(Account.hasAccount(Account.getId(Crypto.getPublicKey(NON_EXISTENT_ACCOUNT_PRIVATE_KEY)), Nxt.getBlockchain().getHeight()));
        Assert.assertFalse(Account.hasAccount(Account.getId(Crypto.getPublicKey(ANOTHER_ACCOUNT_PRIVATE_KEY)), Nxt.getBlockchain().getHeight()));
        SendMessageCall builder = SendMessageCall.create(IGNIS.getId()).
                secretPhrase(NON_EXISTENT_ACCOUNT_SECRET).
                message("hello world").
                recipient(Account.getId(Crypto.getPublicKey(ANOTHER_ACCOUNT_PRIVATE_KEY))).
                feeNQT(ChildChain.IGNIS.ONE_COIN);
        JSONAssert result = new JSONAssert(builder.call());
        Assert.assertEquals("Not enough funds", result.str("errorDescription"));

        builder.feeNQT(0);
        result = new JSONAssert(builder.call());
        Logger.logDebugMessage("response" + result.getJson());
        Assert.assertEquals(2 * Fee.NEW_ACCOUNT_FEE + Constants.ONE_FXT / 100, Convert.parseLong(result.getJson().get("minimumFeeFQT")));
        bundleTransactions(Collections.singletonList(result.fullHash()));

        generateBlock();
    }

    @Test
    public void listenToMessages() {
        ChildChain chain = IGNIS;
        SendMessageCall.create(chain.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getStrId())
                .message("hello world")
                .feeNQT(IGNIS.ONE_COIN)
                .callNoError();
        generateBlock();

        MessageEvent actual = messageListenerRule.getEvents().get(0);
        Assert.assertEquals(ALICE.getId(), actual.getSenderAccount().getId());
        Assert.assertEquals(BOB.getAccount().getId(), actual.getRecipientAccount().getId());
        Assert.assertEquals(chain, actual.getChain());
        Assert.assertEquals("hello world", Convert.toString(actual.getMessage().getMessage()));
        Assert.assertEquals(1, messageListenerRule.getEvents().size());
    }

    @Test
    public void listenToMessagesNullMessage() {
        ChildChain chain = IGNIS;
        SendMessageCall.create(chain.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getStrId())
                .feeNQT(IGNIS.ONE_COIN)
                .callNoError();
        generateBlock();

        MessageEvent actual = messageListenerRule.getEvents().get(0);
        Assert.assertEquals(ALICE.getId(), actual.getSenderAccount().getId());
        Assert.assertEquals(BOB.getAccount().getId(), actual.getRecipientAccount().getId());
        Assert.assertEquals(chain, actual.getChain());
        Assert.assertNull(actual.getMessage());
        Assert.assertEquals(1, messageListenerRule.getEvents().size());
	}

	@Test
	public void testForcedIsTextOfFileMessage() {
        SendMessageCall sendMessageCall = createSendMessageCall()
                .messageFile(Convert.toBytes("test message oymkbhnv"));

        Assert.assertEquals("Expected auto-detected text", true,
                getMessageIsText(sendMessageCall.callNoError(), false));

        sendMessageCall.messageIsText(false);
        Assert.assertEquals(false, getMessageIsText(sendMessageCall.callNoError(), false));

        sendMessageCall = createSendMessageCall()
                .messageFile(new byte[]{0, 0, 0, 1, 0, 4, 0, 127, 0, -127});

        Assert.assertEquals("Expected auto-detected non-text", false,
                getMessageIsText(sendMessageCall.callNoError(), false));

        sendMessageCall.messageIsText(true);
        Assert.assertEquals("Incorrect \"messageFile\" does not contain UTF-8 text",
                sendMessageCall.build().invokeWithError().getErrorDescription());
    }

    @Test
    public void testForcedIsTextOfEncryptedFileMessage() {
        SendMessageCall sendMessageCall = createSendMessageCall()
                .messageToEncryptFile(Convert.toBytes("test message rewio834"));

        Assert.assertEquals("Expected auto-detected text", true,
                getMessageIsText(sendMessageCall.callNoError(), true));

        sendMessageCall.messageToEncryptIsText(false);
        Assert.assertEquals(false, getMessageIsText(sendMessageCall.callNoError(), true));

        sendMessageCall = createSendMessageCall()
                .messageToEncryptFile(new byte[]{0, 0, 0, 1, 0, 4, 0, 127, 0, -127});

        Assert.assertEquals("Expected auto-detected non-text", false,
                getMessageIsText(sendMessageCall.callNoError(), true));

        sendMessageCall.messageToEncryptIsText(true);
        Assert.assertEquals("Incorrect \"messageToEncryptFile\" does not contain UTF-8 text",
                sendMessageCall.build().invokeWithError().getErrorDescription());

    }

    @Test
    public void testTwoFiles() {
        String message = "test message rewio834";
        String messageToSelf = "test message to self sdfnkoe";

        EncryptedData encryptedData = Account.encryptTo(ALICE.getPrivateKey(), ALICE.getPublicKey(), Convert.toBytes(messageToSelf), false);

        String fullHash = new JSONAssert(createSendMessageCall()
                .messageToEncryptFile(Convert.toBytes(message))
                .encryptToSelfMessageNonce(encryptedData.getNonce())
                .encryptToSelfMessageFile(encryptedData.getData())
                .compressMessageToEncryptToSelf("false")
                .callNoError()).fullHash();

        generateBlock();

        JSONAssert result = new JSONAssert(ReadMessageCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .transactionFullHash(fullHash).callNoError());

        Assert.assertEquals(message, result.str("decryptedMessage"));
        Assert.assertEquals(messageToSelf, result.str("decryptedMessageToSelf"));
    }

    private SendMessageCall createSendMessageCall() {
        return SendMessageCall.create(IGNIS.getId())
                .secretPhrase(ALICE.getSecretPhrase())
                .recipient(BOB.getStrId())
                .feeNQT(IGNIS.ONE_COIN);
    }

    private Boolean getMessageIsText(JO result, boolean isEncrypted) {
        JSONAssert jsonAssert = new JSONAssert(result)
                .subObj("transactionJSON")
                .subObj("attachment");
        if (isEncrypted) {
            return jsonAssert.subObj("encryptedMessage").bool("isText");
        } else {
            return jsonAssert.bool("messageIsText");
        }
    }
}
