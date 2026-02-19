package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for notification URL generation, ensuring exam exercises generate correct management/exam URLs
 * instead of the student course exercise URL.
 */
class ExerciseNotificationUrlTest {

    private static final Long COURSE_ID = 1L;

    private static final Long EXERCISE_ID = 10L;

    private static final Long EXAM_ID = 100L;

    private static final Long EXERCISE_GROUP_ID = 50L;

    private static final String COURSE_TITLE = "Test Course";

    private static final String COURSE_ICON = "icon.png";

    private static final String EXERCISE_TITLE = "Test Exercise";

    // Instructor-facing notifications

    @Test
    void testProgrammingTestCasesChangedNotification_courseExercise() {
        var notification = new ProgrammingTestCasesChangedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, null, null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testProgrammingTestCasesChangedNotification_examExercise() {
        var notification = new ProgrammingTestCasesChangedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, EXAM_ID, EXERCISE_GROUP_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/programming-exercises/10");
    }

    @Test
    void testProgrammingTestCasesChangedNotification_examExercise_fromDatabase() {
        var params = Map.of("exerciseId", "10", "exerciseTitle", EXERCISE_TITLE, "examId", "100", "exerciseGroupId", "50");
        var notification = new ProgrammingTestCasesChangedNotification(1L, COURSE_ID, ZonedDateTime.now(), params);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/programming-exercises/10");
    }

    @Test
    void testExerciseUpdatedNotification_examExercise_fromDatabase() {
        var params = Map.of("exerciseId", "10", "exerciseTitle", EXERCISE_TITLE, "examId", "100", "exerciseGroupId", "50", "exerciseType", "modeling");
        var notification = new ExerciseUpdatedNotification(1L, COURSE_ID, ZonedDateTime.now(), params);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/modeling-exercises/10");
    }

    @Test
    void testExerciseAssessedNotification_examExercise_fromDatabase() {
        var params = Map.of("exerciseId", "10", "exerciseTitle", EXERCISE_TITLE, "exerciseType", "programming", "numberOfPoints", "100", "score", "85", "examId", "100");
        var notification = new ExerciseAssessedNotification(1L, COURSE_ID, ZonedDateTime.now(), params);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exams/100");
    }

    @Test
    void testExerciseAssessedNotification_courseExercise_fromDatabase() {
        var params = Map.of("exerciseId", "10", "exerciseTitle", EXERCISE_TITLE, "exerciseType", "programming", "numberOfPoints", "100", "score", "85");
        var notification = new ExerciseAssessedNotification(1L, COURSE_ID, ZonedDateTime.now(), params);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testProgrammingBuildRunUpdateNotification_courseExercise() {
        var notification = new ProgrammingBuildRunUpdateNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, null, null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testProgrammingBuildRunUpdateNotification_examExercise() {
        var notification = new ProgrammingBuildRunUpdateNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, EXAM_ID, EXERCISE_GROUP_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/programming-exercises/10");
    }

    @Test
    void testDuplicateTestCaseNotification_courseExercise() {
        var notification = new DuplicateTestCaseNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "01.01.2025", "01.02.2025", null, null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testDuplicateTestCaseNotification_examExercise() {
        var notification = new DuplicateTestCaseNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "01.01.2025", "01.02.2025", EXAM_ID,
                EXERCISE_GROUP_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/programming-exercises/10");
    }

    @Test
    void testExerciseUpdatedNotification_courseExercise() {
        var notification = new ExerciseUpdatedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, null, null, "text");
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testExerciseUpdatedNotification_examExercise() {
        var notification = new ExerciseUpdatedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, EXAM_ID, EXERCISE_GROUP_ID, "text");
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/text-exercises/10");
    }

    @Test
    void testExerciseUpdatedNotification_examProgrammingExercise() {
        var notification = new ExerciseUpdatedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, EXAM_ID, EXERCISE_GROUP_ID, "programming");
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/programming-exercises/10");
    }

    @Test
    void testNewManualFeedbackRequestNotification_courseExercise() {
        var notification = new NewManualFeedbackRequestNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, null, null, "modeling");
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testNewManualFeedbackRequestNotification_examExercise() {
        var notification = new NewManualFeedbackRequestNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, EXAM_ID, EXERCISE_GROUP_ID, "modeling");
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/course-management/1/exams/100/exercise-groups/50/modeling-exercises/10");
    }

    // Student-facing notifications

    @Test
    void testExerciseAssessedNotification_courseExercise() {
        var notification = new ExerciseAssessedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", 100L, 85L, null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testExerciseAssessedNotification_examExercise() {
        var notification = new ExerciseAssessedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", 100L, 85L, EXAM_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exams/100");
    }

    @Test
    void testNewPlagiarismCaseNotification_courseExercise() {
        var notification = new NewPlagiarismCaseNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", "content", null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testNewPlagiarismCaseNotification_examExercise() {
        var notification = new NewPlagiarismCaseNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", "content", EXAM_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exams/100");
    }

    @Test
    void testNewCpcPlagiarismCaseNotification_courseExercise() {
        var notification = new NewCpcPlagiarismCaseNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", "content", null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testNewCpcPlagiarismCaseNotification_examExercise() {
        var notification = new NewCpcPlagiarismCaseNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", "content", EXAM_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exams/100");
    }

    @Test
    void testPlagiarismCaseVerdictNotification_courseExercise() {
        var notification = new PlagiarismCaseVerdictNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", "POINT_DEDUCTION", null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testPlagiarismCaseVerdictNotification_examExercise() {
        var notification = new PlagiarismCaseVerdictNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, "programming", "POINT_DEDUCTION", EXAM_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exams/100");
    }

    // Edge case: partial exam data should fall back to course URL

    @Test
    void testExerciseUpdatedNotification_partialExamData_noExerciseGroupId() {
        var notification = new ExerciseUpdatedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, EXAM_ID, null, "text");
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testExerciseUpdatedNotification_partialExamData_noExerciseType() {
        var notification = new ExerciseUpdatedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, EXAM_ID, EXERCISE_GROUP_ID, null);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }

    @Test
    void testProgrammingTestCasesChangedNotification_partialExamData_noExamId() {
        var notification = new ProgrammingTestCasesChangedNotification(COURSE_ID, COURSE_TITLE, COURSE_ICON, EXERCISE_ID, EXERCISE_TITLE, null, EXERCISE_GROUP_ID);
        assertThat(notification.getRelativeWebAppUrl()).isEqualTo("/courses/1/exercises/10");
    }
}
