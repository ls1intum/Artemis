package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.util.*;

import com.hazelcast.config.Config;
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;

abstract class QuizExerciseCache {

    private Long exerciseId;

    QuizExerciseCache(Long exerciseId) {
        this.exerciseId = Objects.requireNonNull(exerciseId, "exerciseId must not be null");
    }

    /**
     * The id of the QuizExercise
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
     * QuizSubmissions by user name
     */
    abstract Map<String, QuizSubmission> getSubmissions();

    /**
     * StudentParticipations by user name
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

    /**
     * Releases all (Hazelcast) resources, all cached objects will be lost.
     * <p>
     * This should only be used for exceptional cases, such as deleting or resetting the exercise or for testing.
     */
    abstract void clear();

    @Override
    public final int hashCode() {
        return exerciseId.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof QuizExerciseCache))
            return false;
        return exerciseId.equals(((QuizExerciseCache) obj).exerciseId);
    }

    @Override
    public String toString() {
        return "QuizExerciseCache[" + exerciseId + "]";
    }

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
