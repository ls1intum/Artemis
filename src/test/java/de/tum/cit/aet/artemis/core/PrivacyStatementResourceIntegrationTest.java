package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.admin.dto.PrivacyStatementDTO;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PrivacyStatementResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "psr"; // only lower case is supported

    @BeforeEach
    void setUpAdmin() {
        // The admin endpoints resolve the authenticated login against the database, so the account the tests
        // authenticate as has to exist there with the admin authority rather than only in the mock security context.
        userUtilService.addAdmin(TEST_PREFIX);
    }

    @Test
    void testGetPrivacyStatement_invalidLanguageBadRequest() throws Exception {
        request.performMvcRequest(get("/api/core/public/privacy-statement")).andExpect(status().isBadRequest());
        request.performMvcRequest(get("/api/core/public/privacy-statement").param("language", "fr")).andExpect(status().isBadRequest());
        request.performMvcRequest(get("/api/core/public/privacy-statement").param("language", "EN")).andExpect(status().isBadRequest());
        request.performMvcRequest(get("/api/core/public/privacy-statement").param("language", "")).andExpect(status().isBadRequest());
    }

    @Test
    void testGetPrivacyStatement_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/core/public/privacy-statement?language=de", HttpStatus.INTERNAL_SERVER_ERROR, PrivacyStatementDTO.class);
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdate_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/admin/privacy-statement-for-update?language=de", HttpStatus.INTERNAL_SERVER_ERROR, PrivacyStatementDTO.class);

        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_cannotWriteFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class); MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFileUtils.when(() -> FileUtils.writeStringToFile(argThat(file -> file.toString().contains("_de")), anyString(), eq(StandardCharsets.UTF_8)))
                    .thenThrow(new IOException());
            request.putWithResponseBody("/api/admin/privacy-statement", new PrivacyStatementDTO("text", Language.GERMAN), PrivacyStatementDTO.class,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_directoryDoesntExist_createsDirectoryAndSavesFile() throws Exception {
        PrivacyStatementDTO response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class); MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class)) {
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            response = request.putWithResponseBody("/api/admin/privacy-statement", new PrivacyStatementDTO("updatedText", Language.GERMAN), PrivacyStatementDTO.class,
                    HttpStatus.OK);
            mockedFiles.verify(() -> Files.createDirectories(any()));
            mockedFileUtils.verify(() -> FileUtils.writeStringToFile(argThat(file -> file.toString().contains("_de")), anyString(), eq(StandardCharsets.UTF_8)));
        }
        assertThat(response.text()).isEqualTo("updatedText");
        assertThat(response.language()).isEqualTo(Language.GERMAN);

    }

    // no mock user as anonymous access should be allowed
    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    void testGetPrivacyStatementReturnsOtherLanguageIfFirstLanguageNotFound(Language language) throws Exception {
        Language fallback = language == Language.GERMAN ? Language.ENGLISH : Language.GERMAN;
        String filename = "privacy_statement_" + fallback.getShortName() + ".md";
        String text = fallback == Language.ENGLISH ? "Privacy Statement" : "Datenschutzerklärung";
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.endsWith(filename)))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.endsWith(filename)))).thenReturn(text);

            request.performMvcRequest(get("/api/core/public/privacy-statement").param("language", language.getShortName())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("PRIVACY_STATEMENT")).andExpect(jsonPath("$.language").value(fallback.name())).andExpect(jsonPath("$.text").value(text));
        }
    }

    @Test
    void testGetPrivacyStatement_noLanguageFound_badRequest() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(false);

            request.get("/api/core/public/privacy-statement?language=de", HttpStatus.BAD_REQUEST, PrivacyStatementDTO.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetPrivacyStatementForUpdate_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/privacy-statement-for-update?language=de", HttpStatus.FORBIDDEN, PrivacyStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdate_invalidLanguageBadRequest() throws Exception {
        request.performMvcRequest(get("/api/admin/privacy-statement-for-update")).andExpect(status().isBadRequest());
        request.restoreSecurityContext();
        request.performMvcRequest(get("/api/admin/privacy-statement-for-update").param("language", "fr")).andExpect(status().isBadRequest());
        request.restoreSecurityContext();
        request.performMvcRequest(get("/api/admin/privacy-statement-for-update").param("language", "EN")).andExpect(status().isBadRequest());
        request.restoreSecurityContext();
        request.performMvcRequest(get("/api/admin/privacy-statement-for-update").param("language", "")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateFileDoesntExist() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);

            request.performMvcRequest(get("/api/admin/privacy-statement-for-update").param("language", "de")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("PRIVACY_STATEMENT")).andExpect(jsonPath("$.language").value("GERMAN")).andExpect(jsonPath("$.text").doesNotHaveJsonPath());
        }
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    void testGetPrivacyStatementReturnsCorrectFileContent(Language language) throws Exception {
        String filename = "privacy_statement_" + language.getShortName() + ".md";
        String text = language == Language.ENGLISH ? "Privacy Statement" : "Datenschutzerklärung";
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.endsWith(filename)))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.endsWith(filename)))).thenReturn(text);

            request.performMvcRequest(get("/api/core/public/privacy-statement").param("language", language.getShortName())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("PRIVACY_STATEMENT")).andExpect(jsonPath("$.language").value(language.name())).andExpect(jsonPath("$.text").value(text));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testPrivacyStatementEditRoundTrip(Language language) throws Exception {
        String filename = "privacy_statement_" + language.getShortName() + ".md";
        String text = language == Language.ENGLISH ? "Privacy Statement" : "Datenschutzerklärung";
        String editedText = "## Aktualisierter Text für " + text;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class); MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.endsWith(filename)))).thenReturn(text);

            var response = request.performMvcRequest(get("/api/admin/privacy-statement-for-update").param("language", language.getShortName())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("PRIVACY_STATEMENT")).andExpect(jsonPath("$.language").value(language.name())).andExpect(jsonPath("$.text").value(text))
                    .andReturn().getResponse();

            request.restoreSecurityContext();
            var mapper = request.getObjectMapper();
            var payload = (ObjectNode) mapper.readTree(response.getContentAsByteArray());
            payload.put("text", editedText);
            request.performMvcRequest(put("/api/admin/privacy-statement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(payload)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("PRIVACY_STATEMENT")).andExpect(jsonPath("$.language").value(language.name()))
                    .andExpect(jsonPath("$.text").value(editedText));
            mockedFileUtils.verify(() -> FileUtils.writeStringToFile(argThat(file -> file.toPath().endsWith(filename)), eq(editedText), eq(StandardCharsets.UTF_8)));
            mockedFiles.verify(() -> Files.createDirectories(any()), never());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testUpdatePrivacyStatement_instructorAccessForbidden() throws Exception {
        request.put("/api/admin/privacy-statement", new PrivacyStatementDTO("", Language.GERMAN), HttpStatus.FORBIDDEN);
    }

    @Test
    void testAdminPrivacyStatementRequiresAuthentication() throws Exception {
        request.performMvcRequest(get("/api/admin/privacy-statement-for-update").param("language", "de")).andExpect(status().isUnauthorized());
        var body = new PrivacyStatementDTO("Markdown", Language.GERMAN);
        request.performMvcRequest(put("/api/admin/privacy-statement").contentType(MediaType.APPLICATION_JSON).content(JsonObjectMapper.get().writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("text", "test");
        requestBody.put("language", "FRENCH");
        request.put("/api/admin/privacy-statement", JsonObjectMapper.get().writeValueAsString(requestBody), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_blankTextBadRequest() throws Exception {
        PrivacyStatementDTO requestBody = new PrivacyStatementDTO("           ", Language.GERMAN);
        request.put("/api/admin/privacy-statement", requestBody, HttpStatus.BAD_REQUEST);
    }

}
