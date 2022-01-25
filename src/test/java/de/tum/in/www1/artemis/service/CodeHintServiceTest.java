package de.tum.in.www1.artemis.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

public class CodeHintServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CodeHintService codeHintService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        this.programmingExercise = database.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void test1() {
    }
}
