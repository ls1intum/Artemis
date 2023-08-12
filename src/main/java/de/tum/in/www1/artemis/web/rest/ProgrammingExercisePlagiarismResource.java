package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.*;

import java.io.FileInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismChecksConfigHelper;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismChecksService;
import de.tum.in.www1.artemis.service.plagiarism.ProgrammingLanguageNotSupportedForPlagiarismChecksException;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing ProgrammingExercise.
 */
@RestController
@RequestMapping(ROOT)
public class ProgrammingExercisePlagiarismResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExercisePlagiarismResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final PlagiarismChecksService plagiarismChecksService;

    public ProgrammingExercisePlagiarismResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            PlagiarismResultRepository plagiarismResultRepository, PlagiarismChecksService plagiarismChecksService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.plagiarismChecksService = plagiarismChecksService;
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-result : Return the latest plagiarism result or null, if no plagiarism was detected for this exercise yet.
     *
     * @param exerciseId ID of the programming exercise for which the plagiarism result should be returned
     * @return The ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the parameters are invalid
     */
    @GetMapping(PLAGIARISM_RESULT)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<TextPlagiarismResult> getPlagiarismResult(@PathVariable long exerciseId) {
        log.debug("REST request to get the latest plagiarism result for the programming exercise with id: {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        var plagiarismResult = plagiarismResultRepository.findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(programmingExercise.getId());
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return ResponseEntity.ok((TextPlagiarismResult) plagiarismResult);
    }

    /**
     * GET /programming-exercises/{exerciseId}/check-plagiarism : Start the automated plagiarism detection for the given exercise and return its result.
     *
     * @param exerciseId The ID of the programming exercise for which the plagiarism check should be executed
     * @return the ResponseEntity with status 200 (OK) and the list of at most 500 pair-wise submissions with a similarity above the given threshold (e.g. 50%).
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    @GetMapping(CHECK_PLAGIARISM)
    @EnforceAtLeastEditor
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.PlagiarismChecks })
    public ResponseEntity<TextPlagiarismResult> checkPlagiarism(@PathVariable long exerciseId) throws ExitException, IOException {
        var programmingExercise = programmingExerciseRepository.findByIdWithPlagiarismChecksConfigElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        long start = System.nanoTime();
        log.info("Started manual plagiarism checks for programming exercise: exerciseId={}.", exerciseId);
        PlagiarismChecksConfigHelper.createAndSaveDefaultIfNull(programmingExercise, programmingExerciseRepository);
        try {
            var plagiarismResult = plagiarismChecksService.checkProgrammingExercise(programmingExercise);
            return ResponseEntity.ok(plagiarismResult);
        }
        catch (ProgrammingLanguageNotSupportedForPlagiarismChecksException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "programmingLanguageNotSupported");
        }
        finally {
            log.info("Finished manual plagiarism checks for programming exercise: exerciseId={}, elapsed={}.", exerciseId, TimeLogUtil.formatDurationFrom(start));
        }
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-check : Uses JPlag to check for plagiarism and returns the generated output as zip file
     *
     * @param exerciseId The ID of the programming exercise for which the plagiarism check should be executed
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     * @throws IOException is thrown for file handling errors
     */
    @GetMapping(value = CHECK_PLAGIARISM_JPLAG_REPORT)
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> checkPlagiarismWithJPlagReport(@PathVariable long exerciseId) throws IOException {
        log.debug("REST request to check plagiarism for ProgrammingExercise with id: {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithPlagiarismChecksConfigElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        long start = System.nanoTime();
        log.info("Started manual plagiarism checks with Jplag report for programming exercise: exerciseId={}.", exerciseId);
        PlagiarismChecksConfigHelper.createAndSaveDefaultIfNull(programmingExercise, programmingExerciseRepository);
        try {
            var zipFile = plagiarismChecksService.checkProgrammingExerciseWithJplagReport(programmingExercise);
            if (zipFile == null) {
                throw new BadRequestAlertException("Insufficient amount of valid and long enough submissions available for comparison.", "Plagiarism Check",
                        "notEnoughSubmissions");
            }

            var resource = new InputStreamResource(new FileInputStream(zipFile));
            return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
        }
        catch (ProgrammingLanguageNotSupportedForPlagiarismChecksException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "programmingLanguageNotSupported");
        }
        finally {
            log.info("Finished manual plagiarism checks with Jplag report for programming exercise: exerciseId={}, elapsed={}.", exerciseId, TimeLogUtil.formatDurationFrom(start));
        }
    }
}
