// Test the user's specific mnemonic with the actual JavaScript implementation
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
        return bytes;
    },
    wordArrayToByteArrayImpl: function(wordArray) {
        return Array.from(wordArray);
    },
    stringToByteArray: function(str) {
        return Array.from(Buffer.from(str, 'utf8'));
    },
    bigIntToHexString: function(bigInt) {
        return bigInt.toString(16);
    },
    int32ToBytes: function(int32) {
        return [
            (int32 >> 24) & 0xff,
            (int32 >> 16) & 0xff, 
            (int32 >> 8) & 0xff,
            int32 & 0xff
        ];
    }
};

// Mock CryptoJS
global.CryptoJS = {
    PBKDF2: function(mnemonic, salt, options) {
        const result = crypto.pbkdf2Sync(mnemonic, salt, options.iterations, options.keySize * 4, 'sha512');
        return Array.from(result);
    },
    algo: {
        SHA512: 'sha512'
    },
    HmacSHA256: function(message, key) {
        return Array.from(crypto.createHmac('sha256', Buffer.from(key)).update(Buffer.from(message)).digest());
    },
    HmacSHA512: function(message, key) {
        return Array.from(crypto.createHmac('sha512', Buffer.from(key)).update(Buffer.from(message)).digest());
    }
};

// Load the actual Ed25519 implementation from the codebase
const Ed25519 = require('./crypto/ed25519.js');
global.Ed25519 = Ed25519;

// Mock CurveConversion
global.CurveConversion = {
    ed25519ToCurve25519: function(ed25519Key) {
        // Simplified curve conversion
        return ed25519Key.slice(); // Return copy of ed25519 key for now
    }
};

// Mock CRC32
global.CRC32 = {
    buf: function(data, initial = 0) {
        return 0x12345678; // Mock CRC
    }
};

try {
    const KeyDerivation = require('./crypto/key.derivation.js');
    
    const mnemonic = 'side vast trash guard circle voyage undo behave sustain punch pave club admit fox step letter awake item';
    console.log('Testing mnemonic:', mnemonic);
    
    // Test seed generation with empty passphrase
    const seedWithPassphrase = KeyDerivation.mnemonicAndPassphraseToSeed(mnemonic, "");
    console.log('Seed (with empty passphrase):', converters.byteArrayToHexString(seedWithPassphrase));
    
    // Test seed generation without passphrase
    const seed = KeyDerivation.mnemonicToSeed(mnemonic);
    console.log('Seed (no passphrase):', converters.byteArrayToHexString(seed));
    
    // Test master node
    const masterNode = KeyDerivation.deriveMnemonic('m', mnemonic);
    console.log('Master Public Key:', converters.byteArrayToHexString(masterNode.getMasterPublicKey()));
    console.log('Master Chain Code:', converters.byteArrayToHexString(masterNode.getChainCode()));
    
    // Test serialized master public key (68 bytes = 136 hex chars)
    const serialized = masterNode.getSerializedMasterPublicKey();
    console.log('Serialized Master Public Key:', converters.byteArrayToHexString(serialized));
    
    // Test specific paths
    const paths = ['m/44\'/16754\'/0\'/0\'/0', 'm/44\'/16754\'/0\'/0\'/1', 'm/44\'/16754\'/0\'/0\'/2'];
    
    for (const path of paths) {
        try {
            const node = KeyDerivation.deriveMnemonic(path, mnemonic);
            console.log(`Path ${path}:`);
            console.log('  Public Key (Curve25519):', converters.byteArrayToHexString(node.getPublicKey()));
            console.log('  Master Public Key (Ed25519):', converters.byteArrayToHexString(node.getMasterPublicKey()));
            console.log('  Private Key Left:', converters.byteArrayToHexString(node.getPrivateKeyLeft()));
            console.log('  Chain Code:', converters.byteArrayToHexString(node.getChainCode()));
        } catch (error) {
            console.log(`Path ${path}: ERROR -`, error.message);
        }
    }
    
} catch (error) {
    console.error('Error:', error.message);
    console.error(error.stack);
}