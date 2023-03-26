package de.tum.in.www1.artemis.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

public class RepositoryAccessServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "rastest";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepositoryAccessService repositoryAccessService;

    User user;

    Course course;

    ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        user = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().stream().iterator().next();
    }

    @Test
    void testShouldEnforceLockRepositoryPolicy() {
        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, user.getLogin());
        database.createSubmissionAndResult(participation, 50, true);

        // Add LockRepositoryPolicy to the programmingExercise.
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setActive(true);
        lockRepositoryPolicy.setSubmissionLimit(1);
        database.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);

        // Should throw an AccessForbiddenException because the submission limit is already reached.
        assertThrows(AccessForbiddenException.class,
                () -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, programmingExercise, RepositoryActionType.WRITE));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    void testShouldDenyAccessToTestRepository(boolean atLeastEditor) {
        assertThrows(AccessForbiddenException.class, () -> repositoryAccessService.checkAccessTestRepositoryElseThrow(atLeastEditor, programmingExercise, user));
    }
}
