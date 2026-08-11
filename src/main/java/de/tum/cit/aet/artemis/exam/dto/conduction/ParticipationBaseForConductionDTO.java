package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Common fields shared by every student-participation subtype in the conduction payload. The {@code type} discriminator
 * mirrors the {@code @JsonTypeInfo} property on the participation entity hierarchy ({@code student} vs
 * {@code programming}), so the (unchanged) client model and the byte-compat oracle tests deserialize each participation
 * into the correct concrete subtype.
 * <p>
 * Programming participations do not carry submissions in the conduction payload (they are driven by the repository, not
 * by the hand-in), matching the masked entity wire.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationBaseForConductionDTO(String type, long id, InitializationState initializationState, ZonedDateTime initializationDate, boolean testRun, int attempt,
        String participantIdentifier, String participantName, UserNameDTO student, Integer submissionCount, List<SubmissionForConductionDTO> submissions) {

    /**
     * Extracts the common participation fields from a student participation.
     *
     * @param participation the participation to convert
     * @return the common participation fields
     */
    public static ParticipationBaseForConductionDTO of(StudentParticipation participation) {
        boolean isProgramming = participation instanceof ProgrammingExerciseStudentParticipation;
        String type = isProgramming ? "programming" : "student";
        // Map submissions faithfully for every participation type: a fresh conduction leaves programming participations
        // submission-less (dropped by NON_EMPTY), but once submitted/assessed they carry the submission with its results.
        var entitySubmissions = participation.getSubmissions();
        List<SubmissionForConductionDTO> submissions = (entitySubmissions == null || !Hibernate.isInitialized(entitySubmissions) || entitySubmissions.isEmpty()) ? null
                : entitySubmissions.stream().map(SubmissionForConductionDTO::of).toList();
        UserNameDTO student = participation.getStudent().map(UserNameDTO::of).orElse(null);
        return new ParticipationBaseForConductionDTO(type, participation.getId(), participation.getInitializationState(), participation.getInitializationDate(),
                participation.isTestRun(), participation.getAttempt(), participation.getParticipantIdentifier(), participation.getParticipantName(), student,
                participation.getSubmissionCount(), submissions);
    }
}
