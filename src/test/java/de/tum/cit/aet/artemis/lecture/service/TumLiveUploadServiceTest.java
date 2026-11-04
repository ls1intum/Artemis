package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TumLiveRecordingCleanup;
import de.tum.cit.aet.artemis.lecture.domain.TumLiveUnitUpload;
import de.tum.cit.aet.artemis.lecture.repository.TumLiveRecordingCleanupRepository;
import de.tum.cit.aet.artemis.lecture.repository.TumLiveUnitUploadRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.videosource.api.GocastBindingApi;
import de.tum.cit.aet.artemis.videosource.domain.event.GocastGrantRevokingEvent;
import de.tum.cit.aet.artemis.videosource.dto.GocastRecordingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadConnectionDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastUploadTargetDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;

class TumLiveUploadServiceTest {

    private static final long UNIT_ID = 42L;

    private static final String REF = "reference-a-0000";

    private final TumLiveUnitUploadRepository uploadRepository = mock(TumLiveUnitUploadRepository.class);

    private final TumLiveRecordingCleanupRepository cleanupRepository = mock(TumLiveRecordingCleanupRepository.class);

    private final AttachmentVideoUnitTestRepository unitRepository = mock(AttachmentVideoUnitTestRepository.class);

    private final GocastBindingApi api = mock(GocastBindingApi.class);

    private final LectureContentProcessingService contentProcessingService = mock(LectureContentProcessingService.class);

    private final TumLiveUploadService service = new TumLiveUploadService(uploadRepository, cleanupRepository, unitRepository, Optional.of(api),
            Optional.of(contentProcessingService), Duration.ofHours(6));

    private static AttachmentVideoUnit unit() {
        var course = new Course();
        course.setId(7L);
        var lecture = new Lecture();
        lecture.setTitle("Algorithms");
        lecture.setCourse(course);
        var unit = new AttachmentVideoUnit();
        unit.setId(UNIT_ID);
        unit.setName("Lecture 3");
        unit.setLecture(lecture);
        return unit;
    }

    private static TumLiveUnitUpload pending(ZonedDateTime since, Long streamId) {
        var upload = new TumLiveUnitUpload();
        upload.setId(9L);
        upload.setLectureUnitId(UNIT_ID);
        upload.setIntegrationId(17);
        upload.startPending(REF, 23, "algo", null, since);
        upload.setPendingStreamId(streamId);
        return upload;
    }

    @Test
    void startCreatesTheLectureThroughTheCoursesGrantAndRecordsIt() {
        when(api.findUploadConnection(7L)).thenReturn(Optional.of(new GocastUploadConnectionDTO(17, 23, "algo")));
        when(uploadRepository.startPending(eq(UNIT_ID), eq(17L), eq(23L), eq("algo"), anyString(), any())).thenReturn(pending(ZonedDateTime.now(), null));
        when(api.createUpload(23, REF, "Algorithms: Lecture 3")).thenReturn(new GocastUploadTargetDTO(55, "https://tum.live/api/v2/integration/uploads/ticket"));

        var started = service.start(unit());

        assertThat(started.ref()).isEqualTo(REF);
        assertThat(started.uploadUrl()).isEqualTo("https://tum.live/api/v2/integration/uploads/ticket");
        verify(uploadRepository).recordPendingStream(UNIT_ID, REF, 55);
    }

