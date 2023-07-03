package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

class RepositoryAccessServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "rastest";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private RepositoryAccessService repositoryAccessService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseGradingService programmingExerciseGradingService;

    User student;

    Course course;

    ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        student = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().stream().iterator().next();
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testShouldEnforceLockRepositoryPolicy() throws Exception {
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, student.getLogin());

        // Add LockRepositoryPolicy to the programmingExercise.
        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setActive(true);
        lockRepositoryPolicy.setSubmissionLimit(1);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);

        // Process a new result for the submission. This should lock the participation, because the submission limit is reached.
        BambooBuildResultNotificationDTO bambooBuildResult = ProgrammingExerciseFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, null, null, null, List.of(),
                List.of(), new ArrayList<>());
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockGetPushDate(programmingExercise.getProjectKey(), TestConstants.COMMIT_HASH_STRING, ZonedDateTime.now());
        bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly((programmingExercise.getProjectKey() + "-" + participation.getParticipantIdentifier()).toLowerCase(),
                programmingExercise.getProjectKey(), participation.getStudents());
        programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, bambooBuildResult);

        // Should throw an AccessForbiddenException because the submission limit is already reached.
        AccessForbiddenException exception = catchThrowableOfType(
                () -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, student, programmingExercise, RepositoryActionType.WRITE),
                AccessForbiddenException.class);
        assertThat(exception.getMessage()).isEqualTo("submitAfterReachingSubmissionLimit");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    // Student should not have access to the tests repository.
    void testShouldDenyAccessToTestRepository(boolean atLeastEditor) {
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkAccessTestRepositoryElseThrow(atLeastEditor, programmingExercise, student));
    }
}
