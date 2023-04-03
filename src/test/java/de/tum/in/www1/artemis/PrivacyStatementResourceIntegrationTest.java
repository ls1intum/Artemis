package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    void testGetPrivacyStatement_anonymousAccessAllowed() throws Exception {
        request.get("/api/privacy-statement?language=de", HttpStatus.OK, PrivacyStatement.class);
    }

    @Test
    void testGetPrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/privacy-statement?language=fr", HttpStatus.BAD_REQUEST, PrivacyStatement.class);
    }

    @Test
    void testGetPrivacyStatementReturnsOtherLanguageIfFirstLanguageNotFound() throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(true);
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("test");
            response = request.get("/api/privacy-statement?language=de", HttpStatus.OK, PrivacyStatement.class);
        }
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.ENGLISH);
        assertThat(response.getText()).isEqualTo("test");
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
    void testGetPrivacyStatementForUpdate_FileDoesntExist_emptyStringSuccess() throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);
            response = request.get("/api/privacy-statement-for-update?language=de", HttpStatus.OK, PrivacyStatement.class);
        }
        assertThat(response.getText()).isEqualTo("");
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.GERMAN);
    }

    @Test
    void testGetPrivacyStatementReturnsCorrectFileContent() throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            response = request.get("/api/privacy-statement?language=de", HttpStatus.OK, PrivacyStatement.class);
        }
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.GERMAN);
        assertThat(response.getText()).isEqualTo("Datenschutzerklärung");
    }

    @ParameterizedTest
    @ValueSource(strings = { "de", "en" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateReturnsCorrectFileContent(String langShortName) throws Exception {
        PrivacyStatement response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if ("de".equals(langShortName)) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_" + langShortName)))).thenReturn("Datenschutzerklärung");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_" + langShortName)))).thenReturn("Privacy Statement");
            }
            response = request.get("/api/privacy-statement-for-update?language=" + langShortName, HttpStatus.OK, PrivacyStatement.class);
        }
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.fromLanguageShortName(langShortName));
        if ("de".equals(langShortName)) {
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
    void testUpdatePrivacyStatement_writesFile_ReturnsUpdatedFileContent() throws Exception {
        PrivacyStatement response;
        PrivacyStatement requestBody = new PrivacyStatement(PrivacyStatementLanguage.GERMAN);
        requestBody.setText("Datenschutzerklärung");
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            response = request.putWithResponseBody("/api/privacy-statement", requestBody, PrivacyStatement.class, HttpStatus.OK);
            mockedFiles.verify(() -> Files.writeString(argThat(path -> path.toString().contains("_de")), any(), eq(StandardOpenOption.WRITE), eq(StandardOpenOption.CREATE)));

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

}
