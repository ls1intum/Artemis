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
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastInstructorInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastStudentInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastTutorInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceRoleInLectureUnit;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/test/")
public class EnforceRoleInLectureUnitResource {

    @GetMapping("testEnforceAtLeastStudentInLectureUnitExplicit/{lectureUnitId}")
    @EnforceRoleInLectureUnit(Role.STUDENT)
    public ResponseEntity<Void> testEnforceAtLeastStudentInLectureUnitExplicit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInLectureUnit/{lectureUnitId}")
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<Void> testEnforceAtLeastStudentInLectureUnit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInLectureUnitExplicit/{lectureUnitId}")
    @EnforceRoleInLectureUnit(Role.TEACHING_ASSISTANT)
    public ResponseEntity<Void> testEnforceAtLeastTutorInLectureUnitExplicit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInLectureUnit/{lectureUnitId}")
    @EnforceAtLeastTutorInLectureUnit
    public ResponseEntity<Void> testEnforceAtLeastTutorInLectureUnit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInLectureUnitExplicit/{lectureUnitId}")
    @EnforceRoleInLectureUnit(Role.EDITOR)
    public ResponseEntity<Void> testEnforceAtLeastEditorInLectureUnitExplicit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInLectureUnit/{lectureUnitId}")
    @EnforceAtLeastEditorInLectureUnit
    public ResponseEntity<Void> testEnforceAtLeastEditorInLectureUnit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInLectureUnitExplicit/{lectureUnitId}")
    @EnforceRoleInLectureUnit(Role.INSTRUCTOR)
    public ResponseEntity<Void> testEnforceAtLeastInstructorInLectureUnitExplicit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInLectureUnit/{lectureUnitId}")
    @EnforceAtLeastInstructorInLectureUnit
    public ResponseEntity<Void> testEnforceAtLeastInstructorInLectureUnit(@PathVariable long lectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceRoleInLectureUnitFieldName/{renamedLectureUnitId}")
    @EnforceRoleInLectureUnit(value = Role.STUDENT, resourceIdFieldName = "renamedLectureUnitId")
    public ResponseEntity<Void> testEnforceRoleInLectureUnitFieldName(@PathVariable long renamedLectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastStudentInLectureUnitFieldName/{renamedLectureUnitId}")
    @EnforceAtLeastStudentInLectureUnit(resourceIdFieldName = "renamedLectureUnitId")
    public ResponseEntity<Void> testEnforceAtLeastStudentInLectureUnitFieldName(@PathVariable long renamedLectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastTutorInLectureUnitFieldName/{renamedLectureUnitId}")
    @EnforceAtLeastTutorInLectureUnit(resourceIdFieldName = "renamedLectureUnitId")
    public ResponseEntity<Void> testEnforceAtLeastTutorInLectureUnitFieldName(@PathVariable long renamedLectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastEditorInLectureUnitFieldName/{renamedLectureUnitId}")
    @EnforceAtLeastEditorInLectureUnit(resourceIdFieldName = "renamedLectureUnitId")
    public ResponseEntity<Void> testEnforceAtLeastEditorInLectureUnitFieldName(@PathVariable long renamedLectureUnitId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testEnforceAtLeastInstructorInLectureUnitFieldName/{renamedLectureUnitId}")
    @EnforceAtLeastInstructorInLectureUnit(resourceIdFieldName = "renamedLectureUnitId")
    public ResponseEntity<Void> testEnforceAtLeastInstructorInLectureUnitFieldName(@PathVariable long renamedLectureUnitId) {
        return ResponseEntity.ok().build();
    }
}
