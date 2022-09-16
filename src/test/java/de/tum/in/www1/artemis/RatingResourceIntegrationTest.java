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
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.RatingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.RatingService;
import de.tum.in.www1.artemis.util.ModelFactory;

class RatingResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private RatingService ratingService;

    @Autowired
    private RatingRepository ratingRepo;

    @Autowired
    private UserRepository userRepo;

    private Result result;

    private Rating rating;

    private Course course;

    @BeforeEach
    void initTestCase() {
        List<User> users = database.addUsers(2, 1, 0, 1);
        course = database.addCourseWithOneReleasedTextExercise();
        TextExercise exercise = (TextExercise) exerciseRepo.findAll().get(0);
        User student1 = users.get(0);
        database.createAndSaveParticipationForExercise(exercise, student1.getLogin());

        TextSubmission submission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        submission = database.saveTextSubmission(exercise, submission, student1.getLogin());
        submission = (TextSubmission) database.addResultToSubmission(submission, null, null, 0D, true);
        result = submission.getLatestResult();
        rating = new Rating();
        rating.setResult(submission.getLatestResult());
        rating.setRating(2);

        // add instructor of other course
        database.createAndSaveUser("instructor2");
    }

    @AfterEach
    void tearDown() {
        ratingRepo.deleteAll();
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateRating_asUser() throws Exception {
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.CREATED);
        Rating savedRating = ratingService.findRatingByResultId(result.getId()).get();
        assertThat(savedRating.getRating()).isEqualTo(2);
        assertThat(savedRating.getResult().getId()).isEqualTo(result.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateInvalidRating_asUser() throws Exception {
        rating.setRating(7);
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.BAD_REQUEST);
        final Optional<Rating> optionalRating = ratingService.findRatingByResultId(result.getId());
        assertThat(optionalRating).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testCreateRating_asTutor_FORBIDDEN() throws Exception {
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.get("/api/results/" + savedRating.getResult().getId() + "/rating", HttpStatus.OK, Rating.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetRating_asUser_FORBIDDEN() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.get("/api/results/" + savedRating.getResult().getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetRating_asUser_Null() throws Exception {
        Rating savedRating = request.get("/api/results/" + result.getId() + "/rating", HttpStatus.OK, Rating.class);
        assertThat(savedRating).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUpdateRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 5, null, HttpStatus.OK);
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).get();
        assertThat(updatedRating.getRating()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUpdateInvalidRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 7, null, HttpStatus.BAD_REQUEST);
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).get();
        assertThat(updatedRating.getRating()).isNotEqualTo(7);
        assertThat(updatedRating.getRating()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testUpdateRating_asTutor_FORBIDDEN() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 5, null, HttpStatus.FORBIDDEN);

        // check that rating is not updated
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).get();
        assertThat(updatedRating.getRating()).isNotEqualTo(5);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetRatingForInstructorDashboard_asInstructor() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        final var ratings = request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.OK, Rating.class);

        assertThat(ratings).hasSize(1);
        assertThat(ratings.get(0).getId()).isEqualTo(savedRating.getId());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetRatingForInstructorDashboard_asTutor_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetRatingForInstructorDashboard_asStudent_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    void testGetRatingForInstructorDashboard_asInstructor_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }
}
