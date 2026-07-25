package de.tum.cit.aet.artemis.fileupload.dto;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * DTO representing a participation in a file upload exercise.
 *
 * @param id                    the ID of the participation
 * @param initializationState   the initialization state of the participation (e.g. INITIALIZED, FINISHED)
 * @param initializationDate    the date and time when the participation was initialized
 * @param individualDueDate     the individual due date (if extended for this participation)
 * @param presentationScore     the score for presentation (if applicable)
 * @param submissionCount       the number of submissions in this participation
 * @param type                  the participation type (e.g. student, programming)
 * @param testRun               whether the participation is a test run (for instructors/tutors)
 * @param participantName       the name of the participant (user or team)
 * @param participantIdentifier the identifier of the participant (login or team short name)
 * @param isOwner               whether the current user owns this participation
 * @param exercise              the exercise context of this participation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadParticipationDTO(Long id, InitializationState initializationState, ZonedDateTime initializationDate, ZonedDateTime individualDueDate,
        Double presentationScore, Integer submissionCount, String type, Boolean testRun, String participantName, String participantIdentifier, Boolean isOwner,
        FileUploadExerciseContextDTO exercise) {

    /**
     * Factory method to create a {@link FileUploadParticipationDTO} from a {@link Participation} entity.
     *
     * @param participation   the participation entity to map, can be null
     * @param includeExercise whether to include the exercise details in the mapped DTO
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadParticipationDTO of(Participation participation, boolean includeExercise) {
        return of(participation, includeExercise, null, null, null, null);
    }

    /**
     * Factory method to create a {@link FileUploadParticipationDTO} from a {@link Participation} entity with permission flags.
     *
     * @param participation       the participation entity to map, can be null
     * @param includeExercise     whether to include the exercise details in the mapped DTO
     * @param isOwner             whether the current user owns the participation
     * @param isAtLeastTutor      whether the current user has at least tutor privileges for the exercise
     * @param isAtLeastEditor     whether the current user has at least editor privileges for the exercise
     * @param isAtLeastInstructor whether the current user has at least instructor privileges for the exercise
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadParticipationDTO of(Participation participation, boolean includeExercise, Boolean isOwner, Boolean isAtLeastTutor, Boolean isAtLeastEditor,
            Boolean isAtLeastInstructor) {
        if (participation == null) {
            return null;
        }

        Double presentationScore = null;
        String participantName = null;
        String participantIdentifier = null;
        if (participation instanceof StudentParticipation studentParticipation) {
            presentationScore = studentParticipation.getPresentationScore();
            participantName = studentParticipation.getParticipantName();
            participantIdentifier = studentParticipation.getParticipantIdentifier();
        }

        FileUploadExerciseContextDTO exerciseDTO = null;
        Exercise exercise = participation.getExercise();
        if (includeExercise && exercise instanceof FileUploadExercise fileUploadExercise && Hibernate.isInitialized(fileUploadExercise)) {
            exerciseDTO = FileUploadExerciseContextDTO.of(fileUploadExercise, true, isAtLeastTutor, isAtLeastEditor, isAtLeastInstructor);
        }

        return new FileUploadParticipationDTO(participation.getId(), participation.getInitializationState(), participation.getInitializationDate(),
                participation.getIndividualDueDate(), presentationScore, participation.getSubmissionCount(), participation.getType(), participation.isTestRun(), participantName,
                participantIdentifier, isOwner, exerciseDTO);
    }
}
