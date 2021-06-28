package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

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
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/repository-export/" + filePath), new File("./exportTest/" + destinationPath));
        }
        catch (IOException ex) {
            fail("Failed while copying test files", ex);
        }
    }

    private void writeFile(String destinationPath, String content) {
        try {
            FileUtils.writeByteArrayToFile(new File("./exportTest/" + destinationPath), content.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex) {
            fail("Failed while writing test files", ex);
        }
    }

    @AfterEach
    @BeforeEach
    void deleteFiles() throws IOException {
        FileUtils.deleteDirectory(new File("./exportTest/"));
    }

    @Test
    public void normalizeFileEndingsUnix_noChange() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeFileEndingsUnix_normalized() throws IOException {
        writeFile("LineEndingsUnix.java", FILE_WITH_UNIX_LINE_ENDINGS);
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);

        fileService.normalizeLineEndings("./exportTest/LineEndingsUnix.java");
        size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeFileEndingsWindows_noChange() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(136);
    }

    @Test
    public void normalizeFileEndingsWindows_normalized() throws IOException {
        writeFile("LineEndingsWindows.java", FILE_WITH_WINDOWS_LINE_ENDINGS);
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(136);

        fileService.normalizeLineEndings("./exportTest/LineEndingsWindows.java");
        size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeEncodingUTF8() throws IOException {
        copyFile("EncodingUTF8.java", "EncodingUTF8.java");
        Charset charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingUTF8.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    public void normalizeEncodingISO_8559_1() throws IOException {
        copyFile("EncodingISO_8559_1.java", "EncodingISO_8559_1.java");
        Charset charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(StandardCharsets.ISO_8859_1);

        fileService.convertToUTF8("./exportTest/EncodingISO_8559_1.java");
        charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    public void replacePlaceHolder() throws IOException {
        copyFile("pom.xml", "pom.xml");
        File pomXml = new File("./exportTest/pom.xml");
        String fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).contains("${exerciseName}");
        assertThat(fileContent).doesNotContain("SomeCoolExerciseName");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("${exerciseName}", "SomeCoolExerciseName");

        fileService.replaceVariablesInFileRecursive(pomXml.getParent(), replacements);
        fileContent = FileUtils.readFileToString(pomXml, Charset.defaultCharset());

        assertThat(fileContent).doesNotContain("${exerciseName}");
        assertThat(fileContent).contains("SomeCoolExerciseName");
    }

    @Test
    public void testMergePdf_nullInput_shouldReturnEmptyOptional() {
        Optional<byte[]> result = fileService.mergePdfFiles(null);
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    public void testMergePdf_emptyList_shouldReturnEmptyOptional() {
        Optional<byte[]> result = fileService.mergePdfFiles(new ArrayList<>());
        assertThat(result.isEmpty()).isTrue();
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

        paths.add("./exportTest/testfile1.pdf");
        paths.add("./exportTest/testfile2.pdf");

        Optional<byte[]> mergedFile = fileService.mergePdfFiles(paths);
        assertThat(mergedFile.isPresent()).isTrue();
        assertThat(mergedFile.get()).isNotEmpty();
        PDDocument mergedDoc = PDDocument.load(mergedFile.get());
        assertThat(mergedDoc.getNumberOfPages()).isEqualTo(5);
    }
}
