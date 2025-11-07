package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;

/**
 * Service for importing/exporting programming exercises between Artemis and the Sharing Platform.
 * <p>
 * Handles basket lookup and download, secure export link generation, ZIP streaming/parsing,
 * and lightweight caching for repository downloads.
 * </p>
 */
@Service
@Conditional(SharingEnabled.class)
@Lazy
public class ExerciseSharingService {

    /**
     * Upper bound for accepted export token length (guards against abuse).
     * <p>
     * Also used in tests.
     * </p>
     */
    static final int MAX_EXPORT_TOKEN_LENGTH = 300;

    private static final Logger log = LoggerFactory.getLogger(ExerciseSharingService.class);

    private static final int COPY_BUFFER_SIZE = 102400;

    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    @Value("${server.url}")
    private String artemisServerUrl;

    private final RestTemplate restTemplate;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final SharingConnectorService sharingConnectorService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    /**
     * Local {@link ObjectMapper} instance that ignores unknown JSON fields.
     * <p>
     * Allows the Sharing Platform to evolve its metadata format without breaking imports.
     * </p>
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExerciseSharingService(ProgrammingExerciseExportService programmingExerciseExportService, SharingConnectorService sharingConnectorService,
            ProgrammingExerciseRepository programmingExerciseRepository, @Qualifier("sharingRestTemplate") RestTemplate restTemplate) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.sharingConnectorService = sharingConnectorService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.restTemplate = restTemplate;

        // Configure ObjectMapper to ignore unknown properties
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Loads the shopping basket info from the sharing platform with the given basket token.
     * The shopping basket contains info about the requesting user, a list of exercises (currently only one is supported), and a token
     * that allows the retrieval of the contained exercises.
     * For details see {@link ShoppingBasket}.
     *
     * @param basketToken opaque basket identifier
     * @param apiBaseUrl  base URL of the Sharing Platform API that serves the basket
     * @return {@link Optional#of(Object)} with the basket, or {@link Optional#empty()} if not found / invalid / request failed
     */
    public Optional<ShoppingBasket> getBasketInfo(String basketToken, String apiBaseUrl) {
        if (isInvalidToken(basketToken)) {
            return Optional.empty();
        }
        String basketRequestURL = apiBaseUrl.concat("/basket/").concat(basketToken);
        try {
            ShoppingBasket shoppingBasket = restTemplate.getForObject(basketRequestURL, ShoppingBasket.class);
            return Optional.ofNullable(shoppingBasket);
        }
        catch (HttpClientErrorException.NotFound nf) {
            log.warn("Basket {} not found", basketToken, nf);
            return Optional.empty();
        }
        catch (RestClientException rpe) {
            log.warn("Failed to retrieve basket from sharing platform", rpe);
            return Optional.empty();
        }
    }

    /**
     * Retrieves one basket item as a ZIP stream
     *
     * @param sharingInfo basket reference and API endpoint info
     * @return a handle to the ZIP content ({@link SharingMultipartZipFile}); caller must close the stream
     * @throws SharingException if retrieval fails
     */
    public SharingMultipartZipFile getCachedBasketItem(SharingInfoDTO sharingInfo) throws SharingException {
        int itemPosition = sharingInfo.exercisePosition();
        try {
            String url = sharingInfo.apiBaseURL() + "/basket/{basketToken}/repository/" + itemPosition + "?format={format}";
            var resource = restTemplate.getForObject(url, org.springframework.core.io.Resource.class, Map.of("basketToken", sharingInfo.basketToken(), "format", "artemis"));
            if (resource == null) {
                throw new SharingException("Could not retrieve basket item resource");
            }

            // Do NOT close here; caller closes via SharingMultipartZipFile
            InputStream in = resource.getInputStream();
            return new SharingMultipartZipFile(getBasketFileName(sharingInfo.basketToken(), itemPosition), in);
        }
        catch (Exception e) {
            log.warn("Exception during shared exercise retrieval", e);
            throw new SharingException("Could not retrieve basket item", e);
        }
    }

