package de.tum.in.www1.artemis.web.rest;

import java.io.File;
import java.io.IOException;
import java.net.FileNameMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.*;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.FileUploadExerciseRepository;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("/api")
public class FileResource {

    private final Logger log = LoggerFactory.getLogger(FileResource.class);

    private final FileService fileService;

    private final ResourceLoader resourceLoader;

    private final LectureRepository lectureRepository;

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final TokenProvider tokenProvider;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    // NOTE: this list has to be the same as in file-uploader.service.ts
    private List<String> allowedFileExtensions = new ArrayList<>(Arrays.asList("png", "jpg", "jpeg", "svg", "pdf", "zip"));

    public void addAllowedFileExtension(String fileExtension) {
        this.allowedFileExtensions.add(fileExtension);
    }

    public void addRemoveFileExtension(String fileExtension) {
        this.allowedFileExtensions.remove(fileExtension);
    }

    public FileResource(FileService fileService, ResourceLoader resourceLoader, LectureRepository lectureRepository, TokenProvider tokenProvider,
            FileUploadSubmissionRepository fileUploadSubmissionRepository, FileUploadExerciseRepository fileUploadExerciseRepository,
            AttachmentUnitRepository attachmentUnitRepository) {
        this.fileService = fileService;
        this.resourceLoader = resourceLoader;
        this.lectureRepository = lectureRepository;
        this.tokenProvider = tokenProvider;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.attachmentUnitRepository = attachmentUnitRepository;
    }

    /**
     * POST /fileUpload : Upload a new file.
     *
     * @param file The file to save
     * @param keepFileName specifies if original file name should be kept
     * @return The path of the file
     * @throws URISyntaxException if response path can't be converted into URI
     */
    @PostMapping("/fileUpload")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<String> saveFile(@RequestParam(value = "file") MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFileName) throws URISyntaxException {
        log.debug("REST request to upload file : {}", file.getOriginalFilename());
        return handleSaveFile(file, keepFileName, false);

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
        return responseEntityForFilePath(FilePathService.getTempFilepath() + filename);
    }

