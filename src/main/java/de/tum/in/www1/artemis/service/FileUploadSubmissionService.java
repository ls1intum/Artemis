package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.EmptyFileException;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class FileUploadSubmissionService extends SubmissionService {

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileService fileService;

    public FileUploadSubmissionService(FileUploadSubmissionRepository fileUploadSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            ParticipationService participationService, UserService userService, StudentParticipationRepository studentParticipationRepository, FileService fileService,
            AuthorizationCheckService authCheckService, CourseService courseService, ExamService examService) {
        super(submissionRepository, userService, authCheckService, courseService, resultRepository, examService, studentParticipationRepository, participationService);
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.resultRepository = resultRepository;
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
     * @throws EmptyFileException if file is empty
     */
    public FileUploadSubmission handleFileUploadSubmission(FileUploadSubmission fileUploadSubmission, MultipartFile file, FileUploadExercise fileUploadExercise,
            Principal principal) throws IOException, EmptyFileException {
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(fileUploadExercise, principal.getName());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + fileUploadExercise.getId());
        }
        StudentParticipation participation = optionalParticipation.get();

        return save(fileUploadSubmission, file, participation, fileUploadExercise);
    }

    /**
     * Given an exercise id, find a random file upload submission for that exercise which still doesn't have any manual result.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     * For exam exercises we should also remove the test run participations as these should not be graded by the tutors.
     *
     * @param fileUploadExercise the exercise for which we want to retrieve a submission without manual result
     * @param examMode flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a fileUploadSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<FileUploadSubmission> getRandomFileUploadSubmissionEligibleForNewAssessment(FileUploadExercise fileUploadExercise, boolean examMode) {
        var submissionWithoutResult = super.getRandomSubmissionEligibleForNewAssessment(fileUploadExercise, examMode);
        if (submissionWithoutResult.isPresent()) {
            FileUploadSubmission fileUploadSubmission = (FileUploadSubmission) submissionWithoutResult.get();
            return Optional.of(fileUploadSubmission);
        }
        return Optional.empty();
    }

    /**
     * Saves the given submission. Is used for creating and updating file upload submissions. Rolls back if inserting fails - occurs for concurrent createFileUploadSubmission() calls.
     *
     * @param fileUploadSubmission the submission that should be saved
     * @param file                 the file that will be saved on the server
     * @param participation        the participation the submission belongs to
     * @param exercise             the exercise the submission belongs to
     * @return the fileUploadSubmission entity that was saved to the database
     * @throws IOException if file can't be saved
     * @throws EmptyFileException if file is empty
     */
    public FileUploadSubmission save(FileUploadSubmission fileUploadSubmission, MultipartFile file, StudentParticipation participation, FileUploadExercise exercise)
            throws IOException, EmptyFileException {
        final var exerciseDueDate = exercise.getDueDate();
        if (exerciseDueDate != null && exerciseDueDate.isBefore(ZonedDateTime.now()) && participation.getInitializationDate().isBefore(exerciseDueDate)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (file.isEmpty()) {
            throw new EmptyFileException(file.getOriginalFilename());
        }

        final var multipartFileHash = DigestUtils.md5Hex(file.getInputStream());
        // We need to set id for newly created submissions
        if (fileUploadSubmission.getId() == null) {
            fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);
        }
        final var localPath = saveFileForSubmission(file, fileUploadSubmission, exercise);

        // We need to ensure that we can access the store file and the stored file is the same as was passed to us in the request
        final var storedFileHash = DigestUtils.md5Hex(Files.newInputStream(Path.of(localPath)));
        if (!multipartFileHash.equals(storedFileHash)) {
            throw new IOException("The file " + file.getName() + "could not be stored");
        }

        // check if we already had file associated with this submission
        fileUploadSubmission.onDelete();

        // update submission properties
        // NOTE: from now on we always set submitted to true to prevent problems here!
        fileUploadSubmission.setSubmitted(true);
        fileUploadSubmission.setSubmissionDate(ZonedDateTime.now());
        fileUploadSubmission.setType(SubmissionType.MANUAL);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);
        fileUploadSubmission.setFilePath(fileService.publicPathForActualPath(localPath, fileUploadSubmission.getId()));
        fileUploadSubmissionRepository.save(fileUploadSubmission);

        participation.addSubmissions(fileUploadSubmission);
        participation.setInitializationState(InitializationState.FINISHED);
        StudentParticipation savedParticipation = studentParticipationRepository.save(participation);
        if (fileUploadSubmission.getId() == null) {
            Optional<Submission> optionalSubmission = savedParticipation.findLatestSubmission();
            if (optionalSubmission.isPresent()) {
                fileUploadSubmission = (FileUploadSubmission) optionalSubmission.get();
            }
        }

        return fileUploadSubmission;
    }

    private String saveFileForSubmission(final MultipartFile file, final Submission submission, FileUploadExercise exercise) throws IOException {
        final var exerciseId = exercise.getId();
        final var submissionId = submission.getId();
        var filename = file.getOriginalFilename();
        if (filename.contains("\\")) {
            // this can happen on windows computers, then we want to take the last element of the file path
            var components = filename.split("\\\\");
            filename = components[components.length - 1];
        }
        // replace all illegal characters with ascii characters \w means A-Za-z0-9 to avoid problems during download later on
        filename = filename.replaceAll("[^\\w.-]", "");
        // if the filename is now too short, we prepend "file"
        // this prevents potential problems when users call their file e.g. ßßß.pdf
        if (filename.length() < 5) {
            filename = "file" + filename;
        }
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
     * Get the file upload submission with the given ID from the database and lock the submission to prevent other tutors from receiving and assessing it. Additionally, check if the
     * submission lock limit has been reached.
     *
     * @param submissionId       the id of the file upload submission
     * @param fileUploadExercise the corresponding exercise
     * @return the locked file upload submission
     */
    public FileUploadSubmission getLockedFileUploadSubmission(Long submissionId, FileUploadExercise fileUploadExercise) {
        FileUploadSubmission fileUploadSubmission = findOneWithEagerResultAndFeedbackAndAssessorAndParticipationResults(submissionId);

        if (fileUploadSubmission.getResult() == null || fileUploadSubmission.getResult().getAssessor() == null) {
            checkSubmissionLockLimit(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        }

        lockSubmission(fileUploadSubmission);
        return fileUploadSubmission;
    }

    /**
     * Get a file upload submission of the given exercise that still needs to be assessed and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param fileUploadExercise the exercise the submission should belong to
     * @param removeTestRunParticipations flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a locked file upload submission that needs an assessment
     */
    public FileUploadSubmission lockAndGetFileUploadSubmissionWithoutResult(FileUploadExercise fileUploadExercise, boolean removeTestRunParticipations) {
        FileUploadSubmission fileUploadSubmission = getRandomFileUploadSubmissionEligibleForNewAssessment(fileUploadExercise, removeTestRunParticipations)
                .orElseThrow(() -> new EntityNotFoundException("File upload submission for exercise " + fileUploadExercise.getId() + " could not be found"));
        lockSubmission(fileUploadSubmission);
        return fileUploadSubmission;
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
     * Get the file upload submission with the given id from the database. The submission is loaded together with its result, the feedback of the result, the assessor of the result,
     * its participation and all results of the participation. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    private FileUploadSubmission findOneWithEagerResultAndFeedbackAndAssessorAndParticipationResults(Long submissionId) {
        return fileUploadSubmissionRepository.findWithEagerResultAndFeedbackAndAssessorAndParticipationResultsById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("File Upload submission with id \"" + submissionId + "\" does not exist"));
    }

    /**
     * Get the file upload submission with the given id from the database. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the file upload submission with the given id
     */
    public FileUploadSubmission findOne(Long submissionId) {
        return fileUploadSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("File Upload submission with id \"" + submissionId + "\" does not exist"));
    }
}
