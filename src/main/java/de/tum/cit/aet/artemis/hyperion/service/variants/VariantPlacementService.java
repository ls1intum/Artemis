package de.tum.cit.aet.artemis.hyperion.service.variants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;

/**
 * Shared FINALIZING placement logic for all exercise types (plan Sections 3/4 FINALIZING rows, 5.5 and 8:
 * "Finalizer: shared implementation already works"). Places the generated variant per the wizard's placement
 * choice by delegating to the same {@link ExerciseVariantGroupService} the variant-group REST endpoints use —
 * reuse, not duplication.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantPlacementService {

    private static final Logger log = LoggerFactory.getLogger(VariantPlacementService.class);

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseVariantGroupService exerciseVariantGroupService;

    public VariantPlacementService(ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseVariantGroupService exerciseVariantGroupService) {
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseVariantGroupService = exerciseVariantGroupService;
    }

    /**
     * Applies the requested placement to the (already persisted) variant exercise.
     * <ul>
     * <li>Exam variants are already placed: the provisioner imports them into the source's exam exercise group
     * (SAME_EXAM_GROUP, plan Section 5.5), so nothing happens here.</li>
     * <li>STANDALONE (or no placement) leaves the variant ungrouped.</li>
     * <li>EXISTING_GROUP assigns the variant to the given group of the variant's course.</li>
     * <li>NEW_GROUP first creates the group in the variant's course, then assigns the variant to it.</li>
     * </ul>
     *
     * @param variant the persisted variant exercise
     * @param request the wizard request carrying the placement choice
     */
    public void place(Exercise variant, VariantGenerationRequestDTO request) {
        VariantPlacementDTO placement = request.placement();
        if (variant.isExamExercise()) {
            // Placed at provisioning time via the source's exam exercise group; course variant groups do not apply.
            return;
        }
        if (placement == null || placement.type() == null) {
            return;
        }
        switch (placement.type()) {
            case STANDALONE, SAME_EXAM_GROUP -> {
                // STANDALONE: nothing to assign. SAME_EXAM_GROUP outside an exam is rejected at the REST boundary;
                // tolerate it here as a no-op instead of failing an otherwise successful job.
            }
            case EXISTING_GROUP -> {
                Course course = requireCourse(variant);
                ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(placement.existingGroupId(), course.getId());
                exerciseVariantGroupService.assignExerciseToGroup(variant, group);
                log.debug("Placed variant exercise {} into existing variant group {}", variant.getId(), group.getId());
            }
            case NEW_GROUP -> {
                Course course = requireCourse(variant);
                if (placement.newGroup() == null) {
                    throw new IllegalStateException("NEW_GROUP placement without a group payload (should have been rejected at the REST boundary)");
                }
                // Same payload and entity mapping as the group-creation endpoint, so the wizard's new-group form
                // (title, maxPoints, shared timeline dates) is applied in full.
                ExerciseVariantGroup group = exerciseVariantGroupService.createGroup(course.getId(), placement.newGroup().toEntity());
                exerciseVariantGroupService.assignExerciseToGroup(variant, group);
                log.debug("Placed variant exercise {} into new variant group {}", variant.getId(), group.getId());
            }
        }
    }

    private Course requireCourse(Exercise variant) {
        Course course = variant.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            throw new IllegalStateException("Variant exercise " + variant.getId() + " has no course; cannot apply group placement");
        }
        return course;
    }
}
