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
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.domain.quiz.compare.DnDMapping;
import de.tum.in.www1.artemis.domain.quiz.compare.SAMapping;
import de.tum.in.www1.artemis.service.exam.StudentExamService;

class QuizComparisonTest {

    @Test
    void compareSubmittedAnswers() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, futureTimestamp, futureFutureTimestamp, QuizMode.INDIVIDUAL);

        // TODO: we need to make sure the following objects have proper ids, otherwise the comparison would not work
        // MC: answerOption
        // DnD: dragItem, dropLocation
        // SA: spot

        for (var question : quizExercise.getQuizQuestions()) {
            var submittedAnswer1 = QuizExerciseFactory.generateSubmittedAnswerFor(question, true);
            var submittedAnswer2 = QuizExerciseFactory.generateSubmittedAnswerFor(question, false);
            assertThat(compare(submittedAnswer1, submittedAnswer1)).isTrue();
            assertThat(compare(submittedAnswer1, submittedAnswer2)).isFalse();
        }
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
        DnDMapping m3 = new DnDMapping(5, 6);
        DnDMapping m4 = new DnDMapping(1, 2); // same as m1

        Set<DnDMapping> set1 = Set.of(m1, m2);
        Set<DnDMapping> set2 = Set.of(m2, m1);
        Set<DnDMapping> set3 = Set.of(m1, m3);
        Set<DnDMapping> set4 = Set.of(m4, m2);

        assertThat(Objects.equals(set1, set2)).isTrue();
        assertThat(Objects.equals(set1, set3)).isFalse();
        assertThat(Objects.equals(set1, set4)).isTrue();

        // TODO: add more cases

    }

    @Test
    void simpleCompareSAMapping() {
        SAMapping m1 = new SAMapping(1, "2");
        SAMapping m2 = new SAMapping(3, "4");
        SAMapping m3 = new SAMapping(5, "6");
        SAMapping m4 = new SAMapping(1, "2"); // same as m1

        Set<SAMapping> set1 = Set.of(m1, m2);
        Set<SAMapping> set2 = Set.of(m2, m1);
        Set<SAMapping> set3 = Set.of(m1, m3);
        Set<SAMapping> set4 = Set.of(m4, m2);

        assertThat(Objects.equals(set1, set2)).isTrue();
        assertThat(Objects.equals(set1, set3)).isFalse();
        assertThat(Objects.equals(set1, set4)).isTrue();

        // TODO: add more cases

    }
}
