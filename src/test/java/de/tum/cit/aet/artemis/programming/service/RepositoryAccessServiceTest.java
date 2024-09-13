package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseResultTestService.convertBuildResultToJsonObject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.TestConstants;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsGitlabTest;

class RepositoryAccessServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

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
        gitlabRequestMockProvider.enableMockingOfRequests();
        final var commitMap = new HashMap<String, ZonedDateTime>();
        commitMap.put(TestConstants.COMMIT_HASH_STRING, ZonedDateTime.now());
        gitlabRequestMockProvider.mockGetPushDate(participation, commitMap);
        mockRepositoryWritePermissionsForStudent(student, participation.getProgrammingExercise(), HttpStatus.OK);
        final var repoName = (programmingExercise.getProjectKey() + "-" + student).toUpperCase();
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(programmingExercise.getProjectKey() + " » " + repoName + " #3", repoName, null,
                programmingExercise.getProgrammingLanguage(), false, List.of("test1"), List.of(), new ArrayList<>(), new ArrayList<>(), null);
        final var resultRequestBody = convertBuildResultToJsonObject(notification);
        programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, resultRequestBody);

        // Should throw an AccessForbiddenException because the submission limit is already reached.
        AccessForbiddenException exception = catchThrowableOfType(AccessForbiddenException.class,
                () -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, student, programmingExercise, RepositoryActionType.WRITE));

        assertThat(exception.getMessage()).isEqualTo("You are not allowed to access the repository of this programming exercise.");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    // Student should not have access to the tests repository.
    void testShouldDenyAccessToTestRepository(boolean atLeastEditor) {
        assertThatExceptionOfType(AccessForbiddenException.class)
                .isThrownBy(() -> repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(atLeastEditor, programmingExercise, student, "test"));
    }
}
