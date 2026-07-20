package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

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
     */
    public void place(Exercise variant, Long sourceExerciseId, VariantGenerationRequestDTO request) {
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
                exerciseVariantGroupService.assignToGroup(variant, group);
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
                // The wizard presents NEW_GROUP as "group the variant WITH its source": pull the source in first
                // (so a date the wizard left empty is adopted from the source, not the clone), then the variant.
                assignSourceToNewGroup(sourceExerciseId, group, course);
                exerciseVariantGroupService.assignToGroup(variant, group);
                log.debug("Placed variant exercise {} into new variant group {}", variant.getId(), group.getId());
            }
        }
    }

    /**
     * Pulls the source exercise into the group freshly created for its variants — that is the wizard's NEW_GROUP
     * promise. Sources a group cannot legally contain are skipped with a warning instead of failing the job (the
     * variant's own placement, the actual job outcome, still happens): exam exercises, sources that moved to
     * another group mid-job, and non-individual quizzes (rejected by the assignment service).
     */
    private void assignSourceToNewGroup(Long sourceExerciseId, ExerciseVariantGroup group, Course course) {
        Exercise source = exerciseRepository.findByIdElseThrow(sourceExerciseId);
        Course sourceCourse = source.getCourseViaExerciseGroupOrCourseMember();
        if (source.isExamExercise() || sourceCourse == null || !Objects.equals(course.getId(), sourceCourse.getId())) {
            log.warn("Not adding source exercise {} to new variant group {}: not a course exercise of course {}", sourceExerciseId, group.getId(), course.getId());
            return;
        }
        if (source.getExerciseVariantGroup() != null) {
            log.warn("Not adding source exercise {} to new variant group {}: it already belongs to group {}", sourceExerciseId, group.getId(),
                    source.getExerciseVariantGroup().getId());
            return;
        }
        if (source instanceof QuizExercise quizExercise && quizExercise.getQuizMode() != QuizMode.INDIVIDUAL) {
            log.warn("Not adding source quiz {} to new variant group {}: only individual-mode quizzes can join a group", sourceExerciseId, group.getId());
            return;
        }
        exerciseVariantGroupService.assignToGroup(source, group);
        log.debug("Added source exercise {} to new variant group {}", sourceExerciseId, group.getId());
    }

    private Course requireCourse(Exercise variant) {
        Course course = variant.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            throw new IllegalStateException("Variant exercise " + variant.getId() + " has no course; cannot apply group placement");
        }
        return course;
    }
}
