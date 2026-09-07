package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

/**
 * Common exercise fields shared by every exam exercise subtype in the conduction payload. The {@code type} discriminator
 * mirrors the {@code @JsonTypeInfo} property on {@link Exercise}, so the (unchanged) client model and the byte-compat
 * oracle tests deserialize each exercise into the correct concrete subtype.
 * <p>
 * The exercise has already been masked (solutions, grading criteria/instructions, build config stripped) before this
 * factory runs, so it is a faithful copy of the masked entity and re-adds no sensitive fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamExerciseBaseForConductionDTO(String type, long id, String title, String shortName, Double maxPoints, Double bonusPoints, DifficultyLevel difficulty,
        ExerciseMode mode, boolean teamMode, ExerciseType exerciseType, IncludedInOverallScore includedInOverallScore, String problemStatement, Boolean presentationScoreEnabled,
        boolean secondCorrectionEnabled, boolean allowComplaintsForAutomaticAssessments, boolean allowFeedbackRequests, boolean gradingInstructionFeedbackUsed,
        boolean studentAssignedTeamIdComputed, boolean visibleToStudents, ExerciseGroupForConductionDTO exerciseGroup,
        List<StudentParticipationForConductionDTO> studentParticipations) {

    /**
     * Extracts the common exercise fields from a (masked) exam exercise.
     *
     * @param exercise the exercise to convert
     * @return the common exercise fields
     */
    public static ExamExerciseBaseForConductionDTO of(Exercise exercise) {
        var entityParticipations = exercise.getStudentParticipations();
        List<StudentParticipationForConductionDTO> studentParticipations = (entityParticipations == null || !Hibernate.isInitialized(entityParticipations)) ? null
                : entityParticipations.stream().map(StudentParticipationForConductionDTO::of).toList();
        return new ExamExerciseBaseForConductionDTO(exercise.getType(), exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getDifficulty(), exercise.getMode(), exercise.isTeamMode(), exercise.getExerciseType(), exercise.getIncludedInOverallScore(),
                exercise.getProblemStatement(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(), exercise.getAllowComplaintsForAutomaticAssessments(),
                exercise.getAllowFeedbackRequests(), exercise.isGradingInstructionFeedbackUsed(), exercise.isStudentAssignedTeamIdComputed(), exercise.isVisibleToStudents(),
                ExerciseGroupForConductionDTO.of(exercise.getExerciseGroup()), studentParticipations);
    }
}
