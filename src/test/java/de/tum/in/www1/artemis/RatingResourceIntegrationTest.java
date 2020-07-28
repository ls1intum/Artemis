package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

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
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.RatingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
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

    @Autowired
    SubmissionRepository submissionRepo;

    private TextExercise exercise;

    private List<User> users;

    private TextSubmission submission;

    private Result result;

    private Rating rating;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(2, 1, 1);
        database.addCourseWithOneReleasedTextExercise();
        exercise = (TextExercise) exerciseRepo.findAll().get(0);
        User student1 = users.get(0);
        database.addParticipationForExercise(exercise, student1.getLogin());

        submission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        submission = database.addTextSubmission(exercise, submission, student1.getLogin());

        result = ModelFactory.generateResult(true, 0);
        result = database.addResultToSubmission(submission);

        rating = new Rating();
        rating.setResult(result);
        rating.setRating(2);
    }

    @AfterEach
    public void tearDown() {
        ratingRepo.deleteAll();
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testCreateRating_asUser() throws Exception {
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.CREATED);
        Rating savedRating = ratingService.findRatingByResultId(result.getId()).get();
        assertThat(savedRating.getRating()).isEqualTo(2);
        assertThat(savedRating.getResult().getId()).isEqualTo(result.getId());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testCreateInvalidRating_asUser() throws Exception {
        rating.setRating(7);
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.BAD_REQUEST);
        final Optional<Rating> optionalRating = ratingService.findRatingByResultId(result.getId());
        assertThat(optionalRating).isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testCreateRating_asTutor_FORBIDDEN() throws Exception {
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.get("/api/results/" + savedRating.getResult().getId() + "/rating", HttpStatus.OK, Rating.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetRating_asUser_FORBIDDEN() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.get("/api/results/" + savedRating.getResult().getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetRating_asUser_Null() throws Exception {
        Rating savedRating = request.get("/api/results/" + result.getId() + "/rating", HttpStatus.OK, Rating.class);
        assertThat(savedRating).isEqualTo(null);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testUpdateRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 5, null, HttpStatus.OK);
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).get();
        assertThat(updatedRating.getRating()).isEqualTo(5);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testUpdateInvalidRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 7, null, HttpStatus.BAD_REQUEST);
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).get();
        assertThat(updatedRating.getRating()).isNotEqualTo(7);
        assertThat(updatedRating.getRating()).isEqualTo(2);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testUpdateRating_asTutor_FORBIDDEN() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 5, null, HttpStatus.FORBIDDEN);

        // check that rating is not updated
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).get();
        assertThat(updatedRating.getRating()).isNotEqualTo(5);
    }
}
