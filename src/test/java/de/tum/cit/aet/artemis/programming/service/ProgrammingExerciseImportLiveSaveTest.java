package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class ProgrammingExerciseImportLiveSaveTest {

    private final GenerationLiveSaveService live = new GenerationLiveSaveService(new LocalDataProviderService(), true);

    private final ProgrammingExerciseTestRepository exercises = mock(ProgrammingExerciseTestRepository.class);

    private final ProgrammingExerciseBuildConfigRepository configs = mock(ProgrammingExerciseBuildConfigRepository.class);

    private final ProgrammingExerciseTaskTestRepository tasks = mock(ProgrammingExerciseTaskTestRepository.class);

    private final ProgrammingExerciseImportBasicService basic = mock(ProgrammingExerciseImportBasicService.class);

    private final VersionControlService vcs = mock(VersionControlService.class);

    private final ProgrammingExercise source = mock(ProgrammingExercise.class);

    private final ProgrammingExercise fresh = mock(ProgrammingExercise.class);

    private final ProgrammingExerciseBuildConfig oldConfig = new ProgrammingExerciseBuildConfig();

    private final ProgrammingExerciseBuildConfig freshConfig = new ProgrammingExerciseBuildConfig();

    private final ProgrammingExercise destination = new ProgrammingExercise();

    private ProgrammingExerciseImportService service;

    @BeforeEach
    void setup() {
        Course course = new Course();
        course.setId(1L);
        course.setShortName("test");
        destination.setId(80L);
        destination.setCourse(course);
        destination.setTitle("Destination");
        destination.setShortName("dest");
        when(source.getId()).thenReturn(7L);
        when(fresh.getId()).thenReturn(7L);
        when(source.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(fresh.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(fresh.getProjectKey()).thenReturn("SRC");
        when(fresh.getTemplateRepositoryUri()).thenReturn("https://localhost/src-template.git");
        when(fresh.getSolutionRepositoryUri()).thenReturn("https://localhost/src-solution.git");
        when(fresh.getTestRepositoryUri()).thenReturn("https://localhost/src-tests.git");
        when(exercises.findByIdForImportElseThrow(7L)).thenReturn(fresh);
        when(exercises.findBranchByExerciseId(7L)).thenReturn("main");
        when(configs.getProgrammingExerciseBuildConfigElseThrow(7L)).thenReturn(freshConfig);
        when(tasks.findByExerciseIdWithTestCases(7L)).thenReturn(Set.of());
        when(basic.importProgrammingExerciseBasis(eq(fresh), eq(freshConfig), eq(destination), isNull())).thenReturn(destination);
        var uris = mock(UriService.class);
        when(uris.getRepositorySlugFromRepositoryUriString(anyString())).thenAnswer(invocation -> {
            String uri = invocation.getArgument(0);
            return uri.substring(uri.lastIndexOf('/') + 1).replace(".git", "");
        });
        ReflectionTestUtils.setField(basic, "versionControlService", Optional.of(vcs));
        ReflectionTestUtils.setField(basic, "uriService", uris);
        ReflectionTestUtils.setField(basic, "programmingExerciseRepository", exercises);
        ReflectionTestUtils.setField(basic, "programmingExerciseProjectNameService", mock(ProgrammingExerciseProjectNameService.class));
        doCallRealMethod().when(basic).importRepositories(any(), any());
        service = new ProgrammingExerciseImportService(Optional.empty(), Optional.empty(), mock(ProgrammingExerciseValidationService.class),
                mock(ProgrammingExerciseBuildPlanService.class), mock(ProgrammingExerciseCreationScheduleService.class), mock(ProgrammingExerciseTaskService.class), basic,
                mock(ProgrammingExerciseTestCaseTestRepository.class), exercises, Optional.empty(), configs, Optional.of(new HyperionExerciseMutationApi(live)), tasks);
    }

    @Test
    void sourceReaderCoversFreshMetadataAndEveryActualRepositoryCopy() throws Exception {
        var denied = new AtomicInteger();
        try (var writer = Executors.newSingleThreadExecutor()) {
            Runnable assertExcluded = () -> {
                try {
                    writer.submit(() -> assertThatThrownBy(() -> live.claim(7L)).isInstanceOf(ConflictException.class)).get(10, TimeUnit.SECONDS);
                    denied.incrementAndGet();
                }
                catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            };
            when(basic.importProgrammingExerciseBasis(eq(fresh), eq(freshConfig), eq(destination), isNull())).thenAnswer(invocation -> {
                assertExcluded.run();
                return destination;
            });
            doAnswer(invocation -> {
                assertExcluded.run();
                return null;
            }).when(vcs).copyRepositoryWithHistory(eq("SRC"), anyString(), eq("main"), anyString(), anyString(), isNull());

            assertThat(service.importProgrammingExercise(source, oldConfig, destination, null, true, false)).isSameAs(destination);

            assertThat(denied).hasValue(4);
            verify(vcs).copyRepositoryWithHistory(eq("SRC"), eq("src-template"), eq("main"), anyString(), eq("exercise"), isNull());
            verify(vcs).copyRepositoryWithHistory(eq("SRC"), eq("src-solution"), eq("main"), anyString(), eq("solution"), isNull());
            verify(vcs).copyRepositoryWithHistory(eq("SRC"), eq("src-tests"), eq("main"), anyString(), eq("tests"), isNull());
        }
        assertThat(live.getSlotInfo(7L)).isEmpty();
    }

    @Test
    void partialRepositoryCopyFailureReleasesSourceReader() {
        doAnswer(invocation -> {
            throw new IllegalStateException("second copy failed");
        }).when(vcs).copyRepositoryWithHistory(eq("SRC"), eq("src-solution"), eq("main"), anyString(), anyString(), isNull());

        assertThatThrownBy(() -> service.importProgrammingExercise(source, oldConfig, destination, null, true, false)).hasMessage("second copy failed");

        verify(vcs).copyRepositoryWithHistory(eq("SRC"), eq("src-template"), eq("main"), anyString(), anyString(), isNull());
        assertThat(live.getSlotInfo(7L)).isEmpty();
        String token = live.claim(7L);
        live.release(7L, token);
    }

    @Test
    void activeSaveRejectsImportBeforeSourceReloadOrDestinationCreation() throws Exception {
        var held = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var writer = Executors.newSingleThreadExecutor()) {
            var future = writer.submit(() -> {
                String token = live.claim(7L);
                try {
                    held.countDown();
                    assertThat(finish.await(10, TimeUnit.SECONDS)).isTrue();
                }
                finally {
                    live.release(7L, token);
                }
                return null;
            });
            try {
                assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.importProgrammingExercise(source, oldConfig, destination, null, true, false)).isInstanceOf(ConflictException.class);
                verifyNoInteractions(exercises, configs, basic, vcs);
            }
            finally {
                finish.countDown();
            }
            future.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void changedSourcePlacementRequiresFreshAuthorizationBeforeAnyImport() {
        Course moved = new Course();
        moved.setId(2L);
        when(fresh.getCourseViaExerciseGroupOrCourseMember()).thenReturn(moved);

        assertThatThrownBy(() -> service.importProgrammingExercise(source, oldConfig, destination, null, true, false)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("authorize");

        verifyNoInteractions(basic, vcs, configs, tasks);
        assertThat(live.getSlotInfo(7L)).isEmpty();
    }
}
