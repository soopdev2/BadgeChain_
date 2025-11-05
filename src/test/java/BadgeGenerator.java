
import static Utility.Utils.calculateAssertionHash;
import static Utility.Utils.config;
import static Utility.Utils.generateSalt;
import static Utility.Utils.hashRecipientEmail;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 */
public class BadgeGenerator {

    private static final String TEST_BADGE_CLASS_URL = "https://openbagetest.s3.eu-central-1.amazonaws.com/reader-badge.json";

    private static final String ASSERTION_URL = "https://openbagetest.s3.eu-central-1.amazonaws.com/assertion-1759216024.json";

    private static final String IMAGE_URL = "C:/Users/Salvatore/Desktop/logo.png";

    private static final String ACCESS_KEY = config.getString("access_key");
    private static final String SECRET_KEY = config.getString("secret_key");
    private static final String AWS_REGION = config.getString("aws_region");
    private static final String BUCKET_NAME = config.getString("bucket_name");

    private static class Recipient {

        String type = "email";
        boolean hashed = true;
        String identity;
        String salt;

        Recipient(String identity, String salt) {
            this.identity = identity;
            this.salt = salt;

        }
    }

    private static class Verification {

        String type = "hosted";
        String url;

        Verification(String url) {
            this.url = url;
        }
    }

    private static class Assertion {

        @com.google.gson.annotations.SerializedName("@context")
        String context = "https://w3id.org/openbadges/v2";
        String id;
        String type = "Assertion";
        Recipient recipient;
        String issuedOn;
        String expires;
        String badge;
        Verification verification;
    }

