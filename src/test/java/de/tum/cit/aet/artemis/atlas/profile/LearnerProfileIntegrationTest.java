package de.tum.cit.aet.artemis.atlas.profile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.HashMap;
import java.util.Map;
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

        Map<String, Map<String, Object>> response = request.get("/api/atlas/course-learner-profiles", HttpStatus.OK, Map.class);

        // Convert response to actual objects
        Map<Long, CourseLearnerProfileDTO> responseProfiles = new HashMap<>();
        response.forEach((key, value) -> {
            CourseLearnerProfileDTO courseLearnerProfileDTO = new CourseLearnerProfileDTO((Integer) value.get("id"), (Integer) value.get("courseId"),
                    (Integer) value.get("aimForGradeOrBonus"), (Integer) value.get("timeInvestment"), (Integer) value.get("repetitionIntensity"));
            responseProfiles.put(Long.parseLong(key), courseLearnerProfileDTO);
        });

        Set<CourseLearnerProfile> profiles = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE);

        for (CourseLearnerProfile profile : profiles) {
            assertThat(responseProfiles.get(profile.getCourse().getId())).isEqualTo(CourseLearnerProfileDTO.of(profile));
        }
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldRejectInvalidProfileId() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(2, 0, 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 1, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        request.put("/api/atlas/course-learner-profiles/" + 2, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldNotUpdateWithInvalidValues() throws Exception {

        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(0, 0, 0, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, 6, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, 1, 0, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, 1, 6, 1);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, 1, 1, 0);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
        dto = new CourseLearnerProfileDTO(0, 0, 1, 1, 6);
        request.put("/api/atlas/course-learner-profiles/" + 0, dto, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldUpdateLearnerProfile() throws Exception {

        CourseLearnerProfile courseLearnerProfile = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE).stream().findAny().get();
        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(courseLearnerProfile.getId(), courseLearnerProfile.getCourse().getId(),
                (courseLearnerProfile.getAimForGradeOrBonus()) % 4 + 1, (courseLearnerProfile.getTimeInvestment()) % 4 + 1,
                (courseLearnerProfile.getRepetitionIntensity()) % 4 + 1);

        CourseLearnerProfileDTO response = request.putWithResponseBody("/api/atlas/course-learner-profiles/" + courseLearnerProfile.getId(), dto, CourseLearnerProfileDTO.class,
                HttpStatus.OK);

        assertThat(response).isEqualTo(dto);
        CourseLearnerProfileDTO dbState = CourseLearnerProfileDTO.of(courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE).stream().findAny().get());
        assertThat(dbState).isEqualTo(dto);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldFetchLearnerProfileLazily() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);
        assertThat(Hibernate.isInitialized(user.getLearnerProfile())).isFalse();
    }
}
