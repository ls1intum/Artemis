package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.domain.FileUpload;
import de.tum.cit.aet.artemis.core.domain.FileUploadEntityType;
import de.tum.cit.aet.artemis.core.repository.FileUploadRepository;
import de.tum.cit.aet.artemis.core.service.file.FileUploadService;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private FileService fileService;

    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService(fileUploadRepository, fileService);
    }

    @Test
    void shouldCreateFileUploadWhenValidParametersProvided() {
        String path = "/test/path";
        String serverFilePath = "/server/file/path";
        String fileName = "test.txt";
        Long entityId = 1L;
        FileUploadEntityType entityType = FileUploadEntityType.CONVERSATION;

        fileUploadService.createFileUpload(path, serverFilePath, fileName, entityId, entityType);

        verify(fileUploadRepository).save(argThat(fileUpload -> fileUpload.getPath().equals(path) && fileUpload.getServerFilePath().equals(serverFilePath)
                && fileUpload.getFilename().equals(fileName) && fileUpload.getEntityId().equals(entityId) && fileUpload.getEntityType().equals(entityType)));
    }

    @Test
    void shouldFindFileUploadWhenPathExists() {
        String path = "/test/path";
        FileUpload expectedFileUpload = new FileUpload(path, "/server/path", "test.txt", 1L, FileUploadEntityType.CONVERSATION);

        when(fileUploadRepository.findFileUploadByPath(path)).thenReturn(expectedFileUpload);

        Optional<FileUpload> result = fileUploadService.findByPath(path);

        assertThat(result).isPresent().containsSame(expectedFileUpload);
    }

    @Test
    void shouldReturnEmptyOptionalWhenPathDoesNotExist() {
        String path = "/non/existent/path";
        when(fileUploadRepository.findFileUploadByPath(path)).thenReturn(null);

        Optional<FileUpload> result = fileUploadService.findByPath(path);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldDeleteFileUploadsWhenValidFileUploadsProvided() {
        FileUpload fileUpload1 = mock(FileUpload.class);
        FileUpload fileUpload2 = mock(FileUpload.class);
        List<FileUpload> fileUploads = Arrays.asList(fileUpload1, fileUpload2);

        when(fileUpload1.getServerFilePath()).thenReturn("/path/to/file1");
        when(fileUpload2.getServerFilePath()).thenReturn("/path/to/file2");

        fileUploadService.deleteFileUploads(fileUploads);

        verify(fileService).schedulePathForDeletion(Path.of("/path/to/file1"), 1);
        verify(fileService).schedulePathForDeletion(Path.of("/path/to/file2"), 1);
        verify(fileUploadRepository).deleteAll(fileUploads);
    }

    @Test
    void shouldHandleInvalidPathWhenDeletingFileUploads() {
        FileUpload fileUpload = mock(FileUpload.class);
        List<FileUpload> fileUploads = Collections.singletonList(fileUpload);

        when(fileUpload.getServerFilePath()).thenThrow(new InvalidPathException("", ""));
        when(fileUpload.getPath()).thenReturn("/invalid/path");

        assertThatCode(() -> fileUploadService.deleteFileUploads(fileUploads)).doesNotThrowAnyException();

        verify(fileUploadRepository).deleteAll(fileUploads);
    }

    @Test
    void shouldHandleEmptyFileUploadListWhenDeleting() {
        List<FileUpload> emptyFileUploads = Collections.emptyList();

        fileUploadService.deleteFileUploads(emptyFileUploads);

        verify(fileUploadRepository).deleteAll(emptyFileUploads);
        verifyNoInteractions(fileService);
    }
}