    /**
     * Salva il contenuto in un file sul Desktop dell'utente.
     */
    private static void saveFile(String content, String fileName) throws IOException {
        String userHome = System.getProperty("user.home");
        Path desktopPath = Paths.get(userHome, "Desktop", fileName);

        try (FileWriter writer = new FileWriter(desktopPath.toFile())) {
            writer.write(content);
            System.out.println("✅ Salvataggio completato: " + desktopPath.toString());
        } catch (IOException e) {
            System.err.println("❌ Errore durante il salvataggio: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Genera l'Assertion Open Badges in formato JSON, calcola l'hash e la
     * salva. La parte di "notarizzazione" su Hyperledger è stata rimossa.
     *
     * @param recipientEmail L'email del ricevente del badge.
     * @param badgeClassUrl L'URL della Badge Class.
     * @return L'URL dell'Assertion generata.
     * @throws IOException Se si verifica un errore durante il salvataggio del
     * file.
     */
    public static String generateAndSaveAssertion(String recipientEmail, String badgeClassUrl) throws IOException {
        String salt = generateSalt(16);
        String hashedIdentity = hashRecipientEmail(recipientEmail, salt);
        Assertion assertion = new Assertion();
        assertion.id = ASSERTION_URL;
        assertion.recipient = new Recipient(hashedIdentity, salt);

        assertion.issuedOn = Instant.now().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
        Instant futureInstant = Instant.now().atOffset(ZoneOffset.UTC).plus(5, ChronoUnit.YEARS).toInstant();
        assertion.expires = futureInstant.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();

        assertion.badge = badgeClassUrl;
        assertion.verification = new Verification(ASSERTION_URL);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(assertion);

        String assertionHash = calculateAssertionHash(json);
        System.out.println("🔗 Hash SHA-256 dell'Assertion: " + assertionHash);

        saveFile(json, "assertion_reader_test.json");

        return ASSERTION_URL;
    }

    /**
     * Genera e salva un file SVG "Baked" con i metadati dell'Assertion
     * incorporati.
     *
     * @param assertionUrl L'URL dell'Assertion.
     * @throws IOException Se si verifica un errore durante il salvataggio del
     * file.
     */
    public static void generateAndSaveBakedSvg(String assertionUrl) throws IOException {
        String svgContent = String.format(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:openbadges=\"https://openbadges.org/\""
                + " xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"200\" height=\"200\">\n"
                + "  <circle cx=\"100\" cy=\"100\" r=\"90\" fill=\"#336699\"/>\n"
                + "  <image x=\"70\" y=\"70\" width=\"60\" height=\"60\" xlink:href=\"%s\"/>\n"
                + "  <text x=\"100\" y=\"150\" font-size=\"20\" text-anchor=\"middle\" fill=\"white\">TEST BADGE</text>\n\n"
                + "  <openbadges:assertion verify=\"%s\"></openbadges:assertion>\n"
                + "</svg>",
                IMAGE_URL,
                assertionUrl
        );

        saveFile(svgContent, "open-badge-baked.svg");
    }

    public static void main(String[] args) {
        AwsCredentials credentials = AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY);
        S3Client s3Client = S3Client
                .builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();

        //UploadFile(s3Client);
        deleteObjectsInBucket(s3Client);
        listObjectsInBucket(s3Client);
        //Download(s3Client);

        String recipientEmail = "salvatoremancuso703@gmail.com";

        System.out.println("Inizio generazione Open Badge per: " + recipientEmail);

        try {
            String assertionUrl = generateAndSaveAssertion(recipientEmail, TEST_BADGE_CLASS_URL);
            generateAndSaveBakedSvg(assertionUrl);
            System.out.println("Badge generato con successo.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void UploadFile(S3Client s3Client) {
//        File file = new File("C:\\Users\\Salvatore\\Documents\\NetBeansProjects\\mavenproject5\\src\\main\\webapp"
//                + "\\assets\\html\\reader-badge-criteria.html");
        File file = new File("C:\\Users\\Salvatore\\Documents\\NetBeansProjects\\mavenproject5\\src\\main\\webapp"
                + "\\assets\\js\\assertion.json");
//        File file = new File("C:\\Users\\Salvatore\\Documents\\NetBeansProjects\\mavenproject5\\src\\main\\webapp"
//                + "\\assets\\js\\issuer-organization.json");
//        File file = new File("C:\\Users\\Salvatore\\Documents\\NetBeansProjects\\mavenproject5\\src\\main\\webapp"
//                + "\\assets\\js\\reader-badge.json");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("company", "Baeldung");
        metadata.put("environment", "development");

        s3Client.putObject(request
                -> request
                        .bucket(BUCKET_NAME)
                        .key(file.getName())
                        .metadata(metadata)
                        .ifNoneMatch("*"),
                file.toPath());
    }

    public static void Download(S3Client s3Client) {
        String key = "reader-badge.json";
        Path downloadPath = Paths.get("C:\\Users\\Salvatore\\Desktop\\reader-badge.json");
        s3Client.getObject(request
                -> request
                        .bucket(BUCKET_NAME)
                        .key(key),
                ResponseTransformer.toFile(downloadPath));

    }

    public static void listObjectsInBucket(S3Client s3Client) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(BUCKET_NAME)
                .build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);

        List<S3Object> contents = listObjectsV2Response.contents();

        System.out.println("Number of objects in the bucket: " + contents.stream().count());
        contents.stream().forEach(System.out::println);

        s3Client.close();
    }

    public static void deleteObjectsInBucket(S3Client s3Client) {
        List<String> objectKeys = List.of("reader-badge.json","reader-badge-32f23c960.json" , "reader-badge-43d513c16.json", "issuer-organization-67796795c.json","issuer-organization-00cef4dce.json", "assertion.json", "assertion-833aa9bb6.json","reader-badge-criteria.html");
        List<ObjectIdentifier> objectsToDelete = objectKeys
                .stream()
                .map(key -> ObjectIdentifier
                .builder()
                .key(key)
                .build())
                .toList();

        s3Client.deleteObjects(request
                -> request
                        .bucket(BUCKET_NAME)
                        .delete(deleteRequest
                                -> deleteRequest
                                .objects(objectsToDelete)));
    }
}
