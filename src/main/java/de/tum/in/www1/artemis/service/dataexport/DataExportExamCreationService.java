package de.tum.in.www1.artemis.service.dataexport;

import static de.tum.in.www1.artemis.service.dataexport.DataExportExerciseCreationService.CSV_FILE_EXTENSION;
import static de.tum.in.www1.artemis.service.dataexport.DataExportUtil.createDirectoryIfNotExistent;
import static de.tum.in.www1.artemis.service.dataexport.DataExportUtil.retrieveCourseDirPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;

/**
 * A service to create the data export for exams the user has participated in.
 */
@Service
public class DataExportExamCreationService {

    private static final String EXAM_DIRECTORY_PREFIX = "exam_";

    private final StudentExamRepository studentExamRepository;

    private final DataExportExerciseCreationService dataExportExerciseCreationService;

    private final ExamService examService;

    public DataExportExamCreationService(StudentExamRepository studentExamRepository, DataExportExerciseCreationService dataExportExerciseCreationService,
            ExamService examService) {
        this.studentExamRepository = studentExamRepository;
        this.dataExportExerciseCreationService = dataExportExerciseCreationService;
        this.examService = examService;
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
                createDirectoryIfNotExistent(courseDirPath);
                var examDirectoryName = EXAM_DIRECTORY_PREFIX + examTitle + "_" + studentExam.getId();
                var examWorkingDir = Files.createDirectories(courseDirPath.resolve(examDirectoryName));
                createStudentExamExport(studentExam, examWorkingDir);
            }
        }
    }

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

    private void addExamScores(StudentExam studentExam, Path examWorkingDir) throws IOException {
        var studentExamGrade = examService.getStudentExamGradeForDataExport(studentExam);
        var studentResult = studentExamGrade.studentResult();
        List<String> headers = new ArrayList<>();
        var examResults = getExamResultsStreamToPrint(studentResult, headers);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();
        try (final CSVPrinter printer = new CSVPrinter(
                Files.newBufferedWriter(examWorkingDir.resolve(EXAM_DIRECTORY_PREFIX + studentExam.getId() + "_result" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(examResults);
            printer.flush();
        }
    }

    private Stream<?> getExamResultsStreamToPrint(ExamScoresDTO.StudentResult studentResult, List<String> headers) {
        var builder = Stream.builder();
        if (studentResult.overallPointsAchieved() != null) {
            builder.add(studentResult.overallPointsAchieved());
            headers.add("overall points");
        }
        if (studentResult.hasPassed() != null) {
            builder.add(studentResult.hasPassed());
            headers.add("passed");
        }
        if (studentResult.overallGrade() != null) {
            builder.add(studentResult.overallGrade());
            headers.add("overall grade");
        }
        if (studentResult.gradeWithBonus() != null) {
            builder.add(studentResult.gradeWithBonus());
            headers.add("grade with bonus");
        }
        if (studentResult.overallScoreAchieved() != null) {
            builder.add(studentResult.overallScoreAchieved());
            headers.add("overall score (%)");
        }
        return builder.build();
    }

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
