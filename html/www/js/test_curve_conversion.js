// Test curve conversion with known values
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
    }
};

// Load the actual CurveConversion implementation
const CurveConversion = require('./crypto/curve.conversion.js');

// Test with a known Ed25519 public key
const testKey = "226139fe3c1611638decd952c848e5c8285f95a0132c87cf32fe22743daa599d"; 
const ed25519Bytes = converters.hexStringToByteArray(testKey);

console.log('Input Ed25519 key:', testKey);

const curve25519Result = CurveConversion.ed25519ToCurve25519(ed25519Bytes);
const curve25519Hex = converters.byteArrayToHexString(curve25519Result);

console.log('Output Curve25519 key:', curve25519Hex);
console.log('Expected Curve25519 key:', "226139fe3c1611638decd952c848e5c8285f95a0132c87cf32fe22743daa591d");

// Test with the master key
const masterKey = "f057a838365b854c06b06f6d9acace5c4165704c8dfcd876628f891b6dea6f13";
const masterBytes = converters.hexStringToByteArray(masterKey);
const masterCurve = CurveConversion.ed25519ToCurve25519(masterBytes);
const masterCurveHex = converters.byteArrayToHexString(masterCurve);

console.log('Master Ed25519:', masterKey);
console.log('Master Curve25519:', masterCurveHex);