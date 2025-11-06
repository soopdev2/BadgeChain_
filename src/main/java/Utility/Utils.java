/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ResourceBundle;
import org.apache.commons.text.StringEscapeUtils;

/**
 *
 * @author Salvatore
 */
public class Utils {

    public static final ResourceBundle config = ResourceBundle.getBundle("conf.config");

    public static String generateSalt(int length) {
        byte[] salt = new byte[length];
        new SecureRandom().nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    }

    public static String calculateSha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String calculateSha256Hex(String text) throws Exception {
        return calculateSha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calcola l'hash SHA-256 dei dati forniti e lo restituisce in formato
     * esadecimale.
     */
    private static String calculateSha256HashHex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Errore nell'hashing SHA-256", e);
        }
    }

    /**
     * Calcola l'hash dell'Assertion in formato JSON.
     */
    public static String calculateAssertionHash(String json) {
        return calculateSha256HashHex(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Esegue l'hashing di un'email secondo lo standard Open Badges
     * (sha256$hash).
     *
     * @param email
     * @param salt
     * @return
     */
    public static String hashRecipientEmail(String email, String salt) {
        try {
            String combined = email.trim().toLowerCase() + salt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "sha256$" + hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Errore durante l'hashing dell'email", e);
        }
    }

    public static LocalDateTime calcolaScadenza(String scadenza) {
        scadenza = scadenza.trim().toLowerCase();

        String valoreNumerico = scadenza.replaceAll("[^0-9]", "");
        String tipo = scadenza.replaceAll("[0-9]", "");

        int valore = Integer.parseInt(valoreNumerico);
        LocalDateTime now = LocalDateTime.now();

        switch (tipo) {
            case "m" -> {
                return now.plusMinutes(valore);
            }
            case "mo" -> {
                return now.plusMonths(valore);
            }
            case "y" -> {
                return now.plusYears(valore);
            }
            default ->
                throw new IllegalArgumentException("Formato scadenza non valido: " + scadenza);
        }
    }

    public static Integer tryParseInt(String param) {
        try {
            return Integer.valueOf(param);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static Long tryParseLong(String param) {
        try {
            return Long.valueOf(param);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String sanitizeInputString(String input) {
        if (input == null) {
            return null;
        }

        // Normalize unicode
        String s = Normalizer.normalize(input, Normalizer.Form.NFKC);

        s = s.replaceAll("[\\p{Cntrl}]", "");

        s = s.replaceAll("\\p{C}", "");

        s = s.replaceAll("[<>\"'`{}\\[\\]|\\\\;$]", "");

        s = s.trim().replaceAll("\\s+", " ");

        return s;
    }
}
