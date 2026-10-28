package de.tum.cit.aet.artemis.core.security;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Locks down responses that carry files users uploaded.
 * <p>
 * Uploaded files are served under the Artemis origin with a content type derived from their extension, and several upload paths accept SVG or HTML. Opened directly, such a
 * file is a document of the Artemis origin, and the application-wide policy allows inline scripts, so a script in it would act with the session of whoever opens the link.
 * This writer replaces that policy for uploaded files with one that renders them in a sandbox without any script, which also covers files that were stored before uploads
 * were checked. Embedding a file with {@code <img>} or fetching it from the client is unaffected, since neither applies the policy of the fetched response.
 * <p>
 * PDF files are exempt: browsers refuse to show a PDF in a sandboxed document, and the PDF viewer does not run the file's scripts in the Artemis origin.
 * <p>
 * The writer runs when the response is committed, after the content type has been set, so it is registered after the application-wide policy and overrides it.
 */
public class UploadedFileHeaderWriter implements HeaderWriter {

    static final String UPLOADED_FILE_POLICY_DIRECTIVES = "default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'unsafe-inline'; sandbox";

    /**
     * The REST endpoints that serve stored files, and the static resource handlers of {@code StaticResourcesConfiguration} that still serve the upload directories directly.
     */
    private static final List<PathPattern> UPLOADED_FILE_PATHS = List
            .of("/api/core/files/**", "/course/icons/**", "/user/profile-pictures/**", "/drag-and-drop/**", "/markdown/**", "/exam-user/**").stream()
            .map(PathPatternParser.defaultInstance::parse).toList();

    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (servesUploadedFile(request) && !isPdf(response.getContentType())) {
            response.setHeader(CONTENT_SECURITY_POLICY_HEADER, UPLOADED_FILE_POLICY_DIRECTIVES);
        }
    }

    private static boolean servesUploadedFile(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        return UPLOADED_FILE_PATHS.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static boolean isPdf(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_PDF.isCompatibleWith(MediaType.parseMediaType(contentType));
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }
}
