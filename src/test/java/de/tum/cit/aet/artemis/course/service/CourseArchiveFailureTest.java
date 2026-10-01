package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.COURSE_OPERATION_PROGRESS_STATUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.service.export.CourseExamExportService;
import de.tum.cit.aet.artemis.admin.service.export.CourseStudentDataExportService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseOperationStatus;
import de.tum.cit.aet.artemis.course.domain.CourseOperationType;
import de.tum.cit.aet.artemis.course.dto.CourseOperationProgressDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseWithSubmissionsExportService;

class CourseArchiveFailureTest {

    @TempDir
    private Path directory;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void zipFailureReportsFailureAndAllowsRetry(boolean throughArchiveService) throws IOException {
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class))).thenAnswer(_ -> mock(ScheduledFuture.class));
        var messaging = mock(WebsocketMessagingService.class);
        var progress = new CourseOperationProgressService(new ConcurrentMapCacheManager(COURSE_OPERATION_PROGRESS_STATUS), messaging, new LocalDataProviderService(), scheduler);
        var zipService = mock(ZipFileService.class);
        doThrow(new IOException("Disk full")).when(zipService).createZipFile(any(Path.class), any());
        var quizExport = mock(QuizExerciseWithSubmissionsExportService.class);
        var quiz = new QuizExercise();
        quiz.setId(1L);
        quiz.setTitle("Quiz");
        when(quizExport.exportExerciseWithSubmissions(eq(quiz), any(), any(), any())).thenReturn(directory.resolve("quiz.zip"));
        var exporter = new CourseExamExportService(null, zipService, mock(FileService.class), Optional.empty(), Optional.empty(), Optional.empty(), quizExport, messaging,
                Optional.of(mock(ExamRepositoryApi.class)), mock(CourseStudentDataExportService.class), progress, JsonObjectMapper.get());
        ReflectionTestUtils.setField(exporter, "courseArchivesDirPath", directory.toString());
        var course = new Course();
        course.setId(42L);
        course.setTitle("Course");
        course.setShortName("course");
        course.setEndDate(ZonedDateTime.parse("2000-01-01T00:00:00Z"));
        course.setExercises(Set.of(quiz));
        var repository = mock(CourseRepository.class);

        if (throughArchiveService) {
            var archive = new CourseArchiveService(repository, exporter, null, null, null, Optional.empty(), Optional.empty(), null, progress);
            ReflectionTestUtils.setField(archive, "courseArchivesDirPath", directory);
            assertThat(archive.archiveCourseSynchronously(course)).isFalse();
        }
        else {
            assertThat(exporter.exportCourseForArchive(course, directory, new ArrayList<>(), Map.of())).isEmpty();
        }

        verify(messaging).sendMessage(any(),
                argThat((Object payload) -> payload instanceof String message && message.contains("COMPLETED_WITH_ERRORS") && message.contains("ZIP_NOT_CREATED")));
        verify(zipService).createZipFile(any(Path.class), any());
        verify(repository, never()).saveAndFlush(any());
        assertThat(progress.getOperationProgress(course.getId())).get().extracting(CourseOperationProgressDTO::status).isEqualTo(CourseOperationStatus.FAILED);
        var retry = progress.startOperation(course.getId(), CourseOperationType.ARCHIVE, "Retry", 4, ZonedDateTime.now());
        progress.releaseOperationClaim(retry);
    }
}
