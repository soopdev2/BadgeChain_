/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Api;

import ENUM.TipoAccessoEnum;
import Entity.InfoTrack;
import Entity.Utente;
import Services.Filter.Secured;
import static Utility.JpaUtil.salvaInfoTrack;
import static Utility.JpaUtil.trovaUtenteById;
import static Utility.Utils.calculateSha256Hex;
import static Utility.Utils.tryParseLong;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.json.JSONObject;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Salvatore
 */
@Path("/badge")
public class BadgeVerificationApi {

    @POST
    @Path("/verifica")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response verificaBadge(
            @QueryParam("user_id") String user_id_param,
            @QueryParam("selected_user_id") String selected_user_id_param,
            Map<String, String> input) {

        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /openbadge/verifica - Verifica badge");

        // (1) Validazione parametri principali
        String fileUrl = input.get("fileUrl");
        String txHash = input.get("txHash");

        if (fileUrl == null || txHash == null) {
            infoTrack.setDescrizione("ERRORE - 400 - Parametri mancanti: fileUrl, txHash, email sono obbligatori.");
            salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Parametri mancanti: fileUrl, txHash, email sono obbligatori."))
                    .build();
        }

        // (2) Validazione utenti
        if (user_id_param == null || selected_user_id_param == null) {
            infoTrack.setDescrizione("ERRORE - 400 - Parametri user_id e selected_user_id sono obbligatori.");
            salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Parametri user_id e selected_user_id sono obbligatori."))
                    .build();
        }

        Long user_id = tryParseLong(user_id_param);
        Long selected_user_id = tryParseLong(selected_user_id_param);

        Utente utente_mittente = trovaUtenteById(user_id);
        Utente utente_destinatario = trovaUtenteById(selected_user_id);

        if (utente_mittente == null) {
            infoTrack.setDescrizione("ERRORE - 404 - Utente mittente non trovato.");
            salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Utente mittente non trovato."))
                    .build();
        }

        if (utente_destinatario == null) {
            infoTrack.setDescrizione("ERRORE - 404 - Utente destinatario non trovato.");
            salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Utente destinatario non trovato."))
                    .build();
        }

        if (utente_destinatario.getEmail() == null) {
            infoTrack.setDescrizione("ERRORE - 404 - Email utente destinatario non trovata.");
            salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Email utente destinatario non trovata."))
                    .build();
        }

        if (!utente_mittente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN) && !utente_mittente.getId().equals(utente_destinatario.getId())) {
            infoTrack.setDescrizione("ERRORE - 401 - Ruolo non autorizzato.");
            salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Ruolo non autorizzato."))
                    .build();
        }

        try {
            // (3) Scarica file assertion.json
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                infoTrack.setDescrizione("ERRORE - 400 - Download file badge fallito: HTTP " + response.statusCode());
                salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Errore download file badge: HTTP " + response.statusCode()))
                        .build();
            }

            byte[] fileBytes = response.body();

            // (4) Calcola hash SHA-256 locale
            String localFileHash = "0x" + Numeric.toHexStringNoPrefix(
                    MessageDigest.getInstance("SHA-256").digest(fileBytes)
            );

            // (5) Recupera hash on-chain
            String onChainHash;
            try (Web3j web3j = Web3j.build(new HttpService("https://ethereum-sepolia-rpc.publicnode.com"))) {
                EthTransaction ethTransaction = web3j.ethGetTransactionByHash(txHash).send();
                Transaction transaction = ethTransaction.getTransaction()
                        .orElseThrow(() -> new IllegalStateException("Transazione non trovata su blockchain."));
                onChainHash = transaction.getInput();
            }

            boolean hashCorrisponde = onChainHash.equalsIgnoreCase(localFileHash);

            // (6) Verifica identità email
            String jsonText = new String(fileBytes, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(jsonText);
            JSONObject recipient = json.getJSONObject("recipient");

            String identity = recipient.getString("identity");
            String salt = recipient.optString("salt", "");
            String expectedHash = identity.replace("sha256$", "").trim();

            String emailToHash = utente_destinatario.getEmail().trim().toLowerCase() + salt;
            String emailHash = calculateSha256Hex(emailToHash);

            boolean emailCorrisponde = emailHash.equalsIgnoreCase(expectedHash);

            // (7) Crea risultato
            Map<String, Object> result = new HashMap<>();
            result.put("fileUrl", fileUrl);
            result.put("txHash", txHash);
            result.put("localFileHash", localFileHash);
            result.put("onChainHash", onChainHash);
            result.put("emailHash", emailHash);
            result.put("expectedHash", expectedHash);
            result.put("emailMatch", emailCorrisponde);
            result.put("hashMatch", hashCorrisponde);

            boolean isValid = hashCorrisponde && emailCorrisponde;
            result.put("valid", isValid);

            String message;
            if (hashCorrisponde && emailCorrisponde) {
                message = "✅ Badge verificato correttamente: corrisponde alla registrazione on-chain.";
            } else if (!hashCorrisponde) {
                message = "⚠️️ Errore: il badge non è valido o non risulta registrato correttamente on-chain.";
            } else if (!emailCorrisponde) {
                message = "⚠️️️ Errore: il badge non appartiene all’utente selezionato (email non corrispondente).";
            } else {
                message = "⚠️ Errore :️ Badge non valido o dati incoerenti.";
            }

            result.put("message", message);

            // (8) Tracciamento finale
            if (hashCorrisponde && emailCorrisponde) {
                infoTrack.setDescrizione("SUCCESSO - 200 - Badge verificato correttamente e coerente con la blockchain.");
            } else {
                infoTrack.setDescrizione("ATTENZIONE - 200 - Badge verificato ma non corrispondente: hash o email non coincidono.");
            }

            salvaInfoTrack(infoTrack);
            return Response.ok(result).build();

        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore durante la verifica del badge: " + e.getMessage());
            salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore durante la verifica del badge: " + e.getMessage()))
                    .build();
        }
    }

}
