package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

/**
 * Shared FINALIZING placement logic for all exercise types. Places the generated variant per the wizard's
 * placement choice by delegating to the same {@link ExerciseVariantGroupService} the variant-group REST
 * endpoints use — reuse, not duplication.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantPlacementService {

    private static final Logger log = LoggerFactory.getLogger(VariantPlacementService.class);

    /** Why a quiz cannot join a variant group: joining stamps the group's timeline onto it (see ExerciseVariantGroupService). */
    private static final String QUIZ_NOT_EDITABLE_REASON = "a quiz that has already started or ended cannot join a variant group";

    /** Error key {@code ExerciseVariantGroupService} rejects a started or ended quiz member with — the only recoverable assignment failure. */
    private static final String QUIZ_NOT_EDITABLE_ERROR_KEY = "quizMemberNotEditable";

    /** Why a source cannot join its variant's new group: it is already in one, whether read before or claimed by a parallel job. */
    private static final String SOURCE_ALREADY_GROUPED_REASON = "it already belongs to another variant group";

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseVariantGroupService exerciseVariantGroupService;

    private final ExerciseRepository exerciseRepository;

    public VariantPlacementService(ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseVariantGroupService exerciseVariantGroupService,
            ExerciseRepository exerciseRepository) {
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseVariantGroupService = exerciseVariantGroupService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Applies the requested placement to the (already persisted) variant exercise.
     * <ul>
     * <li>Exam variants are already placed: the provisioner imports them into the source's exam exercise group
     * (SAME_EXAM_GROUP), so nothing happens here.</li>
     * <li>STANDALONE (or no placement) leaves the variant ungrouped.</li>
     * <li>EXISTING_GROUP assigns the variant to the given group of the variant's course.</li>
     * <li>NEW_GROUP first creates the group in the variant's course, then assigns the variant to it.</li>
     * </ul>
     *
     * @param variant          the persisted variant exercise
     * @param sourceExerciseId the id of the exercise the variant was generated from
     * @param request          the wizard request carrying the placement choice
     * @return instructor-facing warnings for placement steps that could not be carried out (currently only a
     *         NEW_GROUP source that turned out to be ineligible), empty when the placement was applied in full
     */
    public List<String> place(Exercise variant, Long sourceExerciseId, VariantGenerationRequestDTO request) {
        VariantPlacementDTO placement = request.placement();
        if (variant.isExamExercise()) {
            // Placed at provisioning time via the source's exam exercise group; course variant groups do not apply.
            return List.of();
        }
        if (placement == null || placement.type() == null) {
            return List.of();
        }
        switch (placement.type()) {
            case STANDALONE, SAME_EXAM_GROUP -> {
                // STANDALONE: nothing to assign. SAME_EXAM_GROUP outside an exam is rejected at the REST boundary;
                // tolerate it here as a no-op instead of failing an otherwise successful job.
            }
            case EXISTING_GROUP -> {
                Course course = requireCourse(variant);
                ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(placement.existingGroupId(), course.getId());
                exerciseVariantGroupService.assignToGroup(variant, group);
                log.debug("Placed variant exercise {} into existing variant group {}", variant.getId(), group.getId());
            }
            case NEW_GROUP -> {
                Course course = requireCourse(variant);
                if (placement.newGroup() == null) {
                    throw new IllegalStateException("NEW_GROUP placement without a group payload (should have been rejected at the REST boundary)");
                }
                if (!exerciseVariantGroupService.canJoinGroup(variant)) {
                    // A quiz clone inherits the source's dates, so a source whose batch started produces a variant
                    // that cannot join a group. Creating the group first would leave it behind, empty.
                    log.warn("Not creating a variant group for variant exercise {}: {}", variant.getId(), QUIZ_NOT_EDITABLE_REASON);
                    return List.of("FINALIZING: the variant was left ungrouped — " + QUIZ_NOT_EDITABLE_REASON);
                }
                // Same payload and entity mapping as the group-creation endpoint, so the wizard's new-group form
                // (title, maxPoints, shared timeline dates) is applied in full.
                Exercise source = exerciseRepository.findByIdElseThrow(sourceExerciseId);
                String sourceSkipReason = sourceSkipReason(source, course);
                ExerciseVariantGroup group = exerciseVariantGroupService.createGroup(course.getId(), placement.newGroup().toEntity());
                if (sourceSkipReason == null) {
                    // The wizard presents NEW_GROUP as "group the variant WITH its source", so a date it left empty
                    // is adopted from the source, not the clone. Seeded onto the still-empty group instead of by
                    // letting the source join first: source membership is persisted and stamps the group's timeline
                    // onto the instructor's own exercise, which a failed variant placement would then have to undo.
                    exerciseVariantGroupService.adoptMissingDatesFromExercise(group, source);
                }
                try {
                    exerciseVariantGroupService.assignToGroup(variant, group);
                }
                catch (BadRequestAlertException raced) {
                    if (!QUIZ_NOT_EDITABLE_ERROR_KEY.equals(raced.getErrorKey())) {
                        // Any other rejection (e.g. a programming exercise's invalid build plan configuration) can be
                        // raised AFTER membership was persisted; dropping the group would then delete a live one.
                        throw raced;
                    }
                    // The variant became ineligible between the check above and here — its quiz batch started while
                    // the group was being prepared. Nothing has joined yet, so the group goes with it.
                    exerciseVariantGroupRepository.delete(group);
                    log.warn("Could not place variant exercise {} into the new variant group: {}", variant.getId(), raced.getMessage());
                    return List.of("FINALIZING: the variant was left ungrouped — " + QUIZ_NOT_EDITABLE_REASON);
                }
                log.debug("Placed variant exercise {} into new variant group {}", variant.getId(), group.getId());
                if (sourceSkipReason == null) {
                    sourceSkipReason = assignSourceToNewGroup(source, group);
                }
                if (sourceSkipReason != null) {
                    // The wizard promised "group the variant with its original". The variant is in the new group,
                    // the source is not — reporting COMPLETED here would be a false success, so surface it.
                    return List.of("FINALIZING: the variant was placed in the new group, but its source exercise was not added — " + sourceSkipReason);
                }
            }
        }
        return List.of();
    }

    /**
     * Why the source exercise cannot join the group freshly created for its variants — the wizard's NEW_GROUP promise.
     * Sources a group cannot legally contain are skipped instead of failing the job (the variant's own placement, the
     * actual job outcome, still happens): exam exercises, sources that moved to another group mid-job, non-individual
     * quizzes, and quizzes that have started or ended (all rejected by the assignment service). Eligibility is
     * re-checked here rather than only at the REST boundary because the source can change while generation runs.
     *
     * @param source the exercise the variant was generated from
     * @param course the course of the variant, which the source must share
     * @return null when the source may join, otherwise the instructor-facing reason it is skipped
     */
    @Nullable
    private String sourceSkipReason(Exercise source, Course course) {
        Course sourceCourse = source.getCourseViaExerciseGroupOrCourseMember();
        if (source.isExamExercise() || sourceCourse == null || !Objects.equals(course.getId(), sourceCourse.getId())) {
            log.warn("Not adding source exercise {} to a new variant group: not a course exercise of course {}", source.getId(), course.getId());
            return "it is not an exercise of this course";
        }
        if (source.getExerciseVariantGroup() != null) {
            log.warn("Not adding source exercise {} to a new variant group: it already belongs to group {}", source.getId(), source.getExerciseVariantGroup().getId());
            return SOURCE_ALREADY_GROUPED_REASON;
        }
        if (source instanceof QuizExercise quizExercise && quizExercise.getQuizMode() != QuizMode.INDIVIDUAL) {
            log.warn("Not adding source quiz {} to a new variant group: only individual-mode quizzes can join a group", source.getId());
            return "only individual-mode quizzes can join a variant group";
        }
        if (!exerciseVariantGroupService.canJoinGroup(source)) {
            log.warn("Not adding source exercise {} to a new variant group: {}", source.getId(), QUIZ_NOT_EDITABLE_REASON);
            return QUIZ_NOT_EDITABLE_REASON;
        }
        return null;
    }

    /**
     * Adds the eligible source exercise to the group its variant now belongs to. Runs after the variant's own
     * assignment, so a rejection here costs a warning instead of a rollback of persisted membership.
     *
     * Membership is claimed atomically first. {@link #sourceSkipReason} read the source before the group was even
     * created, and two jobs generated from the same source run in parallel by design, so by now another one may have
     * grouped it: assigning on that stale reading would take the source out of the group the other job created and
     * leave it without the original the wizard promised — while both jobs report success. The conditional claim lets
     * only one job through and turns the other into the skip it already knows how to report.
     *
     * @param source the exercise the variant was generated from
     * @param group  the group the variant was just placed in
     * @return null when the source was added, otherwise the instructor-facing reason it was skipped
     */
    @Nullable
    private String assignSourceToNewGroup(Exercise source, ExerciseVariantGroup group) {
        if (exerciseVariantGroupRepository.claimExerciseIfUngrouped(source.getId(), group.getId()) != 1) {
            log.warn("Not adding source exercise {} to new variant group {}: another job grouped it while this one was running", source.getId(), group.getId());
            return SOURCE_ALREADY_GROUPED_REASON;
        }
        try {
            exerciseVariantGroupService.assignToGroup(source, group);
        }
        catch (BadRequestAlertException raced) {
            if (!QUIZ_NOT_EDITABLE_ERROR_KEY.equals(raced.getErrorKey())) {
                // Only the quiz race is a skip; every other rejection is a real placement failure for the caller. The
                // claim stays: such a rejection can be raised AFTER membership was persisted (see the variant's own
                // assignment above), and releasing it would then ungroup an exercise whose timeline was already written.
                throw raced;
            }
            // The source's quiz batch started between the eligibility check and the assignment. That check is the
            // first thing the assignment does, so nothing but the claim was written and the claim goes back.
            exerciseVariantGroupRepository.releaseExerciseFromGroup(source.getId(), group.getId());
            log.warn("Could not add source exercise {} to new variant group {}: {}", source.getId(), group.getId(), raced.getMessage());
            return QUIZ_NOT_EDITABLE_REASON;
        }
        log.debug("Added source exercise {} to new variant group {}", source.getId(), group.getId());
        return null;
    }

    private Course requireCourse(Exercise variant) {
        Course course = variant.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            throw new IllegalStateException("Variant exercise " + variant.getId() + " has no course; cannot apply group placement");
        }
        return course;
    }
}
