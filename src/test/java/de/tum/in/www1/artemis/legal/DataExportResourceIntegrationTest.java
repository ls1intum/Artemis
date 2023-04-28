package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.DataExportState;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;

@ExtendWith(MockitoExtension.class)
class DataExportResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "dataexport";

    @Value("${artemis.data-export-path}")
    Path dataExportPath;

    @Autowired
    private DataExportRepository dataExportRepository;

    @SpyBean
    private DataExportService dataExportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    private static final String TEST_DATA_EXPORT_BASE_FILE_PATH = "src/test/resources/test-data/data-export/data-export.zip";

    private static final String FILE_FORMAT_CSV = ".csv";

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 5, 5, 5, 1);
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 5, 5, 5, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_containsCorrectContent() throws Exception {
        prepareTestDataForDataExportCreation();
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        var dataExport = request.putWithResponseBody("/api/" + userForExport.getId() + "/data-export", null, DataExport.class, HttpStatus.OK);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExport.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getRequestDate()).isNotNull();
        assertThat(dataExportFromDb.getCreationDate()).isNotNull();
        // extract zip file and check content
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        Predicate<Path> generalUserInformationCsv = path -> "general_user_information.csv".equals(path.getFileName().toString());
        Predicate<Path> courseDir = path -> path.getFileName().toString().startsWith("course_short");
        assertThat(extractedZipDirPath).isDirectoryContaining(generalUserInformationCsv).isDirectoryContaining(courseDir);
        var courseDirPath = getCourseDirectoryPath(extractedZipDirPath);
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("FileUpload2"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling0"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling3")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("Text1"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("TSTEXC")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("quiz"));
        getExerciseDirectoryPaths(courseDirPath).forEach(this::assertCorrectContentForExercise);

    }

    private void prepareTestDataForDataExportCreation() throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        Course course1 = database.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 4, 2, 1, 1, false, 1, validModel);
        var programmingExercise = database.addProgrammingExerciseToCourse(course1, false);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        var participation = database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, TEST_PREFIX + "student1",
                programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        var submission = database.createProgrammingSubmission(participation, false, "abc");
        var submission2 = database.createProgrammingSubmission(participation, false, "def");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 2.0, true, ZonedDateTime.now().minusHours(3));
        database.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course1, TEST_PREFIX + "student1");
        database.addSubmission(participation, submission);
        database.addSubmission(participation, submission2);
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        // Mock student repo
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(programmingExerciseTestService.studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), anyString(), anyBoolean());
    }

    private void assertCorrectContentForExercise(Path exerciseDirPath) {
        Predicate<Path> participationFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_CSV) && path.getFileName().toString().contains("participation");
        Predicate<Path> resultsFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_CSV) && path.getFileName().toString().contains("results");
        Predicate<Path> submissionFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_CSV) && path.getFileName().toString().contains("submission");
        assertThat(exerciseDirPath).isDirectoryContaining(participationFile).isDirectoryContaining(resultsFile).isDirectoryContaining(submissionFile);
        if (exerciseDirPath.toString().contains("TSTEXC")) {
            // zip file of the repository
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith(".zip"));
        }
        else if (exerciseDirPath.toString().contains("Modeling")) {
            // model as json file
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith(".json"));
        }
        else if (exerciseDirPath.toString().contains("Text")) {
            // submission text txt file
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("_text.txt"));
        }

    }

    private Path getCourseDirectoryPath(Path rootPath) throws IOException {
        try (var files = Files.list(rootPath).filter(Files::isDirectory)) {
            return files.findFirst().get();
        }
    }

    private List<Path> getExerciseDirectoryPaths(Path coursePath) throws IOException {
        try (var files = Files.list(coursePath).filter(Files::isDirectory)) {
            return files.toList();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownloadSuccess() throws Exception {
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        // create an export
        var dataExport = prepareDataExportForDownload();
        dataExport.setUser(userForExport);
        dataExport = dataExportRepository.save(dataExport);
        var dataExportFile = request.getFile("/api/" + userForExport.getId() + "/data-export/" + dataExport.getId(), HttpStatus.OK, new LinkedMultiValueMap<>());
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
        var user1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        var user2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/" + user1.getId() + "/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDoesntBelongToLoggedInUser_forbidden() throws Exception {
        var user2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/" + user2.getId() + "/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportErrorDuringCreation_internalServerError() throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        when(dataExportService.requestDataExport(userForExport)).thenThrow(new RuntimeException("Error!"));
        request.putWithResponseBody("/api/" + userForExport.getId() + "/data-export", null, DataExport.class, HttpStatus.INTERNAL_SERVER_ERROR);

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
        request.get("/api/" + userForExport.getId() + "/data-export/" + dataExport.getId(), HttpStatus.INTERNAL_SERVER_ERROR, Resource.class);

    }

    @ParameterizedTest
    @EnumSource(value = DataExportState.class, names = { "REQUESTED", "IN_CREATION", "DELETED", "DOWNLOADED_DELETED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExport_notYetFullyCreatedOrDeleted_accessForbidden(DataExportState state) throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        DataExport dataExport = new DataExport();
        dataExport.setUser(userForExport);
        dataExport.setFilePath("not-existent");
        dataExport.setDataExportState(state);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/" + userForExport.getId() + "/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportIdNotExistent_notFound() throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        request.get("/api/" + userForExport.getId() + "/data-export/999999", HttpStatus.NOT_FOUND, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserIdNotExistent_notFound() throws Exception {
        request.get("/api/" + 999999999 + "/data-export/999999", HttpStatus.NOT_FOUND, Resource.class);

    }
}
