package de.tum.in.www1.artemis.service.exam;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.QuizStatisticService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ExamQuizService {

    static final Logger log = LoggerFactory.getLogger(ExamQuizService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizStatisticService quizStatisticService;

    private final ResultService resultService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final SubmissionRepository submissionRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    public ExamQuizService(StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, SubmissionRepository submissionRepository,
            QuizExerciseRepository quizExerciseRepository, QuizStatisticService quizStatisticService, ResultService resultService,
            QuizSubmissionRepository quizSubmissionRepository, SubmittedAnswerRepository submittedAnswerRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizStatisticService = quizStatisticService;
        this.resultService = resultService;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
    }

    /**
     * Evaluate the given quiz exercise by evaluate the submission for each participation (there is only one for each participation in exams)
     * and update the statistics with the generated results.
     * @param quizExerciseId the id of the QuizExercise that should be evaluated
     */
    public void evaluateQuizAndUpdateStatistics(@NotNull Long quizExerciseId) {
        long start = System.nanoTime();
        log.info("Starting quiz evaluation for quiz {}", quizExerciseId);
        // We have to load the questions and statistics so that we can evaluate and update and we also need the participations and submissions that exist for this exercise so that
        // they can be evaluated
        var quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        Set<Result> createdResults = evaluateSubmissions(quizExercise);
        log.info("Quiz evaluation for quiz {} finished after {} with {} created results", quizExercise.getId(), TimeLogUtil.formatDurationFrom(start), createdResults.size());
        quizStatisticService.updateStatistics(createdResults, quizExercise);
        log.info("Statistic update for quiz {} finished after {}", quizExercise.getId(), TimeLogUtil.formatDurationFrom(start));
    }

    /**
     * This method is intended to be called after a user submits a test run. We calculate the achieved score in the quiz exercises immediately and attach a result.
     * Note: We do not insert the result of this test run quiz participation into the quiz statistics.
     * @param studentExam The test run or test exam containing the users participations in all exam exercises
     */
    public void evaluateQuizParticipationsForTestRunAndTestExam(StudentExam studentExam) {
        final var participations = studentExam.getExercises().stream()
                .flatMap(exercise -> exercise.getStudentParticipations().stream().filter(participation -> participation.getExercise() instanceof QuizExercise))
                .collect(Collectors.toSet());
        submittedAnswerRepository.findQuizSubmissionsSubmittedAnswers(participations);
        for (final var participation : participations) {
            var quizExercise = (QuizExercise) participation.getExercise();
            final var optionalExistingSubmission = participation.findLatestSubmission();
            if (optionalExistingSubmission.isPresent()) {
                QuizSubmission quizSubmission = quizSubmissionRepository.findWithEagerResultAndFeedbackById(optionalExistingSubmission.get().getId())
                        .orElseThrow(() -> new EntityNotFoundException("Submission with id \"" + optionalExistingSubmission.get().getId() + "\" does not exist"));
                participation.setExercise(quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId()));
                quizExercise = (QuizExercise) participation.getExercise();
                Result result;
                if (quizSubmission.getLatestResult() == null) {
                    result = new Result();
                    result.setParticipation(participation);
                    result.setAssessmentType(AssessmentType.AUTOMATIC);
                    // set submission to calculate scores
                    result.setSubmission(quizSubmission);
                    // calculate scores and update result and submission accordingly
                    quizSubmission.calculateAndUpdateScores(quizExercise);
                    result.evaluateQuizSubmission();
                    // remove submission to follow save order for ordered collections
                    result.setSubmission(null);
                    if (studentExam.getExam().isTestExam()) {
                        result.rated(true);
                    }
                    result = resultRepository.save(result);
                    participation.setResults(Set.of(result));
                    studentParticipationRepository.save(participation);
                    result.setSubmission(quizSubmission);
                    quizSubmission.addResult(result);
                }
                else {
                    result = quizSubmission.getLatestResult();
                    // set submission to calculate scores
                    result.setSubmission(quizSubmission);
                    // calculate scores and update result and submission accordingly
                    quizSubmission.calculateAndUpdateScores(quizExercise);
                    // prevent a lazy exception in the evaluateQuizSubmission method
                    result.setParticipation(participation);
                    result.evaluateQuizSubmission();
                    if (studentExam.getExam().isTestExam()) {
                        result.rated(true);
                    }
                    resultRepository.save(result);
                }
                if (studentExam.getExam().isTestExam()) {
                    // In case of an test exam, the quiz statistic should also be updated
                    var quizExercise1 = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
                    quizStatisticService.updateStatistics(Set.of(result), quizExercise1);
                }
                submissionRepository.save(quizSubmission);
            }
        }
    }

    /**
     * // @formatter:off
     * Evaluate the given quiz exercise by performing the following actions for each participation:
     * 1. Get the submission for each participation (there should be only one as in exam mode, the submission gets created upfront and will be updated)
     * - If no submission is found, print a warning and continue as we cannot evaluate that submission
     * - If more than one submission is found, select one of them
     * 2. mark submission and participation as evaluated
     * 3. Create a new result for the selected submission and calculate scores
     * 4. Save the updated submission & participation and the newly created result
     *
     * After processing all participations, the created results will be returned for further processing
     * Note: We ignore test run participations
     * // @formatter:on
     * @param quizExercise the id of the QuizExercise that should be evaluated
     * @return the newly generated results
     */
    private Set<Result> evaluateSubmissions(@NotNull QuizExercise quizExercise) {
        Set<Result> createdResults = new HashSet<>();
        List<StudentParticipation> studentParticipations = studentParticipationRepository.findAllWithEagerLegalSubmissionsAndEagerResultsByExerciseId(quizExercise.getId());
        submittedAnswerRepository.findQuizSubmissionsSubmittedAnswers(studentParticipations);

        for (var participation : studentParticipations) {
            if (!participation.isTestRun()) {
                try {
                    // reconnect so that the quiz questions are available later on (otherwise there will be a org.hibernate.LazyInitializationException)
                    participation.setExercise(quizExercise);
                    Set<Submission> submissions = participation.getSubmissions();
                    QuizSubmission quizSubmission;
                    if (submissions.isEmpty()) {
                        log.warn("Found no submissions for participation {} (Participant {}) in quiz {}", participation.getId(), participation.getParticipant().getName(),
                                quizExercise.getId());
                        continue;
                    }
                    else if (submissions.size() > 1) {
                        log.warn("Found multiple ({}) submissions for participation {} (Participant {}) in quiz {}, taking the one with highest id", submissions.size(),
                                participation.getId(), participation.getParticipant().getName(), quizExercise.getId());
                        List<Submission> submissionsList = new ArrayList<>(submissions);

                        // Load submission with highest id
                        submissionsList.sort(Comparator.comparing(Submission::getId).reversed());
                        quizSubmission = (QuizSubmission) submissionsList.get(0);
                    }
                    else {
                        quizSubmission = (QuizSubmission) submissions.iterator().next();
                    }

                    participation.setInitializationState(InitializationState.FINISHED);

                    boolean resultExisting = false;
                    // create new result if none is existing
                    Result result;
                    if (participation.getResults().isEmpty()) {
                        result = new Result().participation(participation);
                    }
                    else {
                        resultExisting = true;
                        result = participation.getResults().iterator().next();
                    }
                    // Only create Results once after the first evaluation
                    if (!resultExisting) {
                        // delete result from quizSubmission, to be able to set a new one
                        if (quizSubmission.getLatestResult() != null) {
                            resultService.deleteResultWithComplaint(quizSubmission.getLatestResult().getId());
                        }
                        result.setRated(true);
                        result.setAssessmentType(AssessmentType.AUTOMATIC);
                        result.setCompletionDate(ZonedDateTime.now());

                        // set submission to calculate scores
                        result.setSubmission(quizSubmission);
                        // calculate scores and update result and submission accordingly
                        quizSubmission.calculateAndUpdateScores(quizExercise);
                        result.evaluateQuizSubmission();
                        // remove submission to follow save order for ordered collections
                        result.setSubmission(null);

                        // NOTE: we save participation, submission and result here individually so that one exception (e.g. duplicated key) cannot destroy multiple student answers
                        submissionRepository.save(quizSubmission);
                        result = resultRepository.save(result);

                        // add result to participation
                        participation.addResult(result);
                        studentParticipationRepository.save(participation);

                        // add result to submission
                        result.setSubmission(quizSubmission);
                        quizSubmission.addResult(result);
                        submissionRepository.save(quizSubmission);

                        // Add result so that it can be returned (and processed later)
                        createdResults.add(result);
                    }
                }
                catch (Exception e) {
                    log.error("Exception in evaluateExamQuizExercise() for user {} in quiz {}: {}", participation.getParticipantIdentifier(), quizExercise.getId(), e.getMessage(),
                            e);
                }
            }

        }
        return createdResults;
    }
}
