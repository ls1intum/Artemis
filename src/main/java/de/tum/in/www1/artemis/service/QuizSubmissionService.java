package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.quiz.AbstractQuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

@Service
public abstract class QuizSubmissionService<T extends AbstractQuizSubmission> {

    private final SubmissionVersionService submissionVersionService;

    private final Logger log = LoggerFactory.getLogger(AbstractQuizSubmission.class);

    protected QuizSubmissionService(SubmissionVersionService submissionVersionService) {
        this.submissionVersionService = submissionVersionService;
    }

    protected abstract void preSave(QuizExercise quizExercise, T abstractQuizSubmission, User user);

    protected abstract T save(T abstractQuizSubmission, User user);

    /**
     * Updates a submission for the exam mode
     *
     * @param quizExercise   the quiz exercise for which the submission for the exam mode should be done
     * @param quizSubmission the quiz submission includes the submitted answers by the student
     * @param user           the student who wants to submit the quiz during the exam
     * @return the updated quiz submission after it has been saved to the database
     */
    public T saveSubmissionForExamMode(QuizExercise quizExercise, T quizSubmission, User user) {
        // update submission properties
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        quizSubmission.setResults(new ArrayList<>());
        preSave(quizExercise, quizSubmission, user);
        T savedQuizSubmission = this.save(quizSubmission, user);

        // versioning of submission
        try {
            submissionVersionService.saveVersionForIndividual(quizSubmission, user);
        }
        catch (Exception ex) {
            log.error("Quiz submission version could not be saved", ex);
        }

        log.debug("submit exam quiz finished: {}", savedQuizSubmission);

        return savedQuizSubmission;
    }
}
