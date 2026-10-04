package de.tum.cit.aet.artemis.exam.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.BonusResultDTO;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamScoresDTO(Long examId, String title, Integer maxPoints, Double averagePointsAchieved, Boolean hasSecondCorrectionAndStarted,
        List<ExerciseGroupDTO> exerciseGroups, List<StudentResultDTO> studentResults) {

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseGroupDTO(Long id, String title, Double maxPoints, Long numberOfParticipants, List<ExerciseInfoDTO> containedExercises) {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public record ExerciseInfoDTO(Long exerciseId, String title, Double maxPoints, Long numberOfParticipants, String exerciseType) {
        }
    }

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StudentResultDTO(Long userId, String name, String email, String login, String registrationNumber, Boolean submitted, Double overallPointsAchieved,
            Double overallScoreAchieved, String overallGrade, String overallGradeInFirstCorrection, Boolean hasPassed, Double overallPointsAchievedInFirstCorrection,
            BonusResultDTO gradeWithBonus, Map<Long, ExerciseResultDTO> exerciseGroupIdToExerciseResult, PlagiarismVerdict mostSeverePlagiarismVerdict) {
    }

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseResultDTO(Long exerciseId, String title, Double maxScore, Double achievedScore, Double achievedPoints, Boolean hasNonEmptySubmission) {
    }
}
