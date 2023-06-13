package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.exception.FilePathParsingException;

class FileServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    private final Path javaPath = Path.of("templates", "java", "java.txt");

    // the resource loader allows to load resources from the file system for this prefix
    private final Path overridableBasePath = Path.of("templates", "jenkins");

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

    @AfterEach
    @BeforeEach
    void deleteFiles() throws IOException {
        FileUtils.deleteDirectory(Path.of(".", "exportTest").toFile());
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

        fileService.normalizeLineEndings(Path.of(".", "exportTest", "LineEndingsUnix.java").toString());
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

        fileService.normalizeLineEndings(Path.of(".", "exportTest", "LineEndingsWindows.java").toString());
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

        fileService.convertToUTF8(Path.of(".", "exportTest", "EncodingISO_8559_1.java").toString());
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

        fileService.replaceVariablesInFileRecursive(pomXml.getParent(), replacements);
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

        fileService.replaceVariablesInFileRecursive(pomXml.getParent(), replacements, List.of("pom.xml"));
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
    void testManageFilesForUpdatedFilePath_shouldNotThrowException() {
        assertThatNoException().isThrownBy(() -> fileService.manageFilesForUpdatedFilePath("oldFilePath", "newFilePath", "targetFolder", 1L, true));
    }

    @Test
    void testActualPathForPublicPath() {
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
    void testActualPathForPublicFileUploadExercisePath_shouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> fileService.actualPathForPublicPath("asdasdfiles/file-upload-exercises"))
                .withMessageStartingWith("Public path does not contain correct exerciseId or submissionId:");

        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> fileService.actualPathForPublicPath("asdasdfiles/file-asd-exercises"))
                .withMessageStartingWith("Unknown Filepath:");
    }

    @Test
    void testPublicPathForActualTempFilePath() {
        Path actualPath = Path.of(FilePathService.getTempFilePath(), "test");
        String publicPath = fileService.publicPathForActualPath(actualPath.toString(), 1L);
        assertThat(publicPath).isEqualTo(FileService.DEFAULT_FILE_SUBPATH + actualPath.getFileName());
    }

    @Test
    void testPublicPathForActualPath_shouldThrowException() {
        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> {
            Path actualFileUploadPath = Path.of(FilePathService.getFileUploadExercisesFilePath());
            fileService.publicPathForActualPath(actualFileUploadPath.toString(), 1L);

        }).withMessageStartingWith("Unexpected String in upload file path. Exercise ID should be present here:");

        assertThatExceptionOfType(FilePathParsingException.class).isThrownBy(() -> fileService.publicPathForActualPath(Path.of("asdasdfiles", "file-asd-exercises").toString(), 1L))
                .withMessageStartingWith("Unknown Filepath:");
    }

    @Test
    void testReplaceVariablesInFileRecursive_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> fileService.replaceVariablesInFileRecursive("some-path", new HashMap<>()))
                .withMessageEndingWith("should be replaced but the directory does not exist.");
    }

    @Test
    void testNormalizeLineEndingsDirectory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> fileService.normalizeLineEndingsDirectory("some-path"))
                .withMessageEndingWith("should be normalized but the directory does not exist.");
    }

    @Test
    void testConvertToUTF8Directory_shouldThrowException() {
        assertThatRuntimeException().isThrownBy(() -> fileService.convertToUTF8Directory("some-path"))
                .withMessageEndingWith("should be converted to UTF-8 but the directory does not exist.");
    }

    // TODO: either rework those tests or delete them
    @Test
    void testGetUniquePath_shouldNotThrowException() {
        assertThatNoException().isThrownBy(() -> {
            var uniquePath = fileService.getUniquePath("some-random-path-which-does-not-exist");
            assertThat(uniquePath.toString()).isNotEmpty();
        });
    }

    @Test
    void testCreateDirectory_shouldNotThrowException() {
        Path path = Path.of("some-random-path-which-does-not-exist");
        assertThatNoException().isThrownBy(() -> fileService.createDirectory(path));
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
}
