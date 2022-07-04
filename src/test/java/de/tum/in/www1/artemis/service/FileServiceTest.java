package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.exception.FilePathParsingException;

public class FileServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    /*
     * We have to save the content as a String as git will automatically convert the line endings based on the developer's OS, therefore we do not store it as a file in
     * src/test/resources/test-data
     */

    private static final String FILE_WITH_UNIX_LINE_ENDINGS = """
            public class LineEndings {

                public void someMethod() {
                    // Some logic inside here
                    someService.call();
                }
            }
            """;

    private static final String FILE_WITH_WINDOWS_LINE_ENDINGS = """
            public class LineEndings {\r
            \r
                public void someMethod() {\r
                    // Some logic inside here\r
                    someService.call();\r
                }\r
            }\r
            """;

    private void copyFile(String filePath, String destinationPath) {
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/repository-export/" + filePath), Path.of(".", "exportTest", destinationPath).toFile());
        }
        catch (IOException ex) {
            fail("Failed while copying test files", ex);
        }
    }

    private void writeFile(String destinationPath, String content) {
        try {
            FileUtils.writeByteArrayToFile(Path.of(".", "exportTest", destinationPath).toFile(), content.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex) {
            fail("Failed while writing test files", ex);
        }
    }

    @AfterEach
    @BeforeEach
    void deleteFiles() throws IOException {
        FileUtils.deleteDirectory(Path.of(".", "exportTest").toFile());
    }

    @Test
    public void normalizeFileEndingsUnix_noChange() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeFileEndingsUnix_normalized() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);

        fileService.normalizeLineEndings(Path.of(".", "exportTest", "LineEndingsUnix.java").toString());
        size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeFileEndingsWindows_noChange() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(136);
    }

    @Test
    public void normalizeFileEndingsWindows_normalized() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(136);

        fileService.normalizeLineEndings(Path.of(".", "exportTest", "LineEndingsWindows.java").toString());
        size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeEncodingUTF8() throws IOException {
        copyFile("EncodingUTF8.java", "EncodingUTF8.java");
        Charset charset = fileService.detectCharset(Files.readAllBytes(Path.of(".", "exportTest", "EncodingUTF8.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    public void normalizeEncodingISO_8559_1() throws IOException {
        copyFile("EncodingISO_8559_1.java", "EncodingISO_8559_1.java");
        Charset charset = fileService.detectCharset(Files.readAllBytes(Path.of(".", "exportTest", "EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(StandardCharsets.ISO_8859_1);

        fileService.convertToUTF8(Path.of(".", "exportTest", "EncodingISO_8559_1.java").toString());
        charset = fileService.detectCharset(Files.readAllBytes(Path.of(".", "exportTest", "EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    public void replacePlaceHolder() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = Path.of(".", "exportTest", "pom.xml").toFile();
        String fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        fileService.replaceVariablesInFileRecursive(pomXml.getParent(), replacements);
        fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).doesNotContain("${exerciseName}").contains("SomeCoolExerciseName");
    }

    @Test
    public void replacePlaceHolderIgnoreNames() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = Path.of(".", "exportTest", "pom.xml").toFile();
        String fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        fileService.replaceVariablesInFileRecursive(pomXml.getParent(), replacements, List.of("pom.xml"));
        fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");
    }

    @Test
    public void testMergePdf_nullInput_shouldReturnEmptyOptional() {
        Optional<byte[]> result = fileService.mergePdfFiles(null, "");
        assertThat(result).isEmpty();
    }

    @Test
    public void testMergePdf_emptyList_shouldReturnEmptyOptional() {
        Optional<byte[]> result = fileService.mergePdfFiles(new ArrayList<>(), "list_of_pdfs");
        assertThat(result).isEmpty();
    }

    @Test
    public void testMergePdf() throws IOException {
        List<String> paths = new ArrayList<>();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PDDocument doc1 = new PDDocument();
        doc1.addPage(new PDPage());
        doc1.addPage(new PDPage());
        doc1.addPage(new PDPage());
        doc1.save(outputStream);
        doc1.close();

        writeFile("testfile1.pdf", outputStream.toString());

        PDDocument doc2 = new PDDocument();
        doc2.addPage(new PDPage());
        doc2.addPage(new PDPage());
        doc2.save(outputStream);
        doc2.close();

        writeFile("testfile2.pdf", outputStream.toString());

        paths.add(Path.of(".", "exportTest", "testfile1.pdf").toString());
        paths.add(Path.of(".", "exportTest", "testfile2.pdf").toString());

        Optional<byte[]> mergedFile = fileService.mergePdfFiles(paths, "list_of_pdfs");
        assertThat(mergedFile).isPresent();
        assertThat(mergedFile.get()).isNotEmpty();
        PDDocument mergedDoc = PDDocument.load(mergedFile.get());
        assertThat(mergedDoc.getNumberOfPages()).isEqualTo(5);
    }

    @Test
    public void testManageFilesForUpdatedFilePath_shouldNotThrowException() {
        assertDoesNotThrow(() -> {
            fileService.manageFilesForUpdatedFilePath("oldFilePath", "newFilePath", "targetFolder", 1L, true);
        });
    }

    @Test
    public void testActualPathForPublicPath() {
        String actualPath = fileService.actualPathForPublicPath("asdasdfiles/drag-and-drop/backgrounds");
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "drag-and-drop", "backgrounds", "backgrounds").toString());

        actualPath = fileService.actualPathForPublicPath("asdasdfiles/drag-and-drop/drag-items");
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "drag-and-drop", "drag-items", "drag-items").toString());

        actualPath = fileService.actualPathForPublicPath("asdasdfiles/course/icons");
        assertThat(actualPath).isEqualTo(Path.of("uploads", "images", "course", "icons", "icons").toString());

        actualPath = fileService.actualPathForPublicPath("asdasdfiles/attachments/lecture");
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "lecture", "asdasdfiles", "attachments", "lecture").toString());

        actualPath = fileService.actualPathForPublicPath("asdasdfiles/attachments/attachment-unit");
        assertThat(actualPath).isEqualTo(Path.of("uploads", "attachments", "attachment-unit", "asdasdfiles", "attachments", "attachment-unit").toString());
    }

    @Test
    public void testActualPathForPublicFileUploadExercisePath_shouldThrowException() {
        Exception exception = assertThrows(FilePathParsingException.class, () -> fileService.actualPathForPublicPath("asdasdfiles/file-upload-exercises"));
        assertThat(exception.getMessage()).startsWith("Public path does not contain correct exerciseId or submissionId:");

        exception = assertThrows(FilePathParsingException.class, () -> fileService.actualPathForPublicPath("asdasdfiles/file-asd-exercises"));
        assertThat(exception.getMessage()).startsWith("Unknown Filepath:");
    }

    @Test
    public void testPublicPathForActualTempFilePath() {
        Path actualPath = Path.of(FilePathService.getTempFilePath(), "test");
        String publicPath = fileService.publicPathForActualPath(actualPath.toString(), 1L);
        assertThat(publicPath).isEqualTo("/api/files/temp/" + actualPath.getFileName());
    }

    @Test
    public void testPublicPathForActualPath_shouldThrowException() {
        Exception exception = assertThrows(FilePathParsingException.class, () -> {
            Path actualFileUploadPath = Path.of(FilePathService.getFileUploadExercisesFilePath());
            fileService.publicPathForActualPath(actualFileUploadPath.toString(), 1L);
        });
        assertThat(exception.getMessage()).startsWith("Unexpected String in upload file path. Exercise ID should be present here:");

        exception = assertThrows(FilePathParsingException.class, () -> fileService.publicPathForActualPath(Path.of("asdasdfiles", "file-asd-exercises").toString(), 1L));
        assertThat(exception.getMessage()).startsWith("Unknown Filepath:");
    }

    @Test
    public void testReplaceVariablesInFileRecursive_shouldThrowException() {
        Exception exception = assertThrows(RuntimeException.class, () -> fileService.replaceVariablesInFileRecursive("some-path", new HashMap<>()));
        assertThat(exception.getMessage()).endsWith("should be replaced but the directory does not exist.");
    }

    @Test
    public void testNormalizeLineEndingsDirectory_shouldThrowException() {
        Exception exception = assertThrows(RuntimeException.class, () -> fileService.normalizeLineEndingsDirectory("some-path"));
        assertThat(exception.getMessage()).endsWith("should be normalized but the directory does not exist.");
    }

    @Test
    public void testConvertToUTF8Directory_shouldThrowException() {
        Exception exception = assertThrows(RuntimeException.class, () -> fileService.convertToUTF8Directory("some-path"));
        assertThat(exception.getMessage()).endsWith("should be converted to UTF-8 but the directory does not exist.");
    }

    @Test
    public void testGetUniquePath_shouldNotThrowException() {
        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.isDirectory(any())).thenReturn(true);
        mockedFiles.when(() -> Files.createDirectories(any())).thenThrow(NoSuchFileException.class);
        assertDoesNotThrow(() -> {
            var uniquePath = fileService.getUniquePath("some-path");
            assertThat(uniquePath.toString()).isNotEmpty();
        });
        mockedFiles.close();
    }

    @Test
    public void testCreateDirectory_shouldNotThrowException() {
        Path path = Path.of("some-path");
        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.createDirectories(path)).thenThrow(NoSuchFileException.class);
        assertDoesNotThrow(() -> fileService.createDirectory(path));
        mockedFiles.close();
    }

    @Test
    public void testDeleteFiles_shouldNotThrowException() {
        Path path = Path.of("some-path");
        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.delete(path)).thenThrow(NoSuchFileException.class);
        assertDoesNotThrow(() -> fileService.deleteFiles(List.of(path)));
        mockedFiles.close();
    }
}