    /**
     * Parses the {@code Exercise-Details*} entry from a basket ZIP and returns it as a {@link ProgrammingExercise}.
     * <p>
     * Unknown JSON properties are ignored; the returned entity has {@code id = null}.
     * </p>
     *
     * @param sharingInfo basket reference
     * @return the parsed exercise details
     * @throws EntityNotFoundException if the details entry is missing or cannot be parsed
     */
    public ProgrammingExercise getExerciseDetailsFromBasket(SharingInfoDTO sharingInfo) {
        Pattern pattern = Pattern.compile("^Exercise-Details", Pattern.CASE_INSENSITIVE);

        try {
            String exerciseDetailString = getEntryFromBasket(pattern, sharingInfo)
                    .orElseThrow(() -> new EntityNotFoundException("Could not retrieve exercise details from imported exercise"));
            ProgrammingExercise exerciseDetails = objectMapper.readValue(exerciseDetailString, ProgrammingExercise.class);
            exerciseDetails.setId(null);
            return exerciseDetails;
        }
        catch (Exception e) {
            String errorMessage = e.getMessage();
            throw new EntityNotFoundException("Could not retrieve exercise details from imported exercise: " + errorMessage);
        }
    }

    /**
     * Extracts the first ZIP entry whose name matches {@code matchingPattern} and returns its content as UTF-8 text.
     *
     * @param matchingPattern regex to select the ZIP entry
     * @param sharingInfo     basket reference
     * @return {@link Optional String} with the entry content, or {@link Optional#empty()} if not found
     * @throws IOException if reading the ZIP stream fails
     */
    public Optional<String> getEntryFromBasket(Pattern matchingPattern, SharingInfoDTO sharingInfo) throws IOException {
        try (SharingMultipartZipFile zipFile = this.getCachedBasketItem(sharingInfo); ZipInputStream zippedRepositoryStream = new ZipInputStream(zipFile.getInputStream())) {

            ZipEntry entry;
            while ((entry = zippedRepositoryStream.getNextEntry()) != null) {
                Matcher matcher = matchingPattern.matcher(entry.getName());
                if (matcher.find()) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[COPY_BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = zippedRepositoryStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    String entryContent = outputStream.toString(StandardCharsets.UTF_8);
                    zippedRepositoryStream.closeEntry();
                    return Optional.of(entryContent);
                }
                zippedRepositoryStream.closeEntry();
            }
            return Optional.empty(); // Not found
        }
        catch (SharingException e) {
            log.error("Cannot read input Template for {}", sharingInfo.basketToken(), e);
            return Optional.empty();
        }

    }

    /**
     * Exports an exercise and returns a URL on the Sharing Platform that includes a callback to Artemis.
     * <p>
     * Steps:
     * </p>
     * <ol>
     * <li>Export exercise to a ZIP file.</li>
     * <li>Derive a relative path token, encode it URL-safe Base64 (no padding).</li>
     * <li>Compute an HMAC to protect the token from tampering.</li>
     * <li>Build a Sharing URL with {@code exerciseUrl=<Artemis callback>}.</li>
     * </ol>
     *
     * <h3>Security</h3>
     * The download callback uses {@code token} + {@code sec} (HMAC-SHA256 over the token with the shared API key).
     *
     * @param exerciseId Artemis exercise id to export
     * @return Sharing Platform URL that initiates the import flow
     * @throws SharingException if the export fails, the path is invalid, or the Sharing API base URL is missing
     */
    public URL exportExerciseToSharing(Long exerciseId) throws SharingException {
        if (!sharingConnectorService.isSharingApiBaseUrlPresent()) {
            throw new SharingException("No Sharing ApiBaseUrl provided");
        }
        try {
            ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithPlagiarismDetectionConfigTeamConfigBuildConfigAndGradingCriteriaElseThrow(exerciseId);

            List<String> exportErrors = new ArrayList<>();
            Path zipFilePath = programmingExerciseExportService.exportProgrammingExerciseForDownload(exercise, exportErrors);

            if (!exportErrors.isEmpty()) {
                String errorMessage = String.join(", ", exportErrors);
                throw new SharingException("Could not generate Zip file to export: " + errorMessage);
            }

            // remove the 'repoDownloadClonePath' part and 'zip' extension
            Path baseDir = Path.of(repoDownloadClonePath).toAbsolutePath().normalize();
            Path zipAbs = zipFilePath.toAbsolutePath().normalize();
            if (!zipAbs.startsWith(baseDir)) {
                throw new SharingException("Export path is outside of configured clone directory");
            }
            String token = baseDir.relativize(zipAbs).toString().replace(".zip", "");            // We encode the zip-file path as a Base64-Token
            // to simplify the token, we strip trailing "=".
            // We cannot guarantee that the sharing platform connects to the same jvm instance, we have to find the file with the token on the (shared) file system.
            String tokenInB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
            String tokenIntegrity = createHMAC(tokenInB64);

            URL apiBaseUrl = sharingConnectorService.getSharingApiBaseUrlOrNull();
            URIBuilder callBackBuilder = new URIBuilder(artemisServerUrl + "/api/programming/sharing/export/" + tokenInB64);
            callBackBuilder.addParameter("sec", tokenIntegrity);
            URIBuilder builder = new URIBuilder();

            var baseSegments = Arrays.stream(apiBaseUrl.getPath().split("/")).filter(s -> !s.isBlank()).toArray(String[]::new);
            builder.setScheme(apiBaseUrl.getProtocol()).setHost(apiBaseUrl.getHost()).setPort(apiBaseUrl.getPort())
                    .setPathSegments(Stream.concat(Arrays.stream(baseSegments), Stream.of("exercise", "import")).toArray(String[]::new))
                    .addParameter("exerciseUrl", callBackBuilder.build().toString());

            return builder.build().toURL();
        }
        catch (URISyntaxException | IOException | EntityNotFoundException e) {
            String msg = "Could not generate Zip file for export: " + e.getMessage();
            log.error(msg, e);
            throw new SharingException(msg, e);
        }
    }

