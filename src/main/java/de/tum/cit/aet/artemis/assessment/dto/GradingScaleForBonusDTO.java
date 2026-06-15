package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * DTO representing a {@link GradingScale} as exposed within a bonus response.
 * <p>
 * This intentionally mirrors the flat wire shape the bonus client reads (grade steps, the owning course/exam, and the
 * grading metadata) rather than the nested {@link GradingScaleDTO} shape used by the grading-scale endpoints. The bonus
 * client derives the reachable/max points and rounding settings from the nested {@code course}/{@code exam}, so those
 * are carried as small projections. Grade steps are included only when requested (mirroring the previous
 * {@code includeSourceGradeSteps} entity filtering); they are never included for the {@code bonusToGradingScale}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GradingScaleForBonusDTO(Long id, GradeType gradeType, BonusStrategy bonusStrategy, String plagiarismGrade, String noParticipationGrade, Integer presentationsNumber,
        Double presentationsWeight, Set<GradeStepDTO> gradeSteps, CourseForBonusDTO course, ExamForBonusDTO exam) {

    /**
     * Minimal course projection carrying only the fields the bonus client reads: the title, the (reachable/max) points
     * and the rounding accuracy used to round computed bonus values.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseForBonusDTO(Long id, String title, Integer maxPoints, Integer accuracyOfScores) {

        public static CourseForBonusDTO of(Course course) {
            if (course == null) {
                return null;
            }
            return new CourseForBonusDTO(course.getId(), course.getTitle(), course.getMaxPoints(), course.getAccuracyOfScores());
        }
    }

    /**
     * Minimal exam projection carrying only the fields the bonus client reads: the title, the max points and the owning
     * course (used to derive the rounding settings).
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamForBonusDTO(Long id, String title, Integer examMaxPoints, CourseForBonusDTO course) {

        public static ExamForBonusDTO of(Exam exam) {
            if (exam == null) {
                return null;
            }
            return new ExamForBonusDTO(exam.getId(), exam.getTitle(), exam.getExamMaxPoints(), CourseForBonusDTO.of(exam.getCourse()));
        }
    }

    /**
     * Creates a {@link GradingScaleForBonusDTO} from a {@link GradingScale} entity.
     *
     * @param scale             the grading scale entity to convert
     * @param includeGradeSteps whether the grade steps of this grading scale should be serialized
     * @return a DTO representation of the grading scale for use in a bonus response
     */
    public static GradingScaleForBonusDTO of(GradingScale scale, boolean includeGradeSteps) {
        Objects.requireNonNull(scale, "grading scale must exist");

        Set<GradeStepDTO> gradeSteps = Set.of();
        if (includeGradeSteps && Hibernate.isInitialized(scale.getGradeSteps()) && scale.getGradeSteps() != null) {
            gradeSteps = scale.getGradeSteps().stream().map(GradeStepDTO::of).collect(Collectors.toSet());
        }

        return new GradingScaleForBonusDTO(scale.getId(), scale.getGradeType(), scale.getBonusStrategy(), scale.getPlagiarismGrade(), scale.getNoParticipationGrade(),
                scale.getPresentationsNumber(), scale.getPresentationsWeight(), gradeSteps, CourseForBonusDTO.of(scale.getCourse()), ExamForBonusDTO.of(scale.getExam()));
    }
}
