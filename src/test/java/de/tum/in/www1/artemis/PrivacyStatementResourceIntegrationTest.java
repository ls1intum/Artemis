package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.enumeration.Language;

class PrivacyStatementResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "psr"; // only lower case is supported

    @Test
    void testGetPrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/public/privacy-statement?language=fr", HttpStatus.BAD_REQUEST, PrivacyStatement.class);
    }

    @Test
    void testGetPrivacyStatement_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/public/privacy-statement?language=de", HttpStatus.INTERNAL_SERVER_ERROR, PrivacyStatement.class);
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdate_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/admin/privacy-statement-for-update?language=de", HttpStatus.INTERNAL_SERVER_ERROR, PrivacyStatement.class);

        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_cannotWriteFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class); MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFileUtils.when(() -> FileUtils.writeStringToFile(argThat(file -> file.toString().contains("_de")), anyString(), eq(StandardCharsets.UTF_8)))
                    .thenThrow(new IOException());
            request.putWithResponseBody("/api/admin/privacy-statement", new PrivacyStatement("text", Language.GERMAN), PrivacyStatement.class, HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_directoryDoesntExist_createsDirectoryAndSavesFile() throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class); MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class)) {
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            response = request.putWithResponseBody("/api/admin/privacy-statement", new PrivacyStatement("updatedText", Language.GERMAN), PrivacyStatement.class, HttpStatus.OK);
            mockedFiles.verify(() -> Files.createDirectories(any()));
            mockedFileUtils.verify(() -> FileUtils.writeStringToFile(argThat(file -> file.toString().contains("_de")), anyString(), eq(StandardCharsets.UTF_8)));
        }
        assertThat(response.getText()).isEqualTo("updatedText");
        assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);

    }

    // no mock user as anonymous access should be allowed
    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    void testGetPrivacyStatementReturnsOtherLanguageIfFirstLanguageNotFound(Language language) throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            if ("de".equals(language.getShortName())) {
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(true);
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Privacy Statement");
            }
            else {
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
                mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(false);
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            }

            response = request.get("/api/public/privacy-statement?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatement.class);
        }
        if ("de".equals(language.getShortName())) {
            assertThat(response.getLanguage()).isEqualTo(Language.ENGLISH);
            assertThat(response.getText()).isEqualTo("Privacy Statement");
        }
        else {
            assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);
            assertThat(response.getText()).isEqualTo("Datenschutzerklärung");
        }
    }

    @Test
    void testGetPrivacyStatement_noLanguageFound_badRequest() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(false);

            request.get("/api/public/privacy-statement?language=de", HttpStatus.BAD_REQUEST, PrivacyStatement.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPrivacyStatementForUpdate_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/privacy-statement-for-update?language=de", HttpStatus.FORBIDDEN, PrivacyStatement.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdate_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/admin/privacy-statement-for-update?language=fr", HttpStatus.BAD_REQUEST, PrivacyStatement.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateFileDoesntExist_emptyStringSuccess() throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);
            response = request.get("/api/admin/privacy-statement-for-update?language=de", HttpStatus.OK, PrivacyStatement.class);
        }
        assertThat(response.getText()).isEqualTo("");
        assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    void testGetPrivacyStatementReturnsCorrectFileContent(Language language) throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if (language == Language.ENGLISH) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Privacy Statement");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            }
            response = request.get("/api/public/privacy-statement?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatement.class);
        }

        assertThat(response.getLanguage()).isEqualTo(language);
        if (language == Language.ENGLISH) {
            assertThat(response.getText()).isEqualTo("Privacy Statement");
        }
        else {
            assertThat(response.getText()).isEqualTo("Datenschutzerklärung");
        }
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateReturnsCorrectFileContent(Language language) throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if ("de".equals(language.getShortName())) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Privacy Statement");
            }
            response = request.get("/api/admin/privacy-statement-for-update?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatement.class);
        }

        assertThat(response.getLanguage()).isEqualTo(language);
        if ("de".equals(language.getShortName())) {
            assertThat(response.getText()).isEqualTo("Datenschutzerklärung");
        }
        else {
            assertThat(response.getText()).isEqualTo("Privacy Statement");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdatePrivacyStatement_instructorAccessForbidden() throws Exception {
        request.put("/api/admin/privacy-statement", new PrivacyStatement(Language.GERMAN), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_writesFile_ReturnsUpdatedFileContent() throws Exception {
        PrivacyStatement response;
        PrivacyStatement requestBody = new PrivacyStatement(Language.GERMAN);
        requestBody.setText("Datenschutzerklärung");
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class); MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);

            response = request.putWithResponseBody("/api/admin/privacy-statement", requestBody, PrivacyStatement.class, HttpStatus.OK);
            mockedFileUtils.verify(() -> FileUtils.writeStringToFile(argThat(file -> file.toString().contains("_de")), anyString(), eq(StandardCharsets.UTF_8)));
            // we explicitly check the method calls to ensure createDirectories is not called when the directory exists
            mockedFiles.verify(() -> Files.exists(any()));
            mockedFiles.verifyNoMoreInteractions();
        }
        assertThat(response.getLanguage()).isEqualTo(Language.GERMAN);
        assertThat(response.getText()).isEqualTo("Datenschutzerklärung");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("text", "test");
        body.addProperty("language", "FRENCH");
        request.put("/api/admin/privacy-statement", body.toString(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_blankTextBadRequest() throws Exception {
        PrivacyStatement requestBody = new PrivacyStatement(Language.GERMAN);
        requestBody.setText("           ");
        request.put("/api/admin/privacy-statement", requestBody, HttpStatus.BAD_REQUEST);
    }

}
