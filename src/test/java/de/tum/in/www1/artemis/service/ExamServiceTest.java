package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;

public class ExamServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExamService examService;

    private Exam exam1;

    private ExerciseGroup exerciseGroup1;

    @BeforeEach
    void init() {
        Course course1 = database.addEmptyCourse();
        exam1 = database.addExamWithExerciseGroup(course1, true);
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
    }

    @AfterEach
    public void resetDatabase() {
        // TODO: something is broken with resetting the repositories. We might find a better solution for this.
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
}
