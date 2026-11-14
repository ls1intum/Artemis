package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.course.repository.CourseHomeVisitRepository;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeVisitService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseHomeVisitRepositoryTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private CourseHomeVisitRepository visits;

    @Autowired
    private CourseHomeVisitService service;

    @Test
    void concurrentAndOutOfOrderVisitsAreMonotonic() throws Exception {
        userUtilService.addUsers("homevisits", 1, 0, 0, 1);
        var course = courseUtilService.createEnrolledCourse("homevisits");
        var user = userUtilService.getUserByLogin("homevisitsstudent1");
        long c = course.getId();
        long u = user.getId();
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var jobs = List.<Callable<Void>>of(() -> {
                service.acknowledge(c, u, now.minusSeconds(2), 0, now);
                return null;
            }, () -> {
                service.acknowledge(c, u, now.minusSeconds(1), 0, now);
                return null;
            });
            for (var future : executor.invokeAll(jobs))
                future.get();
        }
        var visit = service.read(c, u);
        assertThat(visit.variantCursor()).isEqualTo(1);
        assertThat(visit.lastSeenAt()).isEqualTo(now.minusSeconds(1));
        service.acknowledge(c, u, now, 1, now);
        service.acknowledge(c, u, now.minusSeconds(3), 0, now);
        assertThat(service.read(c, u).variantCursor()).isEqualTo(2);
        assertThat(service.read(c, u).lastSeenAt()).isEqualTo(now);
        visits.deleteByCourseId(c);
        assertThat(service.read(c, u).lastSeenAt()).isNull();
    }
}
