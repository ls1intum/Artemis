package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Rich read DTO for a {@link StudentParticipation} as exposed in the tutor assessment views and the text editor
 * (for-assessment, without-assessment, text-editor). It carries the text submissions together with their results
 * (which include the assessor) and the exercise.
 * <p>
 * The {@code exercise} is wired by the controller because the required filtering differs per endpoint (tutors keep the
 * grading information, students receive a sensitive-information-filtered exercise). The factory maps only the
 * participation metadata, the student (when requested) and the submissions. The caller is expected to have already
 * locked / filtered / hidden details on the entity. The client reads {@code participation.submissions} as a list and
 * selects the relevant submission (latest for assessment, by id in the editor), mirroring the previous entity payload.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextParticipationDTO(Long id, String type, boolean testRun, InitializationState initializationState, ZonedDateTime initializationDate,
        ZonedDateTime individualDueDate, UserNameDTO student, TextExerciseResponseDTO exercise, List<TextSubmissionAssessmentDTO> submissions) implements Serializable {

    /**
     * Converts a {@link StudentParticipation} into a {@link TextParticipationDTO}, without mapping the exercise (the
     * controller wires {@link #exercise()} with the appropriate filtering).
     *
     * @param participation  the participation to convert
     * @param includeStudent whether the student should be included; when {@code false} the student is omitted (e.g. for tutors)
     * @return the converted DTO, or {@code null} if the participation is {@code null}
     */
    public static TextParticipationDTO of(StudentParticipation participation, boolean includeStudent) {
        if (participation == null) {
            return null;
        }

        UserNameDTO student = null;
        if (includeStudent && Hibernate.isInitialized(participation.getStudent().orElse(null))) {
            student = UserNameDTO.of(participation.getStudent().orElse(null));
        }

        List<TextSubmissionAssessmentDTO> submissions = null;
        if (Hibernate.isInitialized(participation.getSubmissions()) && participation.getSubmissions() != null) {
            submissions = participation.getSubmissions().stream().filter(Objects::nonNull).map(submission -> TextSubmissionAssessmentDTO.of((TextSubmission) submission)).toList();
        }

        return new TextParticipationDTO(participation.getId(), participation.getType(), participation.isTestRun(), participation.getInitializationState(),
                participation.getInitializationDate(), participation.getIndividualDueDate(), student, null, submissions);
    }

    /**
     * Returns a copy of this DTO with the given exercise wired in.
     *
     * @param exercise the exercise DTO to attach
     * @return a copy carrying the exercise
     */
    public TextParticipationDTO withExercise(TextExerciseResponseDTO exercise) {
        return new TextParticipationDTO(id, type, testRun, initializationState, initializationDate, individualDueDate, student, exercise, submissions);
    }
}
