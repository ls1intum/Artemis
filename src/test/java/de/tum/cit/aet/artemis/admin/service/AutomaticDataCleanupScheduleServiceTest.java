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

    private DataCleanupProperties properties(boolean warn, boolean reset, boolean feedback, boolean submissionVersions, boolean notEnrolledWarn, boolean notEnrolled,
            boolean plagiarismCases) {
        return new DataCleanupProperties(5, 1, 30, 8, 8, 6, 30, warn, reset, feedback, submissionVersions, notEnrolledWarn, notEnrolled, plagiarismCases);
    }

    private AutomaticDataCleanupScheduleService service(DataCleanupProperties properties) {
        return new AutomaticDataCleanupScheduleService(dataCleanupService, properties);
    }

    private void runAllJobs(AutomaticDataCleanupScheduleService service) {
        service.warnOldCoursesReset();
        service.resetOldCourses();
        service.deleteOldFeedback();
        service.deleteOldSubmissionVersions();
        service.warnNotEnrolledUsers();
        service.deleteNotEnrolledUsers();
        service.deletePlagiarismCases();
    }

    @Test
    void doesNothingWhenAllSchedulesDisabled() {
        runAllJobs(service(properties(false, false, false, false, false, false, false)));

        verifyNoInteractions(dataCleanupService);
    }

    @Test
    void runsOnlyTheEnabledJobs() {
        // only the old-course warning and the not-enrolled-user warning jobs are enabled
        runAllJobs(service(properties(true, false, false, false, true, false, false)));

        verify(dataCleanupService).warnOldCoursesReset();
        verify(dataCleanupService).warnNotEnrolledUsers();
        verify(dataCleanupService, never()).resetOldCourses();
        verify(dataCleanupService, never()).deleteFeedbackOfNonLatestResultsOfOldCourses();
        verify(dataCleanupService, never()).deleteOldCourseSubmissionVersions();
        verify(dataCleanupService, never()).deleteNotEnrolledUsers();
        verify(dataCleanupService, never()).deletePlagiarismCasesOfOldCourses();
    }

    @Test
    void runsEachJobWhenEnabled() {
        runAllJobs(service(properties(true, true, true, true, true, true, true)));

        verify(dataCleanupService).warnOldCoursesReset();
        verify(dataCleanupService).resetOldCourses();
        verify(dataCleanupService).deleteFeedbackOfNonLatestResultsOfOldCourses();
        verify(dataCleanupService).deleteOldCourseSubmissionVersions();
        verify(dataCleanupService).warnNotEnrolledUsers();
        verify(dataCleanupService).deleteNotEnrolledUsers();
        verify(dataCleanupService).deletePlagiarismCasesOfOldCourses();
    }
}
