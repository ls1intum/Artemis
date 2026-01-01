package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseExamExportErrorCause;
import de.tum.cit.aet.artemis.core.domain.CourseExamExportState;
import de.tum.cit.aet.artemis.core.domain.CourseOperationType;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.core.service.course.CourseOperationProgressService;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.fileupload.api.FileSubmissionExportApi;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseWithSubmissionsExportService;
import de.tum.cit.aet.artemis.text.api.TextSubmissionExportApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Service Implementation for exporting courses and exams.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseExamExportService {

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    private static final Logger log = LoggerFactory.getLogger(CourseExamExportService.class);

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ZipFileService zipFileService;

    private final Optional<TextSubmissionExportApi> textSubmissionExportApi;

    private final Optional<FileSubmissionExportApi> fileSubmissionExportApi;

    private final ModelingExerciseWithSubmissionsExportService modelingExerciseWithSubmissionsExportService;

    private final QuizExerciseWithSubmissionsExportService quizExerciseWithSubmissionsExportService;

    private final FileService fileService;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final WebsocketMessagingService websocketMessagingService;

    private final CourseStudentDataExportService courseStudentDataExportService;

    private final CourseOperationProgressService progressService;

    public CourseExamExportService(ProgrammingExerciseExportService programmingExerciseExportService, ZipFileService zipFileService, FileService fileService,
            Optional<TextSubmissionExportApi> textSubmissionExportApi, Optional<FileSubmissionExportApi> fileSubmissionExportApi,
            ModelingExerciseWithSubmissionsExportService modelingExerciseWithSubmissionsExportService,
            QuizExerciseWithSubmissionsExportService quizExerciseWithSubmissionsExportService, WebsocketMessagingService websocketMessagingService,
            Optional<ExamRepositoryApi> examRepositoryApi, CourseStudentDataExportService courseStudentDataExportService, CourseOperationProgressService progressService) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.zipFileService = zipFileService;
        this.fileSubmissionExportApi = fileSubmissionExportApi;
        this.fileService = fileService;
        this.textSubmissionExportApi = textSubmissionExportApi;
        this.modelingExerciseWithSubmissionsExportService = modelingExerciseWithSubmissionsExportService;
        this.quizExerciseWithSubmissionsExportService = quizExerciseWithSubmissionsExportService;
        this.websocketMessagingService = websocketMessagingService;
        this.examRepositoryApi = examRepositoryApi;
        this.courseStudentDataExportService = courseStudentDataExportService;
        this.progressService = progressService;
    }

    private static final int TOTAL_ARCHIVE_STEPS = 4;

    /**
     * Exports the entire course into a single zip file that is saved in the directory specified
     * by outputDir.
     *
     * @param course       The course to export
     * @param outputDir    The directory where the exported course is saved
     * @param exportErrors List of failures that occurred during the export
     * @return Path to the zip file
     */
    public Optional<Path> exportCourse(Course course, Path outputDir, List<String> exportErrors) {
        ZonedDateTime startedAt = ZonedDateTime.now();
        int stepsCompleted = 0;

        // Used for sending export progress notifications to instructors
        var notificationTopic = "/topic/courses/" + course.getId() + "/export-course";
        notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of("Creating temporary directories..."), null);

        // Start progress tracking
        progressService.startOperation(course.getId(), CourseOperationType.ARCHIVE, "Creating directories", TOTAL_ARCHIVE_STEPS);

        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        var courseDirName = course.getShortName() + "-" + course.getTitle() + "-" + timestamp;
        String cleanCourseDirName = FileUtil.sanitizeFilename(courseDirName);
        List<ArchivalReportEntry> reportData = new ArrayList<>();

        // Create a temporary directory that will contain the files that will be zipped
        Path tmpCourseDir = Path.of(courseArchivesDirPath, "_temp-exports_", cleanCourseDirName);
        try {
            Files.createDirectories(tmpCourseDir);
        }
        catch (IOException e) {
            String message = "Failed to export course " + course.getId() + " because the temporary directory: " + tmpCourseDir + " cannot be created.";
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.COMPLETED_WITH_ERRORS, List.of(message),
                    CourseExamExportErrorCause.DIR_NOT_CREATED.toString());
            progressService.failOperation(course.getId(), CourseOperationType.ARCHIVE, "Creating directories", stepsCompleted, TOTAL_ARCHIVE_STEPS, 0, startedAt, message,
                    calculateArchiveProgress(stepsCompleted));
            logMessageAndAppendToList(message, exportErrors, e);
            return Optional.empty();
        }

        try {
            // Step 1: Export course exercises and exams
            progressService.updateProgress(course.getId(), CourseOperationType.ARCHIVE, "Exporting exercises", stepsCompleted, TOTAL_ARCHIVE_STEPS, 0, 0, 0, startedAt,
                    calculateArchiveProgress(stepsCompleted));
            List<Path> exportedFiles = exportCourseAndExamExercises(notificationTopic, course, tmpCourseDir.toString(), exportErrors, reportData);
            stepsCompleted++;

            if (exportedFiles.isEmpty()) {
                String message = "Did not export course " + course.getId() + " because there are no exercises/exams to export.";
                exportErrors.add(message);
                notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.COMPLETED_WITH_ERRORS, List.of(message),
                        CourseExamExportErrorCause.NOTHING_TO_EXPORT.toString());
                progressService.failOperation(course.getId(), CourseOperationType.ARCHIVE, "Exporting exercises", stepsCompleted, TOTAL_ARCHIVE_STEPS, 0, startedAt, message,
                        calculateArchiveProgress(stepsCompleted));
                return Optional.empty();
            }

            // Step 2: Export student data
            progressService.updateProgress(course.getId(), CourseOperationType.ARCHIVE, "Exporting student data", stepsCompleted, TOTAL_ARCHIVE_STEPS, 0, 0, 0, startedAt,
                    calculateArchiveProgress(stepsCompleted));
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of("Exporting student data..."), null);
            List<Path> studentDataFiles = courseStudentDataExportService.exportAllStudentData(course.getId(), tmpCourseDir, exportErrors);
            exportedFiles.addAll(studentDataFiles);
            stepsCompleted++;

            // Step 3: Write report and error file
            progressService.updateProgress(course.getId(), CourseOperationType.ARCHIVE, "Writing reports", stepsCompleted, TOTAL_ARCHIVE_STEPS, 0, 0, 0, startedAt,
                    calculateArchiveProgress(stepsCompleted));
            try {
                exportedFiles.add(writeReport(reportData, tmpCourseDir));
                exportedFiles.add(writeFile(exportErrors, tmpCourseDir, "exportErrors.txt"));
            }
            catch (IOException ex) {
                log.error("Could not write report file for course {} due to the exception ", course.getId(), ex);
            }
            stepsCompleted++;

            // Step 4: Create zip file
            progressService.updateProgress(course.getId(), CourseOperationType.ARCHIVE, "Creating archive", stepsCompleted, TOTAL_ARCHIVE_STEPS, 0, 0, 0, startedAt,
                    calculateArchiveProgress(stepsCompleted));
            Optional<Path> exportedCourse = zipExportedExercises(outputDir, exportErrors, notificationTopic, tmpCourseDir);
            stepsCompleted++;

            progressService.completeOperation(course.getId(), CourseOperationType.ARCHIVE, TOTAL_ARCHIVE_STEPS, exportErrors.size(), startedAt);
            log.info("Successfully exported course {}. The zip file is located at: {}", course.getId(), exportedCourse.orElse(null));
            return exportedCourse;
        }
        catch (Exception e) {
            log.error("Failed to archive course {}", course.getId(), e);
            progressService.failOperation(course.getId(), CourseOperationType.ARCHIVE, "Archive failed", stepsCompleted, TOTAL_ARCHIVE_STEPS, 0, startedAt, e.getMessage(),
                    calculateArchiveProgress(stepsCompleted));
            throw e;
        }
    }

    /**
     * Calculates the progress percentage for archive operations based on completed steps.
     * Archive operations use simple step-based progress since they don't have the same
     * complexity-based weighting as delete/reset operations.
     *
     * @param stepsCompleted the number of steps completed
     * @return the progress percentage (0-100)
     */
    private double calculateArchiveProgress(int stepsCompleted) {
        return (double) stepsCompleted / TOTAL_ARCHIVE_STEPS * 100.0;
    }

    private Optional<Path> zipExportedExercises(Path outputDir, List<String> exportErrors, String notificationTopic, Path tmpDir) {
        // Zip all exported exercises into a single zip file.
        notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of("Done exporting exercises. Creating course zip..."), null);
        Path courseZip = outputDir.resolve(tmpDir.getFileName() + ".zip");
        var exportedCourse = createCourseZipFile(courseZip, List.of(tmpDir), exportErrors);

        // Delete temporary directory used for zipping
        fileService.scheduleDirectoryPathForRecursiveDeletion(tmpDir, 1);

        var exportState = exportErrors.isEmpty() ? CourseExamExportState.COMPLETED : CourseExamExportState.COMPLETED_WITH_WARNINGS;
        notifyUserAboutExerciseExportState(notificationTopic, exportState, exportErrors, null);
        return exportedCourse;
    }

    /**
     * Exports an exam into a single zip file that is saved in the directory specified
     * by outputDir.
     *
     * @param exam         The exam to export
     * @param outputDir    The directory where the exported exam is saved
     * @param exportErrors List of failures that occurred during the export
     * @return Path to the zip file
     */
    public Optional<Path> exportExam(Exam exam, Path outputDir, List<String> exportErrors) {
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        // Used for sending export progress notifications to instructors
        var notificationTopic = "/topic/exams/" + exam.getId() + "/export";
        notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of("Creating temporary directories..."), null);

        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        var examDirName = exam.getId() + "-" + exam.getTitle() + "-" + timestamp;
        var cleanExamDirName = FileUtil.sanitizeFilename(examDirName);
        List<ArchivalReportEntry> reportData = new ArrayList<>();

        // Create a temporary directory that will contain the files that will be zipped
        Path tempExamsDir = Path.of(courseArchivesDirPath, "_temp-exports_", cleanExamDirName);
        try {
            Files.createDirectories(tempExamsDir);
        }
        catch (IOException e) {
            String message = "Failed to export exam " + exam.getId() + " because the temporary directory: " + tempExamsDir + " cannot be created.";
            logMessageAndAppendToList(message, exportErrors, e);
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.COMPLETED_WITH_ERRORS, List.of(message),
                    CourseExamExportErrorCause.DIR_NOT_CREATED.toString());
            return Optional.empty();
        }

        // Export exam exercises
        notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of("Preparing to export exam exercises..."), null);
        var exercises = api.findAllExercisesWithDetailsByExamId(exam.getId());
        List<Path> exportedExercises = exportExercises(notificationTopic, exercises, tempExamsDir, 0, exercises.size(), exportErrors, reportData);

        // Write report and error file
        try {
            exportedExercises.add(writeReport(reportData, tempExamsDir));
            exportedExercises.add(writeFile(exportErrors, tempExamsDir, "exportErrors.txt"));
        }
        catch (IOException ex) {
            log.error("Could not write report file for exam {} due to the exception ", exam.getId(), ex);
        }

        Optional<Path> exportedExamPath = zipExportedExercises(outputDir, exportErrors, notificationTopic, tempExamsDir);

        log.info("Successfully exported exam {}. The zip file is located at: {}", exam.getId(), exportedExamPath.orElse(null));
        return exportedExamPath;
    }

    /**
     * Exports the exercises of the course and of each exam belonging to the course.
     *
     * @param notificationTopic the topic used to notify the client
     * @param course            The course to export
     * @param outputDir         the directory that will be used to store the exported data
     * @param exportErrors      any errors that occurred during the export
     * @param reportData        list of all exercises and their statistics
     * @return list of zip files
     */
    private List<Path> exportCourseAndExamExercises(String notificationTopic, Course course, String outputDir, List<String> exportErrors, List<ArchivalReportEntry> reportData) {
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of("Preparing to export course exercises and exams..."), null);

        // Get every course and exam exercise
        Set<Exercise> courseExercises = course.getExercises();

        // Retrieve exams of the course and get exercises for each exam
        List<Exam> courseExams = api.findByCourseId(course.getId());

        // Calculate the amount of exercises for all exams
        var examExercises = courseExams.stream().map(exam -> api.findAllExercisesWithDetailsByExamId(exam.getId())).flatMap(Collection::stream).collect(Collectors.toSet());

        int totalExercises = courseExercises.size() + examExercises.size();
        int progress = 0;

        // Export exercises of the course
        var exportedFiles = exportCourseExercises(notificationTopic, course, outputDir, progress, totalExercises, exportErrors, reportData);
        progress += courseExercises.size();

        // Export the exercises of the course exams
        List<Path> exportedExercises = exportExams(notificationTopic, courseExams, outputDir, progress, totalExercises, exportErrors, reportData);
        if (!exportedExercises.isEmpty()) {
            exportedFiles.addAll(new ArrayList<>(exportedExercises));
        }

        // Add total to report
        reportData.add(new ArchivalReportEntry(null, "Total Exercises", totalExercises, exportedFiles.size(), 0));

        return exportedFiles;
    }

    /**
     * Exports all exercises of the course and adds them into the directory
     * outputDir/exercises/
     *
     * @param notificationTopic  the topic used to notify the client
     * @param course             The course where the exercises are located
     * @param outputDir          The directory that will be used to store the exercises subdirectory
     * @param progress           The previous export progress that should be used as a base
     * @param totalExerciseCount The total amount of exercises that will be exported
     * @param exportErrors       List of failures that occurred during the export
     * @param reportData         List of all exercises and their statistics
     * @return List of paths for the exported exercises
     */
    private List<Path> exportCourseExercises(String notificationTopic, Course course, String outputDir, int progress, int totalExerciseCount, List<String> exportErrors,
            List<ArchivalReportEntry> reportData) {
        log.info("Exporting course exercises for course {} and title {}", course.getId(), course.getTitle());

        Path exercisesDir = Path.of(outputDir, "course-exercises");
        try {
            Files.createDirectory(exercisesDir);
            return exportExercises(notificationTopic, course.getExercises(), exercisesDir, progress, totalExerciseCount, exportErrors, reportData);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create course exercise directory" + exercisesDir + ".", exportErrors, e);
            return List.of();
        }
    }

    /**
     * Exports all exams of the course by zipping each one separately and add them into the directory
     * outputDir/exams/
     *
     * @param notificationTopic  the topic used to notify the client
     * @param exams              The exams that will be exported
     * @param outputDir          The directory that will be used to store the exams
     * @param progress           The previous export progress that should be used as a base
     * @param totalExerciseCount The total amount of exercises that will be exported
     * @param exportErrors       List of failures that occurred during the export
     * @param reportData         list of all exercises and their statistics
     * @return List of paths
     */
    private List<Path> exportExams(String notificationTopic, List<Exam> exams, String outputDir, int progress, int totalExerciseCount, List<String> exportErrors,
            List<ArchivalReportEntry> reportData) {
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        Optional<Exam> firstExam = exams.stream().findFirst();
        if (firstExam.isEmpty()) {
            log.warn("Skipping exam export since the course does not have any exams");
            return List.of();
        }

        Course course = firstExam.get().getCourse();
        log.info("Export course exams for course {} and title {}", course.getId(), course.getTitle());

        int currentProgress = progress;
        Path examsDir = null;
        try {
            // Create the exams' directory that will contain the exported exams
            examsDir = Path.of(outputDir, "exams");
            Files.createDirectory(examsDir);

            // Export each exam. We first fetch its exercises and then export them.
            var exportedExams = new ArrayList<Path>();
            for (var exam : exams) {
                var examExercises = api.findAllExercisesWithDetailsByExamId(exam.getId());
                var exportedExam = exportExam(notificationTopic, exam, examExercises, examsDir.toString(), currentProgress, totalExerciseCount, exportErrors, reportData);
                exportedExams.addAll(exportedExam);
                currentProgress += examExercises.size();
            }
            return exportedExams;
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create course exams directory " + examsDir + ".", exportErrors, e);
            return List.of();
        }
    }

    /**
     * Exports an exam and adds the zip file into the directory specified by outputDir.
     *
     * @param notificationTopic  the topic used to notify the client
     * @param exam               The exam to export
     * @param examExercises      the exercises that belong to the exam
     * @param outputDir          The directory that will be used to store the exam
     * @param progress           The previous export progress that should be used as a base
     * @param totalExerciseCount The total amount of exercises that will be exported
     * @param exportErrors       List of failures that occurred during the export
     * @param reportData         List of all exercises and their statistics
     * @return List of paths
     */
    private List<Path> exportExam(String notificationTopic, Exam exam, Set<Exercise> examExercises, String outputDir, int progress, int totalExerciseCount,
            List<String> exportErrors, List<ArchivalReportEntry> reportData) {
        log.info("Export course exam {}", exam.getId());

        Path examDir = null;
        try {
            // Create exam directory.
            String cleanExamTitle = FileUtil.sanitizeFilename(exam.getId() + "-" + exam.getTitle());
            examDir = Path.of(outputDir, cleanExamTitle);
            Files.createDirectory(examDir);

            // We retrieve every exercise from each exercise group and flatten the list.
            return exportExercises(notificationTopic, examExercises, examDir, progress, totalExerciseCount, exportErrors, reportData);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create exam directory " + examDir + ".", exportErrors, e);
            return List.of();
        }
    }

    /**
     * Exports the specified exercises by creating a zip file for each exercise.
     * The zip files are created in the directory specified by outputDir. The function returns
     * a list of exercise ids that couldn't be exported.
     *
     * @param notificationTopic  the topic used to notify the client
     * @param exercises          The exercises to export
     * @param outputDir          The path to a directory that will be used to store the zipped files.
     * @param progress           The previous export progress that should be used as a base
     * @param totalExerciseCount The total amount of exercises that will be exported
     * @param exportErrors       List of failures that occurred during the export
     * @param reportData         List of all exercises and their statistics
     * @return List of paths of the exported exercises
     */
    private List<Path> exportExercises(String notificationTopic, Set<Exercise> exercises, Path outputDir, int progress, int totalExerciseCount, List<String> exportErrors,
            List<ArchivalReportEntry> reportData) {
        List<Path> exportedExercises = new ArrayList<>();
        int currentProgress = progress;

        // Sort exercises by id.
        List<Exercise> sortedExercises = new ArrayList<>(exercises);
        sortedExercises.sort(Comparator.comparing(DomainObject::getId));

        // Export exercises
        for (var exercise : sortedExercises) {
            log.info("Exporting {} exercise {} with id {} ", exercise.getType(), exercise.getTitle(), exercise.getId());

            // Notify the user after the progress
            currentProgress++;
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of(currentProgress + "/" + totalExerciseCount + " done"), null);
            // add the id because the exercise title might not be unique, leading to the same directory
            // name for two exercises and the second exercise overriding the first one
            var exerciseExportDir = outputDir.resolve(exercise.getSanitizedExerciseTitle() + "_" + exercise.getId());
            try {
                Files.createDirectory(exerciseExportDir);
            }
            catch (IOException e) {
                log.error("Failed to create directory for exercise {} with id {}: {} Going to skip this exercise", exercise.getTitle(), exercise.getId(), e.getMessage());
                continue;
            }

            // Export options
            var submissionsExportOptions = SubmissionExportOptionsDTO.exportAll();
            try {
                // Export programming exercise
                switch (exercise) {
                    case ProgrammingExercise programmingExercise ->
                        // Download the repositories' template, solution, tests and students' repositories
                        programmingExerciseExportService.exportProgrammingExerciseForArchival(programmingExercise, exportErrors, Optional.of(exerciseExportDir), reportData)
                                .ifPresent(exportedExercises::add);
                    case FileUploadExercise fileUploadExercise -> fileSubmissionExportApi.ifPresent(api -> exportedExercises
                            .add(api.exportFileUploadExerciseWithSubmissions(fileUploadExercise, submissionsExportOptions, exerciseExportDir, exportErrors, reportData)));
                    case TextExercise textExercise -> textSubmissionExportApi.ifPresent(api -> exportedExercises
                            .add(api.exportTextExerciseWithSubmissions(textExercise, submissionsExportOptions, exerciseExportDir, exportErrors, reportData)));
                    case ModelingExercise modelingExercise -> exportedExercises.add(modelingExerciseWithSubmissionsExportService
                            .exportModelingExerciseWithSubmissions(modelingExercise, submissionsExportOptions, exerciseExportDir, exportErrors, reportData));
                    case QuizExercise quizExercise ->
                        exportedExercises.add(quizExerciseWithSubmissionsExportService.exportExerciseWithSubmissions(quizExercise, exerciseExportDir, exportErrors, reportData));
                    default -> logMessageAndAppendToList(
                            "Failed to export exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + "): Exercise type not supported for export", exportErrors, null);
                }
            }
            catch (Exception e) {
                logMessageAndAppendToList("Failed to export exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + "): " + e.getMessage(), exportErrors, e);
            }
        }
        return exportedExercises.stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Creates a zip file out of all the files and directories inside courseDirPath and saves it to
     * the directory specified by outputDirPath
     *
     * @param outputZipFile The path to the zip file that will be created
     * @param filesToZip    the files to zip together
     * @return The path to the zip file
     */
    private Optional<Path> createCourseZipFile(Path outputZipFile, List<Path> filesToZip, List<String> exportErrors) {
        try {
            // Create the parent directories if they don't exist otherwise the zip file cannot be created.
            Path parentDir = outputZipFile.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            zipFileService.createZipFile(outputZipFile, filesToZip);
            log.info("Successfully created zip file: {}", outputZipFile);
            return Optional.of(outputZipFile);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create zip file" + outputZipFile + ".", exportErrors, e);
            return Optional.empty();
        }
    }

    private void logMessageAndAppendToList(String message, List<String> messageList, Exception ex) {
        log.error(message, ex);
        messageList.add(message);
    }

    /***
     * Sends a message notifying the user about the current state of the exercise export
     *
     * @param topic       The topic to send the notification to
     * @param exportState The export state
     * @param messages    optional messages to send
     */
    private void notifyUserAboutExerciseExportState(String topic, CourseExamExportState exportState, List<String> messages, String subMessage) {
        Map<String, String> payload = new HashMap<>();
        payload.put("exportState", exportState.toString());
        payload.put("message", String.join("\n", messages));
        payload.put("subMessage", subMessage);

        var mapper = new ObjectMapper();
        try {
            websocketMessagingService.sendMessage(topic, mapper.writeValueAsString(payload));
        }
        catch (IOException e) {
            log.info("Couldn't notify the user about the exercise export state for topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Writes the given report data in a file "report.csv" in the given output directory
     *
     * @param data      report data to write
     * @param outputDir path to the directory to write the file in
     * @return the output file's path
     * @throws IOException if any error occurs
     */
    private Path writeReport(List<ArchivalReportEntry> data, Path outputDir) throws IOException {
        List<String> lines = data.stream().map(ArchivalReportEntry::toString).collect(Collectors.toCollection(ArrayList::new));
        lines.addFirst(ArchivalReportEntry.getHeadline());
        return writeFile(lines, outputDir, "report.csv");
    }

    /**
     * Writes the given lines in a file with the given name in the given output directory
     *
     * @param data      line to write
     * @param outputDir directory to create the file in
     * @param fileName  name of the file to write to
     * @return the created file
     * @throws IOException if any error occurs
     */
    private Path writeFile(List<String> data, Path outputDir, String fileName) throws IOException {
        Path outputFile = outputDir.resolve(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (String line : data) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        }
        return outputFile;
    }
}
