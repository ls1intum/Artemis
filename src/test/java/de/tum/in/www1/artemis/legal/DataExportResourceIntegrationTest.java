package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.service.scheduled.DataExportScheduleService;

@ExtendWith(MockitoExtension.class)
class DataExportResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "dataexport";

    private static final String TEST_DATA_EXPORT_BASE_FILE_PATH = "src/test/resources/test-data/data-export/data-export.zip";

    @Autowired
    private DataExportRepository dataExportRepository;

    @SpyBean
    private DataExportService dataExportService;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private DataExportScheduleService dataExportScheduleService;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 2, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownloadSuccess() throws Exception {
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        // create an export
        var dataExport = prepareDataExportForDownload();
        dataExport.setUser(userForExport);
        dataExport = dataExportRepository.save(dataExport);
        var dataExportFile = request.getFile("/api/data-exports/" + dataExport.getId(), HttpStatus.OK, new LinkedMultiValueMap<>());
        var dataExportAfterDownload = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFile).isNotNull();
        assertThat(dataExportAfterDownload.getDataExportState()).isEqualTo(DataExportState.DOWNLOADED);
        assertThat(dataExportAfterDownload.getDownloadDate()).isNotNull();
        restoreTestDataInitState(dataExport);

    }

    private DataExport prepareDataExportForDownload() throws IOException {
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setRequestDate(ZonedDateTime.now().minusDays(2));
        dataExport.setCreationDate(ZonedDateTime.now().minusDays(1));
        // rename file to avoid duplicates in the temp directory
        var newFilePath = TEST_DATA_EXPORT_BASE_FILE_PATH + ZonedDateTime.now().toEpochSecond();
        Files.move(Path.of(TEST_DATA_EXPORT_BASE_FILE_PATH), Path.of(newFilePath));
        dataExport.setFilePath(newFilePath);
        return dataExportRepository.save(dataExport);
    }

    private void restoreTestDataInitState(DataExport dataExport) throws IOException {
        // undo file renaming
        Files.move(Path.of(dataExport.getFilePath()), Path.of(TEST_DATA_EXPORT_BASE_FILE_PATH));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDoesntBelongToUser_forbidden() throws Exception {
        var user2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-exports/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportErrorDuringCreation_internalServerError() throws Exception {
        when(dataExportService.requestDataExport()).thenThrow(new RuntimeException("Error!"));
        request.putWithResponseBody("/api/data-exports", null, DataExport.class, HttpStatus.INTERNAL_SERVER_ERROR);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownload_fileDoesntExist_internalServerError() throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        DataExport dataExport = new DataExport();
        dataExport.setUser(userForExport);
        dataExport.setFilePath("not-existent");
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-exports/" + dataExport.getId(), HttpStatus.INTERNAL_SERVER_ERROR, Resource.class);

    }

    @ParameterizedTest
    @EnumSource(value = DataExportState.class, names = { "REQUESTED", "IN_CREATION", "DELETED", "DOWNLOADED_DELETED", "FAILED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExport_notYetFullyCreatedOrDeleted_accessForbidden(DataExportState state) throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        DataExport dataExport = new DataExport();
        dataExport.setUser(userForExport);
        dataExport.setFilePath("not-existent");
        dataExport.setDataExportState(state);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-exports/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportIdNotExistent_notFound() throws Exception {
        request.get("/api/data-exports/999999", HttpStatus.NOT_FOUND, Resource.class);
    }

    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(value = DataExportState.class, names = { "REQUESTED", "IN_CREATION", "DELETED", "DOWNLOADED_DELETED", "FAILED" })
    @ParameterizedTest
    void testCanDownload_noDataExportInCorrectState_false(DataExportState state) throws Exception {
        dataExportRepository.deleteAll();
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(state);
        dataExport.setRequestDate(ZonedDateTime.now());
        dataExport.setUser(userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1"));
        dataExportRepository.save(dataExport);
        var canDownload = request.get("/api/data-exports/can-download", HttpStatus.OK, Boolean.class);
        assertThat(canDownload).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownload_noDataExport_false() throws Exception {
        dataExportRepository.deleteAll();
        var canDownload = request.get("/api/data-exports/can-download", HttpStatus.OK, Boolean.class);
        assertThat(canDownload).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownloadSpecificExport_dataExportNotExistent_false() throws Exception {
        var canDownload = request.get("/api/data-exports/999999/can-download", HttpStatus.OK, Boolean.class);
        assertThat(canDownload).isFalse();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownloadSpecificExport_dataExportBelongsToOtherUser_forbidden() throws Exception {
        var user2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-exports/" + dataExport.getId() + "/can-download", HttpStatus.FORBIDDEN, Boolean.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanRequest_ifNoDataExportInThePast14Days() throws Exception {
        dataExportRepository.deleteAll();
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport.setRequestDate(ZonedDateTime.now().minusDays(15));
        dataExport.setUser(userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1"));
        dataExportRepository.save(dataExport);
        boolean canRequest = request.get("/api/data-exports/can-request", HttpStatus.OK, Boolean.class);
        assertThat(canRequest).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCannotRequest_ifDataExportInThePast14Days() throws Exception {
        dataExportRepository.deleteAll();
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport.setRequestDate(ZonedDateTime.now().minusDays(10));
        dataExport.setUser(userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1"));
        dataExportRepository.save(dataExport);
        boolean canRequest = request.get("/api/data-exports/can-request", HttpStatus.OK, Boolean.class);
        assertThat(canRequest).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanRequest_ifNoDataExport() throws Exception {
        dataExportRepository.deleteAll();
        boolean canRequest = request.get("/api/data-exports/can-request", HttpStatus.OK, Boolean.class);
        assertThat(canRequest).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRequestDataExportSchedulesDataExport() throws Exception {
        doNothing().when(dataExportScheduleService).scheduleDataExportCreation(any(DataExport.class));
        var dataExport = request.putWithResponseBody("/api/data-exports", null, DataExport.class, HttpStatus.OK);
        assertThat(dataExport.getDataExportState()).isEqualTo(DataExportState.REQUESTED);
        verify(dataExportScheduleService).scheduleDataExportCreation(any(DataExport.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRequestDataExport_error_internalServerError() throws Exception {
        doNothing().when(dataExportScheduleService).scheduleDataExportCreation(any(DataExport.class));
        doThrow(new RuntimeException("error")).when(dataExportService).requestDataExport();
        request.putWithResponseBody("/api/data-exports", null, DataExport.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
