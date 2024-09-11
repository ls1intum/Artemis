package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

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

import jakarta.validation.constraints.NotNull;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.domain.Attachment;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.domain.Lecture;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.AttachmentType;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.enumeration.ProjectType;
import de.tum.cit.aet.artemis.domain.exam.ExamUser;
import de.tum.cit.aet.artemis.domain.lecture.AttachmentUnit;
import de.tum.cit.aet.artemis.domain.lecture.Slide;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.cit.aet.artemis.domain.quiz.DragItem;
import de.tum.cit.aet.artemis.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.repository.AttachmentUnitRepository;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.DragItemRepository;
import de.tum.cit.aet.artemis.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.repository.FileUploadSubmissionRepository;
import de.tum.cit.aet.artemis.repository.LectureRepository;
import de.tum.cit.aet.artemis.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.repository.SlideRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.service.LectureUnitService;
import de.tum.cit.aet.artemis.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing Files.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class FileResource {

    private static final Logger log = LoggerFactory.getLogger(FileResource.class);

    private static final int DAYS_TO_CACHE = 1;

    private final FileService fileService;

    private final ResourceLoaderService resourceLoaderService;

    private final LectureRepository lectureRepository;

    private final AttachmentUnitRepository attachmentUnitRepository;

    private final SlideRepository slideRepository;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final AttachmentRepository attachmentRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final QuizQuestionRepository quizQuestionRepository;

    private final DragItemRepository dragItemRepository;

    private final CourseRepository courseRepository;

    private final LectureUnitService lectureUnitService;

    public FileResource(SlideRepository slideRepository, AuthorizationCheckService authorizationCheckService, FileService fileService, ResourceLoaderService resourceLoaderService,
            LectureRepository lectureRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository, AttachmentRepository attachmentRepository,
            AttachmentUnitRepository attachmentUnitRepository, AuthorizationCheckService authCheckService, UserRepository userRepository, ExamUserRepository examUserRepository,
            QuizQuestionRepository quizQuestionRepository, DragItemRepository dragItemRepository, CourseRepository courseRepository, LectureUnitService lectureUnitService) {
        this.fileService = fileService;
        this.resourceLoaderService = resourceLoaderService;
        this.lectureRepository = lectureRepository;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentUnitRepository = attachmentUnitRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.examUserRepository = examUserRepository;
        this.slideRepository = slideRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.dragItemRepository = dragItemRepository;
        this.courseRepository = courseRepository;
        this.lectureUnitService = lectureUnitService;
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
        String responsePath = fileService.handleSaveFile(file, keepFileName, true).toString();

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
    @GetMapping("files/markdown/{filename}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getMarkdownFile(@PathVariable String filename) {
        log.debug("REST request to get file : {}", filename);
        sanitizeFilenameElseThrow(filename);
        return buildFileResponse(FilePathService.getMarkdownFilePath(), filename);
    }

    /**
     * GET /files/templates/:language/:projectType : Get the template file with the given filename<br/>
     * GET /files/templates/:language : Get the template file with the given filename
     * <p>
     * The readme file contains the default problem statement for new programming exercises.
     *
     * @param language    The programming language for which the template file should be returned
     * @param projectType The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping({ "files/templates/{language}/{projectType}", "files/templates/{language}" })
    @EnforceAtLeastEditor
    public ResponseEntity<byte[]> getTemplateFile(@PathVariable ProgrammingLanguage language, @PathVariable Optional<ProjectType> projectType) {
        log.debug("REST request to get readme file for programming language {} and project type {}", language, projectType);

        String languagePrefix = language.name().toLowerCase();
        String projectTypePrefix = projectType.map(type -> type.name().toLowerCase()).orElse("");

        return getTemplateFileContentWithResponse(languagePrefix, projectTypePrefix);
    }

    private ResponseEntity<byte[]> getTemplateFileContentWithResponse(String languagePrefix, String projectTypePrefix) {
        try {
            Resource fileResource = resourceLoaderService.getResource(Path.of("templates", languagePrefix, projectTypePrefix, "readme"));
            if (!fileResource.exists() && !projectTypePrefix.isEmpty()) {
                // Load without project type if not found with project type
                fileResource = resourceLoaderService.getResource(Path.of("templates", languagePrefix, "readme"));
            }
            byte[] fileContent = IOUtils.toByteArray(fileResource.getInputStream());
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
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/drag-and-drop/backgrounds/{questionId}/*")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getDragAndDropBackgroundFile(@PathVariable Long questionId) {
        log.debug("REST request to get background for drag and drop question : {}", questionId);
        DragAndDropQuestion question = quizQuestionRepository.findDnDQuestionByIdOrElseThrow(questionId);
        Course course = question.getExercise().getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        return responseEntityForFilePath(getActualPathFromPublicPathString(question.getBackgroundFilePath()));
    }

    /**
     * GET /files/drag-and-drop/drag-items/:dragItemId/:filename : Get the drag item file with the given name for the given drag item
     *
     * @param dragItemId ID of the drag item, the file belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/drag-and-drop/drag-items/{dragItemId}/*")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getDragItemFile(@PathVariable Long dragItemId) {
        log.debug("REST request to get file for drag item : {}", dragItemId);
        DragItem dragItem = dragItemRepository.findWithEagerQuestionByIdElseThrow(dragItemId);
        Course course = dragItem.getQuestion().getExercise().getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        if (dragItem.getPictureFilePath() == null) {
            throw new EntityNotFoundException("Drag item " + dragItemId + " has no picture file");
        }
        return responseEntityForFilePath(getActualPathFromPublicPathString(dragItem.getPictureFilePath()));
    }

    /**
     * GET /files/file-upload/submission/:submissionId/:filename : Get the file upload exercise submission file
     *
     * @param submissionId id of the submission, the file belongs to
     * @param exerciseId   id of the exercise, the file belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/file-upload-exercises/{exerciseId}/submissions/{submissionId}/*")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getFileUploadSubmission(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        log.debug("REST request to get file for file upload submission : {}", exerciseId);

        FileUploadSubmission submission = fileUploadSubmissionRepository.findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(submissionId, exerciseId);
        FileUploadExercise exercise = (FileUploadExercise) submission.getParticipation().getExercise();

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

        return buildFileResponse(getActualPathFromPublicPathString(submission.getFilePath()), false);
    }

    /**
     * GET /files/course/icons/:courseId/:filename : Get the course image
     *
     * @param courseId ID of the course, the image belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/course/icons/{courseId}/*")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getCourseIcon(@PathVariable Long courseId) {
        log.debug("REST request to get icon for course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        // NOTE: we do not enforce a check if the user is a student in the course here, because the course icon is not criticial and we do not want to waste resources
        return responseEntityForFilePath(getActualPathFromPublicPathString(course.getCourseIcon()));
    }

    /**
     * GET /files/user/profile-picture/:userId/:filename : Get the user image
     *
     * @param userId ID of the user the image belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/user/profile-pictures/{userId}/*")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getProfilePicture(@PathVariable Long userId) {
        log.debug("REST request to get profile picture for user : {}", userId);
        User user = userRepository.findByIdElseThrow(userId);
        return responseEntityForFilePath(getActualPathFromPublicPathString(user.getImageUrl()));
    }

    /**
     * GET /files/templates/code-of-conduct : Get the Code of Conduct template
     *
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/templates/code-of-conduct")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getCourseCodeOfConduct() throws IOException {
        var templatePath = Path.of("templates", "codeofconduct", "README.md");
        log.debug("REST request to get template : {}", templatePath);
        var resource = resourceLoaderService.getResource(templatePath);
        return ResponseEntity.ok(resource.getInputStream().readAllBytes());
    }

    /**
     * GET /files/exam-user/signatures/:examUserId/:filename : Get the exam user signature
     *
     * @param examUserId ID of the exam user, the image belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/exam-user/signatures/{examUserId}/*")
    @EnforceAtLeastInstructor
    public ResponseEntity<byte[]> getUserSignature(@PathVariable Long examUserId) {
        log.debug("REST request to get signature for exam user : {}", examUserId);
        ExamUser examUser = examUserRepository.findWithExamById(examUserId).orElseThrow();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, examUser.getExam().getCourse(), null);

        return buildFileResponse(getActualPathFromPublicPathString(examUser.getSigningImagePath()), false);
    }

    /**
     * GET /files/exam-user/:examUserId/:filename : Get the image of exam user
     *
     * @param examUserId ID of the exam user, the image belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/exam-user/{examUserId}/*")
    @EnforceAtLeastInstructor
    public ResponseEntity<byte[]> getExamUserImage(@PathVariable Long examUserId) {
        log.debug("REST request to get image for exam user : {}", examUserId);
        ExamUser examUser = examUserRepository.findWithExamById(examUserId).orElseThrow();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, examUser.getExam().getCourse(), null);

        return buildFileResponse(getActualPathFromPublicPathString(examUser.getStudentImagePath()), true);
    }

    /**
     * GET /files/attachments/lecture/:lectureId/:filename : Get the lecture attachment
     *
     * @param lectureId ID of the lecture, the attachment belongs to
     * @param filename  the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/lecture/{lectureId}/{filename}")
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
        checkAttachmentAuthorizationOrThrow(course, attachment);

        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink()), false);
    }

    /**
     * GET /files/courses/{courseId}/attachments/{attachmentId} : Returns the file associated with the
     * given attachment ID as a downloadable resource
     *
     * @param courseId     The ID of the course that the Attachment belongs to
     * @param attachmentId the ID of the attachment to retrieve
     * @return ResponseEntity containing the file as a resource
     */
    @GetMapping("files/courses/{courseId}/attachments/{attachmentId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<byte[]> getAttachmentFile(@PathVariable Long courseId, @PathVariable Long attachmentId) {
        Attachment attachment = attachmentRepository.findByIdElseThrow(attachmentId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        checkAttachmentExistsInCourseOrThrow(course, attachment);

        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink()), false);
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

        List<AttachmentUnit> lectureAttachments = attachmentUnitRepository.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, AttachmentType.FILE).stream()
                .filter(unit -> authCheckService.isAllowedToSeeLectureUnit(unit, user) && "pdf".equals(StringUtils.substringAfterLast(unit.getAttachment().getLink(), ".")))
                .toList();

        lectureUnitService.setCompletedForAllLectureUnits(lectureAttachments, user, true);
        List<Path> attachmentLinks = lectureAttachments.stream().map(unit -> FilePathService.actualPathForPublicPathOrThrow(URI.create(unit.getAttachment().getLink()))).toList();

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
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentUnitId}/*")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getAttachmentUnitAttachment(@PathVariable Long attachmentUnitId) {
        log.debug("REST request to get file for attachment unit : {}", attachmentUnitId);
        AttachmentUnit attachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);

        // get the course for a lecture's attachment unit
        Attachment attachment = attachmentUnit.getAttachment();
        Course course = attachmentUnit.getLecture().getCourse();

        // check if the user is authorized to access the requested attachment unit
        checkAttachmentAuthorizationOrThrow(course, attachment);

        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink()), false);
    }

    /**
     * GET files/courses/{courseId}/attachment-units/{attachmenUnitId} : Returns the file associated with the
     * given attachmentUnit ID as a downloadable resource
     *
     * @param courseId         The ID of the course that the Attachment belongs to
     * @param attachmentUnitId the ID of the attachment to retrieve
     * @return ResponseEntity containing the file as a resource
     */
    @GetMapping("files/courses/{courseId}/attachment-units/{attachmentUnitId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<byte[]> getAttachmentUnitFile(@PathVariable Long courseId, @PathVariable Long attachmentUnitId) {
        log.debug("REST request to get file for attachment unit : {}", attachmentUnitId);
        AttachmentUnit attachmentUnit = attachmentUnitRepository.findByIdElseThrow(attachmentUnitId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Attachment attachment = attachmentUnit.getAttachment();
        checkAttachmentUnitExistsInCourseOrThrow(course, attachmentUnit);

        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink()), false);
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

        checkAttachmentAuthorizationOrThrow(course, attachment);

        Slide slide = slideRepository.findSlideByAttachmentUnitIdAndSlideNumber(attachmentUnitId, Integer.parseInt(slideNumber));
        String directoryPath = slide.getSlideImagePath();

        // Use regular expression to match and extract the file name with ".png" format
        Pattern pattern = Pattern.compile(".*/([^/]+\\.png)$");
        Matcher matcher = pattern.matcher(directoryPath);

        if (matcher.matches()) {
            String fileName = matcher.group(1);
            return buildFileResponse(
                    FilePathService.getAttachmentUnitFilePath().resolve(Path.of(attachmentUnit.getId().toString(), "slide", String.valueOf(slide.getSlideNumber()))), fileName,
                    true);
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
    private ResponseEntity<byte[]> buildFileResponse(Path path, String filename) {
        return buildFileResponse(path, filename, false);
    }

    /**
     * Builds the response with headers, body and content type for specified path containing the file name
     *
     * @param path  to the file including the file name
     * @param cache true if the response should contain a header that allows caching; false otherwise
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(Path path, boolean cache) {
        return buildFileResponse(path.getParent(), path.getFileName().toString(), cache);
    }

    /**
     * Builds the response with headers, body and content type for specified path and file name
     *
     * @param path     to the file
     * @param filename the name of the file
     * @param cache    true if the response should contain a header that allows caching; false otherwise
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(Path path, String filename, boolean cache) {
        try {
            Path actualPath = path.resolve(filename);
            byte[] file = fileService.getFileForPath(actualPath);
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

            var response = ResponseEntity.ok().headers(headers).contentType(getMediaTypeFromFilename(filename)).header("filename", filename);
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

    private Path getActualPathFromPublicPathString(@NotNull String publicPath) {
        if (publicPath == null) {
            throw new EntityNotFoundException("No file linked");
        }
        return FilePathService.actualPathForPublicPathOrThrow(URI.create(publicPath));
    }

    private MediaType getMediaTypeFromFilename(String filename) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String mimeType = fileNameMap.getContentTypeFor(filename);
        if (mimeType != null) {
            return MediaType.parseMediaType(mimeType);
        }
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

        return MediaType.parseMediaType(fileTypeMap.getContentType(filename));
    }

    /**
     * Checks if the user is authorized to access an attachment
     *
     * @param course     the course to check if the user is part of it
     * @param attachment the attachment for which the authentication should be checked
     */
    private void checkAttachmentAuthorizationOrThrow(Course course, Attachment attachment) {
        if (attachment.isVisibleToStudents()) {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        }
        else {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        }
    }

    /**
     * Checks if the attachment exists in the mentioned course
     *
     * @param course     the course to check if the attachment is part of it
     * @param attachment the attachment for which the existence should be checked
     */
    private void checkAttachmentExistsInCourseOrThrow(Course course, Attachment attachment) {
        if (!attachment.getLecture().getCourse().equals(course)) {
            throw new EntityNotFoundException("This attachment does not exist in this course.");
        }
    }

    /**
     * Checks if the attachment exists in the mentioned course
     *
     * @param course         the course to check if the attachment is part of it
     * @param attachmentUnit the attachment unit for which the existence should be checked
     */
    private void checkAttachmentUnitExistsInCourseOrThrow(Course course, AttachmentUnit attachmentUnit) {
        if (!attachmentUnit.getLecture().getCourse().equals(course)) {
            throw new EntityNotFoundException("This attachment unit does not exist in this course.");
        }
    }

    /**
     * Reads the file and turns it into a ResponseEntity
     *
     * @param filePath the path for the file to read
     * @return ResponseEntity with status 200 and the file as byte stream, status 404 if the file doesn't exist, or status 500 if there is an error while reading the file
     */
    private ResponseEntity<byte[]> responseEntityForFilePath(Path filePath) {
        try {
            var file = fileService.getFileForPath(filePath);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(file);
        }
        catch (IOException e) {
            log.error("Failed to return requested file with path {}", filePath, e);
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
        String sanitizedFileName = FileService.sanitizeFilename(filename);
        if (!sanitizedFileName.equals(filename)) {
            throw new EntityNotFoundException("The filename contains invalid characters. Only characters a-z, A-Z, 0-9, '_', '.' and '-' are allowed!");
        }
    }
}
