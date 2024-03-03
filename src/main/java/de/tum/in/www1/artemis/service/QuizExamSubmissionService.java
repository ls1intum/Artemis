package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.QuizExamSubmissionRepository;

/**
 * Service Implementation for managing QuizExamSubmission.
 */
@Service
public class QuizExamSubmissionService extends AbstractQuizSubmissionService<QuizExamSubmission> {

    private final QuizExamSubmissionRepository quizExamSubmissionRepository;

    /**
     * QuizExamSubmissionService constructor
     *
     * @param submissionVersionService     the SubmissionVersionService
     * @param quizExamSubmissionRepository the QuizExamSubmissionRepository
     */
    public QuizExamSubmissionService(SubmissionVersionService submissionVersionService, QuizExamSubmissionRepository quizExamSubmissionRepository) {
        super(submissionVersionService);
        this.quizExamSubmissionRepository = quizExamSubmissionRepository;
    }

    /**
     * Saves the given QuizExamSubmission and adds it to the given QuizExercise
     *
     * @param quizConfiguration  the QuizConfiguration of which the given submission belongs to
     * @param quizExamSubmission the AbstractQuizSubmission to be saved
     * @param user               the User that made the given submission
     * @return saved QuizExamSubmission
     */
    @Override
    protected QuizExamSubmission save(QuizConfiguration quizConfiguration, QuizExamSubmission quizExamSubmission, User user) {
        return quizExamSubmissionRepository.save(quizExamSubmission);
    }

    /**
     * Saves the given QuizExamSubmission
     *
     * @param quizExamSubmission the QuizExamSubmission to be saved
     * @return the saved QuizExamSubmission
     */
    public QuizExamSubmission save(QuizExamSubmission quizExamSubmission) {
        return this.save(null, quizExamSubmission, null);
    }

    /**
     * Finds a QuizExamSubmission with SubmittedAnswers by the given studentExamId
     *
     * @param studentExamId the id of the StudentExam
     * @return the QuizExamSubmission with SubmittedAnswers
     */
    public Optional<QuizExamSubmission> findWithEagerSubmittedAnswersByStudentExamId(Long studentExamId) {
        return quizExamSubmissionRepository.findWithEagerSubmittedAnswersByStudentExamId(studentExamId);
    }

    /**
     * Initializes a new QuizExamSubmission
     *
     * @return the newly initialized QuizExamSubmission
     */
    public QuizExamSubmission initializeNewSubmission() {
        return quizExamSubmissionRepository.save(new QuizExamSubmission());
    }

    public List<QuizExamSubmission> findAllWithStudentExamAndExamAndResultsByExamId(Long examId) {
        return quizExamSubmissionRepository.findAllWithStudentExamAndExamAndResultsByExamId(examId);
    }

    public void fetchQuizExamSubmissionsWithResultsFor(Long examId, Set<StudentExam> studentExams) {
        List<QuizExamSubmission> quizExamSubmissions = quizExamSubmissionRepository.findAllWithStudentExamAndExamAndResultsByExamId(examId);
        Map<Long, QuizExamSubmission> studentExamIdQuizExamSubmissionMap = quizExamSubmissions.stream()
                .collect(Collectors.toMap(quizExamSubmission -> quizExamSubmission.getStudentExam().getId(), quizExamSubmission -> quizExamSubmission));
        for (StudentExam studentExam : studentExams) {
            studentExam.setQuizExamSubmission(studentExamIdQuizExamSubmissionMap.getOrDefault(studentExam.getId(), null));
        }
    }

    public void fetchSubmittedAnswersForStudentExams(Long examId, Set<StudentExam> studentExams) {
        List<QuizExamSubmission> quizExamSubmissionsWithSubmittedAnswers = quizExamSubmissionRepository.findAllWithEagerSubmittedAnswersByExamId(examId);
        Map<Long, Set<SubmittedAnswer>> quizExamSubmissionSubmittedAnswersMap = quizExamSubmissionsWithSubmittedAnswers.stream()
                .collect(Collectors.toMap(QuizExamSubmission::getId, AbstractQuizSubmission::getSubmittedAnswers));
        for (StudentExam studentExam : studentExams) {
            QuizExamSubmission quizExamSubmission = studentExam.getQuizExamSubmission();
            if (quizExamSubmission != null) {
                quizExamSubmission.setSubmittedAnswers(quizExamSubmissionSubmittedAnswersMap.getOrDefault(quizExamSubmission.getId(), null));
            }
        }
    }
}
