package Services.logic;

import Entity.InfoTrack;
import Utility.JpaUtil;
import static Utility.Utils.calcolaScadenza;
import static Utility.Utils.config;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.Date;
import java.util.MissingResourceException;
import javax.crypto.SecretKey;

@Path("/oauth2")
public class AuthenticationService {

    public static final String CLIENT_SECRET = config.getString("CLIENT_SECRET");

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getToken(
            @FormParam("grant_type") String grantType,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret) {
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /oauth2/token - Genera token oauth2");
        infoTrack.setClient_id(clientId);

        if (!"client_credentials".equals(grantType)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid grant_type" + " " + grantType).build();
        }

        if (isValidCredentials(clientId, clientSecret)) {
            try {
                String accessToken = generateToken(CLIENT_SECRET, "access_token");
                //String refreshToken = generateRefreshToken(CLIENT_SECRET, "refresh_token");
                Instant expirationInstant = getExpirationInstantFromToken(accessToken, CLIENT_SECRET);
                //Instant expirationInstant2 = getExpirationInstantFromToken(refreshToken, clientSecret);

                //String jsonResponse = "{\"access_token\":\"" + accessToken + "\", \"expiration_date\":\"" + formatInstant(expirationInstant) + "\", \"refresh_token\":\"" + refreshToken + "\", \"refresh_token_expiration_date\":\"" + formatInstant(expirationInstant2) + "\"}";
                String jsonResponseSenzaRefresh = "{\"access_token\":\"" + accessToken + "\", \"expiration_date\":\"" + formatInstant(expirationInstant) + "\"}";

                infoTrack.setDescrizione("SUCCESSO - 200 - Generazione token avvenuta con successo.");
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.ok().entity(jsonResponseSenzaRefresh).type(MediaType.APPLICATION_JSON).build();
            } catch (Exception e) {
                infoTrack.setDescrizione("ERRORE - 500 - Errore durante la generazione del token oauth2 : " + e.getMessage());
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error during token generation").build();
            }
        } else {
            infoTrack.setDescrizione("ERRORE - 401 - Credenziali non valide.");
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
    }

//    @POST
//    @Path("/refreshToken")
//    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response refreshToken(
//            @FormParam("client_id") String clientId,
//            @FormParam("client_secret") String clientSecret,
//            @FormParam("refresh_token") String refreshToken) {
//
//        if (isValidRefreshToken(clientId, clientSecret, refreshToken)) {
//            try {
//                String newAccessToken = generateToken(CLIENT_SECRET, "access_token");
//                String newRefreshToken = generateRefreshToken(CLIENT_SECRET, "refresh_token");
//                Instant expirationInstantAccessToken = getExpirationInstantFromToken(newAccessToken, clientSecret);
//                Instant expirationInstantRefreshToken = getExpirationInstantFromToken(newRefreshToken, clientSecret);
//
//                String jsonResponse = "{\"access_token\":\"" + newAccessToken + "\", \"expiration_date\":\"" + formatInstant(expirationInstantAccessToken) + "\", \"refresh_token\":\"" + newRefreshToken + "\", \"refresh_token_expiration_date\":\"" + formatInstant(expirationInstantRefreshToken) + "\"}";
//
//                return Response.ok().entity(jsonResponse).type(MediaType.APPLICATION_JSON).build();
//            } catch (Exception e) {
//                e.printStackTrace();
//                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error during token generation").build();
//            }
//        } else {
//            return Response.status(Response.Status.UNAUTHORIZED).build();
//        }
//    }
//    @GET
//    @Path("/auth")
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response authenticate(@HeaderParam("Authorization") String authorizationHeader) {
//        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
//            return Response.status(Response.Status.UNAUTHORIZED)
//                    .entity("{\"message\":\"Missing or invalid Authorization header. Expected Bearer token.\"}")
//                    .build();
//        }
//
//        String token = authorizationHeader.substring("Bearer ".length());
//
//        try {
//            if (isValidToken(token) && isAccessToken(token)) {
//                return Response.ok().entity("{\"message\":\"Authentication successful. Token is valid.\"}")
//                        .build();
//            } else {
//                return Response.status(Response.Status.UNAUTHORIZED)
//                        .entity("{\"message\":\"Invalid or expired access token.\"}")
//                        .build();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
//                    .entity("{\"message\":\"Error during token validation.\"}")
//                    .build();
//        }
//    }
//    private boolean isValidRefreshToken(String clientId, String clientSecret, String refreshToken) {
//        EntityManagerFactory entityManagerFactory = null;
//        EntityManager entityManager = null;
//
//        try {
//            entityManagerFactory = Persistence.createEntityManagerFactory("gestionale_questionario");
//            entityManager = entityManagerFactory.createEntityManager();
//
//            TypedQuery<Utente> query = entityManager.createQuery(
//                    "SELECT u FROM Utente u WHERE u.username = :username", Utente.class
//            )
//                    .setParameter("username", clientId);
//
//            Utente utente = query.getSingleResult();
//
//            if (utente != null) {
//                Instant expirationInstant = getExpirationInstantFromToken(refreshToken, clientSecret);
//                if (expirationInstant != null && expirationInstant.isBefore(Instant.now())) {
//                    return false;
//                }
//
//                if (utente.getRefreshToken() != null && utente.getRefreshToken().equals(refreshToken)) {
//                    String newAccessToken = generateToken(CLIENT_SECRET, "access_token");
//                    String newRefreshToken = generateRefreshToken(CLIENT_SECRET, "refresh_token");
//                    utente.setRefreshToken(newRefreshToken);
//
//                    entityManager.getTransaction().begin();
//                    entityManager.merge(utente);
//                    entityManager.getTransaction().commit();
//
//                    return true;
//                } else {
//                    return false;
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            if (entityManager != null) {
//                entityManager.close();
//            }
//            if (entityManagerFactory != null) {
//                entityManagerFactory.close();
//            }
//        }
//
//        return false;
//    }
    public static boolean isValidCredentials(String clientId, String clientSecret) {
        int i = 1;

        while (true) {
            try {
                String storedClientId = config.getString("client_id_" + i);
                String storedClientSecret = config.getString("client_secret_" + i);

                if (storedClientId.equalsIgnoreCase(clientId) && storedClientSecret.equals(clientSecret)) {
                    return true;
                }

                i++;
            } catch (MissingResourceException e) {
                break;
            }
        }

        return false;
    }

    public static String generateToken(String clientSecret, String tokenType) {
        String scadenza = config.getString("scadenza_token");
        LocalDateTime scadenza_token = calcolaScadenza(scadenza);

        byte[] keyBytes = Base64.getDecoder().decode(clientSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        Instant now = Instant.now();
        Instant scadenzaInstant = scadenza_token.atZone(ZoneId.systemDefault()).toInstant();

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(scadenzaInstant))
                .claim("token_type", tokenType)
                .signWith(key)
                .compact();

        return token;
    }

    public static String generateRefreshToken(String clientSecret, String tokenType) {
        String scadenza = config.getString("scadenza_refreshToken");
        LocalDateTime scadenza_refresh_token = calcolaScadenza(scadenza);

        byte[] keyBytes = Base64.getDecoder().decode(clientSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        Instant now = Instant.now();
        Instant scadenzaInstant = scadenza_refresh_token.atZone(ZoneId.systemDefault()).toInstant();

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(scadenzaInstant))
                .claim("token_type", tokenType)
                .signWith(key)
                .compact();

        return token;
    }

    public String formatInstant(Instant instant) {
        if (instant == null) {
            return "Token is invalid or expired.";
        }
        return DateTimeFormatter.ISO_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    private static JwtParser createParser(String clientSecret) {
        byte[] keyBytes = Base64.getDecoder().decode(clientSecret);

        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.parser()
                .verifyWith(key)
                .build();
    }

    public static Instant getExpirationInstantFromToken(String token, String clientSecret) {
        try {
            JwtParser parser = createParser(clientSecret);

            Jws<Claims> claimsJws = parser.parseSignedClaims(token);

            Claims claims = claimsJws.getPayload();
            Date expirationDate = claims.getExpiration();

            if (expirationDate == null) {
                return null;
            }

            return expirationDate.toInstant();
        } catch (SignatureException e) {
            System.err.println("Token signature is invalid or key is wrong.");
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isValidToken(String token) {
        try {
            JwtParser parser = createParser(CLIENT_SECRET);
            parser.parseSignedClaims(token);

            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            System.err.println("Token is expired.");
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            System.err.println("Token signature is invalid.");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isAccessToken(String token) {
        try {
            JwtParser parser = createParser(CLIENT_SECRET);

            Jws<Claims> claimsJws = parser.parseSignedClaims(token);

            Claims claims = claimsJws.getPayload();

            return claims.containsKey("token_type") && "access_token".equals(claims.get("token_type"));

        } catch (io.jsonwebtoken.security.SignatureException e) {
            System.err.println("Token signature is invalid.");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String Auth(String clientId) {
        try {
            String accessToken = generateToken(CLIENT_SECRET, "access_token");
            String refreshToken = generateRefreshToken(CLIENT_SECRET, "refresh_token");
            //saveRefreshTokenToDatabase(clientId, refreshToken);
            return accessToken;
        } catch (Exception e) {
            e.printStackTrace();
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error during token generation").build();
        }

        return null;
    }
}

//    public static void saveRefreshTokenToDatabase(String clientId, String refreshToken) {
//        EntityManagerFactory entityManagerFactory = null;
//        EntityManager entityManager = null;
//
//        try {
//            entityManagerFactory = Persistence.createEntityManagerFactory("gestionale_questionario");
//            entityManager = entityManagerFactory.createEntityManager();
//
//            TypedQuery<Utente> query = entityManager.createQuery(
//                    "SELECT u FROM Utente u WHERE u.username = :username", Utente.class
//            )
//                    .setParameter("username", clientId);
//
//            Utente utente = query.getSingleResult();
//
//            if (utente != null) {
//                utente.setRefreshToken(refreshToken);
//                entityManager.getTransaction().begin();
//                entityManager.merge(utente);
//                entityManager.getTransaction().commit();
//            } else {
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            if (entityManager != null) {
//                entityManager.close();
//            }
//            if (entityManagerFactory != null) {
//                entityManagerFactory.close();
//            }
//        }
//    }
