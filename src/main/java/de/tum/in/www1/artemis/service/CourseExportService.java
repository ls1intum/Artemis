package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;

/**
 * Service Implementation for exporting courses.
 */
@Service
public class CourseExportService {

    private final Logger log = LoggerFactory.getLogger(CourseExportService.class);

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final ZipFileService zipFileService;

    private final FileService fileService;

    private final CourseRepository courseRepository;

    private ExamService examService;

    public CourseExportService(ExerciseService exerciseService, ProgrammingExerciseExportService programmingExerciseExportService, ZipFileService zipFileService,
            FileService fileService, CourseRepository courseRepository) {
        this.exerciseService = exerciseService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.zipFileService = zipFileService;
        this.fileService = fileService;
        this.courseRepository = courseRepository;
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
     * @param course    The course to export
     * @param outputDir The directory where the exported course is saved
     * @return Path to the zip file
     */
    public Optional<Path> exportCourse(Course course, String outputDir) {

        var courseDirName = course.getShortName() + "-" + course.getTitle() + "-" + ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Create a temporary directory that will contain the files that will be zipped
        var courseDirPath = Path.of("./exports", courseDirName);
        try {
            Files.createDirectories(courseDirPath);
        }
        catch (IOException e) {
            log.info("Cannot archive course {} because the temporary directories cannot be created: {}", course.getId(), e.getMessage());
            return Optional.empty();
        }

        // Export course exercises and exams
        exportCourseExercises(course, courseDirPath.toString());
        exportCourseExams(course, courseDirPath.toString());

        var courseArchivePath = createCourseZipFile(courseDirPath, Path.of(outputDir));

        log.info("Successfully archived course {}. The archive is located at: {}", course.getId(), courseArchivePath);

        return courseArchivePath;
    }

    /**
     * Exports all exercises of the course and adds them into the directory
     * outputDir/exercises/
     *
     * @param course    The course where the exercises are located
     * @param outputDir The directory that will be used to store the exercises subdirectory
     */
    private void exportCourseExercises(Course course, String outputDir) {
        Path exercisesDir = Path.of(outputDir, "exercises");
        try {
            Files.createDirectory(exercisesDir);
            exportExercises(course.getExercises(), exercisesDir.toString());
        }
        catch (IOException e) {
            log.info("Failed to create course exercise directory {}: {}", exercisesDir, e.getMessage());
        }
    }

    /**
     * Exports all exams of the course by zipping each one separately and adds them into the directory
     * outputDir/exams/
     *
     * @param course    The course where the exercises are located
     * @param outputDir The directory that will be used to store the exams
     */
    private void exportCourseExams(Course course, String outputDir) {
        Path examsDir = Path.of(outputDir, "exams");
        try {
            Files.createDirectory(examsDir);
            var courseWithExams = courseRepository.findWithEagerLecturesAndExamsById(course.getId());
            if (courseWithExams.isPresent()) {
                var exams = courseWithExams.get().getExams();
                exams.forEach(exam -> exportExam(exam.getId(), examsDir.toString()));
            }
            else {
                log.info("Cannot export exams of course {} because the course doesn't exist", course.getId());
            }
        }
        catch (IOException e) {
            log.info("Failed to create course exams directory {}: {}", examsDir, e.getMessage());
        }
    }

    /**
     * Exports an exam and adds the zip file into the directory specified by outputDir.
     *
     * @param examId    The id of the exam to archive
     * @param outputDir The directory that will be used to store the exam
     */
    private void exportExam(long examId, String outputDir) {
        var exam = examService.findOneWithExerciseGroupsAndExercises(examId);
        Path examDir = Path.of(outputDir, exam.getId() + "-" + exam.getTitle());
        try {
            Files.createDirectory(examDir);
            // We retrieve every exercise from each exercise group and flatten the list.
            var exercises = examService.getAllExercisesOfExam(examId);
            exportExercises(exercises, examDir.toString());
        }
        catch (IOException e) {
            log.info("Failed to create exam directory {}: {}", examDir, e.getMessage());
        }
    }

    /**
     * Exports the specified exercises by creating a zip file for each exercise.
     * The zip files are created in the directory specified by outputDir.
     *
     * @param exercises The exercises to export
     * @param outputDir The path to a directory that will be used to store the zipped files.
     */
    private void exportExercises(Set<Exercise> exercises, String outputDir) {
        ArrayList<Long> exercisesThatFailedToExport = new ArrayList<>();

        exercises.forEach(exercise -> {

            if (exercise instanceof ProgrammingExercise) {
                // We need to lazy load the student participations.
                var participations = exerciseService.findOneWithStudentParticipations(exercise.getId()).getStudentParticipations();
                var programmingParticipations = participations.stream().map(participation -> (ProgrammingExerciseStudentParticipation) participation).collect(Collectors.toList());
                var exportedExercise = programmingExerciseExportService.exportProgrammingExercise((ProgrammingExercise) exercise, programmingParticipations, outputDir);

                if (exportedExercise == null) {
                    exercisesThatFailedToExport.add(exercise.getId());
                    log.info("Cannot export exercise {}", exercise.getId());
                }
            }

            // TODO: Handle other exercise types.
        });

        // Notify that we couldn't export every exercise
        if (!exercisesThatFailedToExport.isEmpty()) {
            var failedExerciseIds = exercisesThatFailedToExport.stream().map(String::valueOf).collect(Collectors.joining(","));
            log.info("The following exercises couldn't be exported {}", failedExerciseIds);
        }
    }

    /**
     * Creates a zip file out of all the files and directories inside courseDirPath and saves it to
     * the directory specified by outputDirPath
     *
     * @param courseDirPath Directory of the contents to zip
     * @return The path to the zip file
     */
    private Optional<Path> createCourseZipFile(Path courseDirPath, Path outputDirPath) {
        var zippedFile = Path.of(outputDirPath.toString(), courseDirPath.getFileName() + ".zip");
        try {
            // Create course output directory if it doesn't exist
            Files.createDirectories(outputDirPath);
            log.info("Created the directorey {} because it didn't exist.", outputDirPath.toString());

            zipFileService.createZipFileWithFolderContent(zippedFile, courseDirPath);
            log.info("Successfully created zip file at: {}", zippedFile);
            return Optional.of(zippedFile);
        }
        catch (IOException e) {
            log.info("Failed to create zip file at {}: {}", zippedFile, e.getMessage());
            return Optional.empty();
        }
        finally {
            fileService.scheduleForDirectoryDeletion(courseDirPath, 1);
        }
    }
}
