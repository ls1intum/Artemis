package de.tum.cit.aet.artemis.programming.web.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.RepositoryParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryResourceMutationGuardTest {

    private static final long EXERCISE_ID = 42L;

    private HazelcastInstance hazelcastInstance;

    @BeforeAll
    void startHazelcast() {
        Config config = new Config();
        config.setClusterName("repository-mutation-guard-test-" + System.nanoTime());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    @Test
    void saveFilesAndCommitChanges_blocksGenerationDuringUncommittedWrites_andDoesNotClaimANestedLease() throws Exception {
        GenerationJobService jobService = new GenerationJobService(hazelcastInstance, event -> {
        }, mock(LLMTokenUsageService.class), null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run);
        jobService.init();
        ProgrammingExerciseMutationGuardService mutationGuard = new ProgrammingExerciseMutationGuardService(Optional.of(new HyperionExerciseMutationApi(jobService)),
                hazelcastInstance);
        UserRepository userRepository = mock(UserRepository.class);
        User user = user("instructor");
        when(userRepository.getUser()).thenReturn(user);
        RepositoryService repositoryService = mock(RepositoryService.class);
        LocalVCServletService localVCServletService = mock(LocalVCServletService.class);
        AtomicInteger guardedMutationStages = new AtomicInteger();
        TestRepositoryResource resource = new TestRepositoryResource(userRepository, repositoryService, localVCServletService, mutationGuard, () -> {
            guardedMutationStages.incrementAndGet();
            assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> jobService.startJob(user("generator"), exercise(), "generate", GenerationMode.GENERATE));
        });
        Repository repository = mock(Repository.class);

        resource.saveFilesAndCommit(repository);

        assertThat(guardedMutationStages).hasValue(2);
        verify(repositoryService).commitChanges(repository, user);
        assertThat(jobService.startJob(user("generator"), exercise(), "generate after save", GenerationMode.GENERATE)).isNotBlank();
    }

    @Test
    void participationMutationGuard_onlyProtectsHyperionTemplateAndSolutionArtifacts() {
        long studentParticipationId = 11L;
        long templateParticipationId = 12L;
        long solutionParticipationId = 13L;
        ParticipationRepository participationRepository = mock(ParticipationRepository.class);
        ProgrammingExerciseStudentParticipation studentParticipation = mock(ProgrammingExerciseStudentParticipation.class);
        TemplateProgrammingExerciseParticipation templateParticipation = mock(TemplateProgrammingExerciseParticipation.class);
        SolutionProgrammingExerciseParticipation solutionParticipation = mock(SolutionProgrammingExerciseParticipation.class);
        when(participationRepository.findByIdElseThrow(studentParticipationId)).thenReturn(studentParticipation);
        when(participationRepository.findByIdElseThrow(templateParticipationId)).thenReturn(templateParticipation);
        when(participationRepository.findByIdElseThrow(solutionParticipationId)).thenReturn(solutionParticipation);
        ProgrammingExerciseRepository programmingExerciseRepository = mock(ProgrammingExerciseRepository.class);
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(templateParticipation)).thenReturn(exercise());
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(solutionParticipation)).thenReturn(exercise());
        RepositoryProgrammingExerciseParticipationResource resource = participationResource(participationRepository, programmingExerciseRepository);

        assertThat(resource.getExerciseIdForMutation(studentParticipationId)).isEmpty();
        assertThat(resource.getExerciseIdForMutation(templateParticipationId)).hasValue(EXERCISE_ID);
        assertThat(resource.getExerciseIdForMutation(solutionParticipationId)).hasValue(EXERCISE_ID);
    }

    @Test
    void auxiliaryRepositoryMutationGuard_resolvesTheOwningExercise() {
        AuxiliaryRepositoryRepository auxiliaryRepositoryRepository = mock(AuxiliaryRepositoryRepository.class);
        when(auxiliaryRepositoryRepository.findExerciseIdById(17L)).thenReturn(Optional.of(EXERCISE_ID));
        AuxiliaryRepositoryResource resource = new AuxiliaryRepositoryResource(mock(UserRepository.class), mock(AuthorizationCheckService.class), mock(GitService.class),
                mock(RepositoryService.class), mock(ProgrammingExerciseRepository.class), mock(RepositoryAccessService.class), Optional.empty(), auxiliaryRepositoryRepository,
                new ProgrammingExerciseMutationGuardService(Optional.empty(), mock(HazelcastInstance.class)));

        assertThat(resource.getExerciseIdForMutation(17L)).hasValue(EXERCISE_ID);
    }

    @Test
    void auxiliaryRepositoryMutationGuard_rejectsMissingOwnershipInsteadOfUsingANoOpLease() {
        AuxiliaryRepositoryRepository auxiliaryRepositoryRepository = mock(AuxiliaryRepositoryRepository.class);
        AuxiliaryRepositoryResource resource = auxiliaryResource(auxiliaryRepositoryRepository, mock(UserRepository.class), mock(RepositoryAccessService.class),
                mock(GitService.class), mock(ProgrammingExerciseMutationGuardService.class));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> resource.getExerciseIdForMutation(17L));
    }

    @Test
    void auxiliaryBatchUpdateRefetchesTheRepositoryAfterClaimingTheExerciseSlot() throws Exception {
        long auxiliaryRepositoryId = 17L;
        ProgrammingExercise exercise = exercise();
        LocalVCRepositoryUri staleUri = mock(LocalVCRepositoryUri.class);
        LocalVCRepositoryUri currentUri = mock(LocalVCRepositoryUri.class);
        AuxiliaryRepository stale = mock(AuxiliaryRepository.class);
        AuxiliaryRepository current = mock(AuxiliaryRepository.class);
        when(stale.getExercise()).thenReturn(exercise);
        when(stale.getVcsRepositoryUri()).thenReturn(staleUri);
        when(current.getExercise()).thenReturn(exercise);
        when(current.getVcsRepositoryUri()).thenReturn(currentUri);
        AuxiliaryRepositoryRepository auxiliaryRepositoryRepository = mock(AuxiliaryRepositoryRepository.class);
        when(auxiliaryRepositoryRepository.findByIdElseThrow(auxiliaryRepositoryId)).thenReturn(stale, current);
        when(auxiliaryRepositoryRepository.findExerciseIdById(auxiliaryRepositoryId)).thenReturn(Optional.of(EXERCISE_ID));
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithGroupsAndAuthorities("instructor")).thenReturn(user("instructor"));
        RepositoryAccessService repositoryAccessService = mock(RepositoryAccessService.class);
        GitService gitService = mock(GitService.class);
        when(gitService.getOrCheckoutRepository(currentUri, true, true)).thenReturn(mock(Repository.class));
        ProgrammingExerciseMutationGuardService mutationGuard = mock(ProgrammingExerciseMutationGuardService.class);
        when(mutationGuard.claimExternalMutation(OptionalLong.of(EXERCISE_ID))).thenReturn(new ProgrammingExerciseMutationGuardService.MutationLease(() -> {
        }));
        AuxiliaryRepositoryResource resource = auxiliaryResource(auxiliaryRepositoryRepository, userRepository, repositoryAccessService, gitService, mutationGuard);
        Principal principal = () -> "instructor";

        resource.updateAuxiliaryFiles(auxiliaryRepositoryId, List.of(), false, principal);

        verify(gitService).getOrCheckoutRepository(currentUri, true, true);
        verify(gitService, never()).getOrCheckoutRepository(staleUri, true, true);
    }

    private static AuxiliaryRepositoryResource auxiliaryResource(AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, UserRepository userRepository,
            RepositoryAccessService repositoryAccessService, GitService gitService, ProgrammingExerciseMutationGuardService mutationGuard) {
        return new AuxiliaryRepositoryResource(userRepository, mock(AuthorizationCheckService.class), gitService, mock(RepositoryService.class),
                mock(ProgrammingExerciseRepository.class), repositoryAccessService, Optional.empty(), auxiliaryRepositoryRepository, mutationGuard);
    }

    private static RepositoryProgrammingExerciseParticipationResource participationResource(ParticipationRepository participationRepository,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        return new RepositoryProgrammingExerciseParticipationResource(mock(UserRepository.class), mock(AuthorizationCheckService.class),
                mock(ParticipationAuthorizationCheckService.class), mock(GitService.class), mock(RepositoryService.class), mock(ProgrammingExerciseParticipationService.class),
                programmingExerciseRepository, participationRepository, mock(BuildLogEntryService.class), mock(ProgrammingSubmissionRepository.class),
                mock(SubmissionPolicyRepository.class), mock(RepositoryAccessService.class), Optional.empty(), mock(RepositoryParticipationService.class),
                new ProgrammingExerciseMutationGuardService(Optional.empty(), mock(HazelcastInstance.class)));
    }

    private static User user(String login) {
        User user = new User();
        user.setLogin(login);
        return user;
    }

    private static ProgrammingExercise exercise() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        return exercise;
    }

    private static final class TestRepositoryResource extends RepositoryResource {

        private final Runnable onSave;

        private TestRepositoryResource(UserRepository userRepository, RepositoryService repositoryService, LocalVCServletService localVCServletService,
                ProgrammingExerciseMutationGuardService mutationGuard, Runnable onSave) {
            super(userRepository, mock(AuthorizationCheckService.class), mock(GitService.class), repositoryService, mock(ProgrammingExerciseRepository.class),
                    mock(RepositoryAccessService.class), Optional.of(localVCServletService), mutationGuard);
            this.onSave = onSave;
        }

        private void saveFilesAndCommit(Repository repository) {
            saveFilesAndCommitChanges(EXERCISE_ID, List.of(), true, () -> {
                onSave.run();
                return repository;
            });
        }

        @Override
        protected Map<String, String> saveFileSubmissions(List<FileSubmission> submissions, Repository repository) {
            onSave.run();
            return Map.of();
        }

        @Override
        Repository getRepository(Long domainId, RepositoryActionType repositoryAction, boolean pullOnCheckout, boolean writeAccess) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        LocalVCRepositoryUri getRepositoryUri(Long domainId) {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean canAccessRepository(Long domainId) {
            throw new UnsupportedOperationException();
        }

        @Override
        String getOrRetrieveBranchOfDomainObject(Long domainID) {
            throw new UnsupportedOperationException();
        }

        @Override
        OptionalLong getExerciseIdForMutation(Long domainId) {
            return OptionalLong.of(EXERCISE_ID);
        }
    }
}
