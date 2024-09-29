package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.dto.PrivacyStatementDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class PrivacyStatementResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "psr"; // only lower case is supported

    @Test
    void testGetPrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/public/privacy-statement?language=fr", HttpStatus.BAD_REQUEST, PrivacyStatementDTO.class);
    }

    @Test
    void testGetPrivacyStatement_cannotReadFileInternalServerError() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(true);
            mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenThrow(new IOException());
            request.get("/api/public/privacy-statement?language=de", HttpStatus.INTERNAL_SERVER_ERROR, PrivacyStatementDTO.class);
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
        PrivacyStatementDTO response;
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

            response = request.get("/api/public/privacy-statement?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatementDTO.class);
        }
        if ("de".equals(language.getShortName())) {
            assertThat(response.language()).isEqualTo(Language.ENGLISH);
            assertThat(response.text()).isEqualTo("Privacy Statement");
        }
        else {
            assertThat(response.language()).isEqualTo(Language.GERMAN);
            assertThat(response.text()).isEqualTo("Datenschutzerklärung");
        }
    }

    @Test
    void testGetPrivacyStatement_noLanguageFound_badRequest() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_de")))).thenReturn(false);
            mockedFiles.when(() -> Files.exists(argThat(path -> path.toString().contains("_en")))).thenReturn(false);

            request.get("/api/public/privacy-statement?language=de", HttpStatus.BAD_REQUEST, PrivacyStatementDTO.class);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPrivacyStatementForUpdate_instructorAccessForbidden() throws Exception {
        request.get("/api/admin/privacy-statement-for-update?language=de", HttpStatus.FORBIDDEN, PrivacyStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdate_unsupportedLanguageBadRequest() throws Exception {
        request.get("/api/admin/privacy-statement-for-update?language=fr", HttpStatus.BAD_REQUEST, PrivacyStatementDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateFileDoesntExist() throws Exception {
        PrivacyStatementDTO response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(false);
            response = request.get("/api/admin/privacy-statement-for-update?language=de", HttpStatus.OK, PrivacyStatementDTO.class);
        }
        assertThat(response.text()).isNull();
        assertThat(response.language()).isEqualTo(Language.GERMAN);
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    void testGetPrivacyStatementReturnsCorrectFileContent(Language language) throws Exception {
        PrivacyStatementDTO response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if (language == Language.ENGLISH) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Privacy Statement");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            }
            response = request.get("/api/public/privacy-statement?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatementDTO.class);
        }

        assertThat(response.language()).isEqualTo(language);
        if (language == Language.ENGLISH) {
            assertThat(response.text()).isEqualTo("Privacy Statement");
        }
        else {
            assertThat(response.text()).isEqualTo("Datenschutzerklärung");
        }
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = { "GERMAN", "ENGLISH" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetPrivacyStatementForUpdateReturnsCorrectFileContent(Language language) throws Exception {
        PrivacyStatementDTO response;
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            if ("de".equals(language.getShortName())) {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_de")))).thenReturn("Datenschutzerklärung");
            }
            else {
                mockedFiles.when(() -> Files.readString(argThat(path -> path.toString().contains("_en")))).thenReturn("Privacy Statement");
            }
            response = request.get("/api/admin/privacy-statement-for-update?language=" + language.getShortName(), HttpStatus.OK, PrivacyStatementDTO.class);
        }

        assertThat(response.language()).isEqualTo(language);
        if ("de".equals(language.getShortName())) {
            assertThat(response.text()).isEqualTo("Datenschutzerklärung");
        }
        else {
            assertThat(response.text()).isEqualTo("Privacy Statement");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdatePrivacyStatement_instructorAccessForbidden() throws Exception {
        request.put("/api/admin/privacy-statement", new PrivacyStatementDTO("", Language.GERMAN), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_writesFile_ReturnsUpdatedFileContent() throws Exception {
        PrivacyStatementDTO requestBody = new PrivacyStatementDTO("Datenschutzerklärung", Language.GERMAN);
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class); MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class)) {
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);

            PrivacyStatementDTO response = request.putWithResponseBody("/api/admin/privacy-statement", requestBody, PrivacyStatementDTO.class, HttpStatus.OK);
            mockedFileUtils.verify(() -> FileUtils.writeStringToFile(argThat(file -> file.toString().contains("_de")), anyString(), eq(StandardCharsets.UTF_8)));
            // we explicitly check the method calls to ensure createDirectories is not called when the directory exists
            mockedFiles.verify(() -> Files.exists(any()));
            mockedFiles.verifyNoMoreInteractions();
            assertThat(response.language()).isEqualTo(Language.GERMAN);
            assertThat(response.text()).isEqualTo("Datenschutzerklärung");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_unsupportedLanguageBadRequest() throws Exception {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("text", "test");
        requestBody.put("language", "FRENCH");
        request.put("/api/admin/privacy-statement", new ObjectMapper().writeValueAsString(requestBody), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testUpdatePrivacyStatement_blankTextBadRequest() throws Exception {
        PrivacyStatementDTO requestBody = new PrivacyStatementDTO("           ", Language.GERMAN);
        request.put("/api/admin/privacy-statement", requestBody, HttpStatus.BAD_REQUEST);
    }

}
