package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.scheduled.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class QuizSubmissionService {

    private final Logger log = LoggerFactory.getLogger(QuizSubmissionService.class);

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final ResultRepository resultRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizScheduleService quizScheduleService;

    private final ParticipationService participationService;

    private final SubmissionVersionService submissionVersionService;

    private final QuizBatchService quizBatchService;

    private final SubmissionRepository submissionRepository;

    public QuizSubmissionService(QuizSubmissionRepository quizSubmissionRepository, QuizScheduleService quizScheduleService, ResultRepository resultRepository,
            SubmissionVersionService submissionVersionService, QuizExerciseRepository quizExerciseRepository, ParticipationService participationService,
            QuizBatchService quizBatchService, SubmissionRepository submissionRepository) {
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.resultRepository = resultRepository;
        this.quizScheduleService = quizScheduleService;
        this.submissionVersionService = submissionVersionService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationService = participationService;
        this.quizBatchService = quizBatchService;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Submit the given submission for practice
     *
     * @param quizSubmission the submission to submit
     * @param quizExercise   the exercise to submit in
     * @param participation  the participation where the result should be saved
     * @return the result entity
     */
    public Result submitForPractice(QuizSubmission quizSubmission, QuizExercise quizExercise, Participation participation) {
        // update submission properties
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.setSubmissionDate(ZonedDateTime.now());
        // calculate scores
        quizSubmission.calculateAndUpdateScores(quizExercise);
        // save parent submission object
        quizSubmission = quizSubmissionRepository.save(quizSubmission);

        // create result
        Result result = new Result().participation(participation);
        result.setRated(false);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        // save result
        result = resultRepository.save(result);

        // setup result - submission relation
        result.setSubmission(quizSubmission);
        // calculate score and update result accordingly
        result.evaluateSubmission();
        quizSubmission.addResult(result);
        quizSubmission.setParticipation(participation);

        // save submission to set result index column
        quizSubmissionRepository.save(quizSubmission);

        // save result to store score
        resultRepository.save(result);

        // result.participation.exercise.quizQuestions turn into proxy objects after saving, so we need to set it again to prevent problems later on
        result.setParticipation(participation);

        // add result to statistics
        quizScheduleService.addResultForStatisticUpdate(quizExercise.getId(), result);
        log.debug("submit practice quiz finished: {}", quizSubmission);
        return result;
    }

    /**
     * Saves a quiz submission into the hash maps for live quizzes. Submitted quizzes are marked to be saved into the database in the QuizScheduleService
     *
     * @param exerciseId the exerciseID to the corresponding QuizExercise
     * @param quizSubmission the submission which should be saved
     * @param user the user who has initiated the request
     * @param submitted whether the user has pressed the submit button or not
     *
     * @return the updated quiz submission object
     * @throws QuizSubmissionException handles errors, e.g. when the live quiz has already ended, or when the quiz was already submitted before
     */
    public QuizSubmission saveSubmissionForLiveMode(Long exerciseId, QuizSubmission quizSubmission, User user, boolean submitted) throws QuizSubmissionException {
        // TODO: what happens if a user executes this call twice in the same moment (using 2 threads)

        String logText = submitted ? "submit quiz in live mode:" : "save quiz in live mode:";

        long start = System.nanoTime();
        // check if submission is still allowed
        QuizExercise quizExercise = quizScheduleService.getQuizExercise(exerciseId);
        if (quizExercise == null) {
            // Fallback solution
            log.info("Quiz not in QuizScheduleService cache, fetching from DB");
            quizExercise = quizExerciseRepository.findByIdElseThrow(exerciseId);
        }
        log.debug("{}: Received quiz exercise for user {} in quiz {} in {} µs.", logText, user.getLogin(), exerciseId, (System.nanoTime() - start) / 1000);
        if (!quizExercise.isQuizStarted() || quizExercise.isQuizEnded()) {
            throw new QuizSubmissionException("The quiz is not active");
        }

        var batch = quizBatchService.getQuizBatchForStudent(quizExercise, user);

        if (batch.stream().noneMatch(QuizBatch::isSubmissionAllowed)) {
            throw new QuizSubmissionException("The quiz is not active");
        }

        var cachedSubmission = quizScheduleService.getQuizSubmission(exerciseId, user.getLogin());
        if (cachedSubmission.isSubmitted()) {
            // current attempt has not yet been committed to DB, so countByExerciseIdAndStudentId will not pick it up
            throw new QuizSubmissionException("You have already submitted the quiz");
        }

        // TODO: add one additional check: fetch quizSubmission.getId() with the corresponding participation and check that the user of participation is the
        // same as the user who executes this call. This prevents injecting submissions to other users

        // check if user has attempts left for this quiz
        int submissionCount = submissionRepository.countByExerciseIdAndStudentId(exerciseId, user.getId());
        log.debug("{} Counted {} submissions for user {} in quiz {} in {} µs.", logText, submissionCount, user.getLogin(), exerciseId, (System.nanoTime() - start) / 1000);
        if (quizExercise.getAllowedNumberOfAttempts() != null && submissionCount >= quizExercise.getAllowedNumberOfAttempts()) {
            throw new QuizSubmissionException("You have no more attempts at this quiz left");
        }

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }

        // set submission date
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        // save submission to HashMap
        quizScheduleService.updateSubmission(exerciseId, user.getLogin(), quizSubmission);

        log.info("{} Saved quiz submission for user {} in quiz {} after {} µs ", logText, user.getLogin(), exerciseId, (System.nanoTime() - start) / 1000);
        return quizSubmission;
    }

    /**
     * Updates a submission for the exam mode
     *
     * @param quizExercise      the quiz exercise for which the submission for the exam mode should be done
     * @param quizSubmission    the quiz submission includes the submitted answers by the student
     * @param user              the student who wants to submit the quiz during the exam
     * @return                  the updated quiz submission after it has been saved to the database
     */
    public QuizSubmission saveSubmissionForExamMode(QuizExercise quizExercise, QuizSubmission quizSubmission, String user) {
        // update submission properties
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, user);

        if (optionalParticipation.isEmpty()) {
            log.warn("The participation for quiz exercise {}, quiz submission {} and user {} was not found", quizExercise.getId(), quizSubmission.getId(), user);
            // TODO: think of better way to handle failure
            throw new EntityNotFoundException(
                    "Participation for quiz exercise " + quizExercise.getId() + " and quiz submission " + quizSubmission.getId() + " for user " + user + " was not found!");
        }
        StudentParticipation studentParticipation = optionalParticipation.get();
        quizSubmission.setParticipation(studentParticipation);
        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        quizSubmission.setResults(new ArrayList<>());
        quizSubmissionRepository.save(quizSubmission);

        // versioning of submission
        try {
            submissionVersionService.saveVersionForIndividual(quizSubmission, user);
        }
        catch (Exception ex) {
            log.error("Quiz submission version could not be saved", ex);
        }

        log.debug("submit exam quiz finished: {}", quizSubmission);
        return quizSubmission;
    }
}
