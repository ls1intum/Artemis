package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class CourseCodeOfConductAgreementServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "coursecodeofconductservice";

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseCodeOfConductAgreementService courseCodeOfConductAgreementService;

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 3);
    }

    @Test
    void fetchAndAgreeIsCodeOfConductAccepted() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "student", "tutor", "editor", "instructor");
        courseRepository.save(course);
        var resultBeforeAgreement = courseCodeOfConductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course);
        assertThat(resultBeforeAgreement).isFalse();

        courseCodeOfConductAgreementService.setUserAgreesToCodeOfConductInCourse(user, course);
        var resultAfterAgreement = courseCodeOfConductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course);
        assertThat(resultAfterAgreement).isTrue();
    }
}
