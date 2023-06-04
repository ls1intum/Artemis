package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import java.time.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.ProfileService;
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

    @SpyBean
    private ProfileService profileService;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 1, 0, 0, 0);
    }

    @Test
    void testScheduleDataExportOnStartup() {
        dataExportRepository.deleteAll();
        createDataExportWithState(DataExportState.DOWNLOADED);
        createDataExportWithState(DataExportState.REQUESTED);
        createDataExportWithState(DataExportState.IN_CREATION);
        doNothing().when(dataExportCreationService).createDataExport(any(DataExport.class));
        var mockedDate = LocalDate.of(2023, 1, 1);
        var mockedTime = LocalTime.of(10, 0);
        var expectedDate = LocalDate.of(2023, 1, 2);
        var expectedTime = LocalTime.of(4, 0);
        Mockito.reset(scheduler);
        try (var localDate = Mockito.mockStatic(LocalDate.class); var localTime = Mockito.mockStatic(LocalTime.class)) {
            localDate.when(LocalDate::now).thenReturn(mockedDate);
            localTime.when(LocalTime::now).thenReturn(mockedTime);
            localTime.when(() -> LocalTime.of(4, 0)).thenReturn(expectedTime);
            Instant expectedTimeInstant = expectedDate.atTime(expectedTime).atZone(ZoneId.systemDefault()).toInstant();
            dataExportScheduleService.scheduleDataExportOnStartup();
            // we want to schedule data exports that are in the state requested or in_creation on startup.
            verify(scheduler, times(2)).schedule(any(Runnable.class), eq(expectedTimeInstant));
        }
    }

    @Test
    void testDontScheduleDataExportOnStartupInDevMode() {
        dataExportRepository.deleteAll();
        createDataExportWithState(DataExportState.DOWNLOADED);
        createDataExportWithState(DataExportState.REQUESTED);
        createDataExportWithState(DataExportState.IN_CREATION);
        doNothing().when(dataExportCreationService).createDataExport(any(DataExport.class));
        when(profileService.isDev()).thenReturn(true);
        var mockedDate = LocalDate.of(2023, 1, 1);
        var mockedTime = LocalTime.of(10, 0);
        var expectedDate = LocalDate.of(2023, 1, 2);
        var expectedTime = LocalTime.of(4, 0);
        Mockito.reset(scheduler);
        try (var localDate = Mockito.mockStatic(LocalDate.class); var localTime = Mockito.mockStatic(LocalTime.class)) {
            localDate.when(LocalDate::now).thenReturn(mockedDate);
            localTime.when(LocalTime::now).thenReturn(mockedTime);
            localTime.when(() -> LocalTime.of(4, 0)).thenReturn(expectedTime);
            Instant expectedTimeInstant = expectedDate.atTime(expectedTime).atZone(ZoneId.systemDefault()).toInstant();
            dataExportScheduleService.scheduleDataExportOnStartup();
            // we want to schedule data exports that are in the state requested or in_creation on startup.
            verify(scheduler, times(0)).schedule(any(Runnable.class), eq(expectedTimeInstant));
        }
    }

    @Test
    void scheduleOnStartupHandlesExceptionGracefully() {
        doThrow(new RuntimeException("error")).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThatCode(() -> dataExportScheduleService.scheduleDataExportOnStartup()).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void schedulesDataExportForNextDayAt4AmIfScheduledAfter4AM() {
        var dataExport = createDataExport();
        var mockedDate = LocalDate.of(2023, 1, 1);
        var mockedTime = LocalTime.of(10, 0);
        var expectedDate = LocalDate.of(2023, 1, 2);
        var expectedTime = LocalTime.of(4, 0);
        try (var localDate = Mockito.mockStatic(LocalDate.class); var localTime = Mockito.mockStatic(LocalTime.class)) {
            localDate.when(LocalDate::now).thenReturn(mockedDate);
            localTime.when(LocalTime::now).thenReturn(mockedTime);
            localTime.when(() -> LocalTime.of(4, 0)).thenReturn(expectedTime);
            Instant expectedTimeInstant = expectedDate.atTime(expectedTime).atZone(ZoneId.systemDefault()).toInstant();
            dataExportScheduleService.scheduleDataExportCreation(dataExport);
            verify(scheduler).schedule(any(Runnable.class), eq(expectedTimeInstant));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void schedulesDataExportForThisDayAt4AmIfScheduledBefore4AM() {
        var dataExport = createDataExport();
        var mockedDate = LocalDate.of(2023, 1, 1);
        var mockedTime = LocalTime.of(3, 0);
        var expectedDate = LocalDate.of(2023, 1, 1);
        var expectedTime = LocalTime.of(4, 0);
        try (var localDate = Mockito.mockStatic(LocalDate.class); var localTime = Mockito.mockStatic(LocalTime.class)) {
            localDate.when(LocalDate::now).thenReturn(mockedDate);
            localTime.when(LocalTime::now).thenReturn(mockedTime);
            localTime.when(() -> LocalTime.of(4, 0)).thenReturn(expectedTime);
            Instant expectedTimeInstant = expectedDate.atTime(expectedTime).atZone(ZoneId.systemDefault()).toInstant();
            dataExportScheduleService.scheduleDataExportCreation(dataExport);
            verify(scheduler).schedule(any(Runnable.class), eq(expectedTimeInstant));
        }
    }

    private DataExport createDataExport() {
        return createDataExportWithState(DataExportState.REQUESTED);
    }

    private DataExport createDataExportWithState(DataExportState state) {
        DataExport dataExport = new DataExport();
        dataExport.setRequestDate(ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()));
        dataExport.setDataExportState(state);
        dataExport.setUser(database.getUserByLogin(TEST_PREFIX + "student1"));
        return dataExportRepository.save(dataExport);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void schedulesDataExportForDeletionAtCorrectTime() {
        var dataExport = createDataExport();
        var mockeZonedDateTime = ZonedDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        var expectedZonedDateTime = ZonedDateTime.of(2023, 1, 8, 0, 0, 0, 0, ZoneId.systemDefault());
        try (var zonedDateTime = Mockito.mockStatic(ZonedDateTime.class)) {
            zonedDateTime.when(ZonedDateTime::now).thenReturn(mockeZonedDateTime);
            zonedDateTime.when(() -> mockeZonedDateTime.plusDays(7)).thenReturn(expectedZonedDateTime);
            dataExportScheduleService.scheduleDataExportDeletion(dataExport);
            verify(scheduler).schedule(any(Runnable.class), eq(expectedZonedDateTime.toInstant()));
        }
    }
}
