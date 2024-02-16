package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class FileServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    @Autowired
    private FileService fileService;

    private final Path javaPath = Path.of("templates", "java", "java.txt");

    // the resource loader allows to load resources from the file system for this prefix
    private final Path overridableBasePath = Path.of("templates", "jenkins");

    private static final URI VALID_BACKGROUND_PATH = URI.create("/api/uploads/images/drag-and-drop/backgrounds/1/BackgroundFile.jpg");

    private static final URI VALID_INTENDED_BACKGROUND_PATH = createURIWithPath("/api/", FilePathService.getDragAndDropBackgroundFilePath());

    private static final URI INVALID_BACKGROUND_PATH = URI.create("/api/uploads/images/drag-and-drop/backgrounds/1/../../../exam-users/signatures/some-file.png");

    private static final URI VALID_DRAGITEM_PATH = URI.create("/api/uploads/images/drag-and-drop/drag-items/1/PictureFile.jpg");

    private static final URI VALID_INTENDED_DRAGITEM_PATH = createURIWithPath("/api/", FilePathService.getDragItemFilePath());

    private static final URI INVALID_DRAGITEM_PATH = URI.create("/api/uploads/images/drag-and-drop/drag-items/1/../../../exam-users/signatures/some-file.png");

    private static URI createURIWithPath(String prefix, Path path) {
        String replacementForWindows = path.toString().replace('\\', '/');
        return URI.create(prefix + replacementForWindows + '/');
    }

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(javaPath);
        FileUtils.deleteDirectory(overridableBasePath.toFile());
    }

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

    private void writeFile(String destinationPath, byte[] bytes) {
        try {
            FileUtils.writeByteArrayToFile(Path.of(".", "exportTest", destinationPath).toFile(), bytes);
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
    void testGetFileForPath() throws IOException {
        writeFile("testFile.txt", FILE_WITH_UNIX_LINE_ENDINGS);
        byte[] result = fileService.getFileForPath(Path.of(".", "exportTest", "testFile.txt"));
        assertThat(result).containsExactly(FILE_WITH_UNIX_LINE_ENDINGS.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testGetFileFOrPath_notFound() throws IOException {
        writeFile("testFile.txt", FILE_WITH_UNIX_LINE_ENDINGS);
        byte[] result = fileService.getFileForPath(Path.of(".", "exportTest", UUID.randomUUID() + ".txt"));
        assertThat(result).isNull();
    }

    @Test
    void testHandleSaveFile_noOriginalFilename() {
        MultipartFile file = mock(MultipartFile.class);
        doAnswer(invocation -> null).when(file).getOriginalFilename();
        assertThatThrownBy(() -> fileService.handleSaveFile(file, false, false)).isInstanceOf(IllegalArgumentException.class);
        verify(file, times(1)).getOriginalFilename();
    }

    @Test
    void testCopyExistingFileToTarget() throws IOException {
        String payload = "test";
        Path filePath = Path.of(".", "exportTest", "testFile.txt");
        FileUtils.writeStringToFile(filePath.toFile(), payload, StandardCharsets.UTF_8);
        Path newFolder = Path.of(".", "exportTest", "newFolder");

        Path newPath = fileService.copyExistingFileToTarget(filePath, newFolder);
        assertThat(newPath).isNotNull();

        assertThat(FileUtils.readFileToString(newPath.toFile(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void testCopyExistingFileToTarget_newFile() {
        assertThat(fileService.copyExistingFileToTarget(null, Path.of(".", "exportTest"))).isNull();
    }

    @Test
    void testCopyExistingFileToTarget_temporaryFile() {
        // We don't need to create a file here as we expect the method to terminate early
        Path tempPath = Path.of(".", "uploads", "files", "temp", "testFile.txt");
        Path newPath = Path.of(".", "exportTest");
        assertThat(fileService.copyExistingFileToTarget(tempPath, newPath)).isNull();
    }

    @Test
    void normalizeFileEndingsUnix_noChange() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void normalizeFileEndingsUnix_normalized() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);

        fileService.normalizeLineEndings(Path.of(".", "exportTest", "LineEndingsUnix.java"));
        size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void normalizeFileEndingsWindows_noChange() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(136);
    }

    @Test
    void normalizeFileEndingsWindows_normalized() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(136);

        fileService.normalizeLineEndings(Path.of(".", "exportTest", "LineEndingsWindows.java"));
        size = Files.readAllBytes(Path.of(".", "exportTest", "LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    void normalizeEncodingUTF8() throws IOException {
        copyFile("EncodingUTF8.java", "EncodingUTF8.java");
        Charset charset = fileService.detectCharset(Files.readAllBytes(Path.of(".", "exportTest", "EncodingUTF8.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void normalizeEncodingISO_8559_1() throws IOException {
        copyFile("EncodingISO_8559_1.java", "EncodingISO_8559_1.java");
        Charset charset = fileService.detectCharset(Files.readAllBytes(Path.of(".", "exportTest", "EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(StandardCharsets.ISO_8859_1);

        fileService.convertToUTF8(Path.of(".", "exportTest", "EncodingISO_8559_1.java"));
        charset = fileService.detectCharset(Files.readAllBytes(Path.of(".", "exportTest", "EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void replacePlaceHolder() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = Path.of(".", "exportTest", "pom.xml").toFile();
        String fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        fileService.replaceVariablesInFileRecursive(pomXml.getParentFile().toPath(), replacements);
        fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).doesNotContain("${exerciseName}").contains("SomeCoolExerciseName");
    }

    @Test
    void replacePlaceHolderIgnoreNames() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = Path.of(".", "exportTest", "pom.xml").toFile();
        String fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        fileService.replaceVariablesInFileRecursive(pomXml.getParentFile().toPath(), replacements, List.of("pom.xml"));
        fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}").doesNotContain("SomeCoolExerciseName");
    }

    @Test
    void testMergePdf_nullInput_shouldReturnEmptyOptional() {
        Optional<byte[]> result = fileService.mergePdfFiles(null, "");
        assertThat(result).isEmpty();
    }

    @Test
    void testMergePdf_emptyList_shouldReturnEmptyOptional() {
        Optional<byte[]> result = fileService.mergePdfFiles(new ArrayList<>(), "list_of_pdfs");
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

        paths.add(Path.of(".", "exportTest", "testfile1.pdf"));
        paths.add(Path.of(".", "exportTest", "testfile2.pdf"));

        Optional<byte[]> mergedFile = fileService.mergePdfFiles(paths, "list_of_pdfs");
        assertThat(mergedFile).isPresent();
        assertThat(mergedFile.get()).isNotEmpty();
        PDDocument mergedDoc = Loader.loadPDF(mergedFile.get());
        assertThat(mergedDoc.getNumberOfPages()).isEqualTo(5);
    }

    @Test
    void testReplaceVariablesInFileRecursive_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> fileService.replaceVariablesInFileRecursive(Path.of("some-path"), new HashMap<>()))
                .withMessageEndingWith("should be replaced but the directory does not exist.");
    }

    @Test
    void testNormalizeLineEndingsDirectory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> fileService.normalizeLineEndingsDirectory(Path.of("some-path")))
                .withMessageEndingWith("should be normalized but the directory does not exist.");
    }

    @Test
    void testConvertToUTF8Directory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> fileService.convertFilesInDirectoryToUtf8(Path.of("some-path")))
                .withMessageEndingWith("should be converted to UTF-8 but the directory does not exist.");
    }

    // TODO: either rework those tests or delete them
    @Test
    void testGetUniqueTemporaryPath_shouldNotThrowException() {
        assertThatNoException().isThrownBy(() -> {
            var uniquePath = fileService.getTemporaryUniqueSubfolderPath(Path.of("some-random-path-which-does-not-exist"), 1);
            assertThat(uniquePath.toString()).isNotEmpty();
            verify(fileService).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(1L));
        });
    }

    @Test
    void testDeleteFiles_shouldNotThrowException() {
        Path path = Path.of("some-random-path-which-does-not-exist");
        assertThatNoException().isThrownBy(() -> fileService.deleteFiles(List.of(path)));
    }

    @Test
    void testCopyResourceKeepDirectories(@TempDir Path targetDir) throws IOException {
        final Resource[] resources = { resourceLoaderService.getResource(javaPath) };

        fileService.copyResources(resources, Path.of("templates"), targetDir, true);

        final Path expectedTargetFile = targetDir.resolve("java").resolve("java.txt");
        assertThat(expectedTargetFile).exists().isNotEmptyFile();
    }

    @Test
    void testCopyResourceDoNotKeepDirectory(@TempDir Path targetDir) throws IOException {
        final Resource[] resources = { resourceLoaderService.getResource(javaPath) };

        fileService.copyResources(resources, Path.of("templates"), targetDir, false);

        final Path expectedTargetFile = targetDir.resolve("java.txt");
        assertThat(expectedTargetFile).exists().isNotEmptyFile();
    }

    @Test
    void testCopyResourceRemovePrefix(@TempDir Path targetDir) throws IOException {
        final Resource[] resources = { resourceLoaderService.getResource(javaPath) };

        fileService.copyResources(resources, Path.of("templates", "java"), targetDir, true);

        final Path expectedTargetFile = targetDir.resolve("java.txt");
        assertThat(expectedTargetFile).exists().isNotEmptyFile();
    }

    @Test
    void testRenameSpecialFilename(@TempDir Path targetDir) throws IOException {
        final Path sourceFile = overridableBasePath.resolve("Makefile.file");
        FileUtils.writeStringToFile(sourceFile.toFile(), "content", Charset.defaultCharset());

        final Resource[] resources = resourceLoaderService.getResources(overridableBasePath);
        assertThat(resources).isNotEmpty();

        fileService.copyResources(resources, Path.of("templates"), targetDir, true);

        final Path expectedTargetFile = targetDir.resolve("jenkins").resolve("Makefile");
        assertThat(expectedTargetFile).exists().isNotEmptyFile();
    }

    @Test
    void testIgnoreDirectoryFalsePositives(@TempDir Path targetDir) throws IOException {
        final Path sourceDirectory = overridableBasePath.resolve("package.xcworkspace");
        Files.createDirectories(sourceDirectory);

        final Resource[] resources = resourceLoaderService.getResources(overridableBasePath);
        assertThat(resources).isNotEmpty();

        fileService.copyResources(resources, Path.of("templates"), targetDir, true);

        final Path expectedTargetFile = targetDir.resolve("jenkins").resolve("package.xcworkspace");
        assertThat(expectedTargetFile).doesNotExist();
    }

    /**
     * Tests whether FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow correctly indicates, that VALID_BACKGROUND_PATH starts with VALID_INTENDED_BACKGROUND_PATH
     */
    @Test
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Valid() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(VALID_BACKGROUND_PATH, VALID_INTENDED_BACKGROUND_PATH)).doesNotThrowAnyException();
    }

    /**
     * Tests whether FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow correctly indicates, that INVALID_BACKGROUND_PATH does not start with
     * VALID_INTENDED_BACKGROUND_PATH
     */
    @Test
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Background_Invalid_Path() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> FileService.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(INVALID_BACKGROUND_PATH, VALID_INTENDED_BACKGROUND_PATH));
    }

    /**
     * Tests whether FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow correctly indicates, that VALID_DRAGITEM_PATH starts with VALID_INTENDED_DRAGITEM_PATH
     */
    @Test
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Picture_Valid() {
        assertThatCode(() -> FileService.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(VALID_DRAGITEM_PATH, VALID_INTENDED_DRAGITEM_PATH)).doesNotThrowAnyException();
    }

    /**
     * Tests whether FileService.sanitizeByCheckingIfPathContainsSubPathElseThrow correctly indicates, that INVALID_DRAGITEM_PATH does not start with VALID_INTENDED_DRAGITEM_PATH
     */
    @Test
    void testSanitizeByCheckingIfPathContainsSubPathElseThrow_Picture_Invalid_Path() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> FileService.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(INVALID_DRAGITEM_PATH, VALID_INTENDED_DRAGITEM_PATH));
    }
}
