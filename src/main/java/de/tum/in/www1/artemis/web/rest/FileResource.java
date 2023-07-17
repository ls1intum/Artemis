package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.net.FileNameMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.Slide;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.lecture.AttachmentUnitResource;

/**
 * REST controller for managing Files.
 */
@RestController
@RequestMapping("/api")
public class FileResource {

    private final Logger log = LoggerFactory.getLogger(FileResource.class);

    private static final int DAYS_TO_CACHE = 1;

    private final FileService fileService;

    private final ResourceLoaderService resourceLoaderService;

    private final LectureRepository lectureRepository;

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final SlideRepository slideRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final AttachmentRepository attachmentRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public FileResource(SlideRepository slideRepository, AuthorizationCheckService authorizationCheckService, FileService fileService, ResourceLoaderService resourceLoaderService,
            LectureRepository lectureRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository, FileUploadExerciseRepository fileUploadExerciseRepository,
            AttachmentRepository attachmentRepository, AttachmentUnitRepository attachmentUnitRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            ExamUserRepository examUserRepository) {
        this.fileService = fileService;
        this.resourceLoaderService = resourceLoaderService;
        this.lectureRepository = lectureRepository;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.examUserRepository = examUserRepository;
        this.slideRepository = slideRepository;
    }

    /**
     * POST /fileUpload : Upload a new file.
     *
     * @param file         The file to save
     * @param keepFileName specifies if original file name should be kept
     * @return The path of the file
     * @throws URISyntaxException if response path can't be converted into URI
     * @deprecated Implement your own usage of {@link FileService#handleSaveFile(MultipartFile, boolean, boolean)} with a mixed multipart request instead. An example for this is
     *             {@link AttachmentUnitResource#updateAttachmentUnit(Long, Long, AttachmentUnit, Attachment, MultipartFile, boolean, String)}
     */
    @Deprecated
    @PostMapping("fileUpload")
    @EnforceAtLeastTutor
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
    @EnforceAtLeastTutor
    public ResponseEntity<byte[]> getTempFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
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
    @EnforceAtLeastTutor
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
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getMarkdownFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
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
    @EnforceAtLeastEditor
    public ResponseEntity<byte[]> getTemplateFile(@PathVariable Optional<ProgrammingLanguage> language, @PathVariable Optional<ProjectType> projectType,
            @PathVariable String filename) {
        log.debug("REST request to get file '{}' for programming language {} and project type {}", filename, language, projectType);
        sanitizeFilenameElseThrow(filename);
        try {
            String languagePrefix = language.map(programmingLanguage -> programmingLanguage.name().toLowerCase()).orElse("");
            String projectTypePrefix = projectType.map(type -> type.name().toLowerCase()).orElse("");

            Resource fileResource = resourceLoaderService.getResource(Path.of("templates", languagePrefix, projectTypePrefix, filename));
            if (!fileResource.exists()) {
                // Load without project type if not found with project type
                fileResource = resourceLoaderService.getResource(Path.of("templates", languagePrefix, filename));
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
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getDragAndDropBackgroundFile(@PathVariable Long questionId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
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
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getDragItemFile(@PathVariable Long dragItemId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
        return responseEntityForFilePath(FilePathService.getDragItemFilePath(), filename);
    }

    /**
     * GET /files/file-upload/submission/:submissionId/:filename : Get the file upload exercise submission file
     *
     * @param submissionId id of the submission, the file belongs to
     * @param exerciseId   id of the exercise, the file belongs to
     * @param filename     the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/file-upload-exercises/{exerciseId}/submissions/{submissionId}/{filename:.+}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getFileUploadSubmission(@PathVariable Long exerciseId, @PathVariable Long submissionId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);

        FileUploadSubmission submission = fileUploadSubmissionRepository.findByIdElseThrow(submissionId);
        FileUploadExercise exercise = fileUploadExerciseRepository.findByIdElseThrow(exerciseId);

        // check if the participation is a StudentParticipation before the following cast
        if (!(submission.getParticipation() instanceof StudentParticipation)) {
            return ResponseEntity.badRequest().build();
        }
        // user or team members that submitted the exercise
        Set<User> usersOfTheSubmission = ((StudentParticipation) submission.getParticipation()).getStudents();
        if (usersOfTheSubmission.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        User requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        // auth check - either the user that submitted the exercise or the requesting user is at least a tutor for the exercise
        if (!usersOfTheSubmission.contains(requestingUser) && !authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            throw new AccessForbiddenException();
        }

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
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getCourseIcon(@PathVariable Long courseId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
        return responseEntityForFilePath(FilePathService.getCourseIconFilePath(), filename);
    }

    /**
     * GET /files/exam-user/signatures/:examUserId/:filename : Get the exam user signature
     *
     * @param examUserId ID of the exam user, the image belongs to
     * @param filename   the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/exam-user/signatures/{examUserId}/{filename:.+}")
    @EnforceAtLeastInstructor
    public ResponseEntity<byte[]> getUserSignature(@PathVariable Long examUserId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
        ExamUser examUser = examUserRepository.findWithExamById(examUserId).orElseThrow();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, examUser.getExam().getCourse(), null);
        return buildFileResponse(FilePathService.getExamUserSignatureFilePath(), filename);
    }

    /**
     * GET /files/exam-user/:examUserId/:filename : Get the image of exam user
     *
     * @param examUserId ID of the exam user, the image belongs to
     * @param filename   the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/exam-user/{examUserId}/{filename:.+}")
    @EnforceAtLeastInstructor
    public ResponseEntity<byte[]> getExamUserImage(@PathVariable Long examUserId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
        ExamUser examUser = examUserRepository.findWithExamById(examUserId).orElseThrow();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, examUser.getExam().getCourse(), null);
        return buildFileResponse(FilePathService.getStudentImageFilePath(), filename, true);
    }

    /**
     * GET /files/attachments/lecture/:lectureId/:filename : Get the lecture attachment
     *
     * @param lectureId ID of the lecture, the attachment belongs to
     * @param filename  the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/lecture/{lectureId}/{filename:.+}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getLectureAttachment(@PathVariable Long lectureId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);

        List<Attachment> lectureAttachments = attachmentRepository.findAllByLectureId(lectureId);
        Attachment attachment = lectureAttachments.stream().filter(lectureAttachment -> filename.equals(Path.of(lectureAttachment.getLink()).getFileName().toString())).findAny()
                .orElseThrow(() -> new EntityNotFoundException("Attachment", filename));

        // get the course for a lecture attachment
        Lecture lecture = attachment.getLecture();
        Course course = lecture.getCourse();

        // check if the user is authorized to access the requested attachment unit
        if (!checkAttachmentAuthorization(course, attachment)) {
            throw new AccessForbiddenException();
        }

        return buildFileResponse(Path.of(FilePathService.getLectureAttachmentFilePath(), String.valueOf(lecture.getId())).toString(), filename);
    }

    /**
     * GET /files/attachments/lecture/{lectureId}/merge-pdf : Get the lecture units
     * PDF attachments merged
     *
     * @param lectureId ID of the lecture, the lecture units belongs to
     * @return The merged PDF file, 403 if the logged-in user is not allowed to
     *         access it, or 404 if the files to be merged do not exist
     */
    @GetMapping("files/attachments/lecture/{lectureId}/merge-pdf")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getLecturePdfAttachmentsMerged(@PathVariable Long lectureId) {
        log.debug("REST request to get merged pdf files for a lecture with id : {}", lectureId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);

        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);

        List<AttachmentUnit> lectureAttachments = attachmentUnitRepository.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, AttachmentType.FILE);

        List<String> attachmentLinks = lectureAttachments.stream()
                .filter(unit -> authCheckService.isAllowedToSeeLectureUnit(unit, user) && "pdf".equals(StringUtils.substringAfterLast(unit.getAttachment().getLink(), ".")))
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
     * @param attachmentUnitId ID of the attachment unit, the attachment belongs to
     * @param filename         the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentUnitId}/{filename:.+}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getAttachmentUnitAttachment(@PathVariable Long attachmentUnitId, @PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
        AttachmentUnit attachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);

        // get the course for a lecture's attachment unit
        Attachment attachment = attachmentUnit.getAttachment();
        Course course = attachmentUnit.getLecture().getCourse();

        // check if the user is authorized to access the requested attachment unit
        if (!checkAttachmentAuthorization(course, attachment)) {
            throw new AccessForbiddenException();
        }

        return buildFileResponse(Path.of(FilePathService.getAttachmentUnitFilePath(), String.valueOf(attachmentUnit.getId())).toString(), filename);
    }

    /**
     * GET files/attachments/slides/attachment-unit/:attachmentUnitId/slide/:slideNumber : Get the lecture unit attachment slide by slide number
     *
     * @param attachmentUnitId ID of the attachment unit, the attachment belongs to
     * @param slideNumber      the slideNumber of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentUnitId}/slide/{slideNumber}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getAttachmentUnitAttachmentSlide(@PathVariable Long attachmentUnitId, @PathVariable String slideNumber) {
        log.debug("REST request to get the slide : {}", slideNumber);
        AttachmentUnit attachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);

        Attachment attachment = attachmentUnit.getAttachment();
        Course course = attachmentUnit.getLecture().getCourse();

        if (!checkAttachmentAuthorization(course, attachment)) {
            throw new AccessForbiddenException();
        }
        Slide slide = slideRepository.findSlideByAttachmentUnitIdAndSlideNumber(attachmentUnitId, Integer.parseInt(slideNumber));
        String directoryPath = slide.getSlideImagePath();

        // Use regular expression to match and extract the file name with ".png" format
        Pattern pattern = Pattern.compile(".*/([^/]+\\.png)$");
        Matcher matcher = pattern.matcher(directoryPath);

        if (matcher.matches()) {
            String fileName = matcher.group(1);
            return buildFileResponse(
                    Path.of(FilePathService.getAttachmentUnitFilePath(), String.valueOf(attachmentUnit.getId()), "slide", String.valueOf(slide.getSlideNumber())).toString(),
                    fileName, true);
        }
        else {
            throw new EntityNotFoundException("Slide", slideNumber);
        }
    }

