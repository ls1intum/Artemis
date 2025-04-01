package de.tum.cit.aet.artemis.sharing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URIBuilder;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
import de.tum.cit.aet.artemis.core.service.FilePathService;
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

    @Autowired
    @Qualifier("sharingRestTemplate")
    protected RestTemplate restTemplate;

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
     * @param apiBaseUrl  the api base url to request the basket content from
     * @return an optional shopping basket
     */
    public Optional<ShoppingBasket> getBasketInfo(String basketToken, String apiBaseUrl) {
        String basketRequestURL = correctLocalHostInDocker(apiBaseUrl).concat("/basket/").concat(basketToken);
        try {
            ShoppingBasket shoppingBasket = restTemplate.getForObject(basketRequestURL, ShoppingBasket.class);
            return Optional.ofNullable(shoppingBasket);
        }
        catch (HttpClientErrorException.NotFound nf) {
            log.warn("Basket {} not found", basketToken, nf);
            return Optional.empty();
        }
        catch (RestClientException rpe) {
            log.warn("Unrecognized property when importing exercise from Sharing", rpe);
            return Optional.empty();
        }
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
        try {
            File f = repositoryCache.get(Pair.of(sharingInfo, itemPosition));
            SharingMultipartZipFile zipFileItem = new SharingMultipartZipFile(getBasketFileName(sharingInfo.getBasketToken(), itemPosition), new FileInputStream(f));
            return Optional.of(zipFileItem);
        }
        catch (WebApplicationException | IOException | ExecutionException wae) {
            log.warn("Exception during shared exercise retrieval", wae);
            throw new SharingException("Could not retrieve basket item", wae);
        }
    }

    /**
     * simple loading cache for file with 1 hour timeout.
     */
    private final LoadingCache<Pair<SharingInfoDTO, Integer>, File> repositoryCache = CacheBuilder.newBuilder().maximumSize(10000).expireAfterAccess(1, TimeUnit.HOURS)
            .removalListener(notification -> {
                File f = (File) notification.getValue();
                if (f != null) {
                    boolean deleted = f.delete();
                    if (!deleted) {
                        log.info("Cannot delete {}", f.getName());
                    }
                }
            }).build(new CacheLoader<>() {

                @Override
                public File load(Pair<SharingInfoDTO, Integer> sharingInfoAndPos) throws SharingException {
                    SharingInfoDTO sharingInfo = sharingInfoAndPos.getLeft();
                    int itemPosition = sharingInfoAndPos.getRight();
                    try {
                        String exercisesZipUrl = correctLocalHostInDocker(sharingInfo.getApiBaseURL()) + "/basket/{basketToken}/repository/" + itemPosition + "?format={format}";
                        Resource zipInputResource = restTemplate.getForObject(exercisesZipUrl, Resource.class,
                                Map.of("basketToken", sharingInfo.getBasketToken(), "format", "artemis"));
                        if (zipInputResource == null) {
                            throw new SharingException("Could not retrieve basket item resource");
                        }
                        InputStream zipInput = zipInputResource.getInputStream();

                        File basketFile = Files.createTempFile(FilePathService.getTempFilePath(), "basketStore", ".zip").toFile();
                        FileCopyUtils.copy(zipInput, new FileOutputStream(basketFile));
                        return basketFile;
                    }
                    catch (IOException e) {
                        log.warn("Cannot load sharing Info", e);
                        throw new SharingException("Cannot load sharing Info", e);
                    }

                }
            });

    public SharingMultipartZipFile getCachedBasketItem(SharingInfoDTO sharingInfo) throws IOException, SharingException {
        int itemPosition = sharingInfo.getExercisePosition();
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
        Pattern pattern = Pattern.compile("^(Problem-Statement|exercise\\.md)$", Pattern.CASE_INSENSITIVE);

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
     * Retrieves the Exercise-Details file from a Sharing basket
     *
     * @param sharingInfo of the basket to extract the problem statement from
     * @return The content of the Exercise-Details file
     */
    public ProgrammingExercise getExerciseDetailsFromBasket(SharingInfoDTO sharingInfo) {
        Pattern pattern = Pattern.compile("^Exercise-Details", Pattern.CASE_INSENSITIVE);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.findAndRegisterModules();

        try {
            String exerciseDetailString = this.getEntryFromBasket(pattern, sharingInfo);
            ProgrammingExercise exerciseDetails = objectMapper.readValue(new StringReader(exerciseDetailString), ProgrammingExercise.class);
            exerciseDetails.setId(null);
            return exerciseDetails;
        }
        catch (Exception e) {
            throw new NotFoundException("Could not retrieve exercise details from imported exercise", null, e);
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
            String msg = "An error occurred during URL creation: " + e.getMessage();
            log.error(msg, e);
            throw new SharingException(msg, e);
        }
        catch (IOException e) {
            String msg = "Could not generate Zip file for export: " + e.getMessage();
            log.error(msg, e);
            throw new SharingException(msg, e);
        }
    }

    /**
     * just to secure token for integrity
     *
     * @param base64token the token (already base64 encoded
     * @return returns HMAC-Hash
     */
    private String createHMAC(String base64token) {
        // selects HMAC-method (here HmacSHA256)
        String algorithm = "HmacSHA256";
        String psk = sharingConnectorService.getSharingApiKeyOrNull();

        SecretKeySpec secretKeySpec = new SecretKeySpec(psk.getBytes(StandardCharsets.UTF_8), algorithm);

        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(base64token.getBytes(StandardCharsets.UTF_8));

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
        return MessageDigest.isEqual(computedHMAC.getBytes(StandardCharsets.UTF_8), sec.getBytes(StandardCharsets.UTF_8));
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
