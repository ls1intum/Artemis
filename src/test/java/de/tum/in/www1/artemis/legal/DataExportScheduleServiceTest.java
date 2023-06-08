package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.scheduled.DataExportScheduleService;

@ExtendWith(MockitoExtension.class)
class DataExportScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "dataexportscheduleservice";

    @Autowired
    private DataExportRepository dataExportRepository;

    @SpyBean
    @Qualifier("taskScheduler")
    private TaskScheduler scheduler;

    @Autowired
    private DataExportScheduleService dataExportScheduleService;

    @SpyBean
    private DataExportCreationService dataExportCreationService;

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 1, 0, 0, 0);
    }

    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ParameterizedTest
    @EnumSource(value = DataExportState.class, names = { "REQUESTED", "IN_CREATION", "EMAIL_SENT", "DOWNLOADED", "DOWNLOADED_DELETED", "FAILED" })
    void testScheduledCronTaskCreatesDataExportAndSchedulesDataExportForDeletion(DataExportState state) {
        dataExportRepository.deleteAll();
        createDataExportWithState(state);
        var mockeZonedDateTime = ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        var expectedZonedDateTime = ZonedDateTime.of(2023, 1, 8, 0, 0, 0, 0, ZoneId.systemDefault());
        try (var zonedDateTime = Mockito.mockStatic(ZonedDateTime.class)) {
            zonedDateTime.when(ZonedDateTime::now).thenReturn(mockeZonedDateTime);
            zonedDateTime.when(() -> mockeZonedDateTime.plusDays(7)).thenReturn(expectedZonedDateTime);
            dataExportScheduleService.createDataExports();
            if (state.shouldBeCreated()) {
                verify(dataExportCreationService).createDataExport(any(DataExport.class));
                verify(scheduler).schedule(any(Runnable.class), eq(expectedZonedDateTime.toInstant()));
            }
            else {
                verify(dataExportCreationService, never()).createDataExport(any(DataExport.class));
                verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
            }
        }
    }

    @Test
    void testCronDataExportCreationTaskScheduledEveryDayAt4AM() {
        final String cronExpression = "0 0 4 * * *";
        final String cronTaskName = "de.tum.in.www1.artemis.service.scheduled.DataExportScheduleService.createDataExports";
        Set<ScheduledTask> scheduledTasks = scheduledTaskHolder.getScheduledTasks();
        long scheduledCronTasksToCreateDataExportsAt4AM = scheduledTasks.stream().filter(scheduledTask -> scheduledTask.getTask() instanceof CronTask)
                .map(scheduledTask -> (CronTask) scheduledTask.getTask()).filter(cronTask -> (cronExpression).equals(cronTask.getExpression()))
                .filter(cronTask -> cronTaskName.equals(cronTask.toString())).count();
        assertThat(scheduledCronTasksToCreateDataExportsAt4AM).isEqualTo(1);
    }

    private DataExport createDataExportWithState(DataExportState state) {
        DataExport dataExport = new DataExport();
        dataExport.setRequestDate(ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()));
        dataExport.setDataExportState(state);
        dataExport.setUser(database.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setFilePath("path");
        return dataExportRepository.save(dataExport);
    }
}
