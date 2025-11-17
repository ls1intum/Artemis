package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizResultService {

    private static final Logger log = LoggerFactory.getLogger(QuizResultService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizStatisticService quizStatisticService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    public QuizResultService(QuizExerciseRepository quizExerciseRepository, QuizStatisticService quizStatisticService,
            StudentParticipationRepository studentParticipationRepository, SubmittedAnswerRepository submittedAnswerRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizStatisticService = quizStatisticService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Evaluate the given quiz exercise by evaluate the submission for each participation (there is only one for each participation in exams)
     * and update the statistics with the generated results.
     *
     * @param quizExerciseId the id of the QuizExercise that should be evaluated
     */
    public void evaluateQuizAndUpdateStatistics(@NonNull Long quizExerciseId) {
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
     * - Filter out submissions that are not submitted before the quiz deadline (practice mode)
     * - If more than one submission is found, select one with the highest ID
     * 2. mark submission and participation as evaluated
     * 3. Create a new result for the selected submission and calculate scores
     * - If a rated result already exists, skip the evaluation
     * - If no rated result exists, create a new one and evaluate the submission
     * 4. Save the updated submission & participation and the newly created result
     * <p>
     * After processing all participations, the created results will be returned for further processing
     * Note: We ignore test run participations
     * // @formatter:on
     *
     * @param quizExercise the id of the QuizExercise that should be evaluated
     * @return the newly generated results
     */
    private Set<Result> evaluateSubmissions(@NonNull QuizExercise quizExercise) {
        Set<Result> createdResults = new HashSet<>();
        List<StudentParticipation> studentParticipations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsByExerciseId(quizExercise.getId());
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
                    // Filter submissions to only include those submitted before the quiz deadline if the due date is not null, otherwise select the one with the highest ID
                    Optional<Submission> validSubmission = submissions.stream()
                            .filter(submission -> quizExercise.getDueDate() == null
                                    || (submission.getSubmissionDate() != null && !submission.getSubmissionDate().isAfter(quizExercise.getDueDate())))
                            .max(Comparator.comparing(Submission::getId));
                    if (validSubmission.isPresent()) {
                        quizSubmission = (QuizSubmission) validSubmission.get();
                    }
                    else {
                        log.warn("No valid submissions found for participation {} (Participant {}) in quiz {}", participation.getId(), participation.getParticipant().getName(),
                                quizExercise.getId());
                        continue;
                    }
                }
                else {
                    quizSubmission = (QuizSubmission) submissions.iterator().next();
                }

                participation.setInitializationState(InitializationState.FINISHED);

                Optional<Result> existingRatedResult = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).filter(Objects::nonNull)
                        .filter(Result::isRated).findFirst();

                if (existingRatedResult.isPresent()) {
                    // A rated result already exists; no need to create a new one
                    log.debug("A rated result already exists for participation {} (Participant {}), skipping evaluation.", participation.getId(),
                            participation.getParticipant().getName());
                }
                else {
                    // No rated result exists; create a new one
                    Result result = new Result().rated(true).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now());

                    // Associate submission with result
                    result.setSubmission(quizSubmission);

                    // Calculate and update scores
                    quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
                    result.evaluateQuizSubmission(quizExercise);

                    // Save entities individually
                    submissionRepository.save(quizSubmission);
                    result = resultRepository.save(result);

                    quizSubmission.addResult(result);
                    submissionRepository.save(quizSubmission);

                    // Add result to the set of created results
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
