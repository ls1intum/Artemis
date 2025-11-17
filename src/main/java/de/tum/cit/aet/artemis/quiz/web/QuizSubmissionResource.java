package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.QuizSubmissionException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.ExamSubmissionApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.result.ResultAfterEvaluationWithSubmissionDTO;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromStudentDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;

/**
 * REST controller for managing QuizSubmission.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionResource.class);

    private static final String ENTITY_NAME = "quizSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserRepository userRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizSubmissionService quizSubmissionService;

    private final ParticipationService participationService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultWebsocketService resultWebsocketService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ExamSubmissionApi> examSubmissionApi;

    public QuizSubmissionResource(QuizExerciseRepository quizExerciseRepository, QuizSubmissionService quizSubmissionService, ParticipationService participationService,
            ResultWebsocketService resultWebsocketService, UserRepository userRepository, AuthorizationCheckService authCheckService, Optional<ExamSubmissionApi> examSubmissionApi,
            StudentParticipationRepository studentParticipationRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizSubmissionService = quizSubmissionService;
        this.participationService = participationService;
        this.resultWebsocketService = resultWebsocketService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.examSubmissionApi = examSubmissionApi;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * TODO: Decide if we want to use this endpoint for both submit and save. If so, we may want to use PUT instead of POST
     * TODO: Don't trust the user submitted values
     * POST /exercises/:exerciseId/submissions/live : Submit a new quizSubmission for live mode.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @param submit         flag to determine if the submission should be submitted or saved
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/submissions/live")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<QuizSubmission> saveOrSubmitForLiveMode(@PathVariable Long exerciseId, @Valid @RequestBody QuizSubmission quizSubmission,
            @RequestParam(name = "submit", defaultValue = "false") boolean submit) {
        log.debug("REST request to save or submit QuizSubmission for live mode : {}", quizSubmission);
        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow();
        try {
            // we set the submitted flag on the server side
            quizSubmission.setSubmitted(submit);
            QuizSubmission updatedQuizSubmission = quizSubmissionService.saveSubmissionForLiveMode(exerciseId, quizSubmission, userLogin, submit);
            return ResponseEntity.ok(updatedQuizSubmission);
        }
        catch (QuizSubmissionException e) {
            log.warn("QuizSubmissionException: {} for user {} in quiz {}", e.getMessage(), userLogin, exerciseId);
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "quizSubmissionError", e.getMessage())).body(null);
        }
    }

    /**
     * POST /exercises/:exerciseId/submissions/practice : Submit a new quizSubmission for practice mode.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/submissions/practice")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<ResultAfterEvaluationWithSubmissionDTO> submitForPractice(@PathVariable Long exerciseId,
            @Valid @RequestBody QuizSubmissionFromStudentDTO quizSubmission) {
        log.debug("REST request to submit QuizSubmission for practice : {}", quizSubmission);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAllowedToSeeCourseExercise(quizExercise, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "Forbidden", "You are not allowed to participate in this exercise.")).body(null);
        }

        // Note that exam quiz exercises do not have an end date, so we need to check in that order
        if (!Boolean.TRUE.equals(quizExercise.isIsOpenForPractice()) || !quizExercise.isQuizEnded()) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotOpenForPractice", "The exercise is not open for practice or hasn't ended yet."))
                    .body(null);
        }

        QuizSubmission convertedSubmission = quizSubmissionService.createNewSubmissionFromDTO(quizSubmission, quizExercise);

        // the following method either reuses an existing participation or creates a new one
        StudentParticipation participation = participationService.startExercise(quizExercise, user, false);
        // we set the exercise again to prevent issues with lazy loaded quiz questions
        participation.setExercise(quizExercise);

        // update and save submission
        Result result = quizSubmissionService.submitForPractice(convertedSubmission, quizExercise, participation);
        // The quizScheduler is usually responsible for updating the participation to FINISHED in the database. If quizzes where the student did not participate are used for
        // practice, the QuizScheduler does not update the participation, that's why we update it manually here
        participation.setInitializationState(InitializationState.FINISHED);
        studentParticipationRepository.saveAndFlush(participation);

        // remove some redundant or unnecessary data that is not needed on client side
        for (SubmittedAnswer answer : convertedSubmission.getSubmittedAnswers()) {
            answer.getQuizQuestion().setQuizQuestionStatistic(null);
        }

        quizExercise.setQuizPointStatistic(null);

        resultWebsocketService.broadcastNewResult(result.getSubmission().getParticipation(), result);

        quizExercise.setCourse(null);
        result.getSubmission().setResults(null);
        result.getSubmission().setParticipation(participation);
        // return result with quizSubmission, participation and quiz exercise (including the solution)
        ResultAfterEvaluationWithSubmissionDTO resultAfterEvaluationDTO = ResultAfterEvaluationWithSubmissionDTO.of(result);
        return ResponseEntity.ok(resultAfterEvaluationDTO);
    }

    /**
     * POST /exercises/:exerciseId/submissions/preview : Submit a new quizSubmission for preview mode. Note that in this case, nothing will be saved in database.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param quizSubmission the quizSubmission to submit
     * @return the ResponseEntity with status 200 and body the result or the appropriate error code.
     */
    @PostMapping("exercises/{exerciseId}/submissions/preview")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<ResultAfterEvaluationWithSubmissionDTO> submitForPreview(@PathVariable Long exerciseId, @Valid @RequestBody QuizSubmissionFromStudentDTO quizSubmission) {
        log.debug("REST request to submit QuizSubmission for preview : {}", quizSubmission);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exerciseId);
        QuizSubmission convertedSubmission = quizSubmissionService.createNewSubmissionFromDTO(quizSubmission, quizExercise);
        StudentParticipation fakeParticipation = new StudentParticipation();
        fakeParticipation.setExercise(quizExercise);

        // update submission
        convertedSubmission.setSubmitted(true);
        convertedSubmission.setType(SubmissionType.MANUAL);
        convertedSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());

        // create result
        Result result = new Result().submission(convertedSubmission);
        result.setRated(false);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        // calculate score and update result accordingly
        result.evaluateQuizSubmission(quizExercise);

        result.getSubmission().setResults(null);
        result.getSubmission().setParticipation(fakeParticipation);

        ResultAfterEvaluationWithSubmissionDTO resultAfterEvaluationDTO = ResultAfterEvaluationWithSubmissionDTO.of(result);
        return ResponseEntity.ok(resultAfterEvaluationDTO);
    }

    /**
     * PUT /exercises/:exerciseId/submissions/exam : Update a QuizSubmission for exam mode
     *
     * @param exerciseId     the id of the exercise for which to update the submission
     * @param quizSubmission the quizSubmission to update
     * @return the ResponseEntity with status 200 and body the result or the appropriate error code.
     */
    @PutMapping("exercises/{exerciseId}/submissions/exam")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<QuizSubmission> submitQuizForExam(@PathVariable Long exerciseId, @Valid @RequestBody QuizSubmission quizSubmission) {
        long start = System.currentTimeMillis();
        log.debug("REST request to submit QuizSubmission for exam : {}", quizSubmission);

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        QuizExercise quizExercise = quizExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (quizExercise.isExamExercise()) {
            ExamSubmissionApi api = examSubmissionApi.orElseThrow(() -> new ExamApiNotPresentException(ExamSubmissionApi.class));

            // Apply further checks if it is an exam submission
            api.checkSubmissionAllowanceElseThrow(quizExercise, user);

            // Prevent multiple submissions (currently only for exam submissions)
            quizSubmission = (QuizSubmission) api.preventMultipleSubmissions(quizExercise, quizSubmission, user);
        }

        QuizSubmission updatedQuizSubmission = quizSubmissionService.saveSubmissionForExamMode(quizExercise, quizSubmission, user);
        long end = System.currentTimeMillis();
        log.info("submitQuizForExam took {}ms for exercise {} and user {}", end - start, exerciseId, user.getLogin());
        return ResponseEntity.ok(updatedQuizSubmission);
    }
}
