package de.tum.cit.aet.artemis.atlas.profile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.util.Set;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.CourseLearnerProfileDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

class LearnerProfileIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "learnerprofiledatabase";

    private static final int NUMBER_OF_STUDENTS = 1;

    private static final String STUDENT1_OF_COURSE = TEST_PREFIX + "student1";

    private Course course;

    private CourseLearnerProfile courseLearnerProfile;

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        course.setLearningPathsEnabled(true);
        courseRepository.save(course);

        learnerProfileUtilService.createCourseLearnerProfileForUsers(TEST_PREFIX, Set.of(course));
        courseLearnerProfile = courseLearnerProfileRepository.findByLoginAndCourseElseThrow(STUDENT1_OF_COURSE, course);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldReturnCourseProfilesForUser() throws Exception {

        Set<CourseLearnerProfileDTO> response = request.getSet("/api/atlas/course-learner-profiles", HttpStatus.OK, CourseLearnerProfileDTO.class);

        User user = userTestRepository.getUserByLoginElseThrow(STUDENT1_OF_COURSE);
        Set<CourseLearnerProfile> profiles = courseLearnerProfileService.getOrCreateByCourses(user, Set.of(course));

        for (CourseLearnerProfile profile : profiles) {
            assertThat((Iterable<CourseLearnerProfileDTO>) response).contains(CourseLearnerProfileDTO.of(profile));
        }
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldRejectInvalidProfileId() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(2, 0, "title1", 1, 1, 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 1, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 2, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldNotUpdateWithInvalidValues() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(0, 0, "title1", 0, 1, 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 6, 1, 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 0, 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 6, 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 0, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 6, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 1, 0, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 1, 6, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 1, 1, 0);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 1, 1, 6);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldUpdateLearnerProfile() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(courseLearnerProfile.getId(), course.getId(), course.getTitle(),
                (courseLearnerProfile.getAimForGradeOrBonus()) % 4 + 1, (courseLearnerProfile.getTimeInvestment()) % 4 + 1, (courseLearnerProfile.getRepetitionIntensity()) % 4 + 1,
                courseLearnerProfile.getProficiency() % 4 + 1, courseLearnerProfile.getInitialProficiency() % 4 + 1);

        CourseLearnerProfileDTO response = request.putWithResponseBody("/api/atlas/course-learner-profiles/" + courseLearnerProfile.getId(), dto, CourseLearnerProfileDTO.class,
                HttpStatus.OK);

        assertThat(response).isEqualTo(dto);
        CourseLearnerProfileDTO dbState = CourseLearnerProfileDTO.of(courseLearnerProfileRepository.findByLoginWithCourse(STUDENT1_OF_COURSE, course).orElseThrow());
        assertThat(dbState).isEqualTo(dto);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldFetchLearnerProfileLazily() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);
        assertThat(Hibernate.isInitialized(user.getLearnerProfile())).isFalse();
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldUpdateProficiency() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);

        double proficiency = courseLearnerProfile.getProficiency();
        double initialProficiency = courseLearnerProfile.getInitialProficiency();

        courseLearnerProfileService.updateProficiency(Set.of(user), course, 1, 100, 1);

        // Fetch updated object
        courseLearnerProfile = courseLearnerProfileRepository.findByLoginAndCourseElseThrow(STUDENT1_OF_COURSE, course);

        assertThat(courseLearnerProfile.getProficiency()).isGreaterThan(proficiency);
        assertThat(courseLearnerProfile.getInitialProficiency()).isEqualTo(initialProficiency);

        // Decrease proficiency after failing to progress with large changes.

        proficiency = courseLearnerProfile.getProficiency();

        courseLearnerProfileService.updateProficiency(Set.of(user), course, 100, 1, 0);

        // Fetch updated object
        courseLearnerProfile = courseLearnerProfileRepository.findByLoginAndCourseElseThrow(STUDENT1_OF_COURSE, course);

        assertThat(courseLearnerProfile.getProficiency()).isLessThan(proficiency);
        assertThat(courseLearnerProfile.getInitialProficiency()).isEqualTo(initialProficiency);

    }

}
