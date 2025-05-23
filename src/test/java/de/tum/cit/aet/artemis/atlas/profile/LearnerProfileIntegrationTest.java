package de.tum.cit.aet.artemis.atlas.profile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.ZonedDateTime;
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

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        Course course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        learnerProfileUtilService.createCourseLearnerProfileForUsers(TEST_PREFIX, Set.of(course));
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldReturnCourseProfilesForUser() throws Exception {

        Set<CourseLearnerProfileDTO> response = request.getSet("/api/atlas/course-learner-profiles", HttpStatus.OK, CourseLearnerProfileDTO.class);

        Set<CourseLearnerProfile> profiles = courseLearnerProfileRepository.findAllByLoginAndCourseActive(STUDENT1_OF_COURSE, ZonedDateTime.now());

        for (CourseLearnerProfile profile : profiles) {
            assertThat(response.iterator().next()).isEqualTo(CourseLearnerProfileDTO.of(profile));
        }
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldRejectInvalidProfileId() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(2, 0, "title1", 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 1, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 2, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldNotUpdateWithInvalidValues() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(0, 0, "title1", 0, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 6, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 0, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 6, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 0);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, "title1", 1, 1, 6);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldUpdateLearnerProfile() throws Exception {

        CourseLearnerProfile courseLearnerProfile = courseLearnerProfileRepository.findAllByLoginAndCourseActive(STUDENT1_OF_COURSE, ZonedDateTime.now()).stream().findAny().get();
        var course = courseLearnerProfile.getCourse();
        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(courseLearnerProfile.getId(), course.getId(), course.getTitle(),
                (courseLearnerProfile.getAimForGradeOrBonus()) % 4 + 1, (courseLearnerProfile.getTimeInvestment()) % 4 + 1,
                (courseLearnerProfile.getRepetitionIntensity()) % 4 + 1);

        CourseLearnerProfileDTO response = request.putWithResponseBody("/api/atlas/course-learner-profiles/" + courseLearnerProfile.getId(), dto, CourseLearnerProfileDTO.class,
                HttpStatus.OK);

        assertThat(response).isEqualTo(dto);
        CourseLearnerProfileDTO dbState = CourseLearnerProfileDTO
                .of(courseLearnerProfileRepository.findAllByLoginAndCourseActive(STUDENT1_OF_COURSE, ZonedDateTime.now()).stream().findAny().get());
        assertThat(dbState).isEqualTo(dto);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldFetchLearnerProfileLazily() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);
        assertThat(Hibernate.isInitialized(user.getLearnerProfile())).isFalse();
    }
}