    @Test
    void startNeedsAConnectedCourse() {
        when(api.findUploadConnection(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(unit())).isInstanceOf(ConflictException.class);
        verify(uploadRepository, never()).startPending(anyLong(), anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void startReportsAnUploadThatIsAlreadyBeingTransferred() {
        when(api.findUploadConnection(7L)).thenReturn(Optional.of(new GocastUploadConnectionDTO(17, 23, "algo")));
        when(uploadRepository.startPending(eq(UNIT_ID), eq(17L), eq(23L), eq("algo"), anyString(), any())).thenReturn(pending(ZonedDateTime.now(), 55L));
        when(api.createUpload(eq(23L), eq(REF), anyString())).thenThrow(new GocastIntegrationException("refused", HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.start(unit())).isInstanceOf(ConflictException.class).hasMessageContaining("already being transferred");
        verify(uploadRepository, never()).recordPendingStream(anyLong(), anyString(), anyLong());
    }

    @Test
    void readyUploadIsAttachedWithTheSlugOfItsOwnConnectionAndProcessedWithTheFreshUnit() {
        var fresh = unit();
        var upload = pending(ZonedDateTime.now(), 55L);
        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(upload));
        when(api.getRecording(23, 55)).thenReturn(new GocastRecordingDTO("https://edge/p.m3u8?jwt=x", false, 0, false, null));
        when(unitRepository.findWithLectureAndCourseAndAttachmentById(UNIT_ID)).thenReturn(Optional.of(fresh));
        when(api.watchUrl("algo", 55)).thenReturn("https://tum.live/w/algo/55");
        when(uploadRepository.attachPending(eq(UNIT_ID), eq(REF), eq("https://tum.live/w/algo/55"), any())).thenReturn(true);

        service.processPendingUploads();

        verify(contentProcessingService).triggerProcessing(fresh);
        verify(api, never()).findUploadConnection(anyLong());
        verify(uploadRepository).markPendingChecked(eq(9L), eq(REF), any());
    }

    @Test
    void convertingUploadWaits() {
        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(pending(ZonedDateTime.now(), 55L)));
        when(api.getRecording(23, 55)).thenReturn(new GocastRecordingDTO(null, true, 40, false, null));

        service.processPendingUploads();

        verify(uploadRepository, never()).attachPending(anyLong(), anyString(), anyString(), any());
        verify(uploadRepository, never()).discardPending(anyLong(), anyString(), any(), any());
    }

    @Test
    void failedOverdueOrDeletedUploadsAreGivenUp() {
        var failed = pending(ZonedDateTime.now(), 55L);
        var overdue = pending(ZonedDateTime.now().minusHours(7), null);
        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(failed, overdue));
        when(api.getRecording(23, 55)).thenReturn(new GocastRecordingDTO(null, false, 0, true, "The file is not a video."));

        service.processPendingUploads();

        verify(uploadRepository).discardPending(eq(UNIT_ID), eq(REF), eq("TUM.Live could not convert the video: The file is not a video."), any());
        verify(uploadRepository).discardPending(eq(UNIT_ID), eq(REF), eq("The upload did not finish in time."), any());

        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(pending(ZonedDateTime.now(), 56L)));
        when(api.getRecording(23, 56)).thenThrow(new GocastIntegrationException("gone", HttpStatus.NOT_FOUND));
        service.processPendingUploads();
        verify(uploadRepository).discardPending(eq(UNIT_ID), eq(REF), eq("The lecture in TUM.Live no longer exists."), any());
    }

    @Test
    void anOverdueUploadThatIsReadyIsStillAttached() {
        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(pending(ZonedDateTime.now().minusHours(7), 55L)));
        when(api.getRecording(23, 55)).thenReturn(new GocastRecordingDTO("https://edge/p.m3u8?jwt=x", false, 0, false, null));
        when(api.watchUrl("algo", 55)).thenReturn("https://tum.live/w/algo/55?video_only=1");

        service.processPendingUploads();

