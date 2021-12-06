package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.service.notifications.NotificationTargetService.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.JsonObject;

public class NotificationTargetServiceTest {

    @Autowired
    private static NotificationTargetService notificationTargetService;

    private static final String PROBLEM_STATEMENT = "problem statement";

    private static final Long EXERCISE_ID = 42L;

    private static final Long EXAM_ID = 27L;

    private static final Long COURSE_ID = 1L;

    private static JsonObject originalTransientTargetWithProblemStatement;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    public static void setUp() {
        prepareOriginalTransientTargetWithProblemStatement();

        notificationTargetService = new NotificationTargetService();
    }

    /**
     * Auxiliary method to prepare the comparison value for the original transient notification target with problem statement
     * Expected value -> "{\"problemStatement\":\"PROBLEM STATEMENT\",\"exercise\":3,\"exam\":1,\"entity\":\"exams\",\"course\":1,\"mainPage\":\"courses\"}"
     */
    private static void prepareOriginalTransientTargetWithProblemStatement() {
        originalTransientTargetWithProblemStatement = new JsonObject();
        originalTransientTargetWithProblemStatement.addProperty(PROBLEM_STATEMENT_TEXT, PROBLEM_STATEMENT);
        originalTransientTargetWithProblemStatement.addProperty(EXERCISE_TEXT, EXERCISE_ID);
        originalTransientTargetWithProblemStatement.addProperty(EXAM_TEXT, EXAM_ID);
        originalTransientTargetWithProblemStatement.addProperty(ENTITY_TEXT, EXAMS_TEXT);
        originalTransientTargetWithProblemStatement.addProperty(COURSE_TEXT, COURSE_ID);
        originalTransientTargetWithProblemStatement.addProperty(MAIN_PAGE_TEXT, COURSES_TEXT);
    }

    /**
     * Tests the method getTargetWithoutProblemStatement() if it correctly extracts the target without the problem statement
     */
    @Test
    public void getTargetWithoutProblemStatement() {
        String resultingTarget = notificationTargetService.getTargetWithoutProblemStatement(originalTransientTargetWithProblemStatement);
        assertThat(!resultingTarget.equals(originalTransientTargetWithProblemStatement.toString())).as("resulting target differs from original one");
        assertThat(!resultingTarget.contains("\"" + PROBLEM_STATEMENT_TEXT + "\":")).as("problem statement was successfully removed from target");
    }
}
