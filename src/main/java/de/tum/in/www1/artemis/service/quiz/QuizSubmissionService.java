package de.tum.in.www1.artemis.service.quiz;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.AbstractQuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;
import de.tum.in.www1.artemis.exception.QuizSubmissionException;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmittedAnswerRepository;
import de.tum.in.www1.artemis.service.AbstractQuizSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.SubmissionVersionService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Service
public class QuizSubmissionService extends AbstractQuizSubmissionService<QuizSubmission> {

    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionService.class);

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final ResultRepository resultRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ParticipationService participationService;

    private final QuizBatchService quizBatchService;

    private final QuizStatisticService quizStatisticService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    public QuizSubmissionService(QuizSubmissionRepository quizSubmissionRepository, ResultRepository resultRepository, SubmissionVersionService submissionVersionService,
            QuizExerciseRepository quizExerciseRepository, ParticipationService participationService, QuizBatchService quizBatchService, QuizStatisticService quizStatisticService,
            StudentParticipationRepository studentParticipationRepository, WebsocketMessagingService websocketMessagingService,
            SubmittedAnswerRepository submittedAnswerRepository) {
        super(submissionVersionService);
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.resultRepository = resultRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationService = participationService;
        this.quizBatchService = quizBatchService;
        this.quizStatisticService = quizStatisticService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.submittedAnswerRepository = submittedAnswerRepository;
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
        quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
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
        result.evaluateQuizSubmission();
        quizSubmission.addResult(result);
        quizSubmission.setParticipation(participation);

        // save submission to set result index column
        quizSubmissionRepository.save(quizSubmission);

        // save result to store score
        resultRepository.save(result);

        // result.participation.exercise.quizQuestions turn into proxy objects after saving, so we need to set it again to prevent problems later on
        result.setParticipation(participation);

        // add result to statistics
        quizStatisticService.recalculateStatistics(quizExercise);

        log.debug("submit practice quiz finished: {}", quizSubmission);
        return result;
    }

    /**
     * Calculate the results for all participations of the given quiz exercise
     *
     * @param quizExerciseId the id of the quiz exercise for which the results should be calculated
     */
    public void calculateAllResults(long quizExerciseId) {
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        log.info("Calculating results for quiz {}", quizExercise.getId());
        studentParticipationRepository.findByExerciseId(quizExercise.getId()).forEach(participation -> {
            participation.setExercise(quizExercise);
            Optional<QuizSubmission> quizSubmissionOptional = quizSubmissionRepository.findWithEagerSubmittedAnswersByParticipationId(participation.getId());

            if (quizSubmissionOptional.isEmpty()) {
                return;
            }
            QuizSubmission quizSubmission = quizSubmissionOptional.get();

            if (quizSubmission.isSubmitted()) {
                if (quizSubmission.getType() == null) {
                    quizSubmission.setType(SubmissionType.MANUAL);
                }
            }
            else if (quizExercise.isQuizEnded()) {
                quizSubmission.setSubmitted(true);
                quizSubmission.setType(SubmissionType.TIMEOUT);
                quizSubmission.setSubmissionDate(ZonedDateTime.now());
            }

            participation.setInitializationState(InitializationState.FINISHED);

            Result result = new Result().participation(participation);
            result.setRated(true);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(quizSubmission.getSubmissionDate());
            result.setSubmission(quizSubmission);

            quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
            result.evaluateQuizSubmission();

            quizSubmissionRepository.save(quizSubmission);
            resultRepository.save(result);
            studentParticipationRepository.save(participation);

            // avoid LazyInitializationException
            participation.setResults(Set.of(result));

            sendQuizResultToUser(quizExerciseId, participation);
        });
        quizStatisticService.recalculateStatistics(quizExercise);
        // notify users via websocket about new results for the statistics, filter out solution information
        quizExercise.filterForStatisticWebsocket();
        websocketMessagingService.sendMessage("/topic/statistic/" + quizExercise.getId(), quizExercise);
    }

    private void sendQuizResultToUser(long quizExerciseId, StudentParticipation participation) {
        // TODO: we should convert this into a DTO instead of removing data from the entity
        var user = participation.getParticipantIdentifier();
        removeUnnecessaryObjectsBeforeSendingToClient(participation);
        websocketMessagingService.sendMessageToUser(user, "/topic/exercise/" + quizExerciseId + "/participation", participation);
    }

    private void removeUnnecessaryObjectsBeforeSendingToClient(StudentParticipation participation) {
        if (participation.getExercise() != null) {
            var quizExercise = (QuizExercise) participation.getExercise();
            // we do not need the course and lectures
            quizExercise.setCourse(null);
            // students should not see statistics
            // TODO: this would be useful, but leads to problems when the quiz schedule service wants to access the statistics again later on
            // quizExercise.setQuizPointStatistic(null);
            // quizExercise.getQuizQuestions().forEach(quizQuestion -> quizQuestion.setQuizQuestionStatistic(null));
        }
        // submissions are part of results, so we do not need them twice
        participation.setSubmissions(null);
        participation.setParticipant(null);
        if (participation.getResults() != null && !participation.getResults().isEmpty()) {
            QuizSubmission quizSubmission = (QuizSubmission) participation.getResults().iterator().next().getSubmission();
            if (quizSubmission != null && quizSubmission.getSubmittedAnswers() != null) {
                for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                    if (submittedAnswer.getQuizQuestion() != null) {
                        // we do not need all information of the questions again, they are already stored in the exercise
                        var question = submittedAnswer.getQuizQuestion();
                        submittedAnswer.setQuizQuestion(question.copyQuestionId());
                    }
                }
            }
        }
    }

    /**
     * Saves a quiz submission into the hash maps for live quizzes. Submitted quizzes are marked to be saved into the database in the QuizScheduleService TODO: Update docs
     *
     * @param exerciseId     the exerciseID to the corresponding QuizExercise
     * @param quizSubmission the submission which should be saved
     * @param userLogin      the login of the user who has initiated the request
     * @param submitted      whether the user has pressed the submit button or not
     * @return the updated quiz submission object
     * @throws QuizSubmissionException handles errors, e.g. when the live quiz has already ended, or when the quiz was already submitted before
     */
    public QuizSubmission saveSubmissionForLiveMode(Long exerciseId, QuizSubmission quizSubmission, String userLogin, boolean submitted) throws QuizSubmissionException {

        String logText = submitted ? "submit quiz in live mode:" : "save quiz in live mode:";

        long start = System.nanoTime();
        var quizExercise = quizExerciseRepository.findByIdElseThrow(exerciseId);
        quizExercise.setQuizBatches(null);
        // A submission always exists because the user has to start the participation before submitting, which creates a submission
        var existingSubmission = quizSubmissionRepository.findByExerciseIdAndStudentLogin(quizExercise.getId(), userLogin)
                .orElseThrow(() -> new EntityNotFoundException("Cannot find quiz submission for exercise " + exerciseId + " and user " + userLogin));
        checkSubmissionForLiveModeOrThrow(quizExercise, existingSubmission, userLogin, logText, start);

        // TODO: ideally we only save if something has changed, we can use "if (!isContentEqualTo(existingSubmission, quizSubmission))"

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }
        // make sure certain values are not overridden wrongly
        quizSubmission.setId(existingSubmission.getId());
        quizSubmission.setQuizBatch(existingSubmission.getQuizBatch());

        // set submission date and link to participation
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        // make sure the participation is not overridden wrongly
        var participation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, userLogin).orElseThrow();
        quizSubmission.setParticipation(participation);
        quizSubmission = quizSubmissionRepository.save(quizSubmission);
        log.info("{} Saved quiz submission for user {} in quiz {} after {} ", logText, userLogin, exerciseId, TimeLogUtil.formatDurationFrom(start));

        return quizSubmission;
    }

    /**
     * Check that the user is allowed to currently submit to the specified exercise and throws an exception if not
     */
    private void checkSubmissionForLiveModeOrThrow(QuizExercise quizExercise, QuizSubmission existingSubmission, String userLogin, String logText, long start)
            throws QuizSubmissionException {
        // check if submission is still allowed
        log.debug("{}: Received quiz exercise for user {} in quiz {} in {} µs.", logText, userLogin, quizExercise.getId(), (System.nanoTime() - start) / 1000);
        if (!quizExercise.isQuizStarted() || quizExercise.isQuizEnded()) {
            throw new QuizSubmissionException("The quiz is not active");
        }

        if (existingSubmission.isSubmitted()) {
            // the old submission has not yet been processed, so don't allow a new one yet
            throw new QuizSubmissionException("You have already submitted the quiz");
        }

        if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            // the batch exists if the quiz is active, otherwise a new inactive batch is returned
            if (!quizBatchService.getOrCreateSynchronizedQuizBatch(quizExercise).isSubmissionAllowed()) {
                throw new QuizSubmissionException("The quiz is not active");
            }

        }
        else {
            // in the other modes the resubmission checks are done at join time and the student-batch association is removed when processing a submission
            var batch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, userLogin);

            // there is no way of distinguishing these two error cases without an extra db query
            if (batch.isEmpty()) {
                throw new QuizSubmissionException("You did not join or have already submitted the quiz");
            }

            if (!batch.get().isSubmissionAllowed()) {
                throw new QuizSubmissionException("The quiz is not active");
            }
        }

        // TODO: add additional checks that may be beneficial
        // for example it is possible for students that are not members of the course to submit the quiz
        // but for performance reasons the checks may have to be done in the quiz submission service where no feedback for the students can be generated
    }

    /**
     * Returns true if student has submitted at least once for the given quiz batch
     *
     * @param quizBatch the quiz batch of interest to check if submission exists
     * @param login     the student of interest to check if submission exists
     * @return boolean the submission status of student for the given quiz batch
     */
    public boolean hasUserSubmitted(QuizBatch quizBatch, String login) {
        Set<QuizSubmission> submissions = quizSubmissionRepository.findAllByQuizBatchAndStudentLogin(quizBatch.getId(), login);
        Optional<QuizSubmission> submission = submissions.stream().findFirst();
        return submission.map(QuizSubmission::isSubmitted).orElse(false);
    }

    /**
     * Find StudentParticipation of the given quizExercise that was done by the given user
     *
     * @param quizExercise   the QuizExercise of which the StudentParticipation belongs to
     * @param quizSubmission the AbstractQuizSubmission of which the participation to be set to
     * @param user           the User of the StudentParticipation
     * @return StudentParticipation the participation if exists, otherwise throw entity not found exception
     */
    protected StudentParticipation getParticipation(QuizExercise quizExercise, AbstractQuizSubmission quizSubmission, User user) {
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, user.getLogin());

        if (optionalParticipation.isEmpty()) {
            log.warn("The participation for quiz exercise {}, quiz submission {} and user {} was not found", quizExercise.getId(), quizSubmission.getId(), user.getLogin());
            // TODO: think of better way to handle failure
            throw new EntityNotFoundException("Participation for quiz exercise " + quizExercise.getId() + " and quiz submission " + quizSubmission.getId() + " for user "
                    + user.getLogin() + " was not found!");
        }
        return optionalParticipation.get();
    }

    /**
     * Set the participation of the quiz submission and then save the quiz submission to the database
     *
     * @param quizExercise   the QuizExercise of the participation of which the given quizSubmission belongs to
     * @param quizSubmission the QuizSubmission to be saved
     * @param user           the User of the participation of which the given quizSubmission belongs to
     * @return saved QuizSubmission
     */
    @Override
    protected QuizSubmission save(QuizExercise quizExercise, QuizSubmission quizSubmission, User user) {
        quizSubmission.setParticipation(this.getParticipation(quizExercise, quizSubmission, user));
        return quizSubmissionRepository.save(quizSubmission);
    }
}
