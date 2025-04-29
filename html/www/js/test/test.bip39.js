/*
 * BIP39 tests adapted from https://github.com/bitcoinjs/bip39
 */

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

QUnit.module("bip39");

QUnit.test('entropyToMnemonic', assert => {
    function convert(entropy) {
        return BIP39.entropyToMnemonic(converters.hexStringToByteArray(entropy));
    }
    englishTestVectors.forEach((vector,index) => {
        assert.equal(vector[1], convert(vector[0]), 'vector ' + index);
    });
});


QUnit.test('mnemonicToEntropy', assert => {
    englishTestVectors.forEach((vector,index) => {
        assert.equal(vector[0], BIP39.mnemonicToEntropy(vector[1]), 'vector ' + index);
    });
});