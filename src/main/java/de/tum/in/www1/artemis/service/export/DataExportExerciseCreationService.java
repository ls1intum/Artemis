package de.tum.in.www1.artemis.service.export;

import static de.tum.in.www1.artemis.service.export.DataExportQuizExerciseCreationService.TXT_FILE_EXTENSION;
import static de.tum.in.www1.artemis.service.export.DataExportUtil.createDirectoryIfNotExistent;
import static de.tum.in.www1.artemis.service.export.DataExportUtil.retrieveCourseDirPath;
import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundToNDecimalPlaces;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;

/**
 * A service to create the data export for exercise participations of the user.
 * It is responsible for creating the export for programming exercises and modeling, text, and file upload exercises.
 * For quiz exercises it delegates the creation of the export to {@link DataExportQuizExerciseCreationService}.
 */
@Service
public class DataExportExerciseCreationService {

    private static final String PDF_FILE_EXTENSION = ".pdf";

    private static final String EXERCISE_PREFIX = "exercise_";

    static final String CSV_FILE_EXTENSION = ".csv";

    private final Path repoClonePath;

    private final Logger log = LoggerFactory.getLogger(DataExportExerciseCreationService.class);

    private final FileService fileService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    // we define the field as optional to allow the application to start even if the apollon profile is not active
    private final Optional<ApollonConversionService> apollonConversionService;

    private final ComplaintRepository complaintRepository;

    private final ExerciseRepository exerciseRepository;

    private final ResultService resultService;

    private final AuthorizationCheckService authCheckService;

    public DataExportExerciseCreationService(@Value("${artemis.repo-download-clone-path}") Path repoClonePath, FileService fileService,
            ProgrammingExerciseExportService programmingExerciseExportService, DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService,
            PlagiarismCaseRepository plagiarismCaseRepository, Optional<ApollonConversionService> apollonConversionService, ComplaintRepository complaintRepository,
            ExerciseRepository exerciseRepository, ResultService resultService, AuthorizationCheckService authCheckService) {
        this.fileService = fileService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.dataExportQuizExerciseCreationService = dataExportQuizExerciseCreationService;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.apollonConversionService = apollonConversionService;
        this.complaintRepository = complaintRepository;
        this.exerciseRepository = exerciseRepository;
        this.repoClonePath = repoClonePath;
        this.resultService = resultService;
        this.authCheckService = authCheckService;
    }

    /**
     * Creates the export for all exercises the user participated in.
     *
     * @param workingDirectory the directory the export should be created in
     * @param user             the user for which the export should be created
     * @throws IOException if an error occurs while accessing the file system
     */
    public void createExercisesExport(Path workingDirectory, User user) throws IOException {
        // retrieve all exercises as we cannot retrieve the exercises by course because a user might have participated in a course they are no longer a member of (they have
        // unenrolled)
        var allExerciseParticipations = exerciseRepository.getAllExercisesUserParticipatedInWithEagerParticipationsSubmissionsResultsFeedbacksByUserId(user.getId());
        var exerciseParticipationsPerCourse = allExerciseParticipations.stream().collect(Collectors.groupingBy(Exercise::getCourseViaExerciseGroupOrCourseMember));
        for (var entry : exerciseParticipationsPerCourse.entrySet()) {
            var course = entry.getKey();
            Path courseDir = retrieveCourseDirPath(workingDirectory, course);
            var exercises = entry.getValue();
            Path exercisesDir = courseDir.resolve("exercises");
            if (!exercises.isEmpty()) {
                createDirectoryIfNotExistent(exercisesDir);
            }
            for (var exercise : exercises) {
                if (exercise instanceof ProgrammingExercise programmingExercise) {
                    createProgrammingExerciseExport(programmingExercise, exercisesDir, user);
                }
                else {
                    createNonProgrammingExerciseExport(exercise, exercisesDir, user);
                }
            }
        }
    }

