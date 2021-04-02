package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private FileService fileService;

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
}
