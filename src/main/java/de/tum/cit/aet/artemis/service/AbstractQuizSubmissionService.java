package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.SubmissionType;
import de.tum.cit.aet.artemis.quiz.domain.AbstractQuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@Profile(PROFILE_CORE)
@Service
public abstract class AbstractQuizSubmissionService<T extends AbstractQuizSubmission> {

    private final SubmissionVersionService submissionVersionService;

    private static final Logger log = LoggerFactory.getLogger(AbstractQuizSubmissionService.class);

    protected AbstractQuizSubmissionService(SubmissionVersionService submissionVersionService) {
        this.submissionVersionService = submissionVersionService;
    }

    /**
     * Save the given submission to the database.
     *
     * @param quizExercise the QuizExercise of which the given submission belongs to
     * @param submission   the AbstractQuizSubmission to be saved
     * @param user         the User that made the given submission
     * @return saved AbstractQuizSubmission
     */
    protected abstract T save(QuizExercise quizExercise, T submission, User user);

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
        T savedQuizSubmission = this.save(quizExercise, quizSubmission, user);

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
