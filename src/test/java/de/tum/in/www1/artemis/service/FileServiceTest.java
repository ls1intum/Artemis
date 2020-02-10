package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationTest;

public class FileServiceTest extends AbstractSpringIntegrationTest {

    @Autowired
    FileService fileService;

    private void copyFile(String filePath, String destinationPath) {
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/repository-export/" + filePath), new File("./exportTest/" + destinationPath));
        }
        catch (IOException ex) {
            fail("Failed while copying test files", ex);
        }
    }

    private void writeFile(String destinationPath, byte[] content) {
        try {
            FileUtils.writeByteArrayToFile(new File("./exportTest/" + destinationPath), content);
        }
        catch (IOException ex) {
            fail("Failed while writing test files", ex);
        }
    }

    @AfterEach
    @BeforeEach
    private void deleteFiles() throws IOException {
        FileUtils.deleteDirectory(new File("./exportTest/"));
    }

    @Test
    public void normalizeFileEndingsUnix_noChange() throws IOException {
        copyFile("LineEndingsUnix.java", "LineEndingsUnix.java");
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeFileEndingsUnix_normalized() throws IOException {
        copyFile("LineEndingsUnix.java", "LineEndingsUnix.java");
        fileService.normalizeLineEndings("./exportTest/LineEndingsUnix.java");
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(129);
    }

    @Test
    public void normalizeFileEndingsWindows_noChange() throws IOException {
        // We have to save the array as byte as git will automatically convert CRLF -> LF, therefor we cannot use the version of the file stored in the test data
        byte[] lineEndingsWindowsArray = new byte[] { 112, 117, 98, 108, 105, 99, 32, 99, 108, 97, 115, 115, 32, 76, 105, 110, 101, 69, 110, 100, 105, 110, 103, 115, 32, 123, 13,
                10, 13, 10, 32, 32, 32, 32, 112, 117, 98, 108, 105, 99, 32, 118, 111, 105, 100, 32, 115, 111, 109, 101, 77, 101, 116, 104, 111, 100, 40, 41, 32, 123, 13, 10, 32,
                32, 32, 32, 32, 32, 32, 32, 47, 47, 32, 83, 111, 109, 101, 32, 108, 111, 103, 105, 99, 32, 105, 110, 115, 105, 100, 101, 32, 104, 101, 114, 101, 13, 10, 32, 32, 32,
                32, 32, 32, 32, 32, 115, 111, 109, 101, 83, 101, 114, 118, 105, 99, 101, 46, 99, 97, 108, 108, 40, 41, 59, 13, 10, 32, 32, 32, 32, 125, 13, 10, 125, 13, 10 };
        System.out.println(lineEndingsWindowsArray.length);
        writeFile("LineEndingsWindows.java", lineEndingsWindowsArray);
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(136);
    }

    @Test
    public void normalizeFileEndingsWindows_normalized() throws IOException {
        // We have to save the array as byte as git will automatically convert CRLF -> LF, therefor we cannot use the version of the file stored in the test data
        byte[] lineEndingsWindowsArray = new byte[] { 112, 117, 98, 108, 105, 99, 32, 99, 108, 97, 115, 115, 32, 76, 105, 110, 101, 69, 110, 100, 105, 110, 103, 115, 32, 123, 13,
                10, 13, 10, 32, 32, 32, 32, 112, 117, 98, 108, 105, 99, 32, 118, 111, 105, 100, 32, 115, 111, 109, 101, 77, 101, 116, 104, 111, 100, 40, 41, 32, 123, 13, 10, 32,
                32, 32, 32, 32, 32, 32, 32, 47, 47, 32, 83, 111, 109, 101, 32, 108, 111, 103, 105, 99, 32, 105, 110, 115, 105, 100, 101, 32, 104, 101, 114, 101, 13, 10, 32, 32, 32,
                32, 32, 32, 32, 32, 115, 111, 109, 101, 83, 101, 114, 118, 105, 99, 101, 46, 99, 97, 108, 108, 40, 41, 59, 13, 10, 32, 32, 32, 32, 125, 13, 10, 125, 13, 10 };
        writeFile("LineEndingsWindows.java", lineEndingsWindowsArray);
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
        assertThat(charset).isEqualTo(Charsets.UTF_8);
    }

    @Test
    public void normalizeEncodingISO_8559_1() throws IOException {
        copyFile("EncodingISO_8559_1.java", "EncodingISO_8559_1.java");
        Charset charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(Charsets.ISO_8859_1);

        fileService.convertToUTF8("./exportTest/EncodingISO_8559_1.java");
        charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(Charsets.UTF_8);
    }
}