    /**
     * Creates an export for a given programming exercise.
     * <p>
     * Includes submission information, the repository from the VCS and potential plagiarism cases.
     *
     * @param programmingExercise the programming exercise for which the export should be created
     * @param exercisesDir        the directory where all exercises of a course should be stored
     * @param user                the user for which the export should be created
     * @throws IOException if an error occurs while accessing the file system
     */
    public void createProgrammingExerciseExport(ProgrammingExercise programmingExercise, Path exercisesDir, User user) throws IOException {
        Path exerciseDir = exercisesDir.resolve(EXERCISE_PREFIX + programmingExercise.getSanitizedExerciseTitle());
        if (!Files.exists(exerciseDir)) {
            Files.createDirectory(exerciseDir);
        }
        createSubmissionsResultsExport(programmingExercise, exerciseDir, user);
        RepositoryExportOptionsDTO repositoryExportOptions = new RepositoryExportOptionsDTO();
        repositoryExportOptions.setExportAllParticipants(false);
        repositoryExportOptions.setAnonymizeRepository(false);
        repositoryExportOptions.setFilterLateSubmissions(false);
        repositoryExportOptions.setCombineStudentCommits(false);
        repositoryExportOptions.setFilterLateSubmissionsIndividualDueDate(false);
        repositoryExportOptions.setExcludePracticeSubmissions(false);
        repositoryExportOptions.setNormalizeCodeStyle(false);
        var listOfProgrammingExerciseParticipations = programmingExercise.getStudentParticipations().stream()
                .filter(studentParticipation -> studentParticipation instanceof ProgrammingExerciseStudentParticipation)
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).toList();

        // we use this directory only to clone the repository and don't do this in our current directory because the current directory is part of the final data export
        // --> we can delete it after use
        var tempRepoWorkingDir = fileService.getTemporaryUniqueSubfolderPath(repoClonePath, 10);
        programmingExerciseExportService.exportStudentRepositories(programmingExercise, listOfProgrammingExerciseParticipations, repositoryExportOptions, tempRepoWorkingDir,
                exerciseDir, Collections.synchronizedList(new ArrayList<>()));

