package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class WeeklySummaryMailTest extends AbstractMailContentTest {

    /**
     * Test that the variables injected in the template are used in the generated HTML content.
     */
    @Test
    void testThatVariablesAreInjectedIntoTheTemplate() {
        // Arrange:
        User recipient = createMinimalMailRecipientUser();
        recipient.setLogin("test_login");
        recipient.setResetKey("test_reset_key");

        ZonedDateTime dueDateTime = ZonedDateTime.now().plusDays(1);
        ZonedDateTime releaseDateTime = ZonedDateTime.now().minusDays(1);

        Exercise easyExercise = new TextExercise();
        easyExercise.setTitle("easy_exercise");
        easyExercise.setDifficulty(DifficultyLevel.EASY);
        easyExercise.setReleaseDate(releaseDateTime);
        easyExercise.setDueDate(dueDateTime);
        easyExercise.setBonusPoints(0.0);
        easyExercise.setMaxPoints(101.0);

        Exercise mediumExercise = new QuizExercise();
        mediumExercise.setTitle("medium_exercise");
        mediumExercise.setDifficulty(DifficultyLevel.MEDIUM);
        mediumExercise.setReleaseDate(releaseDateTime);
        mediumExercise.setDueDate(dueDateTime);
        mediumExercise.setBonusPoints(12.0);
        mediumExercise.setMaxPoints(201.0);

        Exercise hardExercise = new QuizExercise();
        hardExercise.setTitle("hard_exercise");
        hardExercise.setDifficulty(DifficultyLevel.HARD);
        hardExercise.setReleaseDate(releaseDateTime);
        hardExercise.setDueDate(dueDateTime);
        hardExercise.setBonusPoints(13.0);
        hardExercise.setMaxPoints(301.0);

        Set<Exercise> exercises = Set.of(easyExercise, mediumExercise, hardExercise);

        // Act:
        mailService.sendWeeklySummaryEmail(recipient, exercises);

        // Assert:
        String capturedContent = getGeneratedEmailTemplateText("Weekly Summary");
        for (Exercise e : exercises) {
            String dueDateFormat = timeService.convertToHumanReadableDate(e.getDueDate());
            String releaseDateFormat = timeService.convertToHumanReadableDate(e.getReleaseDate());

            assertThat(capturedContent).contains(e.getTitle());
            assertThat(capturedContent).contains(e.getType());
            assertThat(capturedContent).containsAnyOf("Schwer", "Mittel", "Leicht");
            assertThat(capturedContent).contains(String.format("%.0f", e.getBonusPoints()));
            assertThat(capturedContent).contains(String.format("%.0f", e.getMaxPoints()));
            assertThat(capturedContent).contains(dueDateFormat);
            assertThat(capturedContent).contains(releaseDateFormat);
        }
    }
}
