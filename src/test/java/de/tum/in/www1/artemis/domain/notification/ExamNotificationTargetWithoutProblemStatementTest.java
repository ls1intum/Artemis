package de.tum.in.www1.artemis.domain.notification;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

public class ExamNotificationTargetWithoutProblemStatementTest {

    /**
     * Tests the method getTargetWithoutProblemStatement() if it correctly extracts the target without the problem statement
     */
    @Test
    public void getTargetWithoutProblemStatement() {
        String originalTargetWithProblemStatement = "{\"problemStatement\":\"PROBLEM STATEMENT\",\"exercise\":3,\"exam\":1,\"entity\":\"exams\",\"course\":1,\"mainPage\":\"courses\"}";
        String resultingTarget = ExamNotificationTargetWithoutProblemStatement.getTargetWithoutProblemStatement(originalTargetWithProblemStatement);

        assertThat(!resultingTarget.equals(originalTargetWithProblemStatement)).as("resulting target differs from original one");
        assertThat(!resultingTarget.contains("\"problemStatement\":")).as("problem statement was successfully removed from target");
    }
}
