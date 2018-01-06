package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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
    private final String tempPath = "uploads/images/temp/";

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

        // check for size of file
        if (file.getSize() > 5000000) {
            return ResponseEntity.badRequest().body("File size too large. Maximum file size: 5 MB.");
        }

        // check for file type
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (!fileExtension.equalsIgnoreCase("png")
            && !fileExtension.equalsIgnoreCase("jpg")
            && !fileExtension.equalsIgnoreCase("jpeg")
            && !fileExtension.equalsIgnoreCase("svg")) {
            return ResponseEntity.badRequest().body("Unsupported file type! Allowed file types: .png, .jpg, .svg");
        }

        try {
            // create folder if necessary
            File folder = new File(tempPath);
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    log.error("Could not create directory \"uploads/images/temp\".");
                    return ResponseEntity.status(500).build();
                }
            }

            // create file (generate new filename, if file already exists)
            boolean fileCreated;
            File newFile;
            String filename;
            do {
                filename = "Temp_" + ZonedDateTime.now().toString().substring(0, 23) + "_" + Math.random() + "." + fileExtension;
                String path = tempPath + filename;

                newFile = new File(path);
                fileCreated = newFile.createNewFile();
            } while (!fileCreated);
            String responsePath = "/api/files/temp/" + filename;

            // copy contents of uploaded file into newly created file
            Files.copy(file.getInputStream(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // return path for getting the file
            String responseBody = "{\"path\":\"" + responsePath + "\"}";
            return ResponseEntity.created(new URI(responsePath)).body(responseBody);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /files/temp/:filename : Get the temporary file with the given filename
     *
     * @param filename The filename of the file to get
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping("/files/temp/{filename:.+}")
    @Timed
    public ResponseEntity<byte[]> getTempFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);

        File file = new File(tempPath + filename);

        if (file.exists()) {
            try {
                return ResponseEntity.ok(Files.readAllBytes(file.toPath()));
            } catch (IOException e) {
                e.printStackTrace();
                return ResponseEntity.status(500).build();
            }
        } else {
            return ResponseEntity.notFound().build();
        }
    }

}