    /**
     * Computes a URL-safe Base64 HMAC (SHA-256) over the already Base64-encoded token.
     *
     * @param base64token token (URL-safe Base64, without padding)
     * @return HMAC digest encoded as URL-safe Base64 (no padding)
     * @throws IllegalStateException if the API key is missing or HMAC calculation fails
     */
    private String createHMAC(String base64token) {
        // selects HMAC-method (here HmacSHA256)
        String algorithm = "HmacSHA256";
        String psk = sharingConnectorService.getSharingApiKey();
        if (psk == null) {
            throw new IllegalStateException("Sharing API key is not configured");
        }
        SecretKeySpec secretKeySpec = new SecretKeySpec(psk.getBytes(StandardCharsets.UTF_8), algorithm);

        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(base64token.getBytes(StandardCharsets.UTF_8));

            // URL-safe Base64 avoids '+' and '=' in query params
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        }
        catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Cannot calculate MAC", e);
            throw new IllegalStateException("Failed to generate HMAC", e);
        }

    }

    /**
     * Validates the integrity of a token using its HMAC.
     * <p>
     * Accepts {@code sec} with space-for-plus substitutions (defensive for lenient URL decoders).
     * </p>
     *
     * @param base64token URL-safe Base64 token (no padding)
     * @param sec         expected HMAC (URL-safe Base64)
     * @return {@code true} iff the computed HMAC matches {@code sec}
     */
    public boolean validate(String base64token, String sec) {
        // we have to take care that the base64 encoded token may contain a + sign, which may be converted to a space
        // not sure whether this may be an effect of our testing environment
        if (isInvalidToken(base64token) || StringUtils.isEmpty(sec)) {
            return false;
        }
        String sanitizedSec = sec.replace(' ', '+');
        String computedHMAC = createHMAC(base64token);
        return MessageDigest.isEqual(computedHMAC.getBytes(StandardCharsets.UTF_8), sanitizedSec.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resolves a previously exported ZIP on disk based on the Base64 token.
     * <p>
     * Integrity is enforced separately via {@link #validate(String, String)}.
     * </p>
     *
     * @param b64Token URL-safe Base64 token (no padding)
     * @return path to the ZIP if it exists; otherwise {@link Optional#empty()}
     */
    public Optional<Path> getExportedExerciseByToken(String b64Token) {
        if (isInvalidToken(b64Token)) {
            log.warn("Invalid token received: {}", b64Token);
            return Optional.empty();
        }

        String decodedToken = new String(Base64.getUrlDecoder().decode(b64Token), StandardCharsets.UTF_8);
        Path zipPath = Path.of(repoDownloadClonePath, decodedToken + ".zip");
        if (!Files.isRegularFile(zipPath)) {
            return Optional.empty();
        }
        // Integrity is ensured via HMAC validation; decodedToken is a safe relative path segment
        return Optional.of(zipPath);
    }

    /**
     * @return {@code true} if the token is blank, too long, or contains characters other than {@code [a-zA-Z0-9_-]}
     */
    private boolean isInvalidToken(String token) {
        return StringUtils.isBlank(token) || token.length() >= MAX_EXPORT_TOKEN_LENGTH || !token.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Builds a predictable temp filename for a cached basket ZIP.
     *
     * @param basketToken  basket identifier
     * @param itemPosition item index inside the basket
     * @return sanitized filename for the cached ZIP
     */
    private String getBasketFileName(String basketToken, int itemPosition) {
        String safeToken = basketToken.replaceAll("[^a-zA-Z0-9_-]", "");
        return "sharingBasket" + safeToken + "-" + itemPosition + ".zip";
    }

}
