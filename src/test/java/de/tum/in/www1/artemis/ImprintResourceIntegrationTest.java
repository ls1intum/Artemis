package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Imprint;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import net.minidev.json.JSONObject;

class ImprintResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "ir"; // only lower case is supported

    @Test
    void testGetImprint_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/public/imprint?language=fr", HttpStatus.BAD_REQUEST, Imprint.class);
    }

    @Test
    void testGetImprint_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/public/imprint?language=de", HttpStatus.INTERNAL_SERVER_ERROR, Imprint.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetImprintForUpdate_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/admin/imprint-for-update?language=de", HttpStatus.INTERNAL_SERVER_ERROR, Imprint.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_cannotWriteFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(
                    () -> Files.writeString(argThat(path -> path.toString().contains("_de")), anyString(), eq(StandardOpenOption.CREATE), eq(StandardOpenOption.TRUNCATE_EXISTING)))
                    .thenThrow(new IOException());
            request.putWithResponseBody("/api/admin/imprint", new Imprint("text", Language.GERMAN), Imprint.class, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_directoryDoesntExist_createsDirectoryAndSavesFile() throws Exception {
        Imprint response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            response = request.putWithResponseBody("/api/admin/imprint", new Imprint("updatedText", Language.GERMAN), Imprint.class, HttpStatus.OK);
            mockedFiles.verify(() -> Files.createDirectories(any()));
            mockedFiles.verify(() -> Files.writeString(argThat(path -> path.toString().contains("_de")), anyString(), eq(StandardOpenOption.CREATE),
                    eq(StandardOpenOption.TRUNCATE_EXISTING)));
        }
        assertThat(response.getText()).isEqualTo("updatedText");
        assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);
    }

    // no mock user as anonymous access should be allowed
    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    void testGetImprintReturnsOtherLanguageIfFirstLanguageNotFound(Language language) throws Exception {
        Imprint response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            if ("de".equals(language.getShortName())) {
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(true);
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Imprint");
            }
            else {
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(false);
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Impressum");
            }

            response = request.get("/api/public/imprint?language=" + language.getShortName(), HttpStatus.OK, Imprint.class);
        }
        if ("de".equals(language.getShortName())) {
            assertThat(response.getLanguage()).isEqualTo(Language.ENGLISH);
            assertThat(response.getText()).isEqualTo("Imprint");
        }
        else {
            assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);
            assertThat(response.getText()).isEqualTo("Impressum");
        }
    }

    @Test
    void testGetImprint_noLanguageFound_badRequest() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(false);

            request.get("/api/public/imprint?language=de", HttpStatus.BAD_REQUEST, Imprint.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetImprintForUpdate_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/imprint-for-update?language=de", HttpStatus.FORBIDDEN, Imprint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetImprintForUpdate_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/admin/imprint-for-update?language=fr", HttpStatus.BAD_REQUEST, Imprint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetImprintForUpdateFileDoesntExist_emptyStringSuccess() throws Exception {
        Imprint response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);
            response = request.get("/api/admin/imprint-for-update?language=de", HttpStatus.OK, Imprint.class);
        }
        assertThat(response.getText()).isEqualTo("");
        assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    void testGetImprintReturnsCorrectFileContent(Language language) throws Exception {
        Imprint response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if (language == Language.ENGLISH) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Imprint");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Impressum");
            }
            response = request.get("/api/public/imprint?language=" + language.getShortName(), HttpStatus.OK, Imprint.class);
        }

        assertThat(response.getLanguage()).isEqualTo(language);
        if (language == Language.ENGLISH) {
            assertThat(response.getText()).isEqualTo("Imprint");
        }
        else {
            assertThat(response.getText()).isEqualTo("Impressum");
        }
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetImprintForUpdateReturnsCorrectFileContent(Language language) throws Exception {
        Imprint response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if ("de".equals(language.getShortName())) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Impressum");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Imprint");
            }
            response = request.get("/api/admin/imprint-for-update?language=" + language.getShortName(), HttpStatus.OK, Imprint.class);
        }

        assertThat(response.getLanguage()).isEqualTo(language);
        if ("de".equals(language.getShortName())) {
            assertThat(response.getText()).isEqualTo("Impressum");
        }
        else {
            assertThat(response.getText()).isEqualTo("Imprint");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateImprint_instructorAccessForbidden() throws Exception {
        request.put("/api/admin/imprint", new Imprint(Language.GERMAN), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdateImprint_writesFile_ReturnsUpdatedFileContent() throws Exception {
        Imprint response;
        Imprint requestBody = new Imprint(Language.GERMAN);
        requestBody.setText("Impressum");
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            response = request.putWithResponseBody("/api/admin/imprint", requestBody, Imprint.class, HttpStatus.OK);
            mockedFiles.verify(() -> Files.writeString(argThat(path -> path.toString().contains("_de")), anyString(), eq(StandardOpenOption.CREATE),
                    eq(StandardOpenOption.TRUNCATE_EXISTING)));

        }
        assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);
        assertThat(response.getText()).isEqualTo("Impressum");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdateImprint_unsupportedLanguageBadRequest() throws Exception {
        JSONObject body = new JSONObject();
        body.put("text", "test");
        body.put("language", "FRENCH");
        request.put("/api/admin/imprint", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdateImprint_blankTextBadRequest() throws Exception {
        Imprint requestBody = new Imprint(Language.GERMAN);
        requestBody.setText("           ");
        request.put("/api/admin/imprint", requestBody, HttpStatus.BAD_REQUEST);
    }

}
