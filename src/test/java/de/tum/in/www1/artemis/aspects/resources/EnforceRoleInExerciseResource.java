package de.tum.in.www1.artemis.aspects.resources;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceRoleInExercise;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/test/")
public class EnforceRoleInExerciseResource {

    @GetMapping("testEnforceAtLeastStudentInExerciseExplicit/{exerciseId}")
    @EnforceRoleInExercise(Role.STUDENT)
    public ResponseEntity<Void> testEnforceAtLeastStudentInExerciseExplicit(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInExercise/{exerciseId}")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> testEnforceAtLeastStudentInExercise(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInExerciseExplicit/{exerciseId}")
    @EnforceRoleInExercise(Role.TEACHING_ASSISTANT)
    public ResponseEntity<Void> testEnforceAtLeastTutorInExerciseExplicit(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInExercise/{exerciseId}")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<Void> testEnforceAtLeastTutorInExercise(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInExerciseExplicit/{exerciseId}")
    @EnforceRoleInExercise(Role.EDITOR)
    public ResponseEntity<Void> testEnforceAtLeastEditorInExerciseExplicit(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInExercise/{exerciseId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> testEnforceAtLeastEditorInExercise(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInExerciseExplicit/{exerciseId}")
    @EnforceRoleInExercise(Role.INSTRUCTOR)
    public ResponseEntity<Void> testEnforceAtLeastInstructorInExerciseExplicit(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInExercise/{exerciseId}")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<Void> testEnforceAtLeastInstructorInExercise(@PathVariable long exerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceRoleInExerciseFieldName/{renamedExerciseId}")
    @EnforceRoleInExercise(value = Role.STUDENT, resourceIdFieldName = "renamedExerciseId")
    public ResponseEntity<Void> testEnforceRoleInExerciseFieldName(@PathVariable long renamedExerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInExerciseFieldName/{renamedExerciseId}")
    @EnforceAtLeastStudentInExercise(resourceIdFieldName = "renamedExerciseId")
    public ResponseEntity<Void> testEnforceAtLeastStudentInExerciseFieldName(@PathVariable long renamedExerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInExerciseFieldName/{renamedExerciseId}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "renamedExerciseId")
    public ResponseEntity<Void> testEnforceAtLeastTutorInExerciseFieldName(@PathVariable long renamedExerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInExerciseFieldName/{renamedExerciseId}")
    @EnforceAtLeastEditorInExercise(resourceIdFieldName = "renamedExerciseId")
    public ResponseEntity<Void> testEnforceAtLeastEditorInExerciseFieldName(@PathVariable long renamedExerciseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInExerciseFieldName/{renamedExerciseId}")
    @EnforceAtLeastInstructorInExercise(resourceIdFieldName = "renamedExerciseId")
    public ResponseEntity<Void> testEnforceAtLeastInstructorInExerciseFieldName(@PathVariable long renamedExerciseId) {
        return ResponseEntity.ok().build();
    }
}
