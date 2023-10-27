package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.SHARINGEXPORT_RESOURCE_PATH;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.sharing.SharingSetupInfo;
import de.tum.in.www1.artemis.exception.SharingException;
import de.tum.in.www1.artemis.service.SharingPluginService;
import de.tum.in.www1.artemis.service.sharing.ExerciseSharingService;
import de.tum.in.www1.artemis.service.sharing.ProgrammingExerciseImportFromSharingService;
import de.tum.in.www1.artemis.web.rest.dto.SharingInfoDTO;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Exercise.
 */
@RestController
@RequestMapping("/api")
@Profile("sharing")
public class ExerciseSharingResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseSharingResource.class);

    private final SharingPluginService sharingPluginService;

    private final ExerciseSharingService exerciseSharingService;

    private final ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService;

    public ExerciseSharingResource(ExerciseSharingService exerciseSharingService, SharingPluginService sharingPluginService,
            ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService) {
        this.exerciseSharingService = exerciseSharingService;
        this.sharingPluginService = sharingPluginService;
        this.programmingExerciseImportFromSharingService = programmingExerciseImportFromSharingService;
    }

    /**
     * GET /sharing-import/basket
     *
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/sharing/import/basket")
    public ResponseEntity<ShoppingBasket> loadShoppingBasket(@RequestParam String basketToken, @RequestParam String apiBaseUrl) {
        Optional<ShoppingBasket> sharingInfoDTO = exerciseSharingService.getBasketInfo(basketToken, apiBaseUrl);
        return ResponseUtil.wrapOrNotFound(sharingInfoDTO);
    }

    @PostMapping("/sharing/setup-import")
    public ResponseEntity<ProgrammingExercise> setUpFromSharingImport(@RequestBody SharingSetupInfo sharingSetupInfo)
            throws GitAPIException, SharingException, IOException, URISyntaxException {
        ProgrammingExercise exercise = programmingExerciseImportFromSharingService.importProgrammingExerciseFromSharing(sharingSetupInfo);
        return ResponseEntity.ok().body(exercise);
    }

    /**
     * GET /sharing/import/basket/exercise/{exercisePosition}/problemStatement : get problem statement.
     *
     * @param sharingInfo the basket
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PostMapping("/sharing/import/basket/problemStatement")
    public ResponseEntity<String> getProblemStatement(@RequestBody SharingInfoDTO sharingInfo) throws IOException {
        String problemStatement = this.exerciseSharingService.getProblemStatementFromBasket(sharingInfo);
        return ResponseEntity.ok().body(problemStatement);
    }

    @PostMapping("/sharing/import/basket/exerciseDetails")
    public ResponseEntity<String> getExerciseDetails(@RequestBody SharingInfoDTO sharingInfo) throws IOException {
        String exerciseDetails = this.exerciseSharingService.getExerciseDetailsFromBasket(sharingInfo);
        return ResponseEntity.ok().body(exerciseDetails);
    }

    @NotNull
    private ResponseEntity<String> getFileContentFromBasket(String fileName, @RequestBody SharingInfoDTO sharingInfo) throws IOException {
        InputStream repositoryStream = null;
        try {
            repositoryStream = exerciseSharingService.getCachedBasketItem(sharingInfo).getInputStream();
        }
        catch (IOException | SharingException e) {
            log.error("Cannot read input Template for " + sharingInfo.getBasketToken());
        }

        ZipInputStream zippedRepositoryStream = new ZipInputStream(repositoryStream);

        ZipEntry statement = getEntry(fileName, zippedRepositoryStream);
        if (statement == null) {
            return ResponseEntity.ok().body("Statement not found!");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamUtils.copy(zippedRepositoryStream, baos);
        String statementString = baos.toString(StandardCharsets.UTF_8);
        return ResponseEntity.ok().body(statementString);
    }

    /**
     * POST /sharing/export/{exerciseId}: export programming exercise to sharing
     * by generating a unique URL token exposing the exercise
     *
     * @param exerciseId the id of the exercise to export
     * @return the URL to Sharing
     */
    @PostMapping(SHARINGEXPORT_RESOURCE_PATH + "/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public Response exportExerciseToSharing(@RequestBody String callBackUrl, @PathVariable("exerciseId") Long exerciseId) throws URISyntaxException, SharingException {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(exerciseSharingService.exportExerciseToSharing(exerciseId).toURI());

        URI uriRedirect = target.request().get(URI.class);
        uriRedirect = UriBuilder.fromUri(uriRedirect).queryParam("callback", callBackUrl).build();
        return Response.temporaryRedirect(uriRedirect).build();
    }

    /**
     * GET /sharing/export/{exerciseToken}: Endpoint exposing an exported exercise to Sharing
     *
     * @param sharingApiKey used to validate the export
     * @param token         in base64 format and used to retrieve the exercise
     * @return
     * @throws FileNotFoundException
     */
    @GetMapping(SHARINGEXPORT_RESOURCE_PATH + "/{token}")
    // Custom Key validation is applied
    public ResponseEntity<Resource> exportExerciseToSharing(@RequestHeader("Authorization") Optional<String> sharingApiKey, @PathVariable("token") String token)
            throws FileNotFoundException {
        if (sharingApiKey.isEmpty() || !sharingPluginService.validate(sharingApiKey.get())) {
            return ResponseEntity.status(401).body(null);
        }
        File zipFile = exerciseSharingService.getExportedExerciseByToken(token);

        if (zipFile == null) {
            return ResponseEntity.notFound().build();
        }

        /*
         * Customized FileInputStream to delete and therefore clean up the returned files
         */
        class NewFileInputStream extends FileInputStream {

            final File file;

            public NewFileInputStream(@org.jetbrains.annotations.NotNull File file) throws FileNotFoundException {
                super(file);
                this.file = file;
            }

            public void close() throws IOException {
                super.close();
                if (!file.delete()) {
                    log.warn("Could not delete imported file from Sharing-Platform");
                }
            }
        }

        InputStreamResource resource = new InputStreamResource(new NewFileInputStream(zipFile));

        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.valueOf("application/zip")).header("filename", zipFile.getName()).body(resource);
    }

    public ZipEntry getEntry(String path, ZipInputStream zippedRepositoryStream) throws IOException {
        ZipEntry currentEntry = zippedRepositoryStream.getNextEntry();
        String prefix = "";
        if (currentEntry != null && currentEntry.isDirectory()) { // main directory is prefix to all entries
            prefix = currentEntry.getName();
        }
        while (currentEntry != null) {
            if (!currentEntry.isDirectory() && currentEntry.getName().equals(prefix + path))
                return currentEntry;
            currentEntry = zippedRepositoryStream.getNextEntry();
        }
        return null;

    }

}
