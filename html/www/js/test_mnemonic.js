// Simple test to get exact values
const crypto = require('crypto');

// Mock isNode
global.isNode = true;

// Mock converters 
global.converters = {
    byteArrayToHexString: function(bytes) {
        return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('');
    },
    hexStringToByteArray: function(hex) {
        const bytes = [];
        for (let i = 0; i < hex.length; i += 2) {
            bytes.push(parseInt(hex.slice(i, i + 2), 16));
        }
        return bytes;
    },
    byteArrayToWordArrayEx: function(bytes) {
        return {
            words: bytes,
            sigBytes: bytes.length
        };
    },
    wordArrayToByteArrayImpl: function(wordArray) {
        return wordArray.words;
    },
    stringToByteArray: function(str) {
        return Array.from(Buffer.from(str, 'utf8'));
    },
    bigIntToHexString: function(bigInt) {
        return bigInt.toString(16);
    }
};

// Mock CryptoJS
global.CryptoJS = {
    PBKDF2: function(mnemonic, salt, options) {
        return crypto.pbkdf2Sync(mnemonic, salt, options.iterations, options.keySize * 4, 'sha512');
    },
    algo: {
        SHA512: 'sha512'
    },
    HmacSHA256: function(message, key) {
        return crypto.createHmac('sha256', Buffer.from(key)).update(Buffer.from(message)).digest();
    },
    HmacSHA512: function(message, key) {
        return crypto.createHmac('sha512', Buffer.from(key)).update(Buffer.from(message)).digest();
    }
};

// Mock Ed25519 - just for testing, we'll use the constants
global.Ed25519 = {
    PRIME_ORDER: BigInt("7237005577332262213973186563042994240857116359379907606001950938285454250989"),
    BASE_POINT: {
        multiply: function(n) {
            // This is just a mock - we can't fully implement Ed25519 here
            return {
                encode: function() {
                    return new Array(32).fill(0); // Mock result
                }
            };
        }
    }
};

// Mock CurveConversion
global.CurveConversion = {
    ed25519ToCurve25519: function(ed25519Key) {
        return new Array(32).fill(1); // Mock result  
    }
};

try {
    const KeyDerivation = require('./crypto/key.derivation.js');
    
    const mnemonic = 'side vast trash guard circle voyage undo behave sustain punch pave club admit fox step letter awake item';
    console.log('Mnemonic:', mnemonic);
    
    const seed = KeyDerivation.mnemonicToSeed(mnemonic);
    console.log('Seed:', converters.byteArrayToHexString(seed));
    
} catch (error) {
    console.error('Error:', error.message);
}