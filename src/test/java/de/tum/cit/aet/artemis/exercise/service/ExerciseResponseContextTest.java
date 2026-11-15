package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseResponseContextDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ExerciseResponseContextTest {

    @Test
    void directCourseIncludesScorePrecisionAndNoExamContext() {
        var course = new Course();
        course.setId(12L);
        course.setAccuracyOfScores(2);
        var exercise = new TextExercise();
        exercise.setCourse(course);
        var context = ExerciseResponseContextDTO.of(exercise, group -> "unexpected");
        assertThat(context.courseId()).isEqualTo(12L);
        assertThat(context.courseAccuracyOfScores()).isEqualTo(2.0);
        assertThat(context.course().id()).isEqualTo(12L);
        assertThat(context.exerciseGroup()).isNull();
        assertThat(context.examId()).isNull();
    }

    @Test
    void examIncludesItsGroupProjectionAndPublicationDateWithoutDirectCourse() {
        var exam = new Exam();
        exam.setId(34L);
        var publication = ZonedDateTime.parse("2026-10-20T10:00:00Z");
        exam.setPublishResultsDate(publication);
        var group = new ExerciseGroup();
        group.setId(56L);
        group.setExam(exam);
        var exercise = new TextExercise();
        exercise.setExerciseGroup(group);
        var context = ExerciseResponseContextDTO.of(exercise, ExerciseGroup::getId);
        assertThat(context.exerciseGroup()).isEqualTo(56L);
        assertThat(context.exerciseGroupId()).isEqualTo(56L);
        assertThat(context.examId()).isEqualTo(34L);
        assertThat(context.examPublishResultsDate()).isEqualTo(publication);
        assertThat(context.course()).isNull();
        assertThat(context.courseId()).isNull();
    }
}
