package de.tum.cit.aet.artemis.core.service.file;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FileUtil;

/**
 * Service for preparing downloadable file payloads, including support for HTTP range requests on PDF files.
 */
@Lazy
@Service
@Profile(PROFILE_CORE)
public class FileDownloadService {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadService.class);

    private final FileService fileService;

    public FileDownloadService(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Payload container for file download responses.
     *
     * @param status       HTTP status of the response
     * @param content      binary response body
     * @param headers      headers to be set on the response
     * @param mediaType    resolved media type for the file
     * @param contentRange optional content-range header value for partial responses
     */
    public record FileDownloadPayload(HttpStatus status, byte[] content, HttpHeaders headers, MediaType mediaType, Optional<String> contentRange) {
    }

    /**
     * Prepares a download payload for an attachment.
     * <p>
     * For non-PDF files, the whole file is returned. For PDF files, a single range request is supported and validated
     * against {@code maxPdfRangeBytes}. Invalid ranges result in a {@code 416 Requested Range Not Satisfiable} payload.
     *
     * @param path             base directory of the file
     * @param filename         filename to load from {@code path}
     * @param replaceFilename  optional filename used in response headers
     * @param ranges           requested ranges from the HTTP header
     * @param maxPdfRangeBytes maximum allowed range length for PDF responses
     * @return a payload describing status, headers, media type and content
     */
    public FileDownloadPayload prepareAttachmentDownload(Path path, String filename, Optional<String> replaceFilename, List<HttpRange> ranges, int maxPdfRangeBytes) {
        Path actualPath = path.resolve(filename);
        if (!filename.toLowerCase().endsWith(".pdf")) {
            return buildFullFilePayload(actualPath, filename, replaceFilename);
        }
        if (!Files.exists(actualPath)) {
            throw new EntityNotFoundException("File", filename);
        }

        try {
            long fileSize = Files.size(actualPath);
            HttpHeaders headers = createFileHeaders(filename, replaceFilename);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            MediaType mediaType = getMediaTypeFromFilename(filename);

            if (ranges == null || ranges.isEmpty()) {
                return buildFullFilePayload(actualPath, filename, headers, mediaType);
            }

            HttpRange range = ranges.getFirst();
            long start;
            long end;
            try {
                start = range.getRangeStart(fileSize);
                end = range.getRangeEnd(fileSize);
            }
            catch (IllegalArgumentException ex) {
                return buildRangeNotSatisfiablePayload(headers, mediaType, fileSize);
            }

            long rangeLength = end - start + 1;
            if (start < 0 || end < start || end >= fileSize || rangeLength > maxPdfRangeBytes) {
                return buildRangeNotSatisfiablePayload(headers, mediaType, fileSize);
            }

            byte[] rangeBytes = readFileRangeBytes(actualPath, start, rangeLength);
            if (rangeBytes.length == 0) {
                return buildRangeNotSatisfiablePayload(headers, mediaType, fileSize);
            }
            long actualEnd = start + rangeBytes.length - 1;
            String contentRange = "bytes " + start + "-" + actualEnd + "/" + fileSize;

            return new FileDownloadPayload(HttpStatus.PARTIAL_CONTENT, rangeBytes, headers, mediaType, Optional.of(contentRange));
        }
        catch (IOException ex) {
            log.error("Failed to serve PDF range request", ex);
            throw new InternalServerErrorException("Failed to serve PDF range request");
        }
        catch (RuntimeException ex) {
            log.warn("Range request failed, falling back to full response", ex);
            return buildFullFilePayload(actualPath, filename, replaceFilename);
        }
    }

    /**
     * Creates content disposition headers for a file response.
     *
     * @param filename        original filename
     * @param replaceFilename optional replacement filename used for content disposition
     * @return response headers containing the sanitized filename
     */
    public HttpHeaders createFileHeaders(String filename, Optional<String> replaceFilename) {
        HttpHeaders headers = new HttpHeaders();

        String contentType = filename.toLowerCase().endsWith("htm") || filename.toLowerCase().endsWith("html") || filename.toLowerCase().endsWith("svg")
                || filename.toLowerCase().endsWith("svgz") ? "attachment" : "inline";
        String headerFilename = FileUtil.sanitizeFilename(replaceFilename.orElse(filename));
        headers.setContentDisposition(ContentDisposition.builder(contentType).filename(headerFilename).build());
        headers.set("Filename", headerFilename);
        return headers;
    }

    /**
     * Resolves the media type for the given filename.
     *
     * @param filename filename used for media type detection
     * @return detected media type or {@link MediaType#APPLICATION_OCTET_STREAM} as fallback
     */
    public MediaType getMediaTypeFromFilename(String filename) {
        Optional<MediaType> fromSpring = MediaTypeFactory.getMediaType(filename);
        if (fromSpring.isPresent()) {
            return fromSpring.get();
        }

        String mimeType = URLConnection.guessContentTypeFromName(filename);
        if (mimeType != null && !mimeType.isBlank()) {
            return MediaType.parseMediaType(mimeType);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private FileDownloadPayload buildFullFilePayload(Path actualPath, String filename, Optional<String> replaceFilename) {
        return buildFullFilePayload(actualPath, filename, createFileHeaders(filename, replaceFilename), getMediaTypeFromFilename(filename));
    }

    private FileDownloadPayload buildFullFilePayload(Path actualPath, String filename, HttpHeaders headers, MediaType mediaType) {
        try {
            byte[] file = fileService.getFileForPath(actualPath);
            if (file == null) {
                throw new EntityNotFoundException("File", filename);
            }
            return new FileDownloadPayload(HttpStatus.OK, file, headers, mediaType, Optional.empty());
        }
        catch (IOException ex) {
            log.error("Failed to download file: {} on path: {}", filename, actualPath.getParent(), ex);
            throw new InternalServerErrorException("Failed to download file");
        }
    }

    private FileDownloadPayload buildRangeNotSatisfiablePayload(HttpHeaders headers, MediaType mediaType, long fileSize) {
        return new FileDownloadPayload(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, new byte[0], headers, mediaType, Optional.of("bytes */" + fileSize));
    }

    private byte[] readFileRangeBytes(Path actualPath, long start, long rangeLength) throws IOException {
        if (rangeLength <= 0 || rangeLength > Integer.MAX_VALUE) {
            return new byte[0];
        }

        byte[] buffer = new byte[(int) rangeLength];
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(actualPath.toFile(), "r")) {
            randomAccessFile.seek(start);
            int bytesRead = randomAccessFile.read(buffer);
            if (bytesRead <= 0) {
                return new byte[0];
            }
            return bytesRead == buffer.length ? buffer : Arrays.copyOf(buffer, bytesRead);
        }
    }
}
