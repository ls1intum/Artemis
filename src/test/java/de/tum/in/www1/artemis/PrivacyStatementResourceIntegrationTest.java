package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.PrivacyStatementLanguage;
import net.minidev.json.JSONObject;

@ExtendWith(MockitoExtension.class)
class PrivacyStatementResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "psr"; // only lower case is supported

    @Test
    void testGetPrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/privacy-statement?language=fr", HttpStatus.BAD_REQUEST, PrivacyStatement.class);
    }

    @Test
    void testGetPrivacyStatement_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/privacy-statement?language=de", HttpStatus.INTERNAL_SERVER_ERROR, PrivacyStatement.class);
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdate_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/privacy-statement-for-update?language=de", HttpStatus.INTERNAL_SERVER_ERROR, PrivacyStatement.class);

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
            request.putWithResponseBody("/api/privacy-statement", new PrivacyStatement("text", PrivacyStatementLanguage.GERMAN), PrivacyStatement.class,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_directoryDoesntExist_createsDirectoryAndSavesFile() throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            response = request.putWithResponseBody("/api/privacy-statement", new PrivacyStatement("updatedText", PrivacyStatementLanguage.GERMAN), PrivacyStatement.class,
                    HttpStatus.OK);
            mockedFiles.verify(() -> Files.createDirectories(any()));
            mockedFiles.verify(() -> Files.writeString(argThat(path -> path.toString().contains("_de")), anyString(), eq(StandardOpenOption.CREATE),
                    eq(StandardOpenOption.TRUNCATE_EXISTING)));

        }
        assertThat(response.getText()).isEqualTo("updatedText");
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.GERMAN);

    }

    // no mock user as anonymous access should be allowed
    @ParameterizedTest
    @EnumSource(value = PrivacyStatementLanguage.class, names = { "GERMAN", "ENGLISH" })
    void testGetPrivacyStatementReturnsOtherLanguageIfFirstLanguageNotFound(PrivacyStatementLanguage language) throws Exception {
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

            response = request.get("/api/privacy-statement?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatement.class);
        }
        if ("de".equals(language.getShortName())) {
            assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.ENGLISH);
            assertThat(response.getText()).isEqualTo("Privacy Statement");
        }
        else {
            assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.GERMAN);
            assertThat(response.getText()).isEqualTo("Datenschutzerklärung");
        }
    }

    @Test
    void testGetPrivacyStatementReturnsEmptyStringIfNoLanguageFound() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(false);

            request.get("/api/privacy-statement?language=de", HttpStatus.BAD_REQUEST, PrivacyStatement.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPrivacyStatementForUpdate_instructorAccessForbidden() throws Exception {
        request.get("/api/privacy-statement-for-update?language=de", HttpStatus.FORBIDDEN, PrivacyStatement.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdate_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/privacy-statement-for-update?language=fr", HttpStatus.BAD_REQUEST, PrivacyStatement.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateFileDoesntExist_emptyStringSuccess() throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);
            response = request.get("/api/privacy-statement-for-update?language=de", HttpStatus.OK, PrivacyStatement.class);
        }
        assertThat(response.getText()).isEqualTo("");
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.GERMAN);
    }

    @ParameterizedTest
    @EnumSource(value = PrivacyStatementLanguage.class, names = { "GERMAN", "ENGLISH" })
    void testGetPrivacyStatementReturnsCorrectFileContent(PrivacyStatementLanguage language) throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if (language == PrivacyStatementLanguage.ENGLISH) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Privacy Statement");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            }
            response = request.get("/api/privacy-statement?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatement.class);
        }

        assertThat(response.getLanguage()).isEqualTo(language);
        if (language == PrivacyStatementLanguage.ENGLISH) {
            assertThat(response.getText()).isEqualTo("Privacy Statement");
        }
        else {
            assertThat(response.getText()).isEqualTo("Datenschutzerklärung");
        }
    }

    @ParameterizedTest
    @EnumSource(value = PrivacyStatementLanguage.class, names = { "GERMAN", "ENGLISH" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateReturnsCorrectFileContent(PrivacyStatementLanguage language) throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if ("de".equals(language.getShortName())) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Privacy Statement");
            }
            response = request.get("/api/privacy-statement-for-update?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatement.class);
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
        request.put("/api/privacy-statement", new PrivacyStatement(PrivacyStatementLanguage.GERMAN), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_writesFile_ReturnsUpdatedFileContent() throws Exception {
        PrivacyStatement response;
        PrivacyStatement requestBody = new PrivacyStatement(PrivacyStatementLanguage.GERMAN);
        requestBody.setText("Datenschutzerklärung");
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);

            response = request.putWithResponseBody("/api/privacy-statement", requestBody, PrivacyStatement.class, HttpStatus.OK);
            mockedFiles.verify(() -> Files.writeString(argThat(path -> path.toString().contains("_de")), anyString(), eq(StandardOpenOption.CREATE),
                    eq(StandardOpenOption.TRUNCATE_EXISTING)));
            // we explicitly check the method calls to ensure createDirectories is not called when the directory exists
            mockedFiles.verify(() -> Files.exists(any()));
            mockedFiles.verifyNoMoreInteractions();

        }
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.GERMAN);
        assertThat(response.getText()).isEqualTo("Datenschutzerklärung");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        JSONObject body = new JSONObject();
        body.put("text", "test");
        body.put("language", "FRENCH");
        request.put("/api/privacy-statement", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_blankTextBadRequest() throws Exception {
        PrivacyStatement requestBody = new PrivacyStatement(PrivacyStatementLanguage.GERMAN);
        requestBody.setText("           ");
        request.put("/api/privacy-statement", requestBody, HttpStatus.BAD_REQUEST);
    }

}
