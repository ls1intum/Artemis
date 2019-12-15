package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.EmptyFileException;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing FileUploadSubmission.
 */
@RestController
@RequestMapping("/api")
public class FileUploadSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionResource.class);

    private static final String ENTITY_NAME = "fileUploadSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CourseService courseService;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final FileUploadExerciseService fileUploadExerciseService;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseService exerciseService;

    private final UserService userService;

    private final ParticipationService participationService;

    public FileUploadSubmissionResource(CourseService courseService, FileUploadSubmissionService fileUploadSubmissionService, FileUploadExerciseService fileUploadExerciseService,
            AuthorizationCheckService authCheckService, UserService userService, ExerciseService exerciseService, ParticipationService participationService) {
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.courseService = courseService;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.fileUploadExerciseService = fileUploadExerciseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
    }

    /**
     * POST /file-upload-submissions : Submit file upload exercise with file.
     *
     * @param fileUploadSubmission the fileUploadSubmission to create
     * @param exerciseId the id of the exercise of the submission
     * @param file The uploaded file belonging to the submission
     * @param principal the user principal, i.e. the identity of the logged in user - provided by Spring
     * @return the ResponseEntity with status 200 and with body the new fileUploadSubmission, or with status 400 (Bad Request) if the fileUploadSubmission has already an
     * ID
     */
    @PostMapping(value = "/exercises/{exerciseId}/file-upload-submissions")
    @PreAuthorize("hasAnyRole('USER','TA','INSTRUCTOR','ADMIN')")
    public ResponseEntity<FileUploadSubmission> submitFileUploadExercise(@PathVariable long exerciseId, Principal principal,
            @RequestPart("submission") FileUploadSubmission fileUploadSubmission, @RequestPart("file") MultipartFile file) {
        log.debug("REST request to submit new FileUploadSubmission : {}", fileUploadSubmission);

        final var exercise = fileUploadExerciseService.findOne(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastStudentForExercise(exercise, user)) {
            return forbidden();
        }

        // Check if the course hasn't been changed
        final var validityExceptionResponse = this.checkExerciseValidity(exercise);
        if (validityExceptionResponse != null) {
            return validityExceptionResponse;
        }

        // Check the file size
        if (file.getSize() > Constants.MAX_UPLOAD_FILESIZE_BYTES) {
            // NOTE: Maximum file size for submission is 2 MB
            return ResponseEntity.status(413).headers(HeaderUtil.createAlert(applicationName, "The maximum file size is 2MB!", "fileUploadSubmissionFileTooBig")).build();
        }

        // Check the pattern
        final var splittedFileName = file.getOriginalFilename().split("\\.");
        final var fileSuffix = splittedFileName[splittedFileName.length - 1].toLowerCase();
        final var filePattern = String.join("|", exercise.getFilePattern().toLowerCase().replace(" ", "").split(","));
        if (!fileSuffix.matches(filePattern)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .headers(HeaderUtil.createAlert(applicationName, "The uploaded file has the wrong type!", "fileUploadSubmissionIllegalFileType")).build();
        }

        final FileUploadSubmission submission;
        try {
            submission = fileUploadSubmissionService.handleFileUploadSubmission(fileUploadSubmission, file, exercise, principal);
        }
        catch (IOException e) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadSubmission", "cantSaveFile", "The uploaded file could not be saved on the server"))
                    .build();
        }

        catch (EmptyFileException e) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadSubmission", "emptyFile", "The uploaded file is empty"))
                    .build();
        }

        this.fileUploadSubmissionService.hideDetails(submission, user);
        return ResponseEntity.ok(submission);
    }

    /**
     * GET /file-upload-submissions/:id : get the fileUploadSubmissions by it's id. Is used by tutor when assessing submissions.
     *
     * @param submissionId the id of the fileUploadSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('TA','INSTRUCTOR','ADMIN')")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get FileUploadSubmission with id: {}", submissionId);
        var fileUploadSubmission = fileUploadSubmissionService.findOne(submissionId);
        var studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        var fileUploadExercise = (FileUploadExercise) studentParticipation.getExercise();
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise, user)) {
            return forbidden();
        }
        fileUploadSubmission = fileUploadSubmissionService.getLockedFileUploadSubmission(submissionId, fileUploadExercise);
        // Make sure the exercise is connected to the participation in the json response
        studentParticipation.setExercise(fileUploadExercise);
        this.fileUploadSubmissionService.hideDetails(fileUploadSubmission, user);
        return ResponseEntity.ok(fileUploadSubmission);
    }

    /**
     * GET /file-upload-submissions : get all the fileUploadSubmissions for an exercise. It is possible to filter, to receive only the one that have been already submitted, or only the one
     * assessed by the tutor who is doing the call
     *
     * @param exerciseId the id of the exercise
     * @param submittedOnly if only submitted submissions should be returned
     * @param assessedByTutor if the submission was assessed by calling tutor
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Submissions in body
     */
    @GetMapping("/exercises/{exerciseId}/file-upload-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: separate this into 2 calls, one for instructors (with all submissions) and one for tutors (only the submissions for the requesting tutor)
    public ResponseEntity<List<FileUploadSubmission>> getAllFileUploadSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get all file upload submissions");
        final Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();

        if (assessedByTutor) {
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                throw new AccessForbiddenException("You are not allowed to access this resource");
            }
        }
        else if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        final List<FileUploadSubmission> fileUploadSubmissions;
        if (assessedByTutor) {
            fileUploadSubmissions = fileUploadSubmissionService.getAllFileUploadSubmissionsByTutorForExercise(exerciseId, user.getId());
        }
        else {
            fileUploadSubmissions = fileUploadSubmissionService.getFileUploadSubmissions(exerciseId, submittedOnly);
        }

        // tutors should not see information about the student of a submission
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            fileUploadSubmissions.forEach(submission -> fileUploadSubmissionService.hideDetails(submission, user));
        }

        // remove unnecessary data from the REST response
        fileUploadSubmissions.forEach(submission -> {
            if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null) {
                submission.getParticipation().setExercise(null);
            }
        });

        return ResponseEntity.ok().body(fileUploadSubmissions);
    }

    /**
     * GET /file-upload-submission-without-assessment : get one File Upload Submission without assessment.
     *
     * @param exerciseId the id of the exercise
     * @param lockSubmission specifies if the submission should be locked for assessor
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Submissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/file-upload-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission) {
        log.debug("REST request to get a file upload submission without assessment");
        final Exercise fileUploadExercise = exerciseService.findOneWithAdditionalElements(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise, user)) {
            return forbidden();
        }
        if (!(fileUploadExercise instanceof FileUploadExercise)) {
            return badRequest();
        }

        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (fileUploadExercise.getDueDate() != null && fileUploadExercise.getDueDate().isAfter(ZonedDateTime.now())) {
            return notFound();
        }

        // Check if the limit of simultaneously locked submissions has been reached
        fileUploadSubmissionService.checkSubmissionLockLimit(fileUploadExercise.getCourse().getId());

        final FileUploadSubmission fileUploadSubmission;
        if (lockSubmission) {
            fileUploadSubmission = fileUploadSubmissionService.getLockedFileUploadSubmissionWithoutResult((FileUploadExercise) fileUploadExercise);
        }
        else {
            Optional<FileUploadSubmission> optionalFileUploadSubmission = fileUploadSubmissionService
                    .getFileUploadSubmissionWithoutManualResult((FileUploadExercise) fileUploadExercise);
            if (optionalFileUploadSubmission.isEmpty()) {
                return notFound();
            }
            fileUploadSubmission = optionalFileUploadSubmission.get();
        }

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        studentParticipation.setExercise(fileUploadExercise);
        this.fileUploadSubmissionService.hideDetails(fileUploadSubmission, user);
        return ResponseEntity.ok(fileUploadSubmission);
    }

    /**
     * Returns the data needed for the file upload editor, which includes the participation, fileUploadSubmission with answer if existing and the assessments if the submission was already
     * submitted.
     *
     * @param participationId the participationId for which to find the data for the file upload editor
     * @return the ResponseEntity with the participation as body
     */
    @GetMapping("/participations/{participationId}/file-upload-editor")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadSubmission> getDataForFileUpload(@PathVariable Long participationId) {
        StudentParticipation participation = participationService.findOneWithEagerSubmissionsAndResults(participationId);
        if (participation == null) {
            return ResponseEntity.notFound()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).build();
        }
        FileUploadExercise fileUploadExercise;
        if (participation.getExercise() instanceof FileUploadExercise) {
            fileUploadExercise = (FileUploadExercise) participation.getExercise();
            if (fileUploadExercise == null) {
                return ResponseEntity.badRequest()
                        .headers(
                                HeaderUtil.createFailureAlert(applicationName, true, "fileUploadExercise", "exerciseEmpty", "The exercise belonging to the participation is null."))
                        .body(null);
            }

            // make sure sensitive information are not sent to the client
            fileUploadExercise.filterSensitiveInformation();
        }
        else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadExercise", "wrongExerciseType",
                    "The exercise of the participation is not a file upload exercise.")).body(null);
        }

        // Students can only see their own file uploads (to prevent cheating). TAs, instructors and admins can see all file uploads.
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise))) {
            return forbidden();
        }

        Optional<FileUploadSubmission> optionalFileUploadSubmission = participation.findLatestFileUploadSubmission();
        FileUploadSubmission fileUploadSubmission;
        if (!optionalFileUploadSubmission.isPresent()) {
            // this should never happen as the submission is initialized along with the participation when the exercise is started
            fileUploadSubmission = new FileUploadSubmission();
            fileUploadSubmission.setParticipation(participation);
        }
        else {
            // only try to get and set the file upload if the fileUploadSubmission existed before
            fileUploadSubmission = optionalFileUploadSubmission.get();
        }

        // make sure only the latest submission and latest result is sent to the client
        participation.setSubmissions(null);
        participation.setResults(null);

        // do not send the result to the client if the assessment is not finished
        if (fileUploadSubmission.getResult() != null && (fileUploadSubmission.getResult().getCompletionDate() == null || fileUploadSubmission.getResult().getAssessor() == null)) {
            fileUploadSubmission.setResult(null);
        }

        // do not send the assessor information to students
        if (fileUploadSubmission.getResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise)) {
            fileUploadSubmission.getResult().setAssessor(null);
        }

        return ResponseEntity.ok(fileUploadSubmission);
    }

    private ResponseEntity<FileUploadSubmission> checkExerciseValidity(FileUploadExercise fileUploadExercise) {
        if (fileUploadExercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.findOne(fileUploadExercise.getCourse().getId());
        if (course == null) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "courseNotFound", "The course belonging to this file upload exercise does not exist"))
                    .body(null);
        }
        if (!authCheckService.isAtLeastStudentInCourse(course, userService.getUserWithGroupsAndAuthorities())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }
}
