package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;

/**
 * Service Implementation for exporting courses.
 */
@Service
public class CourseExportService {

    private final Logger log = LoggerFactory.getLogger(CourseExportService.class);

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ZipFileService zipFileService;

    private final FileService fileService;

    private final CourseRepository courseRepository;

    private final FileUploadSubmissionExportService fileUploadSubmissionExportService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final ModelingSubmissionExportService modelingSubmissionExportService;

    private ExamService examService;

    public CourseExportService(ProgrammingExerciseExportService programmingExerciseExportService, ZipFileService zipFileService, FileService fileService,
            CourseRepository courseRepository, FileUploadSubmissionExportService fileUploadSubmissionExportService, TextSubmissionExportService textSubmissionExportService,
            ModelingSubmissionExportService modelingSubmissionExportService) {
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.zipFileService = zipFileService;
        this.fileService = fileService;
        this.courseRepository = courseRepository;
        this.fileUploadSubmissionExportService = fileUploadSubmissionExportService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.modelingSubmissionExportService = modelingSubmissionExportService;
    }

    @Autowired
    // break the dependency cycle
    public void setExamService(ExamService examService) {
        this.examService = examService;
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

        var courseDirName = course.getShortName() + "-" + course.getTitle() + "-" + ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Create a temporary directory that will contain the files that will be zipped
        var courseDirPath = Path.of("./exports", courseDirName);
        try {
            Files.createDirectories(courseDirPath);
        }
        catch (IOException e) {
            var error = "Failed to export course " + course.getId() + " because the temporary directory: " + courseDirPath + " cannot be created.";
            exportErrors.add(error);
            log.info(error);
            return Optional.empty();
        }

        // Export course exercises and exams
        exportCourseExercises(course, courseDirPath.toString(), exportErrors);
        exportCourseExams(course, courseDirPath.toString(), exportErrors);

        // Zip them together
        var exportedCoursePath = createCourseZipFile(courseDirPath, Path.of(outputDir), exportErrors);

        log.info("Successfully exported course {}. The zip file is located at: {}", course.getId(), exportedCoursePath);
        return exportedCoursePath;
    }

    /**
     * Exports all exercises of the course and adds them into the directory
     * outputDir/exercises/
     *
     * @param course       The course where the exercises are located
     * @param outputDir    The directory that will be used to store the exercises subdirectory
     * @param exportErrors List of failures that occurred during the export
     */
    private void exportCourseExercises(Course course, String outputDir, List<String> exportErrors) {
        Path exercisesDir = Path.of(outputDir, "exercises");
        try {
            Files.createDirectory(exercisesDir);
            exportExercises(course.getExercises(), exercisesDir.toString(), exportErrors);
        }
        catch (IOException e) {
            var error = "Failed to create course exercise directory" + exercisesDir + ".";
            log.info(error);
            exportErrors.add(error);
        }
    }

    /**
     * Exports all exams of the course by zipping each one separately and adds them into the directory
     * outputDir/exams/
     *
     * @param course       The course where the exercises are located
     * @param outputDir    The directory that will be used to store the exams
     * @param exportErrors List of failures that occurred during the export
     */
    private void exportCourseExams(Course course, String outputDir, List<String> exportErrors) {
        Path examsDir = Path.of(outputDir, "exams");
        try {
            Files.createDirectory(examsDir);

            // Lazy load the exams of the course.
            var courseWithExams = courseRepository.findWithEagerLecturesAndExamsById(course.getId());
            if (courseWithExams.isPresent()) {
                var exams = courseWithExams.get().getExams();
                exams.forEach(exam -> exportExam(exam.getId(), examsDir.toString(), exportErrors));
            }
            else {
                var error = "Failed to export exams of course " + course.getId() + " because the course doesn't exist.";
                log.info(error);
                exportErrors.add(error);
            }
        }
        catch (IOException e) {
            var error = "Failed to create course exams directory " + examsDir + ".";
            log.info(error);
            exportErrors.add(error);
        }
    }

    /**
     * Exports an exam and adds the zip file into the directory specified by outputDir.
     *
     * @param examId       The id of the exam to archive
     * @param outputDir    The directory that will be used to store the exam
     * @param exportErrors List of failures that occurred during the export
     */
    private void exportExam(long examId, String outputDir, List<String> exportErrors) {
        var exam = examService.findOneWithExerciseGroupsAndExercises(examId);
        Path examDir = Path.of(outputDir, exam.getId() + "-" + exam.getTitle());
        try {
            Files.createDirectory(examDir);
            // We retrieve every exercise from each exercise group and flatten the list.
            var exercises = examService.getAllExercisesOfExam(examId);
            exportExercises(exercises, examDir.toString(), exportErrors);
        }
        catch (IOException e) {
            var error = "Failed to create exam directory " + examDir + ".";
            log.info(error);
            exportErrors.add(error);
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
    private void exportExercises(Set<Exercise> exercises, String outputDir, List<String> exportErrors) {
        exercises.forEach(exercise -> {

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
                    // TODO: Quiz submssions aren't supported yet
                    return;
                }
                else {
                    // Exercise is not supported so return
                    return;
                }
            }
            catch (IOException e) {
                var error = "Failed to export exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + "): " + e.getMessage();
                log.info(error);
                exportErrors.add(error);
            }

            // Exported submissions are stored somewhere else so we move the generated zip file into the
            // outputDir (directory where the files needed for the course archive are stored).
            if (exportedSubmissionsFileOrEmpty.isPresent()) {
                var exportedSubmissionsFile = exportedSubmissionsFileOrEmpty.get();
                try {
                    Files.move(exportedSubmissionsFile.toPath(), Path.of(outputDir, exportedSubmissionsFile.getName()));
                }
                catch (IOException e) {
                    var error = "Failed to move file " + exportedSubmissionsFile.toPath() + " to " + outputDir + ".";
                    log.info(error);
                    exportErrors.add(error);
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
            var error = "Failed to create zip file at " + zippedFile + ".";
            log.info(error);
            exportErrors.add(error);
            return Optional.empty();
        }
        finally {
            fileService.scheduleForDirectoryDeletion(courseDirPath, 1);
        }
    }
}
