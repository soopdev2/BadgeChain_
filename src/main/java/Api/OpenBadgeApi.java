/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Api;

import ENUM.TipoAccessoEnum;
import Entity.InfoTrack;
import Entity.Utente;
import Services.Filter.Secured;
import Utility.JpaUtil;
import static Utility.JpaUtil.trovaUtenteById;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static Utility.Utils.config;
import static Utility.Utils.generateSalt;
import static Utility.Utils.hashRecipientEmail;
import static Utility.Utils.tryParseInt;
import static Utility.Utils.tryParseLong;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.NoOpProcessor;
import org.web3j.utils.Numeric;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 *
 * @author Salvatore
 */
@Path("/openbadge")
public class OpenBadgeApi {

    private static final String ACCESS_KEY = config.getString("access_key");
    private static final String SECRET_KEY = config.getString("secret_key");
    private static final String AWS_REGION = config.getString("aws_region");
    private static final String BUCKET_NAME = config.getString("bucket_name");
    private static final String BASE_URL = "https://" + BUCKET_NAME + ".s3." + AWS_REGION + ".amazonaws.com/";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private S3Client createS3Client() {
        return S3Client.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
                ))
                .build();
    }

    @POST
    @Path("/genera")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response generaBadge(
            @QueryParam("user_id") String user_id_param,
            @QueryParam("selected_user_id") String selected_user_id_param,
            Map<String, Object> input) {

        String badgeName = (String) input.getOrDefault("badgeName", "Reader");
        String badgeDescription = (String) input.getOrDefault("badgeDescription", "Reader generico.");
        String imageFileName = (String) input.getOrDefault("imageFileName", "");
        String imageUrl = (String) input.getOrDefault("imageUrl", "");

        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /openbadge/genera - Generazione badge");

        // (1) Validazione criteriaPoints
        List<Map<String, String>> criteriaData;
        try {
            criteriaData = (List<Map<String, String>>) input.getOrDefault("criteriaPoints", new ArrayList<>());
            if (criteriaData.isEmpty()) {
                criteriaData = List.of(Map.of("titolo", "Esame superato", "valore", "N/A"));
            }
        } catch (ClassCastException e) {
            infoTrack.setDescrizione("ERRORE - 400 - Il campo criteriaPoints deve essere una lista di oggetti {titolo: '...', valore: '...'}.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Il campo criteriaPoints deve essere una lista di oggetti {titolo: '...', valore: '...'}"))
                    .build();
        }

        // (2) Controllo max_criteria_count
        String MAX_CRITERIA_COUNT_STRING = config.getString("MAX_CRITERIA_COUNT");
        final int MAX_CRITERIA_COUNT = tryParseInt(MAX_CRITERIA_COUNT_STRING);
        if (MAX_CRITERIA_COUNT <= 0) {
            infoTrack.setDescrizione("ERRORE - 500 - Configurazione non valida: MAX_CRITERIA_COUNT non definito o non valido.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Configurazione non valida: MAX_CRITERIA_COUNT non definito o non valido."))
                    .build();
        }

        if (criteriaData.size() > MAX_CRITERIA_COUNT) {
            infoTrack.setDescrizione("ERRORE - 400 - Il campo criteriaPoints non può contenere più di " + MAX_CRITERIA_COUNT + " elementi.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Il campo criteriaPoints non può contenere più di " + MAX_CRITERIA_COUNT + " elementi."))
                    .build();
        }

        // (3) Validazione parametri utente
        if (user_id_param == null || selected_user_id_param == null) {
            infoTrack.setDescrizione("ERRORE - 400 - Parametri user_id e selected_user_id sono obbligatori.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Parametri user_id e selected_user_id sono obbligatori."))
                    .build();
        }

        Long selected_user_id = tryParseLong(selected_user_id_param);
        Utente utente_destinatario = trovaUtenteById(selected_user_id);

        if (utente_destinatario == null) {
            infoTrack.setDescrizione("ERRORE - 404 - Utente destinatario non trovato.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Utente destinatario non trovato."))
                    .build();
        }

        if (utente_destinatario.getEmail() == null) {
            infoTrack.setDescrizione("ERRORE - 400 - L'indirizzo email dell'utente destinatario non è valido.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "L'indirizzo email dell'utente destinatario non è valido."))
                    .build();
        }

        Long user_id = tryParseLong(user_id_param);
        Utente utente_mittente = trovaUtenteById(user_id);

        if (utente_mittente == null) {
            infoTrack.setDescrizione("ERRORE - 404 - Utente mittente non trovato.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Utente mittente non trovato."))
                    .build();
        }

        if (!utente_mittente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN)) {
            infoTrack.setDescrizione("ERRORE - 401 - Ruolo non autorizzato.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Ruolo non autorizzato."))
                    .build();
        }

        if (utente_mittente.getName() == null) {
            infoTrack.setDescrizione("ERRORE - 400 - Il nome dell'utente mittente non è valido.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Il nome dell'utente mittente non è valido."))
                    .build();
        }

        if (utente_mittente.getUrl() == null) {
            infoTrack.setDescrizione("ERRORE - 400 - L'url dell'utente mittente non è valido.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "L'url dell'utente mittente non è valido."))
                    .build();
        }

        // (4) Gestione immagine badge
        try (S3Client s3 = createS3Client()) {

            String uniqueIdImage = UUID.randomUUID().toString().substring(0, 8);

            if (imageUrl != null && !imageUrl.isBlank()) {
                if (imageUrl.startsWith("http")) {
                    infoTrack.setDescrizione("ERRORE - 400 - immagine badge fornita come URL esterno.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Immagine badge fornita come URL esterno."))
                            .build();
                } else if (imageUrl.startsWith("data:")) {
                    try {
                        int commaIndex = imageUrl.indexOf(",");
                        if (commaIndex <= 0) {
                            infoTrack.setDescrizione("ERRORE - 400 - Formato base64 non valido.");
                            JpaUtil.salvaInfoTrack(infoTrack);
                            return Response.status(Response.Status.BAD_REQUEST)
                                    .entity(Map.of("error", "Formato base64 non valido."))
                                    .build();
                        }

                        String meta = imageUrl.substring(5, commaIndex);
                        String base64Data = imageUrl.substring(commaIndex + 1);
                        String contentType = meta.contains(";") ? meta.substring(0, meta.indexOf(";")) : meta;

                        byte[] imageBytes = Base64.getDecoder().decode(base64Data);

                        String extension = switch (contentType) {
                            case "image/jpeg" ->
                                "jpg";
                            case "image/png" ->
                                "png";
                            case "image/gif" ->
                                "gif";
                            default ->
                                "png";
                        };

                        imageFileName = (imageFileName.isBlank() ? "badge" : imageFileName)
                                + "-" + uniqueIdImage + "." + extension;

                        uploadToS3Base64(s3, imageFileName, imageBytes, contentType, infoTrack);

                        imageUrl = BASE_URL + imageFileName;

                        infoTrack.setDescrizione("SUCCESSO - 200 - Immagine badge caricata su S3 con successo: " + imageUrl);
                        JpaUtil.salvaInfoTrack(infoTrack);

                    } catch (Exception e) {
                        infoTrack.setDescrizione("ERRORE - Caricamento immagine base64 fallito: " + e.getMessage());
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(Map.of("error", "Immagine base64 non valida o upload fallito."))
                                .build();
                    }
                } else {
                    infoTrack.setDescrizione("ERRORE - Formato campo imageUrl non valido.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Il campo imageUrl deve essere un URL valido o un data URL base64."))
                            .build();
                }
            } else if (imageFileName != null && !imageFileName.isBlank()) {
                imageUrl = BASE_URL + imageFileName;
                infoTrack.setDescrizione("ATTENZIONE - Usato imageFileName fornito senza upload.");
                JpaUtil.salvaInfoTrack(infoTrack);
            } else {
                imageFileName = "default-badge.png";
                imageUrl = BASE_URL + imageFileName;
                infoTrack.setDescrizione("ATTENZIONE - Immagine di default assegnata.");
                JpaUtil.salvaInfoTrack(infoTrack);
            }

            // (5) Generazione issuer
            String uniqueIdIssuer = UUID.randomUUID().toString().substring(0, 10);
            Map<String, Object> issuer = Map.of(
                    "@context", "https://w3id.org/openbadges/v2",
                    "id", BASE_URL + "issuer-organization-" + uniqueIdIssuer + ".json",
                    "type", "Issuer",
                    "name", utente_mittente.getName(),
                    "url", utente_mittente.getUrl()
            );
            String issuerJson = gson.toJson(issuer);

            // (6) Generazione criteria.html
            String uniqueIdCriteria = UUID.randomUUID().toString().substring(0, 10);
            String criteriaFileName = "criteria-" + uniqueIdCriteria + ".html";
            String criteriaUrl = BASE_URL + criteriaFileName;
            String criteriaHtml = createCriteriaHtml(badgeName, criteriaData);

            // (7) Generazione badge
            String uniqueIdReader = UUID.randomUUID().toString().substring(0, 10);
            Map<String, Object> badge = new HashMap<>();
            badge.put("@context", "https://w3id.org/openbadges/v2");
            badge.put("id", BASE_URL + "reader-badge-" + uniqueIdReader + ".json");
            badge.put("type", "BadgeClass");
            badge.put("name", badgeName);
            badge.put("description", badgeDescription);
            badge.put("image", imageUrl);
            badge.put("tags", new String[]{"badge", "openbadge"});

            String titlesNarrative = criteriaData.stream()
                    .map(map -> "\n• " + map.getOrDefault("titolo", "Criterio non specificato"))
                    .collect(Collectors.joining());
            String linkText = "\n\nPer visualizzare i dettagli apri il seguente link: " + criteriaUrl;
            String narrativeText = "I criteri soddisfatti sono:" + titlesNarrative + linkText;

            badge.put("criteria", Map.of(
                    "type", "Criteria",
                    "url", criteriaUrl,
                    "narrative", narrativeText
            ));
            badge.put("alignment", new Object[]{});
            badge.put("issuer", BASE_URL + "issuer-organization-" + uniqueIdIssuer + ".json");

            String readerBadgeJson = gson.toJson(badge);

            // (8) Generazione assertion
            String uniqueIdAssertion = UUID.randomUUID().toString().substring(0, 10);
            String assertionFileName = "assertion-" + uniqueIdAssertion + ".json";
            String assertionUrl = BASE_URL + assertionFileName;

            String salt = generateSalt(16);
            String hashedIdentity = hashRecipientEmail(utente_destinatario.getEmail(), salt);

            Map<String, Object> recipient = Map.of(
                    "type", "email",
                    "hashed", true,
                    "identity", hashedIdentity,
                    "salt", salt
            );

            Map<String, Object> verification = Map.of(
                    "type", "hosted",
                    "url", assertionUrl
            );

            Map<String, Object> assertion = new HashMap<>();
            assertion.put("@context", "https://w3id.org/openbadges/v2");
            assertion.put("id", assertionUrl);
            assertion.put("type", "Assertion");
            assertion.put("recipient", recipient);
            assertion.put("issuedOn", Instant.now().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString());
            OffsetDateTime expires = OffsetDateTime.now(ZoneOffset.UTC).plusYears(5);
            assertion.put("expires", expires.truncatedTo(ChronoUnit.SECONDS).toString());
            assertion.put("badge", BASE_URL + "reader-badge-" + uniqueIdReader + ".json");
            assertion.put("verification", verification);

            String assertionJson = gson.toJson(assertion);

            // (9) Upload su S3
            uploadToS3(s3, "issuer-organization-" + uniqueIdIssuer + ".json", issuerJson, infoTrack);
            uploadToS3(s3, criteriaFileName, criteriaHtml, infoTrack);
            uploadToS3(s3, "reader-badge-" + uniqueIdReader + ".json", readerBadgeJson, infoTrack);
            uploadToS3(s3, assertionFileName, assertionJson, infoTrack);

            // (10) Calcolo hash
            String fileUrl = BASE_URL + assertionFileName;
            String hashHex;
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(fileUrl))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    infoTrack.setDescrizione("ERRORE - 500 - Impossibile scaricare il file per il calcolo dell'hash: HTTP " + response.statusCode());
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(Map.of("error", "Errore durante il recupero del file per il calcolo dell'hash."))
                            .build();
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream inputStream = response.body()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        digest.update(buffer, 0, bytesRead);
                    }
                }
                byte[] hashBytes = digest.digest();
                hashHex = Numeric.toHexStringNoPrefix(hashBytes);

            } catch (Exception e) {
                infoTrack.setDescrizione("ERRORE - 500 - Errore durante il calcolo dell'hash: " + e.getMessage());
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of("error", "Errore durante il calcolo dell'hash."))
                        .build();
            }

            // (11) Invio hash su blockchain
            String txHash;
            try {
                txHash = inviaHashSuBlockchain(hashHex, infoTrack);
                if (txHash == null || txHash.isBlank()) {
                    infoTrack.setDescrizione("ERRORE - 502 - Invio dell'hash sulla blockchain fallito.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.BAD_GATEWAY)
                            .entity(Map.of("error", "Invio dell'hash sulla blockchain fallito."))
                            .build();
                }
            } catch (Exception e) {
                infoTrack.setDescrizione("ERRORE - 502 - Errore durante l'invio dell'hash alla blockchain: " + e.getMessage());
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_GATEWAY)
                        .entity(Map.of("error", "Errore durante l'invio dell'hash alla blockchain: " + e.getMessage()))
                        .build();
            }

            // (12) Risposta finale
            Map<String, Object> response = new HashMap<>();
            response.put("issuerUrl", BASE_URL + "issuer-organization-" + uniqueIdIssuer + ".json");
            response.put("badgeUrl", BASE_URL + "reader-badge-" + uniqueIdReader + ".json");
            response.put("criteriaUrl", criteriaUrl);
            response.put("assertionUrl", assertionUrl);
            response.put("imageUrl", imageUrl);
            response.put("txHash", txHash);
            response.put("message", "✅ Badge generato e hash registrato sulla blockchain.");

            infoTrack.setDescrizione("SUCCESSO - 200 - L'openbadge è stato salvato e registrato sulla blockchain.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.ok(response).build();

        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore interno durante la generazione del badge: " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore interno durante la generazione del badge: " + e.getMessage()))
                    .build();
        }
    }

    private String createCriteriaHtml(String badgeName, List<Map<String, String>> criteriaData) {
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html><html><head>");
        htmlContent.append("<title>Criteri di: ").append(badgeName).append("</title>");
        htmlContent.append("<meta charset=\"UTF-8\">");

        htmlContent.append("<style>");
        htmlContent.append("body { font-family: sans-serif; margin: 2em; } h1 { color: #333; }");
        htmlContent.append("table { width: 100%; border-collapse: collapse; margin-top: 1.5em; }");
        htmlContent.append("th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }");
        htmlContent.append("th { background-color: #f2f2f2; color: #555; font-weight: bold; }");
        htmlContent.append("</style>");

        htmlContent.append("</head><body>");
        htmlContent.append("<h1>Criteri per il badge: ").append(badgeName).append("</h1>");
        htmlContent.append("<p>Il destinatario ha soddisfatto i seguenti requisiti per ricevere il badge:</p>");

        htmlContent.append("<table>");
        htmlContent.append("<thead><tr><th>Criterio/Titolo</th><th>Risultato/Valore</th></tr></thead>");
        htmlContent.append("<tbody>");

        for (Map<String, String> data : criteriaData) {
            String titolo = data.getOrDefault("titolo", "Criterio non specificato");
            String valore = data.getOrDefault("valore", "N/A");

            htmlContent.append("<tr>");
            htmlContent.append("<td>").append(titolo).append("</td>");
            htmlContent.append("<td>").append(valore).append("</td>");
            htmlContent.append("</tr>");
        }

        htmlContent.append("</tbody>");
        htmlContent.append("</table>");

        htmlContent.append("<hr><p>Questo documento fa parte della validazione Open Badge.</p>");
        htmlContent.append("</body></html>");
        return htmlContent.toString();
    }

    private String inviaHashSuBlockchain(String hashHex, InfoTrack infoTrack) {
        String nodeUrl = config.getString("aws_peer");
        if (nodeUrl == null || nodeUrl.isBlank()) {
            nodeUrl = "https://ethereum-sepolia-rpc.publicnode.com";
        }

        String privateKey = config.getString("sepolia_private_key");
        String recipient = config.getString("tx_recipient_address");
        if (recipient == null || recipient.isBlank()) {
            recipient = "0x41a7949d7f7fe6B1FA3271e4325cDCE7de5Fd07a";
        }

        Web3j web3j = Web3j.build(new HttpService(nodeUrl));
        try {
            Credentials credentials = Credentials.create(privateKey);
            BigInteger chainId = web3j.ethChainId().send().getChainId();
            BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigInteger gasLimit = BigInteger.valueOf(100_000);

            String data = "0x" + hashHex;
            RawTransaction rawTx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, recipient, BigInteger.ZERO, data);

            RawTransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId.longValue(), new NoOpProcessor(web3j));
            EthSendTransaction resp = txManager.signAndSend(rawTx);

            if (resp.hasError()) {
                String errorMsg = "ERRORE - 502 - Invio hash sulla blockchain fallito: " + resp.getError().getMessage();
                infoTrack.setDescrizione(errorMsg);
                JpaUtil.salvaInfoTrack(infoTrack);
            }

            String txHash = resp.getTransactionHash();
            if (txHash == null || txHash.isBlank()) {
                String errorMsg = "ERRORE - 502 - Hash inviato ma risposta nulla o vuota dalla blockchain.";
                infoTrack.setDescrizione(errorMsg);
                JpaUtil.salvaInfoTrack(infoTrack);
            }

            infoTrack.setDescrizione("SUCCESSO - 200 - Hash registrato sulla blockchain. TxHash: " + txHash);
            JpaUtil.salvaInfoTrack(infoTrack);

            return txHash;

        } catch (Exception e) {
            String errorMsg = "ERRORE - 502 - Eccezione durante invio hash alla blockchain: " + e.getMessage();
            infoTrack.setDescrizione(errorMsg);
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new RuntimeException(e);
        } finally {
            web3j.shutdown();
        }
    }

    private void uploadToS3(S3Client s3, String fileName, String content, InfoTrack infoTrack) throws Exception {
        try {
            String contentType;
            if (fileName.endsWith(".html")) {
                contentType = "text/html";
            } else if (fileName.endsWith(".json")) {
                contentType = "application/json";
            } else {
                contentType = "application/octet-stream";
            }

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3.putObject(putOb, RequestBody.fromString(content));

            infoTrack.setDescrizione("SUCCESSO - File caricato su S3: " + fileName);
            JpaUtil.salvaInfoTrack(infoTrack);

        } catch (S3Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore S3 durante l'upload di " + fileName + ": " + e.awsErrorDetails().errorMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new Exception("Errore durante l'upload su S3 (" + fileName + "): " + e.awsErrorDetails().errorMessage());

        } catch (SdkClientException e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore di connessione S3 durante l'upload di " + fileName + ": " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new Exception("Errore di connessione al servizio S3 (" + fileName + "): " + e.getMessage());

        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore generico durante l'upload di " + fileName + ": " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
        }
    }

    private void uploadToS3Base64(S3Client s3, String fileName, Object content, String contentType, InfoTrack infoTrack) throws Exception {
        try {
            if (contentType == null || contentType.isBlank()) {
                if (fileName.endsWith(".html")) {
                    contentType = "text/html";
                } else if (fileName.endsWith(".json")) {
                    contentType = "application/json";
                } else if (fileName.endsWith(".png")) {
                    contentType = "image/png";
                } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (fileName.endsWith(".gif")) {
                    contentType = "image/gif";
                } else {
                    contentType = "application/octet-stream";
                }
            }

            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            if (content instanceof String textContent) {
                s3.putObject(putOb, RequestBody.fromString(textContent));
            } else if (content instanceof byte[] byteContent) {
                s3.putObject(putOb, RequestBody.fromBytes(byteContent));
            } else {
                throw new IllegalArgumentException("Tipo di contenuto non supportato per upload S3: " + content.getClass());
            }

            infoTrack.setDescrizione("SUCCESSO - File caricato su S3: " + fileName);
            JpaUtil.salvaInfoTrack(infoTrack);

        } catch (S3Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore S3 durante l'upload di " + fileName + ": " + e.awsErrorDetails().errorMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new Exception("Errore durante l'upload su S3 (" + fileName + "): " + e.awsErrorDetails().errorMessage());

        } catch (SdkClientException e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore di connessione S3 durante l'upload di " + fileName + ": " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            throw new Exception("Errore di connessione al servizio S3 (" + fileName + "): " + e.getMessage());

        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore generico durante l'upload di " + fileName + ": " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            throw e;
        }
    }

}
