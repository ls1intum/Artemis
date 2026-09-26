package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO.FailureReason;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.AtlasCompetencyUpdateNotification;
import de.tum.cit.aet.artemis.notification.dto.payload.AtlasCompetencyUpdatePayloadDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;

/**
 * Behaviour of {@link AtlasCompetencyUpdateNotificationService}: which automatic-run outcomes are reported, who is
 * eligible to receive the report, what the payload carries, and that reporting never throws into the scheduler.
 */
@ExtendWith(MockitoExtension.class)
class AtlasCompetencyUpdateNotificationServiceTest {

    private static final long COURSE_ID = 5L;

    @Mock
    private CourseNotificationService courseNotificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    private AtlasCompetencyUpdateNotificationService service;

    private Course course;

    @BeforeEach
    void setUp() {
        service = new AtlasCompetencyUpdateNotificationService(courseNotificationService, userRepository, courseRepository);
        course = new Course();
        course.setId(COURSE_ID);
        course.setTitle("Algorithms");
    }

    private static User user(long id, String login) {
        User user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private void stubRecipients(Set<User> instructors, Set<String> adminLogins, Set<User> admins) {
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(userRepository.getInstructors(course)).thenReturn(instructors);
        when(userRepository.findAllActiveAdminLogins()).thenReturn(adminLogins);
        if (!adminLogins.isEmpty()) {
            when(userRepository.findAllWithAuthoritiesByDeletedIsFalseAndLoginIn(adminLogins)).thenReturn(admins);
        }
    }

    @SuppressWarnings("unchecked")
    private AtlasCompetencyUpdateNotification captureNotification(List<User> expectedRecipients) {
        ArgumentCaptor<AtlasCompetencyUpdateNotification> notification = ArgumentCaptor.forClass(AtlasCompetencyUpdateNotification.class);
        ArgumentCaptor<List<User>> recipients = ArgumentCaptor.forClass(List.class);
        verify(courseNotificationService).sendCourseNotification(notification.capture(), recipients.capture());
        assertThat(recipients.getValue()).containsExactlyInAnyOrderElementsOf(expectedRecipients);
        return notification.getValue();
    }

    @Test
    void completedRun_isSentOnceToInstructorsAndAdmins() {
        User instructor = user(1L, "instructor");
        User instructorAndAdmin = user(2L, "instructor-admin");
        User admin = user(3L, "admin");
        stubRecipients(Set.of(instructor, instructorAndAdmin), Set.of("instructor-admin", "admin"), Set.of(instructorAndAdmin, admin));
        var result = CompetencyOrchestrationResultDTO.success("done",
                List.of(AppliedActionDTO.create(10L, "Sorting", "Created competency Sorting (APPLY).", "The new exercise practices sorting."),
                        AppliedActionDTO.assign(10L, "Sorting", 20L, 1.0, "Linked exercise Merge Sort to competency Sorting.", "It is the core skill.")));

        service.notifyAfterAutomaticRun(COURSE_ID, 2, result);

        AtlasCompetencyUpdateNotification notification = captureNotification(List.of(instructor, instructorAndAdmin, admin));
        assertThat(notification.courseId).isEqualTo(COURSE_ID);
        assertThat(notification.courseTitle()).isEqualTo("Algorithms");
        AtlasCompetencyUpdatePayloadDTO payload = notification.payload();
        assertThat(payload.outcome()).isEqualTo("COMPLETED");
        assertThat(payload.exerciseCount()).isEqualTo(2);
        assertThat(payload.appliedCount()).isEqualTo(2);
        assertThat(payload.createdCount()).isEqualTo(1);
        assertThat(payload.assignedCount()).isEqualTo(1);
        assertThat(payload.editedCount()).isZero();
        assertThat(payload.deletedCount()).isZero();
        assertThat(payload.unassignedCount()).isZero();
        assertThat(payload.omittedCount()).isZero();
        assertThat(payload.changesMarkdown()).isEqualTo("Created competency Sorting \\(APPLY\\)\\.\n*The new exercise practices sorting\\.*\n\n"
                + "Linked exercise Merge Sort to competency Sorting\\.\n*It is the core skill\\.*");
    }

    static Stream<CompetencyOrchestrationResultDTO> unreportedResults() {
        return Stream.of(CompetencyOrchestrationResultDTO.noOp("nothing applicable"), CompetencyOrchestrationResultDTO.inProgress("already running"),
                CompetencyOrchestrationResultDTO.success("verified, nothing to change", List.of()));
    }

    @ParameterizedTest
    @MethodSource("unreportedResults")
    void noOpAndDeferredRuns_areNotReported(CompetencyOrchestrationResultDTO result) {
        service.notifyAfterAutomaticRun(COURSE_ID, 2, result);

        verifyNoInteractions(courseNotificationService, userRepository, courseRepository);
    }

    @Test
    void partialRun_isReportedAsPartial() {
        stubRecipients(Set.of(user(1L, "instructor")), Set.of(), Set.of());
        var result = CompetencyOrchestrationResultDTO.partial("stopped",
                List.of(AppliedActionDTO.unassign(10L, "Sorting", 20L, "Removed link between exercise Merge Sort and competency Sorting.", "Not covered.")),
                FailureReason.LLM_ERROR);

        service.notifyAfterAutomaticRun(COURSE_ID, 1, result);

        AtlasCompetencyUpdatePayloadDTO payload = captureNotification(List.of(user(1L, "instructor"))).payload();
        assertThat(payload.outcome()).isEqualTo("PARTIAL");
        assertThat(payload.unassignedCount()).isEqualTo(1);
        // No admin is active, so the admin lookup by login is skipped.
        verify(userRepository, never()).findAllWithAuthoritiesByDeletedIsFalseAndLoginIn(any());
    }

    @Test
    void failedRun_isReportedWithoutChanges() {
        stubRecipients(Set.of(user(1L, "instructor")), Set.of(), Set.of());

        service.notifyAfterAutomaticRun(COURSE_ID, 3, CompetencyOrchestrationResultDTO.failed("budget", FailureReason.TOOL_CALL_LIMIT_EXCEEDED));

        AtlasCompetencyUpdatePayloadDTO payload = captureNotification(List.of(user(1L, "instructor"))).payload();
        assertThat(payload.outcome()).isEqualTo("FAILED");
        assertThat(payload.exerciseCount()).isEqualTo(3);
        assertThat(payload.appliedCount()).isZero();
        assertThat(payload.changesMarkdown()).isEmpty();
    }

    @Test
    void runThatThrew_isReportedAsFailed() {
        stubRecipients(Set.of(user(1L, "instructor")), Set.of(), Set.of());

        service.notifyAfterAutomaticRun(COURSE_ID, 2, null);

        assertThat(captureNotification(List.of(user(1L, "instructor"))).payload().outcome()).isEqualTo("FAILED");
    }

    @Test
    void largeRun_listsAtMostTheCapAndCountsTheRest() {
        stubRecipients(Set.of(user(1L, "instructor")), Set.of(), Set.of());
        int total = AtlasCompetencyUpdateNotificationService.MAX_LISTED_CHANGES + 5;
        List<AppliedActionDTO> actions = IntStream.range(0, total).mapToObj(i -> AppliedActionDTO.edit((long) i, "C" + i, "Updated description for competency C" + i + ".", ""))
                .toList();

        service.notifyAfterAutomaticRun(COURSE_ID, 1, CompetencyOrchestrationResultDTO.success("done", actions));

        AtlasCompetencyUpdatePayloadDTO payload = captureNotification(List.of(user(1L, "instructor"))).payload();
        assertThat(payload.appliedCount()).isEqualTo(total);
        assertThat(payload.editedCount()).isEqualTo(total);
        assertThat(payload.omittedCount()).isEqualTo(5);
        assertThat(payload.changesMarkdown().split("\n\n")).hasSize(AtlasCompetencyUpdateNotificationService.MAX_LISTED_CHANGES);
    }

    @Test
    void courseWithoutRecipients_sendsNothing() {
        stubRecipients(Set.of(), Set.of(), Set.of());

        service.notifyAfterAutomaticRun(COURSE_ID, 1, CompetencyOrchestrationResultDTO.failed("boom", FailureReason.LLM_ERROR));

        verify(courseNotificationService, never()).sendCourseNotification(any(), anyList());
    }

    @Test
    void deletedCourse_sendsNothing() {
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

        service.notifyAfterAutomaticRun(COURSE_ID, 1, CompetencyOrchestrationResultDTO.failed("boom", FailureReason.LLM_ERROR));

        verifyNoInteractions(courseNotificationService, userRepository);
    }

    @Test
    void sendingFailure_isSwallowed() {
        stubRecipients(Set.of(user(1L, "instructor")), Set.of(), Set.of());
        doThrow(new IllegalStateException("smtp down")).when(courseNotificationService).sendCourseNotification(any(), anyList());

        assertThatCode(() -> service.notifyAfterAutomaticRun(COURSE_ID, 1, CompetencyOrchestrationResultDTO.failed("boom", FailureReason.LLM_ERROR))).doesNotThrowAnyException();
    }

    @Test
    void escapeMarkdown_rendersTitlesLiterallyOnOneLine() {
        assertThat(AtlasCompetencyUpdateNotificationService.escapeMarkdown("  Graphs *and*\n[Trees]_1  ")).isEqualTo("Graphs \\*and\\* \\[Trees\\]\\_1");
    }
}
