package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.competency.LearningPathUtilService;
import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathHealthDTO;

class LearningPathServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    private CourseRepository courseRepository;

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
    class HeathCheckTest {

        @BeforeEach
        void setup() {
            userUtilService.addUsers(TEST_PREFIX, 5, 1, 1, 1);
            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);
        }

        @Test
        void testHealthStatusDisabled() {
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).isEqualTo(LearningPathHealthDTO.HealthStatus.DISABLED);
        }

        @Test
        void testHealthStatusOK() {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).isEqualTo(LearningPathHealthDTO.HealthStatus.OK);
        }

        @Test
        void testHealthStatusMissing() {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            userUtilService.addStudent(TEST_PREFIX + "tumuser", TEST_PREFIX + "student1337");
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).isEqualTo(LearningPathHealthDTO.HealthStatus.MISSING);
            assertThat(healthStatus.missingLearningPaths()).isEqualTo(1);
        }
    }
}
