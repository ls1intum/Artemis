package de.tum.cit.aet.artemis.course;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseOperationStatus;
import de.tum.cit.aet.artemis.course.domain.CourseOperationType;
import de.tum.cit.aet.artemis.course.dto.CourseOperationProgressDTO;
import de.tum.cit.aet.artemis.course.service.CourseDeletionService;
import de.tum.cit.aet.artemis.course.service.CourseOperationClaim;
import de.tum.cit.aet.artemis.course.service.CourseOperationProgressService;
import de.tum.cit.aet.artemis.exercise.util.AsyncDeletionTestUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * The deletion of a course returns 202 (Accepted) and runs in the background. The progress is reported on the progress topic of the course operations, and the operation claim
 * of the course is released when the deletion ended, however it ended.
 */
class CourseAsyncDeletionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseasyncdel";

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private AsyncDeletionTestUtilService asyncDeletionTestUtilService;

    @Autowired
    private CourseOperationProgressService courseOperationProgressService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private CourseDeletionService courseDeletionService;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = courseUtilService.createCourse();
        // The audit log is shared by all tests of the run, so the events of this test are told apart by the title of its course.
        course.setTitle("Async deletion " + UUID.randomUUID());
        course = courseRepository.save(course);
    }

    private long deletionAuditEventsOfThisCourse() {
        return auditEventRepository.find("admin", Instant.EPOCH, Constants.DELETE_COURSE).stream().filter(event -> course.getTitle().equals(event.getData().get("course"))).count();
    }

    private String courseUrl() {
        return "/api/admin/courses/" + course.getId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseReturnsAcceptedAndDeletesInTheBackground() throws Exception {
        String icon = "course-async-deletion-icon.png";
        course.setCourseIcon(icon);
        courseRepository.save(course);

        request.delete(courseUrl(), HttpStatus.ACCEPTED);
        var finalProgress = asyncDeletionTestUtilService.awaitCourseDeletion(course.getId());

        assertThat(finalProgress.operationType()).isEqualTo(CourseOperationType.DELETE);
        assertThat(finalProgress.weightedProgressPercent()).isEqualTo(100.0);
        assertThat(courseRepository.existsById(course.getId())).isFalse();

        // The progress was published on the topic of the course operations and ended with the completion.
        var progress = ArgumentCaptor.forClass(CourseOperationProgressDTO.class);
        verify(websocketMessagingService, atLeastOnce()).sendMessage(topic("/topic/courses/" + course.getId() + "/operation-progress"), progress.capture());
        List<CourseOperationProgressDTO> messages = progress.getAllValues();
        assertThat(messages.getFirst().status()).isEqualTo(CourseOperationStatus.IN_PROGRESS);
        assertThat(messages.getLast().status()).isEqualTo(CourseOperationStatus.COMPLETED);

        // A client that missed the websocket messages reads the same final status.
        var status = request.get("/api/admin/courses/" + course.getId() + "/operation-progress", HttpStatus.OK, CourseOperationProgressDTO.class);
        assertThat(status.status()).isEqualTo(CourseOperationStatus.COMPLETED);

        // The audit event was written for the administrator, and the icon of the deleted course is scheduled for deletion.
        assertThat(deletionAuditEventsOfThisCourse()).isEqualTo(1);
        verify(fileService).schedulePathForDeletion(new FileSystemLocation.CourseIcon(icon).path(), 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseWhileAnotherOperationRunsIsRejected() throws Exception {
        CourseOperationClaim archive = courseOperationProgressService.startOperation(course.getId(), CourseOperationType.ARCHIVE, "Creating directories", 1,
                ZonedDateTime.now(ZoneId.systemDefault()));
        try {
            request.delete(courseUrl(), HttpStatus.CONFLICT);
        }
        finally {
            courseOperationProgressService.releaseOperationClaim(archive);
        }

        // Nothing was deleted or audited, and once the other operation ended the deletion is accepted.
        assertThat(courseRepository.existsById(course.getId())).isTrue();
        assertThat(deletionAuditEventsOfThisCourse()).isZero();
        request.delete(courseUrl(), HttpStatus.ACCEPTED);
        asyncDeletionTestUtilService.awaitCourseDeletion(course.getId());
        assertThat(courseRepository.existsById(course.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseThatFailsInTheBackgroundReportsTheFailureAndReleasesTheClaim() {
        // The preflight of the deletion reads the course, so a course that is gone by the time the background deletion runs makes it fail before anything else happens.
        long courseId = course.getId();
        var claim = courseDeletionService.startDeletion(courseId);
        courseRepository.deleteById(courseId);

        courseDeletionService.deleteAsync(courseId, null, claim, userUtilService.getUserByLogin("admin"));
        await().atMost(Duration.ofSeconds(60))
                .until(() -> courseOperationProgressService.getOperationProgress(courseId).filter(p -> p.status() != CourseOperationStatus.IN_PROGRESS).isPresent());
        var failed = asyncDeletionTestUtilService.awaitCourseDeletionFailure(courseId);

        assertThat(failed.errorMessage()).isNotBlank();
        verify(websocketMessagingService, atLeastOnce()).sendMessage(topic("/topic/courses/" + courseId + "/operation-progress"), any(CourseOperationProgressDTO.class));

        // The claim was released, so another operation on the course is not refused as a conflict.
        var next = courseOperationProgressService.startOperation(courseId, CourseOperationType.ARCHIVE, "Creating directories", 1, ZonedDateTime.now(ZoneId.systemDefault()));
        courseOperationProgressService.releaseOperationClaim(next);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteCourseAsInstructorIsForbidden() throws Exception {
        request.delete(courseUrl(), HttpStatus.FORBIDDEN);

        assertThat(courseRepository.existsById(course.getId())).isTrue();
        assertThat(courseOperationProgressService.getOperationProgress(course.getId())).isEmpty();
    }
}
