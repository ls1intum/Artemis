package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FileUploadSubmissionService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing FileUploadSubmission.
 */
@RestController
@RequestMapping("api/")
public class FileUploadSubmissionResource extends AbstractSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ExamSubmissionService examSubmissionService;

    private final SingleUserNotificationService singleUserNotificationService;

    public FileUploadSubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, FileUploadSubmissionService fileUploadSubmissionService,
            FileUploadExerciseRepository fileUploadExerciseRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            ExerciseRepository exerciseRepository, GradingCriterionRepository gradingCriterionRepository, ExamSubmissionService examSubmissionService,
            StudentParticipationRepository studentParticipationRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository,
            SingleUserNotificationService singleUserNotificationService) {
        super(submissionRepository, resultService, authCheckService, userRepository, exerciseRepository, fileUploadSubmissionService, studentParticipationRepository);
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examSubmissionService = examSubmissionService;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.singleUserNotificationService = singleUserNotificationService;
    }

    /**
     * POST exercises/:exerciseId/file-upload-submissions : Create a new fileUploadSubmission
     *
     * @param exerciseId of the file upload exercise a submission should be created for
     * @param principal the identity of the logged-in user - provided by Spring
     * @param fileUploadSubmission the fileUploadSubmission to create
     * @param file The uploaded file belonging to the submission
     *
     * @return the ResponseEntity with status 200 and with body the new fileUploadSubmission, or with status 400 (Bad Request) if the fileUploadSubmission has already an
     * ID
     */
    @PostMapping("exercises/{exerciseId}/file-upload-submissions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<FileUploadSubmission> createFileUploadSubmission(@PathVariable long exerciseId, Principal principal,
            @RequestPart("submission") FileUploadSubmission fileUploadSubmission, @RequestPart("file") MultipartFile file) {
        log.debug("REST request to submit new FileUploadSubmission : {}", fileUploadSubmission);
        long start = System.currentTimeMillis();

        final var exercise = fileUploadExerciseRepository.findByIdElseThrow(exerciseId);
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        // if there is a participation that has an exercise linked to it,
        // the exercise needs to be the same as the one referenced in the path via exerciseId
        if (fileUploadSubmission.getParticipation() != null && fileUploadSubmission.getParticipation().getExercise() != null
                && !fileUploadSubmission.getParticipation().getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("ExerciseId in Body doesn't match ExerciseId in path!", "exerciseId", "400");
        }

        // Apply further checks if it is an exam submission
        examSubmissionService.checkSubmissionAllowanceElseThrow(exercise, user);

        // Prevent multiple submissions (currently only for exam submissions)
        fileUploadSubmission = (FileUploadSubmission) examSubmissionService.preventMultipleSubmissions(exercise, fileUploadSubmission, user);

        // Check if the user is allowed to submit
        fileUploadSubmissionService.checkSubmissionAllowanceElseThrow(exercise, fileUploadSubmission, user);

        // Check the file size
        if (file.getSize() > Constants.MAX_SUBMISSION_FILE_SIZE) {
            // NOTE: Maximum file size for submission is 8 MB
            return ResponseEntity.status(413).headers(HeaderUtil.createAlert(applicationName, "The maximum file size is 8 MB!", "fileUploadSubmissionFileTooBig")).build();
        }

        // Check the pattern
        final String[] splittedFileName = file.getOriginalFilename().split("\\.");
        final String fileSuffix = splittedFileName[splittedFileName.length - 1].toLowerCase();
        final String filePattern = String.join("|", exercise.getFilePattern().toLowerCase().replaceAll("\\s", "").split(","));
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
        long end = System.currentTimeMillis();
        singleUserNotificationService.notifyUserAboutSuccessfulFileUploadSubmission(exercise, user);
        log.info("submitFileUploadExercise took {}ms for exercise {} and user {}", end - start, exerciseId, user.getLogin());
        return ResponseEntity.ok(submission);
    }

    /**
     * GET file-upload-submissions/:submissionId : get the fileUploadSubmissions by its id. Is used by tutors when assessing submissions.
     * In case an instructor calls, the resultId is used first. If the resultId is not set, the correctionRound is used.
     * If neither resultId nor correctionRound is set, the first correctionRound is used.
     *
     * @param submissionId of the fileUploadSubmission to retrieve
     * @param correctionRound of the result we want to receive
     * @param resultId for which we want to get the submission
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadSubmission, or with status 404 (Not Found)
     */
    @GetMapping("file-upload-submissions/{submissionId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestParam(value = "resultId", required = false) Long resultId) {
        log.debug("REST request to get FileUploadSubmission with id: {}", submissionId);
        var fileUploadSubmission = fileUploadSubmissionRepository.findByIdElseThrow(submissionId);
        var studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        var fileUploadExercise = (FileUploadExercise) studentParticipation.getExercise();

        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkIsAllowedToAssessExerciseElseThrow(fileUploadExercise, user, resultId);

        // load submission with results either by resultId or by correctionRound
        if (resultId != null) {
            // load the submission with additional needed properties by resultId
            fileUploadSubmission = (FileUploadSubmission) submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
            // check if result with the requested id exists
            Result result = fileUploadSubmission.getManualResultsById(resultId);
            if (result == null) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, "FileUploadSubmission", "ResultNotFound", "No Result was found for the given ID."))
                        .body(null);
            }
        }
        else {
            // load and potentially lock the submission with additional needed properties by correctionRound
            fileUploadSubmission = fileUploadSubmissionService.lockAndGetFileUploadSubmission(submissionId, fileUploadExercise, correctionRound);
        }

        // Make sure the exercise is connected to the participation in the json response
        studentParticipation.setExercise(fileUploadExercise);
        var gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(fileUploadExercise.getId());
        fileUploadExercise.setGradingCriteria(gradingCriteria);
        fileUploadSubmission.getParticipation().getExercise().setGradingCriteria(gradingCriteria);

        // prepare fileUploadSubmission for response
        fileUploadSubmissionService.hideDetails(fileUploadSubmission, user);
        fileUploadSubmission.removeNotNeededResults(correctionRound, resultId);
        return ResponseEntity.ok(fileUploadSubmission);
    }

    /**
     * GET exercises/:exerciseId/file-upload-submissions : get all the fileUploadSubmissions for an exercise. It is possible to filter, to receive only the one that have been already submitted, or only the one
     * assessed by the tutor who is doing the call.
     * In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId the id of the exercise
     * @param correctionRound get submission with results for the given correction round
     * @param submittedOnly if only submitted submissions should be returned
     * @param assessedByTutor if the submission was assessed by calling tutor
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Submissions in body
     */
    @GetMapping("exercises/{exerciseId}/file-upload-submissions")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Submission>> getAllFileUploadSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get all file upload submissions");
        return super.getAllSubmissions(exerciseId, submittedOnly, assessedByTutor, correctionRound);
    }

    /**
     * GET exercises/:exerciseId/file-upload-submission-without-assessment : get one File Upload Submission without assessment.
     *
     * @param exerciseId of the exercise
     * @param correctionRound for which we want to find the submission
     * @param lockSubmission specifies if the submission should be locked for assessor
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Submissions in body
     */
    @GetMapping("exercises/{exerciseId}/file-upload-submission-without-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get a file upload submission without assessment");
        final Exercise fileUploadExercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!(fileUploadExercise instanceof FileUploadExercise)) {
            throw new BadRequestAlertException("The requested exercise was not found.", "exerciseId", "400");
        }
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        fileUploadExercise.setGradingCriteria(gradingCriteria);
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, fileUploadExercise, user);

        // Check if tutors can start assessing the students submission
        this.fileUploadSubmissionService.checkIfExerciseDueDateIsReached(fileUploadExercise);

        // Check if the limit of simultaneously locked submissions has been reached
        fileUploadSubmissionService.checkSubmissionLockLimit(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId());

        final FileUploadSubmission fileUploadSubmission;
        if (lockSubmission) {
            fileUploadSubmission = fileUploadSubmissionService.lockAndGetFileUploadSubmissionWithoutResult((FileUploadExercise) fileUploadExercise,
                    fileUploadExercise.isExamExercise(), correctionRound);
        }
        else {
            Optional<FileUploadSubmission> optionalFileUploadSubmission = fileUploadSubmissionService
                    .getRandomFileUploadSubmissionEligibleForNewAssessment((FileUploadExercise) fileUploadExercise, fileUploadExercise.isExamExercise(), correctionRound);
            fileUploadSubmission = optionalFileUploadSubmission.orElseThrow(() -> new EntityNotFoundException("File Upload Submission without Assessment"));
        }

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        studentParticipation.setExercise(fileUploadExercise);
        fileUploadSubmission.getParticipation().getExercise().setGradingCriteria(gradingCriteria);
        this.fileUploadSubmissionService.hideDetails(fileUploadSubmission, user);
        return ResponseEntity.ok(fileUploadSubmission);
    }

    /**
     * GET participations/:participationId/file-upload-editor : Returns the data needed for the file upload editor, which includes the participation, fileUploadSubmission with answer if existing and the assessments if the submission
     * was already submitted.
     *
     * @param participationId for which to find the data for the file upload editor
     * @return the ResponseEntity with the File Upload Submission as body
     */
    @GetMapping("participations/{participationId}/file-upload-editor")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<FileUploadSubmission> getDataForFileUpload(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdWithLegalSubmissionsResultsFeedbackElseThrow(participationId);
        FileUploadExercise fileUploadExercise;
        if (participation.getExercise() instanceof FileUploadExercise) {
            fileUploadExercise = (FileUploadExercise) participation.getExercise();
            // make sure sensitive information are not sent to the client
            fileUploadExercise.filterSensitiveInformation();
        }
        else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, "fileUploadExercise", "wrongExerciseType",
                    "The exercise of the participation is not a file upload exercise.")).body(null);
        }

        // Students can only see their own file uploads (to prevent cheating). TAs, instructors and admins can see all file uploads.
        if (!(authCheckService.isOwnerOfParticipation(participation) || authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise))) {
            throw new AccessForbiddenException("participation", participationId);
        }

        Optional<FileUploadSubmission> optionalSubmission = participation.findLatestSubmission();
        FileUploadSubmission fileUploadSubmission = optionalSubmission.orElseGet(() -> {
            FileUploadSubmission tempSubmission = new FileUploadSubmission();
            tempSubmission.setParticipation(participation);
            return tempSubmission;
        });

        // make sure only the latest submission and latest result is sent to the client
        participation.setSubmissions(null);
        participation.setResults(null);

        if (fileUploadSubmission.getLatestResult() != null) {
            // do not send the feedback to the client
            // if the assessment is not finished
            boolean assessmentUnfinished = fileUploadSubmission.getLatestResult().getCompletionDate() == null || fileUploadSubmission.getLatestResult().getAssessor() == null;
            // or the assessment due date isn't over yet
            boolean assessmentDueDateNotOver = fileUploadExercise.getAssessmentDueDate() != null && fileUploadExercise.getAssessmentDueDate().isAfter(ZonedDateTime.now());

            if (assessmentUnfinished || assessmentDueDateNotOver) {
                fileUploadSubmission.getLatestResult().setFeedbacks(new ArrayList<>());
            }
        }

        // do not send the assessor information to students
        if (fileUploadSubmission.getLatestResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise)) {
            fileUploadSubmission.getLatestResult().setAssessor(null);
        }

        return ResponseEntity.ok(fileUploadSubmission);
    }
}
