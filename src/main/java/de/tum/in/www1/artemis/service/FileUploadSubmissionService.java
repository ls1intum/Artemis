package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class FileUploadSubmissionService extends SubmissionService<FileUploadSubmission, FileUploadSubmissionRepository> {

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionService.class);

    private final FileService fileService;

    public FileUploadSubmissionService(FileUploadSubmissionRepository fileUploadSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            ParticipationService participationService, UserService userService, StudentParticipationRepository studentParticipationRepository,
            SimpMessageSendingOperations messagingTemplate, ResultService resultService, FileService fileService, AuthorizationCheckService authCheckService) {
        super(submissionRepository, userService, authCheckService, resultRepository, participationService, messagingTemplate, studentParticipationRepository,
                fileUploadSubmissionRepository, resultService);
        this.fileService = fileService;
    }

    /**
     * Handles file upload submissions sent from the client and saves them in the database.
     *
     * @param fileUploadSubmission the file upload submission that should be saved
     * @param fileUploadExercise   the corresponding file upload exercise
     * @param file                  the file that will be stored on the server
     * @param principal            the user principal
     * @return the saved file upload submission
     * @throws IOException if file can't be saved
     */
    @Transactional(rollbackFor = Exception.class)
    public FileUploadSubmission handleFileUploadSubmission(FileUploadSubmission fileUploadSubmission, MultipartFile file, FileUploadExercise fileUploadExercise,
            Principal principal) throws IOException {
        fileUploadSubmission = save(fileUploadSubmission, fileUploadExercise, principal.getName(), FileUploadSubmission.class);
        final var localPath = saveFileForSubmission(file, fileUploadSubmission, fileUploadExercise);
        fileUploadSubmission.setFilePath(fileService.publicPathForActualPath(localPath, fileUploadSubmission.getId()));
        genericSubmissionRepository.save(fileUploadSubmission);
        return fileUploadSubmission;
    }

    private String saveFileForSubmission(final MultipartFile file, final Submission submission, FileUploadExercise exercise) throws IOException {
        final var exerciseId = exercise.getId();
        final var submissionId = submission.getId();
        final var filename = file.getOriginalFilename().replaceAll("\\s", "");
        final var dirPath = FileUploadSubmission.buildFilePath(exerciseId, submissionId);
        final var filePath = dirPath + filename;
        final var savedFile = new java.io.File(filePath);
        final var dir = new java.io.File(dirPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }
        Files.copy(file.getInputStream(), savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return filePath;
    }

    /**
     * Soft lock the submission to prevent other tutors from receiving and assessing it.  We set the assessor and save the result to soft lock the assessment in the client. If no result exists for this submission we create one first.
     *
     * @param fileUploadSubmission the submission to lock
     */
    private void lockFileUploadSubmission(FileUploadSubmission fileUploadSubmission) {
        var result = super.lockSubmission(fileUploadSubmission);
        log.debug("Assessment locked with result id: " + result.getId() + " for assessor: " + result.getAssessor().getFirstName());
    }

    /**
     * Get the file upload submission with the given ID from the database and lock the submission to prevent other tutors from receiving and assessing it. Additionally, check if the
     * submission lock limit has been reached.
     *
     * @param submissionId       the id of the file upload submission
     * @param fileUploadExercise the corresponding exercise
     * @return the locked file upload submission
     */
    @Transactional
    public FileUploadSubmission getLockedFileUploadSubmission(Long submissionId, FileUploadExercise fileUploadExercise) {
        FileUploadSubmission fileUploadSubmission = findOneWithEagerResultAndFeedbackAndAssessorAndParticipationResults(submissionId);

        if (fileUploadSubmission.getResult() == null || fileUploadSubmission.getResult().getAssessor() == null) {
            checkSubmissionLockLimit(fileUploadExercise.getCourse().getId());
        }
        lockFileUploadSubmission(fileUploadSubmission);
        return fileUploadSubmission;
    }

    /**
     * Get a file upload submission of the given exercise that still needs to be assessed and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param fileUploadExercise the exercise the submission should belong to
     * @return a locked file upload submission that needs an assessment
     */
    @Transactional
    public FileUploadSubmission getLockedFileUploadSubmissionWithoutResult(FileUploadExercise fileUploadExercise) {
        FileUploadSubmission fileUploadSubmission = getSubmissionWithoutManualResult(fileUploadExercise, FileUploadSubmission.class)
                .orElseThrow(() -> new EntityNotFoundException("File upload submission for exercise " + fileUploadExercise.getId() + " could not be found"));
        lockFileUploadSubmission(fileUploadSubmission);
        return fileUploadSubmission;
    }
}
