package de.tum.in.www1.artemis.service.sharing;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import org.apache.http.client.utils.URIBuilder;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.glassfish.jersey.client.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.sharing.SharingMultipartZipFile;
import de.tum.in.www1.artemis.exception.SharingException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.SharingPluginService;
import de.tum.in.www1.artemis.service.export.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.web.rest.dto.SharingInfoDTO;

@Service
@Profile("sharing")
public class ExerciseSharingService {

    private final Logger log = LoggerFactory.getLogger(ExerciseSharingService.class);

    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    @Value("${server.url}")
    protected String artemisServerUrl;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final SharingPluginService sharingPluginService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ExerciseSharingService(ProgrammingExerciseExportService programmingExerciseExportService, SharingPluginService sharingPluginService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.sharingPluginService = sharingPluginService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    LoadingCache<Pair<String, Integer>, File> repositoryCache = CacheBuilder.newBuilder().maximumSize(10000).expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<>() {

        public File load(Pair<String, Integer> key) {
            return null;
        }
    });

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

    public SharingMultipartZipFile getCachedBasketItem(SharingInfoDTO sharingInfo) throws IOException, SharingException {
        int itemPosition = sharingInfo.getExercisePosition();
        File f;
        f = repositoryCache.getIfPresent(Pair.of(sharingInfo.getBasketToken(), itemPosition));
        if (f != null) {
            try {
                return new SharingMultipartZipFile(getBasketFileName(sharingInfo.getBasketToken(), itemPosition), new FileInputStream(f));
            }
            catch (FileNotFoundException e) {
                log.warn("Cannot find cached file for {}:{} at {}", sharingInfo.getBasketToken(), itemPosition, f.getAbsoluteFile(), e);
            }
        }
        Optional<SharingMultipartZipFile> basketItem = getBasketItem(sharingInfo, itemPosition);
        return basketItem.orElse(null);
    }

    private String correctLocalHostInDocker(String url) {
        if (url.contains("//localhost")) {
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
            return Objects.requireNonNullElse(problemStatement, "No Problem Statement found!");
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
     *
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
     * @throws IOException if a readingf error occurs
     */
    public String getEntryFromBasket(Pattern matchingPattern, SharingInfoDTO sharingInfo) throws IOException {
        InputStream repositoryStream = null;
        try {
            repositoryStream = this.getCachedBasketItem(sharingInfo).getInputStream();
        }
        catch (IOException | SharingException e) {
            log.error("Cannot read input Template for " + sharingInfo.getBasketToken());
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
        if (!sharingPluginService.isSharingApiBaseUrlPresent()) {
            throw new SharingException("No Sharing ApiBaseUrl provided");
        }
        try {
            Optional<ProgrammingExercise> exercise = programmingExerciseRepository.findWithPlagiarismDetectionConfigAndTeamConfigById(exerciseId);

            if (exercise.isEmpty()) {
                throw new SharingException("Could not find exercise to export");
            }

            List<String> exportErrors = new ArrayList<>();
            Path zipFilePath = programmingExerciseExportService.exportProgrammingExerciseForDownload(exercise.get(), exportErrors);

            if (!exportErrors.isEmpty()) {
                throw new SharingException("Could not generate Zip file to export");
            }

            // remove the 'repoDownloadClonePath' part and 'zip' extension
            String token = Path.of(repoDownloadClonePath).relativize(zipFilePath).toString().replace(".zip", "");
            String tokenInB64 = Base64.getEncoder().encodeToString(token.getBytes()).replaceAll("=+$", "");

            URIBuilder builder = new URIBuilder();
            URL apiBaseUrl = sharingPluginService.getSharingApiBaseUrlOrNull();
            String importEndpoint = "/exercise/import";
            String exerciseCallback = artemisServerUrl + "/api/sharing/export/" + tokenInB64;
            builder.setScheme(apiBaseUrl.getProtocol()).setHost(apiBaseUrl.getHost()).setPath(apiBaseUrl.getPath().concat(importEndpoint)).setPort(apiBaseUrl.getPort())
                    .addParameter("exerciseUrl", exerciseCallback);
            if (sharingPluginService.getSharingApiKeyOrNull() != null) {
                builder.addParameter("apiKey", sharingPluginService.getSharingApiKeyOrNull());
            }
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

    public File getExportedExerciseByToken(String b64Token) {
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
