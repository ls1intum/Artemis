package de.tum.cit.aet.artemis.service.exam;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.enumeration.AssessmentType;
import de.tum.cit.aet.artemis.domain.exam.StudentExam;
import de.tum.cit.aet.artemis.domain.quiz.QuizExercise;
import de.tum.cit.aet.artemis.domain.quiz.QuizSubmission;
import de.tum.cit.aet.artemis.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.repository.ResultRepository;
import de.tum.cit.aet.artemis.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.service.ResultService;
import de.tum.cit.aet.artemis.service.quiz.QuizStatisticService;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Service
public class ExamQuizService {

    private static final Logger log = LoggerFactory.getLogger(ExamQuizService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizStatisticService quizStatisticService;

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
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
    }

    /**
     * This method is intended to be called after a user submits a test run. We calculate the achieved score in the quiz exercises immediately and attach a result.
     * Note: We do not insert the result of this test run quiz participation into the quiz statistics.
     *
     * @param studentExam The test run or test exam containing the users participations in all exam exercises
     */
    public void evaluateQuizParticipationsForTestRunAndTestExam(StudentExam studentExam) {
        final var participations = studentExam.getExercises().stream()
                .flatMap(exercise -> exercise.getStudentParticipations().stream().filter(participation -> participation.getExercise() instanceof QuizExercise))
                .collect(Collectors.toSet());
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);
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
                    quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
                    result.evaluateQuizSubmission(quizExercise);
                    // remove submission to follow save order for ordered collections
                    result.setSubmission(null);
                    if (studentExam.isTestExam()) {
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
                    quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
                    // prevent a lazy exception in the evaluateQuizSubmission method
                    result.setParticipation(participation);
                    result.evaluateQuizSubmission(quizExercise);
                    if (studentExam.isTestExam()) {
                        result.rated(true);
                    }
                    resultRepository.save(result);
                }
                if (studentExam.isTestExam()) {
                    // In case of an test exam, the quiz statistic should also be updated
                    var quizExercise1 = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
                    quizStatisticService.updateStatistics(Set.of(result), quizExercise1);
                }
                submissionRepository.save(quizSubmission);
            }
        }
    }
}
