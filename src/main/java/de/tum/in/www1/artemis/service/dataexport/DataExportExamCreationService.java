package de.tum.in.www1.artemis.service.dataexport;

import static de.tum.in.www1.artemis.service.dataexport.DataExportExerciseCreationService.CSV_FILE_EXTENSION;
import static de.tum.in.www1.artemis.service.dataexport.DataExportUtil.createDirectoryIfNotExistent;
import static de.tum.in.www1.artemis.service.dataexport.DataExportUtil.retrieveCourseDirPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;

/**
 * A service to create the data export for exams the user has participated in.
 * This includes exercise participations and general information such as working time.
 * If the results are published, the results are also included.
 */
@Service
public class DataExportExamCreationService {

    private static final String EXAM_DIRECTORY_PREFIX = "exam_";

    private final StudentExamRepository studentExamRepository;

    private final DataExportExerciseCreationService dataExportExerciseCreationService;

    private final ExamService examService;

    private final GradingScaleRepository gradingScaleRepository;

    public DataExportExamCreationService(StudentExamRepository studentExamRepository, DataExportExerciseCreationService dataExportExerciseCreationService, ExamService examService,
            GradingScaleRepository gradingScaleRepository) {
        this.studentExamRepository = studentExamRepository;
        this.dataExportExerciseCreationService = dataExportExerciseCreationService;
        this.examService = examService;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    /**
     * Creates a data export for all exams of the given user grouped by course.
     *
     * @param userId           the id of the user for whom the data export should be created
     * @param workingDirectory the directory in which the data export should be created
     * @throws IOException if an error occurs while accessing the file system
     */
    public void createExportForExams(long userId, Path workingDirectory) throws IOException {
        Map<Course, List<StudentExam>> studentExamsPerCourse = studentExamRepository.findAllWithExercisesParticipationsSubmissionsResultsAndFeedbacksByUserId(userId).stream()
                .collect(Collectors.groupingBy(studentExam -> studentExam.getExam().getCourse()));

        for (var entry : studentExamsPerCourse.entrySet()) {
            for (var studentExam : entry.getValue()) {
                var exam = studentExam.getExam();
                var examTitle = exam.getSanitizedExamTitle();
                var courseDirPath = retrieveCourseDirPath(workingDirectory, exam.getCourse());
                var examsDirPath = courseDirPath.resolve("exams");
                createDirectoryIfNotExistent(examsDirPath);
                var examDirectoryName = EXAM_DIRECTORY_PREFIX + examTitle + "_" + studentExam.getId();
                var examWorkingDir = Files.createDirectories(examsDirPath.resolve(examDirectoryName));
                createStudentExamExport(studentExam, examWorkingDir);
            }
        }
    }

    /**
     * Creates the data export for the given student exam.
     * This includes extracting all exercise participations, general exam information such as working time and the results if the results are published.
     *
     * @param studentExam    the student exam belonging to the user for which the data export should be created
     * @param examWorkingDir the directory in which the information about the exam should be stored
     */
    private void createStudentExamExport(StudentExam studentExam, Path examWorkingDir) throws IOException {
        for (var exercise : studentExam.getExercises()) {
            // since the behavior is undefined if multiple student exams for the same exam and student combination exist, the exercise can be null
            if (exercise == null) {
                continue;
            }
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                dataExportExerciseCreationService.createProgrammingExerciseExport(programmingExercise, examWorkingDir, studentExam.getUser().getId());
            }
            else {
                dataExportExerciseCreationService.createNonProgrammingExerciseExport(exercise, examWorkingDir, studentExam.getUser().getId());
            }
        }
        // leave out the results if the results are not published yet to avoid leaking information through the data export
        if (studentExam.areResultsPublishedYet()) {
            addExamScores(studentExam, examWorkingDir);
        }
        addGeneralExamInformation(studentExam, examWorkingDir);
    }

    /**
     * Adds the results of the student to the data export.
     *
     * @param studentExam    the student exam for which the results should be added
     * @param examWorkingDir the directory in which the results should be stored
     */
    private void addExamScores(StudentExam studentExam, Path examWorkingDir) throws IOException {
        var studentExamGrade = examService.getStudentExamGradeForDataExport(studentExam);
        var studentResult = studentExamGrade.studentResult();
        var gradingScale = gradingScaleRepository.findByExamId(studentExam.getExam().getId());
        List<String> headers = new ArrayList<>();
        var examResults = getExamResultsStreamToPrint(studentResult, headers, gradingScale);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();
        try (final CSVPrinter printer = new CSVPrinter(
                Files.newBufferedWriter(examWorkingDir.resolve(EXAM_DIRECTORY_PREFIX + studentExam.getId() + "_result" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(examResults);
            printer.flush();
        }
    }

    /**
     * Returns a stream of the exam results that should be included in the exam results CSV file.
     *
     * @param studentResult
     * @param headers              a list containing the column headers that should be included in the CSV file
     * @param gradingScaleOptional the optional grading scale of the exam
     * @return a stream of information that should be included in the exam results CSV file
     */
    private Stream<?> getExamResultsStreamToPrint(ExamScoresDTO.StudentResult studentResult, List<String> headers, Optional<GradingScale> gradingScaleOptional) {
        var builder = Stream.builder();
        if (studentResult.overallPointsAchieved() != null) {
            builder.add(studentResult.overallPointsAchieved());
            headers.add("overall points");
        }
        if (studentResult.hasPassed() != null && gradingScaleOptional.isPresent()) {
            builder.add(studentResult.hasPassed());
            headers.add("passed");
        }
        if (studentResult.overallGrade() != null && gradingScaleOptional.isPresent()) {
            builder.add(studentResult.overallGrade());
            headers.add("overall grade");
        }
        if (studentResult.gradeWithBonus() != null && gradingScaleOptional.isPresent()) {
            builder.add(studentResult.gradeWithBonus());
            headers.add("grade with bonus");
        }
        if (studentResult.overallScoreAchieved() != null) {
            builder.add(studentResult.overallScoreAchieved());
            headers.add("overall score (%)");
        }
        return builder.build();
    }

    /**
     * Adds general information about the student exam to the data export.
     * This includes information such as if the exam was started, if it is a test exam, when it was started, if it was submitted, when it was submitted, the working time and the
     * individual end of the working time.
     *
     * @param studentExam    the student exam for which the information should be added
     * @param examWorkingDir the directory in which the information should be stored
     */
    private void addGeneralExamInformation(StudentExam studentExam, Path examWorkingDir) throws IOException {
        String[] headers = { "started", "testExam", "started at", "submitted", "submitted at", "working time (in minutes)", "individual end date" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers).build();

        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(examWorkingDir.resolve(EXAM_DIRECTORY_PREFIX + studentExam.getId() + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(studentExam.isStarted(), studentExam.isTestExam(), studentExam.getStartedDate(), studentExam.isSubmitted(), studentExam.getSubmissionDate(),
                    studentExam.getWorkingTime() / 60, studentExam.getIndividualEndDate());
            printer.flush();
        }
    }

}
