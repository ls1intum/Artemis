package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;

/**
 * Empty cache object representing a cache miss
 */
final class EmptyQuizExerciseCache extends QuizExerciseCache {

    static final Logger log = LoggerFactory.getLogger(EmptyQuizExerciseCache.class);

    static final EmptyQuizExerciseCache INSTANCE = new EmptyQuizExerciseCache();

    private EmptyQuizExerciseCache() {
        super(Long.valueOf(-1));
    }

    @Override
    QuizExercise getExercise() {
        return null;
    }

    @Override
    Map<String, QuizSubmission> getSubmissions() {
        return Map.of();
    }

    @Override
    Map<String, StudentParticipation> getParticipations() {
        return Map.of();
    }

    @Override
    List<ScheduledTaskHandler> getQuizStart() {
        return List.of();
    }

    @Override
    Map<Long, Result> getResults() {
        return Map.of();
    }

    @Override
    void setExercise(QuizExercise newExercise) {
        throwModificationAttemptException();
    }

    @Override
    void setQuizStart(List<ScheduledTaskHandler> quizStart) {
        throwModificationAttemptException();
    }

    @Override
    void clear() {
        log.warn("EmptyQuizExerciseCache cannot be destroyed");
    }

    private static void throwModificationAttemptException() {
        throw new UnsupportedOperationException("EmptyQuizExerciseCache cannot be modified");
    }
}
