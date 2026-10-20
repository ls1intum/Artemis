package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;

class ParticipationLiveSaveCoordinationTest {

    /** The template copy in these tests carries no online IDE configuration. */
    private static final Supplier<Map<String, byte[]>> NO_ADDITIONAL_FILES = Map::of;

    @Test
    void studentCannotCopyATemplateThatALaterSaveFailureCompensates() throws Exception {
        long exerciseId = 42L;
        var provider = mock(DistributedDataProvider.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(new LocalMap<>());
        var liveSave = new GenerationLiveSaveService(provider, true);
        var api = new HyperionExerciseMutationApi(liveSave);
        var vcs = mock(VersionControlService.class);
        var uriService = mock(UriService.class);
        var participations = mock(ProgrammingExerciseStudentParticipationTestRepository.class);
        var service = new ParticipationService(Optional.empty(), Optional.of(vcs), null, null, participations, null, null, null, uriService, null, null, null, null, null,
                Optional.of(api));
        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        var course = new Course();
        course.setShortName("te");
        exercise.setCourse(course);
        exercise.setShortName("st");
        exercise.generateAndSetProjectKey();
        var student = new User();
        student.setLogin("student");
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(5L);
        participation.setExercise(exercise);
        participation.setParticipant(student);
        participation.setInitializationState(InitializationState.UNINITIALIZED);
        var source = new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-template.git");
        var destination = new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-student.git");
        when(uriService.getRepositorySlugFromRepositoryUri(source)).thenReturn("test-template");
        when(participations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var templateHead = new AtomicReference<>("baseline");
        var copiedHead = new AtomicReference<String>();
        when(vcs.copyRepositoryWithoutHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyMap())).thenAnswer(invocation -> {
            copiedHead.set(templateHead.get());
            return destination;
        });
        var templatePushed = new CountDownLatch(1);
        var compensate = new CountDownLatch(1);
        Supplier<LocalVCRepositoryUri> sourceUri = () -> source;
        Supplier<String> branch = () -> "main";

        try (var executor = Executors.newSingleThreadExecutor()) {
            var save = executor.submit(() -> {
                String token = liveSave.claim(exerciseId);
                try {
                    templateHead.set("temporary-generated-template");
                    templatePushed.countDown();
                    assertThat(compensate.await(10, TimeUnit.SECONDS)).isTrue();
                    templateHead.set("baseline");
                }
                finally {
                    liveSave.release(exerciseId, token);
                }
                return null;
            });
            try {
                assertThat(templatePushed.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatExceptionOfType(ConflictException.class)
                        .isThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "copyRepository", exercise, sourceUri, branch, NO_ADDITIONAL_FILES, participation));
                verifyNoInteractions(vcs);
                assertThat(copiedHead.get()).isNull();
            }
            finally {
                compensate.countDown();
            }
            save.get(10, TimeUnit.SECONDS);
        }

        var result = ReflectionTestUtils.invokeMethod(service, "copyRepository", exercise, sourceUri, branch, NO_ADDITIONAL_FILES, participation);
        assertThat(result).isSameAs(participation);
        assertThat(copiedHead.get()).isEqualTo("baseline");
        assertThat(participation.getInitializationState()).isEqualTo(InitializationState.REPO_COPIED);
        assertThat(liveSave.getSlotInfo(exerciseId)).isEmpty();
    }

