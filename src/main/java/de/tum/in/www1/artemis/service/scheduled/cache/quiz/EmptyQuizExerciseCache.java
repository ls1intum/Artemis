package de.tum.in.www1.artemis.service.scheduled.cache.quiz;

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
 * Empty cache object representing a cache miss.
 * <p>
 * This allows to handle a not-cached quiz exercise elegantly, because most operations on the cache are read operations on the
 * exercise object or the submission, participation or result map. This immutable, singleton class returning only null or empty
 * data structures on read operations allows being used as default, if no real cache exists. This saves us much branching and
 * checking in the {@link QuizScheduleService}, as we can either just return the {@link EmptyQuizExerciseCache} or create a new
 * real {@link QuizExerciseCache} for write operations, if no cache can be found.
 * <p>
 * All method that modify a {@link QuizExerciseCache} throw an {@link UnsupportedOperationException} for the empty cache.
 */
final class EmptyQuizExerciseCache extends QuizExerciseCache {

    private static final Logger log = LoggerFactory.getLogger(EmptyQuizExerciseCache.class);

    static final EmptyQuizExerciseCache INSTANCE = new EmptyQuizExerciseCache();

    /**
     * The empty quiz exercise cache is the only one permitted to have the id <code>null</code>
     */
    private EmptyQuizExerciseCache() {
        super(null);
    }

    @Override
    QuizExercise getExercise() {
        return null;
    }

    @Override
    Map<String, Long> getBatches() {
        return Map.of();
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
        log.error("EmptyQuizExerciseCache cannot have an exercise set");
        throwModificationAttemptException();
    }

    @Override
    void setQuizStart(List<ScheduledTaskHandler> quizStart) {
        log.error("EmptyQuizExerciseCache cannot have the quiz start set");
        throwModificationAttemptException();
    }

    @Override
    public void clear() {
        log.error("EmptyQuizExerciseCache cannot be cleared");
        throwModificationAttemptException();
    }

    private static void throwModificationAttemptException() {
        throw new UnsupportedOperationException("EmptyQuizExerciseCache cannot be modified");
    }
}
