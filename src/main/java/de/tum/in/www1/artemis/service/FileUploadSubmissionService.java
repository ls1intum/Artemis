package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.EmptyFileException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class FileUploadSubmissionService extends SubmissionService {

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final FileService fileService;

    private final ExerciseDateService exerciseDateService;

    public FileUploadSubmissionService(FileUploadSubmissionRepository fileUploadSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            ParticipationService participationService, UserRepository userRepository, StudentParticipationRepository studentParticipationRepository, FileService fileService,
            AuthorizationCheckService authCheckService, FeedbackRepository feedbackRepository, ExamDateService examDateService, ExerciseDateService exerciseDateService,
            CourseRepository courseRepository, ParticipationRepository participationRepository, ComplaintRepository complaintRepository) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                exerciseDateService, courseRepository, participationRepository, complaintRepository);
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.fileService = fileService;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Handles file upload submissions sent from the client and saves them in the database.
     *
     * @param fileUploadSubmission the file upload submission that should be saved
     * @param exercise             the corresponding file upload exercise
     * @param file                 the file that will be stored on the server
     * @param user                 the user who initiated the save/submission
     * @return the saved file upload submission
     * @throws IOException if file can't be saved
     * @throws EmptyFileException if file is empty
     */
    public FileUploadSubmission handleFileUploadSubmission(FileUploadSubmission fileUploadSubmission, MultipartFile file, FileUploadExercise exercise, User user)
            throws IOException, EmptyFileException {
        // Don't allow submissions after the due date (except if the exercise was started after the due date)
        final var optionalParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(exercise, user.getLogin());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + user.getLogin() + " in exercise " + exercise.getId());
        }
        final var participation = optionalParticipation.get();
        final var dueDate = ExerciseDateService.getDueDate(participation);
        // Important: for exam exercises, we should NOT check the exercise due date, we only check if for course exercises
        if (dueDate.isPresent() && exerciseDateService.isAfterDueDate(participation) && participation.getInitializationDate().isBefore(dueDate.get())) {
            throw new AccessForbiddenException();
        }
        // NOTE: from now on we always set submitted to true to prevent problems here! Except for late submissions of course exercises to prevent issues in auto-save
        if (exercise.isExamExercise() || exerciseDateService.isBeforeDueDate(participation)) {
            fileUploadSubmission.setSubmitted(true);
        }

        fileUploadSubmission = save(fileUploadSubmission, file, participation, exercise);
        return fileUploadSubmission;
    }

    /**
     * Given an exercise id, find a random file upload submission for that exercise which still doesn't have any manual result.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     * For exam exercises we should also remove the test run participations as these should not be graded by the tutors.
     *
     * @param fileUploadExercise the exercise for which we want to retrieve a submission without manual result
     * @param correctionRound - the correction round we want our submission to have results for
     * @param examMode flag to determine if test runs should be ignored. This should be set to true for exam exercises
     * @return a fileUploadSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<FileUploadSubmission> getRandomFileUploadSubmissionEligibleForNewAssessment(FileUploadExercise fileUploadExercise, boolean examMode, int correctionRound) {
        var submissionWithoutResult = super.getRandomAssessableSubmission(fileUploadExercise, examMode, correctionRound);
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

        String newFilePath = storeFile(fileUploadSubmission, file, exercise);

        // update submission properties
        fileUploadSubmission.setSubmissionDate(ZonedDateTime.now());
        fileUploadSubmission.setType(SubmissionType.MANUAL);
        participation.addSubmission(fileUploadSubmission);

        if (participation.getInitializationState() != InitializationState.FINISHED) {
            participation.setInitializationState(InitializationState.FINISHED);
            studentParticipationRepository.save(participation);
        }

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        fileUploadSubmission.setResults(new ArrayList<>());

        // Note: we save before the new file path is set to potentially remove the old file on the file system
        fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);
        fileUploadSubmission.setFilePath(newFilePath);
        // Note: we save again so that the new file is stored on the file system
        fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);

        return fileUploadSubmission;
    }

    private String storeFile(FileUploadSubmission fileUploadSubmission, MultipartFile file, FileUploadExercise exercise) throws EmptyFileException, IOException {
        if (file.isEmpty()) {
            throw new EmptyFileException(file.getOriginalFilename());
        }

        final var oldFilePath = fileUploadSubmission.getFilePath();

        final var multipartFileHash = DigestUtils.md5Hex(file.getInputStream());
        // We need to set id for newly created submissions
        if (fileUploadSubmission.getId() == null) {
            fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);
        }
        final var newLocalFilePath = saveFileForSubmission(file, fileUploadSubmission, exercise);
        final var newFilePath = fileService.publicPathForActualPath(newLocalFilePath, fileUploadSubmission.getId());

        // We need to ensure that we can access the store file and the stored file is the same as was passed to us in the request
        final var storedFileHash = DigestUtils.md5Hex(Files.newInputStream(Path.of(newLocalFilePath)));
        if (!multipartFileHash.equals(storedFileHash)) {
            throw new IOException("The file " + file.getName() + "could not be stored");
        }

        // Note: we can only delete the file, if the file name was changed (i.e. the new file name is different), otherwise this will cause issues
        if (oldFilePath != null) {
            // check if we already had a file associated with this submission
            if (!oldFilePath.equals(newFilePath)) { // different name
                // IMPORTANT: only delete the file when it has changed the name
                fileUploadSubmission.onDelete();
            }
            else { // same name
                   // IMPORTANT: invalidate the cache so that the new file with the same name will be downloaded (and not a potentially cached one)
                fileService.resetOnPath(newLocalFilePath);
            }
        }
        return newFilePath;
    }

    private String saveFileForSubmission(final MultipartFile file, final Submission submission, FileUploadExercise exercise) throws IOException {
        final var exerciseId = exercise.getId();
        final var submissionId = submission.getId();
        var filename = file.getOriginalFilename();
        if (filename.contains("\\")) {
            // this can happen on Windows computers, then we want to take the last element of the file path
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
        final var filePath = Path.of(dirPath, filename).toString();
        final var savedFile = new File(filePath);
        final var dir = new File(dirPath);

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
     * @param correctionRound the correctionRound for which the submission should lock a result
     * @return the locked file upload submission
     */
    public FileUploadSubmission lockAndGetFileUploadSubmission(Long submissionId, FileUploadExercise fileUploadExercise, int correctionRound) {
        FileUploadSubmission fileUploadSubmission = fileUploadSubmissionRepository.findByIdWithEagerResultAndFeedbackAndAssessorAndParticipationResultsElseThrow(submissionId);

        if (fileUploadSubmission.getLatestResult() == null || fileUploadSubmission.getLatestResult().getAssessor() == null) {
            checkSubmissionLockLimit(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        }

        // correctionRound always defaults to 0, as fileUpload exercises currently are not supported within exams
        lockSubmission(fileUploadSubmission, correctionRound);
        return fileUploadSubmission;
    }

    /**
     * Get a file upload submission of the given exercise that still needs to be assessed and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param fileUploadExercise the exercise the submission should belong to
     * @param correctionRound - the correction round we want our submission to have results for
     * @param ignoreTestRunParticipations flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a locked file upload submission that needs an assessment
     */
    public FileUploadSubmission lockAndGetFileUploadSubmissionWithoutResult(FileUploadExercise fileUploadExercise, boolean ignoreTestRunParticipations, int correctionRound) {
        FileUploadSubmission fileUploadSubmission = getRandomFileUploadSubmissionEligibleForNewAssessment(fileUploadExercise, ignoreTestRunParticipations, correctionRound)
                .orElseThrow(() -> new EntityNotFoundException("File upload submission for exercise " + fileUploadExercise.getId() + " could not be found"));
        lockSubmission(fileUploadSubmission, correctionRound);
        return fileUploadSubmission;
    }
}
