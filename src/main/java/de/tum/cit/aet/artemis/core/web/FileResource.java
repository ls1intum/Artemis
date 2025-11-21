package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.apache.velocity.shaded.commons.io.FilenameUtils.getBaseName;
import static org.apache.velocity.shaded.commons.io.FilenameUtils.getExtension;

import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.FileUploadEntityType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ApiProfileNotPresentException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.file.FileUploadService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exam.api.ExamUserApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadApi;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.lecture.api.LectureAttachmentApi;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.repository.DragItemRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

/**
 * REST controller for managing Files.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class FileResource {

    private static final Logger log = LoggerFactory.getLogger(FileResource.class);

    private static final int DAYS_TO_CACHE = 1;

    private final FileService fileService;

    private final FileUploadService fileUploadService;

    private final ResourceLoaderService resourceLoaderService;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<FileUploadApi> fileUploadApi;

    private final Optional<LectureAttachmentApi> lectureAttachmentApi;

    private final Optional<SlideApi> slideApi;

    private final UserRepository userRepository;

    private final Optional<ExamUserApi> examUserApi;

    private final AuthorizationCheckService authorizationCheckService;

    private final QuizQuestionRepository quizQuestionRepository;

    private final DragItemRepository dragItemRepository;

    private final CourseRepository courseRepository;

    private final Optional<LectureUnitApi> lectureUnitApi;

    public FileResource(FileUploadService fileUploadService, AuthorizationCheckService authorizationCheckService, FileService fileService,
            ResourceLoaderService resourceLoaderService, Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<FileUploadApi> fileUploadApi,
            Optional<LectureAttachmentApi> lectureAttachmentApi, Optional<SlideApi> slideApi, UserRepository userRepository, Optional<ExamUserApi> examUserApi,
            QuizQuestionRepository quizQuestionRepository, DragItemRepository dragItemRepository, CourseRepository courseRepository, Optional<LectureUnitApi> lectureUnitApi) {
        this.fileUploadService = fileUploadService;
        this.fileService = fileService;
        this.resourceLoaderService = resourceLoaderService;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureAttachmentApi = lectureAttachmentApi;
        this.slideApi = slideApi;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.examUserApi = examUserApi;
        this.quizQuestionRepository = quizQuestionRepository;
        this.dragItemRepository = dragItemRepository;
        this.courseRepository = courseRepository;
        this.lectureUnitApi = lectureUnitApi;
        this.fileUploadApi = fileUploadApi;
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
        String publicPath = FileUtil.handleSaveFile(file, keepFileName, true).toString();
        String responsePath = getResponsePathFromPublicPathString(publicPath);

        // return path for getting the file
        String responseBody = "{\"path\":\"" + responsePath + "\"}";

        return ResponseEntity.created(new URI(responsePath)).body(responseBody);
    }

    /**
     * POST /files/courses/{courseId}/conversations/{conversationId} : Upload a new file for use in a conversation.
     *
     * @param file           The file to save. The size must not exceed Constants.MAX_FILE_SIZE_COMMUNICATION.
     * @param courseId       The ID of the course the conversation belongs to.
     * @param conversationId The ID of the conversation the file is used in.
     * @return The path of the file.
     * @throws URISyntaxException If the response path can't be converted into a URI.
     */
    @PostMapping("files/courses/{courseId}/conversations/{conversationId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<String> saveMarkdownFileForConversation(@RequestParam(value = "file") MultipartFile file, @PathVariable Long courseId, @PathVariable Long conversationId)
            throws URISyntaxException {
        log.debug("REST request to upload file for markdown in conversation: {} for conversation {} in course {}", file.getOriginalFilename(), conversationId, courseId);
        if (file.getSize() > Constants.MAX_FILE_SIZE_COMMUNICATION) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large. Maximum file size is " + Constants.MAX_FILE_SIZE_COMMUNICATION + " bytes.");
        }
        var filePathInformation = FileUtil.handleSaveFileInConversation(file, courseId, conversationId);
        String publicPath = filePathInformation.publicPath().toString();

        fileUploadService.createFileUpload(publicPath, filePathInformation.serverPath().toString(), filePathInformation.filename(), conversationId,
                FileUploadEntityType.CONVERSATION);

        // return path for getting the file
        String responsePath = getResponsePathFromPublicPathString(publicPath);
        String responseBody = "{\"path\":\"" + responsePath + "\"}";

        return ResponseEntity.created(new URI(responsePath)).body(responseBody);
    }

    /**
     * GET /files/courses/{courseId}/conversations/{conversationId}/{filename} : Get the markdown file with the given filename for the given conversation.
     *
     * @param courseId       The ID of the course the conversation belongs to.
     * @param conversationId The ID of the conversation the file is used in.
     * @param filename       The filename of the file to get.
     * @return The requested file, or 404 if the file doesn't exist. The response will enable caching.
     */
    @GetMapping("files/courses/{courseId}/conversations/{conversationId}/{filename}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<byte[]> getMarkdownFileForConversation(@PathVariable Long courseId, @PathVariable Long conversationId, @PathVariable String filename) {
        // TODO: Improve the access check
        log.debug("REST request to get file for markdown in conversation: File {} for conversation {} in course {}", filename, conversationId, courseId);
        sanitizeFilenameElseThrow(filename);

        var serverFilePath = FilePathConverter.getMarkdownFilePathForConversation(courseId, conversationId);
        var publicPath = "courses/" + courseId + "/conversations/" + conversationId + "/" + filename;
        var fileUpload = fileUploadService.findByPath(publicPath);

        if (fileUpload.isPresent()) {
            return buildFileResponse(serverFilePath, filename, Optional.ofNullable(fileUpload.get().getFilename()), true);
        }

        return buildFileResponse(serverFilePath, filename, true);
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
        return buildFileResponse(FilePathConverter.getMarkdownFilePath(), filename, false);
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
            byte[] fileContent;
            try (InputStream inputStream = fileResource.getInputStream()) {
                fileContent = inputStream.readAllBytes();
            }
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
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        return responseEntityForFilePath(getActualPathFromPublicPathString(question.getBackgroundFilePath(), FilePathType.DRAG_AND_DROP_BACKGROUND));
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
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        if (dragItem.getPictureFilePath() == null) {
            throw new EntityNotFoundException("Drag item " + dragItemId + " has no picture file");
        }
        return responseEntityForFilePath(getActualPathFromPublicPathString(dragItem.getPictureFilePath(), FilePathType.DRAG_ITEM));
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

        FileUploadApi api = fileUploadApi.orElseThrow(() -> new ApiProfileNotPresentException(FileUploadApi.class, PROFILE_CORE));
        FileUploadSubmission submission = api.findWithTeamStudentsAndParticipationAndExerciseByIdAndExerciseIdElseThrow(submissionId, exerciseId);
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
        if (!usersOfTheSubmission.contains(requestingUser) && !authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            throw new AccessForbiddenException();
        }

        return buildFileResponse(getActualPathFromPublicPathString(submission.getFilePath(), FilePathType.FILE_UPLOAD_SUBMISSION), false);
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
        return responseEntityForFilePath(getActualPathFromPublicPathString(course.getCourseIcon(), FilePathType.COURSE_ICON));
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
        return responseEntityForFilePath(getActualPathFromPublicPathString(user.getImageUrl(), FilePathType.PROFILE_PICTURE));
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
        ExamUserApi api = examUserApi.orElseThrow(() -> new ExamApiNotPresentException(ExamUserApi.class));

        ExamUser examUser = api.findWithExamById(examUserId).orElseThrow();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, examUser.getExam().getCourse(), null);

        return buildFileResponse(getActualPathFromPublicPathString(examUser.getSigningImagePath(), FilePathType.EXAM_USER_SIGNATURE), false);
    }

    /**
     * GET /files/exam-user/:examUserId/:filename : Get the image of exam user
     *
     * @param examUserId ID of the exam user, the image belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/exam-user/{examUserId}/*")
    @EnforceAtLeastTutor
    public ResponseEntity<byte[]> getExamUserImage(@PathVariable long examUserId) {
        log.debug("REST request to get image for exam user : {}", examUserId);
        ExamUserApi api = examUserApi.orElseThrow(() -> new ExamApiNotPresentException(ExamUserApi.class));

        ExamUser examUser = api.findWithExamById(examUserId).orElseThrow();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, examUser.getExam().getCourse(), null);

        return buildFileResponse(getActualPathFromPublicPathString(examUser.getStudentImagePath(), FilePathType.EXAM_USER_IMAGE), true);
    }

    /**
     * GET /files/attachments/lecture/:lectureId/:filename : Get the lecture attachment
     *
     * @param lectureId      ID of the lecture, the attachment belongs to
     * @param attachmentName the filename of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/lecture/{lectureId}/{attachmentName}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getLectureAttachment(@PathVariable Long lectureId, @PathVariable String attachmentName) {
        log.debug("REST request to get lecture attachment : {}", attachmentName);
        LectureAttachmentApi api = lectureAttachmentApi.orElseThrow(() -> new LectureApiNotPresentException(LectureAttachmentApi.class));

        List<Attachment> lectureAttachments = api.findAllByLectureId(lectureId);
        Attachment attachment = lectureAttachments.stream().filter(lectureAttachment -> lectureAttachment.getName().equals(getBaseName(attachmentName))).findAny()
                .orElseThrow(() -> new EntityNotFoundException("Attachment", attachmentName));

        // get the course for a lecture attachment
        Lecture lecture = attachment.getLecture();
        Course course = lecture.getCourse();

        // check if the user is authorized to access the requested attachment video unit
        checkAttachmentAuthorizationOrThrow(course, attachment);

        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink(), FilePathType.LECTURE_ATTACHMENT), retrieveDownloadFilename(attachment));
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
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        LectureUnitApi unitApi = lectureUnitApi.orElseThrow(() -> new LectureApiNotPresentException(LectureUnitApi.class));
        LectureAttachmentApi attachmentApi = lectureAttachmentApi.orElseThrow(() -> new LectureApiNotPresentException(LectureAttachmentApi.class));

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Lecture lecture = api.findByIdElseThrow(lectureId);

        authorizationCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, user);

        List<AttachmentVideoUnit> lectureAttachments = attachmentApi.findAllByLectureIdAndAttachmentTypeElseThrow(lectureId, AttachmentType.FILE).stream().filter(
                unit -> authorizationCheckService.isAllowedToSeeLectureUnit(unit, user) && "pdf".equals(StringUtils.substringAfterLast(unit.getAttachment().getLink(), ".")))
                .toList();

        unitApi.setCompletedForAllLectureUnits(lectureAttachments, user, true);

        // Modified to use studentVersion if available
        List<Path> attachmentLinks = lectureAttachments.stream().map(unit -> {
            Attachment attachment = unit.getAttachment();
            String filePath = attachment.getStudentVersion() != null ? attachment.getStudentVersion() : attachment.getLink();
            FilePathType filePathType = attachment.getStudentVersion() != null ? FilePathType.STUDENT_VERSION_SLIDES : FilePathType.ATTACHMENT_UNIT;
            return FilePathConverter.fileSystemPathForExternalUri(URI.create(filePath), filePathType);
        }).toList();

        Optional<byte[]> file = FileUtil.mergePdfFiles(attachmentLinks, api.getLectureTitle(lectureId));
        if (file.isEmpty()) {
            log.error("Failed to merge PDF lecture units for lecture with id {}", lectureId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(file.get());
    }

    /**
     * GET files/attachments/attachment-unit/:attachmentVideoUnitId/:filename : Get the lecture unit attachment
     * Accesses to this endpoint are created by the server itself in the FilePathService
     *
     * @param attachmentVideoUnitId ID of the attachment video unit, the attachment belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentVideoUnitId}/*")
    @EnforceAtLeastTutor
    public ResponseEntity<byte[]> getAttachmentVideoUnitAttachment(@PathVariable Long attachmentVideoUnitId) {
        log.debug("REST request to get the file for attachment video unit {} for tutors", attachmentVideoUnitId);
        LectureAttachmentApi api = lectureAttachmentApi.orElseThrow(() -> new LectureApiNotPresentException(LectureAttachmentApi.class));

        AttachmentVideoUnit attachmentVideoUnit = api.findAttachmentVideoUnitByIdElseThrow(attachmentVideoUnitId);

        // get the course for a lecture's attachment video unit
        Attachment attachment = attachmentVideoUnit.getAttachment();
        Course course = attachmentVideoUnit.getLecture().getCourse();

        // check if the user is authorized to access the requested attachment video unit
        checkAttachmentAuthorizationOrThrow(course, attachment);
        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink(), FilePathType.ATTACHMENT_UNIT), retrieveDownloadFilename(attachment));
    }

    /**
     * GET files/courses/{courseId}/attachment-units/{attachmentVideoUnitId} : Returns the file associated with the
     * given attachmentVideoUnit ID as a downloadable resource
     *
     * @param courseId              The ID of the course that the Attachment belongs to
     * @param attachmentVideoUnitId the ID of the attachment to retrieve
     * @return ResponseEntity containing the file as a resource
     */
    @GetMapping("files/courses/{courseId}/attachment-units/{attachmentVideoUnitId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<byte[]> getAttachmentVideoUnitFile(@PathVariable Long courseId, @PathVariable Long attachmentVideoUnitId) {
        log.debug("REST request to get the file for attachment video unit {} for editors", attachmentVideoUnitId);
        LectureAttachmentApi api = lectureAttachmentApi.orElseThrow(() -> new LectureApiNotPresentException(LectureAttachmentApi.class));
        AttachmentVideoUnit attachmentVideoUnit = api.findAttachmentVideoUnitByIdElseThrow(attachmentVideoUnitId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Attachment attachment = attachmentVideoUnit.getAttachment();
        checkAttachmentVideoUnitExistsInCourseOrThrow(course, attachmentVideoUnit);

        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink(), FilePathType.ATTACHMENT_UNIT), retrieveDownloadFilename(attachment));
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
        log.debug("REST request to get attachment file : {}", attachmentId);
        LectureAttachmentApi api = lectureAttachmentApi.orElseThrow(() -> new LectureApiNotPresentException(LectureAttachmentApi.class));
        Attachment attachment = api.findAttachmentByIdElseThrow(attachmentId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        checkAttachmentExistsInCourseOrThrow(course, attachment);

        return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink(), FilePathType.LECTURE_ATTACHMENT), retrieveDownloadFilename(attachment));
    }

    /**
     * GET files/attachments/attachment-unit/{attachmentVideoUnitId}/slide/{slideNumber} : Get the lecture unit attachment slide by slide number
     *
     * @param attachmentVideoUnitId ID of the attachment video unit, the attachment belongs to
     * @param slideNumber           the slideNumber of the file
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentVideoUnitId}/slide/{slideNumber}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getAttachmentVideoUnitAttachmentSlide(@PathVariable Long attachmentVideoUnitId, @PathVariable String slideNumber) {
        log.debug("REST request to get the slide {} in attachment video unit {}", slideNumber, attachmentVideoUnitId);
        LectureAttachmentApi api = lectureAttachmentApi.orElseThrow(() -> new LectureApiNotPresentException(LectureAttachmentApi.class));
        SlideApi sApi = slideApi.orElseThrow(() -> new LectureApiNotPresentException(SlideApi.class));

        AttachmentVideoUnit attachmentVideoUnit = api.findAttachmentVideoUnitByIdElseThrow(attachmentVideoUnitId);

        Attachment attachment = attachmentVideoUnit.getAttachment();
        Course course = attachmentVideoUnit.getLecture().getCourse();

        checkAttachmentAuthorizationOrThrow(course, attachment);

        int sNumber = Integer.parseInt(slideNumber);

        Slide slide = sApi.findSlideByAttachmentVideoUnitIdAndSlideNumber(attachmentVideoUnitId, sNumber);

        return getSlideResponse(slide, sNumber);
    }

    /**
     * GET files/slides/{slideId} : Get the lecture unit attachment slide by slide id
     *
     * @param slideId the id of the slide that wanted to be retrieved
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/slides/{slideId}")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getSlideById(@PathVariable long slideId) {
        log.debug("REST request to get the slide with id {}", slideId);
        SlideApi api = slideApi.orElseThrow(() -> new LectureApiNotPresentException(SlideApi.class));

        Slide slide = api.findSlideByIdElseThrow(slideId);

        return getSlideResponse(slide, slideId);
    }

    /**
     * GET files/slides/{slideIdOrNumber} : Get the lecture unit attachment slide by slide id or number
     *
     * @param slideIdOrNumber the id or number of the slide that wanted to be retrieved
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    private ResponseEntity<byte[]> getSlideResponse(Slide slide, long slideIdOrNumber) {
        if (slide.getHidden() != null) {
            throw new AccessForbiddenException("Slide is hidden");
        }

        String directoryPath = slide.getSlideImagePath();

        // Use regular expression to match and extract the file name with ".png" format
        Pattern pattern = Pattern.compile(".*/([^/]+\\.png)$");
        Matcher matcher = pattern.matcher(directoryPath);

        if (matcher.matches()) {
            return buildFileResponse(getActualPathFromPublicPathString(slide.getSlideImagePath(), FilePathType.SLIDE), false);
        }
        else {
            throw new EntityNotFoundException("Slide", slideIdOrNumber);
        }
    }

    /**
     * GET files/attachments/attachment-unit/{attachmentUnitId}/student/* : Get the student version of attachment video unit by attachment video unit id
     *
     * @param attachmentVideoUnitId ID of the attachment video unit, the student version belongs to
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @GetMapping("files/attachments/attachment-unit/{attachmentVideoUnitId}/student/*")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getAttachmentVideoUnitStudentVersion(@PathVariable long attachmentVideoUnitId) {
        log.debug("REST request to get the student version of attachment video unit : {}", attachmentVideoUnitId);
        LectureAttachmentApi api = lectureAttachmentApi.orElseThrow(() -> new LectureApiNotPresentException(LectureAttachmentApi.class));

        AttachmentVideoUnit attachmentVideoUnit = api.findAttachmentVideoUnitByIdElseThrow(attachmentVideoUnitId);
        Attachment attachment = attachmentVideoUnit.getAttachment();
        Course course = attachmentVideoUnit.getLecture().getCourse();
        checkAttachmentAuthorizationOrThrow(course, attachment);

        Optional<String> downloadFilename = retrieveDownloadFilename(attachment);
        // check if hidden link is available in the attachment
        String studentVersion = attachment.getStudentVersion();
        if (studentVersion == null) {
            return buildFileResponse(getActualPathFromPublicPathString(attachment.getLink(), FilePathType.ATTACHMENT_UNIT), downloadFilename);
        }

        String fileName = studentVersion.substring(studentVersion.lastIndexOf("/") + 1);

        return buildFileResponse(FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(Path.of(attachmentVideoUnit.getId().toString(), "student")), fileName,
                downloadFilename, false);
    }

    private static Optional<String> retrieveDownloadFilename(Attachment attachment) {
        return Optional.of(attachment.getName() + "." + getExtension(attachment.getLink()));
    }

    /**
     * Builds the response with headers, body and content type for specified path containing the file name
     *
     * @param path  to the file including the file name
     * @param cache true if the response should contain a header that allows caching; false otherwise
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(Path path, boolean cache) {
        return buildFileResponse(path.getParent(), path.getFileName().toString(), Optional.empty(), cache);
    }

    /**
     * Builds the response with headers, body and content type for specified path containing the file name
     *
     * @param path     to the file including the file name
     * @param filename the name of the file
     * @param cache    true if the response should contain a header that allows caching; false otherwise
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(Path path, String filename, boolean cache) {
        return buildFileResponse(path, filename, Optional.empty(), cache);
    }

    /**
     * Builds the response with headers, body and content type for specified path and file name
     *
     * @param path            to the file
     * @param replaceFilename replaces the downloaded file's name, if provided
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(Path path, Optional<String> replaceFilename) {
        return buildFileResponse(path.getParent(), path.getFileName().toString(), replaceFilename, false);
    }

    /**
     * Builds the response with headers, body and content type for specified path and file name
     *
     * @param path            to the file
     * @param filename        the name of the file
     * @param replaceFilename replaces the downloaded file's name, if provided
     * @param cache           true if the response should contain a header that allows caching; false otherwise
     * @return response entity
     */
    private ResponseEntity<byte[]> buildFileResponse(Path path, String filename, Optional<String> replaceFilename, boolean cache) {
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
            String headerFilename = FileUtil.sanitizeFilename(replaceFilename.orElse(filename));
            headers.setContentDisposition(ContentDisposition.builder(contentType).filename(headerFilename).build());
            headers.set("Filename", headerFilename);

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

    private String getResponsePathFromPublicPathString(@NonNull String publicPath) {
        // fail-safe to raise awareness if the public path is not correct (should not happen)
        if (publicPath.startsWith(ARTEMIS_FILE_PATH_PREFIX)) {
            throw new IllegalArgumentException("The public path should not contain the Artemis file path prefix");
        }
        return ARTEMIS_FILE_PATH_PREFIX + publicPath;
    }

    private Path getActualPathFromPublicPathString(@NonNull String publicPath, FilePathType filePathType) {
        return FilePathConverter.fileSystemPathForExternalUri(URI.create(publicPath), filePathType);
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
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        }
        else {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
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
     * @param course              the course to check if the attachment is part of it
     * @param attachmentVideoUnit the attachment video unit for which the existence should be checked
     */
    private void checkAttachmentVideoUnitExistsInCourseOrThrow(Course course, AttachmentVideoUnit attachmentVideoUnit) {
        if (!attachmentVideoUnit.getLecture().getCourse().equals(course)) {
            throw new EntityNotFoundException("This attachment video unit does not exist in this course.");
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
            return ResponseEntity.ok().cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS)) // Cache for 30 days;
                    .contentType(getMediaTypeFromFilename(filePath.getFileName().toString())).body(file);
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
        String sanitizedFileName = FileUtil.sanitizeFilename(filename);
        if (!sanitizedFileName.equals(filename)) {
            throw new EntityNotFoundException("The filename contains invalid characters. Only characters a-z, A-Z, 0-9, '_', '.' and '-' are allowed!");
        }
    }
}
