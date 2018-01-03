package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.config.WebConfigurer;
import io.github.jhipster.config.JHipsterProperties;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
public class FileUploadResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadResource.class);

    private final Environment env;
    private final JHipsterProperties jHipsterProperties;

    public FileUploadResource(Environment env, JHipsterProperties jHipsterProperties) {
        this.env = env;
        this.jHipsterProperties = jHipsterProperties;
    }

    /**
     * POST  /fileUpload : Upload a new file.
     *
     * @param file The file to save
     * @return The path of the file
     */
    @PostMapping("/fileUpload")
    @Timed
    public ResponseEntity<String> saveUserDataAndFile(@RequestParam(value = "file") MultipartFile file) throws URISyntaxException {
        log.debug("REST request to upload file : {}", file.getOriginalFilename());

        WebConfigurer webConfigurer = new WebConfigurer(env, jHipsterProperties);
        String rootDirectory = webConfigurer.getLocationForStaticAssets() + "/uploads/temp/";
        String filename = "Temp_" + ZonedDateTime.now().toString().substring(0, 23) + "_" + Math.random();
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        String path = fileExtension.equals("") ? rootDirectory + filename : rootDirectory + filename + "." + fileExtension;
        String responsePath = path.replace(webConfigurer.getLocationForStaticAssets(), "");

        File newFile = new File(path);

        try {
            if (!newFile.getParentFile().exists()) {
                newFile.getParentFile().mkdirs();
            }
            if (!newFile.exists()) {
                newFile.createNewFile();
            }

            Files.copy(file.getInputStream(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            String responseBody = "{\"path\":\"" + responsePath + "\"}";
            return ResponseEntity.created(new URI(responsePath)).body(responseBody);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

}
