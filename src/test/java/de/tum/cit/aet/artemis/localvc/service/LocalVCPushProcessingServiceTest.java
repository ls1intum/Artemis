package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseChangedService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

@ExtendWith(MockitoExtension.class)
class LocalVCPushProcessingServiceTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Mock
    private AuxiliaryRepositoryService auxiliaryRepositoryService;

    @Mock
    private ContinuousIntegrationTriggerService ciTriggerService;

    @Mock
    private ProgrammingSubmissionService programmingSubmissionService;

    @Mock
    private ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    @Mock
    private ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService;

    @Mock
    private ExerciseVersionService exerciseVersionService;

    @Mock
    private HyperionExerciseMutationApi hyperionMutationApi;

    @Mock
    private VcsAccessLogService vcsAccessLogService;

    @Spy
    private LocalVCRepositoryResolverService repositoryResolver = new LocalVCRepositoryResolverService();

    @InjectMocks
    private LocalVCPushProcessingService pushProcessingService;

    private User testUser;

    private ProgrammingExercise testExercise;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pushProcessingService, "hyperionMutationApi", Optional.of(hyperionMutationApi));
        ReflectionTestUtils.setField(pushProcessingService, "vcsAccessLogService", Optional.of(vcsAccessLogService));
        ReflectionTestUtils.setField(repositoryResolver, "localVCBasePath", Path.of("/tmp/test-repos"));
        ReflectionTestUtils.setField(repositoryResolver, "localVCBaseUri", URI.create("http://localhost"));

        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");

        Course testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setShortName("TEST");

        testExercise = new ProgrammingExercise();
        testExercise.setId(1L);
        testExercise.setShortName("EXERCISE");
        testExercise.setCourse(testCourse);
        testExercise.generateAndSetProjectKey();
    }

    @ParameterizedTest
    @CsvSource({ "false, false", "true, false", "false, true", "true, true" })
    void processNewPushVersionsBeforeCreatingTheTestSubmissionWithConfiguredDispatch(boolean coordinated, boolean examExercise) {
        when(hyperionMutationApi.isCoordinationEnabled()).thenReturn(coordinated);
        testExercise.setExerciseGroup(examExercise ? new ExerciseGroup() : null);
        Repository repository = mock(Repository.class);
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        ProgrammingSubmission submission = new ProgrammingSubmission();
        String projectKey = testExercise.getProjectKey();
        Path repositoryPath = Path.of("/tmp/test-repos", projectKey, projectKey.toLowerCase(java.util.Locale.ROOT) + "-tests.git");

        when(repository.getDirectory()).thenReturn(repositoryPath.toFile());
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.TESTS)).thenReturn(true);
        when(programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(testExercise.getId(), "commit-hash")).thenReturn(submission);

        pushProcessingService.processNewPush("commit-hash", repository, testUser, Optional.of(testExercise), Optional.of(solutionParticipation), Optional.empty());

        InOrder inOrder = inOrder(exerciseVersionService, programmingSubmissionService);
        if (coordinated && !examExercise) {
            inOrder.verify(exerciseVersionService).createExerciseVersionSynchronously(testExercise, testUser, RepositoryType.TESTS, null, "commit-hash");
            verify(exerciseVersionService, never()).createExerciseVersion(testExercise, testUser, RepositoryType.TESTS, null, "commit-hash");
        }
        else {
            inOrder.verify(exerciseVersionService).createExerciseVersion(testExercise, testUser, RepositoryType.TESTS, null, "commit-hash");
            verify(exerciseVersionService, never()).createExerciseVersionSynchronously(testExercise, testUser, RepositoryType.TESTS, null, "commit-hash");
        }
        inOrder.verify(programmingSubmissionService).createSolutionParticipationSubmissionWithTypeTest(testExercise.getId(), "commit-hash");
    }

    @Test
    void claimProgrammingExerciseMutationClaimsVersionableStaffRepository() {
        Repository repository = mock(Repository.class);
        Path repositoryPath = Path.of("/tmp/test-repos", testExercise.getProjectKey(), testExercise.getProjectKey().toLowerCase(java.util.Locale.ROOT) + "-exercise.git");
        when(repository.getDirectory()).thenReturn(repositoryPath.toFile());
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.TEMPLATE)).thenReturn(true);
        when(hyperionMutationApi.isCoordinationEnabled()).thenReturn(true);
        when(hyperionMutationApi.claimExternalMutationSlot(testExercise.getId())).thenReturn("staff-token");

        var lease = pushProcessingService.claimProgrammingExerciseMutation(repository, testExercise);

        assertThat(lease.coordinated()).isTrue();
        lease.close();
        verify(hyperionMutationApi).clearExternalMutationSlot(testExercise.getId(), "staff-token");
    }

    @Test
    void claimProgrammingExerciseMutationKeepsUserRepositoryUnguarded() {
        Repository repository = mock(Repository.class);
        Path repositoryPath = Path.of("/tmp/test-repos", testExercise.getProjectKey(), testExercise.getProjectKey().toLowerCase(java.util.Locale.ROOT) + "-testuser.git");
        when(repository.getDirectory()).thenReturn(repositoryPath.toFile());
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.USER)).thenReturn(false);

        var lease = pushProcessingService.claimProgrammingExerciseMutation(repository, testExercise);

        assertThat(lease.coordinated()).isFalse();
        lease.close();
        org.mockito.Mockito.verifyNoInteractions(hyperionMutationApi);
    }

    @Test
    void claimProgrammingExerciseMutationClaimsVersionableAuxiliaryRepository() {
        Repository repository = mock(Repository.class);
        Path repositoryPath = Path.of("/tmp/test-repos", testExercise.getProjectKey(), testExercise.getProjectKey().toLowerCase(java.util.Locale.ROOT) + "-customrepo.git");
        when(repository.getDirectory()).thenReturn(repositoryPath.toFile());
        when(auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise("customrepo", testExercise.getId())).thenReturn(true);
        when(exerciseVersionService.isRepositoryTypeVersionable(RepositoryType.AUXILIARY)).thenReturn(true);
        when(hyperionMutationApi.isCoordinationEnabled()).thenReturn(true);
        when(hyperionMutationApi.claimExternalMutationSlot(testExercise.getId())).thenReturn("staff-token");

        var lease = pushProcessingService.claimProgrammingExerciseMutation(repository, testExercise);

        assertThat(lease.coordinated()).isTrue();
        lease.close();
        verify(hyperionMutationApi).clearExternalMutationSlot(testExercise.getId(), "staff-token");
    }
}
