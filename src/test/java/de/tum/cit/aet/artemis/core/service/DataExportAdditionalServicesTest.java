package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyProgressUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.profile.util.LearnerProfileUtilService;
import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.test_repository.SavedPostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.export.IrisChatSessionExportDTO;
import de.tum.cit.aet.artemis.core.service.export.DataExportCommunicationDataService;
import de.tum.cit.aet.artemis.core.service.export.DataExportCompetencyProgressService;
import de.tum.cit.aet.artemis.core.service.export.DataExportIrisService;
import de.tum.cit.aet.artemis.core.service.export.DataExportLearnerProfileService;
import de.tum.cit.aet.artemis.core.service.export.DataExportTutorialGroupService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * Tests for the additional data export services:
 * - DataExportIrisService
 * - DataExportLearnerProfileService
 * - DataExportCompetencyProgressService
 * - DataExportTutorialGroupService
 * - Enhanced DataExportCommunicationDataService (saved posts, notification settings, conversation participations)
 */
class DataExportAdditionalServicesTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "dataexportadditional";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private DataExportCommunicationDataService dataExportCommunicationDataService;

    @Autowired
    private DataExportLearnerProfileService dataExportLearnerProfileService;

    @Autowired
    private DataExportCompetencyProgressService dataExportCompetencyProgressService;

    @Autowired
    private DataExportIrisService dataExportIrisService;

    @Autowired
    private SavedPostTestRepository savedPostRepository;

    @Autowired
    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    @Autowired
    private Optional<IrisChatSessionUtilService> irisChatSessionUtilService;

    @Autowired
    private Optional<LearnerProfileUtilService> learnerProfileUtilService;

    @Autowired
    private Optional<CompetencyUtilService> competencyUtilService;

    @Autowired
    private Optional<CompetencyProgressUtilService> competencyProgressUtilService;

    @Autowired
    private DataExportTutorialGroupService dataExportTutorialGroupService;

    @Autowired
    private Optional<TutorialGroupUtilService> tutorialGroupUtilService;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    private Path workingDirectory;

    private User testUser;

    private Course testCourse;

    @BeforeEach
    void setUp() throws IOException {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        testUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        testCourse = courseUtilService.createCourse();
        workingDirectory = tempFileUtilService.createTempDirectory("data-export-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (workingDirectory != null && Files.exists(workingDirectory)) {
            Files.walk(workingDirectory).sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.delete(path);
                }
                catch (IOException e) {
                    // ignore
                }
            });
        }
    }

    @Nested
    class SavedPostsExportTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportSavedPosts() throws IOException {
            // Create saved posts
            SavedPost savedPost1 = new SavedPost(testUser, 1L, PostingType.POST, SavedPostStatus.IN_PROGRESS, null);
            SavedPost savedPost2 = new SavedPost(testUser, 2L, PostingType.ANSWER, SavedPostStatus.COMPLETED, null);
            savedPostRepository.save(savedPost1);
            savedPostRepository.save(savedPost2);

            // Run export
            dataExportCommunicationDataService.createCommunicationDataExport(testUser.getId(), workingDirectory);

            // Verify saved posts file exists and contains correct data
            Path savedPostsFile = workingDirectory.resolve("saved_posts.csv");
            assertThat(savedPostsFile).exists();

            try (var reader = Files.newBufferedReader(savedPostsFile);
                    var csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
                var records = csvParser.getRecords();
                assertThat(records).hasSize(2);
            }
        }

    }

    @Nested
    class NotificationSettingsExportTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportNotificationSettings() throws IOException {
            // Create notification settings
            GlobalNotificationSetting setting = new GlobalNotificationSetting();
            setting.setUserId(testUser.getId());
            setting.setNotificationType(GlobalNotificationType.NEW_LOGIN);
            setting.setEnabled(false);
            globalNotificationSettingRepository.save(setting);

            // Run export
            dataExportCommunicationDataService.createCommunicationDataExport(testUser.getId(), workingDirectory);

            // Verify notification settings file exists
            Path notificationSettingsFile = workingDirectory.resolve("notification_settings.csv");
            assertThat(notificationSettingsFile).exists();

            try (var reader = Files.newBufferedReader(notificationSettingsFile);
                    var csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
                var records = csvParser.getRecords();
                assertThat(records).hasSize(1);
                assertThat(records.getFirst().get("notification_type")).isEqualTo("NEW_LOGIN");
                assertThat(records.getFirst().get("enabled")).isEqualTo("false");
            }
        }

    }

    @Nested
    class LearnerProfileExportTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportLearnerProfiles() throws IOException {
            // Create learner profile with course learner profile
            learnerProfileUtilService.ifPresent(service -> service.createCourseLearnerProfileForUsers(TEST_PREFIX, Set.of(testCourse)));

            // Run export
            dataExportLearnerProfileService.createLearnerProfileExport(testUser.getId(), workingDirectory);

            // Verify learner profiles file exists
            Path learnerProfilesFile = workingDirectory.resolve("learner_profiles.csv");
            if (learnerProfileUtilService.isPresent()) {
                assertThat(learnerProfilesFile).exists();

                try (var reader = Files.newBufferedReader(learnerProfilesFile);
                        var csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
                    var records = csvParser.getRecords();
                    assertThat(records).isNotEmpty();
                }
            }
        }

    }

    @Nested
    class CompetencyProgressExportTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportCompetencyProgress() throws IOException {
            // Create competency and progress
            if (competencyUtilService.isPresent() && competencyProgressUtilService.isPresent()) {
                CourseCompetency competency = competencyUtilService.get().createCompetency(testCourse);
                competencyProgressUtilService.get().createCompetencyProgress(competency, testUser, 0.5, 0.7);
            }

            // Run export
            dataExportCompetencyProgressService.createCompetencyProgressExport(testUser.getId(), workingDirectory);

            // Verify competency progress file exists
            Path competencyProgressFile = workingDirectory.resolve("competency_progress.csv");
            if (competencyUtilService.isPresent() && competencyProgressUtilService.isPresent()) {
                assertThat(competencyProgressFile).exists();

                try (var reader = Files.newBufferedReader(competencyProgressFile);
                        var csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
                    var records = csvParser.getRecords();
                    assertThat(records).hasSize(1);
                    assertThat(records.getFirst().get("progress")).isEqualTo("0.5");
                    assertThat(records.getFirst().get("confidence")).isEqualTo("0.7");
                }
            }
        }

    }

    @Nested
    class IrisExportTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportIrisChatSessions() throws IOException {
            // Create Iris chat session if Iris is available
            irisChatSessionUtilService.ifPresent(service -> service.createAndSaveCourseChatSessionForUser(testCourse, testUser));

            // Run export
            dataExportIrisService.createIrisExport(testUser.getId(), workingDirectory);

            // Verify Iris chat sessions file exists if Iris is available
            Path irisFile = workingDirectory.resolve("iris_chat_sessions.json");
            if (irisChatSessionUtilService.isPresent()) {
                assertThat(irisFile).exists();

                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                List<IrisChatSessionExportDTO> sessions = objectMapper.readValue(irisFile.toFile(), new TypeReference<>() {
                });
                assertThat(sessions).isNotEmpty();
                assertThat(sessions.getFirst().messages()).isNotEmpty();
                // Verify message content is properly exported (tests the back-reference fix)
                var firstMessage = sessions.getFirst().messages().getFirst();
                assertThat(firstMessage.content()).isNotNull();
                assertThat(firstMessage.content()).isNotEmpty();
                assertThat(firstMessage.sender()).isNotNull();
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportIrisChatSessionsWithMultipleMessages() throws IOException {
            // Create Iris chat session if Iris is available
            irisChatSessionUtilService.ifPresent(service -> service.createAndSaveCourseChatSessionForUser(testCourse, testUser));

            // Run export
            dataExportIrisService.createIrisExport(testUser.getId(), workingDirectory);

            // Verify all messages are exported with content
            Path irisFile = workingDirectory.resolve("iris_chat_sessions.json");
            if (irisChatSessionUtilService.isPresent()) {
                assertThat(irisFile).exists();

                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                List<IrisChatSessionExportDTO> sessions = objectMapper.readValue(irisFile.toFile(), new TypeReference<>() {
                });
                assertThat(sessions).isNotEmpty();
                // Find the session created in this test (the latest one)
                var latestSession = sessions.getLast();
                // The factory creates 2 messages (LLM and USER)
                assertThat(latestSession.messages()).hasSize(2);
                // Verify all messages have content
                for (var message : latestSession.messages()) {
                    assertThat(message.content()).isNotNull();
                    assertThat(message.content()).isNotEmpty();
                    assertThat(message.sender()).isIn("LLM", "USER");
                }
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportIrisChatSessionsNoSessionsCreatesNoFile() throws IOException {
            // Run export without creating any sessions
            dataExportIrisService.createIrisExport(testUser.getId(), workingDirectory);

            // Verify no file is created when there are no sessions
            Path irisFile = workingDirectory.resolve("iris_chat_sessions.json");
            assertThat(irisFile).doesNotExist();
        }

    }

    @Nested
    class TutorialGroupExportTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportTutorialGroupRegistrations() throws IOException {
            // Create tutorial group registration if tutorial groups are available
            tutorialGroupUtilService.ifPresent(service -> {
                // Create a tutorial group with the student already registered
                service.createTutorialGroup(testCourse.getId(), "Test Tutorial Group", "Additional info", 10, false, "Munich", "English", null, Set.of(testUser));
            });

            // Run export
            dataExportTutorialGroupService.createTutorialGroupExport(testUser.getId(), workingDirectory);

            // Verify tutorial group registrations file exists if tutorial groups are available
            Path tutorialGroupFile = workingDirectory.resolve("tutorial_group_registrations.csv");
            if (tutorialGroupUtilService.isPresent()) {
                assertThat(tutorialGroupFile).exists();

                try (var reader = Files.newBufferedReader(tutorialGroupFile);
                        var csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
                    var records = csvParser.getRecords();
                    assertThat(records).hasSize(1);
                    assertThat(records.getFirst().get("tutorial_group_title")).isEqualTo("Test Tutorial Group");
                }
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportTutorialGroupRegistrationsWithNoData() throws IOException {
            // Run export with no tutorial group registrations
            dataExportTutorialGroupService.createTutorialGroupExport(testUser.getId(), workingDirectory);

            // Verify no file is created when there are no registrations
            Path tutorialGroupFile = workingDirectory.resolve("tutorial_group_registrations.csv");
            assertThat(tutorialGroupFile).doesNotExist();
        }

    }

    @Nested
    class ConversationParticipationsExportTest {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testExportConversationParticipationsWithNoData() throws IOException {
            // Run export with no conversation participations
            dataExportCommunicationDataService.createCommunicationDataExport(testUser.getId(), workingDirectory);

            // Verify no file is created when there are no conversation participations
            Path conversationFile = workingDirectory.resolve("conversation_participations.csv");
            assertThat(conversationFile).doesNotExist();
        }

    }
}
