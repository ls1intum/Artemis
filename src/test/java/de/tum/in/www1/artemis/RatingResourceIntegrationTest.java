package de.tum.in.www1.artemis;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.RatingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.RatingService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class RatingResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    UserService userService;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RatingService ratingService;

    @Autowired
    RatingRepository ratingRepo;

    private TextExercise exercise;

    private List<User> users;

    private TextSubmission submission;

    private Result result;

    private Rating rating;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(2, 1, 1);
        database.addCourseWithOneTextExercise();
        exercise = (TextExercise) exerciseRepo.findAll().get(0);
        User student1 = users.get(0);
        User tutor1 = users.get(2);
        Participation participation = database.addParticipationForExercise(exercise, student1.getLogin());

        submission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        submission = database.addTextSubmission(exercise, submission, student1.getLogin());

        result = ModelFactory.generateResult(true, 0);
        result = database.addResultToSubmission(submission);

        rating = new Rating();
        rating.setResult(result);
        rating.setRating(2);

        // this.submission = database.addTextSubmissionWithResultAndAssessor(exercise, submission, student1.getLogin(), tutor1.getLogin());
    }

    @AfterEach
    public void tearDown() {
        ratingRepo.deleteAll();
        database.resetDatabase();
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testCreateRating_asUser() throws Exception {
        request.post("/api/rating", rating, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testGetRating_asUser() throws Exception {
        Rating result = ratingService.saveRating(rating);
        request.get("/api/rating/result/" + result.getId(), HttpStatus.OK, Rating.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void testUpdateRating_asUser() throws Exception {
        Rating result = ratingService.saveRating(rating);
        request.put("/api/rating", result, HttpStatus.OK);
    }
}
