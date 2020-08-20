package de.tum.in.www1.artemis.domain.leaderboard.tutor;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;

public class StudentScore {

    @EmbeddedId
    private long studentScoreId;

    @Column(name = "student_id")
    private long studentId;

    @Column(name = "exercise_id")
    private long exerciseId;

    @Column(name = "result_id")
    private long resultId;

    @Column(name = "score")
    private long score;

    public long getStudentScoreId() {
        return studentScoreId;
    }

    public long getStudentId() {
        return studentId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public long getResultId() {
        return resultId;
    }

    public long getScore() {
        return score;
    }

    public StudentScore(long studentId, long exerciseId, long resultId, long score) {
        this.studentId = studentId;
        this.exerciseId = exerciseId;
        this.resultId = resultId;
        this.score = score;
    }
}
