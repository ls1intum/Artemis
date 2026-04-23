package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.export.CourseStudentDataExportService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class CourseStudentDataExportServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "studentdataexport";

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseStudentDataExportService courseStudentDataExportService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    private Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        // The CourseUtilService.addCourseWithExercisesAndSubmissions method expects 4 tutors
        userUtilService.addUsers(TEST_PREFIX, 3, 4, 0, 1);
        tempDir = tempFileUtilService.createTempDirectory("student-data-export-test");
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var paths = Files.walk(tempDir)) {
                paths.sorted((a, b) -> -a.compareTo(b)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    }
                    catch (IOException e) {
                        // ignore
                    }
                });
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_withExercisesAndSubmissions() throws IOException {
        // Create course with exercises and submissions (without complaints)
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 3, 2, 2, 0, false, 0, "");

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        // Verify no errors occurred
        assertThat(errors).isEmpty();

        // Verify student-data directory was created
        Path studentDataDir = tempDir.resolve("student-data");
        assertThat(studentDataDir).exists();

        // Verify course-scores.csv was created
        Path courseScoresFile = studentDataDir.resolve("course-scores.csv");
        assertThat(courseScoresFile).exists();

        // Read and verify CSV content
        List<String> lines = Files.readAllLines(courseScoresFile);
        assertThat(lines).isNotEmpty();

        // Verify header contains expected columns
        String header = lines.getFirst();
        assertThat(header).contains("Name", "Username", "Email", "RegistrationNumber");
        assertThat(header).contains("Overall Points", "Overall Score", "Max Points");

        // Verify statistics section exists
        assertThat(lines.stream().anyMatch(line -> line.contains("---Statistics---"))).isTrue();
        assertThat(lines.stream().anyMatch(line -> line.startsWith("Max,"))).isTrue();
        assertThat(lines.stream().anyMatch(line -> line.startsWith("Average,"))).isTrue();
        assertThat(lines.stream().anyMatch(line -> line.startsWith("Median,"))).isTrue();
        assertThat(lines.stream().anyMatch(line -> line.startsWith("Std Dev,"))).isTrue();
        assertThat(lines.stream().anyMatch(line -> line.startsWith("Participants,"))).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_withGradingScale() throws IOException {
        // Create course with exercises and submissions (without complaints, using empty suffix to match user groups)
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 2, 2, 2, 0, false, 0, "");

        // Add grading scale to the course
        // intervals: 0-50 (fail), 50-70, 70-85, 85-100 (pass)
        double[] intervals = { 0, 50, 70, 85, 100 };
        GradingScale gradingScale = gradingScaleUtilService.generateGradingScale(4, intervals, true, 1, Optional.empty(), course, null, null);
        gradingScaleRepository.save(gradingScale);

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        assertThat(errors).isEmpty();

        // Verify course-scores.csv contains grade columns
        Path courseScoresFile = tempDir.resolve("student-data").resolve("course-scores.csv");
        assertThat(courseScoresFile).exists();

        List<String> lines = Files.readAllLines(courseScoresFile);
        String header = lines.getFirst();
        assertThat(header).contains("Grade", "Passed");

        // Verify grade distribution file was created
        Path gradeDistributionFile = tempDir.resolve("student-data").resolve("course-grade-distribution.csv");
        assertThat(gradeDistributionFile).exists();

        List<String> gradeDistLines = Files.readAllLines(gradeDistributionFile);
        assertThat(gradeDistLines).isNotEmpty();

        // Verify grade distribution header
        String gradeDistHeader = gradeDistLines.getFirst();
        assertThat(gradeDistHeader).contains("Type", "Value", "LowerBound", "UpperBound", "IsPassingGrade", "StudentCount", "Percentage");

        // Verify both GRADE and INTERVAL types are present
        assertThat(gradeDistLines.stream().anyMatch(line -> line.startsWith("GRADE,"))).isTrue();
        assertThat(gradeDistLines.stream().anyMatch(line -> line.startsWith("INTERVAL,"))).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_emptyCourse() {
        // Create empty course without exercises
        Course course = courseUtilService.createCourse();

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        // Verify no errors occurred (empty course should not cause errors)
        assertThat(errors).isEmpty();

        // Verify student-data directory was created
        Path studentDataDir = tempDir.resolve("student-data");
        assertThat(studentDataDir).exists();

        // Course-scores.csv should not exist for empty course
        Path courseScoresFile = studentDataDir.resolve("course-scores.csv");
        assertThat(courseScoresFile).doesNotExist();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportAllStudentData_createsAllExpectedFiles() throws IOException {
        // Create course with various data (without complaints, using empty suffix to match user groups)
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 2, 2, 2, 0, false, 0, "");

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        assertThat(errors).isEmpty();

        Path studentDataDir = tempDir.resolve("student-data");
        assertThat(studentDataDir).exists();

        // Verify course-scores.csv exists (main export)
        assertThat(studentDataDir.resolve("course-scores.csv")).exists();

        // Verify participation-results.csv exists
        assertThat(studentDataDir.resolve("participation-results.csv")).exists();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_verifyPerExerciseColumns() throws IOException {
        // Create course with specific number of exercises (using empty suffix to match user groups)
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 2, 2, 2, 0, false, 0, "");

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        assertThat(errors).isEmpty();

        Path courseScoresFile = tempDir.resolve("student-data").resolve("course-scores.csv");
        assertThat(courseScoresFile).exists();

        List<String> lines = Files.readAllLines(courseScoresFile);
        String header = lines.getFirst();

        // Verify per-exercise columns exist (Points and Score for each exercise)
        assertThat(header).contains("Points");
        assertThat(header).contains("Score");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_verifyRoundingWithDefaultAccuracy() throws IOException {
        // Create course with exercises and submissions (default accuracy is 1 decimal place)
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 2, 2, 2, 0, false, 0, "");
        assertThat(course.getAccuracyOfScores()).isEqualTo(1);

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        assertThat(errors).isEmpty();

        Path courseScoresFile = tempDir.resolve("student-data").resolve("course-scores.csv");
        List<String> lines = Files.readAllLines(courseScoresFile);

        // Find data rows (skip header and statistics)
        List<String> dataRows = lines.stream().filter(line -> !line.startsWith("Name,") && !line.startsWith("---") && !line.startsWith("Max,") && !line.startsWith("Average,")
                && !line.startsWith("Median,") && !line.startsWith("Std Dev,") && !line.startsWith("Participants,") && !line.isEmpty()).toList();

        assertThat(dataRows).isNotEmpty();

        // Verify that numeric values have at most 1 decimal place (course default)
        for (String row : dataRows) {
            String[] values = row.split(",");
            for (String value : values) {
                if (isNumericValue(value)) {
                    assertThat(countDecimalPlaces(value)).as("Value %s should have at most 1 decimal place", value).isLessThanOrEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_verifyRoundingWithTwoDecimalPlaces() throws IOException {
        // Create course with exercises and submissions
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 2, 2, 2, 0, false, 0, "");

        // Update course accuracy to 2 decimal places
        course.setAccuracyOfScores(2);
        courseRepository.save(course);

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        assertThat(errors).isEmpty();

        Path courseScoresFile = tempDir.resolve("student-data").resolve("course-scores.csv");
        List<String> lines = Files.readAllLines(courseScoresFile);

        // Find data rows (skip header and statistics)
        List<String> dataRows = lines.stream().filter(line -> !line.startsWith("Name,") && !line.startsWith("---") && !line.startsWith("Max,") && !line.startsWith("Average,")
                && !line.startsWith("Median,") && !line.startsWith("Std Dev,") && !line.startsWith("Participants,") && !line.isEmpty()).toList();

        assertThat(dataRows).isNotEmpty();

        // Verify that numeric values have at most 2 decimal places
        for (String row : dataRows) {
            String[] values = row.split(",");
            for (String value : values) {
                if (isNumericValue(value)) {
                    assertThat(countDecimalPlaces(value)).as("Value %s should have at most 2 decimal places", value).isLessThanOrEqualTo(2);
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_verifyRoundingWithZeroDecimalPlaces() throws IOException {
        // Create course with exercises and submissions
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 2, 2, 2, 0, false, 0, "");

        // Update course accuracy to 0 decimal places (whole numbers only)
        course.setAccuracyOfScores(0);
        courseRepository.save(course);

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        assertThat(errors).isEmpty();

        Path courseScoresFile = tempDir.resolve("student-data").resolve("course-scores.csv");
        List<String> lines = Files.readAllLines(courseScoresFile);

        // Find data rows (skip header and statistics)
        List<String> dataRows = lines.stream().filter(line -> !line.startsWith("Name,") && !line.startsWith("---") && !line.startsWith("Max,") && !line.startsWith("Average,")
                && !line.startsWith("Median,") && !line.startsWith("Std Dev,") && !line.startsWith("Participants,") && !line.isEmpty()).toList();

        assertThat(dataRows).isNotEmpty();

        // Verify that numeric values have 0 decimal places (whole numbers)
        for (String row : dataRows) {
            String[] values = row.split(",");
            for (String value : values) {
                if (isNumericValue(value)) {
                    assertThat(countDecimalPlaces(value)).as("Value %s should have 0 decimal places", value).isEqualTo(0);
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportCourseScores_verifyStatisticsRowsRounding() throws IOException {
        // Create course with exercises and submissions
        Course course = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 2, 2, 2, 0, false, 0, "");

        // Use 1 decimal place (default)
        assertThat(course.getAccuracyOfScores()).isEqualTo(1);

        List<String> errors = new ArrayList<>();
        courseStudentDataExportService.exportAllStudentData(course.getId(), tempDir, errors);

        assertThat(errors).isEmpty();

        Path courseScoresFile = tempDir.resolve("student-data").resolve("course-scores.csv");
        List<String> lines = Files.readAllLines(courseScoresFile);

        // Find statistics rows
        List<String> statisticsRows = lines.stream()
                .filter(line -> line.startsWith("Max,") || line.startsWith("Average,") || line.startsWith("Median,") || line.startsWith("Std Dev,")).toList();

        assertThat(statisticsRows).isNotEmpty();

        // Verify that numeric values in statistics rows have at most 1 decimal place
        for (String row : statisticsRows) {
            String[] values = row.split(",");
            for (String value : values) {
                if (isNumericValue(value)) {
                    assertThat(countDecimalPlaces(value)).as("Statistics value %s should have at most 1 decimal place", value).isLessThanOrEqualTo(1);
                }
            }
        }
    }

    /**
     * Checks if a string represents a numeric value (integer or decimal).
     */
    private boolean isNumericValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Counts the number of decimal places in a numeric string.
     * Returns 0 if the number has no decimal point.
     */
    private int countDecimalPlaces(String value) {
        if (value == null || !value.contains(".")) {
            return 0;
        }
        String decimalPart = value.substring(value.indexOf('.') + 1);
        // Remove trailing zeros for accurate count
        decimalPart = decimalPart.replaceAll("0+$", "");
        return decimalPart.length();
    }
}
