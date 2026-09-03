package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Keeps a {@link MilestoneExercise}'s {@code maxPoints} equal to the sum of its group's {@link UserStoryExercise}
 * points.
 * <p>
 * A milestone is the only exercise of its group that counts towards a student's score, and what it is worth is exactly
 * what its user stories are worth together - it is never configured by hand, which is why the milestone creation form
 * has no points field at all. Two things depend on the value being right:
 * <ul>
 * <li>the score a student ends up with, since {@code MilestoneScoreService} expresses their summed story points as a
 * percentage of it, and</li>
 * <li>the static code analysis budget, since {@code maxStaticCodeAnalysisPenalty} is a percentage of the exercise's
 * {@code maxPoints} - keeping the milestone's points equal to the group's total is what makes "at most 20 % of this
 * group's points may be lost to code quality" mean what it says.</li>
 * </ul>
 * Call this whenever a group's membership or a member's points change. It is idempotent: it recomputes the sum from the
 * database and only writes when the value actually moved.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MilestoneExercisePointsService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneExercisePointsService.class);

    private final MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    public MilestoneExercisePointsService(MilestoneExerciseGroupRepository milestoneExerciseGroupRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            InstanceMessageSendService instanceMessageSendService) {
        this.milestoneExerciseGroupRepository = milestoneExerciseGroupRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * Recomputes the milestone exercise's {@code maxPoints} from its group's current members.
     * <p>
     * When the value changes, every student's score on the milestone becomes stale - it is a percentage of this number -
     * so the whole group is scheduled for recomputation.
     *
     * @param milestoneExerciseId the id of the milestone exercise to sync
     */
    public void syncMaxPoints(long milestoneExerciseId) {
        ProgrammingExercise milestoneExercise = programmingExerciseRepository.findById(milestoneExerciseId).orElse(null);
        if (!(milestoneExercise instanceof MilestoneExercise)) {
            // Deleted concurrently, or never a milestone in the first place - nothing to keep in sync.
            return;
        }
        double summedPoints = milestoneExerciseGroupRepository.sumUserStoryMaxPointsByMilestoneExerciseId(milestoneExerciseId).orElse(0.0);
        if (Objects.equals(milestoneExercise.getMaxPoints(), summedPoints)) {
            return;
        }
        log.debug("Milestone exercise {} points change from {} to {} (sum of its user stories).", milestoneExerciseId, milestoneExercise.getMaxPoints(), summedPoints);
        milestoneExercise.setMaxPoints(summedPoints);
        programmingExerciseRepository.save(milestoneExercise);
        instanceMessageSendService.sendMilestoneScoreScheduleForGroup(milestoneExerciseId);
    }

    /**
     * Recomputes the {@code maxPoints} of the milestone owning the given user story, if it has one.
     *
     * @param userStoryExerciseId the id of the user story whose group's milestone to sync
     */
    public void syncMaxPointsForUserStory(long userStoryExerciseId) {
        milestoneExerciseGroupRepository.findMilestoneExerciseIdByUserStoryExerciseId(userStoryExerciseId).ifPresent(this::syncMaxPoints);
    }
}
