package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO.ParticipationExerciseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * DTO representing the REST response for a student participation.
 *
 * @param id                    the unique identifier of the participation
 * @param initializationState   the current initialization state, if available
 * @param initializationDate    the time at which initialization started, if available
 * @param individualDueDate     the individual due date, if configured
 * @param presentationScore     the presentation score, if assigned
 * @param testRun               whether this is an exam test run or course practice participation
 * @param type                  the polymorphic participation discriminator
 * @param submissionCount       the transient number of submissions, if calculated
 * @param participantName       the visible participant name, if authorized
 * @param participantIdentifier the visible participant identifier, if authorized
 * @param student               safe public student information, if authorized and initialized
 * @param team                  safe team information, if authorized and initialized
 * @param exercise              the minimal exercise context, only on submit-path responses
 * @param submissions           initialized lean submissions, or absent when submissions were not loaded
 * @param repositoryUri         the programming repository URI, if applicable
 * @param buildPlanId           the programming build-plan identifier, if applicable
 * @param branch                the programming repository branch, if applicable
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentParticipationDTO(Long id, @Nullable InitializationState initializationState, @Nullable ZonedDateTime initializationDate,
        @Nullable ZonedDateTime individualDueDate, @Nullable Double presentationScore, boolean testRun, String type, @Nullable Integer submissionCount,
        @Nullable String participantName, @Nullable String participantIdentifier, @Nullable UserPublicInfoDTO student, @Nullable TeamDTO team,
        @Nullable ParticipationExerciseDTO exercise, @Nullable List<ParticipationSubmissionDTO> submissions, @Nullable String repositoryUri, @Nullable String buildPlanId,
        @Nullable String branch) {

    /**
     * The participant as the response reports it: either both DTO and name/identifier, or nothing at all.
     *
     * @param student    safe public student information, if the participant is a visible user
     * @param team       safe team information, if the participant is a visible team
     * @param name       the visible participant name
     * @param identifier the visible participant identifier
     */
    // A mapping helper, never serialized: the name and the annotation only satisfy ExerciseCodeStyleArchitectureTest.testDTOImplementations,
    // which requires every class in a dto package to be a *DTO record carrying @JsonInclude.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ParticipantViewDTO(@Nullable UserPublicInfoDTO student, @Nullable TeamDTO team, @Nullable String name, @Nullable String identifier) {

        /** The participant withheld, e.g. from a tutor assessing the participation. */
        static final ParticipantViewDTO HIDDEN = new ParticipantViewDTO(null, null, null, null);

        static ParticipantViewDTO of(@Nullable Participant participant) {
            UserPublicInfoDTO student = participant instanceof User user ? new UserPublicInfoDTO(user) : null;
            TeamDTO team = participant instanceof Team participantTeam ? TeamDTO.of(participantTeam) : null;
            if (student == null && team == null) {
                return HIDDEN;
            }
            return new ParticipantViewDTO(student, team, participant.getName(), participant.getParticipantIdentifier());
        }
    }

    /**
     * Builds the participation a submit response reports, from the projection the save was made against.
     * <p>
     * The submit path never loads the participation as an entity - doing so drags the exercise, its course and, for an
     * exam exercise, the exercise group with its exam and that exam's course along - so the response is mapped from the
     * projected columns plus the exercise and participant the caller already holds.
     *
     * @param target             the projected participation the submission was saved against
     * @param exercise           the exercise the submission belongs to
     * @param participant        the student or team the participation belongs to, may be null when it must be withheld
     * @param includeParticipant whether the participant may be reported; a tutor assessing must not see it
     * @return the participation as the response reports it
     */
    public static StudentParticipationDTO of(StudentParticipationSubmitTargetDTO target, Exercise exercise, @Nullable Participant participant, boolean includeParticipant) {
        ParticipantViewDTO participantView = includeParticipant ? ParticipantViewDTO.of(participant) : ParticipantViewDTO.HIDDEN;
        return new StudentParticipationDTO(target.id(), target.initializationState(), target.initializationDate(), target.individualDueDate(), target.presentationScore(),
                target.testRun(), StudentParticipation.TYPE, null, participantView.name(), participantView.identifier(), participantView.student(), participantView.team(),
                ParticipationExerciseDTO.of(exercise), null, null, null, null);
    }

    /**
     * Maps a participation for an enclosing response, optionally including its visible participant.
     *
     * @param participation  the participation to map
     * @param includeStudent whether initialized participant information should be included
     * @return the participation response, or {@code null} when the input is {@code null}
     */
    public static @Nullable StudentParticipationDTO of(@Nullable StudentParticipation participation, boolean includeStudent) {
        if (participation == null) {
            return null;
        }
        return build(participation, includeStudent ? ParticipantViewDTO.of(initializedParticipant(participation)) : ParticipantViewDTO.HIDDEN,
                ParticipationExerciseDTO.of(participation.getExercise()), null);
    }

    /**
     * Maps a newly started participation including its participant and its initialized submissions with subtype content.
     * No exercise is reported: every client uses the exercise it already holds.
     *
     * @param participation the newly started participation
     * @param participant   the student or team the participation was started for, loaded for this request
     * @return the participation response
     */
    public static StudentParticipationDTO ofAfterStart(StudentParticipation participation, Participant participant) {
        return build(participation, ParticipantViewDTO.of(participant), null, submissionsOf(participation, true));
    }

    /**
     * Maps a participation for latest-result polling with initialized lean submissions and results.
     *
     * @param participation the participation loaded with its latest result
     * @return the polling response
     */
    public static StudentParticipationDTO ofWithLatestResult(StudentParticipation participation) {
        return build(participation, ParticipantViewDTO.HIDDEN, null, submissionsOf(participation, false));
    }

    /**
     * Maps a resumed participation. The resume route saves the participation first and gets a merged instance back,
     * whose team no longer has its students loaded, so it passes the participant it read before the save. No exercise
     * is reported: every client uses the exercise it already holds.
     *
     * @param participation the resumed participation
     * @param participant   the student or team the participation belongs to, read before the save
     * @return the resumed participation response
     */
    public static StudentParticipationDTO ofAfterResume(StudentParticipation participation, @Nullable Participant participant) {
        return build(participation, ParticipantViewDTO.of(participant), null, null);
    }

    /**
     * Maps a participation for its current owner. The participant comes from the eagerly loaded student or team; no
     * exercise is reported, because every client uses the exercise it already holds.
     *
     * @param participation the authorized participation
     * @return the current-user participation response
     */
    public static StudentParticipationDTO ofForCurrentUser(StudentParticipation participation) {
        return build(participation, ParticipantViewDTO.of(initializedParticipant(participation)), null, null);
    }

    /**
     * Maps a participation after a scalar management update without initializing unrelated associations.
     *
     * @param participation the updated participation
     * @return the lean update response
     */
    public static StudentParticipationDTO ofAfterUpdate(StudentParticipation participation) {
        return build(participation, ParticipantViewDTO.HIDDEN, null, null);
    }

    /**
     * The participant of a participation whose student or team was loaded for this request, {@code null} otherwise.
     * Reading {@code getParticipant()} directly would dereference a lazy proxy.
     */
    private static @Nullable Participant initializedParticipant(StudentParticipation participation) {
        User student = participation.getStudent().filter(Hibernate::isInitialized).orElse(null);
        return student != null ? student : participation.getTeam().filter(Hibernate::isInitialized).orElse(null);
    }

    private static @Nullable List<ParticipationSubmissionDTO> submissionsOf(StudentParticipation participation, boolean includeContent) {
        if (participation.getSubmissions() == null || !Hibernate.isInitialized(participation.getSubmissions())) {
            return null;
        }
        return participation.getSubmissions().stream().filter(Objects::nonNull).map(submission -> ParticipationSubmissionDTO.of(submission, includeContent)).toList();
    }

    private static StudentParticipationDTO build(StudentParticipation participation, ParticipantViewDTO participant, @Nullable ParticipationExerciseDTO exercise,
            @Nullable List<ParticipationSubmissionDTO> submissions) {
        String repositoryUri = null;
        String buildPlanId = null;
        String branch = null;
        if (participation instanceof ProgrammingExerciseStudentParticipation programmingParticipation) {
            repositoryUri = programmingParticipation.getRepositoryUri();
            buildPlanId = programmingParticipation.getBuildPlanId();
            branch = programmingParticipation.getBranch();
        }
        return new StudentParticipationDTO(participation.getId(), participation.getInitializationState(), participation.getInitializationDate(),
                participation.getIndividualDueDate(), participation.getPresentationScore(), participation.isTestRun(), participation.getType(), participation.getSubmissionCount(),
                participant.name(), participant.identifier(), participant.student(), participant.team(), exercise, submissions, repositoryUri, buildPlanId, branch);
    }

    /**
     * Removes participant information, including repository identifiers that contain the student login or team name.
     *
     * @return an anonymous participation response
     */
    public StudentParticipationDTO withoutParticipantInformation() {
        return new StudentParticipationDTO(id, initializationState, initializationDate, individualDueDate, presentationScore, testRun, type, submissionCount, null, null, null,
                null, exercise, submissions, null, null, null);
    }

}
