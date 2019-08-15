package de.tum.in.www1.artemis.web.rest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.UserService;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("/api")
public class FileResource {

    private final Logger log = LoggerFactory.getLogger(FileResource.class);

    private final FileService fileService;

    private final ResourceLoader resourceLoader;

    private final UserService userService;

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    private final TokenProvider tokenProvider;

    public FileResource(FileService fileService, ResourceLoader resourceLoader, UserService userService, AuthorizationCheckService authCheckService,
            LectureRepository lectureRepository, TokenProvider tokenProvider) {
        this.fileService = fileService;
        this.resourceLoader = resourceLoader;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.lectureRepository = lectureRepository;
        this.tokenProvider = tokenProvider;
    }

    /**
     * POST /fileUpload : Upload a new file.
     *
     * @param file The file to save
     * @return The path of the file
     */
    @PostMapping("/fileUpload")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<String> saveFile(@RequestParam(value = "file") MultipartFile file, @RequestParam("keepFileName") Boolean keepFileName) throws URISyntaxException {
        log.debug("REST request to upload file : {}", file.getOriginalFilename());

        // NOTE: Maximum file size is set in resources/config/application.yml
        // Currently set to 10 MB

        // check for file type
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (!fileExtension.equalsIgnoreCase("png") && !fileExtension.equalsIgnoreCase("jpg") && !fileExtension.equalsIgnoreCase("jpeg") && !fileExtension.equalsIgnoreCase("svg")
                && !fileExtension.equalsIgnoreCase("pdf") && !fileExtension.equalsIgnoreCase("zip")) {
            return ResponseEntity.badRequest().body("Unsupported file type! Allowed file types: .png, .jpg, .svg, .pdf, .zip");
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
                if (keepFileName) {
                    filename = file.getOriginalFilename().replaceAll("\\s", "");
                }
                else {
                    filename = "Temp_" + ZonedDateTime.now().toString().substring(0, 23).replaceAll(":|\\.", "-") + "_" + UUID.randomUUID().toString().substring(0, 8) + "."
                            + fileExtension;
                }
                String path = Constants.TEMP_FILEPATH + filename;

                newFile = new File(path);
                if (keepFileName && newFile.exists()) {
                    newFile.delete();
                }
                fileCreated = newFile.createNewFile();
            }
            while (!fileCreated);
            String responsePath = "/api/files/temp/" + filename;

            // copy contents of uploaded file into newly created file
            Files.copy(file.getInputStream(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // return path for getting the file
            String responseBody = "{\"path\":\"" + responsePath + "\"}";
            return ResponseEntity.created(new URI(responsePath)).body(responseBody);
        }
        catch (IOException e) {
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
    public ResponseEntity<byte[]> getTempFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(Constants.TEMP_FILEPATH + filename);
    }

    /**
     * GET /files/templates/:filename : Get the template file with the given filename
     *
     * @param filename The filename of the file to get
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping("/files/templates/{filename:.+}")
    public ResponseEntity<byte[]> getTemplateFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        try {
            Resource fileResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:templates" + File.separator + filename);
            byte[] fileContent = IOUtils.toByteArray(fileResource.getInputStream());
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity(fileContent, responseHeaders, HttpStatus.OK);
        }
        catch (IOException ex) {
            log.debug("Error when retrieving template file : {}", ex.getMessage());
            HttpHeaders responseHeaders = new HttpHeaders();
            return new ResponseEntity(null, responseHeaders, HttpStatus.NOT_FOUND);
        }
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
    public ResponseEntity<byte[]> getDragAndDropBackgroundFile(@PathVariable Long questionId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(Constants.DRAG_AND_DROP_BACKGROUND_FILEPATH + filename);
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
    public ResponseEntity<byte[]> getDragItemFile(@PathVariable Long dragItemId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(Constants.DRAG_ITEM_FILEPATH + filename);
    }

    /**
     * GET /files/course/icons/:courseId/:filename : Get the course image
     *
     * @param courseId ID of the course, the image belongs to
     * @param filename the filename of the file
     * @return The requested file, 403 if the logged in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("/files/course/icons/{courseId}/{filename:.+}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<byte[]> getCoursIcon(@PathVariable Long courseId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(Constants.COURSE_ICON_FILEPATH + filename);
    }

    /**
     * GET /files/attachments/access-token/{filename:.+} : Generates an access token that is valid for 30 seconds and given filename
     *
     * @param filename name of the file, the access token is for
     * @return The generated access token
     */
    @GetMapping("files/attachments/access-token/{filename:.+}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getTemporaryFileAccessToken(@PathVariable String filename) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (filename == null) {
            return ResponseEntity.badRequest().build();
        }
        String temporaryAccessToken = tokenProvider.createFileTokenWithCustomDuration(authentication, 30, filename);
        return ResponseEntity.ok(temporaryAccessToken);
    }

    /**
     * GET /files/course/icons/:lectureId/:filename : Get the lecture attachment
     *
     * @param lectureId ID of the lecture, the attachment belongs to
     * @param filename  the filename of the file
     * @param temporaryAccessToken The access token is required to authenticate the user that accesses it
     * @return The requested file, 403 if the logged in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/lecture/{lectureId}/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity getLectureAttachment(@PathVariable Long lectureId, @PathVariable String filename, @RequestParam("access_token") String temporaryAccessToken) {
        log.debug("REST request to get file : {}", filename);
        Optional<Lecture> optionalLecture = lectureRepository.findById(lectureId);
        if (!optionalLecture.isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        if (temporaryAccessToken == null || !this.tokenProvider.validateTokenForAuthorityAndFile(temporaryAccessToken, TokenProvider.DOWNLOAD_FILE_AUTHORITY, filename)) {
            log.info("Attachment with invalid token was accessed");
            return ResponseEntity.status(403).body("Provided token is invalid!");
        }
        Lecture lecture = optionalLecture.get();
        try {
            byte[] file = fileService.getFileForPath(Constants.LECTURE_ATTACHMENT_FILEPATH + lecture.getId() + '/' + filename);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            ByteArrayResource resource = new ByteArrayResource(file);

            ContentDisposition contentDisposition = ContentDisposition.builder("inline").filename(filename).build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(contentDisposition);
            String mediaType = "application/pdf";
            if (filename.endsWith(".pdf")) {
                mediaType = "application/pdf";
            }
            else if (filename.endsWith(".zip")) {
                mediaType = "application/zip";
            }

            return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType(mediaType)).header("filename", filename).body(resource);
        }
        catch (IOException ex) {
            log.error("Lecture attachement download lef to the following exception", ex);
            return ResponseEntity.status(500).build();
        }

    }

    /**
     * Reads the file and turns it into a ResponseEntity
     *
     * @param path the path for the file to read
     * @return ResponseEntity with status 200 and the file as byte[], status 404 if the file doesn't exist, or status 500 if there is an error while reading the file
     */
    private ResponseEntity<byte[]> responseEntityForFilePath(String path) {
        try {
            byte[] file = fileService.getFileForPath(path);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(file);
        }
        catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}
