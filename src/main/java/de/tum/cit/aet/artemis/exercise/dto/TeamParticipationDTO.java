package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * A team's participation in an exercise, as the team detail page and the team assignment websocket topic report it.
 * <p>
 * The submissions and the team are only mapped when they are already loaded, so mapping never triggers a lazy load: the
 * team detail page loads them for the team tutor and instructors only, and the participations of a freshly created team
 * carry neither.
 *
 * @param id                  the id of the participation
 * @param type                the participation kind; the client discriminates on it when it merges participations
 * @param testRun             whether this is a test run rather than a graded attempt
 * @param initializationState how far the team has got with the exercise
 * @param initializationDate  when the participation started
 * @param individualDueDate   the team's own due date, when one was granted
 * @param presentationScore   the presentation score, when one was given
 * @param submissionCount     how many submissions the participation has
 * @param repositoryUri       the repository of a programming participation, which the client needs for the code and
 *                                clone actions
 * @param team                the team owning the participation, which the client matches the logged-in login against
 * @param submissions         the submissions with their results
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamParticipationDTO(Long id, String type, Boolean testRun, InitializationState initializationState, ZonedDateTime initializationDate,
        ZonedDateTime individualDueDate, Double presentationScore, Integer submissionCount, String repositoryUri, TeamDTO team, Set<SubmissionOverviewDTO> submissions)
        implements Serializable {

    /**
     * Projects a participation of a team.
     *
     * @param participation the participation to project
     * @return the projected participation
     */
    public static TeamParticipationDTO of(StudentParticipation participation) {
        TeamDTO team = null;
        if (Hibernate.isInitialized(participation.getTeam().orElse(null))) {
            team = TeamDTO.of(participation.getTeam().orElse(null));
        }
        Set<SubmissionOverviewDTO> submissions = null;
        if (Hibernate.isInitialized(participation.getSubmissions())) {
            submissions = SubmissionOverviewDTO.of(participation.getSubmissions());
        }
        String repositoryUri = participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation ? programmingParticipation.getRepositoryUri() : null;
        return new TeamParticipationDTO(participation.getId(), participation.getType(), participation.isTestRun(), participation.getInitializationState(),
                participation.getInitializationDate(), participation.getIndividualDueDate(), participation.getPresentationScore(), participation.getSubmissionCount(),
                repositoryUri, team, submissions);
    }
}
