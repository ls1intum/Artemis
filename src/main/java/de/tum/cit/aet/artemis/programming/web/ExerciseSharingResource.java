package de.tum.cit.aet.artemis.programming.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.UriBuilder;

import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.sharing.ExerciseSharingService;
import de.tum.cit.aet.artemis.programming.service.sharing.ProgrammingExerciseImportFromSharingService;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingConnectorService;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingException;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingSetupInfo;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing the sharing of programming exercises.
 */
@RestController
@RequestMapping("api/programming/sharing/")
@Profile("sharing")
@Lazy
public class ExerciseSharingResource {

    /*
     * Customized FileInputStream to delete and therefore clean up the returned files
     */
    private static class AutoDeletingFileInputStream extends FileInputStream {

        private final File file;

        private AutoDeletingFileInputStream(@NotNull File file) throws FileNotFoundException {
            super(file);
            this.file = file;
        }

        @Override
        public void close() throws IOException {
            super.close();
            try {
                Files.delete(this.file.toPath());
            }
            catch (IOException e) {
                log.error("Cannot delete {}", this.file.toPath());
            }
        }
    }

    /**
     * a sharing configuration resource path for sharing config export request
     */
    public static final String SHARING_EXPORT_RESOURCE_PATH = "export";

    private static final Logger log = LoggerFactory.getLogger(ExerciseSharingResource.class);

    private final ExerciseSharingService exerciseSharingService;

    private final SharingConnectorService sharingConnectorService;

    private final ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService;

    /**
     * constructor for spring
     *
     * @param exerciseSharingService                      the sharing service
     * @param sharingConnectorService                     the sharing connector service
     * @param programmingExerciseImportFromSharingService programming exercise import from sharing service
     */
    public ExerciseSharingResource(ExerciseSharingService exerciseSharingService, SharingConnectorService sharingConnectorService,
            ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService) {
        this.exerciseSharingService = exerciseSharingService;
        this.programmingExerciseImportFromSharingService = programmingExerciseImportFromSharingService;
        this.sharingConnectorService = sharingConnectorService;
    }

