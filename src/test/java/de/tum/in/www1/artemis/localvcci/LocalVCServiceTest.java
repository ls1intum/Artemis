package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.LOCK_ALL_REPOSITORIES;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.UNLOCK_ALL_REPOSITORIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.service.connectors.ConnectorHealth;

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
        assertThat(health.getAdditionalInfo().get("url")).isEqualTo(localVCBaseUrl);
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
        // However, the ClientForwardResource will pick up these requests.
        request.postWithoutLocation(ROOT + LOCK_ALL_REPOSITORIES.replace("{exerciseId}", programmingExercise.getId().toString()), null, HttpStatus.OK, null);
        request.postWithoutLocation(ROOT + UNLOCK_ALL_REPOSITORIES.replace("{exerciseId}", programmingExercise.getId().toString()), null, HttpStatus.OK, null);

        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lock-all-repositories", null, HttpStatus.OK, null);
        request.postWithoutLocation("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/unlock-all-repositories", null, HttpStatus.OK, null);

        verifyNoInteractions(versionControlService);
        verifyNoInteractions(instanceMessageSendService);
    }
}
