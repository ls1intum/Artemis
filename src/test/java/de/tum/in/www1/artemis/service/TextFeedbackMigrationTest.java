package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class TextFeedbackMigrationTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    FeedbackRepository feedbackRepo;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseUtilService db;

    private TextExercise textExercise;

    @Autowired
    TextFeedbackMigration classUnderTest;

    @BeforeEach
    public void prepare() {
        db.addCourseWithOneTextExercise();
        textExercise = (TextExercise) exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void cleanup() {
        db.resetDatabase();
    }

    @Test
    public void testNoErrorForEmptyExercise() {
        classUnderTest.migrate();
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void testNoEffectForExerciseWithTextBlockBasedFeedback() {
        final String text = "Lorem Ipsum";
        TextSubmission textSubmission = ModelFactory.generateTextSubmission(text, Language.ENGLISH, true);
        db.addUsers(1, 1, 0);
        textSubmission = db.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        final TextBlock textBlock = new TextBlock().text(text).startIndex(0).endIndex(text.length());
        final Feedback feedback = new Feedback().credits(1.0).type(FeedbackType.MANUAL).positive(true).detailText("This is a Comment");
        db.addFeedbackAndTextBlockForTextSubmission(textExercise, textSubmission, feedback, textBlock);

        classUnderTest.migrate();

        assertThat(feedbackRepo.findAll().get(0).getReference(), is(equalTo(feedback.getReference())));
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void testSingleFeedbackGetsMigrated() {
        final String text = "Lorem Ipsum";
        TextSubmission textSubmission = ModelFactory.generateTextSubmission(text, Language.ENGLISH, true);
        db.addUsers(1, 1, 0);
        textSubmission = db.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        final Feedback feedback = new Feedback().credits(1.0).type(FeedbackType.MANUAL).positive(true).detailText("This is a Comment").reference(text);
        db.addFeedbackForTextSubmission(textExercise, textSubmission, feedback);

        classUnderTest.migrate();

        final TextBlock block = new TextBlock().text(text).startIndex(0).endIndex(text.length()).submission(textSubmission);
        block.computeId();

        assertThat(feedbackRepo.findAll().get(0).getReference(), is(equalTo(block.getId())));
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void testSingleFeedbackWithTextMultipleOccurrencesGetsMigrated() {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Hello Hello Hello", Language.ENGLISH, true);
        db.addUsers(1, 1, 0);
        textSubmission = db.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        final Feedback feedback = new Feedback().credits(1.0).type(FeedbackType.MANUAL).positive(true).detailText("This is a Comment").reference("Hello");
        db.addFeedbackForTextSubmission(textExercise, textSubmission, feedback);

        classUnderTest.migrate();

        final TextBlock block = new TextBlock().text("Hello").startIndex(0).endIndex(5).submission(textSubmission);
        block.computeId();

        assertThat(feedbackRepo.findAll().get(0).getReference(), is(equalTo(block.getId())));
    }
}
