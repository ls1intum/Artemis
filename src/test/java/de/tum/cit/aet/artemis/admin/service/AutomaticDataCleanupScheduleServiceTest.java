package de.tum.cit.aet.artemis.admin.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.admin.config.DataCleanupProperties;

/**
 * Pure unit tests verifying that the scheduled data-privacy cleanup jobs only run when their kill switch is enabled.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticDataCleanupScheduleServiceTest {

    @Mock
    private DataCleanupService dataCleanupService;

    private DataCleanupProperties properties(boolean warn, boolean reset, boolean feedback, boolean submissionVersions, boolean notEnrolled) {
        return new DataCleanupProperties(5, 1, 30, 8, 8, 6, warn, reset, feedback, submissionVersions, notEnrolled);
    }

    private AutomaticDataCleanupScheduleService service(DataCleanupProperties properties) {
        return new AutomaticDataCleanupScheduleService(dataCleanupService, properties);
    }

    @Test
    void doesNothingWhenAllSchedulesDisabled() {
        AutomaticDataCleanupScheduleService service = service(properties(false, false, false, false, false));

        service.warnOldCoursesReset();
        service.resetOldCourses();
        service.deleteOldFeedback();
        service.deleteOldSubmissionVersions();
        service.deleteNotEnrolledUsers();

        verifyNoInteractions(dataCleanupService);
    }

    @Test
    void runsOnlyTheEnabledJobs() {
        // only the warning and not-enrolled-user jobs are enabled
        AutomaticDataCleanupScheduleService service = service(properties(true, false, false, false, true));

        service.warnOldCoursesReset();
        service.resetOldCourses();
        service.deleteOldFeedback();
        service.deleteOldSubmissionVersions();
        service.deleteNotEnrolledUsers();

        verify(dataCleanupService).warnOldCoursesReset();
        verify(dataCleanupService).deleteNotEnrolledUsers();
        verify(dataCleanupService, never()).resetOldCourses();
        verify(dataCleanupService, never()).deleteFeedbackOfNonLatestResultsOfOldCourses();
        verify(dataCleanupService, never()).deleteOldCourseSubmissionVersions();
    }

    @Test
    void runsEachJobWhenEnabled() {
        AutomaticDataCleanupScheduleService service = service(properties(true, true, true, true, true));

        service.warnOldCoursesReset();
        service.resetOldCourses();
        service.deleteOldFeedback();
        service.deleteOldSubmissionVersions();
        service.deleteNotEnrolledUsers();

        verify(dataCleanupService).warnOldCoursesReset();
        verify(dataCleanupService).resetOldCourses();
        verify(dataCleanupService).deleteFeedbackOfNonLatestResultsOfOldCourses();
        verify(dataCleanupService).deleteOldCourseSubmissionVersions();
        verify(dataCleanupService).deleteNotEnrolledUsers();
    }
}
