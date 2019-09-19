package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

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

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final CourseService courseService;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final FileUploadExerciseService fileUploadExerciseService;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseService exerciseService;

    private final UserService userService;

    private final ParticipationService participationService;

    private final ResultRepository resultRepository;

    public FileUploadSubmissionResource(FileUploadSubmissionRepository fileUploadSubmissionRepository, CourseService courseService,
            FileUploadSubmissionService fileUploadSubmissionService, FileUploadExerciseService fileUploadExerciseService, AuthorizationCheckService authCheckService,
            UserService userService, ExerciseService exerciseService, ParticipationService participationService, ResultRepository resultRepository) {
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.courseService = courseService;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.fileUploadExerciseService = fileUploadExerciseService;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
    }

    /**
     * POST /file-upload-submissions : Create a new fileUploadSubmission.
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
        if (!authCheckService.isAtLeastStudentForExercise(exercise)) {
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

        final FileUploadSubmission submission;
        try {
            submission = fileUploadSubmissionService.handleFileUploadSubmission(fileUploadSubmission, file, exercise, principal);
        }
        catch (IOException e) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadSubmission", "fileUploadSubmissionCantStore",
                    "The uploaded file could not be saved on the server")).build();
        }

        hideDetails(submission);
        return ResponseEntity.ok(submission);
    }

    /**
     * GET /file-upload-submissions/:id : get the "id" fileUploadSubmission.
     *
     * @param submissionId the id of the fileUploadSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-submissions/{submissionId}")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to get FileUploadSubmission with id: {}", submissionId);
        var fileUploadExercise = (FileUploadExercise) fileUploadSubmissionService.findOne(submissionId).getParticipation().getExercise();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise)) {
            return forbidden();
        }
        var fileUploadSubmission = fileUploadSubmissionService.getLockedFileUploadSubmission(submissionId, fileUploadExercise);
        // Make sure the exercise is connected to the participation in the json response
        StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        studentParticipation.setExercise(fileUploadExercise);
        hideDetails(fileUploadSubmission);
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
    public ResponseEntity<List<FileUploadSubmission>> getAllFileUploadSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get all file upload submissions");
        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        List<FileUploadSubmission> fileUploadSubmissions;
        if (assessedByTutor) {
            User user = userService.getUserWithGroupsAndAuthorities();
            fileUploadSubmissions = fileUploadSubmissionService.getAllFileUploadSubmissionsByTutorForExercise(exerciseId, user.getId());
        }
        else {
            fileUploadSubmissions = fileUploadSubmissionService.getFileUploadSubmissions(exerciseId, submittedOnly);
        }

        return ResponseEntity.ok().body(fileUploadSubmissions);
    }

    /**
     * GET /file-upload-submission-without-assessment : get one File Upload Submission without assessment.
     *
     * @param exerciseId the id of the exercise
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Submissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/file-upload-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmissionWithoutAssessment(@PathVariable Long exerciseId) {
        log.debug("REST request to get a file upload submission without assessment");
        Exercise exercise = fileUploadExerciseService.findOne(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now())) {
            return notFound();
        }

        // Check if the limit of simultaneously locked submissions has been reached
        fileUploadSubmissionService.checkSubmissionLockLimit(exercise.getCourse().getId());

        Optional<FileUploadSubmission> fileUploadSubmissionWithoutAssessment = this.fileUploadSubmissionService
                .getFileUploadSubmissionWithoutManualResult((FileUploadExercise) exercise);

        // tutors should not see information about the student of a submission
        if (fileUploadSubmissionWithoutAssessment.isPresent() && fileUploadSubmissionWithoutAssessment.get().getParticipation() != null
                && fileUploadSubmissionWithoutAssessment.get().getParticipation() instanceof StudentParticipation) {
            ((StudentParticipation) fileUploadSubmissionWithoutAssessment.get().getParticipation()).filterSensitiveInformation();
        }

        return ResponseUtil.wrapOrNotFound(fileUploadSubmissionWithoutAssessment);
    }

    /**
     * DELETE /file-upload-submissions/:submissionId : delete the "id" fileUploadSubmission.
     *
     * @param submissionId the id of the fileUploadSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/file-upload-submissions/{submissionId}")
    public ResponseEntity<Void> deleteFileUploadSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to delete FileUploadSubmission : {}", submissionId);
        fileUploadSubmissionRepository.deleteById(submissionId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, submissionId.toString())).build();
    }

    /**
     * Returns the data needed for the file upload editor, which includes the participation, fileUploadSubmission with answer if existing and the assessments if the submission was already
     * submitted.
     *
     * @param participationId the participationId for which to find the data for the file upload editor
     * @return the ResponseEntity with the participation as body
     */
    @GetMapping("/file-upload-editor/{participationId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StudentParticipation> getDataForFileUpload(@PathVariable Long participationId) {
        StudentParticipation participation = participationService.findOneStudentParticipationWithEagerSubmissionsResultsExerciseAndCourse(participationId);
        if (participation == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "participationNotFound", "No participation was found for the given ID.")).body(null);
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
        }
        else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadExercise", "wrongExerciseType",
                    "The exercise of the participation is not a modeling exercise.")).body(null);
        }

        // users can only see their own submission (to prevent cheating), TAs, instructors and admins
        // can see all answers
        if (!authCheckService.isOwnerOfParticipation(participation) && !courseService.userHasAtLeastTAPermissions(fileUploadExercise.getCourse())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // if no results, check if there are really no results or the relation to results was not
        // updated yet
        if (participation.getResults().size() <= 0) {
            List<Result> results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participation.getId());
            participation.setResults(new HashSet<>(results));
        }

        Optional<FileUploadSubmission> optionalFileUploadSubmission = participation.findLatestFileUploadSubmission();
        participation.setSubmissions(new HashSet<>());

        participation.getExercise().filterSensitiveInformation();

        if (optionalFileUploadSubmission.isPresent()) {
            FileUploadSubmission fileUploadSubmission = optionalFileUploadSubmission.get();

            // set reference to participation to null, since we are already inside a participation
            fileUploadSubmission.setParticipation(null);

            Result result = fileUploadSubmission.getResult();
            if (result != null && !authCheckService.isAtLeastInstructorForExercise(fileUploadExercise)) {
                result.setAssessor(null);
            }

            participation.addSubmissions(fileUploadSubmission);
        }

        if (!authCheckService.isAtLeastInstructorForExercise(fileUploadExercise)) {
            participation.setStudent(null);
        }

        return ResponseEntity.ok(participation);
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
        if (!courseService.userHasAtLeastStudentPermissions(course)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return null;
    }

    /**
     * Removes sensitive information (e.g. example solution of the exercise) from the submission based on the role of the current user. This should be called before sending a
     * submission to the client. IMPORTANT: Do not call this method from a transactional context as this would remove the sensitive information also from the entities in the
     * database without explicitly saving them.
     */
    private void hideDetails(FileUploadSubmission fileUploadSubmission) {
        // do not send old submissions or old results to the client
        if (fileUploadSubmission.getParticipation() != null) {
            fileUploadSubmission.getParticipation().setSubmissions(null);
            fileUploadSubmission.getParticipation().setResults(null);

            Exercise exercise = fileUploadSubmission.getParticipation().getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                    exercise.filterSensitiveInformation();
                    fileUploadSubmission.setResult(null);
                }
                // remove information about the student from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
                    StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
                    studentParticipation.setStudent(null);
                }
            }
        }
    }

    private void checkAuthorization(FileUploadExercise exercise) throws AccessForbiddenException {
        if (!authCheckService.isAtLeastStudentForExercise(exercise)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }
}
