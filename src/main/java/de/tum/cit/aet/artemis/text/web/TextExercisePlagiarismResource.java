package de.tum.cit.aet.artemis.text.web;

import static de.tum.cit.aet.artemis.plagiarism.web.PlagiarismResultResponseBuilder.buildPlagiarismResultResponse;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.jplag.exceptions.ExitException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismDetectionApi;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismResultApi;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismResultDTO;
import de.tum.cit.aet.artemis.plagiarism.exception.PlagiarismApiNotPresentException;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

/**
 * REST controller for handling plagiarism detection and results for Text Exercises.
 */
@Conditional(TextEnabled.class)
@Lazy
@RestController
@RequestMapping("api/text/")
public class TextExercisePlagiarismResource {

    private static final Logger log = LoggerFactory.getLogger(TextExercisePlagiarismResource.class);

    private final AuthorizationCheckService authCheckService;

    private final Optional<PlagiarismResultApi> plagiarismResultApi;

    private final Optional<PlagiarismDetectionApi> plagiarismDetectionApi;

    private final TextExerciseRepository textExerciseRepository;

    public TextExercisePlagiarismResource(TextExerciseRepository textExerciseRepository, Optional<PlagiarismResultApi> plagiarismResultApi,
            AuthorizationCheckService authCheckService, Optional<PlagiarismDetectionApi> plagiarismDetectionApi) {
        this.plagiarismResultApi = plagiarismResultApi;
        this.textExerciseRepository = textExerciseRepository;
        this.authCheckService = authCheckService;
        this.plagiarismDetectionApi = plagiarismDetectionApi;
    }

    /**
     * GET /text-exercises/{exerciseId}/plagiarism-result
     * <p>
     * Return the latest plagiarism result or null, if no plagiarism was detected for this exercise
     * yet.
     *
     * @param exerciseId ID of the text exercise for which the plagiarism result should be returned
     * @return The ResponseEntity with status 200 (Ok) or with status 400 (Bad Request) if the
     *         parameters are invalid
     */
    @GetMapping("text-exercises/{exerciseId}/plagiarism-result")
    @EnforceAtLeastEditor
    public ResponseEntity<PlagiarismResultDTO> getPlagiarismResult(@PathVariable long exerciseId) {
        log.debug("REST request to get the latest plagiarism result for the text exercise with id: {}", exerciseId);
        PlagiarismResultApi api = plagiarismResultApi.orElseThrow(() -> new PlagiarismApiNotPresentException(PlagiarismResultApi.class));

        TextExercise textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);
        var plagiarismResult = api.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        api.prepareResultForClient(plagiarismResult);
        return buildPlagiarismResultResponse(plagiarismResult);
    }

    /**
     * GET /text-exercises/{exerciseId}/check-plagiarism
     * <p>
     * Start the automated plagiarism detection for the given exercise and return its result.
     *
     * @param exerciseId          ID of the exercise for which to detect plagiarism
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (in % between 0 and 100)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @param minimumSize         consider only submissions whose size is greater or equal to this value
     * @return the ResponseEntity with status 200 (OK) and the list of at most 500 pair-wise submissions with a similarity above the given threshold (e.g. 50%).
     */
    @GetMapping("text-exercises/{exerciseId}/check-plagiarism")
    @FeatureToggle(Feature.PlagiarismChecks)
    @EnforceAtLeastEditor
    public ResponseEntity<PlagiarismResultDTO> checkPlagiarism(@PathVariable long exerciseId, @RequestParam int similarityThreshold, @RequestParam int minimumScore,
            @RequestParam int minimumSize) throws ExitException {
        PlagiarismDetectionApi api = plagiarismDetectionApi.orElseThrow(() -> new PlagiarismApiNotPresentException(PlagiarismDetectionApi.class));

        TextExercise textExercise = textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);

        long start = System.nanoTime();
        log.info("Started manual plagiarism checks for text exercise: exerciseId={}.", exerciseId);
        PlagiarismDetectionConfigHelper.updateWithTemporaryParameters(textExercise, similarityThreshold, minimumScore, minimumSize);
        var plagiarismResult = api.checkTextExercise(textExercise);
        log.info("Finished manual plagiarism checks for text exercise: exerciseId={}, elapsed={}.", exerciseId, TimeLogUtil.formatDurationFrom(start));
        return buildPlagiarismResultResponse(plagiarismResult);
    }
}
