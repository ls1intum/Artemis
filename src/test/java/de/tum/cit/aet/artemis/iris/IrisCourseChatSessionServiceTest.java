package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;

class IrisCourseChatSessionServiceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscoursechatsessionservice";

    @Autowired
    private IrisCourseChatSessionService irisCourseChatSessionService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private Course course;

    private User user;

    private IrisCourseChatSession session;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = courseUtilService.createCourse();

        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        // Ensure course membership for auth check
        user.setGroups(Set.of(course.getStudentGroupName()));

        session = new IrisCourseChatSession(course, user);
        session.setId(7L); // needed for exception message path
    }

    @Test
    void checkHasAccessTo_allowsSessionOwner() {
        assertThatNoException().isThrownBy(() -> irisCourseChatSessionService.checkHasAccessTo(user, session));
    }

    @Test
    void checkHasAccessTo_throwsForDifferentUser() {
        session.setUserId(123L);

        assertThatThrownBy(() -> irisCourseChatSessionService.checkHasAccessTo(user, session)).isInstanceOf(AccessForbiddenException.class).extracting(Throwable::getMessage)
                .asString().contains("Iris Session");
    }
}
