package de.tum.cit.aet.artemis.core.aspects.util;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastEditorInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastInstructorInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastStudentInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastTutorInLecture;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceRoleInLecture;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/test/")
@Lazy
public class EnforceRoleInLectureResource {

    @GetMapping("testEnforceAtLeastStudentInLectureExplicit/{lectureId}")
    @EnforceRoleInLecture(Role.STUDENT)
    public ResponseEntity<Void> testEnforceAtLeastStudentInLectureExplicit(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInLecture/{lectureId}")
    @EnforceAtLeastStudentInLecture
    public ResponseEntity<Void> testEnforceAtLeastStudentInLecture(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInLectureExplicit/{lectureId}")
    @EnforceRoleInLecture(Role.TEACHING_ASSISTANT)
    public ResponseEntity<Void> testEnforceAtLeastTutorInLectureExplicit(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInLecture/{lectureId}")
    @EnforceAtLeastTutorInLecture
    public ResponseEntity<Void> testEnforceAtLeastTutorInLecture(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInLectureExplicit/{lectureId}")
    @EnforceRoleInLecture(Role.EDITOR)
    public ResponseEntity<Void> testEnforceAtLeastEditorInLectureExplicit(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInLecture/{lectureId}")
    @EnforceAtLeastEditorInLecture
    public ResponseEntity<Void> testEnforceAtLeastEditorInLecture(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInLectureExplicit/{lectureId}")
    @EnforceRoleInLecture(Role.INSTRUCTOR)
    public ResponseEntity<Void> testEnforceAtLeastInstructorInLectureExplicit(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInLecture/{lectureId}")
    @EnforceAtLeastInstructorInLecture
    public ResponseEntity<Void> testEnforceAtLeastInstructorInLecture(@PathVariable long lectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceRoleInLectureFieldName/{renamedLectureId}")
    @EnforceRoleInLecture(value = Role.STUDENT, resourceIdFieldName = "renamedLectureId")
    public ResponseEntity<Void> testEnforceRoleInLectureFieldName(@PathVariable long renamedLectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInLectureFieldName/{renamedLectureId}")
    @EnforceAtLeastStudentInLecture(resourceIdFieldName = "renamedLectureId")
    public ResponseEntity<Void> testEnforceAtLeastStudentInLectureFieldName(@PathVariable long renamedLectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInLectureFieldName/{renamedLectureId}")
    @EnforceAtLeastTutorInLecture(resourceIdFieldName = "renamedLectureId")
    public ResponseEntity<Void> testEnforceAtLeastTutorInLectureFieldName(@PathVariable long renamedLectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInLectureFieldName/{renamedLectureId}")
    @EnforceAtLeastEditorInLecture(resourceIdFieldName = "renamedLectureId")
    public ResponseEntity<Void> testEnforceAtLeastEditorInLectureFieldName(@PathVariable long renamedLectureId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInLectureFieldName/{renamedLectureId}")
    @EnforceAtLeastInstructorInLecture(resourceIdFieldName = "renamedLectureId")
    public ResponseEntity<Void> testEnforceAtLeastInstructorInLectureFieldName(@PathVariable long renamedLectureId) {
        return ResponseEntity.ok().build();
    }
}
