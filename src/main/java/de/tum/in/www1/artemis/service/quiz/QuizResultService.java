package de.tum.in.www1.artemis.service.quiz;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.SubmittedAnswerRepository;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

@Profile(PROFILE_CORE)
@Service
public class QuizResultService {

    private static final Logger log = LoggerFactory.getLogger(QuizResultService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizStatisticService quizStatisticService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final ResultService resultService;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    public QuizResultService(QuizExerciseRepository quizExerciseRepository, QuizStatisticService quizStatisticService,
            StudentParticipationRepository studentParticipationRepository, SubmittedAnswerRepository submittedAnswerRepository, ResultService resultService,
            SubmissionRepository submissionRepository, ResultRepository resultRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizStatisticService = quizStatisticService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.resultService = resultService;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Evaluate the given quiz exercise by evaluate the submission for each participation (there is only one for each participation in exams)
     * and update the statistics with the generated results.
     *
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
        if (quizExercise.getQuizPointStatistic() == null) {
            quizStatisticService.recalculateStatistics(quizExercise);
        }
        quizStatisticService.updateStatistics(createdResults, quizExercise);
        log.info("Statistic update for quiz {} finished after {}", quizExercise.getId(), TimeLogUtil.formatDurationFrom(start));
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
     * <p>
     * After processing all participations, the created results will be returned for further processing
     * Note: We ignore test run participations
     * // @formatter:on
     *
     * @param quizExercise the id of the QuizExercise that should be evaluated
     * @return the newly generated results
     */
    private Set<Result> evaluateSubmissions(@NotNull QuizExercise quizExercise) {
        Set<Result> createdResults = new HashSet<>();
        List<StudentParticipation> studentParticipations = studentParticipationRepository.findAllWithEagerLegalSubmissionsAndEagerResultsByExerciseId(quizExercise.getId());
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(studentParticipations);

        for (var participation : studentParticipations) {
            if (participation.isTestRun()) {
                continue;
            }
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
                    // Load submission with highest id
                    quizSubmission = (QuizSubmission) submissions.stream().max(Comparator.comparing(Submission::getId)).get();
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
                    if (quizSubmission.getLastResult() != null) {
                        resultService.deleteResult(quizSubmission.getLastResult(), true);
                    }
                    result.setRated(true);
                    result.setAssessmentType(AssessmentType.AUTOMATIC);
                    result.setCompletionDate(ZonedDateTime.now());

                    // set submission to calculate scores
                    result.setSubmission(quizSubmission);
                    // calculate scores and update result and submission accordingly
                    quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
                    result.evaluateQuizSubmission(quizExercise);
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
                log.error("Exception in evaluateExamQuizExercise() for user {} in quiz {}: {}", participation.getParticipantIdentifier(), quizExercise.getId(), e.getMessage(), e);
            }

        }
        return createdResults;
    }
}
