package de.tum.in.www1.artemis.service.sharing;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.apache.http.client.utils.URIBuilder;
import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.codeability.sharing.plugins.api.search.SearchResultDTO;
import org.glassfish.jersey.client.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.sharing.SharingMultipartZipFile;
import de.tum.in.www1.artemis.exception.SharingException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.SharingPluginService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.web.rest.dto.SharingInfoDTO;

@Service
@Profile("sharing")
public class ExerciseSharingService {

    @Value("${artemis.repo-download-clone-path}")
    private String repoDownloadClonePath;

    @Value("${server.url}")
    protected String artemisServerUrl;

    @Value("${artemis.sharing.api-url}")
    private String sharingApiUrl;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final SharingPluginService sharingPluginService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ExerciseSharingService(ProgrammingExerciseExportService programmingExerciseExportService, SharingPluginService sharingPluginService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.sharingPluginService = sharingPluginService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    private final Logger log = LoggerFactory.getLogger(ExerciseSharingService.class);

    LoadingCache<Pair<String, Integer>, File> repositoryCache = CacheBuilder.newBuilder().maximumSize(10000).expireAfterWrite(4, TimeUnit.HOURS).build(new CacheLoader<>() {

        public File load(Pair<String, Integer> key) {
            return null;
        }
    });

    public Optional<ShoppingBasket> getBasketInfo(String basketToken, String apiBaseUrl) {
        ClientConfig restClientConfig = new ClientConfig();
        restClientConfig.register(ShoppingBasket.class);
        Client client = ClientBuilder.newClient(restClientConfig);

        WebTarget target = client.target(apiBaseUrl.concat("/basket/").concat(basketToken));

        ShoppingBasket shoppingBasket = target.request().accept(MediaType.APPLICATION_JSON).get(ShoppingBasket.class);
        if (shoppingBasket != null) {
            for (SearchResultDTO ex : shoppingBasket.exerciseInfo) {
                // TODO: add some artemis specific magic here in case
            }
        }
        return Optional.ofNullable(shoppingBasket);
    }

    public Optional<SharingMultipartZipFile> getBasketItem(SharingInfoDTO sharingInfo, int itemPosition) throws SharingException {
        ClientConfig restClientConfig = new ClientConfig();
        restClientConfig.register(ShoppingBasket.class);
        Client client = ClientBuilder.newClient(restClientConfig);

        WebTarget target = client.target(sharingInfo.getApiBaseURL() + "/basket/" + sharingInfo.getBasketToken() + "/repository/" + itemPosition);
        InputStream zipInput = target.request().accept(MediaType.APPLICATION_OCTET_STREAM).get(InputStream.class);

        if (zipInput == null) {
            throw new SharingException("Could not retrieve basket item");
        }

        SharingMultipartZipFile zipFileItem = new SharingMultipartZipFile(getBasketFileName(sharingInfo.getBasketToken(), itemPosition), zipInput);
        return Optional.of(zipFileItem);
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
        f = File.createTempFile("baskedCache" + sharingInfo.getBasketToken() + "-" + itemPosition, "zip");

        Optional<SharingMultipartZipFile> basketItem = getBasketItem(sharingInfo, itemPosition);
        if (basketItem.isEmpty()) {
            return null;
        }
        return basketItem.get();
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
            ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
            File zipFile = programmingExerciseExportService.exportProgrammingExerciseInstructorMaterial(exercise, null).toFile();
            String token = zipFile.getName().replace(".zip", "");

            URIBuilder builder = new URIBuilder();
            URL apiBaseUrl = sharingPluginService.getSharingApiBaseUrlOrNull();
            String importEndpoint = "/exercise/import";
            String exerciseCallback = artemisServerUrl + "/api/sharing/export/" + token;
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

    public File getExportedExerciseByToken(String token) {
        Path parent = Paths.get(repoDownloadClonePath, "programming-exercise-material", token + ".zip");
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
