package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseExportService;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;

/**
 * Service Implementation for exporting courses and exams.
 */
@Service
public class CourseExamExportService {

    private final Logger log = LoggerFactory.getLogger(CourseExamExportService.class);

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ZipFileService zipFileService;

    private final FileService fileService;

    private final FileUploadSubmissionExportService fileUploadSubmissionExportService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final ModelingSubmissionExportService modelingSubmissionExportService;

    private final ExamRepository examRepository;

    private final WebsocketMessagingService websocketMessagingService;

    public CourseExamExportService(ProgrammingExerciseExportService programmingExerciseExportService, ZipFileService zipFileService, FileService fileService,
            FileUploadSubmissionExportService fileUploadSubmissionExportService, TextSubmissionExportService textSubmissionExportService,
            ModelingSubmissionExportService modelingSubmissionExportService, WebsocketMessagingService websocketMessagingService, ExamRepository examRepository) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.zipFileService = zipFileService;
        this.fileService = fileService;
        this.fileUploadSubmissionExportService = fileUploadSubmissionExportService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.modelingSubmissionExportService = modelingSubmissionExportService;
        this.websocketMessagingService = websocketMessagingService;
        this.examRepository = examRepository;
    }

    /**
     * Exports the entire course into a single zip file that is saved in the directory specified
     * by outputDir.
     *
     * @param course       The course to export
     * @param outputDir    The directory where the exported course is saved
     * @param exportErrors List of failures that occurred during the export
     * @return Path to the zip file
     */
    public Optional<Path> exportCourse(Course course, String outputDir, List<String> exportErrors) {
        // Used for sending export progress notifications to instructors
        var notificationTopic = "/topic/courses/" + course.getId() + "/export-course";

        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        var courseDirName = course.getShortName() + "-" + course.getTitle() + "-" + timestamp;
        String cleanCourseDirName = fileService.removeIllegalCharacters(courseDirName);

        // Create a temporary directory that will contain the files that will be zipped
        Path courseDirPath = Path.of("./exports", cleanCourseDirName);
        try {
            Files.createDirectories(courseDirPath);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to export course " + course.getId() + " because the temporary directory: " + courseDirPath + " cannot be created.", exportErrors);
            return Optional.empty();
        }

        // Export course exercises and exams
        List<Path> exportedFiles = new ArrayList<>();
        exportedFiles.addAll(exportCourseExercises(notificationTopic, course, courseDirPath.toString(), exportErrors));
        exportedFiles.addAll(exportCourseExams(notificationTopic, course, courseDirPath.toString(), exportErrors));

        // Zip all exported exercises into a single zip file.
        Path courseZip = Path.of(outputDir, courseDirPath.getFileName() + ".zip");
        var exportedCourse = createCourseZipFile(courseZip, exportedFiles, exportErrors);

        // Delete temporary directory used for zipping
        fileService.scheduleForDirectoryDeletion(courseDirPath, 1);

        var exportState = exportErrors.isEmpty() ? CourseExamExportState.COMPLETED : CourseExamExportState.COMPLETED_WITH_WARNINGS;
        notifyUserAboutExerciseExportState(notificationTopic, exportState, exportErrors);

        log.info("Successfully exported course {}. The zip file is located at: {}", course.getId(), exportedCourse);
        return exportedCourse;
    }

    /**
     * Exports an exam into a single zip file that is saved in the directory specified
     * by outputDir.
     *
     * @param exam       The exam to export
     * @param outputDir    The directory where the exported exam is saved
     * @param exportErrors List of failures that occurred during the export
     * @return Path to the zip file
     */
    public Optional<Path> exportExam(Exam exam, String outputDir, List<String> exportErrors) {
        // Used for sending export progress notifications to instructors
        var notificationTopic = "/topic/exams/" + exam.getId() + "/export";

        var timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-Hmss"));
        var examDirName = exam.getId() + "-" + exam.getTitle() + "-" + timestamp;
        var cleanExamDirName = fileService.removeIllegalCharacters(examDirName);

        // Create a temporary directory that will contain the files that will be zipped
        Path examDirPath = Path.of("./exports", cleanExamDirName, cleanExamDirName);
        try {
            Files.createDirectories(examDirPath);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to export exam " + exam.getId() + " because the temporary directory: " + examDirPath + " cannot be created.", exportErrors);
            return Optional.empty();
        }

        // Export exam exercises
        var exercises = examRepository.findAllExercisesByExamId(exam.getId());
        List<Path> exportedExercises = exportExercises(notificationTopic, exercises, examDirPath.toString(), exportErrors);

        // Zip all exported exercises into a single zip file.
        Path examZip = Path.of(outputDir, examDirPath.getFileName() + ".zip");
        var exportedExamPath = createCourseZipFile(examZip, exportedExercises, exportErrors);

        // Delete temporary directory used for zipping
        fileService.scheduleForDirectoryDeletion(examDirPath, 1);

        var exportState = exportErrors.isEmpty() ? CourseExamExportState.COMPLETED : CourseExamExportState.COMPLETED_WITH_WARNINGS;
        notifyUserAboutExerciseExportState(notificationTopic, exportState, exportErrors);

        log.info("Successfully exported exam {}. The zip file is located at: {}", exam.getId(), exportedExamPath);
        return exportedExamPath;
    }

    /**
     * Exports all exercises of the course and adds them into the directory
     * outputDir/exercises/
     *
     * @param course       The course where the exercises are located
     * @param outputDir    The directory that will be used to store the exercises subdirectory
     * @param exportErrors List of failures that occurred during the export
     */
    private List<Path> exportCourseExercises(String notificationTopic, Course course, String outputDir, List<String> exportErrors) {
        log.info("Exporting course exercises for course {} and title {}", course.getId(), course.getTitle());

        Path exercisesDir = Path.of(outputDir, "course-exercises");
        try {
            Files.createDirectory(exercisesDir);
            return exportExercises(notificationTopic, course.getExercises(), exercisesDir.toString(), exportErrors);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create course exercise directory" + exercisesDir + ".", exportErrors);
            return List.of();
        }
    }

    /**
     * Exports all exams of the course by zipping each one separately and adds them into the directory
     * outputDir/exams/
     *
     * @param course       The course where the exams are located
     * @param outputDir    The directory that will be used to store the exams
     * @param exportErrors List of failures that occurred during the export
     */
    private List<Path> exportCourseExams(String notificationTopic, Course course, String outputDir, List<String> exportErrors) {
        log.info("Export course exams for course {} and title {}", course.getId(), course.getTitle());

        Path examsDir = null;
        try {
            examsDir = Path.of(outputDir, "exams");
            Files.createDirectory(examsDir);

            List<Exam> exams = examRepository.findByCourseId(course.getId());
            Path finalExamsDir = examsDir;

            var exportedExamExercises = exams.stream().map(exam -> exportExam(notificationTopic, exam.getId(), finalExamsDir.toString(), exportErrors));
            return exportedExamExercises.flatMap(Collection::stream).collect(Collectors.toList());
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create course exams directory " + examsDir + ".", exportErrors);
            return List.of();
        }
    }

    /**
     * Exports an exam and adds the zip file into the directory specified by outputDir.
     *
     * @param examId       The id of the exam to archive
     * @param outputDir    The directory that will be used to store the exam
     * @param exportErrors List of failures that occurred during the export
     */
    private List<Path> exportExam(String notificationTopic, long examId, String outputDir, List<String> exportErrors) {
        log.info("Export course exam {}", examId);

        Path examDir = null;
        try {
            // Create exam directory.
            var exam = examRepository.findByIdElseThrow(examId);
            String cleanExamTitle = fileService.removeIllegalCharacters(exam.getId() + "-" + exam.getTitle());
            examDir = Path.of(outputDir, cleanExamTitle);
            Files.createDirectory(examDir);

            // We retrieve every exercise from each exercise group and flatten the list.
            var exercises = examRepository.findAllExercisesByExamId(examId);
            return exportExercises(notificationTopic, exercises, examDir.toString(), exportErrors);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create exam directory " + examDir + ".", exportErrors);
            return List.of();
        }
    }

    /**
     * Exports the specified exercises by creating a zip file for each exercise.
     * The zip files are created in the directory specified by outputDir. The function returns
     * a list of exercise ids that couldn't be exported.
     *
     * @param exercises    The exercises to export
     * @param outputDir    The path to a directory that will be used to store the zipped files.
     * @param exportErrors List of failures that occurred during the export
     */
    private List<Path> exportExercises(String notificationTopic, Set<Exercise> exercises, String outputDir, List<String> exportErrors) {
        AtomicInteger exportedExercises = new AtomicInteger(0);
        return exercises.stream().map(exercise -> {
            log.info("Exporting exercise {} with id {} ", exercise.getTitle(), exercise.getId());

            // Notify the user after the progress
            exportedExercises.addAndGet(1);
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, List.of(exportedExercises + "/" + exercises.size() + " done"));

            // Export programming exercise
            if (exercise instanceof ProgrammingExercise) {
                return programmingExerciseExportService.exportProgrammingExercise((ProgrammingExercise) exercise, Path.of(outputDir), exportErrors);
            }

            // Export the other exercises types

            // Export options
            var submissionsExportOptions = new SubmissionExportOptionsDTO();
            submissionsExportOptions.setExportAllParticipants(true);

            // The zip file containing student submissions for the other exercise types
            Optional<File> exportedSubmissionsFileOrEmpty = Optional.empty();

            try {
                if (exercise instanceof FileUploadExercise) {
                    exportedSubmissionsFileOrEmpty = fileUploadSubmissionExportService.exportStudentSubmissions(exercise.getId(), submissionsExportOptions);

                }
                else if (exercise instanceof TextExercise) {
                    exportedSubmissionsFileOrEmpty = textSubmissionExportService.exportStudentSubmissions(exercise.getId(), submissionsExportOptions);

                }
                else if (exercise instanceof ModelingExercise) {
                    exportedSubmissionsFileOrEmpty = modelingSubmissionExportService.exportStudentSubmissions(exercise.getId(), submissionsExportOptions);
                }
                else if (exercise instanceof QuizExercise) {
                    // TODO: Quiz submissions aren't supported yet
                    return null;
                }
                else {
                    // Exercise is not supported so return
                    return null;
                }
            }
            catch (IOException e) {
                logMessageAndAppendToList("Failed to export exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + "): " + e.getMessage(), exportErrors);
            }

            // Exported submissions are stored somewhere else so we move the generated zip file into the
            // outputDir (directory where the files needed for the course archive are stored).
            if (exportedSubmissionsFileOrEmpty.isPresent()) {
                var exportedSubmissionsFile = exportedSubmissionsFileOrEmpty.get();
                try {
                    Path newExportedSubmissionsFilePath = Path.of(outputDir, exportedSubmissionsFile.getName());
                    Files.move(exportedSubmissionsFile.toPath(), newExportedSubmissionsFilePath);
                    // Delete the directory where the zip was located before it was moved
                    FileUtils.deleteDirectory(Path.of(exportedSubmissionsFile.getParent()).toFile());
                    return newExportedSubmissionsFilePath;
                }
                catch (IOException e) {
                    logMessageAndAppendToList("Failed to move file " + exportedSubmissionsFile.toPath() + " to " + outputDir + ".", exportErrors);
                    return null;
                }
            }
            else {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Creates a zip file out of all the files and directories inside courseDirPath and saves it to
     * the directory specified by outputDirPath
     *
     * @param outputZipFile The path to the zip file that will be created
     * @param filesToZip the files to zip together
     * @return The path to the zip file
     */
    private Optional<Path> createCourseZipFile(Path outputZipFile, List<Path> filesToZip, List<String> exportErrors) {
        try {
            zipFileService.createZipFile(outputZipFile, filesToZip, true);
            log.info("Successfully created zip file: {}", outputZipFile);
            return Optional.of(outputZipFile);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create zip file" + outputZipFile + ".", exportErrors);
            return Optional.empty();
        }
    }

    private void logMessageAndAppendToList(String message, List<String> messageList) {
        log.error(message);
        messageList.add(message);
    }

    /***
     * Sends a message notifying the user about the current state of the exercise export
     *
     * @param topic The topic to send the notification to
     * @param exportState The export state
     * @param messages  optional messages to send
     */
    private void notifyUserAboutExerciseExportState(String topic, CourseExamExportState exportState, List<String> messages) {
        Map<String, String> payload = new HashMap<>();
        payload.put("exportState", exportState.toString());
        payload.put("message", String.join("\n", messages));

        var mapper = new ObjectMapper();
        try {
            websocketMessagingService.sendMessage(topic, mapper.writeValueAsString(payload));
        }
        catch (IOException e) {
            log.info("Couldn't notify the user about the exercise export state for topic {}: {}", topic, e.getMessage());
        }
    }
}
