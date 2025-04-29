/*
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

package com.jelurida.ardor.integration.wallet.ledger;

import nxt.util.Convert;
import nxt.util.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class LedgerSpeculos extends LedgerDevice {
    private final String host;
    private final int port;

    LedgerSpeculos(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public byte[] exchange(byte[] input) {
        Logger.logDebugMessage("Opening socket to ledger emulator (Speculos) on %s:%d", host, port);
        try (
            Socket socket = new Socket(host, port);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            Logger.logDebugMessage("exchange() => %s", Convert.toHexString(input));
            out.writeInt(input.length);
            out.write(input);
            out.flush();

            int responseLength = in.readInt();
            if (responseLength > 1024*1024) {
                throw new IllegalStateException("Ledger Speculos response too large: " + responseLength);
            }
            byte[] output = new byte[responseLength];
            int readBytes = in.read(output);
            if (readBytes != responseLength) {
                Logger.logDebugMessage("read bytes (%d) doesn't match expected response length (%d)",
                        readBytes, responseLength);
            }
            Logger.logDebugMessage("exchange() <= %s", Convert.toHexString(output));
            return output;
        } catch (IOException e) {
            Logger.logErrorMessage("IO error talking to Ledger Speculos", e);
            return new byte[0];
        }
    }
}
