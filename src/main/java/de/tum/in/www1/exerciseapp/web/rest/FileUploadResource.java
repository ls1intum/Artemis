package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.service.AuthorizationCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
public class FileUploadResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadResource.class);

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

        String rootDirectory = "/uploads/temp/";
        String filename = "Temp_" + ZonedDateTime.now().toString().substring(0, 23) + "_" + Math.random();
        String path = rootDirectory + filename;

        try {
            file.transferTo(new File(path));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.created(new URI(path)).body(path);
    }

}
