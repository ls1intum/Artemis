package de.tum.cit.aet.artemis.programming.web;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.UriBuilder;

import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.codeability.sharing.plugins.api.util.SecretChecksumCalculator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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
import de.tum.cit.aet.artemis.programming.service.sharing.SharingEnabled;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingException;
import de.tum.cit.aet.artemis.programming.service.sharing.SharingSetupInfo;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller that orchestrates importing and exporting programming exercises
 * between Artemis and the external Sharing Platform.
 *
 * <p>
 * Active only when {@link SharingEnabled} matches; otherwise the controller is not loaded.
 * </p>
 */
@RestController
@RequestMapping("api/programming/sharing/")
@Conditional(SharingEnabled.class)
@Lazy
public class ExerciseSharingResource {

    /**
     * FileInputStream wrapper that deletes the underlying temporary file on close.
     * <p>
     * Used to ensure exported archives are removed after being streamed.
     * </p>
     */
    private static final class AutoDeletingFileInputStream extends FilterInputStream {

        private final Path path;

        private AutoDeletingFileInputStream(@NotNull Path path) throws IOException {
            super(Files.newInputStream(path));
            this.path = path;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            }
            finally {
                try {
                    Files.deleteIfExists(this.path);
                }
                catch (IOException e) {
                    log.error("Could not delete temporary file {}", this.path, e);
                }
            }
        }
    }

    public static final String SHARING_EXPORT_RESOURCE_PATH = "export";

    private static final Logger log = LoggerFactory.getLogger(ExerciseSharingResource.class);

    private final ExerciseSharingService exerciseSharingService;

    private final SharingConnectorService sharingConnectorService;

    private final ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService;

    public ExerciseSharingResource(ExerciseSharingService exerciseSharingService, SharingConnectorService sharingConnectorService,
            ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService) {
        this.exerciseSharingService = exerciseSharingService;
        this.programmingExerciseImportFromSharingService = programmingExerciseImportFromSharingService;
        this.sharingConnectorService = sharingConnectorService;
    }

    /**
     * GET {@code api/programming/sharing/import/basket}
     * <p>
     * Loads a shopping basket (a set of exercises) from the Sharing Platform after validating
     * the request using a shared-secret checksum.
     * </p>
     *
     * <h3>Validation</h3>
     * The checksum is computed over {@code returnURL} and {@code apiBaseURL} with the shared API key.
     * Requests with an invalid checksum are rejected with {@code 400 Bad Request}.
     *
     * @param basketToken opaque basket identifier issued by the Sharing Platform
     * @param returnURL   URL to which the UI should return after loading the basket
     * @param apiBaseURL  base URL of the Sharing Platform API (used for follow-up calls)
     * @param checksum    HMAC-like checksum of {@code returnURL} and {@code apiBaseURL} using the shared secret
     * @return {@code 200 OK} with the {@link ShoppingBasket}; {@code 404 Not Found} if the basket
     *         does not exist; {@code 400 Bad Request} on checksum failure
     */
    @GetMapping("import/basket")
    @EnforceAtLeastEditor
    public ResponseEntity<ShoppingBasket> loadShoppingBasket(@RequestParam String basketToken, @RequestParam String returnURL, @RequestParam String apiBaseURL,
            @RequestParam String checksum) {
        if (SecretChecksumCalculator.checkChecksum(Map.of("returnURL", returnURL, "apiBaseURL", apiBaseURL), sharingConnectorService.getSharingApiKey(), checksum)) {
            Optional<ShoppingBasket> sharingInfoDTO = exerciseSharingService.getBasketInfo(basketToken, apiBaseURL);
            return ResponseUtil.wrapOrNotFound(sharingInfoDTO);
        }
        else {
            log.warn("Checksum validation failed for basketToken={}", basketToken);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * POST {@code api/programming/sharing/setup-import}
     * <p>
     * Creates and persists a {@link ProgrammingExercise} in Artemis based on the provided
     * {@link SharingSetupInfo} received from the Sharing Platform.
     * </p>
     *
     * @param sharingSetupInfo details required to import (exercise metadata, templates, etc.)
     * @return {@code 200 OK} with the created {@link ProgrammingExercise}; {@code 500 Internal Server Error}
     *         on import failures (e.g., VCS operations, invalid payload, IO/URI issues)
     */
    @PostMapping("setup-import")
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> setUpFromSharingImport(@RequestBody SharingSetupInfo sharingSetupInfo) {
        try {
            ProgrammingExercise exercise = programmingExerciseImportFromSharingService.importProgrammingExerciseFromSharing(sharingSetupInfo);
            return ResponseEntity.ok().body(exercise);
        }
        catch (GitAPIException | SharingException | IOException | URISyntaxException e) {
            log.error("Error importing exercise from sharing platform", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST {@code api/programming/sharing/import/basket/exercise-details}
     * <p>
     * Returns detailed information for a single exercise referenced by its position in a basket.
     * The request must be signed using the shared checksum mechanism contained in {@link SharingInfoDTO}.
     * </p>
     *
     * @param sharingInfo basket-scoped reference to an exercise; also carries a checksum for validation
     * @return {@code 200 OK} with the {@link ProgrammingExercise} details; {@code 400 Bad Request}
     *         on checksum failure; {@code 404 Not Found} if the exercise cannot be resolved
     */
    // TODO: we should NOT use a POST request for a GET Operation
    @PostMapping("import/basket/exercise-details")
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExercise> getExerciseDetails(@RequestBody SharingInfoDTO sharingInfo) {
        if (!sharingInfo.checkChecksum(sharingConnectorService.getSharingApiKey())) {
            return ResponseEntity.badRequest().build();
        }
        ProgrammingExercise exerciseDetails = this.exerciseSharingService.getExerciseDetailsFromBasket(sharingInfo);
        return ResponseEntity.ok().body(exerciseDetails);
    }

    /**
     * POST {@code api/programming/sharing/export/{exerciseId}}
     * <p>
     * Exports a programming exercise to the Sharing Platform and returns a one-time URL that the client
     * can follow. The method appends a {@code callBack} parameter (the UI-return URL) to the generated link.
     * </p>
     *
     * @param callBackUrl URL the Sharing Platform should redirect to after export completes
     * @param exerciseId  Artemis exercise identifier to export
     * @return {@code 200 OK} with a JSON-quoted URL string pointing to the Sharing Platform;
     *         {@code 500 Internal Server Error} if export fails
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
     * GET {@code api/programming/sharing/export/{token}}
     * <p>
     * Streams the exported exercise archive (ZIP) to the Sharing Platform. Access is guarded by
     * a token and a secondary digest parameter.
     * </p>
     *
     * <h3>Security</h3>
     * Both {@code token} and {@code sec} must be valid according to
     * {@link ExerciseSharingService#validate(String, String)}. Invalid requests return {@code 401 Unauthorized}.
     *
     * <h3>Response</h3>
     * Returns an octet-stream with the ZIP content. The underlying temporary file is deleted
     * automatically once the response stream is closed.
     *
     * @param token base64-encoded export token that identifies the exported exercise
     * @param sec   digest of the shared key for request validation
     * @return {@code 200 OK} with the ZIP stream; {@code 404 Not Found} if the token is unknown;
     *         {@code 401 Unauthorized} on failed validation; {@code 500 Internal Server Error} on IO errors
     */
    @GetMapping(SHARING_EXPORT_RESOURCE_PATH + "/{token}")
    // Custom Key validation is applied
    public ResponseEntity<Resource> exportExerciseToSharing(@PathVariable("token") String token, @RequestParam("sec") String sec) {
        if (!exerciseSharingService.validate(token, sec)) {
            log.warn("Security Token {} is not valid", sec);
            return ResponseEntity.status(401).build();
        }
        Optional<Path> zipFilePath = exerciseSharingService.getExportedExerciseByToken(token);

        if (zipFilePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStreamResource resource = new InputStreamResource(new AutoDeletingFileInputStream(zipFilePath.get()));
            return ResponseEntity.ok().contentLength(zipFilePath.get().toFile().length()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("filename", zipFilePath.get().toString()).body(resource);
        }
        catch (IOException e) {
            log.error("Exported file not found for token {}", token, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
