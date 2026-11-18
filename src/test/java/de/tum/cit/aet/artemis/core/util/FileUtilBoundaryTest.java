package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class FileUtilBoundaryTest {

    @TempDir
    private Path temporaryDirectory;

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void uploadsSelectCorrectStorageAndPreserveFileContents(boolean markdown) throws Exception {
        Path temp = temporaryDirectory.resolve("temp");
        Path markdownDirectory = temporaryDirectory.resolve("markdown");
        var file = new MockMultipartFile("upload", "my file.png", "image/png", "uploaded bytes".getBytes(StandardCharsets.UTF_8));
        try (var paths = mockStatic(FilePathConverter.class)) {
            paths.when(FilePathConverter::getTempFilePath).thenReturn(temp);
            paths.when(FilePathConverter::getMarkdownFilePath).thenReturn(markdownDirectory);
            URI uri = FileUtil.handleSaveFile(file, true, markdown);
            assertThat(uri.toString()).startsWith(markdown ? "markdown/Markdown_" : "temp/Temp_").endsWith("_my_file.png");
            Path destination = (markdown ? markdownDirectory : temp).resolve(Path.of(uri.toString()).getFileName());
            assertThat(destination).hasContent("uploaded bytes");
            assertThat(Files.exists(markdown ? temp : markdownDirectory)).isFalse();
        }
    }

    @Test
    void transformsTextRecursivelyButPreservesBinaryIgnoredFilesAndGitMetadata() throws Exception {
        Files.createDirectory(temporaryDirectory.resolve("nested"));
        Files.createDirectory(temporaryDirectory.resolve(".git"));
        Path text = temporaryDirectory.resolve("nested/text.txt");
        FileUtils.writeStringToFile(text.toFile(), "hello TOKEN\r\nnext\rline", StandardCharsets.UTF_8);
        Path ignored = temporaryDirectory.resolve("ignored.txt");
        FileUtils.writeStringToFile(ignored.toFile(), "TOKEN", StandardCharsets.UTF_8);
        Path git = temporaryDirectory.resolve(".git/config");
        FileUtils.writeStringToFile(git.toFile(), "TOKEN\r\n", StandardCharsets.UTF_8);
        byte[] binaryBytes = new byte[] { 0, (byte) 0xff, 1 };
        Path binary = temporaryDirectory.resolve("image.png");
        FileUtils.writeByteArrayToFile(binary.toFile(), binaryBytes);
        FileUtil.replaceVariablesInFileRecursive(temporaryDirectory, Map.of("TOKEN", "WORLD"), List.of("ignored.txt"));
        assertThat(Files.readString(text)).isEqualTo("hello WORLD\r\nnext\rline");
        assertThat(ignored).hasContent("TOKEN");
        assertThat(Files.readString(git)).isEqualTo("TOKEN\r\n");
        assertThat(Files.readAllBytes(binary)).isEqualTo(binaryBytes);
        FileUtil.normalizeLineEndingsDirectory(temporaryDirectory);
        assertThat(Files.readString(text)).isEqualTo("hello WORLD\nnext\nline");
        assertThat(Files.readString(git)).isEqualTo("TOKEN\r\n");
        assertThat(Files.readAllBytes(binary)).isEqualTo(binaryBytes);
    }

    @Test
    void recursiveOperationsRejectFilesAsDirectoriesAndMissingPaths() throws Exception {
        Path file = temporaryDirectory.resolve("file.txt");
        FileUtils.writeStringToFile(file.toFile(), "text", StandardCharsets.UTF_8);
        for (Path path : List.of(file, temporaryDirectory.resolve("missing"))) {
            assertThatThrownBy(() -> FileUtil.replaceVariablesInDirectoryName(path, "x", "y")).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> FileUtil.replaceVariablesInFilename(path, "x", "y")).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> FileUtil.replaceVariablesInFileRecursive(path, Map.of())).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> FileUtil.normalizeLineEndingsDirectory(path)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> FileUtil.convertFilesInDirectoryToUtf8(path)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void directoryRenamingRecursesButKeepsGitInternalsUntouched() throws Exception {
        Path original = Files.createDirectories(temporaryDirectory.resolve("source_TOKEN/nested_TOKEN"));
        FileUtils.writeStringToFile(original.resolve("keep.txt").toFile(), "content", StandardCharsets.UTF_8);
        Path git = Files.createDirectories(temporaryDirectory.resolve("source_TOKEN/.git/inside_TOKEN"));
        FileUtils.writeStringToFile(git.resolve("config").toFile(), "config", StandardCharsets.UTF_8);
        FileUtil.replaceVariablesInDirectoryName(temporaryDirectory.resolve("source_TOKEN"), "TOKEN", "done");
        assertThat(temporaryDirectory.resolve("source_done/nested_done/keep.txt")).hasContent("content");
        assertThat(temporaryDirectory.resolve("source_done/.git/inside_TOKEN/config")).hasContent("config");
        assertThat(temporaryDirectory.resolve("source_TOKEN")).doesNotExist();
    }

    @Test
    void nonFileResourcesDecodePathsAndIgnoredDirectoriesNeverOpenTheirStream() throws Exception {
        Resource resource = mock(Resource.class);
        when(resource.isFile()).thenReturn(false);
        when(resource.getURL()).thenReturn(URI.create("file:/templates/source/sub/file.txt").toURL());
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("resource bytes".getBytes(StandardCharsets.UTF_8)));
        FileUtil.copyResource(resource, Path.of("templates/source"), temporaryDirectory, true);
        assertThat(temporaryDirectory.resolve("sub/file.txt")).hasContent("resource bytes");
        Resource directory = mock(Resource.class);
        when(directory.isFile()).thenReturn(false);
        when(directory.getURL()).thenReturn(URI.create("file:/templates/source/project.xcodeproj").toURL());
        FileUtil.copyResource(directory, Path.of("templates/source"), temporaryDirectory, false);
        verify(directory, org.mockito.Mockito.never()).getInputStream();
    }
}
