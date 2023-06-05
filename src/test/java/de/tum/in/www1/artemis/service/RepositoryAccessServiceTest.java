package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

class RepositoryAccessServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "rastest";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryAccessService repositoryAccessService;

    User student;

    Course course;

    ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        student = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().stream().iterator().next();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testShouldEnforceLockRepositoryPolicy() {
        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, student.getLogin());
        database.createSubmissionAndResult(participation, 50, true);

        // Add LockRepositoryPolicy to the programmingExercise.
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setActive(true);
        lockRepositoryPolicy.setSubmissionLimit(1);
        database.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);

        // Should throw an AccessForbiddenException because the submission limit is already reached.
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, student, programmingExercise, RepositoryActionType.WRITE));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    // Student should not have access to the tests repository.
    void testShouldDenyAccessToTestRepository(boolean atLeastEditor) {
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkAccessTestRepositoryElseThrow(atLeastEditor, programmingExercise, student));
    }
}
