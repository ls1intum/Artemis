package de.tum.in.www1.artemis.service.dataexport;

import static de.tum.in.www1.artemis.service.dataexport.DataExportQuizExerciseCreationService.TXT_FILE_EXTENSION;
import static de.tum.in.www1.artemis.service.dataexport.DataExportUtil.retrieveCourseDirPath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;

/**
 * A service to create the data export for exercise participations of the user
 */
@Service
public class DataExportExerciseCreationService {

    private static final String PDF_FILE_EXTENSION = ".pdf";

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

    public DataExportExerciseCreationService(@Value("${artemis.repo-download-clone-path}") Path repoClonePath, FileService fileService,
            ProgrammingExerciseExportService programmingExerciseExportService, DataExportQuizExerciseCreationService dataExportQuizExerciseCreationService,
            PlagiarismCaseRepository plagiarismCaseRepository, Optional<ApollonConversionService> apollonConversionService, ComplaintRepository complaintRepository,
            ExerciseRepository exerciseRepository) {
        this.fileService = fileService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.dataExportQuizExerciseCreationService = dataExportQuizExerciseCreationService;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.apollonConversionService = apollonConversionService;
        this.complaintRepository = complaintRepository;
        this.exerciseRepository = exerciseRepository;
        this.repoClonePath = repoClonePath;
    }

    /**
     * Creates the export for all exercises the user participated in.
     *
     * @param workingDirectory the directory the export should be created in
     * @param userId           the id of the user that requested the export
     * @throws IOException if an error occurs while accessing the file system
     */
    public void createExercisesExport(Path workingDirectory, long userId) throws IOException {
        // retrieve all exercises as we cannot retrieve the exercises by course because a user might have participated in a course they are no longer a member of (they have
        // unenrolled)
        var allExerciseParticipations = exerciseRepository.getAllExercisesUserParticipatedInWithEagerParticipationsSubmissionsResultsFeedbacksByUserId(userId);
        var exerciseParticipationsPerCourse = allExerciseParticipations.stream().collect(Collectors.groupingBy(Exercise::getCourseViaExerciseGroupOrCourseMember));
        for (var entry : exerciseParticipationsPerCourse.entrySet()) {
            var course = entry.getKey();
            Path courseDir = retrieveCourseDirPath(workingDirectory, course);
            var exercises = entry.getValue();
            if (!exercises.isEmpty()) {
                Files.createDirectory(courseDir);
            }
            for (var exercise : exercises) {
                if (exercise instanceof ProgrammingExercise programmingExercise) {
                    createProgrammingExerciseExport(programmingExercise, courseDir, userId);
                }
                else {
                    createNonProgrammingExerciseExport(exercise, courseDir, userId);
                }
            }
        }
    }

    /**
     * Creates an export for a given programming exercise. Includes submission information, the repository from the VCS and potential plagiarism cases.
     *
     * @param programmingExercise the programming exercise for which the export should be created
     * @param courseDir           the directory that is used for the course the exercise belongs to
     * @param userId              the id of the user that requested the export
     * @throws IOException if an error occurs while accessing the file system
     */

    public void createProgrammingExerciseExport(ProgrammingExercise programmingExercise, Path courseDir, long userId) throws IOException {
        Path exerciseDir = courseDir.resolve(programmingExercise.getSanitizedExerciseTitle());
        if (!Files.exists(exerciseDir)) {
            Files.createDirectory(exerciseDir);
        }
        createSubmissionsResultsExport(programmingExercise, exerciseDir);
        RepositoryExportOptionsDTO repositoryExportOptions = new RepositoryExportOptionsDTO();
        repositoryExportOptions.setExportAllParticipants(false);
        repositoryExportOptions.setAnonymizeRepository(false);
        repositoryExportOptions.setFilterLateSubmissions(false);
        repositoryExportOptions.setCombineStudentCommits(false);
        repositoryExportOptions.setFilterLateSubmissionsIndividualDueDate(false);
        repositoryExportOptions.setExcludePracticeSubmissions(false);
        repositoryExportOptions.setNormalizeCodeStyle(true);
        var listOfProgrammingExerciseParticipations = programmingExercise.getStudentParticipations().stream()
                .filter(studentParticipation -> studentParticipation instanceof ProgrammingExerciseStudentParticipation)
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).toList();
        List<String> exportRepoErrors = new ArrayList<>();
        // we use this directory only to clone the repository and don't do this in our current directory because the current directory is part of the final data export
        // --> we can delete it after use
        var tempRepoWorkingDir = fileService.getTemporaryUniquePath(repoClonePath, 10);
        programmingExerciseExportService.exportStudentRepositories(programmingExercise, listOfProgrammingExerciseParticipations, repositoryExportOptions, tempRepoWorkingDir,
                exerciseDir, exportRepoErrors);

