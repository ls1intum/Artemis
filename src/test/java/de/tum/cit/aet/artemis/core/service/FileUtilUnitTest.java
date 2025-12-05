package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class FileUtilUnitTest {

    public static final Path exportTestRootPath = Path.of(".", "local", "server-integration-test", "exportTest");

    private static final Path lineEndingsUnixPath = exportTestRootPath.resolve("LineEndingsUnix.java");

    private static final Path lineEndingsWindowsPath = exportTestRootPath.resolve("LineEndingsWindows.java");

    @AfterEach
    @BeforeEach
    void deleteFiles() throws IOException {
        RepositoryExportTestUtil.safeDeleteDirectory(exportTestRootPath);
    }

    @Test
    void validPathShouldPass() {
        URI path = URI.create("/api/core/uploads/images/drag-and-drop/backgrounds/1/BackgroundFile.jpg");
        URI subPath = URI.create("/api/core/uploads/images/drag-and-drop");
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(path, subPath));
    }

    @Test
    void invalidPathShouldThrow() {
        URI path = URI.create("/api/core/uploads/images/drag-and-drop/drag-items/1/PictureFile.jpg");
        URI subPath = URI.create("/api/core/uploads/images/drag-and-drop/backgrounds");

        assertThatThrownBy(() -> FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(path, subPath)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid path");
    }

    @Test
    void pathWithPathTraversalShouldThrow() {
        URI path = URI.create("/api/core/uploads/images/drag-and-drop/drag-items/../../exam-users/1/PictureFile.jpg");
        URI subPath = URI.create("/api/core/uploads/images/drag-and-drop/drag-items");

        assertThatThrownBy(() -> FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(path, subPath)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid path");
    }

    @Test
    void validPathWithRedundantElementsShouldPass() {
        URI path = URI.create("/api/core/../core/uploads/./images/drag-and-drop/backgrounds/1/BackgroundFile.jpg");
        URI subPath = URI.create("/api/core/uploads/images/drag-and-drop");
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(path, subPath));
    }

    @Test
    void subPathLongerThanPathShouldThrow() {
        URI path = URI.create("/api/core/uploads");
        URI subPath = URI.create("/api/core/uploads/images");
        assertThatThrownBy(() -> FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(path, subPath)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid path");
    }

    @Test
    void pathAndSubPathEqualShouldPass() {
        URI path = URI.create("/api/core/uploads/images/drag-and-drop");
        URI subPath = URI.create("/api/core/uploads/images/drag-and-drop");
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(path, subPath));
    }

    @Test
    void rootPathShouldPass() {
        URI path = URI.create("/api");
        URI subPath = URI.create("/");
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(path, subPath));
    }

    @ParameterizedTest
    @ValueSource(strings = { "folder/file.txt", "folder/subfolder/file.pdf", "file.docx", "safe_name-123.txt" })
    void testSanitizeFilePath_ValidPaths(String filePath) {
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(filePath));
    }

    @ParameterizedTest
    @ValueSource(strings = { "folder/../file.txt", "folder/evil/../../file.txt" })
    void testSanitizeFilePath_InvalidPaths(String filePath) {
        assertThatThrownBy(() -> FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(filePath)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path is not valid!");
    }

    @Test
    void testSanitizeFilePath_EmptyPath() {
        assertThatNoException().isThrownBy(() -> FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(""));
    }

    @Test
    void normalizeEncodingUTF8() throws IOException {
        copyFile("EncodingUTF8.java", "EncodingUTF8.java");
        Charset charset = FileUtil.detectCharset(Files.readAllBytes(exportTestRootPath.resolve("EncodingUTF8.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void normalizeEncodingISO_8559_1() throws IOException {
        copyFile("EncodingISO_8559_1.java", "EncodingISO_8559_1.java");
        final var exportTestPath = exportTestRootPath.resolve("EncodingISO_8559_1.java");
        Charset charset = FileUtil.detectCharset(Files.readAllBytes(exportTestPath));
        assertThat(charset).isEqualTo(StandardCharsets.ISO_8859_1);

        FileUtil.convertToUTF8(exportTestPath);
        charset = FileUtil.detectCharset(Files.readAllBytes(exportTestPath));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void testReplaceVariablesInFileRecursive_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> FileUtil.replaceVariablesInFileRecursive(Path.of("some-path"), new HashMap<>()))
                .withMessageEndingWith("should be replaced but the directory does not exist.");
    }

    @Test
    void testNormalizeLineEndingsDirectory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> FileUtil.normalizeLineEndingsDirectory(Path.of("some-path")))
                .withMessageEndingWith("should be normalized but the directory does not exist.");
    }

    @Test
    void testConvertToUTF8Directory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> FileUtil.convertFilesInDirectoryToUtf8(Path.of("some-path")))
                .withMessageEndingWith("should be converted to UTF-8 but the directory does not exist.");
    }

    @Test
    void replacePlaceHolder() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = exportTestRootPath.resolve("pom.xml").toFile();
        String fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        FileUtil.replaceVariablesInFileRecursive(pomXml.getParentFile().toPath(), replacements);
        fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).doesNotContain("${exerciseName}").contains("SomeCoolExerciseName");
    }

    @Test
    void replacePlaceHolderIgnoreNames() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = exportTestRootPath.resolve("pom.xml").toFile();
        String fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        FileUtil.replaceVariablesInFileRecursive(pomXml.getParentFile().toPath(), replacements, List.of("pom.xml"));
        fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");
    }

    private static void copyFile(String filePath, String destinationPath) {
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/repository-export/" + filePath), exportTestRootPath.resolve(destinationPath).toFile());
        }
        catch (IOException ex) {
            fail("Failed while copying test files", ex);
        }
    }

    @Test
    void testHandleSaveFile_noOriginalFilename() {
        MultipartFile file = mock(MultipartFile.class);
        doAnswer(invocation -> null).when(file).getOriginalFilename();
        assertThatThrownBy(() -> FileUtil.handleSaveFile(file, false, false)).isInstanceOf(IllegalArgumentException.class);
        verify(file, times(1)).getOriginalFilename();
    }

    @Test
    void testCopyExistingFileToTarget() throws IOException {
        String payload = "test";
        Path filePath = exportTestRootPath.resolve("testFile.txt");
        FileUtils.writeStringToFile(filePath.toFile(), payload, StandardCharsets.UTF_8);
        Path newFolder = exportTestRootPath.resolve("newFolder");

        Path newPath = FileUtil.copyExistingFileToTarget(filePath, newFolder, FilePathType.COURSE_ICON);
        assertThat(newPath).isNotNull();
        assertThat(FileUtils.readFileToString(newPath.toFile(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void testCopyExistingFileToTarget_newFile() {
        assertThat(FileUtil.copyExistingFileToTarget(null, Path.of(".", "exportTest"), FilePathType.DRAG_ITEM)).isNull();
    }

    @Test
    void testCopyExistingFileToTarget_temporaryFile() {
        // We don't need to create a file here as we expect the method to terminate early
        Path tempPath = Path.of(".", "uploads", "files", "temp", "testFile.txt");
        Path newPath = Path.of(".", "exportTest");
        assertThat(FileUtil.copyExistingFileToTarget(tempPath, newPath, FilePathType.TEMPORARY)).isNull();
    }

    @Test
    void normalizeFileEndingsUnix_noChange() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsUnixPath).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void normalizeFileEndingsUnix_normalized() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsUnixPath).length;
        assertThat(size).isEqualTo(129);

        FileUtil.normalizeLineEndings(lineEndingsUnixPath);
        size = Files.readAllBytes(lineEndingsUnixPath).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void normalizeFileEndingsWindows_noChange() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsWindowsPath).length;
        assertThat(size).isEqualTo(136);
    }

    @Test
    void normalizeFileEndingsWindows_normalized() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(lineEndingsWindowsPath).length;
        assertThat(size).isEqualTo(136);

        FileUtil.normalizeLineEndings(lineEndingsWindowsPath);
        size = Files.readAllBytes(lineEndingsWindowsPath).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void testMergePdf_nullInput_shouldReturnEmptyOptional() {
        Optional<byte[]> result = FileUtil.mergePdfFiles(null, "");
        assertThat(result).isEmpty();
    }

    @Test
    void testMergePdf_emptyList_shouldReturnEmptyOptional() {
        Optional<byte[]> result = FileUtil.mergePdfFiles(new ArrayList<>(), "list_of_pdfs");
        assertThat(result).isEmpty();
    }

    @Test
    void testMergePdf() throws IOException {
        List<Path> paths = new ArrayList<>();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PDDocument doc1 = new PDDocument();
        doc1.addPage(new PDPage());
        doc1.addPage(new PDPage());
        doc1.addPage(new PDPage());
        doc1.save(outputStream);
        doc1.close();

        writeFile("testfile1.pdf", outputStream.toByteArray());

        outputStream.reset();
        PDDocument doc2 = new PDDocument();
        doc2.addPage(new PDPage());
        doc2.addPage(new PDPage());
        doc2.save(outputStream);
        doc2.close();

        writeFile("testfile2.pdf", outputStream.toByteArray());

        paths.add(exportTestRootPath.resolve("testfile1.pdf"));
        paths.add(exportTestRootPath.resolve("testfile2.pdf"));

        Optional<byte[]> mergedFile = FileUtil.mergePdfFiles(paths, "list_of_pdfs");
        assertThat(mergedFile).isPresent();
        assertThat(mergedFile.get()).isNotEmpty();
        PDDocument mergedDoc = Loader.loadPDF(mergedFile.get());
        assertThat(mergedDoc.getNumberOfPages()).isEqualTo(5);
    }

    @Test
    void testDeleteFiles_shouldNotThrowException() {
        Path path = Path.of("some-random-path-which-does-not-exist");
        assertThatNoException().isThrownBy(() -> FileUtil.deleteFiles(List.of(path)));
    }

    public static void writeFile(String destinationPath, String content) {
        try {
            FileUtils.writeByteArrayToFile(exportTestRootPath.resolve(destinationPath).toFile(), content.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex) {
            fail("Failed while writing test files", ex);
        }
    }

    public static void writeFile(String destinationPath, byte[] bytes) {
        try {
            FileUtils.writeByteArrayToFile(exportTestRootPath.resolve(destinationPath).toFile(), bytes);
        }
        catch (IOException ex) {
            fail("Failed while writing test files", ex);
        }
    }

    /*
     * We have to save the content as a String as git will automatically convert the line endings based on the developer's OS, therefore we do not store it as a file in
     * src/test/resources/test-data
     */
    public static final String FILE_WITH_UNIX_LINE_ENDINGS = """
            public class LineEndings {

                public void someMethod() {
                    // Some logic inside here
                    someService.call();
                }
            }
            """;

    public static final String FILE_WITH_WINDOWS_LINE_ENDINGS = """
            public class LineEndings {\r
            \r
                public void someMethod() {\r
                    // Some logic inside here\r
                    someService.call();\r
                }\r
            }\r
            """;
}
