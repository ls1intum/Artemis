package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.util.*;

import com.hazelcast.config.Config;
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;

abstract class QuizExerciseCache {

    private Long id;

    QuizExerciseCache(Long id) {
        this.id = Objects.requireNonNull(id, "exercise id must not be null");
    }

    /**
     * The id of the QuizExercise
     */
    final Long getId() {
        return id;
    }

    /**
     * Returns the quiz exercise.
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
     * The results by their id, must be a Map because Hazelcast uses serialized objects for set operations and not hashCode()/equals()
     */
    abstract Map<Long, Result> getResults();

    abstract void setExercise(QuizExercise newExercise);

    abstract void setQuizStart(List<ScheduledTaskHandler> quizStart);

    /**
     * Releases all Hazelcast resources, this makes the {@link QuizExerciseCache}-object unusable.
     */
    abstract void destroy();

    abstract boolean isDummy();

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof QuizExerciseCache))
            return false;
        return id.equals(((QuizExerciseCache) obj).id);
    }

    @Override
    public String toString() {
        return "QuizExerciseCache[" + id + "]";
    }

    static QuizExerciseCache empty() {
        return EmptyQuizExerciseCache.INSTANCE;
    }

    static ArrayList<ScheduledTaskHandler> getEmptyQuizStartList() {
        return new ArrayList<>(0);
    }

    static void registerSerializers(Config config) {
        QuizExerciseDistibutedCache.registerSerializer(config);
    }
}
