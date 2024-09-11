package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;
import static de.tum.cit.aet.artemis.core.service.export.DataExportUtil.createDirectoryIfNotExistent;
import static de.tum.cit.aet.artemis.core.service.export.DataExportUtil.retrieveCourseDirPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO;

/**
 * A service to create the data export for exams the user has participated in.
 * This includes exercise participations and general information such as working time.
 * Results are only included if the results are already published.
 */
@Profile(PROFILE_CORE)
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
        Map<Course, List<StudentExam>> studentExamsPerCourse = studentExamRepository
                .findAllWithExercisesSubmissionPolicyParticipationsSubmissionsResultsAndFeedbacksByUserId(userId).stream()
                .collect(Collectors.groupingBy(studentExam -> studentExam.getExam().getCourse()));

        for (var entry : studentExamsPerCourse.entrySet()) {
            for (var studentExam : entry.getValue()) {
                var exam = studentExam.getExam();
                var examTitle = exam.getSanitizedExamTitle();
                var courseDirPath = retrieveCourseDirPath(workingDirectory, exam.getCourse());
                var examsDirPath = courseDirPath.resolve("exams");
                createDirectoryIfNotExistent(examsDirPath);
                var examDirectoryName = EXAM_DIRECTORY_PREFIX + examTitle + "_" + studentExam.getId();
                var examWorkingDirPath = examsDirPath.resolve(examDirectoryName);
                createDirectoryIfNotExistent(examWorkingDirPath);
                createStudentExamExport(studentExam, examWorkingDirPath);
            }
        }
    }

    /**
     * Creates the data export for the given student exam.
     * <p>
     * This includes extracting all exercise participations, general exam information such as working time, and the results if the results are published.
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
                dataExportExerciseCreationService.createProgrammingExerciseExport(programmingExercise, examWorkingDir, studentExam.getUser());
            }
            else {
                dataExportExerciseCreationService.createNonProgrammingExerciseExport(exercise, examWorkingDir, studentExam.getUser());
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
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();
        try (final CSVPrinter printer = new CSVPrinter(
                Files.newBufferedWriter(examWorkingDir.resolve(EXAM_DIRECTORY_PREFIX + studentExam.getId() + "_result" + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(examResults);
            printer.flush();
        }
    }

    /**
     * Returns a stream of the exam results that should be included in the exam results CSV file.
     *
     * @param studentResult        the result belonging to the student exam
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
     * <p>
     * This includes information such as if the exam was started, if it is a test exam, when it was started, if it was submitted, when it was submitted, the working time, and the
     * individual end of the working time.
     *
     * @param studentExam    the student exam for which the information should be added
     * @param examWorkingDir the directory in which the information should be stored
     */
    private void addGeneralExamInformation(StudentExam studentExam, Path examWorkingDir) throws IOException {
        List<String> headers = new ArrayList<>();
        var generalExamInformation = getGeneralExamInformationStreamToPrint(studentExam, headers);
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();

        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(examWorkingDir.resolve(EXAM_DIRECTORY_PREFIX + studentExam.getId() + CSV_FILE_EXTENSION)), csvFormat)) {
            printer.printRecord(generalExamInformation);
            printer.flush();
        }
    }

    /**
     * Returns a stream of the general exam information that should be included in the general exam information CSV file.
     * Do not include information if it is not available, this means null.
     *
     * @param studentExam the student exam for which the information should be added
     * @param headers     a list containing the column headers that should be included in the CSV file
     * @return a stream of information that should be included in the general exam information CSV file
     */
    private Stream<?> getGeneralExamInformationStreamToPrint(StudentExam studentExam, List<String> headers) {
        var builder = Stream.builder();
        if (studentExam.isStarted() != null) {
            builder.add(studentExam.isStarted());
            headers.add("started");
        }
        headers.add("test exam");
        builder.add(studentExam.isTestExam());
        if (studentExam.getStartedDate() != null) {
            builder.add(studentExam.getStartedDate());
            headers.add("started at");
        }
        if (studentExam.isSubmitted() != null) {
            builder.add(studentExam.isSubmitted());
            headers.add("submitted");
        }
        if (studentExam.getSubmissionDate() != null) {
            builder.add(studentExam.getSubmissionDate());
            headers.add("submitted at");
        }
        if (studentExam.getWorkingTime() != null) {
            builder.add(studentExam.getWorkingTime() / 60);
            headers.add("working time (in minutes)");
        }
        if (studentExam.getIndividualEndDate() != null) {
            builder.add(studentExam.getIndividualEndDate());
            headers.add("individual end date");
        }
        return builder.build();
    }

}
