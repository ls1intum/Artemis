package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.plagiarism.ProgrammingPlagiarismDetectionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

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

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService;

    public ProgrammingExercisePlagiarismResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            PlagiarismResultRepository plagiarismResultRepository, Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService,
            ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.programmingPlagiarismDetectionService = programmingPlagiarismDetectionService;
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-result : Return the latest plagiarism result or null, if no plagiarism was detected for this exercise yet.
     *
     * @param exerciseId ID of the programming exercise for which the plagiarism result should be returned
     * @return The ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the parameters are invalid
     */
    @GetMapping(PLAGIARISM_RESULT)
    @PreAuthorize("hasRole('EDITOR')")
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
     * @param exerciseId          The ID of the programming exercise for which the plagiarism check should be executed
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @return the ResponseEntity with status 200 (OK) and the list of at most 500 pair-wise submissions with a similarity above the given threshold (e.g. 50%).
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    @GetMapping(CHECK_PLAGIARISM)
    @PreAuthorize("hasRole('EDITOR')")
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.PlagiarismChecks })
    public ResponseEntity<TextPlagiarismResult> checkPlagiarism(@PathVariable long exerciseId, @RequestParam float similarityThreshold, @RequestParam int minimumScore)
            throws ExitException, IOException {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        ProgrammingLanguage language = programmingExercise.getProgrammingLanguage();
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.get().getProgrammingLanguageFeatures(language);

        if (!programmingLanguageFeature.isPlagiarismCheckSupported()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "programmingLanguageNotSupported",
                    "Artemis does not support plagiarism checks for the programming language " + language)).body(null);
        }

        long start = System.nanoTime();
        log.info("Start programmingPlagiarismDetectionService.checkPlagiarism for exercise {}", exerciseId);
        var plagiarismResult = programmingPlagiarismDetectionService.checkPlagiarism(exerciseId, similarityThreshold, minimumScore);
        log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons in {}", plagiarismResult.getComparisons().size(),
                TimeLogUtil.formatDurationFrom(start));
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return ResponseEntity.ok(plagiarismResult);
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-check : Uses JPlag to check for plagiarism and returns the generated output as zip file
     *
     * @param exerciseId          The ID of the programming exercise for which the plagiarism check should be executed
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    @GetMapping(value = CHECK_PLAGIARISM_JPLAG_REPORT, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasRole('EDITOR')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> checkPlagiarismWithJPlagReport(@PathVariable long exerciseId, @RequestParam float similarityThreshold, @RequestParam int minimumScore)
            throws ExitException, IOException {
        log.debug("REST request to check plagiarism for ProgrammingExercise with id: {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.get()
                .getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());
        if (!programmingLanguageFeature.isPlagiarismCheckSupported()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "programmingLanguageNotSupported",
                    "Artemis does not support plagiarism checks for the programming language " + programmingExercise.getProgrammingLanguage())).body(null);
        }

        File zipFile = programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(exerciseId, similarityThreshold, minimumScore);
        if (zipFile == null) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError", "Insufficient amount of comparisons available for comparison."))
                    .body(null);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }
}
