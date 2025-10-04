package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.exercise.domain.PlannedExercise;
import de.tum.cit.aet.artemis.exercise.dto.PlannedExerciseCreateDTO;
import de.tum.cit.aet.artemis.exercise.repository.PlannedExerciseRepository;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/planned-exercise/")
public class PlannedExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(PlannedExerciseResource.class);

    private final PlannedExerciseRepository plannedExerciseRepository;

    private final CourseRepository courseRepository;

    public PlannedExerciseResource(PlannedExerciseRepository plannedExerciseRepository, CourseRepository courseRepository) {
        this.plannedExerciseRepository = plannedExerciseRepository;
        this.courseRepository = courseRepository;
    }

    // TODO: add validation? Specifically that always at least one date property is non-null?

    @PostMapping("courses/{courseId}/planned-exercises/batch")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<PlannedExercise>> createAll(@PathVariable Long courseId, @RequestBody List<PlannedExerciseCreateDTO> dtos) {
        log.debug("REST request to batch create PlannedExercises for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        List<PlannedExercise> plannedExercises = dtos.stream().map(PlannedExerciseCreateDTO::toDomainObject).toList();
        plannedExercises.forEach(plannedExercise -> plannedExercise.setCourse(course));
        List<PlannedExercise> savedPlannedExercises = plannedExerciseRepository.saveAll(plannedExercises);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPlannedExercises);
    }

    @PostMapping("courses/{courseId}/planned-exercises")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<PlannedExercise> create(@PathVariable Long courseId, @RequestBody PlannedExerciseCreateDTO dto) {
        log.debug("REST request to create PlannedExercise for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        PlannedExercise plannedExercise = PlannedExerciseCreateDTO.toDomainObject(dto);
        plannedExercise.setCourse(course);
        PlannedExercise savedPlannedExercise = plannedExerciseRepository.save(plannedExercise);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPlannedExercise);
    }

    @GetMapping("courses/{courseId}/planned-exercises")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<List<PlannedExercise>> getAll(@PathVariable Long courseId) {
        log.debug("REST request to get all PlannedExercises for course {}", courseId);
        List<PlannedExercise> plannedExercises = plannedExerciseRepository.findAllByCourseIdOrderByFirstAvailableDate(courseId);
        return ResponseEntity.ok(plannedExercises);
    }

    @PutMapping("courses/{courseId}/planned-exercises")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<PlannedExercise> update(@PathVariable Long courseId, @RequestBody PlannedExercise plannedExercise) {
        log.debug("REST request to update PlannedExercises {} for course {} with ", plannedExercise.getId(), courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        plannedExercise.setCourse(course);
        PlannedExercise savedPlannedExercise = plannedExerciseRepository.save(plannedExercise);
        return ResponseEntity.ok(savedPlannedExercise);
    }

    @DeleteMapping("courses/{courseId}/planned-exercises/{plannedExerciseId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> delete(@PathVariable Long courseId, @PathVariable Long plannedExerciseId) {
        log.debug("REST request to delete PlannedExercises {} for course {}", plannedExerciseId, courseId);
        plannedExerciseRepository.deleteById(plannedExerciseId);
        return ResponseEntity.noContent().build();
    }
}
