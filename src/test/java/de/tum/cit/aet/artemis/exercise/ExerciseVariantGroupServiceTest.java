package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseEditorSyncService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService.MutationLease;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@ExtendWith(MockitoExtension.class)
class ExerciseVariantGroupServiceTest {

    @Mock
    private ExerciseVariantGroupRepository groupRepository;

    @Mock
    private ExerciseTestRepository exerciseRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private ProgrammingExerciseCreationUpdateService programmingUpdateService;

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private ExerciseService exerciseService;

    @Mock
    private ExerciseVersionService versionService;

    @Mock
    private InstanceMessageSendService messages;

    @Mock
    private QuizExerciseService quizService;

    @Mock
    private ProgrammingExerciseMutationGuardService mutationGuard;

    @Mock
    private ProgrammingExerciseTestRepository programmingRepository;

    @Mock
    private Runnable release;

    private ExerciseVariantGroupService service;

    @BeforeEach
    void setUp() {
        service = new ExerciseVariantGroupService(groupRepository, exerciseRepository, courseRepository, programmingUpdateService, participationRepository, exerciseService,
                versionService, messages, quizService, Optional.empty(), mutationGuard, programmingRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void busyProgrammingMemberRejectsWholeGroupUpdateAndReleasesEarlierClaims(boolean releaseFails) {
        var group = new ExerciseVariantGroup();
        var first = programmingExercise(1);
        var busy = programmingExercise(2);
        var text = new TextExercise();
        text.setId(3L);
        group.addExercise(first);
        group.addExercise(busy);
        group.addExercise(text);
        group.setDueDate(ZonedDateTime.now().plusDays(2));
        when(mutationGuard.claimExternalMutation(1L)).thenReturn(new MutationLease(release));
        when(mutationGuard.claimExternalMutation(2L)).thenThrow(new ConflictException("Generation is active", "exercise", "generationActive"));

        var releaseFailure = new IllegalStateException("Release unavailable");
        if (releaseFails) {
            doThrow(releaseFailure).when(release).run();
        }
        assertThatThrownBy(() -> service.saveWithTimelineAppliedToMembers(group)).isInstanceOf(ConflictException.class)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(releaseFails ? new Throwable[] { releaseFailure } : new Throwable[0]));

        verifyNoInteractions(groupRepository, exerciseRepository, courseRepository, programmingUpdateService, programmingRepository, versionService);
        assertThat(first.getDueDate()).isNull();
        assertThat(busy.getDueDate()).isNull();
        assertThat(text.getDueDate()).isNull();
        verify(release).run();
    }

    @Test
    void busyAssignmentDoesNotAdoptDatesOrSaveMembership() {
        var exercise = programmingExercise(1);
        exercise.setDueDate(ZonedDateTime.now().plusDays(2));
        var group = new ExerciseVariantGroup();
        when(mutationGuard.claimExternalMutation(1L)).thenThrow(new ConflictException("Generation is active", "exercise", "generationActive"));

        assertThatThrownBy(() -> service.assignToGroup(exercise, group)).isInstanceOf(ConflictException.class);

        verifyNoInteractions(groupRepository, exerciseRepository, courseRepository, programmingUpdateService, programmingRepository);
        assertThat(group.getDueDate()).isNull();
        assertThat(exercise.getExerciseVariantGroup()).isNull();
    }

    @Test
    void unassignmentReloadsExerciseAfterClaimInsteadOfOverwritingGeneratedContent() {
        var stale = programmingExercise(1);
        stale.setProblemStatement("Old statement");
        var current = programmingExercise(1);
        current.setProblemStatement("Generated statement");
        current.setExerciseVariantGroup(new ExerciseVariantGroup());
        when(mutationGuard.claimExternalMutation(1L)).thenReturn(new MutationLease(release));
        when(programmingRepository.findByIdElseThrow(Long.valueOf(1L))).thenAnswer(invocation -> {
            verify(mutationGuard).claimExternalMutation(1L);
            return current;
        });
        when(exerciseRepository.save(current)).thenReturn(current);

        service.assignToGroup(stale, null);

        verify(exerciseRepository).save(same(current));
        assertThat(current.getProblemStatement()).isEqualTo("Generated statement");
        assertThat(current.getExerciseVariantGroup()).isNull();
        verify(programmingUpdateService, never()).updateTimeline(any(), any());
        verify(release).run();
    }

    @ParameterizedTest
    @CsvSource({ "true,timeline", "false,timeline", "true,assignment", "false,assignment", "true,unassignment", "false,unassignment", "true,push", "false,push" })
    void coordinatedVersionCaptureFinishesBeforeLeaseRelease(boolean coordinated, String operation) {
        var current = spy(programmingExercise(1));
        current.setProblemStatement("Before release");
        var head = new AtomicReference<>("before-release");
        var released = new AtomicBoolean();
        var captured = new AtomicReference<ExerciseSnapshotDTO>();
        var versionTasks = new ArrayList<Runnable>();
        var repositoryUri = mock(LocalVCRepositoryUri.class);
        doReturn(repositoryUri).when(current).getVcsTestRepositoryUri();
        var git = mock(GitService.class);
        when(git.getLastCommitHash(repositoryUri)).thenAnswer(invocation -> head.get());
        var users = mock(UserTestRepository.class);
        var author = new User();
        author.setId(7L);
        if (!operation.equals("push"))
            when(users.getUser()).thenReturn(author);
        var versions = mock(ExerciseVersionTestRepository.class);
        when(versions.save(any(ExerciseVersion.class))).thenAnswer(invocation -> {
            ExerciseVersion version = invocation.getArgument(0);
            captured.set(version.getExerciseSnapshot());
            version.setId(99L);
            return version;
        });
        when(programmingRepository.findForVersioningById(current.getId())).thenReturn(Optional.of(current));
        var previousVersion = new ExerciseVersion();
        previousVersion.setId(98L);
        previousVersion
                .setExerciseSnapshot(ExerciseSnapshotDTO.of(current, null, new ProgrammingExerciseSnapshotDTO.CommitHashesDTO(null, null, "previous-commit", java.util.Map.of())));
        when(versions.findTopByExerciseIdOrderByCreatedDateDesc(1L)).thenReturn(Optional.of(previousVersion));
        var editorSync = mock(ExerciseEditorSyncService.class);
        var realVersions = new ExerciseVersionService(versions, git, programmingRepository, mock(), mock(), Optional.empty(), Optional.empty(), Optional.empty(), users, editorSync,
                mock(), mock(), _ -> {
                }, JsonObjectMapper.get(), versionTasks::add, mock(), mock());
        var guardedGroups = new ExerciseVariantGroupService(groupRepository, exerciseRepository, courseRepository, programmingUpdateService, participationRepository,
                exerciseService, realVersions, messages, quizService, Optional.empty(), mutationGuard, programmingRepository);
        when(mutationGuard.claimExternalMutation(1L)).thenReturn(new MutationLease(() -> {
            released.set(true);
            head.set("after-release");
            current.setProblemStatement("After release");
        }, coordinated));
        var group = new ExerciseVariantGroup();
        group.setDueDate(ZonedDateTime.now().plusDays(2));
        if (!operation.equals("push"))
            when(programmingRepository.findByIdElseThrow(current.getId())).thenReturn(current);
        if (operation.equals("timeline")) {
            group.addExercise(current);
        }
        else if (!operation.equals("push")) {
            when(exerciseRepository.save(current)).thenReturn(current);
        }
        if (operation.equals("timeline") || operation.equals("assignment")) {
            when(programmingUpdateService.updateTimeline(any(ProgrammingExerciseTimelineUpdateDTO.class), any(), any())).thenReturn(current);
        }
        switch (operation) {
            case "timeline" -> guardedGroups.saveWithTimelineAppliedToMembers(group);
            case "assignment" -> guardedGroups.assignToGroup(current, group);
            case "unassignment" -> guardedGroups.assignToGroup(current, null);
            case "push" -> {
                var previousRequest = RequestContextHolder.getRequestAttributes();
                var request = new MockHttpServletRequest();
                request.addHeader(ExerciseEditorSyncService.CLIENT_SESSION_HEADER, "push-session");
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                try (var lease = mutationGuard.claimExternalMutation(1L)) {
                    if (coordinated)
                        realVersions.createExerciseVersionSynchronously(current, author, RepositoryType.TESTS, null, "before-release");
                    else
                        realVersions.createExerciseVersion(current, author, RepositoryType.TESTS, null, "before-release");
                }
                finally {
                    RequestContextHolder.setRequestAttributes(previousRequest);
                }
            }
            default -> throw new IllegalArgumentException(operation);
        }
        assertThat(released).isTrue();
        if (coordinated) {
            assertThat(versionTasks).isEmpty();
            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().problemStatement()).isEqualTo("Before release");
            assertThat(captured.get().programmingData().testsCommitId()).isEqualTo("before-release");
        }
        else {
            assertThat(captured.get()).isNull();
            assertThat(versionTasks).hasSize(1);
            versionTasks.removeFirst().run();
            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().problemStatement()).isEqualTo("After release");
            assertThat(captured.get().programmingData().testsCommitId()).isEqualTo("after-release");
        }
        if (operation.equals("push")) {
            verify(editorSync).broadcastNewCommitAlert(1L, ExerciseEditorSyncTarget.TESTS_REPOSITORY, null, coordinated ? "push-session" : null);
        }
    }

    private static ProgrammingExercise programmingExercise(long id) {
        var exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }
}
