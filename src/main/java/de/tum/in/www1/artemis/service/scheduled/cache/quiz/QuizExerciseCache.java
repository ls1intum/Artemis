package de.tum.in.www1.artemis.service.scheduled.cache.quiz;

import java.util.*;

import com.hazelcast.config.Config;
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

/**
 * Represents the cache for one specific quiz exercise.
 */
abstract class QuizExerciseCache implements Cache {

    private final Long exerciseId;

    QuizExerciseCache(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    /**
     * The id of the QuizExercise, only <code>null</code> for the {@linkplain EmptyQuizExerciseCache empty cache}.
     */
    final Long getExerciseId() {
        return exerciseId;
    }

    /**
     * Returns the cached quiz exercise object.
     *
     * @return the actual QuizExercise object, may be null.
     */
    abstract QuizExercise getExercise();

    /**
     * QuizBatch ids by username
     */
    abstract Map<String, Long> getBatches();

    /**
     * QuizSubmissions by username
     */
    abstract Map<String, QuizSubmission> getSubmissions();

    /**
     * StudentParticipations by username
     */
    abstract Map<String, StudentParticipation> getParticipations();

    /**
     * The scheduled start tasks of the QuizExercise
     */
    abstract List<ScheduledTaskHandler> getQuizStart();

    /**
     * The results by their id
     */
    abstract Map<Long, Result> getResults();

    /**
     * Set the cached {@link QuizExercise} object
     */
    abstract void setExercise(QuizExercise newExercise);

    /**
     * Set the ScheduledTaskHandlers
     */
    abstract void setQuizStart(List<ScheduledTaskHandler> quizStart);

    @Override
    public final int hashCode() {
        return Objects.hashCode(exerciseId);
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof QuizExerciseCache))
            return false;
        return Objects.equals(exerciseId, ((QuizExerciseCache) obj).exerciseId);
    }

    @Override
    public String toString() {
        return "QuizExerciseCache[" + exerciseId + "]";
    }

    /**
     * Returns an empty quiz exercise cache
     *
     * @return the {@link EmptyQuizExerciseCache} instance
     */
    static QuizExerciseCache empty() {
        return EmptyQuizExerciseCache.INSTANCE;
    }

    static ArrayList<ScheduledTaskHandler> getEmptyQuizStartList() {
        return new ArrayList<>(0);
    }

    static void registerSerializers(Config config) {
        QuizExerciseDistributedCache.registerSerializer(config);
    }
}
