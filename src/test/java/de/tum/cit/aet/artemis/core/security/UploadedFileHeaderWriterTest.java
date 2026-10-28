package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class UploadedFileHeaderWriterTest {

    private static final String APPLICATION_POLICY = "script-src 'self' 'unsafe-inline'";

    private final UploadedFileHeaderWriter writer = new UploadedFileHeaderWriter();

    private MockHttpServletResponse writeHeaders(String path, String contentType) {
        var response = new MockHttpServletResponse();
        response.setHeader("Content-Security-Policy", APPLICATION_POLICY);
        response.setContentType(contentType);
        writer.writeHeaders(new MockHttpServletRequest("GET", path), response);
        return response;
    }

    @ParameterizedTest
    @CsvSource({ "/api/core/files/users/1/profile-pictures/ProfilePicture_x.svg, image/svg+xml", "/api/core/files/markdown/Markdown_x.html, text/html",
            "/api/core/files/courses/1/conversations/2/x.svg, image/svg+xml", "/user/profile-pictures/ProfilePicture_x.svg, image/svg+xml",
            "/course/icons/CourseIcon_x.svg, image/svg+xml", "/markdown/Markdown_x.html, text/html;charset=UTF-8", "/api/core/files/users/1/profile-pictures/x.png, image/png" })
    void sandboxesUploadedFiles(String path, String contentType) {
        assertThat(writeHeaders(path, contentType).getHeader("Content-Security-Policy")).isEqualTo(UploadedFileHeaderWriter.UPLOADED_FILE_POLICY_DIRECTIVES);
    }

    @ParameterizedTest
    @ValueSource(strings = { "application/pdf", "application/pdf;charset=UTF-8" })
    void keepsApplicationPolicyForPdf(String contentType) {
        assertThat(writeHeaders("/api/core/files/attachments/lectures/1/slides.pdf", contentType).getHeader("Content-Security-Policy")).isEqualTo(APPLICATION_POLICY);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "/index.html", "/courses/1", "/api/core/account", "/api/core/filesystem" })
    void keepsApplicationPolicyOutsideUploadedFiles(String path) {
        assertThat(writeHeaders(path, "text/html").getHeader("Content-Security-Policy")).isEqualTo(APPLICATION_POLICY);
    }
}
