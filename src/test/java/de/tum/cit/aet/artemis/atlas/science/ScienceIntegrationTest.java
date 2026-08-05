package de.tum.cit.aet.artemis.atlas.science;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO;
import de.tum.cit.aet.artemis.atlas.service.ScienceCourseService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.course.domain.Course;

class ScienceIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "scienceintegration";

    private Course course;

    @Autowired
    private ScienceCourseService scienceCourseService;

    @BeforeEach
    void enableFeatureToggle() {
        featureToggleService.enableFeature(Feature.Science);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = courseUtilService.addEmptyCourse();
    }

    @AfterEach
    void disableFeatureToggle() {
        featureToggleService.disableFeature(Feature.Science);
    }

    private void sendPutRequest(ScienceEventDTO event) throws Exception {
        request.put("/api/atlas/science", event, HttpStatus.OK);
    }

    @ParameterizedTest
    @EnumSource(value = ScienceEventType.class, names = { "SCIENCE__OPT_IN", "SCIENCE__OPT_OUT", "SCIENCE__DATA_DELETED" }, mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testLogEventOfType(ScienceEventType type) throws Exception {
        scienceCourseService.enableCourse(course.getId());
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);

        final var event = new ScienceEventDTO(type, 3L, course.getId());
        sendPutRequest(event);
        final var loggedEvents = scienceEventRepository.findAllByType(type).stream().filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList();
        assertThat(loggedEvents).hasSize(1);
        final var loggedEvent = loggedEvents.stream().findFirst().get();
        final var principal = SecurityContextHolder.getContext().getAuthentication().getName();
        assertThat(loggedEvent.getIdentity()).isEqualTo(principal);
        assertThat(loggedEvent.getType()).isEqualTo(type);
        assertThat(loggedEvent.getResourceId()).isEqualTo(event.resourceId());
        assertThat(loggedEvent.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDoesNotLogWithoutConsent() throws Exception {
        scienceCourseService.enableCourse(course.getId());

        final var event = new ScienceEventDTO(ScienceEventType.EXERCISE__OPEN, 3L, course.getId());
        sendPutRequest(event);
        assertThat(
                scienceEventRepository.findAllByType(ScienceEventType.EXERCISE__OPEN).stream().filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList())
                .isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDoesNotLogClientSuppliedAuditEvent() throws Exception {
        scienceCourseService.enableCourse(course.getId());

        final var event = new ScienceEventDTO(ScienceEventType.SCIENCE__OPT_IN, course.getId(), course.getId());
        sendPutRequest(event);

        assertThat(
                scienceEventRepository.findAllByType(ScienceEventType.SCIENCE__OPT_IN).stream().filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList())
                .isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testLogsInitialOptOutDecision() {
        scienceCourseService.enableCourse(course.getId());

        scienceCourseService.saveConsentForCurrentUser(course.getId(), false);

        final var loggedEvents = scienceEventRepository.findAllByType(ScienceEventType.SCIENCE__OPT_OUT).stream()
                .filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList();
        assertThat(loggedEvents).hasSize(1);
        final var loggedEvent = loggedEvents.stream().findFirst().get();
        assertThat(loggedEvent.getIdentity()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(loggedEvent.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetEnabledCourseHistoryLoadsCourseData() {
        scienceCourseService.enableCourse(course.getId());

        var enabledCourseHistory = scienceCourseService.getEnabledCourseHistory();

        assertThat(enabledCourseHistory).anySatisfy(enabledCourse -> {
            assertThat(enabledCourse.courseId()).isEqualTo(course.getId());
            assertThat(enabledCourse.courseTitle()).isEqualTo(course.getTitle());
            assertThat(enabledCourse.courseShortName()).isEqualTo(course.getShortName());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetConsentsForCurrentUserLoadsCourseData() {
        scienceCourseService.enableCourse(course.getId());
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);

        var consents = scienceCourseService.getConsentsForCurrentUser();

        assertThat(consents).anySatisfy(consent -> {
            assertThat(consent.courseId()).isEqualTo(course.getId());
            assertThat(consent.courseTitle()).isEqualTo(course.getTitle());
            assertThat(consent.courseShortName()).isEqualTo(course.getShortName());
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseDeletesScienceEvents() throws Exception {
        ScienceEvent scienceEvent = new ScienceEvent();
        scienceEvent.setIdentity(TEST_PREFIX + "student1");
        scienceEvent.setTimestamp(ZonedDateTime.now());
        scienceEvent.setType(ScienceEventType.EXERCISE__OPEN);
        scienceEvent.setResourceId(3L);
        scienceEvent.setCourseId(course.getId());
        scienceEvent = scienceEventRepository.save(scienceEvent);

        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        assertThat(scienceEventRepository.existsById(scienceEvent.getId())).isFalse();
    }
}
