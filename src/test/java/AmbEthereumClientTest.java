
import static Utility.Utils.config;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.NoOpProcessor;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

public class AmbEthereumClientTest {

    private static final Logger log = LogManager.getLogger(AmbEthereumClientTest.class);

    public static void main(String[] args) {
        try {
            // Endpoint del nodo Ethereum (Sepolia o AWS AMB)
            String nodeUrl = config.getString("aws_peer");
            if (nodeUrl == null || nodeUrl.isBlank()) {
                nodeUrl = "https://ethereum-sepolia-rpc.publicnode.com"; //URL pubblico Sepolia
                log.warn("Parametro 'aws_peer' non impostato — uso endpoint pubblico Sepolia.");
            }

            // Chiave privata (account Ethereum Sepolia)
            String privateKey = config.getString("sepolia_private_key");
            if (privateKey == null || privateKey.isBlank()) {
                throw new IllegalStateException("Chiave privata non trovata: configura 'sepolia_private_key'.");
            }

            // URL del file badge da caricare
            String fileUrl = config.getString("badge_file_url");
            if (fileUrl == null || fileUrl.isBlank()) {
                fileUrl = "https://openbagetest.s3.eu-central-1.amazonaws.com/assertion.json";
                log.warn("Parametro 'badge_file_url' non impostato — uso URL di test.");
            }

            // Indirizzo destinatario
            String wallet = config.getString("tx_recipient_address");
            if (wallet == null || wallet.isBlank()) {
                wallet = "0x41a7949d7f7fe6B1FA3271e4325cDCE7de5Fd07a";
                log.warn("Parametro 'tx_recipient_address' non impostato — uso indirizzo di test.");
            }

            //Connessione al nodo Etherum
            Web3j web3j = Web3j.build(new HttpService(nodeUrl));
            try {
                log.info("Connesso al nodo Ethereum: {}", nodeUrl);

                // Ottieni Chain ID
                EthChainId chainIdResp = web3j.ethChainId().send();
                BigInteger chainId = chainIdResp.getChainId();
                log.info("Chain ID rilevato: {}", chainId);

                if (!chainId.equals(BigInteger.valueOf(11155111L))) {
                    log.warn("⚠️  ATTENZIONE: Non sei sulla rete Sepolia. Verifica l'endpoint.");
                }

                // Crea credenziali
                Credentials credentials = Credentials.create(privateKey);
                String address = credentials.getAddress();
                log.info("Account mittente: {}", address);

                // Verifica saldo
                EthGetBalance balanceResp = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
                BigInteger balance = balanceResp.getBalance();
                log.info("Saldo in Wei: {}", balance);

                if (balance.equals(BigInteger.ZERO)) {
                    throw new IllegalStateException("❌ Saldo 0. Ottieni ETH Sepolia da un faucet prima di procedere.");
                }

                //Calcolo hash del file
                log.info("Calcolo hash SHA-256 del file badge...");
                String data;
                try (InputStream inputStream = new URL(fileUrl).openStream()) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        digest.update(buffer, 0, bytesRead);
                    }
                    byte[] hashBytes = digest.digest();
                    String hashHex = Numeric.toHexStringNoPrefix(hashBytes);
                    data = "0x" + hashHex;
                    log.info("Hash calcolato: {}", data);
                }

                //Preparazione transazione
                EthGetTransactionCount txCountResp = web3j.ethGetTransactionCount(
                        address, DefaultBlockParameterName.PENDING).send();
                BigInteger nonce = txCountResp.getTransactionCount();
                log.info("Nonce attuale: {}", nonce);

                BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
                log.info("Gas price: {}", gasPrice);

                // Stima gas
                org.web3j.protocol.core.methods.request.Transaction estimateTx
                        = org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                                address, nonce, gasPrice, DefaultGasProvider.GAS_LIMIT, wallet, data);

                EthEstimateGas estimateGasResp = web3j.ethEstimateGas(estimateTx).send();
                BigInteger gasLimit = DefaultGasProvider.GAS_LIMIT;

                if (estimateGasResp.getError() == null && estimateGasResp.getAmountUsed() != null) {
                    gasLimit = estimateGasResp.getAmountUsed().add(BigInteger.valueOf(10_000));
                } else {
                    log.warn("Errore stima gas — uso valore di default: {}", gasLimit);
                }
                log.info("Gas limit finale: {}", gasLimit);

                //Invio della transazione
                RawTransaction rawTx = RawTransaction.createTransaction(
                        nonce, gasPrice, gasLimit, wallet, BigInteger.ZERO, data);

                RawTransactionManager txManager = new RawTransactionManager(
                        web3j, credentials, chainId.longValue(), new NoOpProcessor(web3j));

                EthSendTransaction sendResp = txManager.signAndSend(rawTx);
                if (sendResp.hasError()) {
                    log.error("❌ Errore durante l'invio della transazione: {}", sendResp.getError().getMessage());
                    return;
                }

                String txHash = sendResp.getTransactionHash();
                log.info("✅ Transazione inviata con successo!");
                log.info("🔗 Hash transazione: {}", txHash);

                // Attesa per la ricevuta
                int attempts = 60;
                int intervalSeconds = 5;
                Optional<TransactionReceipt> receiptOpt = Optional.empty();

                for (int i = 0; i < attempts; i++) {
                    receiptOpt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
                    if (receiptOpt.isPresent()) {
                        log.info("Receipt trovata al tentativo {}/{}", i + 1, attempts);
                        break;
                    }
                    log.info("Attesa della receipt... ({}/{})", i + 1, attempts);
                    try {
                        TimeUnit.SECONDS.sleep(intervalSeconds);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Polling interrotto", e);
                        break;
                    }
                }

                if (receiptOpt.isPresent()) {
                    TransactionReceipt receipt = receiptOpt.get();
                    log.info("✅ Receipt ricevuta: block={}, gasUsed={}", receipt.getBlockNumber(), receipt.getGasUsed());
                } else {
                    log.warn("⚠️ Timeout: receipt non trovata. Controlla manualmente su Etherscan.");
                }

                if (receiptOpt.isPresent()) {
                    TransactionReceipt receipt = receiptOpt.get();
                    log.info("📦 Receipt ricevuta:");
                    log.info("Block #: {}", receipt.getBlockNumber());
                    log.info("Status  : {}", receipt.getStatus());
                    log.info("Gas usato: {}", receipt.getGasUsed());
                    log.info("🔗 Etherscan: https://sepolia.etherscan.io/tx/{}", txHash);
                } else {
                    log.warn("⚠️ Timeout: receipt non trovata. Verifica manualmente su Etherscan:");
                    log.warn("🔗 https://sepolia.etherscan.io/tx/{}", txHash);
                }
            } catch (Exception e) {
                log.error("Errore", e);
            } finally {
                if (web3j != null) {
                    web3j.shutdown();
                }
            }

        } catch (Exception e) {
            log.error("❌ Errore durante l'esecuzione:", e);
        }
    }
}
