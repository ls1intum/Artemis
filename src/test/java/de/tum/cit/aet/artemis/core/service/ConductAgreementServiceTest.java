package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.service.ConductAgreementService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ConductAgreementServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "conductagreementservice";

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(1);

    @Autowired
    private ConductAgreementService conductAgreementService;

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 3);
    }

    @Test
    void fetchConductAgreementIfCodeOfConductIsNullOrEmpty() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setCourseInformationSharingMessagingCodeOfConduct(null);
        courseRepository.save(course);
        var resultIfCodeOfConductIsNull = conductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course);
        assertThat(resultIfCodeOfConductIsNull).isTrue();

        course.setCourseInformationSharingMessagingCodeOfConduct("");
        courseRepository.save(course);
        var resultIfCodeOfConductIsEmpty = conductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course);
        assertThat(resultIfCodeOfConductIsEmpty).isTrue();
    }

    @Test
    void fetchAndAgreeAndResetConductAgreement() {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_TIMESTAMP, new HashSet<>(), "student", "tutor", "editor", "instructor");
        course.setCourseInformationSharingMessagingCodeOfConduct("Code of Conduct");
        courseRepository.save(course);
        var resultBeforeAgreement = conductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course);
        assertThat(resultBeforeAgreement).isFalse();

        conductAgreementService.setUserAgreesToCodeOfConductInCourse(user, course);
        var resultAfterAgreement = conductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course);
        assertThat(resultAfterAgreement).isTrue();

        conductAgreementService.resetUsersAgreeToCodeOfConductInCourse(course);
        var resultAfterReset = conductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course);
        assertThat(resultAfterReset).isFalse();
    }
}
