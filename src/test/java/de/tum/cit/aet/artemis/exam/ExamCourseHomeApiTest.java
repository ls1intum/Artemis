package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExamCourseHomeApi;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@WithMockUser(username = ExamCourseHomeApiTest.PREFIX + "student1", roles = "USER")
class ExamCourseHomeApiTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homeexams";

    @Autowired
    private ExamCourseHomeApi home;

    @Autowired
    private ExamRepository exams;

    @Autowired
    private ExamUserRepository registrations;

    private Course course;

    private ZonedDateTime base;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        base = ZonedDateTime.now();
    }

    @Test
    void onlyVisibleRegisteredNotStartedExamsAreSuggested() {
        var visible = exams.save(ExamFactory.generateExam(course, base.minusDays(1), base.plusHours(2), base.plusHours(3), false));
        var hidden = exams.save(ExamFactory.generateExam(course, base.plusDays(1), base.plusDays(2), base.plusDays(2).plusHours(1), false));
        exams.save(ExamFactory.generateExam(course, base.minusDays(1), base.plusHours(1), base.plusHours(2), false));
        var user = userUtilService.getUserByLogin(PREFIX + "student1");
        for (var exam : java.util.List.of(visible, hidden)) {
            var registration = new ExamUser();
            registration.setExam(exam);
            registration.setUser(user);
            registrations.saveAndFlush(registration);
        }
        var result = home.load(course.getId(), user, Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.items()).extracting(i -> i.target().id()).containsExactly(visible.getId());
    }
}
