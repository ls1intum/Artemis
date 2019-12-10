package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing FileUploadSubmission.
 */
@RestController
@RequestMapping("/api")
public class FileUploadSubmissionResource extends GenericSubmissionResource<FileUploadSubmission> {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionResource.class);

    private static final String ENTITY_NAME = "fileUploadSubmission";

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final FileUploadExerciseService fileUploadExerciseService;

    public FileUploadSubmissionResource(CourseService courseService, FileUploadSubmissionService fileUploadSubmissionService, FileUploadExerciseService fileUploadExerciseService,
            AuthorizationCheckService authCheckService, UserService userService, ExerciseService exerciseService, ParticipationService participationService) {
        super(courseService, authCheckService, userService, exerciseService, participationService);
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.fileUploadExerciseService = fileUploadExerciseService;
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
        final var validityExceptionResponse = this.checkExerciseValidityForStudent(exercise);
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
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadSubmission", "fileUploadSubmissionCantStore",
                    "The uploaded file could not be saved on the server")).build();
        }

        hideDetails(submission, user);
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
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmission(@PathVariable long submissionId) {
        log.debug("REST request to get FileUploadSubmission with id: {}", submissionId);
        var fileUploadSubmission = fileUploadSubmissionService.findOne(submissionId);
        var studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        final var fileUploadExercise = (FileUploadExercise) studentParticipation.getExercise();
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise, user)) {
            return forbidden();
        }
        fileUploadSubmission = fileUploadSubmissionService.getLockedFileUploadSubmission(submissionId, fileUploadExercise);
        // Make sure the exercise is connected to the participation in the json response
        studentParticipation.setExercise(fileUploadExercise);
        hideDetails(fileUploadSubmission, user);
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
    public ResponseEntity<List<FileUploadSubmission>> getAllFileUploadSubmissions(@PathVariable long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get all file upload submissions");
        return getAllSubmissions(exerciseId, assessedByTutor, submittedOnly, fileUploadSubmissionService, FileUploadSubmission.class);
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
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmissionWithoutAssessment(@PathVariable long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission) {
        log.debug("REST request to get a file upload submission without assessment");
        final Exercise fileUploadExercise = exerciseService.findOne(exerciseId);
        final var exerciseInvalid = this.checkExerciseValidityForTutor(fileUploadExercise, FileUploadExercise.class, fileUploadSubmissionService);
        if (exerciseInvalid != null) {
            return exerciseInvalid;
        }

        final FileUploadSubmission fileUploadSubmission;
        if (lockSubmission) {
            fileUploadSubmission = fileUploadSubmissionService.getLockedFileUploadSubmissionWithoutResult((FileUploadExercise) fileUploadExercise);
        }
        else {
            Optional<FileUploadSubmission> optionalFileUploadSubmission = fileUploadSubmissionService
                    .getSubmissionWithoutManualResult(fileUploadExercise, FileUploadSubmission.class).map(FileUploadSubmission.class::cast);
            if (optionalFileUploadSubmission.isEmpty()) {
                return notFound();
            }
            fileUploadSubmission = optionalFileUploadSubmission.get();
        }

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        studentParticipation.setExercise(fileUploadExercise);
        hideDetails(fileUploadSubmission, userService.getUserWithGroupsAndAuthorities());
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
    public ResponseEntity<FileUploadSubmission> getDataForFileUpload(@PathVariable long participationId) {
        return getDataForEditor(participationId, FileUploadExercise.class, FileUploadSubmission.class, new FileUploadSubmission());
    }
}
