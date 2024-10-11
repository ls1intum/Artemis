package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.SHARINGEXPORT_RESOURCE_PATH;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.UriBuilder;

import org.codeability.sharing.plugins.api.ShoppingBasket;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.SharingInfoDTO;
import de.tum.cit.aet.artemis.exercise.service.sharing.ProgrammingExerciseImportFromSharingService;
import de.tum.cit.aet.artemis.exercise.service.sharing.SharingException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.sharing.ExerciseSharingService;
import de.tum.cit.aet.artemis.sharing.SharingSetupInfo;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing the sharing of programming exercises.
 */
@RestController
@RequestMapping("/api")
@Profile("sharing")
public class ExerciseSharingResource {

    /**
     * a logger
     */
    private final Logger log = LoggerFactory.getLogger(ExerciseSharingResource.class);

    /**
     * the exercise sharing service
     */
    private final ExerciseSharingService exerciseSharingService;

    /**
     * the programming-exercise import from Sharing Service
     */
    private final ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService;

    /**
     * constuctor for spring
     *
     * @param exerciseSharingService                      the sharing service
     * @param programmingExerciseImportFromSharingService programming exercise import from sharing service
     */
    public ExerciseSharingResource(ExerciseSharingService exerciseSharingService, ProgrammingExerciseImportFromSharingService programmingExerciseImportFromSharingService) {
        this.exerciseSharingService = exerciseSharingService;

        this.programmingExerciseImportFromSharingService = programmingExerciseImportFromSharingService;
    }

    /**
     * GET .../sharing-import/basket
     *
     * @return the ResponseEntity with status 200 (OK) and with body the Shopping Basket, or with status 404 (Not Found)
     */
    @GetMapping("/sharing/import/basket")
    public ResponseEntity<ShoppingBasket> loadShoppingBasket(@RequestParam String basketToken, @RequestParam String apiBaseUrl) {
        Optional<ShoppingBasket> sharingInfoDTO = exerciseSharingService.getBasketInfo(basketToken, apiBaseUrl);
        return ResponseUtil.wrapOrNotFound(sharingInfoDTO);
    }

    /**
     * GET .../sharing/setup-import
     *
     * @return the ResponseEntity with status 200 (OK) and with body the programming exercise, or with status 404 (Not Found)
     */
    @PostMapping("/sharing/setup-import")
    public ResponseEntity<ProgrammingExercise> setUpFromSharingImport(@RequestBody SharingSetupInfo sharingSetupInfo)
            throws GitAPIException, SharingException, IOException, URISyntaxException {
        ProgrammingExercise exercise = programmingExerciseImportFromSharingService.importProgrammingExerciseFromSharing(sharingSetupInfo);
        return ResponseEntity.ok().body(exercise);
    }

    /**
     * GET .../sharing/import/basket/problemStatement : get problem statement of the exercise defined in sharingInfo.
     *
     * @param sharingInfo the sharing info (with exercise position in basket)
     * @return the ResponseEntity with status 200 (OK) and with body the problem statement, or with status 404 (Not Found)
     */
    @PostMapping("/sharing/import/basket/problemStatement")
    public ResponseEntity<String> getProblemStatement(@RequestBody SharingInfoDTO sharingInfo) throws IOException {
        String problemStatement = this.exerciseSharingService.getProblemStatementFromBasket(sharingInfo);
        return ResponseEntity.ok().body(problemStatement);
    }

    /**
     * GET .../sharing/import/basket/exerciseDetails : get exercise details of the exercise defined in sharingInfo.
     * TODO: why seems result identical to getProblemStatement?
     *
     * @param sharingInfo the sharing info (with exercise position in basket)
     * @return the ResponseEntity with status 200 (OK) and with body the problem statement, or with status 404 (Not Found)
     */
    @PostMapping("/sharing/import/basket/exerciseDetails")
    public ResponseEntity<String> getExerciseDetails(@RequestBody SharingInfoDTO sharingInfo) throws IOException {
        String exerciseDetails = this.exerciseSharingService.getExerciseDetailsFromBasket(sharingInfo);
        return ResponseEntity.ok().body(org.apache.commons.text.StringEscapeUtils.escapeHtml4(exerciseDetails));
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
    public ResponseEntity<String> exportExerciseToSharing(HttpServletResponse response, @RequestBody String callBackUrl, @PathVariable("exerciseId") Long exerciseId) {
        try {
            URI uriRedirect = exerciseSharingService.exportExerciseToSharing(exerciseId).toURI();
            uriRedirect = UriBuilder.fromUri(uriRedirect).queryParam("callBack", callBackUrl).build();
            return ResponseEntity.ok().body("\"" + uriRedirect.toString() + "\"");
        }
        catch (SharingException | URISyntaxException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }

    }

    /**
     * GET /sharing/export/{exerciseToken}: Endpoint exposing an exported exercise zip to Sharing
     *
     * @param token in base64 format and used to retrieve the exercise
     * @return a stream of the zip file
     * @throws FileNotFoundException if zip file does not exist any more
     */
    @GetMapping(SHARINGEXPORT_RESOURCE_PATH + "/{token}")
    // Custom Key validation is applied
    public ResponseEntity<Resource> exportExerciseToSharing(@PathVariable("token") String token, @RequestParam("sec") String sec) throws FileNotFoundException {
        if (sec.isEmpty() || !exerciseSharingService.validate(token, sec)) {
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

            public NewFileInputStream(@NotNull File file) throws FileNotFoundException {
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

}
