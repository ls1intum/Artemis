package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.scheduled.DataExportScheduleService;

@ExtendWith(MockitoExtension.class)
class DataExportScheduleServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "dataexportscheduleservice";

    @SpyBean
    private DataExportRepository dataExportRepository;

    @Autowired
    private DataExportScheduleService dataExportScheduleService;

    @SpyBean
    private DataExportCreationService dataExportCreationService;

    @SpyBean
    private DataExportService dataExportService;

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @SpyBean
    private ProfileService profileService;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 1, 0, 0, 0);
    }

    @ParameterizedTest
    @MethodSource("provideDataExportStatesAndExpectedToBeCreated")
    void testScheduledCronTaskCreatesDataExports(DataExportState state, boolean shouldBeCreated) {
        doNothing().when(dataExportCreationService).createDataExport(any(DataExport.class));
        dataExportRepository.deleteAll();
        createDataExportWithState(state);
        dataExportScheduleService.createDataExportsAndDeleteOldOnes();
        if (shouldBeCreated) {
            verify(dataExportCreationService).createDataExport(any(DataExport.class));
        }
        else {
            verify(dataExportCreationService, never()).createDataExport(any(DataExport.class));
        }
    }

    private static Stream<Arguments> provideDataExportStatesAndExpectedToBeCreated() {
        return Stream.of(Arguments.of(DataExportState.REQUESTED, true), Arguments.of(DataExportState.IN_CREATION, true), Arguments.of(DataExportState.EMAIL_SENT, false),
                Arguments.of(DataExportState.DOWNLOADED, false), Arguments.of(DataExportState.DOWNLOADED_DELETED, false), Arguments.of(DataExportState.FAILED, false));
    }

    @ParameterizedTest
    @MethodSource("provideCreationDatesAndExpectedToDelete")
    void testScheduledCronTaskDeletesOldDataExports(ZonedDateTime creationDate, boolean shouldDelete) {
        var dataExport = createDataExportWithCreationDate(creationDate);
        doNothing().when(dataExportService).deleteDataExportAndSetDataExportState(any(DataExport.class));
        dataExportScheduleService.createDataExportsAndDeleteOldOnes();
        if (shouldDelete) {
            verify(dataExportService).deleteDataExportAndSetDataExportState(dataExport);
        }
        else {
            verify(dataExportService, never()).deleteDataExportAndSetDataExportState(dataExport);
        }

    }

    private static Stream<Arguments> provideCreationDatesAndExpectedToDelete() {
        return Stream.of(Arguments.of(ZonedDateTime.now().minusDays(10), true), Arguments.of(ZonedDateTime.now().minusDays(7).minusMinutes(1), true),
                Arguments.of(ZonedDateTime.now().minusDays(5), false), Arguments.of(ZonedDateTime.now().minusDays(1), false));

    }

    @Test
    void testCronDataExportCreationTaskScheduledEveryDayAt4AM() {
        final String cronExpression = "0 0 4 * * *";
        final String cronTaskName = "de.tum.in.www1.artemis.service.scheduled.DataExportScheduleService.createDataExportsAndDeleteOldOnes";
        Set<ScheduledTask> scheduledTasks = scheduledTaskHolder.getScheduledTasks();
        long scheduledCronTasksToCreateDataExportsAt4AM = scheduledTasks.stream().filter(scheduledTask -> scheduledTask.getTask() instanceof CronTask)
                .map(scheduledTask -> (CronTask) scheduledTask.getTask()).filter(cronTask -> (cronExpression).equals(cronTask.getExpression()))
                .filter(cronTask -> cronTaskName.equals(cronTask.toString())).count();
        assertThat(scheduledCronTasksToCreateDataExportsAt4AM).isEqualTo(1);
    }

    @Test
    void testDoesntExecuteCronJobInDevMode() {
        when(profileService.isDev()).thenReturn(true);
        dataExportScheduleService.createDataExportsAndDeleteOldOnes();
        verify(dataExportCreationService, never()).createDataExport(any(DataExport.class));
        verify(dataExportService, never()).deleteDataExportAndSetDataExportState(any(DataExport.class));
        verify(dataExportRepository, never()).findAllToBeCreated();
        verify(dataExportRepository, never()).findAllToBeDeleted();
    }

    private DataExport createDataExportWithState(DataExportState state) {
        DataExport dataExport = new DataExport();
        dataExport.setRequestDate(ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()));
        dataExport.setDataExportState(state);
        dataExport.setUser(database.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setFilePath("path");
        return dataExportRepository.save(dataExport);
    }

    private DataExport createDataExportWithCreationDate(ZonedDateTime creationDate) {
        DataExport dataExport = new DataExport();
        dataExport.setCreationDate(creationDate);
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(database.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setFilePath("path");
        return dataExportRepository.save(dataExport);
    }
}
