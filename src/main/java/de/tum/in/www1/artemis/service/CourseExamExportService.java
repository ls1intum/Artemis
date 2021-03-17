package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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

        // Create a temporary directory that will contain the files that will be zipped
        var courseDirPath = Path.of("./exports", courseDirName, courseDirName);
        try {
            Files.createDirectories(courseDirPath);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to export course " + course.getId() + " because the temporary directory: " + courseDirPath + " cannot be created.", exportErrors);
            return Optional.empty();
        }

        try {
            // Export course exercises and exams
            exportCourseExercises(notificationTopic, course, courseDirPath.toString(), exportErrors);
            exportCourseExams(notificationTopic, course, courseDirPath.toString(), exportErrors);

            // Zip them together
            var exportedCoursePath = createCourseZipFile(courseDirPath.getParent(), Path.of(outputDir), exportErrors);

            log.info("Successfully exported course {}. The zip file is located at: {}", course.getId(), exportedCoursePath);
            return exportedCoursePath;
        }
        catch (Exception e) {
            logMessageAndAppendToList("Failed to export the entire course " + course.getTitle() + ": " + e.getMessage(), exportErrors);
            return Optional.empty();
        }
        finally {
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.COMPLETED, "");
        }
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

        // Create a temporary directory that will contain the files that will be zipped
        var examDirPath = Path.of("./exports", examDirName, examDirName);
        try {
            Files.createDirectories(examDirPath);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to export exam " + exam.getId() + " because the temporary directory: " + examDirPath + " cannot be created.", exportErrors);
            return Optional.empty();
        }

        try {
            exportExam(notificationTopic, exam.getId(), examDirPath.toString(), exportErrors);

            var exportedExamPath = createCourseZipFile(examDirPath.getParent(), Path.of(outputDir), exportErrors);

            log.info("Successfully exported exam {}. The zip file is located at: {}", exam.getId(), exportedExamPath);
            return exportedExamPath;
        }
        catch (Exception e) {
            logMessageAndAppendToList("Failed to export the exam " + exam.getTitle() + ": " + e.getMessage(), exportErrors);
            return Optional.empty();
        }
        finally {
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.COMPLETED, "");
        }
    }

    /**
     * Exports all exercises of the course and adds them into the directory
     * outputDir/exercises/
     *
     * @param course       The course where the exercises are located
     * @param outputDir    The directory that will be used to store the exercises subdirectory
     * @param exportErrors List of failures that occurred during the export
     */
    private void exportCourseExercises(String notificationTopic, Course course, String outputDir, List<String> exportErrors) {
        Path exercisesDir = Path.of(outputDir, "course-exercises");
        try {
            Files.createDirectory(exercisesDir);
            exportExercises(notificationTopic, course.getExercises(), exercisesDir.toString(), exportErrors);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create course exercise directory" + exercisesDir + ".", exportErrors);
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
    private void exportCourseExams(String notificationTopic, Course course, String outputDir, List<String> exportErrors) {
        Path examsDir = Path.of(outputDir, "exams");
        try {
            Files.createDirectory(examsDir);
            List<Exam> exams = examRepository.findByCourseId(course.getId());
            exams.forEach(exam -> exportExam(notificationTopic, exam.getId(), examsDir.toString(), exportErrors));

        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create course exams directory " + examsDir + ".", exportErrors);
        }
    }

    /**
     * Exports an exam and adds the zip file into the directory specified by outputDir.
     *
     * @param examId       The id of the exam to archive
     * @param outputDir    The directory that will be used to store the exam
     * @param exportErrors List of failures that occurred during the export
     */
    private void exportExam(String notificationTopic, long examId, String outputDir, List<String> exportErrors) {
        var exam = examRepository.findByIdElseThrow(examId);
        Path examDir = Path.of(outputDir, exam.getId() + "-" + exam.getTitle());
        try {
            Files.createDirectory(examDir);
            // We retrieve every exercise from each exercise group and flatten the list.
            var exercises = examRepository.findAllExercisesByExamId(examId);
            exportExercises(notificationTopic, exercises, examDir.toString(), exportErrors);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create exam directory " + examDir + ".", exportErrors);
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
    private void exportExercises(String notificationTopic, Set<Exercise> exercises, String outputDir, List<String> exportErrors) {
        AtomicInteger exportedExercises = new AtomicInteger(0);
        exercises.forEach(exercise -> {
            // Notify the user after the progress
            exportedExercises.addAndGet(1);
            notifyUserAboutExerciseExportState(notificationTopic, CourseExamExportState.RUNNING, exportedExercises + "/" + exercises.size() + " done");

            if (exercise instanceof ProgrammingExercise) {
                programmingExerciseExportService.exportProgrammingExercise((ProgrammingExercise) exercise, outputDir, exportErrors);
                return;
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
                    return;
                }
                else {
                    // Exercise is not supported so return
                    return;
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
                    Files.move(exportedSubmissionsFile.toPath(), Path.of(outputDir, exportedSubmissionsFile.getName()));
                    // Delete the directory where the zip was located before it was moved
                    FileUtils.deleteDirectory(Path.of(exportedSubmissionsFile.getParent()).toFile());
                }
                catch (IOException e) {
                    logMessageAndAppendToList("Failed to move file " + exportedSubmissionsFile.toPath() + " to " + outputDir + ".", exportErrors);
                }
            }
        });
    }

    /**
     * Creates a zip file out of all the files and directories inside courseDirPath and saves it to
     * the directory specified by outputDirPath
     *
     * @param courseDirPath Directory of the contents to zip
     * @return The path to the zip file
     */
    private Optional<Path> createCourseZipFile(Path courseDirPath, Path outputDirPath, List<String> exportErrors) {
        var zippedFile = Path.of(outputDirPath.toString(), courseDirPath.getFileName() + ".zip");
        try {
            // Create course output directory if it doesn't exist
            Files.createDirectories(outputDirPath);
            log.info("Created the directory {} because it didn't exist.", outputDirPath.toString());

            zipFileService.createZipFileWithFolderContent(zippedFile, courseDirPath);
            log.info("Successfully created zip file at: {}", zippedFile);
            return Optional.of(zippedFile);
        }
        catch (IOException e) {
            logMessageAndAppendToList("Failed to create zip file at " + zippedFile + ".", exportErrors);
            return Optional.empty();
        }
        finally {
            fileService.scheduleForDirectoryDeletion(courseDirPath, 1);
        }
    }

    private void logMessageAndAppendToList(String message, List<String> messageList) {
        log.info(message);
        messageList.add(message);
    }

    /***
     * Sends a message notifying the user about the current state of the exercise export
     *
     * @param topic The topic to send the notification to
     * @param exportState The export state
     * @param progress progress message
     */
    private void notifyUserAboutExerciseExportState(String topic, CourseExamExportState exportState, String progress) {
        Map<String, String> message = new HashMap<>();
        message.put("exportState", exportState.toString());
        message.put("progress", progress);

        var mapper = new ObjectMapper();
        try {
            websocketMessagingService.sendMessage(topic, mapper.writeValueAsString(message));
        }
        catch (IOException e) {
            log.info("Couldn't notify the user about the exercise export state for topic {}: {}", topic, e.getMessage());
        }
    }
}
