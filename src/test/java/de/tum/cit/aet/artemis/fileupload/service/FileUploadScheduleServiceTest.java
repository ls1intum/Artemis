package de.tum.cit.aet.artemis.fileupload.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.fileupload.domain.FileUpload;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadRepository;

@ExtendWith(MockitoExtension.class)
class FileUploadScheduleServiceTest {

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private FileUploadService fileUploadService;

    @InjectMocks
    private FileUploadScheduleService fileUploadScheduleService;

    @Test
    void shouldDeleteOrphanedFileUploadsWhenCleanupMethodIsCalled() {
        List<FileUpload> orphanedConversationUploads = List.of(mock(FileUpload.class), mock(FileUpload.class));

        when(fileUploadRepository.findOrphanedConversationReferences()).thenReturn(orphanedConversationUploads);

        // If this fails you may have added a new entity type that needs to be added as a case to the function
        fileUploadScheduleService.cleanupOrphanedFileUploads();

        verify(fileUploadRepository).findOrphanedConversationReferences();

        verify(fileUploadService).deleteFileUploads(orphanedConversationUploads);
    }

    @Test
    void shouldDeleteNullEntityFileUploadsWhenOlderThanThreeDays() {
        List<FileUpload> nullEntityUploads = List.of(mock(FileUpload.class), mock(FileUpload.class));

        ZonedDateTime cutoffDate = ZonedDateTime.now().minusDays(FileUploadScheduleService.DAYS_UNTIL_NULL_ENTITY_FILES_ARE_DELETED);

        when(fileUploadRepository.findNullEntityReferences(any())).thenReturn(nullEntityUploads);

        fileUploadScheduleService.cleanupNullEntityFileUploads();

        verify(fileUploadRepository).findNullEntityReferences(argThat(date -> date.isAfter(cutoffDate.minusMinutes(1)) && date.isBefore(cutoffDate.plusMinutes(1))));

        verify(fileUploadService).deleteFileUploads(nullEntityUploads);
    }

}
