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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.RatingService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.util.ModelFactory;

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
    UserRepository userRepository;

    @Autowired
    RatingService ratingService;

    @Autowired
    RatingRepository ratingRepo;

    @Autowired
    SubmissionRepository submissionRepo;

    @Autowired
    UserRepository userRepo;

    private TextExercise exercise;

    private List<User> users;

    private TextSubmission submission;

    private Result result;

    private Rating rating;

    private Course course;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(2, 1, 1);
        course = database.addCourseWithOneReleasedTextExercise();
        exercise = (TextExercise) exerciseRepo.findAll().get(0);
        User student1 = users.get(0);
        database.createAndSaveParticipationForExercise(exercise, student1.getLogin());

        submission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        submission = database.saveTextSubmission(exercise, submission, student1.getLogin());
        submission = (TextSubmission) database.addResultToSubmission(submission, null, null, 0L, true);
        result = submission.getLatestResult();
        rating = new Rating();
        rating.setResult(submission.getLatestResult());
        rating.setRating(2);

        // add instructor of other course
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
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

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetRatingForInstructorDashboard_asInstructor() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        final var ratings = request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.OK, Rating.class);

        assertThat(ratings.size()).isEqualTo(1);
        assertThat(ratings.get(0).getId()).isEqualTo(savedRating.getId());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetRatingForInstructorDashboard_asTutor_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetRatingForInstructorDashboard_asStudent_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(value = "instructor2", roles = "INSTRUCTOR")
    public void testGetRatingForInstructorDashboard_asInstructor_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }
}
