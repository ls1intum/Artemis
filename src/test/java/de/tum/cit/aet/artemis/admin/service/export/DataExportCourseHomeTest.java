package de.tum.cit.aet.artemis.admin.service.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.course.service.home.CourseHomeVisitService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class DataExportCourseHomeTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private DataExportCourseHomeService export;

    @Autowired
    private CourseHomeVisitService visits;

    @Autowired
    private JsonMapper mapper;

    @Test
    void exportsOnlyTheRequestedUsersVisits(@TempDir Path directory) throws Exception {
        userUtilService.addUsers("homeexport", 2, 0, 0, 1);
        var course = courseUtilService.createEnrolledCourse("homeexport");
        var user = userUtilService.getUserByLogin("homeexportstudent1");
        var other = userUtilService.getUserByLogin("homeexportstudent2");
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        visits.acknowledge(course.getId(), user.getId(), now, 0, now);
        visits.acknowledge(course.getId(), other.getId(), now.minusSeconds(5), 0, now);
        export.createCourseHomeExport(user.getId(), directory);
        var rows = mapper.readTree(Files.readString(directory.resolve("course_home_visits.json")));
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).get("courseId").asLong()).isEqualTo(course.getId());
        assertThat(rows.get(0).get("lastSeenAt").asText()).isEqualTo(now.toString());
        assertThat(rows.get(0).get("variantCursor").asLong()).isEqualTo(1);
        assertThat(rows.get(0).has("userId")).isFalse();
    }
}
