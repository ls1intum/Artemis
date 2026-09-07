package de.tum.cit.aet.artemis.exam.service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticService;

@Conditional(ExamEnabled.class)
@Lazy
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
            QuizExerciseRepository quizExerciseRepository, QuizStatisticService quizStatisticService, QuizSubmissionRepository quizSubmissionRepository,
            SubmittedAnswerRepository submittedAnswerRepository) {
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
        log.debug("Evaluating quiz participations for test run/test exam for student exam with id {}", studentExam.getId());
        // StudentExam.exercises is an @OrderColumn list, so Hibernate materializes a null for every gap in
        // exercise_order. This runs after the student exam was already marked submitted, and it is not wrapped in the
        // caller's try/catch, so dereferencing such a gap would answer the hand-in with a 500 on an exam the student
        // can no longer resubmit.
        final var participations = studentExam.getExercises().stream().filter(Objects::nonNull)
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
                    result.setAssessmentType(AssessmentType.AUTOMATIC);
                    // set submission to calculate scores
                    result.setSubmission(quizSubmission);
                    // calculate scores and update result and submission accordingly
                    quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
                    result.evaluateQuizSubmission(quizExercise);
                    result.setExerciseId(quizExercise.getId());
                    if (studentExam.isTestExam()) {
                        result.rated(true);
                    }
                    result = resultRepository.save(result);
                    // The participation reaching this method is reconstructed at the controller boundary from the slim
                    // submit body (see StudentExamSubmitMapper): it carries only what the submit path needs — id,
                    // participant, exercise, testRun and INITIALIZED. Saving that id-bearing partial entity merges it
                    // over the persisted row, which nulls initializationDate, individualDueDate and presentationScore,
                    // resets attempt to 0 (so repeated test-exam attempts lose their number) and can regress a
                    // FINISHED participation to INITIALIZED. The row already exists and this evaluation changes nothing
                    // on it, so only persist a participation that is not stored yet.
                    if (participation.getId() == null) {
                        studentParticipationRepository.save(participation);
                    }
                    quizSubmission.addResult(result);
                }
                else {
                    result = quizSubmission.getLatestResult();
                    // set submission to calculate scores
                    result.setSubmission(quizSubmission);
                    // calculate scores and update result and submission accordingly
                    quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
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
