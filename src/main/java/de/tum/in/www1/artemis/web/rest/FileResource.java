package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.net.FileNameMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.web.rest.lecture.AttachmentUnitResource;

/**
 * REST controller for managing Files.
 */
@RestController
@RequestMapping("/api")
public class FileResource {

    private final Logger log = LoggerFactory.getLogger(FileResource.class);

    private final FileService fileService;

    private final ResourceLoaderService resourceLoaderService;

    private final LectureRepository lectureRepository;

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public FileResource(FileService fileService, ResourceLoaderService resourceLoaderService, LectureRepository lectureRepository,
            FileUploadSubmissionRepository fileUploadSubmissionRepository, FileUploadExerciseRepository fileUploadExerciseRepository,
            AttachmentUnitRepository attachmentUnitRepository, AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.fileService = fileService;
        this.resourceLoaderService = resourceLoaderService;
        this.lectureRepository = lectureRepository;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * POST /fileUpload : Upload a new file.
     *
     * @param file         The file to save
     * @param keepFileName specifies if original file name should be kept
     * @return The path of the file
     * @throws URISyntaxException if response path can't be converted into URI
     * @deprecated Implement your own usage of {@link FileService#handleSaveFile(MultipartFile, boolean, boolean)} with a mixed multipart request instead. An example for this is
     * * {@link AttachmentUnitResource#updateAttachmentUnit(Long, Long, AttachmentUnit, Attachment, MultipartFile, boolean, String)}
     */
    @Deprecated
    @PostMapping("fileUpload")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<String> saveFile(@RequestParam(value = "file") MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFileName) throws URISyntaxException {
        log.debug("REST request to upload file : {}", file.getOriginalFilename());
        String responsePath = fileService.handleSaveFile(file, keepFileName, false);

        // return path for getting the file
        String responseBody = "{\"path\":\"" + responsePath + "\"}";

        return ResponseEntity.created(new URI(responsePath)).body(responseBody);

    }

    /**
     * GET /files/temp/:filename : Get the temporary file with the given filename
     *
     * @param filename The filename of the file to get
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping("files/temp/{filename:.+}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<byte[]> getTempFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(FilePathService.getTempFilePath(), filename);
    }

    /**
     * POST /markdown-file-upload : Upload a new file for markdown.
     *
     * @param file         The file to save
     * @param keepFileName specifies if original file name should be kept
     * @return The path of the file
     * @throws URISyntaxException if response path can't be converted into URI
     */
    @PostMapping("markdown-file-upload")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<String> saveMarkdownFile(@RequestParam(value = "file") MultipartFile file, @RequestParam(defaultValue = "false") boolean keepFileName)
            throws URISyntaxException {
        log.debug("REST request to upload file for markdown: {}", file.getOriginalFilename());
        String responsePath = fileService.handleSaveFile(file, keepFileName, true);

        // return path for getting the file
        String responseBody = "{\"path\":\"" + responsePath + "\"}";

        return ResponseEntity.created(new URI(responsePath)).body(responseBody);
    }

    /**
     * GET /files/markdown/:filename : Get the markdown file with the given filename
     *
     * @param filename The filename of the file to get
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping("files/markdown/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getMarkdownFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return buildFileResponse(FilePathService.getMarkdownFilePath(), filename);
    }

    /**
     * GET /files/templates/:filename : Get the template file with the given filename
     *
     * @param filename    The filename of the file to get
     * @param language    The programming language for which the template file should be returned
     * @param projectType The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping({ "files/templates/{language}/{projectType}/{filename}", "files/templates/{language}/{filename}", "/files/templates/{filename:.+}" })
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<byte[]> getTemplateFile(@PathVariable Optional<ProgrammingLanguage> language, @PathVariable Optional<ProjectType> projectType,
            @PathVariable String filename) {
        log.debug("REST request to get file '{}' for programming language {} and project type {}", filename, language, projectType);
        try {
            String languagePrefix = language.map(programmingLanguage -> programmingLanguage.name().toLowerCase()).orElse("");
            String projectTypePrefix = projectType.map(type -> type.name().toLowerCase()).orElse("");

            Resource fileResource = resourceLoaderService.getResource("templates", languagePrefix, projectTypePrefix, filename);
            if (!fileResource.exists()) {
                // Load without project type if not found with project type
                fileResource = resourceLoaderService.getResource("templates", languagePrefix, filename);
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
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/drag-and-drop/backgrounds/{questionId}/{filename:.+}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> getDragAndDropBackgroundFile(@PathVariable Long questionId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(FilePathService.getDragAndDropBackgroundFilePath(), filename);
    }

    /**
     * GET /files/drag-and-drop/drag-items/:dragItemId/:filename : Get the drag item file with the given name for the given drag item
     *
     * @param dragItemId ID of the drag item, the file belongs to
     * @param filename   the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/drag-and-drop/drag-items/{dragItemId}/{filename:.+}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> getDragItemFile(@PathVariable Long dragItemId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(FilePathService.getDragItemFilePath(), filename);
    }

    /**
     * GET /files/file-upload/submission/:submissionId/:filename : Get the file upload exercise submission file
     *
     * @param submissionId         id of the submission, the file belongs to
     * @param exerciseId           id of the exercise, the file belongs to
     * @param filename             the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/file-upload-exercises/{exerciseId}/submissions/{submissionId}/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getFileUploadSubmission(@PathVariable Long exerciseId, @PathVariable Long submissionId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        FileUploadSubmission submission = fileUploadSubmissionRepository.findByIdElseThrow(submissionId);
        FileUploadExercise exercise = fileUploadExerciseRepository.findByIdElseThrow(exerciseId);
        return buildFileResponse(FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId()), filename);
    }

    /**
     * GET /files/course/icons/:courseId/:filename : Get the course image
     *
     * @param courseId ID of the course, the image belongs to
     * @param filename the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/course/icons/{courseId}/{filename:.+}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> getCourseIcon(@PathVariable Long courseId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        return responseEntityForFilePath(FilePathService.getCourseIconFilePath(), filename);
    }

    /**
     * GET /files/attachments/lecture/:lectureId/:filename : Get the lecture attachment
     *
     * @param lectureId            ID of the lecture, the attachment belongs to
     * @param filename             the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/lecture/{lectureId}/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getLectureAttachment(@PathVariable Long lectureId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        return buildFileResponse(Path.of(FilePathService.getLectureAttachmentFilePath(), String.valueOf(lecture.getId())).toString(), filename);
    }

    /**
     * GET /files/attachments/lecture/{lectureId}/merge-pdf : Get the lecture units
     * PDF attachments merged
     *
     * @param lectureId            ID of the lecture, the lecture units belongs to
     * @return The merged PDF file, 403 if the logged-in user is not allowed to
     *         access it, or 404 if the files to be merged do not exist
     */
    @GetMapping("files/attachments/lecture/{lectureId}/merge-pdf")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getLecturePdfAttachmentsMerged(@PathVariable Long lectureId) {
        log.debug("REST request to get merged pdf files for a lecture with id : {}", lectureId);

        User user = userRepository.getUserWithGroupsAndAuthorities();

        Set<AttachmentUnit> lectureAttachments = attachmentUnitRepository.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, AttachmentType.FILE);

        List<String> attachmentLinks = lectureAttachments.stream()
                .filter(unit -> authCheckService.isAllowedToSeeLectureUnit(unit, user) && "pdf".equals(StringUtils.substringAfterLast(unit.getAttachment().getLink(), ".")))
                .sorted(Comparator.comparing(LectureUnit::getOrder))
                .map(unit -> Path.of(FilePathService.getAttachmentUnitFilePath(), String.valueOf(unit.getId()), StringUtils.substringAfterLast(unit.getAttachment().getLink(), "/"))
                        .toString())
                .toList();

        Optional<byte[]> file = fileService.mergePdfFiles(attachmentLinks, lectureRepository.getLectureTitle(lectureId));
        if (file.isEmpty()) {
            log.error("Failed to merge PDF lecture units for lecture with id {}", lectureId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(file.get());
    }

    /**
     * GET files/attachments/attachment-unit/:attachmentUnitId/:filename : Get the lecture unit attachment
     *
     * @param attachmentUnitId     ID of the attachment unit, the attachment belongs to
     * @param filename             the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentUnitId}/{filename:.+}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> getAttachmentUnitAttachment(@PathVariable Long attachmentUnitId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        AttachmentUnit attachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);
        return buildFileResponse(Path.of(FilePathService.getAttachmentUnitFilePath(), String.valueOf(attachmentUnit.getId())).toString(), filename);
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
            var actualPath = Path.of(path, filename).toString();
            var file = fileService.getFileForPath(actualPath);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            HttpHeaders headers = new HttpHeaders();

            // attachment will force the user to download the file
            String lowerCaseFilename = filename.toLowerCase();
            String contentType = lowerCaseFilename.endsWith("htm") || lowerCaseFilename.endsWith("html") || lowerCaseFilename.endsWith("svg") || lowerCaseFilename.endsWith("svgz")
                    ? "attachment"
                    : "inline";
            headers.setContentDisposition(ContentDisposition.builder(contentType).filename(filename).build());

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
            log.error("Failed to download file: {} on path: {}", filename, path, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Reads the file and turns it into a ResponseEntity
     *
     * @param path the path for the file to read
     * @return ResponseEntity with status 200 and the file as byte stream, status 404 if the file doesn't exist, or status 500 if there is an error while reading the file
     */
    private ResponseEntity<byte[]> responseEntityForFilePath(String path, String filename) {
        try {
            var actualPath = Path.of(path, filename).toString();
            var file = fileService.getFileForPath(actualPath);
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
