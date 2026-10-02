package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/** Placement is part of the common save, not permission to run a separate variant engine. Quiz placement is untouched. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationVariantPlacementService {

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    private final ExerciseVariantGroupRepository groups;

    private final ExerciseVariantGroupService groupService;

    private final ProgrammingExerciseRepository exercises;

    public GenerationVariantPlacementService(ExerciseVariantGroupRepository groups, ExerciseVariantGroupService groupService, ProgrammingExerciseRepository exercises) {
        this.groups = groups;
        this.groupService = groupService;
        this.exercises = exercises;
    }

    /**
     * Validates placement in the source's authorized course, before provisioning a destination.
     *
     * @param source  authorized source
     * @param request transformation and placement
     */
    public void validate(ProgrammingExercise source, VariantGenerationRequestDTO request) {
        VariantPlacementDTO placement = request.placement();
        if (placement == null || placement.type() == null) {
            throw new BadRequestAlertException("A placement choice is required", ENTITY_NAME, "missingPlacement");
        }
        if (source.isExamExercise()) {
            if (placement.type() != VariantPlacementDTO.PlacementType.SAME_EXAM_GROUP) {
                throw new BadRequestAlertException("Exam variants stay in their source exercise group", ENTITY_NAME, "invalidExamPlacement");
            }
            return;
        }
        switch (placement.type()) {
            case SAME_EXAM_GROUP -> throw new BadRequestAlertException("SAME_EXAM_GROUP requires an exam exercise", ENTITY_NAME, "invalidPlacement");
            case EXISTING_GROUP -> {
                if (placement.existingGroupId() == null) {
                    throw new BadRequestAlertException("A group id is required", ENTITY_NAME, "missingGroupId");
                }
                groups.findByIdAndCourseIdElseThrow(placement.existingGroupId(), source.getCourseViaExerciseGroupOrCourseMember().getId());
            }
            case NEW_GROUP -> {
                if (placement.newGroup() == null || placement.newGroup().title() == null || placement.newGroup().title().isBlank()) {
                    throw new BadRequestAlertException("A group title is required", ENTITY_NAME, "missingGroupTitle");
                }
                if (groups.findByExerciseId(source.getId()).isPresent()) {
                    throw new BadRequestAlertException("The source already belongs to a variant group", ENTITY_NAME, "sourceAlreadyGrouped");
                }
            }
            case STANDALONE -> {
            }
        }
    }

    /**
     * Places verified output while the common job still owns its destination. A concurrently grouped source is a warning, not a reason to discard verified work.
     *
     * @param event           admitted authoring run
     * @param verifyOwnership current destination ownership check
     * @return placement warnings
     */
    public List<String> place(GenerationStartedEvent event, Runnable verifyOwnership) {
        var preparation = event.variantPreparation();
        if (preparation == null) {
            return List.of();
        }
        var choice = preparation.request().placement();
        if (choice.type() == VariantPlacementDTO.PlacementType.STANDALONE || choice.type() == VariantPlacementDTO.PlacementType.SAME_EXAM_GROUP) {
            return List.of();
        }
        verifyOwnership.run();
        ProgrammingExercise destination = exercises.findByIdElseThrow(event.exercise().getId());
        long courseId = destination.getCourseViaExerciseGroupOrCourseMember().getId();
        ExerciseVariantGroup group;
        if (choice.type() == VariantPlacementDTO.PlacementType.EXISTING_GROUP) {
            group = groups.findByIdAndCourseIdElseThrow(choice.existingGroupId(), courseId);
        }
        else {
            group = groupService.createGroup(courseId, choice.newGroup().toEntity());
            ProgrammingExercise source = exercises.findByIdElseThrow(preparation.sourceExerciseId());
            if (groups.findByExerciseId(source.getId()).isEmpty() && source.getCourseViaExerciseGroupOrCourseMember().getId().equals(courseId)) {
                groupService.seedGroupDatesFromExercise(group, source);
            }
        }
        groupService.assignToGroupWhileAuthoring(destination.getId(), group, verifyOwnership);
        if (choice.type() == VariantPlacementDTO.PlacementType.NEW_GROUP) {
            try {
                if (!groupService.assignProgrammingSourceIfUngrouped(preparation.sourceExerciseId(), group)) {
                    return List.of("The variant was saved in its new group, but the source is no longer eligible to join it.");
                }
            }
            catch (ConflictException busySource) {
                return List.of("The variant was saved in its new group, but another operation currently protects the source. Add the source after that operation finishes.");
            }
        }
        return List.of();
    }
}
