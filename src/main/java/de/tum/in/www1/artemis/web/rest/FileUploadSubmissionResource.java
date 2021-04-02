package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.IOException;
import java.security.Principal;
import java.util.*;

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
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing FileUploadSubmission.
 */
@RestController
@RequestMapping("/api")
public class FileUploadSubmissionResource extends AbstractSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionResource.class);

    private static final String ENTITY_NAME = "fileUploadSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ExamSubmissionService examSubmissionService;

    public FileUploadSubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, FileUploadSubmissionService fileUploadSubmissionService,
            FileUploadExerciseRepository fileUploadExerciseRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            ExerciseRepository exerciseRepository, GradingCriterionRepository gradingCriterionRepository, ExamSubmissionService examSubmissionService,
            StudentParticipationRepository studentParticipationRepository, FileUploadSubmissionRepository fileUploadSubmissionRepository) {
        super(submissionRepository, resultService, authCheckService, userRepository, exerciseRepository, fileUploadSubmissionService, studentParticipationRepository);
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examSubmissionService = examSubmissionService;
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
    }

    /**
     * POST /exercises/{exerciseId}/file-upload-submissions : Create a new fileUploadSubmission
     *
     * @param exerciseId the id of the exercise of the submission
     * @param principal the user principal, i.e. the identity of the logged in user - provided by Spring
     * @param fileUploadSubmission the fileUploadSubmission to create
     * @param file The uploaded file belonging to the submission
     *
     * @return the ResponseEntity with status 200 and with body the new fileUploadSubmission, or with status 400 (Bad Request) if the fileUploadSubmission has already an
     * ID
     */
    @PostMapping(value = "/exercises/{exerciseId}/file-upload-submissions")
    @PreAuthorize("hasAnyRole('USER','TA','INSTRUCTOR','ADMIN')")
    public ResponseEntity<FileUploadSubmission> createFileUploadSubmission(@PathVariable long exerciseId, Principal principal,
            @RequestPart("submission") FileUploadSubmission fileUploadSubmission, @RequestPart("file") MultipartFile file) {
        log.debug("REST request to submit new FileUploadSubmission : {}", fileUploadSubmission);
        long start = System.currentTimeMillis();

        final var exercise = fileUploadExerciseRepository.findOne(exerciseId);
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastStudentForExercise(exercise, user)) {
            return forbidden();
        }

        // Make sure that the exercise exists
        if (exercise == null) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "submission", "exerciseNotFound", "No exercise was found for the given ID.")).body(null);
        }

        // Apply further checks if it is an exam submission
        Optional<ResponseEntity<FileUploadSubmission>> examSubmissionAllowanceFailure = examSubmissionService.checkSubmissionAllowance(exercise, user);
        if (examSubmissionAllowanceFailure.isPresent()) {
            return examSubmissionAllowanceFailure.get();
        }

        // Prevent multiple submissions (currently only for exam submissions)
        fileUploadSubmission = (FileUploadSubmission) examSubmissionService.preventMultipleSubmissions(exercise, fileUploadSubmission, user);

        // Check if the user is allowed to submit
        Optional<ResponseEntity<FileUploadSubmission>> submissionAllowanceFailure = fileUploadSubmissionService.checkSubmissionAllowance(exercise, fileUploadSubmission, user);
        if (submissionAllowanceFailure.isPresent()) {
            return submissionAllowanceFailure.get();
        }

        // Check the file size
        if (file.getSize() > Constants.MAX_UPLOAD_FILESIZE_BYTES) {
            // NOTE: Maximum file size for submission is 4 MB
            return ResponseEntity.status(413).headers(HeaderUtil.createAlert(applicationName, "The maximum file size is 4 MB!", "fileUploadSubmissionFileTooBig")).build();
        }

        // Check the pattern
        final var splittedFileName = file.getOriginalFilename().split("\\.");
        final var fileSuffix = splittedFileName[splittedFileName.length - 1].toLowerCase();
        final var filePattern = String.join("|", exercise.getFilePattern().toLowerCase().replaceAll("\\s", "").split(","));
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
        log.info("submitFileUploadExercise took " + (end - start) + "ms for exercise " + exerciseId + " and user " + user.getLogin());
        return ResponseEntity.ok(submission);
    }

    /**
     * GET /file-upload-submissions/:id : get the fileUploadSubmissions by it's id. Is used by tutor when assessing submissions.
     * In case an instructors calls, the resultId is used first. If the resultId is not set, the correctionRound is used.
     * If neither resultId nor correctionRound is set, the first correctionRound is used.
     *
     * @param submissionId the id of the fileUploadSubmission to retrieve
     * @param correctionRound the correctionRound of the result we want to receive
     * @param resultId the resultId for which we want to get the submission
     * @return the ResponseEntity with status 200 (OK) and with body the fileUploadSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/file-upload-submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('TA','INSTRUCTOR','ADMIN')")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestParam(value = "resultId", required = false) Long resultId) {
        log.debug("REST request to get FileUploadSubmission with id: {}", submissionId);
        var fileUploadSubmission = fileUploadSubmissionRepository.findOne(submissionId);
        var studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        var fileUploadExercise = (FileUploadExercise) studentParticipation.getExercise();

        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAllowedToAssesExercise(fileUploadExercise, user, resultId)) {
            return forbidden();
        }

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
     * GET /file-upload-submissions : get all the fileUploadSubmissions for an exercise. It is possible to filter, to receive only the one that have been already submitted, or only the one
     * assessed by the tutor who is doing the call.
     * In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId the id of the exercise
     * @param correctionRound get submission with results for the given correction round
     * @param submittedOnly if only submitted submissions should be returned
     * @param assessedByTutor if the submission was assessed by calling tutor
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Submissions in body
     */
    @GetMapping("/exercises/{exerciseId}/file-upload-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    // TODO: separate this into 2 calls, one for instructors (with all submissions) and one for tutors (only the submissions for the requesting tutor)
    public ResponseEntity<List<Submission>> getAllFileUploadSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get all file upload submissions");
        return super.getAllSubmissions(exerciseId, submittedOnly, assessedByTutor, correctionRound);
    }

    /**
     * GET /file-upload-submission-without-assessment : get one File Upload Submission without assessment.
     *
     * @param exerciseId the id of the exercise
     * @param correctionRound the correctionround for which we want to find the submission
     * @param lockSubmission specifies if the submission should be locked for assessor
     * @return the ResponseEntity with status 200 (OK) and the list of File Upload Submissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/file-upload-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadSubmission> getFileUploadSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get a file upload submission without assessment");
        final Exercise fileUploadExercise = exerciseRepository.findByIdElseThrow(exerciseId);
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        fileUploadExercise.setGradingCriteria(gradingCriteria);
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise, user)) {
            return forbidden();
        }
        if (!(fileUploadExercise instanceof FileUploadExercise)) {
            return badRequest();
        }

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

            if (optionalFileUploadSubmission.isEmpty()) {
                return notFound();
            }
            fileUploadSubmission = optionalFileUploadSubmission.get();
        }

        // Make sure the exercise is connected to the participation in the json response
        final StudentParticipation studentParticipation = (StudentParticipation) fileUploadSubmission.getParticipation();
        studentParticipation.setExercise(fileUploadExercise);
        fileUploadSubmission.getParticipation().getExercise().setGradingCriteria(gradingCriteria);
        this.fileUploadSubmissionService.hideDetails(fileUploadSubmission, user);
        return ResponseEntity.ok(fileUploadSubmission);
    }

    /**
     * Returns the data needed for the file upload editor, which includes the participation, fileUploadSubmission with answer if existing and the assessments if the submission
     * was already submitted.
     *
     * @param participationId the participationId for which to find the data for the file upload editor
     * @return the ResponseEntity with the participation as body
     */
    @GetMapping("/participations/{participationId}/file-upload-editor")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FileUploadSubmission> getDataForFileUpload(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdWithSubmissionsResultsFeedbackElseThrow(participationId);
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

        Optional<Submission> optionalSubmission = participation.findLatestSubmission();
        FileUploadSubmission fileUploadSubmission;
        if (optionalSubmission.isEmpty()) {
            // this should never happen as the submission is initialized along with the participation when the exercise is started
            fileUploadSubmission = new FileUploadSubmission();
            fileUploadSubmission.setParticipation(participation);
        }
        else {
            // only try to get and set the file upload if the fileUploadSubmission existed before
            fileUploadSubmission = (FileUploadSubmission) optionalSubmission.get();
        }

        // make sure only the latest submission and latest result is sent to the client
        participation.setSubmissions(null);
        participation.setResults(null);

        // do not send the result to the client if the assessment is not finished
        if (fileUploadSubmission.getLatestResult() != null
                && (fileUploadSubmission.getLatestResult().getCompletionDate() == null || fileUploadSubmission.getLatestResult().getAssessor() == null)) {
            fileUploadSubmission.setResults(new ArrayList<Result>());
        }

        // do not send the assessor information to students
        if (fileUploadSubmission.getLatestResult() != null && !authCheckService.isAtLeastTeachingAssistantForExercise(fileUploadExercise)) {
            fileUploadSubmission.getLatestResult().setAssessor(null);
        }

        return ResponseEntity.ok(fileUploadSubmission);
    }
}
