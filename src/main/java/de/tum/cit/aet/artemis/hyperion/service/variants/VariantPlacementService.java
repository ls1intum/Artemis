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
                ExerciseVariantGroup group = exerciseVariantGroupService.createGroup(course.getId(), placement.newGroup().toEntity());
                // The wizard presents NEW_GROUP as "group the variant WITH its source": pull the source in first
                // (so a date the wizard left empty is adopted from the source, not the clone), then the variant.
                String sourceSkipReason = assignSourceToNewGroup(sourceExerciseId, group, course);
                try {
                    exerciseVariantGroupService.assignToGroup(variant, group);
                }
                catch (BadRequestAlertException raced) {
                    // The variant became ineligible between the check above and here — its quiz batch started while
                    // the source was being added. Drop the group unless the source made it in, so no empty group is left.
                    if (sourceSkipReason != null) {
                        exerciseVariantGroupRepository.delete(group);
                    }
                    log.warn("Could not place variant exercise {} into the new variant group: {}", variant.getId(), raced.getMessage());
                    return List.of("FINALIZING: the variant was left ungrouped — " + QUIZ_NOT_EDITABLE_REASON);
                }
                log.debug("Placed variant exercise {} into new variant group {}", variant.getId(), group.getId());
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
     * Pulls the source exercise into the group freshly created for its variants — that is the wizard's NEW_GROUP
     * promise. Sources a group cannot legally contain are skipped instead of failing the job (the variant's own
     * placement, the actual job outcome, still happens): exam exercises, sources that moved to another group
     * mid-job, and non-individual quizzes (rejected by the assignment service). Eligibility is re-checked here
     * rather than only at the REST boundary because the source can change while generation runs.
     *
     * @return null when the source was added, otherwise the instructor-facing reason it was skipped
     */
    @Nullable
    private String assignSourceToNewGroup(Long sourceExerciseId, ExerciseVariantGroup group, Course course) {
        Exercise source = exerciseRepository.findByIdElseThrow(sourceExerciseId);
        Course sourceCourse = source.getCourseViaExerciseGroupOrCourseMember();
        if (source.isExamExercise() || sourceCourse == null || !Objects.equals(course.getId(), sourceCourse.getId())) {
            log.warn("Not adding source exercise {} to new variant group {}: not a course exercise of course {}", sourceExerciseId, group.getId(), course.getId());
            return "it is not an exercise of this course";
        }
        if (source.getExerciseVariantGroup() != null) {
            log.warn("Not adding source exercise {} to new variant group {}: it already belongs to group {}", sourceExerciseId, group.getId(),
                    source.getExerciseVariantGroup().getId());
            return "it already belongs to another variant group";
        }
        if (source instanceof QuizExercise quizExercise && quizExercise.getQuizMode() != QuizMode.INDIVIDUAL) {
            log.warn("Not adding source quiz {} to new variant group {}: only individual-mode quizzes can join a group", sourceExerciseId, group.getId());
            return "only individual-mode quizzes can join a variant group";
        }
        if (!exerciseVariantGroupService.canJoinGroup(source)) {
            log.warn("Not adding source exercise {} to new variant group {}: {}", sourceExerciseId, group.getId(), QUIZ_NOT_EDITABLE_REASON);
            return QUIZ_NOT_EDITABLE_REASON;
        }
        try {
            exerciseVariantGroupService.assignToGroup(source, group);
        }
        catch (BadRequestAlertException raced) {
            // The source's quiz batch started between the check above and the assignment.
            log.warn("Could not add source exercise {} to new variant group {}: {}", sourceExerciseId, group.getId(), raced.getMessage());
            return QUIZ_NOT_EDITABLE_REASON;
        }
        log.debug("Added source exercise {} to new variant group {}", sourceExerciseId, group.getId());
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
