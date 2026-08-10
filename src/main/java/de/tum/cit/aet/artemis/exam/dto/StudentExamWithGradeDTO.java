package de.tum.cit.aet.artemis.exam.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.web.GradeStepResource;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.detail.StudentExamForDetailDTO;

/**
 * DTO that wraps a {@link StudentExam} projection and contains related assessment result and grading information if
 * available. Includes max points and max bonus points.
 * <p>
 * It is possible to calculate data included in this DTO by using the {@link StudentExam}, {@link ExamScoresDTO.StudentResult}
 * and {@link GradingScale} for the exam. The calculations are done in the server and returned with this DTO so that the client
 * does not need to repeat the same filtering and calculation logic. Also this removes the need for client to send an extra request to
 * {@link GradeStepResource#getGradeStepByPercentageForExam(Long, Long, Double)}
 * by including the grade in {@link ExamScoresDTO.StudentResult#overallGrade()}.
 * <p>
 * The wrapped student exam is projected to {@link StudentExamForDetailDTO} rather than exposed as the raw entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamWithGradeDTO(Double maxPoints, Double maxBonusPoints, GradeType gradeType, StudentExamForDetailDTO studentExam, ExamScoresDTO.StudentResult studentResult,
        Map<Long, Double> achievedPointsPerExercise) {
}
