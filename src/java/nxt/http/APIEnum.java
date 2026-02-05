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

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum APIEnum {
    //To preserve compatibility, please add new APIs to the end of the enum.
    //When an API is deleted, set its name to empty string and handler to null.
    APPROVE_TRANSACTION("approveTransaction", ApproveTransaction.class),
    BROADCAST_TRANSACTION("broadcastTransaction", BroadcastTransaction.class),
    CALCULATE_FULL_HASH("calculateFullHash", CalculateFullHash.class),
    CANCEL_ASK_ORDER("cancelAskOrder", CancelAskOrder.class),
    CANCEL_BID_ORDER("cancelBidOrder", CancelBidOrder.class),
    CAST_VOTE("castVote", CastVote.class),
    CREATE_POLL("createPoll", CreatePoll.class),
    CURRENCY_BUY("currencyBuy", CurrencyBuy.class),
    CURRENCY_SELL("currencySell", CurrencySell.class),
    CURRENCY_RESERVE_INCREASE("currencyReserveIncrease", CurrencyReserveIncrease.class),
    CURRENCY_RESERVE_CLAIM("currencyReserveClaim", CurrencyReserveClaim.class),
    CURRENCY_MINT("currencyMint", CurrencyMint.class),
    DECRYPT_FROM("decryptFrom", DecryptFrom.class),
    DELETE_ASSET_SHARES("deleteAssetShares", DeleteAssetShares.class),
    DGS_LISTING("dgsListing", DGSListing.class),
    DGS_DELISTING("dgsDelisting", DGSDelisting.class),
    DGS_DELIVERY("dgsDelivery", DGSDelivery.class),
    DGS_FEEDBACK("dgsFeedback", DGSFeedback.class),
    DGS_PRICE_CHANGE("dgsPriceChange", DGSPriceChange.class),
    DGS_PURCHASE("dgsPurchase", DGSPurchase.class),
    DGS_QUANTITY_CHANGE("dgsQuantityChange", DGSQuantityChange.class),
    DGS_REFUND("dgsRefund", DGSRefund.class),
    DECODE_TOKEN("decodeToken", DecodeToken.class),
    DECODE_FILE_TOKEN("decodeFileToken", DecodeFileToken.class),
    DECODE_Q_R_CODE("decodeQRCode", DecodeQRCode.class),
    ENCODE_Q_R_CODE("encodeQRCode", EncodeQRCode.class),
    ENCRYPT_TO("encryptTo", EncryptTo.class),
    EVENT_REGISTER("eventRegister", EventRegister.class),
    EVENT_WAIT("eventWait", EventWait.class),
    GENERATE_TOKEN("generateToken", GenerateToken.class),
    GENERATE_FILE_TOKEN("generateFileToken", GenerateFileToken.class),
    GET_ACCOUNT("getAccount", GetAccount.class),
    GET_ACCOUNT_BLOCK_COUNT("getAccountBlockCount", GetAccountBlockCount.class),
    GET_ACCOUNT_BLOCK_IDS("getAccountBlockIds", GetAccountBlockIds.class),
    GET_ACCOUNT_BLOCKS("getAccountBlocks", GetAccountBlocks.class),
    GET_ACCOUNT_ID("getAccountId", GetAccountId.class),
    GET_ACCOUNT_LEDGER("getAccountLedger", GetAccountLedger.class),
    GET_ACCOUNT_LEDGER_ENTRY("getAccountLedgerEntry", GetAccountLedgerEntry.class),
    GET_VOTER_PHASED_TRANSACTIONS("getVoterPhasedTransactions", GetVoterPhasedTransactions.class),
    GET_LINKED_PHASED_TRANSACTIONS("getLinkedPhasedTransactions", GetLinkedPhasedTransactions.class),
    GET_POLLS("getPolls", GetPolls.class),
    GET_ACCOUNT_PHASED_TRANSACTIONS("getAccountPhasedTransactions", GetAccountPhasedTransactions.class),
    GET_ACCOUNT_PHASED_TRANSACTION_COUNT("getAccountPhasedTransactionCount", GetAccountPhasedTransactionCount.class),
    GET_ACCOUNT_PUBLIC_KEY("getAccountPublicKey", GetAccountPublicKey.class),
    GET_ACCOUNT_LESSORS("getAccountLessors", GetAccountLessors.class),
    GET_ACCOUNT_ASSETS("getAccountAssets", GetAccountAssets.class),
    GET_ACCOUNT_CURRENCIES("getAccountCurrencies", GetAccountCurrencies.class),
    GET_ACCOUNT_CURRENCY_COUNT("getAccountCurrencyCount", GetAccountCurrencyCount.class),
    GET_ACCOUNT_ASSET_COUNT("getAccountAssetCount", GetAccountAssetCount.class),
    GET_ACCOUNT_PROPERTIES("getAccountProperties", GetAccountProperties.class),
    SELL_ALIAS("sellAlias", SellAlias.class),
    BUY_ALIAS("buyAlias", BuyAlias.class),
    GET_ALIAS("getAlias", GetAlias.class),
    GET_ALIAS_COUNT("getAliasCount", GetAliasCount.class),
    GET_ALIASES("getAliases", GetAliases.class),
    GET_ALIASES_LIKE("getAliasesLike", GetAliasesLike.class),
    GET_ALL_ASSETS("getAllAssets", GetAllAssets.class),
    GET_ALL_CURRENCIES("getAllCurrencies", GetAllCurrencies.class),
    GET_ASSET("getAsset", GetAsset.class),
    GET_ASSETS("getAssets", GetAssets.class),
    GET_ASSET_IDS("getAssetIds", GetAssetIds.class),
    GET_ASSETS_BY_ISSUER("getAssetsByIssuer", GetAssetsByIssuer.class),
    GET_ASSET_ACCOUNTS("getAssetAccounts", GetAssetAccounts.class),
    GET_ASSET_ACCOUNT_COUNT("getAssetAccountCount", GetAssetAccountCount.class),
    GET_ASSET_PHASED_TRANSACTIONS("getAssetPhasedTransactions", GetAssetPhasedTransactions.class),
    GET_BALANCE("getBalance", GetBalance.class),
    GET_BLOCK("getBlock", GetBlock.class),
    GET_BLOCK_ID("getBlockId", GetBlockId.class),
    GET_BLOCKS("getBlocks", GetBlocks.class),
    GET_BLOCKCHAIN_STATUS("getBlockchainStatus", GetBlockchainStatus.class),
    GET_BLOCKCHAIN_TRANSACTIONS("getBlockchainTransactions", GetBlockchainTransactions.class),
    GET_REFERENCING_TRANSACTIONS("getReferencingTransactions", GetReferencingTransactions.class),
    GET_CONSTANTS("getConstants", GetConstants.class),
    GET_CURRENCY("getCurrency", GetCurrency.class),
    GET_CURRENCIES("getCurrencies", GetCurrencies.class),
    GET_CURRENCY_FOUNDERS("getCurrencyFounders", GetCurrencyFounders.class),
    GET_CURRENCY_IDS("getCurrencyIds", GetCurrencyIds.class),
    GET_CURRENCIES_BY_ISSUER("getCurrenciesByIssuer", GetCurrenciesByIssuer.class),
    GET_CURRENCY_ACCOUNTS("getCurrencyAccounts", GetCurrencyAccounts.class),
    GET_CURRENCY_ACCOUNT_COUNT("getCurrencyAccountCount", GetCurrencyAccountCount.class),
    GET_CURRENCY_PHASED_TRANSACTIONS("getCurrencyPhasedTransactions", GetCurrencyPhasedTransactions.class),
    GET_DGS_GOODS("getDGSGoods", GetDGSGoods.class),
    GET_DGS_GOODS_COUNT("getDGSGoodsCount", GetDGSGoodsCount.class),
    GET_DGS_GOOD("getDGSGood", GetDGSGood.class),
    GET_DGS_GOODS_PURCHASES("getDGSGoodsPurchases", GetDGSGoodsPurchases.class),
    GET_DGS_GOODS_PURCHASE_COUNT("getDGSGoodsPurchaseCount", GetDGSGoodsPurchaseCount.class),
    GET_DGS_PURCHASES("getDGSPurchases", GetDGSPurchases.class),
    GET_DGS_PURCHASE("getDGSPurchase", GetDGSPurchase.class),
    GET_DGS_PURCHASE_COUNT("getDGSPurchaseCount", GetDGSPurchaseCount.class),
    GET_DGS_PENDING_PURCHASES("getDGSPendingPurchases", GetDGSPendingPurchases.class),
    GET_DGS_EXPIRED_PURCHASES("getDGSExpiredPurchases", GetDGSExpiredPurchases.class),
    GET_DGS_TAGS("getDGSTags", GetDGSTags.class),
    GET_DGS_TAG_COUNT("getDGSTagCount", GetDGSTagCount.class),
    GET_DGS_TAGS_LIKE("getDGSTagsLike", GetDGSTagsLike.class),
    GET_GUARANTEED_BALANCE("getGuaranteedBalance", GetGuaranteedBalance.class),
    GET_E_C_BLOCK("getECBlock", GetECBlock.class),
    GET_PLUGINS("getPlugins", GetPlugins.class),
    GET_MY_INFO("getMyInfo", GetMyInfo.class),
    GET_PEER("getPeer", GetPeer.class),
    GET_PEERS("getPeers", GetPeers.class),
    GET_PHASING_POLL("getPhasingPoll", GetPhasingPoll.class),
    GET_PHASING_POLL_VOTES("getPhasingPollVotes", GetPhasingPollVotes.class),
    GET_PHASING_POLL_VOTE("getPhasingPollVote", GetPhasingPollVote.class),
    GET_POLL("getPoll", GetPoll.class),
    GET_POLL_RESULT("getPollResult", GetPollResult.class),
    GET_POLL_VOTES("getPollVotes", GetPollVotes.class),
    GET_POLL_VOTE("getPollVote", GetPollVote.class),
    GET_STATE("getState", GetState.class),
    GET_TIME("getTime", GetTime.class),
    GET_TRADES("getTrades", GetTrades.class),
    GET_LAST_TRADES("getLastTrades", GetLastTrades.class),
    GET_EXCHANGES("getExchanges", GetExchanges.class),
    GET_EXCHANGES_BY_EXCHANGE_REQUEST("getExchangesByExchangeRequest", GetExchangesByExchangeRequest.class),
    GET_EXCHANGES_BY_OFFER("getExchangesByOffer", GetExchangesByOffer.class),
    GET_LAST_EXCHANGES("getLastExchanges", GetLastExchanges.class),
    GET_ALL_TRADES("getAllTrades", GetAllTrades.class),
    GET_ALL_EXCHANGES("getAllExchanges", GetAllExchanges.class),
    GET_ASSET_TRANSFERS("getAssetTransfers", GetAssetTransfers.class),
    GET_ASSET_HISTORY("getAssetHistory", GetAssetHistory.class),
    GET_EXPECTED_ASSET_TRANSFERS("getExpectedAssetTransfers", GetExpectedAssetTransfers.class),
    GET_EXPECTED_ASSET_DELETES("getExpectedAssetDeletes", GetExpectedAssetDeletes.class),
    GET_CURRENCY_TRANSFERS("getCurrencyTransfers", GetCurrencyTransfers.class),
    GET_EXPECTED_CURRENCY_TRANSFERS("getExpectedCurrencyTransfers", GetExpectedCurrencyTransfers.class),
    GET_TRANSACTION("getTransaction", GetTransaction.class),
    GET_TRANSACTION_BYTES("getTransactionBytes", GetTransactionBytes.class),
    GET_UNCONFIRMED_TRANSACTION_IDS("getUnconfirmedTransactionIds", GetUnconfirmedTransactionIds.class),
    GET_UNCONFIRMED_TRANSACTIONS("getUnconfirmedTransactions", GetUnconfirmedTransactions.class),
    GET_EXPECTED_TRANSACTIONS("getExpectedTransactions", GetExpectedTransactions.class),
    GET_ACCOUNT_CURRENT_ASK_ORDER_IDS("getAccountCurrentAskOrderIds", GetAccountCurrentAskOrderIds.class),
    GET_ACCOUNT_CURRENT_BID_ORDER_IDS("getAccountCurrentBidOrderIds", GetAccountCurrentBidOrderIds.class),
    GET_ACCOUNT_CURRENT_ASK_ORDERS("getAccountCurrentAskOrders", GetAccountCurrentAskOrders.class),
    GET_ACCOUNT_CURRENT_BID_ORDERS("getAccountCurrentBidOrders", GetAccountCurrentBidOrders.class),
    GET_ALL_OPEN_ASK_ORDERS("getAllOpenAskOrders", GetAllOpenAskOrders.class),
    GET_ALL_OPEN_BID_ORDERS("getAllOpenBidOrders", GetAllOpenBidOrders.class),
    GET_BUY_OFFERS("getBuyOffers", GetBuyOffers.class),
    GET_EXPECTED_BUY_OFFERS("getExpectedBuyOffers", GetExpectedBuyOffers.class),
    GET_SELL_OFFERS("getSellOffers", GetSellOffers.class),
    GET_EXPECTED_SELL_OFFERS("getExpectedSellOffers", GetExpectedSellOffers.class),
    GET_OFFER("getOffer", GetOffer.class),
    GET_AVAILABLE_TO_BUY("getAvailableToBuy", GetAvailableToBuy.class),
    GET_AVAILABLE_TO_SELL("getAvailableToSell", GetAvailableToSell.class),
    GET_ASK_ORDER("getAskOrder", GetAskOrder.class),
    GET_ASK_ORDER_IDS("getAskOrderIds", GetAskOrderIds.class),
    GET_ASK_ORDERS("getAskOrders", GetAskOrders.class),
    GET_BID_ORDER("getBidOrder", GetBidOrder.class),
    GET_BID_ORDER_IDS("getBidOrderIds", GetBidOrderIds.class),
    GET_BID_ORDERS("getBidOrders", GetBidOrders.class),
    GET_EXPECTED_ASK_ORDERS("getExpectedAskOrders", GetExpectedAskOrders.class),
    GET_EXPECTED_BID_ORDERS("getExpectedBidOrders", GetExpectedBidOrders.class),
    GET_EXPECTED_ORDER_CANCELLATIONS("getExpectedOrderCancellations", GetExpectedOrderCancellations.class),
    GET_ORDER_TRADES("getOrderTrades", GetOrderTrades.class),
    GET_ACCOUNT_EXCHANGE_REQUESTS("getAccountExchangeRequests", GetAccountExchangeRequests.class),
    GET_EXPECTED_EXCHANGE_REQUESTS("getExpectedExchangeRequests", GetExpectedExchangeRequests.class),
    GET_MINTING_TARGET("getMintingTarget", GetMintingTarget.class),
    GET_ALL_SHUFFLINGS("getAllShufflings", GetAllShufflings.class),
    GET_ACCOUNT_SHUFFLINGS("getAccountShufflings", GetAccountShufflings.class),
    GET_ASSIGNED_SHUFFLINGS("getAssignedShufflings", GetAssignedShufflings.class),
    GET_HOLDING_SHUFFLINGS("getHoldingShufflings", GetHoldingShufflings.class),
    GET_SHUFFLING("getShuffling", GetShuffling.class),
    GET_SHUFFLING_PARTICIPANTS("getShufflingParticipants", GetShufflingParticipants.class),
    GET_PRUNABLE_MESSAGE("getPrunableMessage", GetPrunableMessage.class),
    GET_PRUNABLE_MESSAGES("getPrunableMessages", GetPrunableMessages.class),
    GET_ALL_PRUNABLE_MESSAGES("getAllPrunableMessages", GetAllPrunableMessages.class),
    VERIFY_PRUNABLE_MESSAGE("verifyPrunableMessage", VerifyPrunableMessage.class),
    ISSUE_ASSET("issueAsset", IssueAsset.class),
    ISSUE_CURRENCY("issueCurrency", IssueCurrency.class),
    LEASE_BALANCE("leaseBalance", LeaseBalance.class),
    LONG_CONVERT("longConvert", LongConvert.class),
    HEX_CONVERT("hexConvert", HexConvert.class),
    PARSE_TRANSACTION("parseTransaction", ParseTransaction.class),
    PLACE_ASK_ORDER("placeAskOrder", PlaceAskOrder.class),
    PLACE_BID_ORDER("placeBidOrder", PlaceBidOrder.class),
    PUBLISH_EXCHANGE_OFFER("publishExchangeOffer", PublishExchangeOffer.class),
    RS_CONVERT("rsConvert", RSConvert.class),
    READ_MESSAGE("readMessage", ReadMessage.class),
    SEND_MESSAGE("sendMessage", SendMessage.class),
    SEND_MONEY("sendMoney", SendMoney.class),
    SET_ACCOUNT_INFO("setAccountInfo", SetAccountInfo.class),
    SET_ACCOUNT_PROPERTY("setAccountProperty", SetAccountProperty.class),
    DELETE_ACCOUNT_PROPERTY("deleteAccountProperty", DeleteAccountProperty.class),
    SET_ALIAS("setAlias", SetAlias.class),
    SHUFFLING_CREATE("shufflingCreate", ShufflingCreate.class),
    SHUFFLING_REGISTER("shufflingRegister", ShufflingRegister.class),
    SHUFFLING_PROCESS("shufflingProcess", ShufflingProcess.class),
    SHUFFLING_VERIFY("shufflingVerify", ShufflingVerify.class),
    SHUFFLING_CANCEL("shufflingCancel", ShufflingCancel.class),
    START_SHUFFLER("startShuffler", StartShuffler.class),
    STOP_SHUFFLER("stopShuffler", StopShuffler.class),
    GET_SHUFFLERS("getShufflers", GetShufflers.class),
    DELETE_ALIAS("deleteAlias", DeleteAlias.class),
    SIGN_TRANSACTION("signTransaction", SignTransaction.class),
    START_FORGING("startForging", StartForging.class),
    STOP_FORGING("stopForging", StopForging.class),
    GET_FORGING("getForging", GetForging.class),
    TRANSFER_ASSET("transferAsset", TransferAsset.class),
    TRANSFER_CURRENCY("transferCurrency", TransferCurrency.class),
    CAN_DELETE_CURRENCY("canDeleteCurrency", CanDeleteCurrency.class),
    DELETE_CURRENCY("deleteCurrency", DeleteCurrency.class),
    DIVIDEND_PAYMENT("dividendPayment", DividendPayment.class),
    SEARCH_DGS_GOODS("searchDGSGoods", SearchDGSGoods.class),
    SEARCH_ASSETS("searchAssets", SearchAssets.class),
    SEARCH_CURRENCIES("searchCurrencies", SearchCurrencies.class),
    SEARCH_POLLS("searchPolls", SearchPolls.class),
    SEARCH_ACCOUNTS("searchAccounts", SearchAccounts.class),
    SEARCH_TAGGED_DATA("searchTaggedData", SearchTaggedData.class),
    UPLOAD_TAGGED_DATA("uploadTaggedData", UploadTaggedData.class),
    GET_ACCOUNT_TAGGED_DATA("getAccountTaggedData", GetAccountTaggedData.class),
    GET_ALL_TAGGED_DATA("getAllTaggedData", GetAllTaggedData.class),
    GET_CHANNEL_TAGGED_DATA("getChannelTaggedData", GetChannelTaggedData.class),
    GET_TAGGED_DATA("getTaggedData", GetTaggedData.class),
    DOWNLOAD_TAGGED_DATA("downloadTaggedData", DownloadTaggedData.class),
    GET_DATA_TAGS("getDataTags", GetDataTags.class),
    GET_DATA_TAG_COUNT("getDataTagCount", GetDataTagCount.class),
    GET_DATA_TAGS_LIKE("getDataTagsLike", GetDataTagsLike.class),
    VERIFY_TAGGED_DATA("verifyTaggedData", VerifyTaggedData.class),
    CLEAR_UNCONFIRMED_TRANSACTIONS("clearUnconfirmedTransactions", ClearUnconfirmedTransactions.class),
    REQUEUE_UNCONFIRMED_TRANSACTIONS("requeueUnconfirmedTransactions", RequeueUnconfirmedTransactions.class),
    REBROADCAST_UNCONFIRMED_TRANSACTIONS("rebroadcastUnconfirmedTransactions", RebroadcastUnconfirmedTransactions.class),
    GET_ALL_WAITING_TRANSACTIONS("getAllWaitingTransactions", GetAllWaitingTransactions.class),
    GET_ALL_BROADCASTED_TRANSACTIONS("getAllBroadcastedTransactions", GetAllBroadcastedTransactions.class),
    FULL_RESET("fullReset", FullReset.class),
    POP_OFF("popOff", PopOff.class),
    SCAN("scan", Scan.class),
    LUCENE_REINDEX("luceneReindex", LuceneReindex.class),
    ADD_PEER("addPeer", AddPeer.class),
    BLACKLIST_PEER("blacklistPeer", BlacklistPeer.class),
    DUMP_PEERS("dumpPeers", DumpPeers.class),
    GET_LOG("getLog", GetLog.class),
    GET_STACK_TRACES("getStackTraces", GetStackTraces.class),
    RETRIEVE_PRUNED_DATA("retrievePrunedData", RetrievePrunedData.class),
    RETRIEVE_PRUNED_TRANSACTION("retrievePrunedTransaction", RetrievePrunedTransaction.class),
    SET_LOGGING("setLogging", SetLogging.class),
    SHUTDOWN("shutdown", Shutdown.class),
    TRIM_DERIVED_TABLES("trimDerivedTables", TrimDerivedTables.class),
    HASH("hash", Hash.class),
    FULL_HASH_TO_ID("fullHashToId", FullHashToId.class),
    SET_PHASING_ONLY_CONTROL("setPhasingOnlyControl", SetPhasingOnlyControl.class),
    GET_PHASING_ONLY_CONTROL("getPhasingOnlyControl", GetPhasingOnlyControl.class),
    GET_ALL_PHASING_ONLY_CONTROLS("getAllPhasingOnlyControls", GetAllPhasingOnlyControls.class),
    DETECT_MIME_TYPE("detectMimeType", DetectMimeType.class),
    START_FUNDING_MONITOR("startFundingMonitor", StartFundingMonitor.class),
    STOP_FUNDING_MONITOR("stopFundingMonitor", StopFundingMonitor.class),
    GET_FUNDING_MONITOR("getFundingMonitor", GetFundingMonitor.class),
    DOWNLOAD_PRUNABLE_MESSAGE("downloadPrunableMessage", DownloadPrunableMessage.class),
    GET_SHARED_KEY("getSharedKey", GetSharedKey.class),
    SET_API_PROXY_PEER("setAPIProxyPeer", SetAPIProxyPeer.class),
    SEND_TRANSACTION("sendTransaction", SendTransaction.class),
    GET_ASSET_DIVIDENDS("getAssetDividends", GetAssetDividends.class),
    BLACKLIST_API_PROXY_PEER("blacklistAPIProxyPeer", BlacklistAPIProxyPeer.class),
    GET_NEXT_BLOCK_GENERATORS("getNextBlockGenerators", GetNextBlockGenerators.class),
    START_BUNDLER("startBundler", StartBundler.class),
    STOP_BUNDLER("stopBundler", StopBundler.class),
    GET_BUNDLERS("getBundlers", GetBundlers.class),
    BUNDLE_TRANSACTIONS("bundleTransactions", BundleTransactions.class),
    EXCHANGE_COINS("exchangeCoins", ExchangeCoins.class),
    CANCEL_COIN_EXCHANGE("cancelCoinExchange", CancelCoinExchange.class),
    GET_COIN_EXCHANGE_ORDER("getCoinExchangeOrder", GetCoinExchangeOrder.class),
    GET_COIN_EXCHANGE_ORDER_IDS("getCoinExchangeOrderIds", GetCoinExchangeOrderIds.class),
    GET_COIN_EXCHANGE_ORDERS("getCoinExchangeOrders", GetCoinExchangeOrders.class),
    GET_COIN_EXCHANGE_TRADES("getCoinExchangeTrades", GetCoinExchangeTrades.class),
    GET_COIN_EXCHANGE_TRADE("getCoinExchangeTrade", GetCoinExchangeTrade.class),
    GET_EXPECTED_COIN_EXCHANGE_ORDERS("getExpectedCoinExchangeOrders", GetExpectedCoinExchangeOrders.class),
    GET_EXPECTED_COIN_EXCHANGE_ORDER_CANCELLATIONS("getExpectedCoinExchangeOrderCancellations", GetExpectedCoinExchangeOrderCancellations.class),
    GET_BUNDLER_RATES("getBundlerRates", GetBundlerRates.class),
    GET_FXT_TRANSACTION("getFxtTransaction", GetFxtTransaction.class),
    GET_BALANCES("getBalances", GetBalances.class),
    GET_RICH_ACCOUNTS("getRichAccounts", GetRichAccounts.class),
    GET_RECENT_BLOCKCHAIN_TRANSACTIONS("getRecentBlockchainTransactions", GetRecentBlockchainTransactions.class),
    SIMULATE_COIN_EXCHANGE("simulateCoinExchange", SimulateCoinExchange.class),
    GET_EFFECTIVE_BALANCE("getEffectiveBalance", GetEffectiveBalance.class),
    BLACKLIST_BUNDLER("blacklistBundler", BlacklistBundler.class),
    GET_ALL_BUNDLER_RATES("getAllBundlerRates", GetAllBundlerRates.class),
    EVALUATE_EXPRESSION("evaluateExpression", EvaluateExpression.class),
    PARSE_PHASING_PARAMS("parsePhasingParams", ParsePhasingParams.class),
    SET_PHASING_ASSET_CONTROL("setPhasingAssetControl", SetPhasingAssetControl.class),
    GET_PHASING_ASSET_CONTROL("getPhasingAssetControl", GetPhasingAssetControl.class),
    INCREASE_ASSET_SHARES("increaseAssetShares", IncreaseAssetShares.class),
    GET_EXECUTED_TRANSACTIONS("getExecutedTransactions", GetExecutedTransactions.class),
    ADD_BUNDLING_RULE("addBundlingRule", AddBundlingRule.class),
    GET_BUNDLING_OPTIONS("getBundlingOptions", GetBundlingOptions.class),
    PROCESS_VOUCHER("processVoucher", ProcessVoucher.class),
    SET_CONTRACT_REFERENCE("setContractReference", SetContractReference.class),
    DELETE_CONTRACT_REFERENCE("deleteContractReference", DeleteContractReference.class),
    GET_CONTRACT_REFERENCES("getContractReferences", GetContractReferences.class),
    CALCULATE_FEE("calculateFee", CalculateFee.class),
    GET_ASSET_PROPERTIES("getAssetProperties", GetAssetProperties.class),
    SET_ASSET_PROPERTY("setAssetProperty", SetAssetProperty.class),
    DELETE_ASSET_PROPERTY("deleteAssetProperty", DeleteAssetProperty.class),
    GET_HASHED_SECRET_PHASED_TRANSACTIONS("getHashedSecretPhasedTransactions", GetHashedSecretPhasedTransactions.class),
    SPLIT_SECRET("splitSecret", SplitSecret.class),
    COMBINE_SECRET("combineSecret", CombineSecret.class),
    MANAGE_PEERS_NETWORKING("managePeersNetworking", ManagePeersNetworking.class),
    GET_LEDGER_MASTER_PUBLIC_KEY("getLedgerMasterPublicKey", GetLedgerMasterPublicKey.class),
    DERIVE_ACCOUNT_FROM_MASTER_PUBLIC_KEY("deriveAccountFromMasterPublicKey", DeriveAccountFromMasterPublicKey.class),
    DERIVE_ACCOUNT_FROM_SEED("deriveAccountFromSeed", DeriveAccountFromSeed.class),
    GET_ACCOUNT_PERMISSIONS("getAccountPermissions", GetAccountPermissions.class),
    ADD_ACCOUNT_PERMISSION("addAccountPermission", AddAccountPermission.class),
    REMOVE_ACCOUNT_PERMISSION("removeAccountPermission", RemoveAccountPermission.class),
    GET_CHAIN_PERMISSIONS("getChainPermissions", GetChainPermissions.class),
    GET_EPOCH_TIME("getEpochTime", GetEpochTime.class),
    BOOTSTRAP_API_PROXY("bootstrapAPIProxy", BootstrapAPIProxy.class),
    GET_CONFIGURATION("getConfiguration", GetConfiguration.class),
    SET_CONFIGURATION("setConfiguration", SetConfiguration.class),
    GET_API_PROXY_REPORTS("getAPIProxyReports", GetAPIProxyReports.class),
    SET_ASSET_TRADE_ROYALTIES("setAssetTradeRoyalties", SetAssetTradeRoyalties.class);

    private static final Map<String, APIEnum> apiByName = new HashMap<>();

    static {
        final EnumSet<APITag> tagsNotRequiringBlockchain = EnumSet.of(APITag.UTILS);
        for (APIEnum api : values()) {
            if (apiByName.put(api.getName(), api) != null) {
                AssertionError assertionError = new AssertionError("Duplicate API name: " + api.getName());
                assertionError.printStackTrace();
                throw assertionError;
            }
            APIServlet.APIRequestHandler handler = api.getHandler();
            if (handler == null) {
                continue;
            }
            if (!Collections.disjoint(handler.getAPITags(), tagsNotRequiringBlockchain)
                    && handler.requireBlockchain()) {
                AssertionError assertionError = new AssertionError("API " + api.getName()
                        + " is not supposed to require blockchain");
                assertionError.printStackTrace();
                throw assertionError;
            }
        }
    }

    public static APIEnum fromName(String name) {
        return apiByName.get(name);
    }

    private final String name;
    private APIServlet.APIRequestHandler handler;

    APIEnum(String name, Class<?> handlerClass) {
        this.name = name;
        try {
            Field field = handlerClass.getDeclaredField("instance");
            handler = (APIServlet.APIRequestHandler)field.get(null);
        } catch (NoClassDefFoundError | IllegalAccessException | NoSuchFieldException e) {
            System.out.printf("Cannot load API handler %s\n", handlerClass.getSimpleName());
        }
    }

    public String getName() {
        return name;
    }

    public APIServlet.APIRequestHandler getHandler() {
        return handler;
    }

    public static EnumSet<APIEnum> base64StringToEnumSet(String apiSetBase64) {
        EnumSet<APIEnum> result = EnumSet.noneOf(APIEnum.class);
        if (apiSetBase64 == null) {
            return result;
        }
        byte[] decoded = Base64.getDecoder().decode(apiSetBase64);
        BitSet bs = BitSet.valueOf(decoded);
        for (int i = bs.nextSetBit(0); i >= 0 && i < APIEnum.values().length; i = bs.nextSetBit(i+1)) {
            result.add(APIEnum.values()[i]);
            if (i == Integer.MAX_VALUE) {
                break; // or (i+1) would overflow
            }
        }
        return result;
    }

    public static String enumSetToBase64String(EnumSet<APIEnum> apiSet) {
        BitSet bitSet = new BitSet();
        for (APIEnum api: apiSet) {
            bitSet.set(api.ordinal());
        }
        return Base64.getEncoder().encodeToString(bitSet.toByteArray());
    }
}
