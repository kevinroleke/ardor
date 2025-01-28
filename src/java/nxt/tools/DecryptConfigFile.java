/*
 * Copyright © 2024 Jelurida Swiss SA
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

package nxt.tools;

import nxt.crypto.Crypto;
import nxt.util.Convert;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DecryptConfigFile {
    public static void main(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-h")) {
            printHelp();
            return;
        }

        if (args.length < 2) {
            System.out.println("Error: Missing arguments.\n");
            printHelp();
            return;
        }

        try {
            byte[] key = Crypto.sha256().digest(Convert.toBytes(args[1]));
            Path path = Paths.get(args[0]);
            if (!Files.isReadable(path)) {
                System.out.println("Cannot open " + path);
                return;
            }

            byte[] data = Files.readAllBytes(path);
            byte[] decrypted = Crypto.aesDecrypt(data, key);
            System.out.println("Contents:");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(decrypted)))) {
                String line;
                while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
                    System.out.println(line);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -cp conf:classes:lib/* nxt.tools.DecryptConfigFile <file_path> <password>");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <file_path>   Path to the encrypted file.");
        System.out.println("  <password>    Password to decrypt the file as specified when the configuration file was created.");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -cp conf:classes:lib/* nxt.tools.DecryptConfigFile contractRunner mySecretKey");
    }
}
