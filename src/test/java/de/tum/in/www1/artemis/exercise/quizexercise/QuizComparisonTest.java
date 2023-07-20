package de.tum.in.www1.artemis.exercise.quizexercise;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.domain.quiz.compare.DnDMapping;
import de.tum.in.www1.artemis.domain.quiz.compare.SAMapping;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.service.exam.StudentExamService;

class QuizComparisonTest {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Test
    void compareCourseQuizSubmittedAnswers() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, futureTimestamp, futureFutureTimestamp, QuizMode.INDIVIDUAL);

        creatSubmissionsForQuizQuestionsAndAssert(quizExercise);
    }

    @Test
    void compareExamQuizSubmittedAnswers() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ExamFactory.generateExamWithExerciseGroup(course, true);
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exam.getExerciseGroups().get(0));

        creatSubmissionsForQuizQuestionsAndAssert(quizExercise);
    }

    void creatSubmissionsForQuizQuestionsAndAssert(QuizExercise quizExercise) {
        long id = 1L;
        for (var question : quizExercise.getQuizQuestions()) {
            id = setQuizQuestionIds(question, id);

            var submittedAnswer1 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);
            var submittedAnswer2 = QuizExerciseFactory.generateSubmittedAnswerFor(question, false);
            var submittedAnswer3 = QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question);
            var submittedAnswer4 = QuizExerciseFactory.generateSubmittedAnswerFor(question, false);

            assertThat(compare(submittedAnswer1, submittedAnswer1)).isTrue();
            assertThat(compare(submittedAnswer1, submittedAnswer2)).isFalse();
            assertThat(compare(submittedAnswer2, submittedAnswer1)).isFalse();
            assertThat(compare(submittedAnswer1, submittedAnswer3)).isFalse();
            assertThat(compare(submittedAnswer1, submittedAnswer4)).isFalse();

            assertThat(compare(submittedAnswer2, submittedAnswer3)).isFalse();
            assertThat(compare(submittedAnswer2, submittedAnswer4)).isTrue();
            assertThat(compare(submittedAnswer4, submittedAnswer2)).isTrue();

            assertThat(compare(submittedAnswer3, submittedAnswer4)).isFalse();
        }
    }

    Long setQuizQuestionIds(QuizQuestion question, Long id) {
        if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
            for (var item : dragAndDropQuestion.getDragItems()) {
                item.setId(id);
                id++;
            }

            for (var location : dragAndDropQuestion.getDropLocations()) {
                location.setId(id);
                id++;
            }

        }
        else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
            for (var spot : shortAnswerQuestion.getSpots()) {
                spot.setId(id);
                id++;
            }

        }
        else if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
            for (var answerOption : multipleChoiceQuestion.getAnswerOptions()) {
                answerOption.setId(id);
                id++;
            }
        }
        return id;
    }

    boolean compare(SubmittedAnswer answer1, SubmittedAnswer answer2) {
        if (answer1 instanceof DragAndDropSubmittedAnswer submittedAnswer1 && answer2 instanceof DragAndDropSubmittedAnswer submittedAnswer2) {
            return StudentExamService.isContentEqualTo(submittedAnswer1, submittedAnswer2);
        }
        else if (answer1 instanceof MultipleChoiceSubmittedAnswer submittedAnswer1 && answer2 instanceof MultipleChoiceSubmittedAnswer submittedAnswer2) {
            return StudentExamService.isContentEqualTo(submittedAnswer1, submittedAnswer2);
        }
        else if (answer1 instanceof ShortAnswerSubmittedAnswer submittedAnswer1 && answer2 instanceof ShortAnswerSubmittedAnswer submittedAnswer2) {
            return StudentExamService.isContentEqualTo(submittedAnswer1, submittedAnswer2);
        }
        throw new RuntimeException("Not supported");
    }

    @Test
    void simpleCompareDnDMapping() {
        DnDMapping m1 = new DnDMapping(1, 2);
        DnDMapping m2 = new DnDMapping(3, 4);
        DnDMapping m3 = new DnDMapping(4, 3); // item id and location id of m2 switched
        DnDMapping m4 = new DnDMapping(1, 2); // same as m1
        DnDMapping m5 = new DnDMapping(3, 4);  // same as m2
        DnDMapping m6 = new DnDMapping(4, 3);  // same as m3

        Set<DnDMapping> set1 = Set.of(m1, m2);
        Set<DnDMapping> set2 = Set.of(m2, m1);  // same as set1
        Set<DnDMapping> set3 = Set.of(m1, m3);
        Set<DnDMapping> set4 = Set.of(m4, m2); // same as set1
        Set<DnDMapping> set5 = Set.of(m5, m4); // same as set1
        Set<DnDMapping> set6 = Set.of(m1, m2, m3);
        Set<DnDMapping> set7 = Set.of(m3, m4, m5);  // same as set6
        Set<DnDMapping> set8 = Set.of(m1, m6);  // same as set3

        assertThat(Objects.equals(set1, set2)).isTrue();
        assertThat(Objects.equals(set1, set3)).isFalse();
        assertThat(Objects.equals(set1, set4)).isTrue();
        assertThat(Objects.equals(set1, set5)).isTrue();
        assertThat(Objects.equals(set1, set6)).isFalse();
        assertThat(Objects.equals(set1, set7)).isFalse();
        assertThat(Objects.equals(set1, set8)).isFalse();

        assertThat(Objects.equals(set2, set3)).isFalse();
        assertThat(Objects.equals(set2, set4)).isTrue();
        assertThat(Objects.equals(set2, set5)).isTrue();
        assertThat(Objects.equals(set2, set6)).isFalse();
        assertThat(Objects.equals(set2, set7)).isFalse();
        assertThat(Objects.equals(set2, set8)).isFalse();

        assertThat(Objects.equals(set3, set4)).isFalse();
        assertThat(Objects.equals(set3, set5)).isFalse();
        assertThat(Objects.equals(set3, set6)).isFalse();
        assertThat(Objects.equals(set3, set7)).isFalse();
        assertThat(Objects.equals(set3, set8)).isTrue();

        assertThat(Objects.equals(set4, set5)).isTrue();  // both same as set1
        assertThat(Objects.equals(set4, set6)).isFalse();
        assertThat(Objects.equals(set4, set7)).isFalse();
        assertThat(Objects.equals(set4, set8)).isFalse();

        assertThat(Objects.equals(set5, set6)).isFalse();
        assertThat(Objects.equals(set5, set7)).isFalse();
        assertThat(Objects.equals(set5, set8)).isFalse();

        assertThat(Objects.equals(set6, set7)).isTrue();
        assertThat(Objects.equals(set6, set8)).isFalse();

        assertThat(Objects.equals(set7, set8)).isFalse();
    }

    @Test
    void simpleCompareSAMapping() {
        SAMapping m1 = new SAMapping(1, "2");
        SAMapping m2 = new SAMapping(3, "4");
        SAMapping m3 = new SAMapping(6, "5"); // item id and location id of m2 switched
        SAMapping m4 = new SAMapping(1, "2"); // same as m1
        SAMapping m5 = new SAMapping(3, "4"); // same as m2
        SAMapping m6 = new SAMapping(3, null);
        SAMapping m7 = new SAMapping(3, null);

        Set<SAMapping> set1 = Set.of(m1, m2);
        Set<SAMapping> set2 = Set.of(m2, m1);  // same as set1
        Set<SAMapping> set3 = Set.of(m1, m3);
        Set<SAMapping> set4 = Set.of(m4, m2);  // same as set1
        Set<SAMapping> set5 = Set.of(m5, m4);  // same as set1
        Set<SAMapping> set6 = Set.of(m1, m5, m3);
        Set<SAMapping> set7 = Set.of(m3, m4, m2); // same as set6
        Set<SAMapping> set8 = Set.of(m2, m6);
        Set<SAMapping> set9 = Set.of(m5, m7); // same as set8

        assertThat(Objects.equals(set1, set2)).isTrue();
        assertThat(Objects.equals(set1, set3)).isFalse();
        assertThat(Objects.equals(set1, set4)).isTrue();
        assertThat(Objects.equals(set1, set5)).isTrue();
        assertThat(Objects.equals(set1, set6)).isFalse();
        assertThat(Objects.equals(set1, set7)).isFalse();
        assertThat(Objects.equals(set1, set8)).isFalse();
        assertThat(Objects.equals(set1, set9)).isFalse();

        assertThat(Objects.equals(set2, set3)).isFalse();
        assertThat(Objects.equals(set2, set4)).isTrue();
        assertThat(Objects.equals(set2, set5)).isTrue();
        assertThat(Objects.equals(set2, set6)).isFalse();
        assertThat(Objects.equals(set2, set7)).isFalse();
        assertThat(Objects.equals(set2, set8)).isFalse();
        assertThat(Objects.equals(set2, set9)).isFalse();

        assertThat(Objects.equals(set3, set4)).isFalse(); // different from all other sets
        assertThat(Objects.equals(set3, set5)).isFalse();
        assertThat(Objects.equals(set3, set6)).isFalse();
        assertThat(Objects.equals(set3, set7)).isFalse();
        assertThat(Objects.equals(set3, set8)).isFalse();
        assertThat(Objects.equals(set3, set9)).isFalse();

        assertThat(Objects.equals(set4, set5)).isTrue(); // both same as set1
        assertThat(Objects.equals(set4, set6)).isFalse();
        assertThat(Objects.equals(set4, set7)).isFalse();
        assertThat(Objects.equals(set4, set8)).isFalse();
        assertThat(Objects.equals(set4, set9)).isFalse();

        assertThat(Objects.equals(set5, set6)).isFalse();
        assertThat(Objects.equals(set5, set7)).isFalse();
        assertThat(Objects.equals(set5, set8)).isFalse();
        assertThat(Objects.equals(set5, set9)).isFalse();

        assertThat(Objects.equals(set6, set7)).isTrue();
        assertThat(Objects.equals(set4, set8)).isFalse();
        assertThat(Objects.equals(set4, set9)).isFalse();

        assertThat(Objects.equals(set8, set9)).isTrue();
    }
}
