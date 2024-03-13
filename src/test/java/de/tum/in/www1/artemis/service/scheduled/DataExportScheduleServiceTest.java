package de.tum.in.www1.artemis.service.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.time.ZonedDateTime;
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
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

@ExtendWith(MockitoExtension.class)
class DataExportScheduleServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "dataexportscheduleservice";

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private DataExportScheduleService dataExportScheduleService;

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        userUtilService.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 1, 0, 0, 0);
    }

    @ParameterizedTest
    @MethodSource("provideDataExportStatesAndExpectedToBeCreated")
    void testScheduledCronTaskCreatesDataExports(DataExportState state, boolean shouldBeCreated) throws InterruptedException {
        dataExportRepository.deleteAll();
        var dataExport = createDataExportWithState(state);
        dataExportScheduleService.createDataExportsAndDeleteOldOnes();
        dataExport = dataExportRepository.findByIdElseThrow(dataExport.getId());

        if (shouldBeCreated) {
            assertThat(dataExport.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
            assertThat(dataExport.getCreationFinishedDate()).isNotNull();
        }
        else {
            assertThat(dataExport.getDataExportState()).isEqualTo(state);
            assertThat(dataExport.getCreationFinishedDate()).isNull();
        }
    }

    @Test
    void testScheduledCronTaskSendsEmailToAdminAboutSuccessfulDataExports() throws InterruptedException {
        dataExportRepository.deleteAll();
        createDataExportWithState(DataExportState.REQUESTED);
        createDataExportWithState(DataExportState.REQUESTED);
        createDataExportWithState(DataExportState.REQUESTED);
        // first data export creation should fail, the subsequent ones should succeed
        doThrow(new RuntimeException("error")).doNothing().doNothing().when(fileService).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), anyLong());
        dataExportScheduleService.createDataExportsAndDeleteOldOnes();
        var dataExportsAfterCreation = dataExportRepository.findAllSuccessfullyCreatedDataExports();
        verify(mailService).sendSuccessfulDataExportsEmailToAdmin(any(User.class), anyString(), anyString(), eq(Set.copyOf(dataExportsAfterCreation)));

    }

    private static Stream<Arguments> provideDataExportStatesAndExpectedToBeCreated() {
        return Stream.of(Arguments.of(DataExportState.REQUESTED, true), Arguments.of(DataExportState.IN_CREATION, true), Arguments.of(DataExportState.EMAIL_SENT, false),
                Arguments.of(DataExportState.DOWNLOADED, false), Arguments.of(DataExportState.DOWNLOADED_DELETED, false), Arguments.of(DataExportState.FAILED, false));
    }

    @ParameterizedTest
    @MethodSource("provideCreationDatesAndExpectedToDelete")
    void testScheduledCronTaskDeletesOldDataExports(ZonedDateTime creationDate, DataExportState state, boolean shouldDelete) throws InterruptedException {
        var dataExport = createDataExportWithCreationDateAndState(creationDate, state);
        doNothing().when(fileService).schedulePathForDeletion(any(), anyLong());
        var dataExportId = dataExport.getId();
        dataExportScheduleService.createDataExportsAndDeleteOldOnes();
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExportId);
        if (shouldDelete) {
            if (state == DataExportState.EMAIL_SENT) {
                assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.DELETED);
            }
            else {
                assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.DOWNLOADED_DELETED);
            }
        }
        else {
            assertThat(dataExportFromDb.getDataExportState()).isEqualTo(state);
        }

    }

    private static Stream<Arguments> provideCreationDatesAndExpectedToDelete() {
        return Stream.of(Arguments.of(ZonedDateTime.now().minusDays(10), DataExportState.EMAIL_SENT, true),
                Arguments.of(ZonedDateTime.now().minusDays(7).minusMinutes(1), DataExportState.DOWNLOADED, true),
                Arguments.of(ZonedDateTime.now().minusDays(5), DataExportState.EMAIL_SENT, false),
                Arguments.of(ZonedDateTime.now().minusDays(1), DataExportState.DOWNLOADED, false));

    }

    @Test
    void testCronDataExportCreationTaskScheduledEveryDayAt4AMByDefault() {
        final String cronExpression = "0 0 4 * * *";
        final String cronTaskName = "de.tum.in.www1.artemis.service.scheduled.DataExportScheduleService.createDataExportsAndDeleteOldOnes";
        Set<ScheduledTask> scheduledTasks = scheduledTaskHolder.getScheduledTasks();
        long scheduledCronTasksToCreateDataExportsAt4AM = scheduledTasks.stream().filter(scheduledTask -> scheduledTask.getTask() instanceof CronTask)
                .map(scheduledTask -> (CronTask) scheduledTask.getTask()).filter(cronTask -> (cronExpression).equals(cronTask.getExpression()))
                .filter(cronTask -> cronTaskName.equals(cronTask.toString())).count();
        assertThat(scheduledCronTasksToCreateDataExportsAt4AM).isOne();
    }

    private DataExport createDataExportWithState(DataExportState state) {
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(state);
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setFilePath("path");
        return dataExportRepository.save(dataExport);
    }

    private DataExport createDataExportWithCreationDateAndState(ZonedDateTime creationDate, DataExportState state) {
        DataExport dataExport = new DataExport();
        dataExport.setCreationFinishedDate(creationDate);
        dataExport.setDataExportState(state);
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setFilePath("path");
        return dataExportRepository.save(dataExport);
    }
}
