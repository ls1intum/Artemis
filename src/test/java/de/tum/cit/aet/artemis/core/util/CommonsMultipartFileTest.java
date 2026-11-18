package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommonsMultipartFileTest {

    @TempDir
    private Path temporaryDirectory;

    @ParameterizedTest
    @CsvSource(value = { "NULL,''", "simple.txt,simple.txt", "/home/user/file.txt,file.txt", "C:\\Users\\user\\file.txt,file.txt", "/folder/,''" }, nullValues = "NULL")
    void originalFilenameRemovesClientDirectoryComponents(String name, String expected) {
        FileItem item = mock(FileItem.class);
        when(item.getName()).thenReturn(name);
        assertThat(new CommonsMultipartFile(item).getOriginalFilename()).isEqualTo(expected);
    }

    @Test
    void memoryBackedFileExposesMetadataAndCanBeCopiedRepeatedly() throws Exception {
        DiskFileItem item = new DiskFileItem("upload", "text/plain", false, "source.txt", 1024, temporaryDirectory.toFile());
        try (var output = item.getOutputStream()) {
            output.write("content".getBytes(StandardCharsets.UTF_8));
        }
        var file = new CommonsMultipartFile(item);
        assertThat(file.getName()).isEqualTo("upload");
        assertThat(file.getContentType()).isEqualTo("text/plain");
        assertThat(file.getSize()).isEqualTo(7);
        assertThat(file.isEmpty()).isFalse();
        assertThat(file.getBytes()).isEqualTo("content".getBytes(StandardCharsets.UTF_8));
        try (var input = file.getInputStream()) {
            assertThat(input.readAllBytes()).isEqualTo(file.getBytes());
        }
        Path existing = temporaryDirectory.resolve("existing.txt");
        FileUtils.writeStringToFile(existing.toFile(), "old", StandardCharsets.UTF_8);
        file.transferTo(existing.toFile());
        assertThat(existing).hasContent("content");
        Path copy = temporaryDirectory.resolve("copy.txt");
        file.transferTo(copy);
        assertThat(copy).hasContent("content");
        assertThat(file.toString()).isEqualTo("MultipartFile[field=\"upload\", filename=source.txt, contentType=text/plain, size=7]");
    }

    @Test
    void movingDiskBackedFileMakesOriginalUnreadableAndUntransferable() throws Exception {
        DiskFileItem item = new DiskFileItem("upload", "text/plain", false, "source.txt", 1, temporaryDirectory.toFile());
        try (var output = item.getOutputStream()) {
            output.write("disk content".getBytes(StandardCharsets.UTF_8));
        }
        var file = new CommonsMultipartFile(item);
        assertThat(file.getBytes()).isEqualTo("disk content".getBytes(StandardCharsets.UTF_8));
        Path destination = temporaryDirectory.resolve("moved.txt");
        file.transferTo(destination.toFile());
        assertThat(destination).hasContent("disk content");
        assertThatThrownBy(file::getBytes).isInstanceOf(IllegalStateException.class).hasMessageContaining("moved");
        assertThatThrownBy(file::getInputStream).isInstanceOf(IllegalStateException.class).hasMessageContaining("moved");
        assertThatThrownBy(() -> file.transferTo(temporaryDirectory.resolve("second.txt"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> file.transferTo(temporaryDirectory.resolve("third.txt").toFile())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullPayloadUsesEmptyContentAndDetectsMovedGenericFile() throws Exception {
        FileItem item = mock(FileItem.class);
        when(item.getFieldName()).thenReturn("upload");
        var file = new CommonsMultipartFile(item);
        assertThat(file.isEmpty()).isTrue();
        assertThat(file.getBytes()).isEmpty();
        try (var input = file.getInputStream()) {
            assertThat(input.read()).isEqualTo(-1);
        }
        assertThat(file.toString()).isEqualTo("MultipartFile[field=\"upload\", size=0]");
        when(item.getSize()).thenReturn(1L);
        assertThatThrownBy(file::getBytes).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void undeletableDestinationAndFileItemErrorsPreserveActionableFailureTypes() throws Exception {
        FileItem item = mock(FileItem.class);
        when(item.isInMemory()).thenReturn(true);
        var file = new CommonsMultipartFile(item);
        File destination = mock(File.class);
        when(destination.exists()).thenReturn(true);
        when(destination.getAbsolutePath()).thenReturn("/cannot-replace");
        assertThatThrownBy(() -> file.transferTo(destination)).isInstanceOf(IOException.class).hasMessageContaining("could not be deleted");
        when(destination.exists()).thenReturn(false);
        doThrow(new FileUploadException("upload failed")).when(item).write(destination);
        assertThatThrownBy(() -> file.transferTo(destination)).isInstanceOf(IllegalStateException.class).hasMessage("upload failed");
        doThrow(new IOException("disk failure")).when(item).write(destination);
        assertThatThrownBy(() -> file.transferTo(destination)).isInstanceOf(IOException.class).hasMessage("disk failure");
        doThrow(new IllegalStateException("unavailable")).when(item).write(destination);
        assertThatThrownBy(() -> file.transferTo(destination)).isInstanceOf(IllegalStateException.class).hasMessage("unavailable");
        doThrow(new Exception("unexpected")).when(item).write(destination);
        assertThatThrownBy(() -> file.transferTo(destination)).isInstanceOf(IOException.class).hasMessage("File transfer failed").hasCauseInstanceOf(Exception.class);
    }
}