    @Test
    void studentCopiesCanOverlapButExcludeASaveUntilBothFinish() throws Exception {
        long exerciseId = 42L;
        var provider = mock(DistributedDataProvider.class);
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(new LocalMap<>());
        var liveSave = new GenerationLiveSaveService(provider, true);
        var vcs = mock(VersionControlService.class);
        var uriService = mock(UriService.class);
        var participations = mock(ProgrammingExerciseStudentParticipationTestRepository.class);
        var service = new ParticipationService(Optional.empty(), Optional.of(vcs), null, null, participations, null, null, null, uriService, null, null, null, null, null,
                Optional.of(new HyperionExerciseMutationApi(liveSave)));
        var exercise = new ProgrammingExercise();
        exercise.setId(exerciseId);
        var course = new Course();
        course.setShortName("te");
        exercise.setCourse(course);
        exercise.setShortName("st");
        exercise.generateAndSetProjectKey();
        var source = new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-template.git");
        var destination = new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-student.git");
        when(uriService.getRepositorySlugFromRepositoryUri(source)).thenReturn("test-template");
        when(participations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var bothCopying = new CountDownLatch(2);
        var finishCopies = new CountDownLatch(1);
        when(vcs.copyRepositoryWithoutHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyMap())).thenAnswer(invocation -> {
            bothCopying.countDown();
            assertThat(finishCopies.await(10, TimeUnit.SECONDS)).isTrue();
            return destination;
        });
        Supplier<LocalVCRepositoryUri> sourceUri = () -> source;
        Supplier<String> branch = () -> "main";
        var first = participation(exercise, "student1");
        var second = participation(exercise, "student2");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstCopy = executor.submit(() -> ReflectionTestUtils.invokeMethod(service, "copyRepository", exercise, sourceUri, branch, NO_ADDITIONAL_FILES, first));
            var secondCopy = executor.submit(() -> ReflectionTestUtils.invokeMethod(service, "copyRepository", exercise, sourceUri, branch, NO_ADDITIONAL_FILES, second));
            try {
                assertThat(bothCopying.await(10, TimeUnit.SECONDS)).as("both students must reach actual VCS copying before either finishes").isTrue();
                assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> liveSave.claim(exerciseId));
            }
            finally {
                finishCopies.countDown();
            }
            assertThat(firstCopy.get(10, TimeUnit.SECONDS)).isSameAs(first);
            assertThat(secondCopy.get(10, TimeUnit.SECONDS)).isSameAs(second);
        }
        String token = liveSave.claim(exerciseId);
        liveSave.release(exerciseId, token);
        assertThat(liveSave.getSlotInfo(exerciseId)).isEmpty();
    }

    @Test
    void examTemplateCopyDoesNotAccessTheGenerationCoordinationMap() {
        var provider = mock(DistributedDataProvider.class);
        var map = org.mockito.Mockito.<de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap<String, GenerationLiveSaveService.LiveSaveSlot>>mock();
        when(provider.<String, GenerationLiveSaveService.LiveSaveSlot>getMap(GenerationLiveSaveService.MAP_NAME)).thenReturn(map);
        when(map.get(anyString())).thenThrow(new IllegalStateException("generation coordination is unavailable"));
        when(map.putIfAbsent(anyString(), any())).thenThrow(new IllegalStateException("generation coordination is unavailable"));
        var liveSave = new GenerationLiveSaveService(provider, true);
        var vcs = mock(VersionControlService.class);
        var uriService = mock(UriService.class);
        var participations = mock(ProgrammingExerciseStudentParticipationTestRepository.class);
        var service = new ParticipationService(Optional.empty(), Optional.of(vcs), null, null, participations, null, null, null, uriService, null, null, null, null, null,
                Optional.of(new HyperionExerciseMutationApi(liveSave)));
        var course = new Course();
        course.setShortName("te");
        var exam = new de.tum.cit.aet.artemis.exam.domain.Exam();
        exam.setCourse(course);
        var group = new de.tum.cit.aet.artemis.exam.domain.ExerciseGroup();
        group.setExam(exam);
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        exercise.setExerciseGroup(group);
        exercise.setShortName("st");
        exercise.generateAndSetProjectKey();
        var participation = participation(exercise, "examstudent");
        var source = new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-template.git");
        var destination = new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-examstudent.git");
        when(uriService.getRepositorySlugFromRepositoryUri(source)).thenReturn("test-template");
        when(participations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vcs.copyRepositoryWithoutHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyMap())).thenReturn(destination);
        Supplier<LocalVCRepositoryUri> sourceUri = () -> source;
        Supplier<String> branch = () -> "main";

        Object copied = ReflectionTestUtils.invokeMethod(service, "copyRepository", exercise, sourceUri, branch, NO_ADDITIONAL_FILES, participation);
        assertThat(copied).isSameAs(participation);
        assertThat(participation.getInitializationState()).isEqualTo(InitializationState.REPO_COPIED);
        verifyNoInteractions(map);
    }

    private static ProgrammingExerciseStudentParticipation participation(ProgrammingExercise exercise, String login) {
        var student = new User();
        student.setLogin(login);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student);
        participation.setInitializationState(InitializationState.UNINITIALIZED);
        return participation;
    }
}
