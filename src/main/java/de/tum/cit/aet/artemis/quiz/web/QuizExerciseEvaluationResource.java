package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithStatisticsDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizResultService;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticService;

/**
 * REST controller for evaluating and re-evaluating quiz exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseEvaluationResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseEvaluationResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizResultService quizResultService;

    private final QuizExerciseService quizExerciseService;

    private final ExerciseService exerciseService;

    private final QuizStatisticService quizStatisticService;

    private final Optional<ExamDateApi> examDateApi;

    private final QuizExerciseRepository quizExerciseRepository;

    private final UserRepository userRepository;

    public QuizExerciseEvaluationResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, UserRepository userRepository,
            ExerciseService exerciseService, Optional<ExamDateApi> examDateApi, QuizResultService quizResultService, QuizStatisticService quizStatisticService) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.userRepository = userRepository;
        this.exerciseService = exerciseService;
        this.examDateApi = examDateApi;
        this.quizResultService = quizResultService;
        this.quizStatisticService = quizStatisticService;
    }

    /**
     * POST /quiz-exercises/{exerciseId}/evaluate : Evaluate the quiz exercise
     *
     * @param quizExerciseId the id of the quiz exercise
     * @return ResponseEntity void
     */
    @PostMapping("quiz-exercises/{quizExerciseId}/evaluate")
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<Void> evaluateQuizExercise(@PathVariable Long quizExerciseId) {
        log.debug("REST request to evaluate quiz exercise {}", quizExerciseId);
        var quizExercise = quizExerciseRepository.findByIdElseThrow(quizExerciseId);
        if (!quizExercise.isQuizEnded()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "quizExercise", "quizNotEndedYet", "Quiz hasn't ended yet.")).build();
        }

        quizResultService.evaluateQuizAndUpdateStatistics(quizExerciseId);
        log.debug("Evaluation of quiz exercise {} finished", quizExerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /quiz-exercises/:quizExerciseId/re-evaluate : Re-evaluates an existing quizExercise.
     * <p>
     * 1. reset not allowed changes and set flag updateResultsAndStatistics if a recalculation of results and statistics is necessary
     * 2. save changed quizExercise
     * 3. if flag is set: -> change results if an answer or a question is set invalid -> recalculate statistics and results and save them.
     *
     * @param quizExerciseId the quiz id for the quiz that should be re-evaluated
     * @param quizExercise   the quizExercise to re-evaluate
     * @param files          the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExercise}
     * @return the ResponseEntity with status 200 (OK) , or with status 400 (Bad Request) if the quizExercise is not valid, or with
     *         status 500 (Internal Server Error) if the quizExercise couldn't be re-evaluated
     */
    @PutMapping(value = "quiz-exercises/{quizExerciseId}/re-evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<Void> reEvaluateQuizExercise(@PathVariable Long quizExerciseId, @RequestPart("exercise") @Valid QuizExerciseReEvaluateDTO quizExercise,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
        log.info("REST request to re-evaluate quiz exercise : {}", quizExerciseId);
        QuizExercise originalQuizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);

        if (originalQuizExercise.isExamExercise()) {
            ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
            // Re-evaluation of an exam quiz is only possible if all students finished their exam
            ZonedDateTime latestIndividualExamEndDate = api.getLatestIndividualExamEndDate(originalQuizExercise.getExerciseGroup().getExam());
            if (latestIndividualExamEndDate == null || latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                throw new BadRequestAlertException("The exam of the quiz exercise has not ended yet. Re-evaluation is only allowed after an exam has ended.", ENTITY_NAME,
                        "examOfQuizExerciseNotEnded");
            }
        }
        else if (!originalQuizExercise.isQuizEnded()) {
            throw new BadRequestAlertException("The quiz exercise has not ended yet. Re-evaluation is only allowed after a quiz has ended.", ENTITY_NAME, "quizExerciseNotEnded");
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();

        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;

        originalQuizExercise = quizExerciseService.reEvaluate(quizExercise, originalQuizExercise, nullsafeFiles);
        exerciseService.logUpdate(originalQuizExercise, originalQuizExercise.getCourseViaExerciseGroupOrCourseMember(), user);

        originalQuizExercise.validateScoreSettings();
        return ResponseEntity.ok().build();
    }

    /**
     * GET /quiz-exercises/:quizExerciseId/recalculate-statistics : recalculate all statistics in case something went wrong with them
     *
     * @param quizExerciseId the id of the quizExercise for which the statistics should be recalculated
     * @return the ResponseEntity with status 200 (OK) and with body the quizExercise, or with status 404 (Not Found)
     */
    @GetMapping("quiz-exercises/{quizExerciseId}/recalculate-statistics")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "quizExerciseId")
    public ResponseEntity<QuizExerciseWithStatisticsDTO> recalculateStatistics(@PathVariable Long quizExerciseId) {
        log.info("REST request to recalculate quiz statistics : {}", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        quizStatisticService.recalculateStatistics(quizExercise);
        // fetch the quiz exercise again to make sure the latest changes are included
        quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        QuizExerciseWithStatisticsDTO quizExerciseDTO = QuizExerciseWithStatisticsDTO.of(quizExercise);
        return ResponseEntity.ok(quizExerciseDTO);
    }

}
