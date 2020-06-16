package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;

/**
 * Mainly for testing
 */
final class QuizExerciseLocalCache extends QuizExerciseCache {

    List<ScheduledTaskHandler> quizStart;

    private transient QuizExercise exercise;

    private transient ConcurrentMap<String, StudentParticipation> participations;

    private transient ConcurrentMap<String, QuizSubmission> submissions;

    private transient ConcurrentMap<Long, Result> results;

    QuizExerciseLocalCache(Long id, List<ScheduledTaskHandler> quizStart) {
        super(id);
        participations = new ConcurrentHashMap<>();
        submissions = new ConcurrentHashMap<>();
        results = new ConcurrentHashMap<>();
        setQuizStart(quizStart);
    }

    QuizExerciseLocalCache(Long id) {
        this(id, getEmptyQuizStartList());
    }

    @Override
    QuizExercise getExercise() {
        return exercise;
    }

    @Override
    Map<String, QuizSubmission> getSubmissions() {
        return submissions;
    }

    @Override
    Map<String, StudentParticipation> getParticipations() {
        return participations;
    }

    @Override
    List<ScheduledTaskHandler> getQuizStart() {
        return quizStart;
    }

    @Override
    Map<Long, Result> getResults() {
        return results;
    }

    @Override
    void setExercise(QuizExercise newExercise) {
        this.exercise = newExercise;
    }

    @Override
    void setQuizStart(List<ScheduledTaskHandler> quizStart) {
        this.quizStart = Objects.requireNonNull(quizStart);
    }

    @Override
    void destroy() {
        participations.clear();
        submissions.clear();
        results.clear();
        exercise = null;
    }

    @Override
    boolean isDummy() {
        return false;
    }
}
