package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

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

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final Logger log = LoggerFactory.getLogger(DataExportService.class);

    private final ZipFileService zipFileService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final DataExportRepository dataExportRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private Path workingDirectory;

    public DataExportService(CourseRepository courseRepository, ParticipationRepository participationRepository, SubmissionService submissionService, UserService userService,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService, ZipFileService zipFileService,
            ProgrammingExerciseExportService programmingExerciseExportService, ProgrammingExerciseRepository programmingExerciseRepository,
            ModelingExerciseRepository modelingExerciseRepository, TextExerciseRepository textExerciseRepository, FileUploadExerciseRepository fileUploadExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, DataExportRepository dataExportRepository, SubmittedAnswerRepository submittedAnswerRepository,
            QuizQuestionRepository quizQuestionRepository, QuizSubmissionRepository quizSubmissionRepository) {
        this.courseRepository = courseRepository;
        this.participationRepository = participationRepository;
        this.submissionService = submissionService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.zipFileService = zipFileService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.dataExportRepository = dataExportRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
    }

    public DataExport requestDataExport(User user) throws IOException {
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        dataExport.setUser(user);
        dataExport.setRequestDate(ZonedDateTime.now());
        if (!Files.exists(dataExportPath)) {
            Files.createDirectories(dataExportPath);
        }
        dataExportRepository.save(dataExport);
        workingDirectory = Files.createTempDirectory(dataExportPath, "data-export-working-dir");
        dataExport.setDataExportState(DataExportState.IN_CREATION);
        dataExportRepository.save(dataExport);
        var dataExportPath = createDataExport(user);
        dataExport.setFilePath(dataExportPath.toString());
        // sending the email will be part of a follow-up, for now this just implies export finished
        dataExport.setCreationDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;

    }

    public DataExport downloadDataExport(long userId, long dataExportId) {
        var dataExport = dataExportRepository.findByIdElseThrow(dataExportId);
        var user = userRepository.findByIdElseThrow(userId);
        if (!Objects.equals(dataExport.getUser().getId(), user.getId())) {
            throw new AccessForbiddenException("Data export doesn't belong to user");
        }
        dataExport.setDownloadDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;

    }

    private Path createDataExport(User user) throws IOException {
        log.error(user.getGroups().toString());
        var courses = courseRepository.getAllCoursesUserIsMemberOf(authorizationCheckService.isAdmin(user), user.getGroups());
        for (var course : courses) {
            var courseWorkingDir = Files.createTempDirectory(workingDirectory, "course-" + course.getId());
            var outputDir = retrieveOutputDirectoryCreateIfNotExistent(courseWorkingDir);

            var programmingExercises = programmingExerciseRepository
                    .getAllProgrammingExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(), user.getId());
            for (var programmingExercise : programmingExercises) {
                createProgrammingExerciseExport(programmingExercise, courseWorkingDir, outputDir);
            }
            var modelingExercises = modelingExerciseRepository.getAllModelingExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(),
                    user.getId());
            for (var modelingExercise : modelingExercises) {
                createNonProgrammingExerciseExport(modelingExercise, courseWorkingDir, outputDir);
            }
            var textExercises = textExerciseRepository.getAllTextExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(),
                    user.getId());
            for (var textExercise : textExercises) {
                createNonProgrammingExerciseExport(textExercise, courseWorkingDir, outputDir);
            }
            var fileUploadExercises = fileUploadExerciseRepository
                    .getAllFileUploadExercisesWithEagerParticipationsSubmissionsAndResultsOfUserFromCourseByCourseAndUserId(course.getId(), user.getId());
            for (var fileUploadExercise : fileUploadExercises) {
                createNonProgrammingExerciseExport(fileUploadExercise, courseWorkingDir, outputDir);
            }
            var quizExercises = quizExerciseRepository.getAllQuizExercisesWithEagerParticipationsSubmissionsOfUserFromCourseByCourseAndUserId(course.getId(), user.getId());
            for (var quizExercise : quizExercises) {
                createNonProgrammingExerciseExport(quizExercise, courseWorkingDir, outputDir);
            }
            createCourseZipFile(course, outputDir);
        }
        addGeneralUserInformation(user);
        addGDPRInformationFile();
        return createDataExportZipFile(user.getLogin());

    }

    private void addGDPRInformationFile() throws IOException {
        var outputDir = retrieveOutputDirectoryCreateIfNotExistent(workingDirectory);
        // TODO place this info in a file
        String art15GdprInformation = """
                # General information in accordance with Art. 15 GDPR
                ## Purposes of processing
                The purposes of processing of personal data are teaching and examinations in an university context.

                ## Categories of personal data
                Artemis only processes general personal data

                ## Recipients of personal data
                As operator of the Artemis instance at TUM the Rechnerbetriebsgruppe processes personal data.

                ## Right to lodge a complaint
                According to Art. 15 1(f) GDPR you have the right to lodge a complaint with the supervisory authority

                ## Right of rectification or erasure of personal data
                According to Art. 15 1(e) GDPR you have the right to
                            """;
        Files.writeString(outputDir.resolve("README.md"), art15GdprInformation, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    }

    private void addGeneralUserInformation(User user) throws IOException {
        var outputDir = retrieveOutputDirectoryCreateIfNotExistent(workingDirectory);
        String[] headers = new String[] { "login", "name", "email", "registration number" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(outputDir.resolve("general_user_information.csv")), csvFormat)) {
            printer.printRecord(user.getLogin(), user.getName(), user.getEmail(), user.getRegistrationNumber());
            printer.flush();

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
        var outputDir = retrieveOutputDirectoryCreateIfNotExistent(exerciseWorkingDir);
        programmingExerciseExportService.exportStudentRepositories(programmingExercise, listOfProgrammingExerciseParticipations, repositoryExportOptions, exerciseWorkingDir,
                outputDir, exportRepoErrors);

        zipFileService.createZipFileWithFolderContent(
                outputPath.resolve(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getShortName() + ".zip"), outputDir,
                null);

    }

    private Path retrieveOutputDirectoryCreateIfNotExistent(Path workingDirectory) throws IOException {
        var outputPath = workingDirectory.resolve("output");
        if (!Files.exists(outputPath)) {
            Files.createDirectory(outputPath);

        }
        return outputPath;
    }

    private Path createParticipationsSubmissionsResultsExport(Exercise exercise, Path workingDirectory) throws IOException {
        var exerciseWorkingDir = Files.createTempDirectory(workingDirectory, "exercise-" + exercise.getId());
        var outputDir = retrieveOutputDirectoryCreateIfNotExistent(exerciseWorkingDir);
        for (var participation : exercise.getStudentParticipations()) {
            createParticipationCsvFile(participation, outputDir);
            for (var submission : participation.getSubmissions()) {
                createSubmissionCsvFile(submission, outputDir);
                if (submission instanceof FileUploadSubmission) {
                    copyFileUploadSubmissionFile(FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId()), outputDir);
                }
                else if (submission instanceof TextSubmission textSubmission) {
                    storeTextSubmissionContent(textSubmission, outputDir);
                }
                else if (submission instanceof ModelingSubmission modelingSubmission) {
                    storeModelingSubmissionContent(modelingSubmission, outputDir);
                }
                else if (submission instanceof QuizSubmission) {
                    createCsvForQuizAnswers((QuizExercise) exercise, participation, outputDir);
                }

                createResultsCsvFile(submission, outputDir);
            }
        }
        return exerciseWorkingDir;
    }

    private void createCsvForQuizAnswers(QuizExercise quizExercise, StudentParticipation participation, Path outputDir) throws IOException {
        List<String> headers = new ArrayList<>(List.of("id", "question title", "submission id", "score"));
        Set<QuizQuestion> quizQuestions = quizQuestionRepository.getQuizQuestionsByExerciseId(quizExercise.getId());
        QuizSubmission quizSubmission = null;

        // collect necessary information for csv headers
        for (var submission : participation.getSubmissions()) {
            quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(submission.getId());
            for (var question : quizQuestions) {
                var submittedAnswer = quizSubmission.getSubmittedAnswerForQuestion(question);
                // if this question wasn't answered, the submitted answer is null
                if (submittedAnswer != null) {
                    populateHeaders(submittedAnswer, headers);
                }

            }
        }

        // write csv
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();
        for (var submission : participation.getSubmissions()) {
            try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(outputDir.resolve("quiz_submission_" + submission.getId() + "_answers.csv")), csvFormat)) {
                for (var question : quizQuestions) {
                    var submittedAnswer = quizSubmission.getSubmittedAnswerForQuestion(question);
                    // if this question wasn't answered, the submitted answer is null
                    if (submittedAnswer != null) {
                        printer.printRecord(getSubmittedAnswerStreamToPrint(submittedAnswer, headers));
                    }

                }
                printer.flush();
            }
        }

    }

    void populateHeaders(SubmittedAnswer answer, List<String> headers) {
        if (answer instanceof MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) {
            for (var selectedOption : multipleChoiceSubmittedAnswer.getSelectedOptions()) {
                headers.add("selected option for quiz question " + answer.getQuizQuestion().getTitle());
            }
        }
        else if (answer instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
            for (var submittedText : shortAnswerSubmittedAnswer.getSubmittedTexts()) {
                headers.add("submitted text for quiz question spot " + submittedText.getSpot().getSpotNr());
            }
        }
    }

    Stream<?> getSubmittedAnswerStreamToPrint(SubmittedAnswer submittedAnswer, List<String> headers) {
        var builder = Stream.builder();
        builder.add(submittedAnswer.getId()).add(submittedAnswer.getQuizQuestion().getTitle()).add(submittedAnswer.getSubmission().getId()).add(submittedAnswer.getScoreInPoints());
        if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) {
            for (var selectedOption : multipleChoiceSubmittedAnswer.getSelectedOptions()) {
                headers.add("selected option for quiz question " + submittedAnswer.getQuizQuestion().getTitle());
                builder.add(selectedOption.getText());
            }
        }
        else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
            for (var submittedText : shortAnswerSubmittedAnswer.getSubmittedTexts()) {
                headers.add("submitted text for quiz question spot " + submittedText.getSpot().getSpotNr());
                builder.add(submittedText.getText());
            }

        }
        return builder.build();
    }

    private void storeModelingSubmissionContent(ModelingSubmission modelingSubmission, Path outputDir) throws IOException {
        Files.writeString(outputDir.resolve("modeling_exercise_" + modelingSubmission.getParticipation().getExercise().getSanitizedExerciseTitle() + "_submission.json"),
                modelingSubmission.getModel());
    }

    private void createParticipationCsvFile(StudentParticipation participation, Path outputDir) throws IOException {
        String[] headers = new String[] { "id", "exercise ", "participating student/team", "number of submissions", "presentation score", "due date", "practice mode/test run" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();
        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(outputDir.resolve("participation_" + participation.getId() + ".csv")), csvFormat)) {
            printer.printRecord(participation.getId(), participation.getExercise().getTitle(), participation.getParticipant().getName(), participation.getSubmissions().size(),
                    participation.getPresentationScore(), participation.getIndividualDueDate(), participation.isTestRun());
            printer.flush();

        }

    }

    private void storeTextSubmissionContent(TextSubmission textSubmission, Path outputDir) throws IOException {
        Files.writeString(outputDir.resolve("text_exercise_submission_" + textSubmission.getId() + "_text.txt"), textSubmission.getText());
    }

    private void createResultsCsvFile(Submission submission, Path outputDir) throws IOException {
        String[] headers = new String[] { "id", "exercise ", "submission id", "assessment type", "score", "complaint for result" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();
        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(outputDir.resolve("results_submission_" + submission.getId() + ".csv")), csvFormat)) {
            for (var result : submission.getResults()) {
                if (result != null) {
                    printer.printRecord(result.getId(), result.getSubmission().getParticipation().getExercise().getTitle(), result.getSubmission().getId(),
                            result.getAssessmentType(), result.getScore(), result.hasComplaint());
                }

            }
            printer.flush();

        }

    }

    private void createNonProgrammingExerciseExport(Exercise exercise, Path workingDirectory, Path outputPath) throws IOException {
        Path exerciseWorkingDir = createParticipationsSubmissionsResultsExport(exercise, workingDirectory);
        zipFileService.createZipFileWithFolderContent(
                outputPath.resolve(exercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + exercise.getSanitizedExerciseTitle() + ".zip"),
                retrieveOutputDirectoryCreateIfNotExistent(exerciseWorkingDir), null);

    }

    private void copyFileUploadSubmissionFile(String submissionFilePath, Path outputDir) {
        try {
            FileUtils.copyDirectory(new File(submissionFilePath), outputDir.toFile());
        }
        catch (IOException ignored) {
            // ignore if we cannot retrieve the submitted file, it might no longer exist.
        }
    }

    private void createCourseZipFile(Course course, Path exercisesOutputDir) throws IOException {
        zipFileService.createZipFileWithFolderContent(retrieveOutputDirectoryCreateIfNotExistent(workingDirectory).resolve("course-" + course.getShortName() + ".zip"),
                exercisesOutputDir, null);

    }

    private Path createDataExportZipFile(String userLogin) throws IOException {
        // There should actually never exist more than one data export for a user at a time (once the feature is fully implemented), but to be sure the name is unique, we add the
        // current timestamp
        return zipFileService.createZipFileWithFolderContent(dataExportPath.resolve("data-export-" + userLogin + ZonedDateTime.now().toEpochSecond() + ".zip"),
                retrieveOutputDirectoryCreateIfNotExistent(workingDirectory), null);
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
        }
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();

        try (final CSVPrinter printer = new CSVPrinter(sw, csvFormat)) {
            printer.printRecord(getSubmissionStreamToPrint(submission));

        }
        Files.writeString(outputPath.resolve("participation_" + submission.getParticipation().getId() + "_submission_" + submission.getId() + ".csv"), sw.toString());
    }

    private Stream<?> getSubmissionStreamToPrint(Submission submission) {
        var builder = Stream.builder();
        builder.add(submission.getId()).add(submission.getSubmissionDate()).add(submission.getType()).add(submission.getDurationInMinutes());
        if (submission instanceof ProgrammingSubmission programmingSubmission) {
            builder.add(programmingSubmission.getCommitHash());

        }
        else if (submission instanceof TextSubmission textSubmission) {
            builder.add(textSubmission.getText());
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            builder.add(modelingSubmission.getModel());
        }
        else if (submission instanceof QuizSubmission quizSubmission) {
            builder.add(quizSubmission.getScoreInPoints());
        }
        return builder.build();
    }

}
