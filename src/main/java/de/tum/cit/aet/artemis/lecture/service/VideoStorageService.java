package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile(PROFILE_CORE)
public class VideoStorageService {

    private static final Logger log = LoggerFactory.getLogger(VideoStorageService.class);

    private final RestTemplate restTemplate;

    @Value("${artemis.video-storage.upload-url}")
    private String videoStorageUrl;

    @Value("${artemis.video-storage.secret-token}")
    private String secretToken;

    @Autowired
    public VideoStorageService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public String uploadVideo(MultipartFile file) throws IOException {
        log.info("Uploading video file: {}", file.getOriginalFilename());
        log.debug("Target video storage URL: {}", videoStorageUrl);

        // Save to temp file (repeatable)
        File tempFile = File.createTempFile("upload-", "-" + file.getOriginalFilename());
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            out.write(file.getBytes());
        }

        FileSystemResource fileResource = new FileSystemResource(tempFile);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", secretToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(videoStorageUrl, requestEntity, String.class);
            log.info("Upload successful. Response: {}", response.getBody());
            return response.getBody();
        }
        catch (Exception e) {
            log.error("Video upload failed to {}: {}", videoStorageUrl, e.getMessage(), e);
            throw e;
        }
        finally {
            boolean deleted = tempFile.delete();
            if (!deleted) {
                log.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }

}
