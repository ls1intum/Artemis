package de.tum.cit.aet.artemis.quiz.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizQuestionApi implements AbstractApi {

    private final QuizQuestionRepository quizQuestionRepository;

    public QuizQuestionApi(QuizQuestionRepository quizQuestionRepository) {
        this.quizQuestionRepository = quizQuestionRepository;
    }

    public long countAllQuizQuestionsByCourseIdAvailableForPractice(long courseId) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.countAllQuizQuestionsByCourseIdBefore(courseId, now);
    }

    public boolean areQuizExercisesInCourseAvailableForPractice(long courseId) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.areQuizExercisesWithDueDateBefore(courseId, now);
    }

    public Slice<QuizQuestion> findAllQuizQuestionsByCourseIdAvailableForPractice(long courseId, Pageable pageable) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.findAllQuizQuestionsByCourseIdWithDueDateBefore(courseId, now, pageable);
    }

    public Slice<QuizQuestion> findAllQuizQuestionsByCourseIdAvailableForPracticeNotIn(Set<Long> ids, long courseId, Pageable pageable) {
        ZonedDateTime now = ZonedDateTime.now();
        return quizQuestionRepository.findAllQuizQuestionsByCourseIdWithDueDateBeforeNotIn(ids, courseId, now, pageable);
    }
}
