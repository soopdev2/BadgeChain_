
import static Utility.Utils.calculateSha256Hex;
import static Utility.Utils.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

public class BadgeVerifier {

    private static final Logger log = LogManager.getLogger(BadgeVerifier.class);

    public static void main(String[] args) {
        log.info("Inizio verifica badge");

        try {
            // URL del badge
            String fileUrl = config.getString("badge_file_url");

            // Download badge
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("Errore durante il download del file: HTTP {}", response.statusCode());
                return;
            }

            byte[] fileBytes = response.body();
            String localFileHash = "0x" + Numeric.toHexStringNoPrefix(
                    MessageDigest.getInstance("SHA-256").digest(fileBytes)
            );
            log.info("Hash calcolato localmente: {}", localFileHash);

            // Connessione a Sepolia Ethereum
            Web3j web3j = Web3j.build(new HttpService("https://ethereum-sepolia-rpc.publicnode.com"));
            try {
                log.info("Connesso alla rete: Sepolia (public node)");

                // HASH della transazione da verificare
                String txHash = config.getString("tx_hash");

                EthTransaction ethTransaction = web3j.ethGetTransactionByHash(txHash).send();
                Transaction transaction = ethTransaction.getTransaction().orElseThrow(()
                        -> new IllegalStateException("Transazione non trovata!")
                );

                String onChainHash = transaction.getInput();
                log.info("Dato on-chain: {}", onChainHash);

                if (onChainHash.equalsIgnoreCase(localFileHash)) {
                    log.info("Il badge è valido! Il file corrisponde alla registrazione on-chain.");
                } else {
                    log.error("Il badge NON corrisponde. Hash diverso da quello registrato.");
                    return;
                }

                // Verifica email
                String jsonText = new String(fileBytes, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonText);
                JSONObject recipient = json.getJSONObject("recipient");

                String identity = recipient.getString("identity");
                String salt = recipient.optString("salt", "");

                if (!identity.startsWith("sha256$")) {
                    log.error("Formato dell'identità non valido: {}", identity);
                    return;
                }

                String expectedHash = identity.substring("sha256$".length());

                try (Scanner scanner = new Scanner(System.in)) {
                    log.info("Attesa input utente per email da verificare");
                    System.out.println("Inserisci l'email da verificare: ");
                    String email = scanner.nextLine().trim().toLowerCase();

                    String emailToHash = email + salt;
                    String emailHash = calculateSha256Hex(emailToHash);

                    log.info("Hash email calcolato: {}", emailHash);
                    log.info("Hash atteso dal badge: {}", expectedHash);

                    if (emailHash.equalsIgnoreCase(expectedHash)) {
                        log.info("L'email inserita corrisponde al destinatario del badge.");
                    } else {
                        log.warn("L'email NON corrisponde al destinatario del badge.");
                    }
                }
            } catch (Exception e) {
                log.error("Errore durante la verifica.", e);
            } finally {
                web3j.shutdown();
            }

        } catch (Exception e) {
            log.error("Errore durante l'esecuzione del BadgeVerifier", e);
        }
    }
}
