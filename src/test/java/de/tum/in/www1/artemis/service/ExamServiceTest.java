package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.ExamRepository;

public class ExamServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExamService examService;

    @Autowired
    ExamRepository examRepository;

    private Exam exam1;

    private Exam examInThePast;

    private Exam examInTheFuture;

    private ExerciseGroup exerciseGroup1;

    @BeforeEach
    void init() {
        Course course1 = database.addEmptyCourse();
        exam1 = database.addExamWithExerciseGroup(course1, true);
        examInThePast = database.addExam(course1, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1));
        examInTheFuture = database.addExam(course1, ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(1));
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
        examRepository.save(exam1);
    }

    @AfterEach
    public void resetDatabase() {
        exam1.removeExerciseGroup(exerciseGroup1);
        examService.save(exam1);
        database.resetDatabase();
    }

    @Test
    public void testForNullIndexColumnError() {
        Exam examResult = examService.findOne(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        examResult = examService.findOneWithExerciseGroups(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        assertThat(examResult.getExerciseGroups().get(0)).isEqualTo(exerciseGroup1);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testCanGetCurrentAndUpcomingExams() {
        List<Exam> exams = examService.findAllCurrentAndUpcomingExams();
        assertThat(exams.size()).isEqualTo(2);
        assertThat(exams).contains(exam1, examInTheFuture);
        assertThat(exams).doesNotContain(examInThePast);
    }
}
