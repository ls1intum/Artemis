package de.tum.in.www1.artemis.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.quiz.QuizConfiguration;
import de.tum.in.www1.artemis.domain.quiz.QuizExamSubmission;
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

    public QuizExamSubmission save(QuizExamSubmission quizExamSubmission) {
        return this.save(null, quizExamSubmission, null);
    }

    public Optional<QuizExamSubmission> findWithEagerSubmittedAnswersByStudentExamId(Long studentExamId) {
        return quizExamSubmissionRepository.findWithEagerSubmittedAnswersByStudentExamId(studentExamId);
    }

    public QuizExamSubmission initializeNewSubmission() {
        return quizExamSubmissionRepository.save(new QuizExamSubmission());
    }
}
