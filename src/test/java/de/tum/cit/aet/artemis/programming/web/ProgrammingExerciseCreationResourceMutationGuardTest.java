package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.programming.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingExerciseCreationResourceMutationGuardTest {

    private static final long EXERCISE_ID = 42L;

    private final ProgrammingExerciseTestRepository repository = mock(ProgrammingExerciseTestRepository.class);

    private final UserTestRepository userRepository = mock(UserTestRepository.class);

    private final AuthorizationCheckService authCheckService = mock(AuthorizationCheckService.class);

    private final ProgrammingExerciseCreationUpdateService creationUpdateService = mock(ProgrammingExerciseCreationUpdateService.class);

    private final CourseService courseService = mock(CourseService.class);

    private final ProgrammingExerciseValidationService validationService = mock(ProgrammingExerciseValidationService.class);

    private final ExerciseVersionService exerciseVersionService = mock(ExerciseVersionService.class);

    private final ProgrammingExerciseMutationGuardService mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);

    private final Runnable leaseRelease = mock(Runnable.class);

    private final User user = new User();

    private ProgrammingExerciseCreationResource resource;

    @BeforeEach
    void setUp() {
        when(userRepository.getUserWithAuthorities()).thenReturn(user);
        when(userRepository.getUser()).thenReturn(user);
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenReturn(new ProgrammingExerciseMutationGuardService.MutationLease(leaseRelease));
        resource = new ProgrammingExerciseCreationResource(authCheckService, courseService, validationService, creationUpdateService, mock(StaticCodeAnalysisService.class),
                Optional.empty(), repository, userRepository, exerciseVersionService, mutationGuard);
    }

    @Test
    void setupCreatesTheInitialVersionBeforeReturningTheExercise() throws Exception {
        Course course = mock(Course.class);
        ProgrammingExercise requestExercise = mock(ProgrammingExercise.class);
        ProgrammingExercise createdExercise = mock(ProgrammingExercise.class);
        when(createdExercise.getId()).thenReturn(EXERCISE_ID);
        when(courseService.retrieveCourseOverExerciseGroupOrCourseId(requestExercise)).thenReturn(course);
        when(creationUpdateService.createProgrammingExercise(requestExercise, false)).thenReturn(createdExercise);

        var response = resource.createProgrammingExercise(requestExercise, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(createdExercise);
        var order = inOrder(creationUpdateService, exerciseVersionService);
        order.verify(creationUpdateService).createProgrammingExercise(requestExercise, false);
        order.verify(exerciseVersionService).createExerciseVersionSynchronously(createdExercise, user);
    }

    @Test
    void generateStructureOracleRefetchesAuthoritativeMutationDataAndCreatesVersionSynchronously() throws Exception {
        ProgrammingExercise authorizationExercise = exercise("authorization", false);
        ProgrammingExercise authoritativeExercise = exercise("authoritative", true);
        when(repository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID)).thenReturn(authorizationExercise,
                authoritativeExercise);
        when(creationUpdateService.generateStructureOracleFile(authoritativeExercise.getVcsSolutionRepositoryUri(), authoritativeExercise.getVcsTemplateRepositoryUri(),
                authoritativeExercise.getVcsTestRepositoryUri(), "structural/test/authoritative", user)).thenReturn(true);

        var response = resource.generateStructureOracleForExercise(EXERCISE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repository, times(2)).findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID);
        verify(creationUpdateService).generateStructureOracleFile(authoritativeExercise.getVcsSolutionRepositoryUri(), authoritativeExercise.getVcsTemplateRepositoryUri(),
                authoritativeExercise.getVcsTestRepositoryUri(), "structural/test/authoritative", user);
        verify(exerciseVersionService).createExerciseVersionSynchronously(authoritativeExercise, user);

        var order = inOrder(repository, authCheckService, mutationGuard, creationUpdateService, exerciseVersionService, leaseRelease);
        order.verify(repository).findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID);
        order.verify(authCheckService).checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, authorizationExercise, user);
        order.verify(mutationGuard).claimExternalMutation(EXERCISE_ID);
        order.verify(repository).findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID);
        order.verify(creationUpdateService).generateStructureOracleFile(authoritativeExercise.getVcsSolutionRepositoryUri(), authoritativeExercise.getVcsTemplateRepositoryUri(),
                authoritativeExercise.getVcsTestRepositoryUri(), "structural/test/authoritative", user);
        order.verify(exerciseVersionService).createExerciseVersionSynchronously(authoritativeExercise, user);
        order.verify(leaseRelease).run();
    }

    @Test
    void generateStructureOraclePreservesMutationGuardConflict() {
        ProgrammingExercise authorizationExercise = exercise("authorization", false);
        when(repository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID)).thenReturn(authorizationExercise);
        when(mutationGuard.claimExternalMutation(EXERCISE_ID)).thenThrow(new ConflictException("Generation is running", "programmingExercise", "generationRunning"));

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> resource.generateStructureOracleForExercise(EXERCISE_ID));

        var order = inOrder(repository, authCheckService, mutationGuard);
        order.verify(repository).findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID);
        order.verify(authCheckService).checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, authorizationExercise, user);
        order.verify(mutationGuard).claimExternalMutation(EXERCISE_ID);
        verify(repository).findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID);
        verifyNoInteractions(creationUpdateService, exerciseVersionService, leaseRelease);
    }

    @Test
    void generateStructureOracleReleasesLeaseAfterSynchronousVersionFailure() throws Exception {
        ProgrammingExercise authorizationExercise = exercise("authorization", false);
        ProgrammingExercise authoritativeExercise = exercise("authoritative", false);
        when(repository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesAndBuildConfigElseThrow(EXERCISE_ID)).thenReturn(authorizationExercise,
                authoritativeExercise);
        when(creationUpdateService.generateStructureOracleFile(authoritativeExercise.getVcsSolutionRepositoryUri(), authoritativeExercise.getVcsTemplateRepositoryUri(),
                authoritativeExercise.getVcsTestRepositoryUri(), "test/authoritative", user)).thenReturn(true);
        var versionFailure = new IllegalStateException("version failure");
        org.mockito.Mockito.doThrow(versionFailure).when(exerciseVersionService).createExerciseVersionSynchronously(authoritativeExercise, user);

        var response = resource.generateStructureOracleForExercise(EXERCISE_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var order = inOrder(creationUpdateService, exerciseVersionService, leaseRelease);
        order.verify(creationUpdateService).generateStructureOracleFile(authoritativeExercise.getVcsSolutionRepositoryUri(), authoritativeExercise.getVcsTemplateRepositoryUri(),
                authoritativeExercise.getVcsTestRepositoryUri(), "test/authoritative", user);
        order.verify(exerciseVersionService).createExerciseVersionSynchronously(authoritativeExercise, user);
        order.verify(leaseRelease).run();
    }

    private ProgrammingExercise exercise(String repositorySuffix, boolean sequentialTestRuns) {
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getPackageName()).thenReturn("example");
        when(exercise.getPackageFolderName()).thenReturn(repositorySuffix);
        when(exercise.getProjectName()).thenReturn(repositorySuffix);
        when(exercise.getVcsSolutionRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        when(exercise.getVcsTemplateRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        when(exercise.getVcsTestRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        ProgrammingExerciseBuildConfig buildConfig = mock(ProgrammingExerciseBuildConfig.class);
        when(buildConfig.hasSequentialTestRuns()).thenReturn(sequentialTestRuns);
        when(exercise.getBuildConfig()).thenReturn(buildConfig);
        return exercise;
    }
}
