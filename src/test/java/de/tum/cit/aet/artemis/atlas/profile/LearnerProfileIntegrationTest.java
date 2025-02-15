package de.tum.cit.aet.artemis.atlas.profile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertThat;

import java.util.Optional;
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

    private static final String COURSE_LEARNER_PROFILE_API = "/api/learner-profiles/course-learner-profiles/";

    private Course course;

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        course = courseUtilService.createCourse();

        learnerProfileUtilService.createCourseLearnerProfileForUsers(TEST_PREFIX, Set.of(course));
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testLazyFetchLearnerProfile() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);
        assertThat(Hibernate.isInitialized(user.getLearnerProfile())).isFalse();
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGettersSetters() {

        Optional<CourseLearnerProfile> profileOpt = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE).stream().findAny();
        assertThat(profileOpt).isPresent();
        CourseLearnerProfile profile = profileOpt.get();

        User user = userTestRepository.findOneWithLearningPathsAndLearnerProfileByLogin(STUDENT1_OF_COURSE).get();
        assertThat(profile.getLearnerProfile()).isEqualTo(user.getLearnerProfile());

        int newVal = (profile.getAimForGradeOrBonus() % 5) + 1;
        profile.setAimForGradeOrBonus(newVal);
        assertThat(profile.getAimForGradeOrBonus()).isEqualTo(newVal);
        newVal = (profile.getTimeInvestment() % 5) + 1;
        profile.setTimeInvestment(newVal);
        assertThat(profile.getTimeInvestment()).isEqualTo(newVal);
        newVal = (profile.getRepetitionIntensity() % 5) + 1;
        profile.setRepetitionIntensity(newVal);
        assertThat(profile.getRepetitionIntensity()).isEqualTo(newVal);

    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testSettingValidLearnerProfile() throws Exception {

        Optional<CourseLearnerProfile> profileOpt = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE).stream().findFirst();
        assertThat(profileOpt).isPresent();

        CourseLearnerProfile profile = profileOpt.get();
        CourseLearnerProfileDTO profileDTO = new CourseLearnerProfileDTO(profile.getId(), course.getId(), ((profile.getAimForGradeOrBonus() % 5) + 1),
                ((profile.getRepetitionIntensity() % 5) + 1), ((profile.getTimeInvestment() % 5) + 1));

        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDTO, HttpStatus.OK);

        profileOpt = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE).stream().findFirst();
        assertThat(profileOpt).isPresent();
        CourseLearnerProfile newProfile = profileOpt.get();

        assertThat(newProfile.getAimForGradeOrBonus()).isEqualTo(profileDTO.aimForGradeOrBonus());
        assertThat(newProfile.getRepetitionIntensity()).isEqualTo(profileDTO.repetitionIntensity());
        assertThat(newProfile.getTimeInvestment()).isEqualTo(profileDTO.timeInvestment());
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testSettingInvalidLearnerProfile() throws Exception {
        Set<CourseLearnerProfile> profiles = courseLearnerProfileRepository.findAllByLogin(STUDENT1_OF_COURSE);
        long invalidId = profiles.stream().mapToLong(CourseLearnerProfile::getId).max().orElse(0) + 1;

        CourseLearnerProfile profile = profiles.stream().findAny().get();
        long oldId = profile.getId();
        profile.setId(invalidId);
        CourseLearnerProfileDTO profileDto = CourseLearnerProfileDTO.of(profile);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

        profileDto = new CourseLearnerProfileDTO(oldId, profile.getCourse().getId() + 1, 1, 1, 1);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

        profileDto = new CourseLearnerProfileDTO(oldId, profile.getCourse().getId(), 0, 1, 1);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

        profileDto = new CourseLearnerProfileDTO(oldId, profile.getCourse().getId(), 6, 1, 1);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

        profileDto = new CourseLearnerProfileDTO(oldId, profile.getCourse().getId(), 1, 0, 1);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

        profileDto = new CourseLearnerProfileDTO(oldId, profile.getCourse().getId(), 1, 6, 1);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

        profileDto = new CourseLearnerProfileDTO(oldId, profile.getCourse().getId(), 1, 1, 0);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

        profileDto = new CourseLearnerProfileDTO(oldId, profile.getCourse().getId(), 1, 1, 6);
        request.put(COURSE_LEARNER_PROFILE_API + profile.getId(), profileDto, HttpStatus.BAD_REQUEST);

    }
}
