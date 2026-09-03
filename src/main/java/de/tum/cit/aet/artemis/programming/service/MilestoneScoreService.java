package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Aggregates a {@link MilestoneExerciseGroup}'s points onto its anchor {@link MilestoneExercise}, which is the only
 * exercise of the group that counts towards a student's score:
 *
 * <pre>
 * milestone points = sum(points achieved on each UserStoryExercise) - static code analysis penalty
 * </pre>
 *
 * and {@code 0} outright if the shared codebase violates a {@link CategoryState#BLOCKING} category.
 * <p>
 * <b>Why the penalty is applied here and nowhere else.</b> Every user story of a group shares one repository and one CI
 * build, so a static code analysis violation belongs to the group's codebase rather than to any one story. Charging it
 * per story would charge it once per story. The build's SCA feedback therefore stays on the milestone result (the
 * fan-out in {@code ProgrammingExerciseGradingService} deliberately does not copy it down), it is priced exactly once by
 * the ordinary grading path against the milestone's {@code maxPoints} - which {@code MilestoneExerciseService} keeps
 * equal to the sum of the group's story points - and this service subtracts it once from the aggregate.
 * <p>
 * <b>Consistency.</b> This is deliberately a recomputation from current state rather than an incremental update, which
 * makes it idempotent and safe to run concurrently with itself or with a fan-out that is still writing story results.
 * A run that observes a half-written fan-out produces a transiently wrong value; every story result write schedules
 * another run (see {@code MilestoneScoreScheduleService}), so the value converges on the last write. This is the same
 * eventual-consistency contract participant scores already have.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MilestoneScoreService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneScoreService.class);

    private final MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ResultRepository resultRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    public MilestoneScoreService(MilestoneExerciseGroupRepository milestoneExerciseGroupRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseGradingService programmingExerciseGradingService, ResultRepository resultRepository, ScaFeedbackRepository scaFeedbackRepository) {
        this.milestoneExerciseGroupRepository = milestoneExerciseGroupRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.resultRepository = resultRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
    }

    /**
     * Recomputes and stores the given student's score on a milestone exercise from the current state of their user story
     * results and the milestone result's own static code analysis feedback.
     * <p>
     * Writing the result fires {@code ResultListener}, so the milestone's participant score and the course score follow
     * on their own. The listener only schedules a milestone recomputation for {@link UserStoryExercise} results, so this
     * write does not re-trigger this method.
     *
     * @param milestoneExerciseId the id of the milestone exercise to recompute
     * @param studentId           the id of the student whose milestone score to recompute
     * @return the updated milestone result, or empty if there is nothing to update (no group, no participation, or no
     *         rated result on the milestone participation yet)
     */
    public Optional<Result> recalculate(long milestoneExerciseId, long studentId) {
        Optional<MilestoneExerciseGroup> group = milestoneExerciseGroupRepository.findByMilestoneExerciseIdWithExercises(milestoneExerciseId);
        if (group.isEmpty()) {
            log.debug("No milestone group found for milestone exercise {}, skipping score aggregation.", milestoneExerciseId);
            return Optional.empty();
        }

        Optional<Result> milestoneResult = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentId(milestoneExerciseId, studentId)
                .flatMap(participation -> resultRepository.findLatestRatedResultWithFeedbacksForParticipation(participation.getId()));
        if (milestoneResult.isEmpty()) {
            // The student has not pushed anything to the shared repository yet, so there is no result to carry the
            // group's points. Their user story results cannot exist either - they are all derived from this one.
            log.debug("No rated milestone result for exercise {} and student {}, skipping score aggregation.", milestoneExerciseId, studentId);
            return Optional.empty();
        }
        Result result = milestoneResult.get();

        // Loaded separately from the group: the group's exercises collection holds the user stories, never the anchor
        // itself (see MilestoneExerciseGroup), and the aggregate needs the anchor's own maxPoints and course.
        MilestoneExercise milestoneExercise = (MilestoneExercise) programmingExerciseRepository.findByIdElseThrow(milestoneExerciseId);

        double achievedPoints = sumAchievedUserStoryPoints(group.get(), studentId);
        // Loaded through the repository rather than read off result.getScaFeedbacks(): Result#scaFeedbacks is a lazy
        // association and the query above fetches only Result#feedbacks, while this service runs outside any transaction
        // and off the scheduler rather than a web request (spring.jpa.open-in-view is false) - touching the collection
        // would throw LazyInitializationException. Only the categories and penalties are needed here, not the rule
        // messages, so the plain by-result query is enough.
        List<ScaFeedback> scaFeedback = scaFeedbackRepository.findByResultIds(List.of(result.getId()));
        double penaltyPoints = staticCodeAnalysisPenaltyPoints(scaFeedback);
        // The exact rule an ordinary exercise applies to its own result, applied here to the group's points instead.
        boolean blocked = !programmingExerciseGradingService.findBlockingStaticCodeAnalysisFeedback(milestoneExercise, scaFeedback).isEmpty();

        double points = blocked ? 0.0 : Math.max(0.0, achievedPoints - penaltyPoints);
        result.setScore(points, milestoneExercise.getMaxPoints(), milestoneExercise.getCourseViaExerciseGroupOrCourseMember());

        log.debug("Aggregated milestone {} for student {}: {} story points - {} penalty{} = {} of {} points.", milestoneExerciseId, studentId, achievedPoints, penaltyPoints,
                blocked ? " (blocked)" : "", points, milestoneExercise.getMaxPoints());

        return Optional.of(resultRepository.save(result));
    }

    /**
     * Sums the points the student achieved across the group's user stories. A story the student has not started, or has
     * no rated result for, contributes nothing - it still counts towards the milestone's {@code maxPoints}, which is
     * exactly the intended "an untouched story earns no points".
     */
    private double sumAchievedUserStoryPoints(MilestoneExerciseGroup group, long studentId) {
        double achievedPoints = 0.0;
        for (Exercise member : group.getExercises()) {
            if (!(member instanceof UserStoryExercise userStoryExercise)) {
                continue;
            }
            Optional<Result> storyResult = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentId(userStoryExercise.getId(), studentId)
                    .flatMap(participation -> resultRepository.findFirstBySubmissionParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), true));
            if (storyResult.isEmpty() || storyResult.get().getScore() == null) {
                continue;
            }
            achievedPoints += storyResult.get().getScore() / 100.0 * userStoryExercise.getMaxPoints();
        }
        return achievedPoints;
    }

    /**
     * Reads the penalty back off the milestone result's own static code analysis feedback instead of recomputing it. The
     * ordinary grading path already priced every issue there as a side effect of
     * {@code calculateStaticCodeAnalysisPenalty}, which writes each surviving issue's share into
     * {@link ScaFeedback#getPenalty()} - so the sum of those penalties <em>is</em> the group's penalty, already capped
     * per category and by {@code maxStaticCodeAnalysisPenalty}. Recomputing would mean running categorization a second
     * time against the same configuration for no gain.
     * <p>
     * Returned as a positive number, matching how the caller subtracts it - the negation of
     * {@link ScaFeedback#getCredits()}, which is the form the ordinary score calculation consumes.
     *
     * @param scaFeedback the milestone result's static code analysis feedback
     * @return the total penalty in points, as a non-negative number
     */
    private double staticCodeAnalysisPenaltyPoints(List<ScaFeedback> scaFeedback) {
        return scaFeedback.stream().mapToDouble(feedback -> Objects.requireNonNullElse(feedback.getPenalty(), 0.0)).sum();
    }

}
