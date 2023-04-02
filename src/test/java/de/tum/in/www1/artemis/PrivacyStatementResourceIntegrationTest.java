package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mockStatic;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.PrivacyStatement;
import de.tum.in.www1.artemis.domain.PrivacyStatementLanguage;

@ExtendWith(MockitoExtension.class)
class PrivacyStatementResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "psr"; // only lower case is supported

    private static final String BASE_PATH = "src/main/resources/public/content/";

    private static final String PRIVACY_STATEMENT_FILE_NAME = "privacy_statement_";

    private static final String PRIVACY_STATEMENT_FILE_EXTENSION = ".md";

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
        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
        Path privacyStatementEnglish = getPrivacyStatementPath(PrivacyStatementLanguage.ENGLISH);
        mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(true);
        mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("test");
        var response = request.get("/api/privacy-statement?language=de", HttpStatus.OK, PrivacyStatement.class);
        mockedFiles.close();
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
        var response = request.get("/api/privacy-statement-for-update?language=de", HttpStatus.OK, PrivacyStatement.class);
        assertThat(response.getText()).isEqualTo("");
        assertThat(response.getLanguage()).isEqualTo(PrivacyStatementLanguage.GERMAN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdatePrivacyStatement_instructorAccessForbidden() throws Exception {
        request.put("/api/privacy-statement", new PrivacyStatement(PrivacyStatementLanguage.GERMAN), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        // TODO fix this
        String body = "{\"text\" : \"test\", \"language\" : \"fr\"}";
        request.put("/api/privacy-statement", body, HttpStatus.BAD_REQUEST);
    }

    private Path getPrivacyStatementPath(PrivacyStatementLanguage language) {
        return Path.of(BASE_PATH, PRIVACY_STATEMENT_FILE_NAME + language.getShortName() + PRIVACY_STATEMENT_FILE_EXTENSION);
    }
}
