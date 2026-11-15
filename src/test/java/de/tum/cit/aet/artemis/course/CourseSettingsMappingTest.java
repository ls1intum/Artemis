package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseCreateDTO;
import de.tum.cit.aet.artemis.course.dto.CourseUpdateDTO;

class CourseSettingsMappingTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void creationAndUpdateApplySharedSettingsWhilePreservingTheirConfigurationLifecycle(boolean online) {
        String json = """
                {"title":"Algorithms","shortName":"algos","description":"Description","semester":"WS26/27",
                "startDate":"2026-10-01T10:00:00Z","endDate":"2027-03-31T18:00:00Z","onlineCourse":%s,
                "maxComplaints":4,"maxTeamComplaints":3,"maxComplaintTimeDays":7,"color":"#123456",
                "enrollmentEnabled":true,"enrollmentConfirmationMessage":"Welcome","unenrollmentEnabled":true,
                "learningPathsEnabled":true,"presentationScore":10,"maxPoints":100,"accuracyOfScores":2,"timeZone":"Europe/Berlin"}
                """.formatted(online);
        var mapper = JsonObjectMapper.get();
        Course created = mapper.readValue(json, CourseCreateDTO.class).toCourse();
        var updated = new Course();
        var configuration = new CourseConfiguration();
        configuration.setCourse(updated);
        updated.setCourseConfiguration(configuration);
        mapper.readValue(json, CourseUpdateDTO.class).applyTo(updated);
        assertThat(created.getTitle()).isEqualTo("Algorithms");
        assertThat(created.isOnlineCourse()).isEqualTo(online);
        assertThat(created.getMaxComplaints()).isEqualTo(4);
        assertThat(created.getMaxComplaintTimeDays()).isEqualTo(7);
        assertThat(created.getMaxPoints()).isEqualTo(100);
        assertThat(updated).usingRecursiveComparison().ignoringFields("athenaConfig", "courseConfiguration").isEqualTo(created);
        assertThat(updated.getCourseConfiguration()).isSameAs(configuration);
        assertThat(created.getCourseConfiguration()).isNotNull().isNotSameAs(configuration);
    }
}
