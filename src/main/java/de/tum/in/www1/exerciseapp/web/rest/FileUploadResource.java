package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.config.Constants;
import de.tum.in.www1.exerciseapp.domain.DragAndDropQuestion;
import de.tum.in.www1.exerciseapp.domain.DragItem;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.repository.DragAndDropQuestionRepository;
import de.tum.in.www1.exerciseapp.repository.DragItemRepository;
import de.tum.in.www1.exerciseapp.service.AuthorizationCheckService;
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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("/api")
public class FileUploadResource {

    private final AuthorizationCheckService authCheckService;
    private final DragAndDropQuestionRepository dragAndDropQuestionRepository;
    private final DragItemRepository dragItemRepository;

    public FileUploadResource(AuthorizationCheckService authCheckService, DragAndDropQuestionRepository dragAndDropQuestionRepository, DragItemRepository dragItemRepository) {
        this.authCheckService = authCheckService;
        this.dragAndDropQuestionRepository = dragAndDropQuestionRepository;
        this.dragItemRepository = dragItemRepository;
    }

    private final Logger log = LoggerFactory.getLogger(FileUploadResource.class);

    /**
     * POST  /fileUpload : Upload a new file.
     *
     * @param file The file to save
     * @return The path of the file
     */
    @PostMapping("/fileUpload")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    @Timed
    public ResponseEntity<String> saveFile(@RequestParam(value = "file") MultipartFile file) throws URISyntaxException {
        log.debug("REST request to upload file : {}", file.getOriginalFilename());

        // NOTE: Maximum file size is set in resources/config/application.yml
        // Currently set to 5 MB

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
            File folder = new File(Constants.TEMP_FILEPATH);
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    log.error("Could not create directory: {}", Constants.TEMP_FILEPATH);
                    return ResponseEntity.status(500).build();
                }
            }

            // create file (generate new filename, if file already exists)
            boolean fileCreated;
            File newFile;
            String filename;
            do {
                filename = "Temp_" + ZonedDateTime.now().toString().substring(0, 23).replaceAll(":|\\.", "-") + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + fileExtension;
                String path = Constants.TEMP_FILEPATH + filename;

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
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    @Timed
    public ResponseEntity<byte[]> getTempFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);

        File file = new File(Constants.TEMP_FILEPATH + filename);
        return responseEntityForFile(file);
    }

    /**
     * GET /files/drag-and-drop/backgrounds/:questionId/:filename : Get the background file with the given name for the given drag and drop question
     *
     * @param questionId ID of the drag and drop question, the file belongs to
     * @param filename   the filename of the file
     * @return The requested file, 403 if the logged in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("/files/drag-and-drop/backgrounds/{questionId}/{filename:.+}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<byte[]> getDragAndDropBackgroundFile(@PathVariable Long questionId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);

        DragAndDropQuestion question = dragAndDropQuestionRepository.findOne(questionId);
        if (question == null) {
            return ResponseEntity.notFound().build();
        }
        if (authCheckService.isAllowedToSeeExercise(question.getExercise(), null)) {
            File file = new File(Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH + filename);
            return responseEntityForFile(file);
        } else {
            return ResponseEntity.status(403).build(); // 403 FORBIDDEN
        }
    }

    /**
     * GET /files/drag-and-drop/drag-items/:dragItemId/:filename : Get the drag item file with the given name for the given drag item
     *
     * @param dragItemId ID of the drag item, the file belongs to
     * @param filename   the filename of the file
     * @return The requested file, 403 if the logged in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("/files/drag-and-drop/drag-items/{dragItemId}/{filename:.+}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @Timed
    public ResponseEntity<byte[]> getDragItemFile(@PathVariable Long dragItemId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);

        DragItem dragItem = dragItemRepository.findOne(dragItemId);
        if (dragItem == null) {
            return ResponseEntity.notFound().build();
        }
        if (authCheckService.isAllowedToSeeExercise(dragItem.getQuestion().getExercise(), null)) {
            File file = new File(Constants.DRAG_ITEM_FILEPATH + filename);
            return responseEntityForFile(file);
        } else {
            return ResponseEntity.status(403).build(); // 403 FORBIDDEN
        }
    }

    /**
     * Reads the file and turns it into a ResponseEntity
     *
     * @param file the file to read
     * @return ResponseEntity with status 200 and the file as byte[], status 404 if the file doesn't exist, or status 500 if there is an error while reading the file
     */
    private ResponseEntity<byte[]> responseEntityForFile(File file) {
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
