package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;

/**
 * Service Implementation for managing the data export in accordance with Art. 15 GDPR.
 */
@Service
public class DataExportService {

    @Value("${artemis.data-export-path}")
    private Path dataExportPath;

    private final CourseRepository courseRepository;

    private final ParticipationRepository participationRepository;

    private final SubmissionService submissionService;

    private final UserService userService;

    private final AuthorizationCheckService authorizationCheckService;

    private final Logger log = LoggerFactory.getLogger(DataExportService.class);

    private final ZipFileService zipFileService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private Path workingDirectory;

    public DataExportService(CourseRepository courseRepository, ParticipationRepository participationRepository, SubmissionService submissionService, UserService userService,
            AuthorizationCheckService authorizationCheckService, ZipFileService zipFileService, ProgrammingExerciseExportService programmingExerciseExportService,
            ProgrammingExerciseRepository programmingExerciseRepository, ModelingExerciseRepository modelingExerciseRepository, TextExerciseRepository textExerciseRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository, QuizExerciseRepository quizExerciseRepository) {
        this.courseRepository = courseRepository;
        this.participationRepository = participationRepository;
        this.submissionService = submissionService;
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
        this.zipFileService = zipFileService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
    }

    public void requestDataExport(User user) throws IOException {
        if (!Files.exists(dataExportPath)) {
            Files.createDirectories(dataExportPath);
        }
        workingDirectory = Files.createTempDirectory(dataExportPath, "data-export-working-dir");
        createDataExport(user);

    }

    public void downloadDataExport() {

    }

