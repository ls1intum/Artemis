package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

public class ExamServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ExamService examService;

    private Exam exam1;

    private ExerciseGroup exerciseGroup1;

    @BeforeEach
    void init() {
        Course course1 = database.addEmptyCourse();
        exam1 = database.addExam(course1);
        exerciseGroup1 = database.addExerciseGroup(exam1, true);
        exam1.setExerciseGroups(Collections.singletonList(exerciseGroup1));
        examService.save(exam1);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void testForNullIndexColumnError() {
        Exam examResult = examService.findOne(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        examResult = examService.findOneWithExerciseGroups(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        assertThat(examResult.getExerciseGroups()).isEqualTo(Collections.singletonList(exerciseGroup1));
    }
}
