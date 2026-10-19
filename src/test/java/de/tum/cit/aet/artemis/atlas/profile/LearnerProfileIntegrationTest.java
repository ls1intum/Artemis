package de.tum.cit.aet.artemis.atlas.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.CourseLearnerProfileDTO;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

class LearnerProfileIntegrationTest extends AbstractAtlasIntegrationTest {

    @Autowired
    private LearnerProfileRepository learnerProfileRepository;

    private static final String TEST_PREFIX = "learnerprofiledatabase";

    private static final int NUMBER_OF_STUDENTS = 1;

    private static final String STUDENT1_OF_COURSE = TEST_PREFIX + "student1";

    /** The course of the current test method. Earlier methods leave theirs behind, so assertions name this one. */
    private Course course;

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Course must be created BEFORE outsider users are saved so that enrollPrefixedUsersInCourse
        // (called inside createEnrolledCourse) does not pick them up.
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        learnerProfileUtilService.createCourseLearnerProfileForUsers(TEST_PREFIX, Set.of(course));

        // Add users that are not in the course (created AFTER enrollment so they stay unenrolled)
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldReturnCourseProfilesForUser() throws Exception {
        Set<CourseLearnerProfileDTO> response = request.getSet("/api/atlas/course-learner-profiles", HttpStatus.OK, CourseLearnerProfileDTO.class);

        CourseLearnerProfile profile = courseLearnerProfileOfStudent();
        assertThat(response).contains(CourseLearnerProfileDTO.of(profile));
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldRejectInvalidProfileId() throws Exception {
        // Ids of profiles the student does not have, derived from one it does: hard-coded small ids belong to
        // whichever test wrote them first, so in a full run they can name a profile of this very student.
        long ownProfileId = courseLearnerProfileOfStudent().getId();
        long unknownProfileId = ownProfileId + 1_000_000;

        // The body has to name the profile the path names
        CourseLearnerProfileDTO mismatched = new CourseLearnerProfileDTO(unknownProfileId, course.getId(), course.getTitle(), 1, 1, 1);
        request.put("/api/atlas/course-learner-profiles/" + ownProfileId, mismatched, HttpStatus.BAD_REQUEST);

        // And it has to be a profile of the requesting student
        request.put("/api/atlas/course-learner-profiles/" + unknownProfileId, mismatched, HttpStatus.BAD_REQUEST);
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

        CourseLearnerProfile courseLearnerProfile = courseLearnerProfileOfStudent();
        CourseLearnerProfileDTO dto = new CourseLearnerProfileDTO(courseLearnerProfile.getId(), course.getId(), course.getTitle(),
                (courseLearnerProfile.getAimForGradeOrBonus()) % 4 + 1, (courseLearnerProfile.getTimeInvestment()) % 4 + 1,
                (courseLearnerProfile.getRepetitionIntensity()) % 4 + 1);

        CourseLearnerProfileDTO response = request.putWithResponseBody("/api/atlas/course-learner-profiles/" + courseLearnerProfile.getId(), dto, CourseLearnerProfileDTO.class,
                HttpStatus.OK);

        assertThat(response).isEqualTo(dto);
        assertThat(CourseLearnerProfileDTO.of(courseLearnerProfileOfStudent())).isEqualTo(dto);
    }

    private CourseLearnerProfile courseLearnerProfileOfStudent() {
        User student = userTestRepository.getUserWithAuthorities(STUDENT1_OF_COURSE);
        return courseLearnerProfileRepository.findByUserIdAndCourseId(student.getId(), course.getId()).orElseThrow();
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void shouldReadTheProfileThroughItsOwnRepository() {
        User user = userTestRepository.getUserWithAuthorities(STUDENT1_OF_COURSE);
        // The account does not carry the profile: it is read where it is needed, so that loading an account never
        // pays for a profile almost no caller wants.
        assertThat(learnerProfileRepository.findByUser(user)).isPresent();
    }
}