    private void createDataExport(User user) throws IOException {
        var courses = courseRepository.getAllCoursesUserIsMemberOf(authorizationCheckService.isAdmin(user), user.getGroups());
        for (var course : courses) {
            var courseWorkingDir = Files.createTempDirectory(workingDirectory, "course-" + course.getId());
            var outputDir = courseWorkingDir.resolve("output");
            if (!Files.exists(outputDir)) {
                Files.createDirectory(outputDir);
            }

            var programmingExercises = programmingExerciseRepository
                    .getAllProgrammingExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(), user.getId());
            for (var programmingExercise : programmingExercises) {
                createProgrammingExerciseExport(programmingExercise, courseWorkingDir, outputDir);
            }
            var modelingExercises = modelingExerciseRepository.getAllModelingExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(),
                    user.getId());
            for (var modelingExercise : modelingExercises) {
                createModelingExerciseExport(modelingExercise, courseWorkingDir, outputDir);
            }
            var textExercises = textExerciseRepository.getAllTextExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(),
                    user.getId());
            for (var textExercise : textExercises) {
                createTextExerciseExport(textExercise, courseWorkingDir, outputDir);
            }
            var fileUploadExercises = fileUploadExerciseRepository
                    .getAllFileUploadExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(), user.getId());
            for (var fileUploadExercise : fileUploadExercises) {
                createFileUploadExerciseExport(fileUploadExercise, courseWorkingDir, outputDir);
            }
            var quizExercises = quizExerciseRepository.getAllQuizExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(),
                    user.getId());
            createCourseZipFile();
        }

    }

    private void createProgrammingExerciseExport(ProgrammingExercise programmingExercise, Path workingDirectory, Path outputPath) throws IOException {
        Path exerciseWorkingDir = createParticipationsSubmissionsResultsExport(programmingExercise, workingDirectory);
        RepositoryExportOptionsDTO repositoryExportOptions = new RepositoryExportOptionsDTO();
        repositoryExportOptions.setExportAllParticipants(false);
        repositoryExportOptions.setAnonymizeStudentCommits(false);
        repositoryExportOptions.setFilterLateSubmissions(false);
        repositoryExportOptions.setCombineStudentCommits(false);
        repositoryExportOptions.setFilterLateSubmissionsIndividualDueDate(false);
        repositoryExportOptions.setExcludePracticeSubmissions(false);
        repositoryExportOptions.setHideStudentNameInZippedFolder(false);
        repositoryExportOptions.setNormalizeCodeStyle(true);
        var listOfProgrammingExerciseParticipations = programmingExercise.getStudentParticipations().stream()
                .filter(studentParticipation -> studentParticipation instanceof ProgrammingExerciseStudentParticipation)
                .map(studentParticipation -> (ProgrammingExerciseStudentParticipation) studentParticipation).toList();
        List<String> exportRepoErrors = new ArrayList<>();
        programmingExerciseExportService.exportStudentRepositories(programmingExercise, listOfProgrammingExerciseParticipations, repositoryExportOptions, exerciseWorkingDir,
                exportRepoErrors);
        zipFileService.createZipFileWithFolderContent(
                outputPath.resolve(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getShortName() + ".zip"),
                exerciseWorkingDir, null);

    }

    private Path createParticipationsSubmissionsResultsExport(Exercise exercise, Path workingDirectory) throws IOException {
        var exerciseWorkingDir = Files.createTempDirectory(workingDirectory, "exercise-" + exercise.getId());
        for (var particpation : exercise.getStudentParticipations()) {
            // TODO create participation csv file
            for (var submission : particpation.getSubmissions()) {
                createSubmissionCsvFile(submission, exerciseWorkingDir);
                for (var result : submission.getResults()) {
                    // TODO create result csv file
                }
            }
        }
        return exerciseWorkingDir;
    }

    private void createFileUploadExerciseExport(FileUploadExercise fileUploadExercise, Path workingDirectory, Path outputPath) throws IOException {
        Path exerciseWorkingDir = createParticipationsSubmissionsResultsExport(fileUploadExercise, workingDirectory);
        zipFileService.createZipFileWithFolderContent(
                outputPath.resolve(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + fileUploadExercise.getSanitizedExerciseTitle() + ".zip"),
                exerciseWorkingDir, null);

    }

    private void createTextExerciseExport(TextExercise textExercise, Path workingDirectory, Path outputPath) throws IOException {
        Path exerciseWorkingDir = createParticipationsSubmissionsResultsExport(textExercise, workingDirectory);
        zipFileService.createZipFileWithFolderContent(
                outputPath.resolve(textExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + textExercise.getSanitizedExerciseTitle() + ".zip"),
                exerciseWorkingDir, null);

    }

    private void createModelingExerciseExport(ModelingExercise modelingExercise, Path workingDirectory, Path outputPath) throws IOException {
        Path exerciseWorkingDir = Files.createTempDirectory(workingDirectory, "modeling-exercise" + modelingExercise.getId());
        log.info("Creating export for modeling exercise with id{}", modelingExercise.getId());
        for (var particpation : modelingExercise.getStudentParticipations()) {
            // TODO create participation csv file
            for (var submission : particpation.getSubmissions()) {
                createSubmissionCsvFile(submission, exerciseWorkingDir);
                for (var result : submission.getResults()) {
                    // TODO create result csv file
                }
            }
        }
        zipFileService.createZipFileWithFolderContent(
                outputPath.resolve(modelingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + modelingExercise.getSanitizedExerciseTitle() + ".zip"),
                exerciseWorkingDir, null);

    }

    private void createCourseZipFile() {

    }

    private void createExerciseZipFile(Path exercisePath, Path outputPath) {

    }

    private void createSubmissionCsvFile(Submission submission, Path outputPath) throws IOException {
        StringWriter sw = new StringWriter();
        List<String> headers = new ArrayList<>(List.of("id", "submissionDate", "type", "durationInMinutes"));
        if (submission instanceof ProgrammingSubmission) {
            headers.add("commitHash");
        }
        else if (submission instanceof TextSubmission) {
            headers.add("text");
        }
        else if (submission instanceof ModelingSubmission) {
            headers.add("model");
        }
        else if (submission instanceof QuizSubmission) {
            headers.add("scoreInPoints");
            headers.add("submittedAnswers");
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();

        try (final CSVPrinter printer = new CSVPrinter(sw, csvFormat)) {
            printer.printRecord(getSubmissionStreamToPrint(submission));

        }
        Files.writeString(outputPath.resolve(submission.getParticipation().getExercise().getCourseViaExerciseGroupOrCourseMember().getShortName() + "exercise-"
                + submission.getParticipation().getExercise().getTitle() + "-participation" + submission.getParticipation().getId() + "-submission" + submission.getId() + ".csv"),
                sw.toString());
    }

    private Stream<?> getSubmissionStreamToPrint(Submission submission) {
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            return Stream.of(programmingSubmission.getId(), programmingSubmission.getSubmissionDate(), programmingSubmission.getType(),
                    programmingSubmission.getDurationInMinutes(), programmingSubmission.getCommitHash());

        }
        else if (submission instanceof TextSubmission textSubmission) {
            return Stream.of(textSubmission.getId(), textSubmission.getSubmissionDate(), textSubmission.getType(), textSubmission.getDurationInMinutes(), textSubmission.getText());
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            return Stream.of(modelingSubmission.getId(), modelingSubmission.getSubmissionDate(), modelingSubmission.getType(), modelingSubmission.getDurationInMinutes(),
                    modelingSubmission.getModel());
        }
        else if (submission instanceof FileUploadSubmission fileUploadSubmission) {
            return Stream.of(fileUploadSubmission.getId(), fileUploadSubmission.getSubmissionDate(), fileUploadSubmission.getType(), fileUploadSubmission.getDurationInMinutes());
        }
        else if (submission instanceof QuizSubmission quizSubmission) {
            // TODO add submitted answers once we have a query to fetch them
            return Stream.of(quizSubmission.getId(), quizSubmission.getSubmissionDate(), quizSubmission.getType(), quizSubmission.getDurationInMinutes(),
                    quizSubmission.getScoreInPoints());
        }
        else {
            throw new IllegalArgumentException("Submission type not supported");
        }
    }

}
