package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

@ExtendWith(MockitoExtension.class)
class StudentVersionRepairSchedulerTest {

    @Mock
    private SlideTestRepository slideRepository;

    @Mock
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Mock
    private AttachmentService attachmentService;

    private StudentVersionRepairScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StudentVersionRepairScheduler(slideRepository, attachmentVideoUnitRepository, attachmentService);
    }

    @Test
    void regeneratesAMissingStudentVersionWithoutAnotherEdit() {
        Attachment attachment = attachmentOfUnit(1L);
        when(slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion()).thenReturn(List.of(1L));
        doAnswer(invocation -> {
            invocation.<Attachment>getArgument(0).setStudentVersion("student.pdf");
            return null;
        }).when(attachmentService).generateMissingStudentVersion(attachment);

        scheduler.repairMissingStudentVersions();

        verify(attachmentService).generateMissingStudentVersion(attachment);
    }

    @Test
    void stopsRetryingAfterTheAttemptLimitAndOneFailureDoesNotStopTheOthers() {
        Attachment failing = attachmentOfUnit(1L);
        Attachment working = attachmentOfUnit(2L);
        when(slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion()).thenReturn(List.of(1L, 2L));
        doThrow(new InternalServerErrorException("write failed")).when(attachmentService).generateMissingStudentVersion(failing);

        for (int run = 0; run <= StudentVersionRepairScheduler.MAX_ATTEMPTS; run++) {
            scheduler.repairMissingStudentVersions();
        }

        verify(attachmentService, times(StudentVersionRepairScheduler.MAX_ATTEMPTS)).generateMissingStudentVersion(failing);
        // Runs that succeed do not count against the limit.
        verify(attachmentService, times(StudentVersionRepairScheduler.MAX_ATTEMPTS + 1)).generateMissingStudentVersion(working);
    }

    @Test
    void anAttachmentThatNoLongerNeedsAStudentVersionStartsOverLater() {
        Attachment attachment = attachmentOfUnit(1L);
        when(slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion()).thenReturn(List.of(1L), List.of(1L), List.of(1L), List.of(), List.of(1L));
        doThrow(new InternalServerErrorException("write failed")).when(attachmentService).generateMissingStudentVersion(attachment);

        for (int run = 0; run < 5; run++) {
            scheduler.repairMissingStudentVersions();
        }

        verify(attachmentService, times(StudentVersionRepairScheduler.MAX_ATTEMPTS + 1)).generateMissingStudentVersion(attachment);
    }

    @Test
    void aSuccessfulRunResetsTheFailureCount() {
        Attachment attachment = attachmentOfUnit(1L);
        when(slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion()).thenReturn(List.of(1L));
        // Fails twice, succeeds once, then fails again three times: the success starts the count over, so all six runs try it.
        doThrow(new InternalServerErrorException("write failed")).doThrow(new InternalServerErrorException("write failed")).doNothing()
                .doThrow(new InternalServerErrorException("write failed")).when(attachmentService).generateMissingStudentVersion(attachment);

        for (int run = 0; run < 7; run++) {
            scheduler.repairMissingStudentVersions();
        }

        verify(attachmentService, times(6)).generateMissingStudentVersion(attachment);
    }

    @Test
    void aDeletedUnitIsSkipped() {
        when(slideRepository.findAttachmentVideoUnitIdsWithHiddenSlidesAndWithoutStudentVersion()).thenReturn(List.of(1L));
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(1L)).thenReturn(Optional.empty());

        scheduler.repairMissingStudentVersions();

        verify(attachmentService, never()).generateMissingStudentVersion(any());
    }

    private Attachment attachmentOfUnit(long attachmentVideoUnitId) {
        var unit = new AttachmentVideoUnit();
        unit.setId(attachmentVideoUnitId);
        var attachment = new Attachment();
        attachment.setId(attachmentVideoUnitId * 10);
        attachment.setAttachmentVideoUnit(unit);
        unit.setAttachment(attachment);
        when(attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(attachmentVideoUnitId)).thenReturn(Optional.of(unit));
        return attachment;
    }
}
