package de.tum.cit.aet.artemis.iris;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.iris.service.IrisLectureUnitAutoIngestionService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class IrisLectureUnitAutoIngestionServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "irislectureunitautoingestion";

    @Mock
    private IrisLectureApi irisLectureApi;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private LectureUnitRepositoryApi lectureUnitRepositoryApi;

    @InjectMocks
    private IrisLectureUnitAutoIngestionService irisLectureUnitAutoIngestionService;

    @Mock
    private ScheduledFuture<?> scheduledFutureMock;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    private AttachmentVideoUnit attachmentVideoUnit;

    private Long lectureUnitId;

    @BeforeEach
    void setUp() {
        lectureUnitId = 1L;
        attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setId(lectureUnitId);
        attachmentVideoUnit.setName("Test Video Unit");
    }

    @Test
    void scheduleLectureUnitAutoIngestion_shouldScheduleTask_forAttachmentVideoUnit() {
        when(lectureUnitRepositoryApi.findByIdElseThrow(lectureUnitId)).thenReturn(attachmentVideoUnit);
        doReturn(scheduledFutureMock).when(taskScheduler).schedule(runnableCaptor.capture(), instantCaptor.capture());

        irisLectureUnitAutoIngestionService.scheduleLectureUnitAutoIngestion(lectureUnitId);

        verify(lectureUnitRepositoryApi).findByIdElseThrow(lectureUnitId);
        verify(taskScheduler).schedule(runnableCaptor.getValue(), instantCaptor.getValue());

        Runnable scheduledRunnable = runnableCaptor.getValue();
        scheduledRunnable.run();
        verify(irisLectureApi).addLectureUnitToPyrisDB(attachmentVideoUnit);
    }

    @Test
    void scheduleLectureUnitAutoIngestion_shouldCancelExistingTask_beforeSchedulingNewOne() {
        when(lectureUnitRepositoryApi.findByIdElseThrow(lectureUnitId)).thenReturn(attachmentVideoUnit);

        ScheduledFuture<?> firstScheduledFuture = mock(ScheduledFuture.class, "firstFuture");
        ScheduledFuture<?> secondScheduledFuture = mock(ScheduledFuture.class, "secondFuture");

        doReturn(firstScheduledFuture).doReturn(secondScheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        irisLectureUnitAutoIngestionService.scheduleLectureUnitAutoIngestion(lectureUnitId);
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        irisLectureUnitAutoIngestionService.scheduleLectureUnitAutoIngestion(lectureUnitId);

        verify(firstScheduledFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
        verify(secondScheduledFuture, never()).cancel(anyBoolean());
    }

    @Test
    void cancelLectureUnitAutoIngestion_shouldCancelScheduledTask() {
        when(lectureUnitRepositoryApi.findByIdElseThrow(lectureUnitId)).thenReturn(attachmentVideoUnit);
        doReturn(scheduledFutureMock).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        irisLectureUnitAutoIngestionService.scheduleLectureUnitAutoIngestion(lectureUnitId);
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        irisLectureUnitAutoIngestionService.cancelLectureUnitAutoIngestion(lectureUnitId);

        verify(scheduledFutureMock).cancel(false);

        irisLectureUnitAutoIngestionService.cancelLectureUnitAutoIngestion(lectureUnitId);
        verify(scheduledFutureMock, times(1)).cancel(false);
    }

    @Test
    void cancelLectureUnitAutoIngestion_shouldDoNothing_ifNoTaskScheduledForId() {
        Long nonExistentLectureUnitId = 99L;
        irisLectureUnitAutoIngestionService.cancelLectureUnitAutoIngestion(nonExistentLectureUnitId);

        verify(scheduledFutureMock, never()).cancel(anyBoolean());
        verifyNoInteractions(irisLectureApi);
    }

    @Test
    void scheduleLectureUnitAutoIngestion_shouldNotScheduleTask_forNonAttachmentVideoUnit() {
        LectureUnit nonVideoUnit = mock(LectureUnit.class);
        when(lectureUnitRepositoryApi.findByIdElseThrow(lectureUnitId)).thenReturn(nonVideoUnit);

        irisLectureUnitAutoIngestionService.scheduleLectureUnitAutoIngestion(lectureUnitId);

        verify(lectureUnitRepositoryApi).findByIdElseThrow(lectureUnitId);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
