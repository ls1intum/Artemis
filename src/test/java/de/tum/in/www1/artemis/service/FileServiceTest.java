package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ResourceUtils;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("artemis")
public class FileServiceTest {

    @Autowired
    private MockMvc mockMvc;

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

    @AfterEach
    @Before
    private void deleteFiles() throws IOException {
        FileUtils.deleteDirectory(new File("./exportTest/"));
    }

    @Test
    public void normalizeFileEndingsUnix_noChange() throws IOException {
        copyFile("LineEndingsUnix.java", "LineEndingsUnix.java");
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(128);
    }

    @Test
    public void normalizeFileEndingsUnix_normalized() throws IOException {
        copyFile("LineEndingsUnix.java", "LineEndingsUnix.java");
        fileService.normalizeLineEndings("./exportTest/LineEndingsUnix.java");
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsUnix.java")).length;
        assertThat(size).isEqualTo(128);
    }

    @Test
    public void normalizeFileEndingsWindows_noChange() throws IOException {
        copyFile("LineEndingsWindows.java", "LineEndingsWindows.java");
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(134);
    }

    @Test
    public void normalizeFileEndingsWindows_normalized() throws IOException {
        copyFile("LineEndingsWindows.java", "LineEndingsWindows.java");
        fileService.normalizeLineEndings("./exportTest/LineEndingsWindows.java");
        int size = FileUtils.readFileToByteArray(new File("./exportTest/LineEndingsWindows.java")).length;
        assertThat(size).isEqualTo(128);
    }

    @Test
    public void normalizeEncodingUTF8() throws IOException {
        copyFile("EncodingUTF8.java", "EncodingUTF8.java");
        Charset charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingUTF8.java")));
        assertThat(charset).isEqualTo(Charsets.UTF_8);
    }

    @Test
    public void normalizeEncodingASCII() throws IOException {
        copyFile("EncodingISO_8559_1.java", "EncodingISO_8559_1.java");
        Charset charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(Charsets.ISO_8859_1);

        fileService.convertToUTF8("./exportTest/EncodingISO_8559_1.java");
        charset = fileService.detectCharset(FileUtils.readFileToByteArray(new File("./exportTest/EncodingISO_8559_1.java")));
        assertThat(charset).isEqualTo(Charsets.UTF_8);
    }
}
