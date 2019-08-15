package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class FileUploadSubmissionService extends SubmissionService {

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final ResultRepository resultRepository;

    private final ParticipationService participationService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public FileUploadSubmissionService(FileUploadSubmissionRepository fileUploadSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            ParticipationService participationService, UserService userService, StudentParticipationRepository studentParticipationRepository,
            SimpMessageSendingOperations messagingTemplate) {
        super(submissionRepository, userService);
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles file upload submissions sent from the client and saves them in the database.
     *
     * @param fileUploadSubmission the file upload submission that should be saved
     * @param fileUploadExercise   the corresponding file upload exercise
     * @param principal      the user principal
     * @return the saved file upload submission
     */
    @Transactional
    public FileUploadSubmission handleFileUploadSubmission(FileUploadSubmission fileUploadSubmission, FileUploadExercise fileUploadExercise, Principal principal) {
        if (fileUploadSubmission.isExampleSubmission() == Boolean.TRUE) {
            fileUploadSubmission = save(fileUploadSubmission);
        }
        else {
            Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginAnyState(fileUploadExercise.getId(), principal.getName());
            if (!optionalParticipation.isPresent()) {
                throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + fileUploadExercise.getId());
            }
            StudentParticipation participation = optionalParticipation.get();

            if (participation.getInitializationState() == InitializationState.FINISHED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot submit more than once");
            }

            fileUploadSubmission = save(fileUploadSubmission, participation);
        }
        return fileUploadSubmission;
    }

    /**
     * Given an exerciseId, returns all the file upload submissions for that exercise, including their results. Submissions can be filtered to include only already submitted
     * submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @return a list of file upload submissions for the given exercise id
     */
    @Transactional(readOnly = true)
    public List<FileUploadSubmission> getFileUploadSubmissions(Long exerciseId, boolean submittedOnly) {
        List<StudentParticipation> participations = studentParticipationRepository.findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(exerciseId);
        List<FileUploadSubmission> submissions = new ArrayList<>();
        for (StudentParticipation participation : participations) {
            Optional<FileUploadSubmission> submission = participation.findLatestFileUploadSubmission();
            if (submission.isPresent()) {
                if (submittedOnly && !submission.get().isSubmitted()) {
                    // filter out non submitted submissions if the flag is set to true
                    continue;
                }
                submissions.add(submission.get());
            }
            // avoid infinite recursion
            participation.getExercise().setParticipations(null);
        }
        return submissions;
    }

    /**
     * Given an exercise id and a tutor id, it returns all the file upload submissions where the tutor has a result associated
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param tutorId    - the id of the tutor we are interested in
     * @return a list of file upload Submissions
     */
    @Transactional(readOnly = true)
    public List<FileUploadSubmission> getAllFileUploadSubmissionsByTutorForExercise(Long exerciseId, Long tutorId) {
        // We take all the results in this exercise associated to the tutor, and from there we retrieve the submissions
        List<Result> results = this.resultRepository.findAllByParticipationExerciseIdAndAssessorId(exerciseId, tutorId);

        return results.stream().map(result -> {
            Submission submission = result.getSubmission();
            FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();

            result.setSubmission(null);
            fileUploadSubmission.setLanguage(submission.getLanguage());
            fileUploadSubmission.setResult(result);
            fileUploadSubmission.setParticipation(submission.getParticipation());
            fileUploadSubmission.setId(submission.getId());
            fileUploadSubmission.setSubmissionDate(submission.getSubmissionDate());

            return fileUploadSubmission;
        }).collect(Collectors.toList());
    }

    /**
     * Given an exercise id, find a random file upload submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param fileUploadExercise the exercise for which we want to retrieve a submission without manual result
     * @return a fileUploadSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    @Transactional(readOnly = true)
    public Optional<FileUploadSubmission> getFileUploadSubmissionWithoutManualResult(FileUploadExercise fileUploadExercise) {
        Random r = new Random();
        List<FileUploadSubmission> submissionsWithoutResult = participationService.findByExerciseIdWithEagerSubmittedSubmissionsWithoutManualResults(fileUploadExercise.getId())
                .stream().map(StudentParticipation::findLatestFileUploadSubmission).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())));
    }

    /**
     * Creates a new Result object, assigns it to the given submission and stores the changes to the database. Note, that this method is also called for example submissions which
     * do not have a participation. Therefore, we check if the given submission has a participation and only then update the participation with the new result.
     *
     * @param submission the submission for which a new result should be created
     * @return the newly created result
     */
    public Result setNewResult(FileUploadSubmission submission) {
        Result result = new Result();
        result.setSubmission(submission);
        submission.setResult(result);
        if (submission.getParticipation() != null) {
            submission.getParticipation().addResult(result);
        }
        resultRepository.save(result);
        fileUploadSubmissionRepository.save(submission);
        return result;
    }

    /**
     * Saves the given submission. Is used for creating and updating file upload submissions. Rolls back if inserting fails - occurs for concurrent createFileUploadSubmission() calls.
     *
     * @param fileUploadSubmission the submission that should be saved
     * @param participation  the participation the participation the submission belongs to
     * @return the fileUploadSubmission entity that was saved to the database
     */
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSubmission save(FileUploadSubmission fileUploadSubmission, StudentParticipation participation) {
        // update submission properties
        fileUploadSubmission.setSubmissionDate(ZonedDateTime.now());
        fileUploadSubmission.setType(SubmissionType.MANUAL);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);

        participation.addSubmissions(fileUploadSubmission);

        User user = participation.getStudent();

        if (fileUploadSubmission.isSubmitted()) {
            participation.setInitializationState(InitializationState.FINISHED);

            messagingTemplate.convertAndSendToUser(participation.getStudent().getLogin(), "/topic/exercise/" + participation.getExercise().getId() + "/participation",
                    participation);
        }
        StudentParticipation savedParticipation = studentParticipationRepository.save(participation);
        if (fileUploadSubmission.getId() == null) {
            Optional<FileUploadSubmission> optionalFileUploadSubmission = savedParticipation.findLatestFileUploadSubmission();
            if (optionalFileUploadSubmission.isPresent()) {
                fileUploadSubmission = optionalFileUploadSubmission.get();
            }
        }

        return fileUploadSubmission;
    }

    /**
     * The same as `save()`, but without participation, is used by example submission, which aren't linked to any participation
     *
     * @param fileUploadSubmission the submission to notifyCompass
     * @return the fileUploadSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSubmission save(FileUploadSubmission fileUploadSubmission) {
        fileUploadSubmission.setSubmissionDate(ZonedDateTime.now());
        fileUploadSubmission.setType(SubmissionType.MANUAL);

        // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
        if (fileUploadSubmission.getResult() != null && fileUploadSubmission.getResult().getSubmission() == null) {
            fileUploadSubmission.getResult().setSubmission(fileUploadSubmission);
        }

        fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);

        return fileUploadSubmission;
    }

    /**
     * @param courseId the course we are interested in
     * @return the number of file upload submissions which should be assessed, so we ignore the ones after the exercise due date
     */
    @Transactional(readOnly = true)
    public long countSubmissionsToAssessByCourseId(Long courseId) {
        return fileUploadSubmissionRepository.countByCourseIdSubmittedBeforeDueDate(courseId);
    }

    /**
     * @param exerciseId the exercise we are interested in
     * @return the number of file upload submissions which should be assessed, so we ignore the ones after the exercise due date
     */
    @Transactional(readOnly = true)
    public long countSubmissionsToAssessByExerciseId(Long exerciseId) {
        return fileUploadSubmissionRepository.countByExerciseIdSubmittedBeforeDueDate(exerciseId);
    }

    /**
     * Get the file upload submission with the given id from the database. The submission is loaded together with its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    public FileUploadSubmission findOneWithEagerResultAndFeedback(Long submissionId) {
        return fileUploadSubmissionRepository.findByIdWithEagerResultAndFeedback(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("File Upload submission with id \"" + submissionId + "\" does not exist"));
    }

    /**
     * Get the file upload submission with the given id from the database. The submission is loaded together with its result and the assessor. Throws an EntityNotFoundException if no
     * submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    public FileUploadSubmission findOneWithEagerResult(Long submissionId) {
        return fileUploadSubmissionRepository.findByIdWithEagerResult(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("File Upload submission with id \"" + submissionId + "\" does not exist"));
    }
}
