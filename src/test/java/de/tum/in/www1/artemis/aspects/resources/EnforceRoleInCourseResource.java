package de.tum.in.www1.artemis.aspects.resources;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceRoleInCourse;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/test/")
public class EnforceRoleInCourseResource {

    @GetMapping("testEnforceAtLeastStudentInCourseExplicit/{courseId}")
    @EnforceRoleInCourse(Role.STUDENT)
    public ResponseEntity<Void> testEnforceAtLeastStudentInCourseExplicit(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInCourse/{courseId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Void> testEnforceAtLeastStudentInCourse(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInCourseExplicit/{courseId}")
    @EnforceRoleInCourse(Role.TEACHING_ASSISTANT)
    public ResponseEntity<Void> testEnforceAtLeastTutorInCourseExplicit(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInCourse/{courseId}")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<Void> testEnforceAtLeastTutorInCourse(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInCourseExplicit/{courseId}")
    @EnforceRoleInCourse(Role.EDITOR)
    public ResponseEntity<Void> testEnforceAtLeastEditorInCourseExplicit(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInCourse/{courseId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> testEnforceAtLeastEditorInCourse(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInCourseExplicit/{courseId}")
    @EnforceRoleInCourse(Role.INSTRUCTOR)
    public ResponseEntity<Void> testEnforceAtLeastInstructorInCourseExplicit(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInCourse/{courseId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> testEnforceAtLeastInstructorInCourse(@PathVariable long courseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceRoleInCourseFieldName/{renamedCourseId}")
    @EnforceRoleInCourse(value = Role.STUDENT, resourceIdFieldName = "renamedCourseId")
    public ResponseEntity<Void> testEnforceRoleInCourseFieldName(@PathVariable long renamedCourseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInCourseFieldName/{renamedCourseId}")
    @EnforceAtLeastStudentInCourse(resourceIdFieldName = "renamedCourseId")
    public ResponseEntity<Void> testEnforceAtLeastStudentInCourseFieldName(@PathVariable long renamedCourseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInCourseFieldName/{renamedCourseId}")
    @EnforceAtLeastTutorInCourse(resourceIdFieldName = "renamedCourseId")
    public ResponseEntity<Void> testEnforceAtLeastTutorInCourseFieldName(@PathVariable long renamedCourseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInCourseFieldName/{renamedCourseId}")
    @EnforceAtLeastEditorInCourse(resourceIdFieldName = "renamedCourseId")
    public ResponseEntity<Void> testEnforceAtLeastEditorInCourseFieldName(@PathVariable long renamedCourseId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInCourseFieldName/{renamedCourseId}")
    @EnforceAtLeastInstructorInCourse(resourceIdFieldName = "renamedCourseId")
    public ResponseEntity<Void> testEnforceAtLeastInstructorInCourseFieldName(@PathVariable long renamedCourseId) {
        return ResponseEntity.ok().build();
    }
}
