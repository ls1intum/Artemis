package de.tum.in.www1.artemis.dataexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.service.export.DataExportService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.DataExportDTO;
import de.tum.in.www1.artemis.web.rest.dto.RequestDataExportDTO;

@ExtendWith(MockitoExtension.class)
class DataExportResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "dataexport";

    private static final String TEST_DATA_EXPORT_BASE_FILE_PATH = "src/test/resources/test-data/data-export/data-export.zip";

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private AuditingHandler auditingHandler;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
        userUtilService.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 2, 0, 0, 1);
    }

    @AfterEach
    void tearDown() {
        // reset to the default
        auditingHandler.setDateTimeProvider(null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownloadSuccess() throws Exception {
        var userForExport = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
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
        dataExport.setCreationFinishedDate(ZonedDateTime.now().minusDays(1));
        // rename file to avoid duplicates in the temp directory
        var newFilePath = TEST_DATA_EXPORT_BASE_FILE_PATH + ZonedDateTime.now().toEpochSecond();
        FileUtils.moveFile(Path.of(TEST_DATA_EXPORT_BASE_FILE_PATH).toFile(), Path.of(newFilePath).toFile());
        dataExport.setFilePath(newFilePath);
        return dataExportRepository.save(dataExport);
    }

    private void restoreTestDataInitState(DataExport dataExport) throws IOException {
        // undo file renaming
        FileUtils.moveFile(Path.of(dataExport.getFilePath()).toFile(), Path.of(TEST_DATA_EXPORT_BASE_FILE_PATH).toFile());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDoesntBelongToUser_forbidden() throws Exception {
        var user2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-exports/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownload_fileDoesntExist_internalServerError() throws Exception {
        var userForExport = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
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
        var userForExport = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
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
    void testCanDownload_noDataExportInCorrectState_dataExportIdNull(DataExportState state) throws Exception {
        dataExportRepository.deleteAll();
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(state);
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExportRepository.save(dataExport);
        var dataExportToDownload = request.get("/api/data-exports/can-download", HttpStatus.OK, DataExportDTO.class);
        assertThat(dataExportToDownload.id()).isNull();
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(value = DataExportState.class, names = { "EMAIL_SENT", "DOWNLOADED" })
    void testCanDownload_dataExportInCorrectState_dataExportIdReturned(DataExportState state) throws Exception {
        dataExportRepository.deleteAll();
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(state);
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport = dataExportRepository.save(dataExport);
        var dataExportToDownload = request.get("/api/data-exports/can-download", HttpStatus.OK, DataExportDTO.class);
        assertThat(dataExportToDownload.id()).isEqualTo(dataExport.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownload_multipleDataExportsInCorrectState_returnsLatest() throws Exception {
        dataExportRepository.deleteAll();
        initDataExport(DataExportState.DOWNLOADED);
        var expectedDataExport = initDataExport(DataExportState.EMAIL_SENT);
        var dataExportToDownload = request.get("/api/data-exports/can-download", HttpStatus.OK, DataExportDTO.class);
        assertThat(dataExportToDownload.id()).isEqualTo(expectedDataExport.getId());
        assertThat(dataExportToDownload.dataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownload_noDataExport_dataExportIdNull() throws Exception {
        dataExportRepository.deleteAll();
        var dataExportToDownload = request.get("/api/data-exports/can-download", HttpStatus.OK, DataExportDTO.class);
        assertThat(dataExportToDownload.id()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownloadSpecificExport_dataExportNotExistent_notFound() throws Exception {
        request.get("/api/data-exports/999999/can-download", HttpStatus.NOT_FOUND, Boolean.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownloadSpecificExport_dataExportNotDownloadable_false() throws Exception {
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.REQUESTED);
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport = dataExportRepository.save(dataExport);
        var canDownload = request.get("/api/data-exports/" + dataExport.getId() + "/can-download", HttpStatus.OK, Boolean.class);
        assertThat(canDownload).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanRequestDataExportIfNeverRequested() throws Exception {
        dataExportRepository.deleteAll();
        var canRequest = request.get("/api/data-exports/can-request", HttpStatus.OK, Boolean.class);
        assertThat(canRequest).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanRequestDataExportIfLastOneFailed() throws Exception {
        dataExportRepository.deleteAll();
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.FAILED);
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExportRepository.save(dataExport);
        var canRequest = request.get("/api/data-exports/can-request", HttpStatus.OK, Boolean.class);
        assertThat(canRequest).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCanDownloadSpecificExport_dataExportBelongsToOtherUser_forbidden() throws Exception {
        var user2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
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
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // this is needed to fake the date used for @CreatedDate of the AbstractAuditingEntity
        auditingHandler.setDateTimeProvider(() -> Optional.of(ZonedDateTime.now().minusDays(15)));
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
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        // created date is automatically set on save
        dataExportRepository.save(dataExport);
        boolean canRequest = request.get("/api/data-exports/can-request", HttpStatus.OK, Boolean.class);
        assertThat(canRequest).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRequest_ifDataExportInThePast14Days_forbidden() throws Exception {
        dataExportRepository.deleteAll();
        DataExport dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.DOWNLOADED);
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExportRepository.save(dataExport);
        request.postWithResponseBody("/api/data-exports", null, RequestDataExportDTO.class, HttpStatus.FORBIDDEN);
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
    void testRequestingDataExportCreatesCorrectDataExportObject() throws Exception {
        dataExportRepository.deleteAll();
        var dataExport = request.postWithResponseBody("/api/data-exports", null, RequestDataExportDTO.class, HttpStatus.OK);
        assertThat(dataExport.dataExportState()).isEqualTo(DataExportState.REQUESTED);
        assertThat(dataExport.createdDate()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = DataExportState.class, names = { "EMAIL_SENT", "DOWNLOADED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteDataExportSchedulesDirectoryForDeletion_setsCorrectState(DataExportState state) {
        var dataExport = initDataExport(state);
        doNothing().when(fileService).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), anyInt());
        dataExportService.deleteDataExportAndSetDataExportState(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        if (state == DataExportState.DOWNLOADED) {
            assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.DOWNLOADED_DELETED);
        }
        else {
            assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.DELETED);
        }
        verify(fileService).schedulePathForDeletion(Path.of(dataExportFromDb.getFilePath()), 2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRequestForAnotherUserInstructor_forbidden() throws Exception {
        request.post("/api/admin/data-exports/" + TEST_PREFIX + "student1", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testRequestForAnotherUserAsAdmin_success() throws Exception {
        var usernameToRequest = TEST_PREFIX + "student1";
        dataExportRepository.deleteAll();
        var response = request.postWithResponseBody("/api/admin/data-exports/" + usernameToRequest, null, RequestDataExportDTO.class, HttpStatus.OK);
        assertThat(response.dataExportState()).isEqualTo(DataExportState.REQUESTED);
        assertThat(response.createdDate()).isNotNull();
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(response.id());
        assertThat(dataExportFromDb.getUser().getLogin()).isEqualTo(usernameToRequest);
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.REQUESTED);
        assertThat(dataExportFromDb.getCreatedBy()).isEqualTo(TEST_PREFIX + "admin");
    }

    private DataExport initDataExport(DataExportState state) {
        DataExport dataExport = new DataExport();
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setDataExportState(state);
        dataExport.setFilePath("path");
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;
    }
}
