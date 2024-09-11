package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.plagiarism.web.PlagiarismResultResponseBuilder.buildPlagiarismResultResponse;

import java.io.FileInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.jplag.exceptions.ExitException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextPlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismDetectionService;
import de.tum.cit.aet.artemis.plagiarism.service.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.web.rest.dto.plagiarism.PlagiarismResultDTO;

/**
 * REST controller for managing ProgrammingExercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExercisePlagiarismResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExercisePlagiarismResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    private final PlagiarismDetectionService plagiarismDetectionService;

    public ProgrammingExercisePlagiarismResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            PlagiarismResultRepository plagiarismResultRepository, PlagiarismDetectionService plagiarismDetectionService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.plagiarismResultRepository = plagiarismResultRepository;
        this.plagiarismDetectionService = plagiarismDetectionService;
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-result : Return the latest plagiarism result or null, if no plagiarism was detected for this exercise yet.
     *
     * @param exerciseId ID of the programming exercise for which the plagiarism result should be returned
     * @return The ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the parameters are invalid
     */
    @GetMapping("programming-exercises/{exerciseId}/plagiarism-result")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<PlagiarismResultDTO<TextPlagiarismResult>> getPlagiarismResult(@PathVariable long exerciseId) {
        log.debug("REST request to get the latest plagiarism result for the programming exercise with id: {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        var plagiarismResult = (TextPlagiarismResult) plagiarismResultRepository.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(programmingExercise.getId());
        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
        return buildPlagiarismResultResponse(plagiarismResult);
    }

    /**
     * GET /programming-exercises/{exerciseId}/check-plagiarism : Start the automated plagiarism detection for the given exercise and return its result.
     *
     * @param exerciseId          The ID of the programming exercise for which the plagiarism check should be executed
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @param minimumSize         consider only submissions whose number of diff to template lines is greate or equal to this value
     * @return the ResponseEntity with status 200 (OK) and the list of at most 500 pair-wise submissions with a similarity above the given threshold (e.g. 50%).
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    @GetMapping("programming-exercises/{exerciseId}/check-plagiarism")
    @EnforceAtLeastEditor
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.PlagiarismChecks })
    public ResponseEntity<PlagiarismResultDTO<TextPlagiarismResult>> checkPlagiarism(@PathVariable long exerciseId, @RequestParam int similarityThreshold,
            @RequestParam int minimumScore, @RequestParam int minimumSize) throws ExitException, IOException {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        long start = System.nanoTime();
        log.info("Started manual plagiarism checks for programming exercise: exerciseId={}.", exerciseId);
        PlagiarismDetectionConfigHelper.updateWithTemporaryParameters(programmingExercise, similarityThreshold, minimumScore, minimumSize);
        try {
            var plagiarismResult = (TextPlagiarismResult) plagiarismDetectionService.checkProgrammingExercise(programmingExercise);
            return buildPlagiarismResultResponse(plagiarismResult);
        }
        catch (ProgrammingLanguageNotSupportedForPlagiarismDetectionException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "programmingLanguageNotSupported");
        }
        finally {
            log.info("Finished manual plagiarism checks for programming exercise: exerciseId={}, elapsed={}.", exerciseId, TimeLogUtil.formatDurationFrom(start));
        }
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-check : Uses JPlag to check for plagiarism and returns the generated output as zip file
     *
     * @param exerciseId          The ID of the programming exercise for which the plagiarism check should be executed
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @param minimumSize         consider only submissions whose number of diff to template lines is greate or equal to this value
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     * @throws IOException is thrown for file handling errors
     */
    @GetMapping(value = "programming-exercises/{exerciseId}/check-plagiarism-jplag-report")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> checkPlagiarismWithJPlagReport(@PathVariable long exerciseId, @RequestParam int similarityThreshold, @RequestParam int minimumScore,
            @RequestParam int minimumSize) throws IOException {
        log.debug("REST request to check plagiarism for ProgrammingExercise with id: {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        long start = System.nanoTime();
        log.info("Started manual plagiarism checks with Jplag report for programming exercise: exerciseId={}.", exerciseId);
        PlagiarismDetectionConfigHelper.updateWithTemporaryParameters(programmingExercise, similarityThreshold, minimumScore, minimumSize);
        try {
            var zipFile = plagiarismDetectionService.checkProgrammingExerciseWithJplagReport(programmingExercise);
            if (zipFile == null) {
                throw new BadRequestAlertException("Insufficient amount of valid and long enough submissions available for comparison.", "Plagiarism Check",
                        "notEnoughSubmissions");
            }

            var resource = new InputStreamResource(new FileInputStream(zipFile));
            return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
        }
        catch (ProgrammingLanguageNotSupportedForPlagiarismDetectionException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "programmingLanguageNotSupported");
        }
        finally {
            log.info("Finished manual plagiarism checks with Jplag report for programming exercise: exerciseId={}, elapsed={}.", exerciseId, TimeLogUtil.formatDurationFrom(start));
        }
    }
}
