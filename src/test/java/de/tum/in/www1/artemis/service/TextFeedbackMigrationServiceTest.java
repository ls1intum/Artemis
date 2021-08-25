package de.tum.in.www1.artemis.service;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TextFeedbackMigrationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    FeedbackRepository feedbackRepo;

    @Autowired
    UserService userService;

    private TextExercise textExercise;

    @Autowired
    TextFeedbackMigrationService classUnderTest;

    @Autowired
    TextBlockRepository textBlockRepository;

    @Autowired
    TextSubmissionRepository textSubmissionRepo;

    @Autowired
    TextAssessmentService textAssessmentService;

    @BeforeEach
    public void prepare() {
        database.addCourseWithOneFinishedTextExercise();
        textExercise = (TextExercise) exerciseRepo.findAll().get(0);
    }

    @AfterEach
    public void cleanup() {
        database.resetDatabase();
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
        database.addUsers(1, 1, 0, 0);
        textSubmission = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of());
        final TextBlock textBlock = new TextBlock().text(text).startIndex(0).endIndex(text.length());
        final Feedback feedback = new Feedback().credits(1.0).type(FeedbackType.MANUAL).positive(true).detailText("This is a Comment");
        addFeedbackAndTextBlockForTextSubmission(textSubmission.getId(), feedback, textBlock);

        classUnderTest.migrate();

        assertThat(feedbackRepo.findAll().get(0).getReference(), is(equalTo(feedback.getReference())));
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void testSingleFeedbackGetsMigrated() {
        final String text = "Lorem Ipsum";
        TextSubmission textSubmission = ModelFactory.generateTextSubmission(text, Language.ENGLISH, true);
        database.addUsers(1, 1, 0, 0);
        textSubmission = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of());
        final Feedback feedback = new Feedback().credits(1.0).type(FeedbackType.MANUAL).positive(true).detailText("This is a Comment").reference(text);
        addFeedbackForTextSubmission(textSubmission, feedback);

        classUnderTest.migrate();

        final TextBlock block = new TextBlock().text(text).startIndex(0).endIndex(text.length()).submission(textSubmission);
        block.computeId();

        assertThat(feedbackRepo.findAll().get(0).getReference(), is(equalTo(block.getId())));
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void testSingleFeedbackWithTextMultipleOccurrencesGetsMigrated() {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Hello Hello Hello", Language.ENGLISH, true);
        database.addUsers(1, 1, 0, 0);
        textSubmission = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of());
        final Feedback feedback = new Feedback().credits(1.0).type(FeedbackType.MANUAL).positive(true).detailText("This is a Comment").reference("Hello");
        addFeedbackForTextSubmission(textSubmission, feedback);

        classUnderTest.migrate();

        final TextBlock block = new TextBlock().text("Hello").startIndex(0).endIndex(5).submission(textSubmission);
        block.computeId();

        assertThat(feedbackRepo.findAll().get(0).getReference(), is(equalTo(block.getId())));
    }

    private Result addFeedbackForTextSubmission(TextSubmission submission, Feedback feedback) {
        final Result result = submission.getLatestResult();
        return textAssessmentService.saveManualAssessment(submission, asList(feedback), result.getId());
    }

    private Result addFeedbackAndTextBlockForTextSubmission(long submissionId, Feedback feedback, TextBlock block) {
        TextSubmission submission = textSubmissionRepo.findWithEagerResultsAndFeedbackAndTextBlocksById(submissionId).get();
        final Result result = submission.getLatestResult();
        block.computeId();
        block.setSubmission(submission);
        block = textBlockRepository.save(block);
        submission.addBlock(block);
        textSubmissionRepo.save(submission);
        feedback.setReference(block.getId());

        return textAssessmentService.saveManualAssessment(submission, asList(feedback), result.getId());
    }
}