        createPlagiarismCaseInfoExport(programmingExercise, exerciseDir, userId);

    }

    /**
     * Creates an export for a text, modeling, file upload or quiz exercise. Includes submission information and potential plagiarism cases.
     *
     * @param exercise  the exercise for which the export should be created
     * @param courseDir the directory that is used for the course the exercise belongs to
     * @param userId    the id of the user that requested the export
     * @throws IOException if an error occurs while accessing the file system
     */
    public void createNonProgrammingExerciseExport(Exercise exercise, Path courseDir, long userId) throws IOException {
        Path exercisePath = courseDir.resolve(exercise.getSanitizedExerciseTitle());
        if (!Files.exists(exercisePath)) {
            Files.createDirectory(exercisePath);
        }
        createSubmissionsResultsExport(exercise, exercisePath);
        createPlagiarismCaseInfoExport(exercise, exercisePath, userId);
    }

    private void createSubmissionsResultsExport(Exercise exercise, Path exerciseDir) throws IOException {
        boolean includeResults = exercise.isExamExercise() && exercise.getExamViaExerciseGroupOrCourseMember().resultsPublished()
                || exercise.isCourseExercise() && ExerciseDateService.isAfterAssessmentDueDate(exercise);
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
                if (includeResults) {
                    createResultsAndComplaintFiles(submission, exerciseDir);
                }
            }
        }
    }

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
            Files.write(outputDir.resolve(fileName + PDF_FILE_EXTENSION), modelAsPdf.readAllBytes());
        }
        catch (IOException e) {
            log.warn("Failed to include the model as pdf, going to include it as plain JSON file.");
            addModelJsonWithExplanationHowToView(modelingSubmission.getModel(), outputDir, fileName);
        }
    }

    private void addModelJsonWithExplanationHowToView(String model, Path outputDir, String fileName) throws IOException {
        Files.writeString(outputDir.resolve(fileName + ".json"), model);
        String explanation = """
                You can view your model if you go to [Apollon Modeling Editor](https://www.apollon.ase.in.tum.de) and click on File --> Import and select the .json file.
                """;
        Files.writeString(outputDir.resolve("view_model.md"), explanation);
    }

    private void storeTextSubmissionContent(TextSubmission textSubmission, Path outputDir) throws IOException {
        // text can be null which leads to an exception
        if (textSubmission.getText() != null) {
            Files.writeString(outputDir.resolve("text_exercise_submission_" + textSubmission.getId() + "_text.txt"), textSubmission.getText());
        }
        else {
            log.warn("Cannot include text submission content in data export because content is null for submission with id: {}", textSubmission.getId());
        }
    }

    private void createResultsAndComplaintFiles(Submission submission, Path outputDir) throws IOException {
        StringBuilder resultScoreAndFeedbacks = new StringBuilder();
        for (var result : submission.getResults()) {
            if (result != null) {
                var score = result.getScore();
                if (score != null) {
                    resultScoreAndFeedbacks.append("Score of submission: ").append(score).append("%").append(" ")
                            .append(score * submission.getParticipation().getExercise().getMaxPoints() / 100).append(" Points").append("\n");
                }
                if (submission instanceof ProgrammingSubmission && result.getPassedTestCaseCount() != null && result.getTestCaseCount() != null && result.getTestCaseCount() > 0) {
                    resultScoreAndFeedbacks.append("Passed test cases: ").append(result.getPassedTestCaseCount()).append("/").append(result.getTestCaseCount()).append("\n");
                }
                for (var feedback : result.getFeedbacks()) {
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
                Files.writeString(outputDir.resolve("submission_" + submission.getId() + "_result_" + result.getId() + TXT_FILE_EXTENSION), resultScoreAndFeedbacks);
            }
            resultScoreAndFeedbacks = new StringBuilder();
        }
        var possibleComplaint = complaintRepository.findWithEagerComplaintResponseByResultSubmissionId(submission.getId());
        if (possibleComplaint.isPresent()) {
            addComplaintData(possibleComplaint.get(), outputDir);
        }
    }

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

    private void copyFileUploadSubmissionFile(String submissionFilePath, Path outputDir, FileUploadSubmission fileUploadSubmission) throws IOException {
        try {
            FileUtils.copyDirectory(new File(submissionFilePath), outputDir.toFile());
        }
        catch (IOException exception) {
            log.info("Cannot include submission for file upload exercise stored at {}", submissionFilePath);
            addInfoThatFileForFileUploadSubmissionNoLongerExists(outputDir, fileUploadSubmission);
        }
    }

    private void addInfoThatFileForFileUploadSubmissionNoLongerExists(Path outputDir, FileUploadSubmission fileUploadSubmission) throws IOException {
        var exercise = fileUploadSubmission.getParticipation().getExercise();
        Files.writeString(outputDir.resolve("submission_file_no_longer_exists.md"),
                String.format("Your submitted file for the exercise %s no longer exists on the file system.", exercise));
    }

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

    private Stream<?> getSubmissionStreamToPrint(Submission submission) {
        var builder = Stream.builder();
        builder.add(submission.getId()).add(submission.getSubmissionDate());
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            builder.add(programmingSubmission.getCommitHash());
        }
        return builder.build();
    }
}