    /**
     * GET .../sharing/import/basket
     *
     * @param basketToken the token of the shopping basket
     * @param returnURL   the URL to return to after the basket is loaded
     * @param apiBaseURL  the base URL of the API
     * @param checksum    the checksum to validate the request
     * @return the ResponseEntity with status 200 (OK) and with body the Shopping Basket, or with status 404 (Not Found)
     */
    @GetMapping("import/basket")
    @EnforceAtLeastEditor
    public ResponseEntity<ShoppingBasket> loadShoppingBasket(@RequestParam String basketToken, @RequestParam String returnURL, @RequestParam String apiBaseURL,
            @RequestParam String checksum) {
        if (SecretChecksumCalculator.checkChecksum(Map.of("returnURL", returnURL, "apiBaseURL", apiBaseURL), sharingConnectorService.getSharingApiKeyOrNull(), checksum)) {
            Optional<ShoppingBasket> sharingInfoDTO = exerciseSharingService.getBasketInfo(basketToken, apiBaseURL);
            return ResponseUtil.wrapOrNotFound(sharingInfoDTO);
        }
        else {
            log.warn("Checksum validation failed for basketToken={}", basketToken);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET .../sharing/setup-import
     * sets up the imported exercise as a ProgrammingExercise.
     *
     * @param sharingSetupInfo the sharing setup information containing the details of the exercise to be imported
     * @throws GitAPIException if there is an error with the git operations
     * @return the ResponseEntity with status 200 (OK) and with body the programming exercise, or with status 404 (Not Found)
     */
    @PostMapping("setup-import")
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> setUpFromSharingImport(@RequestBody SharingSetupInfo sharingSetupInfo)
            throws GitAPIException, SharingException, IOException, URISyntaxException {
        ProgrammingExercise exercise = programmingExerciseImportFromSharingService.importProgrammingExerciseFromSharing(sharingSetupInfo);
        return ResponseEntity.ok().body(exercise);
    }

    /**
     * GET .../sharing/import/basket/problem-statement get the problem statement of the exercise defined in sharingInfo.
     *
     * @param sharingInfo the sharing info (with exercise position in the basket)
     * @return the ResponseEntity with status 200 (OK) and with body the problem statement, or with status 404 (Not Found)
     */
    @PostMapping("import/basket/problem-statement")
    @EnforceAtLeastEditor
    public ResponseEntity<String> getProblemStatement(@RequestBody SharingInfoDTO sharingInfo) {
        if (!sharingInfo.checkChecksum(sharingConnectorService.getSharingApiKeyOrNull())) {
            return ResponseEntity.badRequest().build();
        }
        String problemStatement = this.exerciseSharingService.getProblemStatementFromBasket(sharingInfo);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(problemStatement);
    }

    /**
     * GET .../sharing/import/basket/exercise-details: get exercise details of the exercise defined in sharingInfo.
     *
     * @param sharingInfo the sharing info (with exercise position in the basket)
     * @return the ResponseEntity with status 200 (OK) and with body the problem statement, or with status 404 (Not Found)
     */
    @PostMapping("import/basket/exercise-details")
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> getExerciseDetails(@RequestBody SharingInfoDTO sharingInfo) {
        if (!sharingInfo.checkChecksum(sharingConnectorService.getSharingApiKeyOrNull())) {
            return ResponseEntity.badRequest().build();
        }
        ProgrammingExercise exerciseDetails = this.exerciseSharingService.getExerciseDetailsFromBasket(sharingInfo);
        return ResponseEntity.ok().body(exerciseDetails);
    }

    /**
     * POST /sharing/export/{exerciseId}: export programming exercise to sharing
     * by generating a unique URL token exposing the exercise
     *
     * @param exerciseId  the id of the exercise to export
     * @param callBackUrl the call back url returned to the client after export has finished
     * @return the URL to Sharing
     */
    @PostMapping(SHARING_EXPORT_RESOURCE_PATH + "/{exerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<String> exportExerciseToSharing(@RequestBody String callBackUrl, @PathVariable("exerciseId") Long exerciseId) {
        try {
            URI uriRedirect = exerciseSharingService.exportExerciseToSharing(exerciseId).toURI();
            uriRedirect = UriBuilder.fromUri(uriRedirect).queryParam("callBack", callBackUrl).build();
            return ResponseEntity.ok().body("\"" + uriRedirect.toString() + "\"");
        }
        catch (SharingException | URISyntaxException e) {
            log.error("Error exporting exercise to sharing platform", e);
            return ResponseEntity.internalServerError().body("An error occurred while exporting the exercise");
        }
    }

    /**
     * GET /sharing/export/{exerciseToken}: Endpoint exposing an exported exercise zip to Sharing
     *
     * @param token in base64 format and used to retrieve the exercise
     * @param sec   digest of the shared key
     * @return a stream of the zip file
     * @throws FileNotFoundException if the zip file does not exist anymore
     */
    @GetMapping(SHARING_EXPORT_RESOURCE_PATH + "/{token}")
    @EnforceAtLeastEditor
    // Custom Key validation is applied
    public ResponseEntity<Resource> exportExerciseToSharing(@PathVariable("token") String token, @RequestParam("sec") String sec) throws FileNotFoundException {
        if (!exerciseSharingService.validate(token, sec)) {
            log.warn("Security Token {} is not valid", sec);
            return ResponseEntity.status(401).build();
        }
        File zipFile = exerciseSharingService.getExportedExerciseByToken(token);

        if (zipFile == null) {
            return ResponseEntity.notFound().build();
        }

        InputStreamResource resource = new InputStreamResource(new AutoDeletingFileInputStream(zipFile));

        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

}
