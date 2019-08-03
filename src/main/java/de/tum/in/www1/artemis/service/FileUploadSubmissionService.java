package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;

import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.repository.*;

@Service
@Transactional
public class FileUploadSubmissionService extends SubmissionService {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionService.class);

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final ParticipationService participationService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public FileUploadSubmissionService(FileUploadSubmissionRepository fileUploadSubmissionRepository, SubmissionRepository submissionRepository, ResultService resultService,
            ResultRepository resultRepository, ParticipationService participationService, UserService userService, StudentParticipationRepository studentParticipationRepository,
            SimpMessageSendingOperations messagingTemplate) {
        super(submissionRepository, userService);
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.resultService = resultService;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles text submissions sent from the client and saves them in the database.
     *
     * @param fileUploadSubmission the file upload submission that should be saved
     * @param fileUploadExercise   the corresponding file upload exercise
     * @param principal      the user principal
     * @return the saved text submission
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
     * Saves the given submission. Is used for creating and updating file upload submissions. Rolls back if inserting fails - occurs for concurrent createTextSubmission() calls.
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
}