        createPlagiarismCaseInfoExport(programmingExercise, exerciseDir, user.getId());

    }

    /**
     * Creates an export for a text, modeling, file upload or quiz exercise. Includes submission information and potential plagiarism cases.
     *
     * @param exercise  the exercise for which the export should be created
     * @param courseDir the directory that is used for the course the exercise belongs to
     * @param user      the user for which the export should be created
     * @throws IOException if an error occurs while accessing the file system
     */
    public void createNonProgrammingExerciseExport(Exercise exercise, Path courseDir, User user) throws IOException {
        Path exercisePath = courseDir.resolve(EXERCISE_PREFIX + exercise.getSanitizedExerciseTitle());
        if (!Files.exists(exercisePath)) {
            Files.createDirectory(exercisePath);
        }
        createSubmissionsResultsExport(exercise, exercisePath, user);
        createPlagiarismCaseInfoExport(exercise, exercisePath, user.getId());
    }

    /**
     * Creates the export for the submission of the user to the given exercise.
     * <p>
     * Includes the submission information and the submission content and the results if the results are
     * published.
     * For quiz exercises it delegates the creation of the export to {@link DataExportQuizExerciseCreationService}.
     *
     * @param exercise    the exercise for which the export should be created
     * @param exerciseDir the directory in which the export should be created
     * @param user        the user for which the export should be created
     */
    private void createSubmissionsResultsExport(Exercise exercise, Path exerciseDir, User user) throws IOException {
        // quizzes do not have an assessment due date, so we need to check if they have ended according to their due date
        boolean isInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        boolean includeResults = (exercise.isExamExercise() && exercise.getExamViaExerciseGroupOrCourseMember().resultsPublished())
                || (exercise.isCourseExercise() && ExerciseDateService.isAfterAssessmentDueDate(exercise) && !(exercise instanceof QuizExercise))
                || (exercise.isCourseExercise() && exercise instanceof QuizExercise quizExercise && quizExercise.isQuizEnded()) || isInstructor;
        for (var participation : exercise.getStudentParticipations()) {
            for (var submission : participation.getSubmissions()) {
                createSubmissionCsvFile(submission, exerciseDir);
                if (submission instanceof FileUploadSubmission fileUploadSubmission) {
                    copyFileUploadSubmissionFile(FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId()), exerciseDir, fileUploadSubmission);
                }
                else if (submission instanceof TextSubmission textSubmission) {
                    storeTextSubmissionContent(textSubmission, exerciseDir);
                }
                else if (submission instanceof ModelingSubmission modelingSubmission) {
                    storeModelingSubmissionContent(modelingSubmission, exerciseDir);
                }
                else if (submission instanceof QuizSubmission) {
                    dataExportQuizExerciseCreationService.createQuizAnswersExport((QuizExercise) exercise, participation, exerciseDir, includeResults);
                }
                // for a programming exercise, we want to include the results that are visible before the assessment due date
                if (includeResults || exercise instanceof ProgrammingExercise) {
                    boolean programmingExerciseBeforeAssessmentDueDate = exercise instanceof ProgrammingExercise && !ExerciseDateService.isAfterAssessmentDueDate(exercise);
                    createResultsAndComplaintFiles(submission, exerciseDir, user, programmingExerciseBeforeAssessmentDueDate, isInstructor);
                }
            }
        }
    }

    /**
     * Stores the modeling submission as pdf if the apollon profile is active and the apollon conversion service works, otherwise stores it as json file.
     *
     * @param modelingSubmission the modeling submission for which the content should be stored
     * @param outputDir          the directory in which the content should be stored
     * @throws IOException if the file cannot be written
     */
    private void storeModelingSubmissionContent(ModelingSubmission modelingSubmission, Path outputDir) throws IOException {
        if (modelingSubmission.getModel() == null) {
            log.warn("Cannot include modeling submission content in data export because content is null for submission with id: {}", modelingSubmission.getId());
            return;
        }
        var fileName = "submission_" + modelingSubmission.getId();
        if (apollonConversionService.isEmpty()) {
            log.warn("Cannot include modeling submission content in data export as pdf because apollon profile is not active. Going to include the json file");
            addModelJsonWithExplanationHowToView(modelingSubmission.getModel(), outputDir, fileName);
            return;
        }

        try (var modelAsPdf = apollonConversionService.get().convertModel(modelingSubmission.getModel())) {
            FileUtils.writeByteArrayToFile(outputDir.resolve(fileName + PDF_FILE_EXTENSION).toFile(), modelAsPdf.readAllBytes());
        }
        catch (Exception e) {
            log.warn("Failed to include the model as pdf, going to include it as plain JSON file.");
            addModelJsonWithExplanationHowToView(modelingSubmission.getModel(), outputDir, fileName);
        }
    }

    /**
     * Stores the given model as json file and adds a markdown file with an explanation how to view the model.
     * <p>
     * Used if the Apollon Conversion Service is not available or an error occurs while using it.
     *
     * @param model     the model belonging to the submission as JSON string
     * @param outputDir the directory in which the content should be stored
     * @param fileName  the file name of the JSON file
     * @throws IOException if the file cannot be written
     */
    private void addModelJsonWithExplanationHowToView(String model, Path outputDir, String fileName) throws IOException {
        FileUtils.writeStringToFile(outputDir.resolve(fileName + ".md").toFile(), model, StandardCharsets.UTF_8);
        String explanation = """
                You can view your model if you go to [Apollon Modeling Editor](https://www.apollon.ase.in.tum.de) and click on File --> Import and select the .json file.
                """;
        FileUtils.writeStringToFile(outputDir.resolve("view_model.md").toFile(), explanation, StandardCharsets.UTF_8);
    }

    /**
     * Stores the text submission content as txt file.
     *
     * @param textSubmission the text submission for which the content should be stored
     * @param outputDir      the directory in which the content should be stored
     * @throws IOException if the file cannot be written
     */
    private void storeTextSubmissionContent(TextSubmission textSubmission, Path outputDir) throws IOException {
        // text can be null which leads to an exception
        if (textSubmission.getText() != null) {
            FileUtils.writeStringToFile(outputDir.resolve("text_exercise_submission_" + textSubmission.getId() + "_text.txt").toFile(), textSubmission.getText(),
                    StandardCharsets.UTF_8);
        }
        else {
            log.warn("Cannot include text submission content in data export because content is null for submission with id: {}", textSubmission.getId());
        }
    }

    /**
     * Creates a txt file containing the results with the score, the number of passed test cases if it is a programming exercise
     * and the feedbacks (both manual and automatic).
     *
     * @param submission                                 the submission for which the results should be stored
     * @param outputDir                                  the directory in which the results should be stored
     * @param user                                       the user for which the export should be created
     * @param programmingExerciseBeforeAssessmentDueDate whether the programming exercise is before the assessment due date
     * @param isInstructor                               whether the user is an instructor in the course the exercise belongs to
     * @throws IOException if the file cannot be written
     */
    private void createResultsAndComplaintFiles(Submission submission, Path outputDir, User user, boolean programmingExerciseBeforeAssessmentDueDate, boolean isInstructor)
            throws IOException {
        StringBuilder resultScoreAndFeedbacks = new StringBuilder();
        for (var result : submission.getResults()) {
            if (result != null) {
                // Do not include the results if the assessment due date is in the future and the assessment is not automatic and the user is not an instructor
                // We only consider programming exercises here because for other exercises this method is not called if the assessment due date is in the future
                if (programmingExerciseBeforeAssessmentDueDate && result.getAssessmentType() != AssessmentType.AUTOMATIC && !isInstructor) {
                    continue;
                }
                resultService.filterSensitiveInformationIfNecessary(submission.getParticipation(), List.of(result), Optional.of(user));
                var score = result.getScore();
                if (score != null) {
                    resultScoreAndFeedbacks.append("Score of submission: ").append(score).append("%").append(" ")
                            .append(roundToNDecimalPlaces(score * submission.getParticipation().getExercise().getMaxPoints() / 100, 2)).append(" Points").append("\n");
                }
                if (submission instanceof ProgrammingSubmission && result.getPassedTestCaseCount() != null && result.getTestCaseCount() != null && result.getTestCaseCount() > 0) {
                    resultScoreAndFeedbacks.append("Passed test cases: ").append(result.getPassedTestCaseCount()).append("/").append(result.getTestCaseCount()).append("\n");
                }
                if (submission instanceof ProgrammingSubmission programmingSubmission && programmingSubmission.isBuildFailed()) {
                    resultScoreAndFeedbacks.append("Build failed").append("\n");
                }
                for (var feedback : result.getFeedbacks()) {
                    if (feedback != null) {
                        resultScoreAndFeedbacks.append("- Feedback: ");

                        // null if it's manual feedback
                        if (feedback.getText() != null) {
                            resultScoreAndFeedbacks.append(feedback.getText()).append("\t");
                        }
                        // null if the test case passes
                        if (feedback.getDetailText() != null) {
                            resultScoreAndFeedbacks.append(feedback.getDetailText()).append("\t");
                        }
                        if (feedback.getCredits() != null) {
                            resultScoreAndFeedbacks.append(feedback.getCredits());
                        }
                        resultScoreAndFeedbacks.append("\n");
                    }
                }
                FileUtils.writeStringToFile(outputDir.resolve("submission_" + submission.getId() + "_result_" + result.getId() + TXT_FILE_EXTENSION).toFile(),
                        resultScoreAndFeedbacks.toString(), StandardCharsets.UTF_8);
            }
            resultScoreAndFeedbacks = new StringBuilder();
        }
        var possibleComplaint = complaintRepository.findWithEagerComplaintResponseByResultSubmissionId(submission.getId());
        if (possibleComplaint.isPresent()) {
            addComplaintData(possibleComplaint.get(), outputDir);
        }
    }

    /**
     * Creates a CSV file containing the complaint data.
     * <p>
     * Complaint can be either a complaint or a more feedback request.
     *
     * @param complaint the complaint for which the data should be stored
     * @param outputDir the directory in which the data should be stored
     * @throws IOException if the file cannot be written
     */
    private void addComplaintData(Complaint complaint, Path outputDir) throws IOException {
        List<String> headers = new ArrayList<>();
        var dataStreamBuilder = Stream.builder();
        headers.add("id");
        dataStreamBuilder.add(complaint.getId());
        headers.add("submitted at");
        dataStreamBuilder.add(complaint.getSubmittedTime());
        headers.add("complaint type");
        dataStreamBuilder.add(complaint.getComplaintType());
        headers.add("complaint text");
        dataStreamBuilder.add(complaint.getComplaintText());

        if (complaint.getComplaintResponse() != null) {
            headers.add("complaint response");
            dataStreamBuilder.add(complaint.getComplaintResponse().getResponseText());
        }
        if (complaint.isAccepted() != null && ComplaintType.COMPLAINT == complaint.getComplaintType()) {
            headers.add("accepted");
            dataStreamBuilder.add(complaint.isAccepted());
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();
        var prefix = complaint.getComplaintType() == ComplaintType.COMPLAINT ? "complaint_" : "more_feedback_";

        try (final var printer = new CSVPrinter(Files.newBufferedWriter(outputDir.resolve(prefix + complaint.getId() + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(dataStreamBuilder.build());
            printer.flush();
        }
    }

    /**
     * Creates a CSV file containing the plagiarism case information.
     *
     * @param exercise     the exercise for which the plagiarism case information should be stored
     * @param exercisePath the directory in which the plagiarism case information should be stored
     * @param userId       the id of the user that requested the export and that is involved in the plagiarism case
     * @throws IOException if the file cannot be written
     */
    private void createPlagiarismCaseInfoExport(Exercise exercise, Path exercisePath, long userId) throws IOException {
        var plagiarismCaseOptional = plagiarismCaseRepository.findByStudentIdAndExerciseIdWithPostAndAnswerPost(userId, exercise.getId());
        List<String> headers = new ArrayList<>();
        var dataStreamBuilder = Stream.builder();
        if (plagiarismCaseOptional.isEmpty()) {
            return;
        }
        var plagiarismCase = plagiarismCaseOptional.get();
        if (plagiarismCase.getVerdict() != null) {
            headers.add("Verdict");
            headers.add("Verdict Date");
            dataStreamBuilder.add(plagiarismCase.getVerdict());
            dataStreamBuilder.add(plagiarismCase.getVerdictDate());
        }
        if (plagiarismCase.getPost() != null) {
            headers.add("Plagiarism case announcement");
            dataStreamBuilder.add(plagiarismCase.getPost().getContent());
        }
        if (!plagiarismCase.getPost().getAnswers().isEmpty()) {
            headers.add("Plagiarism case replies");
            dataStreamBuilder.add(plagiarismCase.getPost().getAnswers().stream().map(AnswerPost::getContent).collect(Collectors.joining("\n")));
        }
        if (plagiarismCase.getVerdict() == PlagiarismVerdict.POINT_DEDUCTION) {
            dataStreamBuilder.add(plagiarismCase.getVerdictPointDeduction());
        }
        else if (plagiarismCase.getVerdict() == PlagiarismVerdict.WARNING) {
            dataStreamBuilder.add(plagiarismCase.getVerdictMessage());
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(exercisePath.resolve("plagiarism_case_" + plagiarismCase.getId() + CSV_FILE_EXTENSION)),
                csvFormat)) {
            printer.printRecord(dataStreamBuilder.build());
            printer.flush();
        }
    }

    /**
     * Copies the file upload submission file to the data export working directory if it still exists.
     *
     * @param submissionFilePath   the path to the file upload submission file
     * @param outputDir            the directory to which the file should be copied
     * @param fileUploadSubmission the file upload submission for which the file should be copied
     * @throws IOException if the file cannot be copied
     */
    private void copyFileUploadSubmissionFile(Path submissionFilePath, Path outputDir, FileUploadSubmission fileUploadSubmission) throws IOException {
        try {
            FileUtils.copyDirectory(submissionFilePath.toFile(), outputDir.toFile());
        }
        catch (IOException exception) {
            log.info("Cannot include submission for file upload exercise stored at {}", submissionFilePath);
            addInfoThatFileForFileUploadSubmissionNoLongerExists(outputDir, fileUploadSubmission);
        }
    }

    /**
     * Adds a markdown file to the data export working directory that informs the user that the file for the file upload submission no longer exists.
     *
     * @param outputDir            the directory in which the file should be stored
     * @param fileUploadSubmission the file upload submission for which the file should be stored
     * @throws IOException if the file cannot be written
     */
    private void addInfoThatFileForFileUploadSubmissionNoLongerExists(Path outputDir, FileUploadSubmission fileUploadSubmission) throws IOException {
        var exercise = fileUploadSubmission.getParticipation().getExercise();
        FileUtils.writeStringToFile(outputDir.resolve("submission_file_no_longer_exists.md").toFile(),
                String.format("Your submitted file for the exercise %s no longer exists on the file system.", exercise), StandardCharsets.UTF_8);
    }

    /**
     * Creates a CSV file containing the submission information.
     * <p>
     * This includes the id, the submission date and the commit hash if it is a programming exercise.
     *
     * @param submission the submission for which the information should be stored
     * @param outputPath the directory in which the information should be stored
     * @throws IOException if the file cannot be written
     */
    private void createSubmissionCsvFile(Submission submission, Path outputPath) throws IOException {
        List<String> headers = new ArrayList<>(List.of("id", "submissionDate"));
        if (submission instanceof ProgrammingSubmission) {
            headers.add("commitHash");
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();

        try (final CSVPrinter printer = new CSVPrinter(
                Files.newBufferedWriter(outputPath.resolve("participation_" + submission.getParticipation().getId() + "_submission_" + submission.getId() + CSV_FILE_EXTENSION)),
                csvFormat)) {
            printer.printRecord(getSubmissionStreamToPrint(submission));
            printer.flush();
        }
    }

    /**
     * Returns a stream of the submission information that should be included in the CSV file.
     * <p>
     * This includes the id, the submission date and the commit hash if it is a programming exercise.
     *
     * @param submission the submission for which the information should be stored
     * @return a stream of the submission information that should be included in the CSV file
     */
    private Stream<?> getSubmissionStreamToPrint(Submission submission) {
        var builder = Stream.builder();
        builder.add(submission.getId()).add(submission.getSubmissionDate());
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            builder.add(programmingSubmission.getCommitHash());
        }
        return builder.build();
    }
}
