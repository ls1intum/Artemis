package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathHealthDTO;
import de.tum.cit.aet.artemis.atlas.learningpath.util.LearningPathUtilService;
import de.tum.cit.aet.artemis.atlas.profile.util.LearnerProfileUtilService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRecommendationService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LearningPathServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "learningpathservice";

    @Autowired
    private LearningPathService learningPathService;

    @Autowired
    private LearningPathUtilService learningPathUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private LearnerProfileUtilService learnerProfileUtilService;

    private Course course;

    @BeforeEach
    void setAuthorizationForRepositoryRequests() {
        SecurityUtils.setAuthorizationObject();
    }

    @BeforeEach
    void setup() {
        course = courseUtilService.createCourse();
    }

    @Nested
    class HealthCheck {

        @BeforeEach
        void setup() {
            userUtilService.addUsers(TEST_PREFIX, 5, 1, 1, 1);

            learnerProfileUtilService.createLearnerProfilesForUsers(TEST_PREFIX);

            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);

        }

        @Test
        void testHealthStatusMissing() {
            final var competency1 = competencyUtilService.createCompetency(course);
            final var competency2 = competencyUtilService.createCompetency(course);
            competencyUtilService.addRelation(competency1, RelationType.MATCHES, competency2);
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            userUtilService.addStudent(TEST_PREFIX + "tumuser", TEST_PREFIX + "student1337");
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactly(LearningPathHealthDTO.HealthStatus.MISSING);
            assertThat(healthStatus.missingLearningPaths()).isEqualTo(1);
        }

        @Test
        void testHealthStatusNoCompetencies() {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactlyInAnyOrder(LearningPathHealthDTO.HealthStatus.NO_COMPETENCIES, LearningPathHealthDTO.HealthStatus.NO_RELATIONS);
            assertThat(healthStatus.missingLearningPaths()).isNull();
        }

        @Test
        void testHealthStatusNoRelations() {
            competencyUtilService.createCompetency(course);
            competencyUtilService.createCompetency(course);
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactly(LearningPathHealthDTO.HealthStatus.NO_RELATIONS);
            assertThat(healthStatus.missingLearningPaths()).isNull();
        }
    }

    @Nested
    class GenerateNgxPathRepresentation {

        @BeforeEach
        void setup() {
            userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);
        }

        @Test
        void testUtilityConstantsValid() throws NoSuchFieldException, IllegalAccessException {
            Field extendsUtilityRatioField = LearningPathRecommendationService.class.getDeclaredField("EXTENDS_UTILITY_RATIO");
            Field assumesUtilityRatioField = LearningPathRecommendationService.class.getDeclaredField("ASSUMES_UTILITY_RATIO");
            extendsUtilityRatioField.setAccessible(true);
            assumesUtilityRatioField.setAccessible(true);
            final var extendsUtilityRatio = extendsUtilityRatioField.getDouble(null);
            final var assumesUtilityRatio = assumesUtilityRatioField.getDouble(null);
            assertThat(extendsUtilityRatio).isLessThan(assumesUtilityRatio);
        }
    }
}