    /**
     * POST /markdown-file-upload : Upload a new file for markdown.
     *
     * @param file The file to save
     * @param keepFileName specifies if original file name should be kept
     * @return The path of the file
     * @throws URISyntaxException if response path can't be converted into URI
     */
    @PostMapping("/markdown-file-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<String> saveMarkdownFile(@RequestParam(value = "file") MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFileName)
            throws URISyntaxException {
        log.debug("REST request to upload file for markdown: {}", file.getOriginalFilename());
        return handleSaveFile(file, keepFileName, true);
    }

    /**
     * GET /files/markdown/:filename : Get the markdown file with the given filename
     *
     * @param filename The filename of the file to get
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping("/files/markdown/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getMarkdownFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(FilePathService.getMarkdownFilepath() + filename);
    }

    /**
     * GET /files/templates/:filename : Get the template file with the given filename
     *
     * @param filename The filename of the file to get
     * @param language The programming language for which the template file should be returned
     * @param projectType The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping({ "files/templates/{language}/{projectType}/{filename}", "files/templates/{language}/{filename}", "/files/templates/{filename:.+}" })
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<byte[]> getTemplateFile(@PathVariable Optional<ProgrammingLanguage> language, @PathVariable Optional<ProjectType> projectType,
            @PathVariable String filename) {
        log.debug("REST request to get file '{}' for programming language {} and project type {}", filename, language, projectType);
        try {
            String languagePrefix = language.map(programmingLanguage -> File.separator + programmingLanguage.name().toLowerCase()).orElse("");
            String projectTypePrefix = projectType.map(type -> File.separator + type.name().toLowerCase()).orElse("");

            Resource fileResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                    .getResource("classpath:templates" + languagePrefix + projectTypePrefix + File.separator + filename);
            if (!fileResource.exists()) {
                // Load without project type if not found with project type
                fileResource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:templates" + languagePrefix + File.separator + filename);
            }

            var fileContent = IOUtils.toByteArray(fileResource.getInputStream());
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(fileContent, responseHeaders, HttpStatus.OK);
        }
        catch (IOException ex) {
            log.debug("Error when retrieving template file : {}", ex.getMessage());
            HttpHeaders responseHeaders = new HttpHeaders();
            return new ResponseEntity<>(null, responseHeaders, HttpStatus.NOT_FOUND);
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
        return responseEntityForFilePath(FilePathService.getDragAndDropBackgroundFilepath() + filename);
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
        return responseEntityForFilePath(FilePathService.getDragItemFilepath() + filename);
    }

    /**
     * GET /files/file-upload/submission/:submissionId/:filename : Get the file upload exercise submission file
     *
     * @param submissionId id of the submission, the file belongs to
     * @param exerciseId id of the exercise, the file belongs to
     * @param filename  the filename of the file
     * @param temporaryAccessToken The access token is required to authenticate the user that accesses it
     * @return The requested file, 403 if the logged in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("/files/file-upload-exercises/{exerciseId}/submissions/{submissionId}/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getFileUploadSubmission(@PathVariable Long exerciseId, @PathVariable Long submissionId, @PathVariable String filename,
            @RequestParam("access_token") String temporaryAccessToken) {
        log.debug("REST request to get file : {}", filename);
        Optional<FileUploadSubmission> optionalSubmission = fileUploadSubmissionRepository.findById(submissionId);
        Optional<FileUploadExercise> optionalFileUploadExercise = fileUploadExerciseRepository.findById(exerciseId);
        if (optionalSubmission.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!validateTemporaryAccessToken(temporaryAccessToken, filename)) {
            // NOTE: this is a special case, because we like to show this error message directly in the browser (without the angular client being active)
            String errorMessage = "You don't have the access rights for this file! Please login to Artemis and download the file in the corresponding exercise";
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorMessage.getBytes());
        }
        return buildFileResponse(FileUploadSubmission.buildFilePath(optionalFileUploadExercise.get().getId(), optionalSubmission.get().getId()), filename);
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
    public ResponseEntity<byte[]> getCourseIcon(@PathVariable Long courseId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(FilePathService.getCourseIconFilepath() + filename);
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
    public ResponseEntity<byte[]> getLectureAttachment(@PathVariable Long lectureId, @PathVariable String filename, @RequestParam("access_token") String temporaryAccessToken) {
        log.debug("REST request to get file : {}", filename);
        Optional<Lecture> optionalLecture = lectureRepository.findById(lectureId);
        if (optionalLecture.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!validateTemporaryAccessToken(temporaryAccessToken, filename)) {
            // NOTE: this is a special case, because we like to show this error message directly in the browser (without the angular client being active)
            String errorMessage = "You don't have the access rights for this file! Please login to Artemis and download the attachment in the corresponding lecture";
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorMessage.getBytes());
        }
        return buildFileResponse(FilePathService.getLectureAttachmentFilepath() + optionalLecture.get().getId(), filename);
    }

    /**
     * GET files/attachments/attachment-unit/:attachmentUnitId/:filename : Get the lecture unit attachment
     *
     * @param attachmentUnitId     ID of the attachment unit, the attachment belongs to
     * @param filename             the filename of the file
     * @param temporaryAccessToken The access token is required to authenticate the user that accesses it
     * @return The requested file, 403 if the logged in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentUnitId}/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getAttachmentUnitAttachment(@PathVariable Long attachmentUnitId, @PathVariable String filename,
            @RequestParam("access_token") String temporaryAccessToken) {
        log.debug("REST request to get file : {}", filename);
        Optional<AttachmentUnit> optionalAttachmentUnit = attachmentUnitRepository.findById(attachmentUnitId);
        if (optionalAttachmentUnit.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!validateTemporaryAccessToken(temporaryAccessToken, filename)) {
            // NOTE: this is a special case, because we like to show this error message directly in the browser (without the angular client being active)
            String errorMessage = "You don't have the access rights for this file! Please login to Artemis and download the attachment in the corresponding attachmentUnit";
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorMessage.getBytes());
        }
        return buildFileResponse(FilePathService.getAttachmentUnitFilePath() + optionalAttachmentUnit.get().getId(), filename);
    }

    /**
     * Validates temporary access token
     *
     * @param temporaryAccessToken token to be validated
     * @param filename             the name of the file
     * @return true if temporaryAccessToken is valid for this file, false otherwise
     */
    private boolean validateTemporaryAccessToken(String temporaryAccessToken, String filename) {
        if (temporaryAccessToken == null || !this.tokenProvider.validateTokenForAuthorityAndFile(temporaryAccessToken, TokenProvider.DOWNLOAD_FILE_AUTHORITY, filename)) {
            log.info("Attachment with invalid token was accessed");
            return false;
        }
        return true;
    }

    /**
     * Helper method which handles the file creation for both normal file uploads and for markdown
     * @param file The file to be uplaoded
     * @param keepFileName specifies if original file name should be kept
     * @param markdown boolean which is set to true, when we are uploading a file within the markdown editor
     * @return The path of the file
     * @throws URISyntaxException if response path can't be converted into URI
     */
    @NotNull
    private ResponseEntity<String> handleSaveFile(MultipartFile file, boolean keepFileName, boolean markdown) throws URISyntaxException {
        // NOTE: Maximum file size is set in resources/config/application.yml
        // Currently set to 10 MB

        // check for file type
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (fileExtension == null || this.allowedFileExtensions.stream().noneMatch(fileExtension::equalsIgnoreCase)) {
            return ResponseEntity.badRequest().body("Unsupported file type! Allowed file types: " + String.join(", ", this.allowedFileExtensions));
        }

        final String filePath;
        final String fileNameAddition;
        final StringBuilder responsePath = new StringBuilder();

        // set the appropriate values depending on the use case
        if (markdown) {
            filePath = FilePathService.getMarkdownFilepath();
            fileNameAddition = "Markdown_";
            responsePath.append("/api/files/markdown/");
        }
        else {
            filePath = FilePathService.getTempFilepath();
            fileNameAddition = "Temp_";
            responsePath.append("/api/files/temp/");
        }

        try {
            // create folder if necessary
            File folder;
            folder = new File(filePath);
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    log.error("Could not create directory: {}", filePath);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
                    filename = fileNameAddition + ZonedDateTime.now().toString().substring(0, 23).replaceAll(":|\\.", "-") + "_" + UUID.randomUUID().toString().substring(0, 8)
                            + "." + fileExtension;
                }
                String path = filePath + filename;

                newFile = new File(path);
                if (keepFileName && newFile.exists()) {
                    newFile.delete();
                }
                fileCreated = newFile.createNewFile();
            }
            while (!fileCreated);
            responsePath.append(filename);

            // copy contents of uploaded file into newly created file
            Files.copy(file.getInputStream(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // return path for getting the file
            String responseBody = "{\"path\":\"" + responsePath.toString() + "\"}";
            return ResponseEntity.created(new URI(responsePath.toString())).body(responseBody);
        }
        catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Builds the response with headers, body and content type for specified path and file name
     *
     * @param path to the file
     * @param filename the name of the file
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(String path, String filename) {
        try {
            var file = fileService.getFileForPath(path + '/' + filename);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            ContentDisposition contentDisposition = ContentDisposition.builder("inline").filename(filename).build();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(contentDisposition);
            FileNameMap fileNameMap = URLConnection.getFileNameMap();
            String mimeType = fileNameMap.getContentTypeFor(filename);

            // If we were unable to find mimeType with previous method, try another one, which returns application/octet-stream mime type,
            // if it also can't determine mime type
            if (mimeType == null) {
                MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();
                mimeType = fileTypeMap.getContentType(filename);
            }
            return ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType(mimeType)).header("filename", filename).body(file);
        }
        catch (IOException ex) {
            log.error("Download of file: " + filename + "on path: " + path + " let to the following exception", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Reads the file and turns it into a ResponseEntity
     *
     * @param path the path for the file to read
     * @return ResponseEntity with status 200 and the file as byte stream, status 404 if the file doesn't exist, or status 500 if there is an error while reading the file
     */
    private ResponseEntity<byte[]> responseEntityForFilePath(String path) {
        try {
            var file = fileService.getFileForPath(path);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(file);
        }
        catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
