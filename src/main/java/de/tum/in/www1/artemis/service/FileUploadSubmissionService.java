package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
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

    private final Logger log = LoggerFactory.getLogger(FileUploadSubmissionService.class);

    private final FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final ParticipationService participationService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final FileService fileService;

    public FileUploadSubmissionService(FileUploadSubmissionRepository fileUploadSubmissionRepository, SubmissionRepository submissionRepository, ResultRepository resultRepository,
            ParticipationService participationService, UserService userService, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            FileService fileService, AuthorizationCheckService authCheckService) {
        super(submissionRepository, userService, authCheckService);
        this.fileUploadSubmissionRepository = fileUploadSubmissionRepository;
        this.resultRepository = resultRepository;
        this.participationService = participationService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultService = resultService;
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
    @Transactional
    public FileUploadSubmission handleFileUploadSubmission(FileUploadSubmission fileUploadSubmission, MultipartFile file, FileUploadExercise fileUploadExercise,
            Principal principal) throws IOException, EmptyFileException {
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseIdAndStudentLoginAnyState(fileUploadExercise.getId(), principal.getName());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + fileUploadExercise.getId());
        }
        StudentParticipation participation = optionalParticipation.get();

        return save(fileUploadSubmission, file, participation, fileUploadExercise);
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
        List<StudentParticipation> participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exerciseId);
        List<FileUploadSubmission> submissions = new ArrayList<>();
        participations.stream().peek(participation -> participation.getExercise().setStudentParticipations(null)).map(StudentParticipation::findLatestSubmission)
                // filter out non submitted submissions if the flag is set to true
                .filter(submission -> submission.isPresent() && (!submittedOnly || submission.get().isSubmitted()))
                .forEach(submission -> submissions.add((FileUploadSubmission) submission.get()));
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

        // TODO: properly load the submissions with all required data from the database without using @Transactional
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
        Random random = new Random();
        var participations = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResults(fileUploadExercise.getId());
        var submissionsWithoutResult = participations.stream().map(StudentParticipation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }
        var submissionWithoutResult = (FileUploadSubmission) submissionsWithoutResult.get(random.nextInt(submissionsWithoutResult.size()));
        return Optional.of(submissionWithoutResult);
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
            result.setParticipation(submission.getParticipation());
        }
        result = resultRepository.save(result);
        fileUploadSubmissionRepository.save(submission);
        return result;
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
        fileUploadSubmission.setSubmissionDate(ZonedDateTime.now());
        fileUploadSubmission.setType(SubmissionType.MANUAL);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmission = fileUploadSubmissionRepository.save(fileUploadSubmission);
        fileUploadSubmission.setFilePath(fileService.publicPathForActualPath(localPath, fileUploadSubmission.getId()));
        fileUploadSubmissionRepository.save(fileUploadSubmission);

        participation.addSubmissions(fileUploadSubmission);

        if (fileUploadSubmission.isSubmitted()) {
            participation.setInitializationState(InitializationState.FINISHED);
        }
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
     * Soft lock the submission to prevent other tutors from receiving and assessing it.  We set the assessor and save the result to soft lock the assessment in the client. If no result exists for this submission we create one first.
     *
     * @param fileUploadSubmission the submission to lock
     */
    private void lockSubmission(FileUploadSubmission fileUploadSubmission) {
        Result result = fileUploadSubmission.getResult();
        if (result == null) {
            result = setNewResult(fileUploadSubmission);
        }

        if (result.getAssessor() == null) {
            resultService.setAssessor(result);
        }

        result.setAssessmentType(AssessmentType.MANUAL);
        result = resultRepository.save(result);
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

        lockSubmission(fileUploadSubmission);
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
        FileUploadSubmission fileUploadSubmission = getFileUploadSubmissionWithoutManualResult(fileUploadExercise)
                .orElseThrow(() -> new EntityNotFoundException("File upload submission for exercise " + fileUploadExercise.getId() + " could not be found"));
        lockSubmission(fileUploadSubmission);
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