    /**
     * Builds the response with headers, body and content type for specified path and file name
     *
     * @param path     to the file
     * @param filename the name of the file
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(String path, String filename) {
        return buildFileResponse(path, filename, false);
    }

    /**
     * Builds the response with headers, body and content type for specified path and file name
     *
     * @param path     to the file
     * @param filename the name of the file
     * @param cache    true if the response should contain a header that allows caching; false otherwise
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(String path, String filename, boolean cache) {
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
            var response = ResponseEntity.ok().headers(headers).contentType(MediaType.parseMediaType(mimeType)).header("filename", filename);
            if (cache) {
                var cacheControl = CacheControl.maxAge(Duration.ofDays(DAYS_TO_CACHE)).cachePublic();
                response = response.cacheControl(cacheControl);
            }
            return response.body(file);
        }
        catch (IOException ex) {
            log.error("Failed to download file: {} on path: {}", filename, path, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Checks if the user is authorized to access an attachment
     *
     * @param course     the course to check if the user is part of it
     * @param attachment the attachment for which the authentication should be checked
     * @return true if the user is authorized to access the attachment, otherwise false is returned
     */
    private boolean checkAttachmentAuthorization(Course course, Attachment attachment) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        if (attachment.isVisibleToStudents() || authCheckService.isAtLeastTeachingAssistantInCourse(course, null)) {
            return true;
        }
        log.info("User not authorized to access attachment");
        return false;
    }

    /**
     * Reads the file and turns it into a ResponseEntity
     *
     * @param path the path for the file to read
     * @return ResponseEntity with status 200 and the file as byte stream, status 404 if the file doesn't exist, or status 500 if there is an error while reading the file
     */
    private ResponseEntity<byte[]> responseEntityForFilePath(String path, String filename) {
        sanitizeFilenameElseThrow(filename);
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

    /**
     * removes illegal characters and compares the resulting with the original file name
     * If both are not equal, it throws an exception
     *
     * @param filename the filename which is validated
     */
    private static void sanitizeFilenameElseThrow(String filename) {
        String sanitizedFileName = FileService.removeIllegalCharacters(filename);
        if (!sanitizedFileName.equals(filename)) {
            throw new EntityNotFoundException("The filename contains invalid characters. Only characters a-z, A-Z, 0-9, '_', '.' and '-' are allowed!");
        }
    }

}
