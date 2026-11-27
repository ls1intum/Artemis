package de.tum.cit.aet.artemis.exercise.service.team;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueResultDTO;
import de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueSubmissionDTO;
import de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueTeamDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssessmentQueueEntryDTO;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssessmentQueueRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Applies the assessment rules of course team exercises and builds their assessment queue.
 * <p>
 * Only the tutor of a team and instructors may assess the submissions of a team. A new assessment starts only once the team's own deadline, including an
 * individual extension, has passed, and for programming exercises also the build and test date.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class TeamAssessmentService {

    private final TeamAssessmentQueueRepository teamAssessmentQueueRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public TeamAssessmentService(TeamAssessmentQueueRepository teamAssessmentQueueRepository, AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.teamAssessmentQueueRepository = teamAssessmentQueueRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * Checks that the current user may open the assessment of the given submission.
     * <p>
     * Submissions outside team exercises and practice participations without a team, which instructors create to try an exercise, are not restricted.
     *
     * @param submission          the submission to assess
     * @param startsNewAssessment whether opening the submission creates a new assessment
     * @throws AccessForbiddenException if the user is neither the team's tutor nor an instructor, or if a new assessment cannot start yet
     */
    public void checkCanAssessElseThrow(Submission submission, boolean startsNewAssessment) {
        if (!submission.getParticipation().getExercise().isTeamMode() || !(submission.getParticipation() instanceof StudentParticipation participation)
                || (participation.isTestRun() && participation.getTeam().isEmpty())) {
            return;
        }
        Exercise exercise = participation.getExercise();
        User user = userRepository.getUserWithAuthorities();
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user) && participation.getTeam().filter(team -> team.isOwner(user)).isEmpty()) {
            throw new AccessForbiddenException("Only the team tutor may assess this submission.");
        }
        if (!startsNewAssessment) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (ExerciseDateService.getDueDate(participation).filter(now::isBefore).isPresent()) {
            throw new AccessForbiddenException("The team's submission deadline has not passed yet.");
        }
        if (isBuildAndTestDatePending(exercise, now)) {
            throw new AccessForbiddenException("The build and test date has not passed yet.");
        }
    }

    /**
     * Lists the submissions of the user's teams that can be assessed next and the existing assessments of the given correction round, without claiming any of them.
     * Tutors see the teams they own, instructors see all teams.
     *
     * @param exercise        the course team exercise
     * @param correctionRound the displayed correction round
     * @return one entry per team that has a submission to assess or an existing assessment
     */
    public List<AssessmentQueueTeamDTO> getAssessmentQueue(Exercise exercise, int correctionRound) {
        User user = userRepository.getUserWithAuthorities();
        Long teamOwnerId = authCheckService.isAtLeastInstructorForExercise(exercise, user) ? null : user.getId();
        List<TeamAssessmentQueueEntryDTO> entries = teamAssessmentQueueRepository.findTeamAssessmentQueueParticipations(exercise.getId(), teamOwnerId, correctionRound);

        // A team has either a submission without a manual result or an assessed one, so the two lists never share a participation.
        Map<Long, AssessmentQueueSubmissionDTO> submissionByParticipation = new HashMap<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (canStartAssessments(exercise, now)) {
            teamAssessmentQueueRepository.findCandidateSubmissions(exercise.getId(), now, teamOwnerId)
                    .forEach(submission -> submissionByParticipation.put(submission.participationId(), submission));
        }
        List<Long> assessedSubmissionIds = entries.stream().map(TeamAssessmentQueueEntryDTO::submissionId).filter(Objects::nonNull).toList();
        if (!assessedSubmissionIds.isEmpty()) {
            teamAssessmentQueueRepository.findSubmissions(assessedSubmissionIds)
                    .forEach(submission -> submissionByParticipation.putIfAbsent(submission.participationId(), submission));
        }

        List<Long> submissionIds = submissionByParticipation.values().stream().map(AssessmentQueueSubmissionDTO::id).toList();
        Map<Long, List<AssessmentQueueResultDTO>> resultsBySubmission = submissionIds.isEmpty() ? Map.of()
                : teamAssessmentQueueRepository.findResults(submissionIds).stream().collect(Collectors.groupingBy(AssessmentQueueResultDTO::submissionId));
        return entries.stream().filter(entry -> submissionByParticipation.containsKey(entry.participationId())).map(entry -> {
            AssessmentQueueSubmissionDTO submission = submissionByParticipation.get(entry.participationId());
            return new AssessmentQueueTeamDTO(entry.participationId(), entry.teamId(), entry.shortName(),
                    submission.toResponse(exercise.getExerciseType(), resultsBySubmission.getOrDefault(submission.id(), List.of())));
        }).toList();
    }

    private static boolean canStartAssessments(Exercise exercise, ZonedDateTime now) {
        return !(exercise instanceof ProgrammingExercise programmingExercise) || (programmingExercise.areManualResultsAllowed() && !isBuildAndTestDatePending(exercise, now));
    }

    private static boolean isBuildAndTestDatePending(Exercise exercise, ZonedDateTime now) {
        return exercise instanceof ProgrammingExercise programmingExercise && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().isAfter(now);
    }
}
