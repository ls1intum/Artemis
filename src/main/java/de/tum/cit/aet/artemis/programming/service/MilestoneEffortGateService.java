package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.repository.UserStoryEffortRepository;

/**
 * Decides whether a participant may write to a milestone group's shared repository yet.
 * <p>
 * A milestone group's {@link UserStoryExercise}s all share the anchor {@link MilestoneExercise}'s repository, so there is
 * one place work arrives for the whole group - and one place to require that every story the participant has started
 * carries a time estimate before more work lands on top of it.
 * <p>
 * Consulted from the two paths that can write to that repository, which are enforced separately because they do not
 * share code: {@code LocalVCPrePushHook} for git pushes over HTTP and SSH, and the online code editor's commit endpoint
 * in {@code RepositoryProgrammingExerciseParticipationResource}, which commits without ever reaching a git hook.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MilestoneEffortGateService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneEffortGateService.class);

    private final MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    private final UserStoryEffortRepository userStoryEffortRepository;

    private final AuthorizationCheckService authCheckService;

    public MilestoneEffortGateService(MilestoneExerciseGroupRepository milestoneExerciseGroupRepository, UserStoryEffortRepository userStoryEffortRepository,
            AuthorizationCheckService authCheckService) {
        this.milestoneExerciseGroupRepository = milestoneExerciseGroupRepository;
        this.userStoryEffortRepository = userStoryEffortRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * The user story exercises that block the participant from writing to this repository: the ones they have started but
     * not yet estimated.
     * <p>
     * Only stories the participant already has a participation in are asked about, so the block can always be cleared -
     * a story that was never started has nowhere to record an estimate. Only the <em>estimated</em> effort is required:
     * the actual effort is by definition unknowable before the work the write contains.
     * <p>
     * Returns empty - i.e. allows the write - for everything that is not a student writing to a milestone repository:
     * template/solution/test repositories, non-milestone exercises, and teaching staff, who must be able to set an
     * exercise up and to assist in a participant's repository.
     *
     * @param exercise      the exercise the repository belongs to
     * @param participation the participation being written to
     * @param user          the user performing the write
     * @return the titles of the started-but-unestimated stories, empty when nothing blocks the write
     */
    public List<String> findStoriesBlockingWrite(ProgrammingExercise exercise, ProgrammingExerciseParticipation participation, User user) {
        try {
            if (!(exercise instanceof MilestoneExercise) || !(participation instanceof ProgrammingExerciseStudentParticipation)) {
                return List.of();
            }
            if (authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                return List.of();
            }
            MilestoneExerciseGroup group = milestoneExerciseGroupRepository.findByMilestoneExerciseIdWithExercises(exercise.getId()).orElse(null);
            if (group == null) {
                return List.of();
            }
            return userStoryEffortRepository.findStartedStoryTitlesWithoutEstimateByGroupIdAndStudentLogin(group.getId(), user.getLogin());
        }
        catch (Exception e) {
            // Deliberately fail open. This runs on the hot path of every push in the course; refusing everyone's work
            // because of an unexpected error here would be a far worse outcome than letting an unestimated story through.
            log.error("Could not determine whether the milestone effort gate blocks user {} on exercise {}; allowing the write", user.getLogin(), exercise.getId(), e);
            return List.of();
        }
    }

    /**
     * Renders the blocking stories as the single line a student sees - in a git client after {@code ! [remote rejected]},
     * or as an alert in the online editor. Names the stories rather than counting them, so the student knows where to go.
     *
     * @param blockingStoryTitles the titles returned by {@link #findStoriesBlockingWrite}, which must not be empty
     * @return the message to reject the write with
     */
    public String buildRejectionMessage(List<String> blockingStoryTitles) {
        return "Enter an estimated effort for these user stories before pushing: " + String.join(", ", blockingStoryTitles);
    }
}
