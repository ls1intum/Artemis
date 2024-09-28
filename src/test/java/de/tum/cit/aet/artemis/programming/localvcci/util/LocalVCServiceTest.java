package de.tum.cit.aet.artemis.programming.localvcci.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class LocalVCServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localvcservice";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Test
    void testHealth() {
        ConnectorHealth health = versionControlService.health();
        assertThat(health.additionalInfo().get("url")).isEqualTo(localVCBaseUrl);
    }

    @Test
    void testUnsupportedMethods() {
        versionControlService.addMemberToRepository(null, null, null);
        versionControlService.removeMemberFromRepository(null, null);
        versionControlService.setRepositoryPermissionsToReadOnly(null, null, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLockingAndUnlockingShouldReturnNotFound() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        // Locking and unlocking repositories is not available for the local version control system.
        request.post("/api/programming-exercises/" + programmingExercise.getId() + "/lock-all-repositories", null, HttpStatus.NOT_FOUND);
        request.post("/api/programming-exercises/" + programmingExercise.getId() + "/unlock-all-repositories", null, HttpStatus.NOT_FOUND);

        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lock-all-repositories", null, HttpStatus.NOT_FOUND);
        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/unlock-all-repositories", null, HttpStatus.NOT_FOUND);

        verifyNoInteractions(versionControlService);
        verifyNoInteractions(instanceMessageSendService);
    }
}
