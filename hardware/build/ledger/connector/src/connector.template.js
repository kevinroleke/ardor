import TransportWebUSB from "@ledgerhq/hw-transport-webusb";

function getTransport() {
    return TransportWebUSB;
}

function getNodeJsBufferClass() {
    return Buffer;
}

global.ledgerdevice = {
    getTransport: getTransport,
    getNodeJsBufferClass: getNodeJsBufferClass
};
