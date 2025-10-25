package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.NotFoundException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URIBuilder;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;

/**
 * service for sharing exercises via the sharing platform.
 */
@Service
@Conditional(SharingEnabled.class)
@Lazy
public class ExerciseSharingService {

    /**
     * just a limit to the maximal accepted token length.
     *
     */
    // also needed in tests.
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
     * just a local instance of an object mapper which ignores unknown properties, in case the Sharing Platform extends its metadata format.
     */
    private final ObjectMapper objectMapperAllowingUnknownProperties;

    public ExerciseSharingService(ProgrammingExerciseExportService programmingExerciseExportService, SharingConnectorService sharingConnectorService,
            ProgrammingExerciseRepository programmingExerciseRepository, @Qualifier("sharingRestTemplate") RestTemplate restTemplate) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.sharingConnectorService = sharingConnectorService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.restTemplate = restTemplate;

        this.objectMapperAllowingUnknownProperties = new ObjectMapper();
        objectMapperAllowingUnknownProperties.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapperAllowingUnknownProperties.findAndRegisterModules();
    }

    /**
     * Loads the shopping basket info from the sharing platform with the given basket token.
     * The shopping basket contains info about the requesting user, a list of exercises (currently only one is supported), and a token
     * that allows the retrieval of the contained exercises.
     * For details see {@link ShoppingBasket}.
     *
     * @param basketToken the basket token
     * @param apiBaseUrl  the api base url to request the basket content from
     * @return an optional shopping basket
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
     * returns a single exercise from the basket (as a zip file)
     *
     * @param sharingInfo  the sharing info
     * @param itemPosition the item position
     * @return the exercise as a zip stream
     * @throws SharingException if exercise cannot be loaded
     */
    public SharingMultipartZipFile getBasketItem(SharingInfoDTO sharingInfo, int itemPosition) throws SharingException {
        try {
            Path cachedZipFile = repositoryCache.get(Pair.of(sharingInfo, itemPosition));
            // Ensure proper resource management - SharingMultipartZipFile should handle stream closing
            return new SharingMultipartZipFile(getBasketFileName(sharingInfo.basketToken(), itemPosition), Files.newInputStream(cachedZipFile));
        }
        catch (IOException | ExecutionException wae) {
            log.warn("Exception during shared exercise retrieval", wae);
            throw new SharingException("Could not retrieve basket item", wae);
        }
    }

    /**
     * simple loading cache for paths to zip-files with 1-hour timeout.
     */
    private final LoadingCache<@NotNull Pair<SharingInfoDTO, Integer>, @NotNull Path> repositoryCache = CacheBuilder.newBuilder().maximumSize(100)
            .expireAfterAccess(1, TimeUnit.HOURS).removalListener(notification -> {
                Path outdatedBasketZipfile = (Path) notification.getValue();
                try {
                    Files.deleteIfExists(outdatedBasketZipfile);
                }
                catch (IOException e) {
                    log.info("Cannot delete {}", outdatedBasketZipfile, e);
                }
            }).build(new CacheLoader<>() {

                @Override
                public Path load(Pair<SharingInfoDTO, Integer> sharingInfoAndPos) throws SharingException {
                    SharingInfoDTO sharingInfo = sharingInfoAndPos.getLeft();
                    int itemPosition = sharingInfoAndPos.getRight();
                    try {
                        String exercisesZipUrl = sharingInfo.apiBaseURL() + "/basket/{basketToken}/repository/" + itemPosition + "?format={format}";
                        Resource zipInputResource = restTemplate.getForObject(exercisesZipUrl, Resource.class,
                                Map.of("basketToken", sharingInfo.basketToken(), "format", "artemis"));
                        if (zipInputResource == null) {
                            throw new SharingException("Could not retrieve basket item resource");
                        }
                        Path basketFilePath = Files.createTempFile("basketStore", ".zip");
                        try (InputStream zipInput = zipInputResource.getInputStream(); OutputStream outputStream = Files.newOutputStream(basketFilePath)) {
                            FileCopyUtils.copy(zipInput, outputStream);
                        }

                        return basketFilePath;
                    }
                    catch (IOException e) {
                        log.warn("Cannot load sharing info for basket {} item {} from {}.", sharingInfo.basketToken(), itemPosition, sharingInfo.apiBaseURL(), e);
                        throw new SharingException("Cannot load sharing Info", e);
                    }

                }
            });

    /**
     * Access to the repository cache. For test purpose only!
     *
     * @return repository cache
     */
    LoadingCache<@NotNull Pair<SharingInfoDTO, Integer>, @NotNull Path> getRepositoryCache() {
        return repositoryCache;
    }

    public SharingMultipartZipFile getCachedBasketItem(SharingInfoDTO sharingInfo) throws SharingException {
        int itemPosition = sharingInfo.exercisePosition();
        return getBasketItem(sharingInfo, itemPosition);
    }

    /**
     * Retrieves the Exercise-Details file from a Sharing basket, parses it, and returns it as a ProgrammingExercise-Object.
     *
     * @param sharingInfo of the basket to extract the problem statement from
     * @return The content of the Exercise-Details file
     */
    public ProgrammingExercise getExerciseDetailsFromBasket(SharingInfoDTO sharingInfo) {
        Pattern pattern = Pattern.compile("^Exercise-Details", Pattern.CASE_INSENSITIVE);

        try {
            String exerciseDetailString = getEntryFromBasket(pattern, sharingInfo)
                    .orElseThrow(() -> new NotFoundException("Could not retrieve exercise details from imported exercise"));
            ProgrammingExercise exerciseDetails = objectMapperAllowingUnknownProperties.readValue(Reader.of(exerciseDetailString), ProgrammingExercise.class);
            exerciseDetails.setId(null);
            return exerciseDetails;
        }
        catch (Exception e) {
            String errorMessage = e.getMessage();
            throw new NotFoundException("Could not retrieve exercise details from imported exercise: " + errorMessage, e);
        }
    }

    /**
     * Retrieves an entry from a given Sharing basket, selected by a RegEx and returns the content as a String.
     * If nothing is found, Optional.empty() is returned.
     *
     * @param matchingPattern RegEx matching the entry to return.
     * @param sharingInfo     of the basket to retrieve the entry from
     * @return The content of the entry, or Optional.empty() if not found.
     * @throws IOException if a reading error occurs
     */
    public Optional<String> getEntryFromBasket(Pattern matchingPattern, SharingInfoDTO sharingInfo) throws IOException {
        try (SharingMultipartZipFile zipFile = this.getCachedBasketItem(sharingInfo); ZipInputStream zippedRepositoryStream = new ZipInputStream(zipFile.getInputStream())) {

            ZipEntry entry;
            while ((entry = zippedRepositoryStream.getNextEntry()) != null) {
                Matcher matcher = matchingPattern.matcher(entry.getName());
                if (matcher.find()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[COPY_BUFFER_SIZE];
                    int bytesRead;
                    while ((bytesRead = zippedRepositoryStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    String entryContent = baos.toString(StandardCharsets.UTF_8);
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
     * Creates a zip-file for exercise and returns a URL pointing to Sharing
     * with a callback URL addressing the generated Zip file for download via the Sharing Platform
     *
     * @param exerciseId the ID of the exercise to export
     * @return URL to sharing with a callback URL to the generated zip file
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
     * an HMAC just to secure token for integrity against tampering.
     *
     * @param base64token the token (already base64 encoded
     * @return returns HMAC-Hash
     */
    private String createHMAC(String base64token) {
        // selects HMAC-method (here HmacSHA256)
        String algorithm = "HmacSHA256";
        String psk = sharingConnectorService.getSharingApiKeyOrNull();
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
     * checks the integrity of the base64token
     *
     * @param base64token the base64token
     * @param sec         the hmac hash
     * @return true, iff hash is correct
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
     * loads the stored file from the file system (via the b64 token).
     *
     * @param b64Token the base64 encoded token
     * @return the file referenced by the token
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

    private boolean isInvalidToken(String token) {
        return StringUtils.isBlank(token) || token.length() >= MAX_EXPORT_TOKEN_LENGTH || !token.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Returns a formatted filename for a basket file.
     *
     * @param basketToken  of the retrieved file
     * @param itemPosition of the retrieved file
     */
    private String getBasketFileName(String basketToken, int itemPosition) {
        String safeToken = basketToken.replaceAll("[^a-zA-Z0-9_-]", "");
        return "sharingBasket" + safeToken + "-" + itemPosition + ".zip";
    }

}
