package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamScoresDTO(Long examId, String title, Integer maxPoints, Double averagePointsAchieved, Boolean hasSecondCorrectionAndStarted, List<ExerciseGroup> exerciseGroups,
        List<StudentResult> studentResults) {

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseGroup(Long id, String title, Double maxPoints, Long numberOfParticipants, List<ExerciseInfo> containedExercises) {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public record ExerciseInfo(Long exerciseId, String title, Double maxPoints, Long numberOfParticipants, String exerciseType) {
        }
    }

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StudentResult(Long userId, String name, String email, String login, String registrationNumber, Boolean submitted, Double overallPointsAchieved,
            Double overallScoreAchieved, String overallGrade, String overallGradeInFirstCorrection, Boolean hasPassed, Double overallPointsAchievedInFirstCorrection,
            BonusResultDTO gradeWithBonus, Map<Long, ExerciseResult> exerciseGroupIdToExerciseResult, PlagiarismVerdict mostSeverePlagiarismVerdict, boolean hasParticipated) {
    }

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseResult(Long exerciseId, String title, Double maxScore, Double achievedScore, Double achievedPoints, Boolean hasNonEmptySubmission) {
    }
}