        verify(uploadRepository).attachPending(eq(UNIT_ID), eq(REF), eq("https://tum.live/w/algo/55?video_only=1"), any());
        verify(uploadRepository, never()).discardPending(anyLong(), anyString(), any(), any());
    }

    @Test
    void anOverdueUploadThatIsStillConvertingIsGivenUp() {
        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(pending(ZonedDateTime.now().minusHours(7), 55L)));
        when(api.getRecording(23, 55)).thenReturn(new GocastRecordingDTO(null, true, 40, false, null));

        service.processPendingUploads();

        verify(uploadRepository).discardPending(eq(UNIT_ID), eq(REF), eq("The upload did not finish in time."), any());
        verify(uploadRepository, never()).attachPending(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void anOverdueUploadIsKeptWhileTumLiveCannotBeAsked() {
        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(pending(ZonedDateTime.now().minusHours(7), 55L)));
        when(api.getRecording(23, 55)).thenThrow(new GocastIntegrationException("down", HttpStatus.SERVICE_UNAVAILABLE));

        service.processPendingUploads();

        verify(uploadRepository, never()).discardPending(anyLong(), anyString(), any(), any());
    }

    @Test
    void unreachableTumLiveLeavesPendingUploadsAlone() {
        when(uploadRepository.findTop50ByPendingRefIsNotNullOrderByPendingCheckedAtAsc()).thenReturn(List.of(pending(ZonedDateTime.now(), 55L)));
        when(api.getRecording(23, 55)).thenThrow(new GocastIntegrationException("down", HttpStatus.SERVICE_UNAVAILABLE));

        service.processPendingUploads();

        verify(uploadRepository, never()).discardPending(anyLong(), anyString(), any(), any());
    }

    private static TumLiveRecordingCleanup cleanup() {
        var cleanup = new TumLiveRecordingCleanup();
        cleanup.setId(5L);
        ReflectionTestUtils.setField(cleanup, "courseId", 7L);
        ReflectionTestUtils.setField(cleanup, "integrationId", 17L);
        ReflectionTestUtils.setField(cleanup, "grantId", 23L);
        ReflectionTestUtils.setField(cleanup, "externalRef", REF);
        return cleanup;
    }

    @Test
    void cleanupIsRemovedOnlyAfterTumLiveConfirms() {
        when(cleanupRepository.findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any())).thenReturn(List.of(cleanup()));

        service.processCleanups();

        verify(api).deleteUpload(23, REF);
        verify(cleanupRepository).deleteConfirmed(5L);
    }

    @Test
    void rowsOfDeletedUnitsAreRemovedOnlyWhenTheRunBeforeFoundThemToo() {
        // Row 1 is released by its unit's deletion in between; rows 2 and 3 stay.
        when(uploadRepository.findIdsWithoutUnitAndPendingUpload()).thenReturn(Set.of(1L, 2L)).thenReturn(Set.of(2L, 3L)).thenReturn(Set.of(3L));

        service.processCleanups();
        verify(uploadRepository, never()).deleteWithoutUnitAndPendingUploadByIdIn(any());

        service.processCleanups();
        verify(uploadRepository).deleteWithoutUnitAndPendingUploadByIdIn(Set.of(2L));

        service.processCleanups();
        verify(uploadRepository).deleteWithoutUnitAndPendingUploadByIdIn(Set.of(3L));
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = { "NOT_FOUND", "CONFLICT" })
    void refusedCleanupIsKeptWithTheReasonAndRetriedDaily(HttpStatus status) {
        when(cleanupRepository.findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any())).thenReturn(List.of(cleanup()));
        doThrow(new GocastIntegrationException("refused", status)).when(api).deleteUpload(23, REF);
        var before = ZonedDateTime.now();

        service.processCleanups();

        verify(cleanupRepository, never()).deleteConfirmed(anyLong());
        verify(cleanupRepository).postpone(eq(5L), argThat(next -> next.isAfter(before.plusHours(23))), eq("TUM.Live answered " + status));
    }

    @Test
    void unavailableTumLiveRetriesCleanupSoonAfterTheNextEntries() {
        var blockedBefore = cleanup();
        ReflectionTestUtils.setField(blockedBefore, "blockedReason", "TUM.Live answered 409 CONFLICT");
        when(cleanupRepository.findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any())).thenReturn(List.of(blockedBefore));
        doThrow(new GocastIntegrationException("down", HttpStatus.SERVICE_UNAVAILABLE)).when(api).deleteUpload(23, REF);
        var before = ZonedDateTime.now();

        service.processCleanups();

        verify(cleanupRepository, never()).deleteConfirmed(anyLong());
        // Moved behind the entries that are due now, keeping the reason of its earlier refusal.
        verify(cleanupRepository).postpone(eq(5L), argThat(next -> next.isAfter(before.plusMinutes(4)) && next.isBefore(before.plusHours(1))),
                eq("TUM.Live answered 409 CONFLICT"));
    }

    @Test
    void anUploadIsForgottenWhenTheUnitsLinkShowsAnotherVideo() {
        when(api.findStreamId("https://www.youtube.com/watch?v=x")).thenReturn(Optional.empty());
        when(api.findStreamId("https://tum.live/w/algo/55")).thenReturn(Optional.of(55L));

        service.forgetUploadUnlessShown(UNIT_ID, "https://www.youtube.com/watch?v=x");
        service.forgetUploadUnlessShown(UNIT_ID, "https://tum.live/w/algo/55");

        verify(uploadRepository).forgetCurrentUnlessShown(UNIT_ID, "https://www.youtube.com/watch?v=x", null);
        verify(uploadRepository).forgetCurrentUnlessShown(UNIT_ID, "https://tum.live/w/algo/55", 55L);
    }

    @Test
    void withoutTheTumLiveConnectionAnUploadIsForgottenWhenTheLinkChanges() {
        var withoutConnection = new TumLiveUploadService(uploadRepository, cleanupRepository, unitRepository, Optional.empty(), Optional.of(contentProcessingService),
                Duration.ofHours(6));

        withoutConnection.forgetUploadUnlessShown(UNIT_ID, "https://tum.live/w/algo/56");

        verify(uploadRepository).forgetCurrentUnlessShown(UNIT_ID, "https://tum.live/w/algo/56", null);
    }

    @Test
    void cleanupOfARevokedGrantGoesThroughTheCoursesNewGrant() {
        when(cleanupRepository.findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any())).thenReturn(List.of(cleanup()));
        doThrow(new GocastIntegrationException("revoked", HttpStatus.NOT_FOUND)).when(api).deleteUpload(23, REF);
        when(api.findUploadConnection(7L)).thenReturn(Optional.of(new GocastUploadConnectionDTO(17, 24, "algo")));

        service.processCleanups();

        verify(api).deleteUpload(24, REF);
        verify(cleanupRepository).deleteConfirmed(5L);
    }

    @Test
    void cleanupDoesNotUseTheGrantOfAnotherIntegration() {
        when(cleanupRepository.findTop50ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any())).thenReturn(List.of(cleanup()));
        doThrow(new GocastIntegrationException("revoked", HttpStatus.NOT_FOUND)).when(api).deleteUpload(23, REF);
        when(api.findUploadConnection(7L)).thenReturn(Optional.of(new GocastUploadConnectionDTO(99, 24, "algo")));

        service.processCleanups();

        verify(api, never()).deleteUpload(24, REF);
        verify(cleanupRepository).postpone(eq(5L), any(), eq("TUM.Live answered " + HttpStatus.NOT_FOUND));
    }

    @Test
    void beforeTheGrantIsRevokedItsUnattachedUploadsAreDeleted() {
        when(uploadRepository.findByCourseIdAndPendingGrantId(7L, 23L)).thenReturn(List.of(pending(ZonedDateTime.now(), 55L)));
        when(cleanupRepository.findTop50ByGrantIdAndIdGreaterThanOrderByIdAsc(23L, 0L)).thenReturn(List.of(cleanup()));

        service.deleteUnattachedUploadsBeforeRevocation(new GocastGrantRevokingEvent(7, 17, 23));

        verify(uploadRepository).discardPending(eq(UNIT_ID), eq(REF), eq("The course was disconnected from TUM.Live."), any());
        verify(api).deleteUpload(23, REF);
        verify(cleanupRepository).deleteConfirmed(5L);
    }

    @Test
    void allQueuedDeletionsOfTheGrantAreTriedBeforeItIsRevoked() {
        var second = cleanup();
        second.setId(6L);
        ReflectionTestUtils.setField(second, "externalRef", "reference-b-0000");
        var third = cleanup();
        third.setId(7L);
        ReflectionTestUtils.setField(third, "externalRef", "reference-c-0000");
        when(uploadRepository.findByCourseIdAndPendingGrantId(7L, 23L)).thenReturn(List.of());
        when(cleanupRepository.findTop50ByGrantIdAndIdGreaterThanOrderByIdAsc(23L, 0L)).thenReturn(List.of(cleanup(), second));
        when(cleanupRepository.findTop50ByGrantIdAndIdGreaterThanOrderByIdAsc(23L, 6L)).thenReturn(List.of(third));
        // TUM.Live refuses the first one; the others are tried all the same.
        doThrow(new GocastIntegrationException("refused", HttpStatus.CONFLICT)).when(api).deleteUpload(23, REF);

        service.deleteUnattachedUploadsBeforeRevocation(new GocastGrantRevokingEvent(7, 17, 23));

        verify(cleanupRepository).deleteConfirmed(6L);
        verify(cleanupRepository).deleteConfirmed(7L);
        verify(cleanupRepository, never()).deleteConfirmed(5L);
        verify(cleanupRepository).findTop50ByGrantIdAndIdGreaterThanOrderByIdAsc(23L, 7L);
    }

    @Test
    void aFailureBeforeTheRevocationIsOnlyLogged() {
        when(uploadRepository.findByCourseIdAndPendingGrantId(7L, 23L)).thenReturn(List.of());
        when(cleanupRepository.findTop50ByGrantIdAndIdGreaterThanOrderByIdAsc(23L, 0L)).thenReturn(List.of(cleanup()));
        doThrow(new GocastIntegrationException("down", HttpStatus.SERVICE_UNAVAILABLE)).when(api).deleteUpload(23, REF);

        assertThatCode(() -> service.deleteUnattachedUploadsBeforeRevocation(new GocastGrantRevokingEvent(7, 17, 23))).doesNotThrowAnyException();

        verify(cleanupRepository, never()).deleteConfirmed(anyLong());
    }

    @Test
    void anUnavailableTumLiveEndsTheCleanupBeforeTheRevocation() {
        var second = cleanup();
        second.setId(6L);
        ReflectionTestUtils.setField(second, "externalRef", "reference-b-0000");
        when(uploadRepository.findByCourseIdAndPendingGrantId(7L, 23L)).thenReturn(List.of());
        when(cleanupRepository.findTop50ByGrantIdAndIdGreaterThanOrderByIdAsc(23L, 0L)).thenReturn(List.of(cleanup(), second));
        doThrow(new GocastIntegrationException("down", HttpStatus.SERVICE_UNAVAILABLE)).when(api).deleteUpload(23, REF);

        service.deleteUnattachedUploadsBeforeRevocation(new GocastGrantRevokingEvent(7, 17, 23));

        verify(api, never()).deleteUpload(23, "reference-b-0000");
    }
}
