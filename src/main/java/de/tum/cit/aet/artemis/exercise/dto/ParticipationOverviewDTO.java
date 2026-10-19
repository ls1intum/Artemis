package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * A participation as the course overview renders it.
 * <p>
 * The student is deliberately absent: every participation here belongs to the requesting user, so sending it would
 * repeat the user's own account once per exercise.
 *
 * @param id                  the id of the participation, which the live result subscription keys on
 * @param type                the participation kind, which the client discriminates on
 * @param initializationState how far the student has got with the exercise
 * @param initializationDate  when the participation started
 * @param testRun             whether this is a test run rather than a graded attempt
 * @param individualDueDate   the student's own due date, when one was granted
 * @param repositoryUri       the student's repository for a programming participation; needed by the code actions
 * @param submissions         the submissions of this participation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationOverviewDTO(Long id, String type, InitializationState initializationState, ZonedDateTime initializationDate, Boolean testRun,
        ZonedDateTime individualDueDate, String repositoryUri, Set<SubmissionOverviewDTO> submissions) {

    /**
     * Projects a participation with its submissions and results for the course overview.
     *
     * @param participation the participation to project
     * @return the projected participation
     */
    public static ParticipationOverviewDTO of(StudentParticipation participation) {
        String repositoryUri = participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation ? programmingParticipation.getRepositoryUri() : null;
        return new ParticipationOverviewDTO(participation.getId(), participation.getType(), participation.getInitializationState(), participation.getInitializationDate(),
                participation.isTestRun(), participation.getIndividualDueDate(), repositoryUri, SubmissionOverviewDTO.of(participation.getSubmissions()));
    }

    /**
     * Projects the participations of an exercise for the course overview.
     *
     * @param participations the participations to project, may be null
     * @return the projected participations
     */
    public static Set<ParticipationOverviewDTO> of(Set<StudentParticipation> participations) {
        return participations == null ? Set.of() : participations.stream().map(ParticipationOverviewDTO::of).collect(Collectors.toSet());
    }
}
