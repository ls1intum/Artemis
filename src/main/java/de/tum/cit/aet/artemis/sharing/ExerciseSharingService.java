package de.tum.cit.aet.artemis.sharing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.client.utils.URIBuilder;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.glassfish.jersey.client.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exercise.service.sharing.SharingConnectorService;
import de.tum.cit.aet.artemis.exercise.service.sharing.SharingException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;

/**
 * service for sharing exercises via the sharing platform.
 */
@Service
@Profile("sharing")
public class ExerciseSharingService {

    /**
     * the logger
     */
    private final Logger log = LoggerFactory.getLogger(ExerciseSharingService.class);

    /**
     * the repo download path
     */
    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    /**
     * the artemis server url
     */
    @Value("${server.url}")
    protected String artemisServerUrl;

    /**
     * the profile service
     */
    protected ProfileService profileService;

    /**
     * the programming Exercise Export Service
     */
    private final ProgrammingExerciseExportService programmingExerciseExportService;

    /**
     * the sharing connector service
     */
    private final SharingConnectorService sharingConnectorService;

    /**
     * the programming exercise repository
     */
    private final ProgrammingExerciseRepository programmingExerciseRepository;

    /**
     * constructor for spring
     *
     * @param programmingExerciseExportService programming exercise export service
     * @param sharingConnectorService          sharing connector service
     * @param programmingExerciseRepository    programming exercise repository
     * @param profileService                   profile service
     */
    public ExerciseSharingService(ProgrammingExerciseExportService programmingExerciseExportService, SharingConnectorService sharingConnectorService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProfileService profileService) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.sharingConnectorService = sharingConnectorService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.profileService = profileService;
    }

    /**
     * loads the basket info from the sharing platform
     *
     * @param basketToken the basket token
     * @param apiBaseUrl  the url
     * @return an optional shopping basket
     */
    public Optional<ShoppingBasket> getBasketInfo(String basketToken, String apiBaseUrl) {
        ClientConfig restClientConfig = new ClientConfig();
        restClientConfig.register(ShoppingBasket.class);
        try (Client client = ClientBuilder.newClient(restClientConfig)) {
            WebTarget target = client.target(correctLocalHostInDocker(apiBaseUrl).concat("/basket/").concat(basketToken));
            String response = target.request().accept(MediaType.APPLICATION_JSON).get(String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.registerModule(new JavaTimeModule());

            ShoppingBasket shoppingBasket = objectMapper.readValue(response, ShoppingBasket.class);
            return Optional.ofNullable(shoppingBasket);

        }
        catch (ResponseProcessingException rpe) {
            log.warn("Unrecognized property when importing exercise from Sharing", rpe);
            return Optional.empty();
        }
        catch (JsonProcessingException e) {
            log.error("Cannot parse properties: ", e);
        }
        return Optional.empty();
    }

    /**
     * return an exercise from the basket (as zip file)
     *
     * @param sharingInfo  the sharing info
     * @param itemPosition the item position
     * @return an zip stream
     * @throws SharingException if exercise cannot be loaded
     */
    public Optional<SharingMultipartZipFile> getBasketItem(SharingInfoDTO sharingInfo, int itemPosition) throws SharingException {
        ClientConfig restClientConfig = new ClientConfig();
        restClientConfig.register(ShoppingBasket.class);

        try (Client client = ClientBuilder.newClient(restClientConfig)) {
            WebTarget target = client.target(correctLocalHostInDocker(sharingInfo.getApiBaseURL()) + "/basket/" + sharingInfo.getBasketToken() + "/repository/" + itemPosition)
                    .queryParam("format", "artemis");
            InputStream zipInput = target.request().accept(MediaType.APPLICATION_OCTET_STREAM).get(InputStream.class);

            if (zipInput == null) {
                throw new SharingException("Could not retrieve basket item");
            }

            SharingMultipartZipFile zipFileItem = new SharingMultipartZipFile(getBasketFileName(sharingInfo.getBasketToken(), itemPosition), zipInput);
            return Optional.of(zipFileItem);
        }
        catch (WebApplicationException wae) {
            throw new SharingException("Could not retrieve basket item");
        }
    }

    /**
     * simple loading cache for file with 1 hour timeout.
     */
    private final LoadingCache<SharingInfoDTO, File> repositoryCache = CacheBuilder.newBuilder().maximumSize(10000).expireAfterAccess(1, TimeUnit.HOURS)
            .removalListener(notification -> ((File) notification.getValue()).delete()).build(new CacheLoader<>() {

                public File load(SharingInfoDTO sharingInfo) {
                    try {
                        Optional<SharingMultipartZipFile> basketItemO = getBasketItem(sharingInfo, sharingInfo.getExercisePosition());
                        return basketItemO.map(basketItem -> {
                            try {
                                File fTemp = File.createTempFile("SharingBasket", ".zip");

                                StreamUtils.copy(basketItem.getInputStream(), new FileOutputStream(fTemp));
                                return fTemp;
                            }
                            catch (IOException e) {
                                log.warn("Cannot load sharing Info", e);
                                return null;
                            }
                        }).orElse(null);
                    }
                    catch (SharingException e) {
                        log.warn("Cannot load sharing Info", e);
                        return null;
                    }

                }
            });

    public SharingMultipartZipFile getCachedBasketItem(SharingInfoDTO sharingInfo) throws IOException, SharingException {
        int itemPosition = sharingInfo.getExercisePosition();
        File f = repositoryCache.getIfPresent(sharingInfo);
        if (f != null) {
            try {
                return new SharingMultipartZipFile(getBasketFileName(sharingInfo.getBasketToken(), itemPosition), new FileInputStream(f));
            }
            catch (FileNotFoundException e) {
                log.warn("Cannot find cached file for {}:{} at {}", sharingInfo.getBasketToken(), itemPosition, f.getAbsoluteFile(), e);
            }
        }
        // second try (first try in cache);
        Optional<SharingMultipartZipFile> basketItem = getBasketItem(sharingInfo, itemPosition);
        return basketItem.orElse(null);
    }

    /**
     * this is just a weak implementation for local testing (within a docker). It replaces an url to localhost with
     * host.docker.internal.
     *
     * @param url the url to be corrected
     * @return an url, that points to host.docker.internal if previously directed to localhost.
     */
    private String correctLocalHostInDocker(String url) {
        if (url.contains("//localhost") && profileService.isProfileActive("docker")) {
            return url.replace("//localhost", "//host.docker.internal");
        }
        return url;
    }

    /**
     * Retrieves the Problem-Statement file from a Sharing basket
     *
     * @param sharingInfo of the basket to extract the problem statement from
     * @return The content of the Problem-Statement file
     */
    public String getProblemStatementFromBasket(SharingInfoDTO sharingInfo) {
        Pattern pattern = Pattern.compile("^Problem-Statement|^exercise.md$", Pattern.CASE_INSENSITIVE);

        try {
            String problemStatement = this.getEntryFromBasket(pattern, sharingInfo);
            // The Basket comes from the sharing platform, however the problem statement comes from a git repository.
            // A malicious user manipulate the problem statement, and insert malicious code.
            return Objects.requireNonNullElse(org.springframework.web.util.HtmlUtils.htmlEscape(problemStatement), "No Problem Statement found!");
        }
        catch (Exception e) {
            throw new NotFoundException("Could not retrieve problem statement from imported exercise");
        }
    }

    /**
     * TODO: check usage
     * Retrieves the Exercise-Details file from a Sharing basket
     *
     * @param sharingInfo of the basket to extract the problem statement from
     * @return The content of the Exercise-Details file
     */
    public String getExerciseDetailsFromBasket(SharingInfoDTO sharingInfo) {
        Pattern pattern = Pattern.compile("^Exercise-Details", Pattern.CASE_INSENSITIVE);

        try {
            String problemStatement = this.getEntryFromBasket(pattern, sharingInfo);
            return Objects.requireNonNullElse(problemStatement, "No Problem Statement found!");
        }
        catch (Exception e) {
            throw new NotFoundException("Could not retrieve exercise details from imported exercise");
        }
    }

    /**
     * Retrieves an entry from a given Sharing basket, basing on the given RegEx.
     * If nothing is found, null is returned.
     *
     * @param matchingPattern RegEx matching the entry to return.
     * @param sharingInfo     of the basket to retrieve the entry from
     * @return The content of the entry, or null if not found.
     * @throws IOException if a reading error occurs
     */
    public String getEntryFromBasket(Pattern matchingPattern, SharingInfoDTO sharingInfo) throws IOException {
        InputStream repositoryStream;
        try {
            repositoryStream = this.getCachedBasketItem(sharingInfo).getInputStream();
        }
        catch (IOException | SharingException e) {
            log.error("Cannot read input Template for {}", sharingInfo.getBasketToken(), e);
            return null;
        }

        ZipInputStream zippedRepositoryStream = new ZipInputStream(repositoryStream);

        ZipEntry entry;
        while ((entry = zippedRepositoryStream.getNextEntry()) != null) {
            Matcher matcher = matchingPattern.matcher(entry.getName());
            if (matcher.find()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[102400];
                int bytesRead;
                while ((bytesRead = zippedRepositoryStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                String entryContent = baos.toString(StandardCharsets.UTF_8);
                baos.close();
                zippedRepositoryStream.closeEntry();
                return entryContent;
            }
            zippedRepositoryStream.closeEntry();
        }
        return null; // Not found
    }

    /**
     * Creates Zip file for exercise and returns a URL pointing to Sharing
     * with a callback URL addressing the generated Zip file for download
     *
     * @param exerciseId the ID of the exercise to export
     * @return URL to sharing with a callback URL to the generated zip file
     */
    public URL exportExerciseToSharing(Long exerciseId) throws SharingException {
        if (!sharingConnectorService.isSharingApiBaseUrlPresent()) {
            throw new SharingException("No Sharing ApiBaseUrl provided");
        }
        try {
            Optional<ProgrammingExercise> exercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigTeamConfigAndBuildConfigById(exerciseId);

            if (exercise.isEmpty()) {
                throw new SharingException("Could not find exercise to export");
            }

            List<String> exportErrors = new ArrayList<>();
            Path zipFilePath = programmingExerciseExportService.exportProgrammingExerciseForDownload(exercise.get(), exportErrors);

            if (!exportErrors.isEmpty()) {
                String errorMessage = String.join(", ", exportErrors);
                throw new SharingException("Could not generate Zip file to export: " + errorMessage);
            }

            // remove the 'repoDownloadClonePath' part and 'zip' extension
            String token = Path.of(repoDownloadClonePath).relativize(zipFilePath).toString().replace(".zip", "");
            String tokenInB64 = Base64.getEncoder().encodeToString(token.getBytes()).replaceAll("=+$", "");
            String tokenIntegrity = createHMAC(tokenInB64);

            URL apiBaseUrl = sharingConnectorService.getSharingApiBaseUrlOrNull();
            String sharingImportEndPoint = "/exercise/import";
            URIBuilder callBackBuilder = new URIBuilder(artemisServerUrl + "/api/sharing/export/" + tokenInB64);
            callBackBuilder.addParameter("sec", tokenIntegrity);
            URIBuilder builder = new URIBuilder();
            builder.setScheme(apiBaseUrl.getProtocol()).setHost(apiBaseUrl.getHost()).setPath(apiBaseUrl.getPath().concat(sharingImportEndPoint)).setPort(apiBaseUrl.getPort())
                    .addParameter("exerciseUrl", callBackBuilder.build().toString());

            return builder.build().toURL();
        }
        catch (URISyntaxException e) {
            log.error("An error occurred during URL creation: " + e.getMessage());
            return null;
        }
        catch (IOException e) {
            log.error("Could not generate Zip file for export: " + e.getMessage());
            return null;
        }
    }

    /**
     * just to secure token for integrity
     *
     * @param base64token the token (already base64 encoded
     * @return returns HMAC-Hash
     */
    private String createHMAC(String base64token) {
        // Definiere die HMAC-Methode (z. B. HmacSHA256)
        String algorithm = "HmacSHA256";
        String psk = sharingConnectorService.getSharingApiKeyOrNull();

        // Konvertiere den Pre-shared Key in ein Byte-Array
        SecretKeySpec secretKeySpec = new SecretKeySpec(psk.getBytes(), algorithm);

        try {
            // Initialisiere den Mac mit dem Algorithmus und dem Schlüssel
            Mac mac = Mac.getInstance(algorithm);
            mac.init(secretKeySpec);
            // Berechne das HMAC
            byte[] hmacBytes = mac.doFinal(base64token.getBytes());

            // Konvertiere das Ergebnis in Base64 für einfache Speicherung
            return Base64.getEncoder().encodeToString(hmacBytes);
        }
        catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return Base64.getEncoder().encodeToString(new byte[] {});
        }

    }

    /**
     * checks the integrity of the base64token
     *
     * @param base64token the base64token
     * @param sec         the hmac hash
     * @return true, iff hash is correct
     */
    public boolean validate(String base64token, String sec) {
        String computedHMAC = createHMAC(base64token);
        return computedHMAC.equals(sec);
    }

    /**
     * loads the stored file from file system (via the b64 token).
     *
     * @param b64Token the base64 encoded token
     * @return the file referenced by the token
     */
    public File getExportedExerciseByToken(String b64Token) {
        if (!isValidToken(b64Token)) {
            log.warn("Invalid token received: {}", b64Token);
            return null;
        }
        String decodedToken = new String(Base64.getDecoder().decode(b64Token));
        Path parent = Paths.get(repoDownloadClonePath, decodedToken + ".zip");
        File exportedExercise = parent.toFile();
        if (exportedExercise.exists()) {
            return exportedExercise;
        }
        else {
            return null;
        }
    }

    private boolean isValidToken(String token) {
        // Implement validation logic, e.g., check for illegal characters or patterns
        return token.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Returns a formatted filename for a basket file.
     *
     * @param basketToken  of the retrieved file
     * @param itemPosition of the retrieved file
     */
    private String getBasketFileName(String basketToken, int itemPosition) {
        return "sharingBasket" + basketToken + "-" + itemPosition + ".zip";
    }

}
