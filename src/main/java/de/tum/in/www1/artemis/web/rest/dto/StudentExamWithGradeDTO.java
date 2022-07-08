package de.tum.in.www1.artemis.web.rest.dto;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradeType;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

/**
 * DTO that wraps the {@link StudentExam} and contains related assessment result and grading information if available.
 * Includes max points and max bonus points for the {@link #studentExam}.
 *
 * It is possible to calculate data included in this DTO by using the {@link StudentExam}, {@link ExamScoresDTO.StudentResult}
 * and {@link de.tum.in.www1.artemis.domain.GradingScale} for the exam. The calculations are done in the server and returned with this DTO so that the client
 * does not need to repeat the same filtering and calculation logic. Also this removes the need for client to send an extra request to {@link de.tum.in.www1.artemis.web.rest.GradeStepResource#getGradeStepByPercentageForExam(Long, Long, Double)}
 * by including the grade in {@link ExamScoresDTO.StudentResult#overallGrade}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentExamWithGradeDTO {

    public Double maxPoints;

    public Double maxBonusPoints;

    public GradeType gradeType;

    public StudentExam studentExam;

    public ExamScoresDTO.StudentResult studentResult;

    public Map<Long, Double> achievedPointsPerExercise = new HashMap<>();

    public StudentExamWithGradeDTO() {
        // Empty constructor is needed by Jackson
    }

    public StudentExamWithGradeDTO(StudentExam studentExam, ExamScoresDTO.StudentResult studentResult) {
        this.studentExam = studentExam;
        this.studentResult = studentResult;
    }
}
