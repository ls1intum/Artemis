package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile(PROFILE_CORE)
public class VideoStorageService {

    private static final Logger log = LoggerFactory.getLogger(VideoStorageService.class);

    // Configuration defaults
    private static final int LARGE_FILE_THRESHOLD = 500 * 1024 * 1024; // 500MB

    private static final int STANDARD_BUFFER_SIZE = 8 * 1024;          // 8KB

    private static final int LARGE_BUFFER_SIZE = 64 * 1024;            // 64KB

    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;         // 1MB

    // (Currently unused but kept for parity/timeouts and future refactors)
    @SuppressWarnings("unused")
    private final RestTemplate restTemplate;

    @Value("${artemis.video-storage.upload-url}")
    private String videoStorageUrl;

    @Value("${artemis.video-storage.secret-token}")
    private String secretToken;

    @Value("${artemis.video-storage.upload-timeout:1800}")
    private int uploadTimeoutSeconds;

    @Value("${artemis.video-storage.max-file-size:5368709120}")
    private long maxFileSize;

    @Value("${artemis.video-storage.buffer-size:8192}")
    private int bufferSizeConfig;

    @Value("${artemis.video-storage.chunk-size:1048576}")
    private int chunkSizeConfig;

    @Autowired
    public VideoStorageService(RestTemplateBuilder builder) {
        // Build a RestTemplate with timeouts; not used in this class yet but kept intentionally
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(30)).setReadTimeout(Duration.ofSeconds(1800)) // overwritten below when used
                .build();
    }

    public String uploadVideo(MultipartFile file) throws IOException {
        if (file == null) {
            throw new IOException("No file provided");
        }

        final long size = file.getSize();
        final String originalName = file.getOriginalFilename();

        log.info("Uploading video file: {} (size: {} bytes)", originalName, size);
        log.debug("Target video storage URL: {}", videoStorageUrl);

        // Basic validations
        if (size <= 0) {
            throw new IOException("Cannot upload empty file");
        }
        if (size > maxFileSize) {
            throw new IOException("File exceeds configured maximum size: " + maxFileSize + " bytes");
        }

        // Choose upload strategy based on file size
        final String response;
        if (size > LARGE_FILE_THRESHOLD) {
            log.info("Large file ({} MB). Using chunked streaming upload.", size / (1024 * 1024));
            response = uploadLargeVideoStreaming(file);
        }
        else {
            log.info("Standard file size. Using optimized streaming upload.");
            response = uploadVideoStreaming(file);
        }

        // Parse response and generate playlist URL
        return parseResponseAndGeneratePlaylistUrl(response);
    }

    private String uploadVideoStreaming(MultipartFile file) throws IOException {
        URL url = new URL(videoStorageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Compute boundary ONCE and reuse
        final String boundary = generateBoundary();
        final int chunkSize = chunkSizeConfig > 0 ? chunkSizeConfig : DEFAULT_CHUNK_SIZE;
        final int bufferSize = bufferSizeConfig > 0 ? bufferSizeConfig : STANDARD_BUFFER_SIZE;

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            // Prefer standard Bearer format; server accepts both 'token' and 'Bearer token'
            connection.setRequestProperty("Authorization", "Bearer " + secretToken);
            connection.setConnectTimeout(30_000); // 30s
            connection.setReadTimeout(uploadTimeoutSeconds * 1000);

            // Stream to avoid buffering entire file
            connection.setChunkedStreamingMode(chunkSize);

            try (OutputStream os = connection.getOutputStream()) {
                writeMultipartFormData(os, file, bufferSize, boundary);
            }

            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                try (InputStream is = connection.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            // Read error body for diagnostics
            String errorBody = readErrorBody(connection);
            throw new IOException("Upload failed with response code: " + code + (errorBody.isEmpty() ? "" : " body: " + errorBody));

        }
        finally {
            connection.disconnect();
        }
    }

    private String uploadLargeVideoStreaming(MultipartFile file) throws IOException {
        URL url = new URL(videoStorageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        final String boundary = generateBoundary();
        final int chunkSize = chunkSizeConfig > 0 ? chunkSizeConfig : DEFAULT_CHUNK_SIZE;
        final int bufferSize = Math.max(LARGE_BUFFER_SIZE, bufferSizeConfig > 0 ? bufferSizeConfig : LARGE_BUFFER_SIZE);

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Authorization", "Bearer " + secretToken);
            connection.setConnectTimeout(30_000); // 30s
            connection.setReadTimeout(uploadTimeoutSeconds * 1000);

            // Larger chunked streaming for big files
            connection.setChunkedStreamingMode(chunkSize);

            try (OutputStream os = connection.getOutputStream()) {
                writeMultipartFormData(os, file, bufferSize, boundary);
            }

            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                try (InputStream is = connection.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }

            String errorBody = readErrorBody(connection);
            throw new IOException("Upload failed with response code: " + code + (errorBody.isEmpty() ? "" : " body: " + errorBody));

        }
        finally {
            connection.disconnect();
        }
    }

    private void writeMultipartFormData(OutputStream outputStream, MultipartFile file, int bufferSize, String boundary) throws IOException {
        byte[] buffer = new byte[Math.max(bufferSize, STANDARD_BUFFER_SIZE)];

        // Safe filename (no URL-encoding in multipart headers). Escape quotes if present.
        String rawName = file.getOriginalFilename();
        if (rawName == null || rawName.isBlank()) {
            rawName = "upload.mp4";
        }
        String safeFilename = rawName.replace("\"", "\\\"");

        // Default part content-type if missing
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "video/mp4";
        }

        // ---- Part headers
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(safeFilename).append("\"\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n\r\n");
        outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));

        // ---- Binary body
        try (InputStream is = file.getInputStream()) {
            int read;
            while ((read = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }

        // ---- CRLF + final boundary
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        String tail = "--" + boundary + "--\r\n";
        outputStream.write(tail.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private String generateBoundary() {
        // ASCII-only boundary
        return "----ArtemisBoundary" + System.currentTimeMillis();
    }

    private String parseResponseAndGeneratePlaylistUrl(String response) throws IOException {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response);

            com.fasterxml.jackson.databind.JsonNode videoIdNode = root.get("videoId");
            if (videoIdNode == null || videoIdNode.isNull()) {
                throw new IOException("Invalid response: 'videoId' not found");
            }
            String videoId = videoIdNode.asText();

            String status = root.hasNonNull("status") ? root.get("status").asText() : "unknown";

            String baseUrl = extractBaseUrl(videoStorageUrl);
            String playlistUrl = baseUrl + "/api/videos/" + videoId + "/playlist.m3u8";

            log.info("Video upload successful. VideoId: {}, Status: {}, Playlist URL: {}", videoId, status, playlistUrl);
            return playlistUrl;

        }
        catch (Exception e) {
            log.error("Failed to parse video storage response: {}", response, e);
            throw new IOException("Failed to parse video storage response: " + e.getMessage());
        }
    }

    private String extractBaseUrl(String uploadUrl) {
        try {
            URL url = new URL(uploadUrl);
            int port = url.getPort();
            String hostPort = (port == -1) ? url.getHost() : (url.getHost() + ":" + port);
            return url.getProtocol() + "://" + hostPort;
        }
        catch (Exception e) {
            log.warn("Failed to parse upload URL '{}', falling back to http://localhost:8000", uploadUrl, e);
            return "http://localhost:8000";
        }
    }

    private static String readErrorBody(HttpURLConnection connection) {
        try (InputStream es = connection.getErrorStream()) {
            if (es == null)
                return "";
            return new String(es.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception ignored) {
            return "";
        }
    }
}
