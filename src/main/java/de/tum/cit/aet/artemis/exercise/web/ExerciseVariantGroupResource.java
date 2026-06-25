package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupAssignmentDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;

/**
 * REST controller for managing {@link ExerciseVariantGroup}s, the course-owned groupings of interchangeable exercise
 * variants.
 * <p>
 * Authorization mirrors the rights for the exercises themselves: editors create, update and read groups (and assign
 * exercises to them), while only instructors may delete a group. Every endpoint additionally verifies that the targeted
 * group (and exercise) belongs to the course in the request path.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ExerciseVariantGroupResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantGroupResource.class);

    private static final String ENTITY_NAME = "exerciseVariantGroup";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseRepository courseRepository;

    private final ExerciseVariantGroupRepository exerciseVariantGroupRepository;

    private final ExerciseRepository exerciseRepository;

    public ExerciseVariantGroupResource(CourseRepository courseRepository, ExerciseVariantGroupRepository exerciseVariantGroupRepository, ExerciseRepository exerciseRepository) {
        this.courseRepository = courseRepository;
        this.exerciseVariantGroupRepository = exerciseVariantGroupRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * POST /courses/:courseId/exercise-variant-groups : Create a new exercise variant group in the given course.
     *
     * @param createDTO the settings of the group to create
     * @param courseId  the id of the course that will own the group
     * @return the ResponseEntity with status 201 (Created) and the created group in the body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/exercise-variant-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> createExerciseVariantGroup(@Valid @RequestBody CreateExerciseVariantGroupDTO createDTO, @PathVariable Long courseId)
            throws URISyntaxException {
        log.debug("REST request to create ExerciseVariantGroup in course {} : {}", courseId, createDTO);
        // The course owns the unidirectional collection (the course_id FK lives on this table but is managed from the
        // Course side). Persist the group first so it gets an id, then attach it to the course so the FK is written.
        ExerciseVariantGroup group = exerciseVariantGroupRepository.save(createDTO.toEntity());
        Course course = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(courseId);
        course.addExerciseVariantGroup(group);
        courseRepository.save(course);
        return ResponseEntity.created(new URI("/api/exercise/courses/" + courseId + "/exercise-variant-groups/" + group.getId())).body(new ExerciseVariantGroupDTO(group));
    }

    /**
     * PUT /courses/:courseId/exercise-variant-groups/:groupId : Update an existing exercise variant group. The owning
     * course cannot be changed.
     *
     * @param updateDTO the new settings of the group
     * @param groupId   the id of the group to update
     * @param courseId  the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the updated group in the body
     */
    @PutMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> updateExerciseVariantGroup(@Valid @RequestBody UpdateExerciseVariantGroupDTO updateDTO, @PathVariable Long groupId,
            @PathVariable Long courseId) {
        log.debug("REST request to update ExerciseVariantGroup {} in course {} : {}", groupId, courseId, updateDTO);
        if (!Objects.equals(groupId, updateDTO.id())) {
            throw new BadRequestAlertException("The id in the path and the body must match", ENTITY_NAME, "idMismatch");
        }
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
        updateDTO.applyTo(group);
        exerciseVariantGroupRepository.save(group);
        // Variants share the group's timeline. Keep every member exercise's own dates in sync with the (possibly changed)
        // group dates so they stay consistent wherever an exercise's dates are read (exercise lists, calendar, grading).
        applyGroupTimelineToExercises(group);
        // Build the response from the loaded entity (its exercises were fetched); the save() return value is a re-merged
        // instance whose lazy exercises collection cannot initialize once the session is closed (open-in-view is off).
        return ResponseEntity.ok(new ExerciseVariantGroupDTO(group));
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups : Get all exercise variant groups of a course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of groups in the body
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<ExerciseVariantGroupDTO>> getExerciseVariantGroupsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ExerciseVariantGroups for course {}", courseId);
        List<ExerciseVariantGroupDTO> groups = exerciseVariantGroupRepository.findAllByCourseId(courseId).stream().map(ExerciseVariantGroupDTO::new).toList();
        return ResponseEntity.ok(groups);
    }

    /**
     * GET /courses/:courseId/exercise-variant-groups/:groupId : Get a single exercise variant group.
     *
     * @param groupId  the id of the group to retrieve
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the group in the body
     */
    @GetMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<ExerciseVariantGroupDTO> getExerciseVariantGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to get ExerciseVariantGroup {} in course {}", groupId, courseId);
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
        return ResponseEntity.ok(new ExerciseVariantGroupDTO(group));
    }

    /**
     * DELETE /courses/:courseId/exercise-variant-groups/:groupId : Delete an exercise variant group. The aggregated
     * exercises survive and simply lose their group membership (the foreign key is set to null).
     *
     * @param groupId  the id of the group to delete
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/exercise-variant-groups/{groupId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteExerciseVariantGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to delete ExerciseVariantGroup {} in course {}", groupId, courseId);
        // Load the group without its member exercises: keeping them out of the persistence context lets the
        // ON DELETE SET NULL foreign key (see the Liquibase changelog) ungroup them, instead of Hibernate failing the
        // flush because managed exercises still reference the removed group. The members survive, simply ungrouped.
        ExerciseVariantGroup group = exerciseVariantGroupRepository.findByIdAndCourseIdWithoutExercisesElseThrow(groupId, courseId);
        exerciseVariantGroupRepository.delete(group);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, group.getTitle())).build();
    }

    /**
     * PUT /courses/:courseId/exercises/:exerciseId/variant-group : Assign an exercise to a variant group, or remove it
     * from its current group. Membership is edited from the exercise side, so moving an exercise between groups is a
     * single request.
     *
     * @param assignmentDTO the target group ({@code groupId == null} removes the exercise from its group)
     * @param exerciseId    the id of the exercise to (re-)assign
     * @param courseId      the id of the course the exercise (and the target group) belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("courses/{courseId}/exercises/{exerciseId}/variant-group")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> setExerciseVariantGroup(@RequestBody ExerciseVariantGroupAssignmentDTO assignmentDTO, @PathVariable Long exerciseId, @PathVariable Long courseId) {
        log.debug("REST request to assign exercise {} in course {} to variant group {}", exerciseId, courseId, assignmentDTO.groupId());
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        Course exerciseCourse = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (exerciseCourse == null || !Objects.equals(exerciseCourse.getId(), courseId)) {
            throw new BadRequestAlertException("The exercise does not belong to the course in the path", ENTITY_NAME, "courseIdMismatch");
        }
        ExerciseVariantGroup group = assignmentDTO.groupId() == null ? null : exerciseVariantGroupRepository.findByIdAndCourseIdElseThrow(assignmentDTO.groupId(), courseId);
        exercise.setExerciseVariantGroup(group);
        if (group != null) {
            // Joining a group means adopting the group's shared timeline (even unset dates), so the variant's dates stay
            // consistent with its siblings. Removing an exercise (group == null) leaves its current dates untouched.
            applyGroupTimeline(group, exercise);
        }
        exerciseRepository.save(exercise);
        return ResponseEntity.ok().build();
    }

    /**
     * Copies the group's shared timeline onto every member exercise and persists them, keeping the variants' own dates in
     * sync with the group.
     *
     * @param group the group whose (already fetched) member exercises should adopt its timeline
     */
    private void applyGroupTimelineToExercises(ExerciseVariantGroup group) {
        group.getExercises().forEach(exercise -> applyGroupTimeline(group, exercise));
        exerciseRepository.saveAll(group.getExercises());
    }

    /**
     * Overwrites the exercise's timeline fields with the group's, including unset (null) dates, so that all variants in a
     * group share one timeline.
     *
     * @param group    the group providing the shared timeline
     * @param exercise the member exercise to update in place (not persisted here)
     */
    private void applyGroupTimeline(ExerciseVariantGroup group, Exercise exercise) {
        exercise.setReleaseDate(group.getReleaseDate());
        exercise.setStartDate(group.getStartDate());
        exercise.setDueDate(group.getDueDate());
        exercise.setAssessmentDueDate(group.getAssessmentDueDate());
        exercise.setExampleSolutionPublicationDate(group.getExampleSolutionPublicationDate());
    }
}
