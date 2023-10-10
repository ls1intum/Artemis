package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.service.RatingService;
import de.tum.in.www1.artemis.user.UserUtilService;

class RatingResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "ratingresourceintegrationtest"; // only lower case is supported

    @Autowired
    private RatingService ratingService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Result result;

    private Rating rating;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        TextExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(exercise, student1.getLogin());

        TextSubmission submission = ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        submission = textExerciseUtilService.saveTextSubmission(exercise, submission, student1.getLogin());
        submission = (TextSubmission) participationUtilService.addResultToSubmission(submission, null, null, 0D, true);
        result = submission.getLatestResult();
        rating = new Rating();
        rating.setResult(submission.getLatestResult());
        rating.setRating(2);

        // add instructor of other course
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateRating_asUser() throws Exception {
        int response = request.postWithResponseBody("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, Integer.class, HttpStatus.CREATED);
        Rating savedRating = ratingService.findRatingByResultId(result.getId()).orElseThrow();
        assertThat(savedRating.getRating()).isEqualTo(2);
        assertThat(response).isEqualTo(2);
        assertThat(savedRating.getResult().getId()).isEqualTo(result.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(ints = { 7, 123, -5, 0 })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateInvalidRating_asUser(int value) throws Exception {
        rating.setRating(value);
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.BAD_REQUEST);
        final Optional<Rating> optionalRating = ratingService.findRatingByResultId(result.getId());
        assertThat(optionalRating).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateRating_asTutor_FORBIDDEN() throws Exception {
        request.post("/api/results/" + result.getId() + "/rating/" + rating.getRating(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        int response = request.get("/api/results/" + savedRating.getResult().getId() + "/rating", HttpStatus.OK, Integer.class);
        assertThat(response).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetRating_asUser_FORBIDDEN() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.get("/api/results/" + savedRating.getResult().getId() + "/rating", HttpStatus.FORBIDDEN, Integer.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetRating_asUser_Null() throws Exception {
        Integer savedRating = request.get("/api/results/" + result.getId() + "/rating", HttpStatus.OK, Integer.class);
        assertThat(savedRating).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 5, null, HttpStatus.OK);
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).orElseThrow();
        assertThat(updatedRating.getRating()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateInvalidRating_asUser() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 7, null, HttpStatus.BAD_REQUEST);
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).orElseThrow();
        assertThat(updatedRating.getRating()).isNotEqualTo(7);
        assertThat(updatedRating.getRating()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateRating_asTutor_FORBIDDEN() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        request.put("/api/results/" + savedRating.getResult().getId() + "/rating/" + 5, null, HttpStatus.FORBIDDEN);

        // check that rating is not updated
        Rating updatedRating = ratingService.findRatingByResultId(savedRating.getResult().getId()).orElseThrow();
        assertThat(updatedRating.getRating()).isNotEqualTo(5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetRatingForInstructorDashboard_asInstructor() throws Exception {
        Rating savedRating = ratingService.saveRating(result.getId(), rating.getRating());
        final var ratings = request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.OK, Rating.class);

        assertThat(ratings).hasSize(1);
        assertThat(ratings.get(0).getId()).isEqualTo(savedRating.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetRatingForInstructorDashboard_asTutor_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetRatingForInstructorDashboard_asStudent_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetRatingForInstructorDashboard_asInstructor_FORBIDDEN() throws Exception {
        request.getList("/api/course/" + course.getId() + "/rating", HttpStatus.FORBIDDEN, Rating.class);
    }
}
