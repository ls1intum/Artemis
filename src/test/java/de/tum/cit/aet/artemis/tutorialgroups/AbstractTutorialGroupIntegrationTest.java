package de.tum.cit.aet.artemis.tutorialgroups;

import static de.tum.cit.aet.artemis.tutorialgroups.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseTestService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;
import de.tum.cit.aet.artemis.tutorialgroups.test_repository.TutorialGroupRegistrationTestRepository;
import de.tum.cit.aet.artemis.tutorialgroups.test_repository.TutorialGroupScheduleTestRepository;
import de.tum.cit.aet.artemis.tutorialgroups.test_repository.TutorialGroupTestRepository;
import de.tum.cit.aet.artemis.tutorialgroups.util.TutorialGroupUtilService;

/**
 * Contains useful methods for testing the tutorial groups feature.
 */
abstract class AbstractTutorialGroupIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    CourseTestService courseTestService;

    @Autowired
    UserTestRepository userRepository;

    @Autowired
    CourseTestRepository courseRepository;

    @Autowired
    TutorialGroupTestRepository tutorialGroupTestRepository;

    @Autowired
    TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    TutorialGroupScheduleTestRepository tutorialGroupScheduleTestRepository;

    @Autowired
    TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    TutorialGroupRegistrationTestRepository tutorialGroupRegistrationTestRepository;

    @Autowired
    TutorialGroupService tutorialGroupService;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    @Autowired
    TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    CourseUtilService courseUtilService;

    @Autowired
    UserUtilService userUtilService;

    Long exampleCourseId;

    Long exampleConfigurationId;

    String exampleTimeZone = "Europe/Bucharest";

    String testPrefix = "";

    Integer defaultSessionStartHour = 10;

    Integer defaultSessionEndHour = 12;

    static final LocalDate FIRST_AUGUST_MONDAY = LocalDate.of(2022, 8, 1);

    static final LocalDate SECOND_AUGUST_MONDAY = LocalDate.of(2022, 8, 8);

    static final LocalDate THIRD_AUGUST_MONDAY = LocalDate.of(2022, 8, 15);

    static final LocalDate FOURTH_AUGUST_MONDAY = LocalDate.of(2022, 8, 22);

    static final LocalDate FIRST_SEPTEMBER_MONDAY = LocalDate.of(2022, 9, 5);

    static final LocalDateTime FIRST_AUGUST_MONDAY_00_00 = LocalDateTime.of(2022, 8, 1, 0, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_08_00 = LocalDateTime.of(2022, 8, 1, 8, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_10_00 = LocalDateTime.of(2022, 8, 1, 10, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_11_00 = LocalDateTime.of(2022, 8, 1, 11, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_12_00 = LocalDateTime.of(2022, 8, 1, 12, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_13_00 = LocalDateTime.of(2022, 8, 1, 13, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_18_00 = LocalDateTime.of(2022, 8, 1, 18, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_23_59 = LocalDateTime.of(2022, 8, 1, 23, 59);

    static final LocalDateTime SECOND_AUGUST_MONDAY_00_00 = LocalDateTime.of(2022, 8, 8, 0, 0);

    static final LocalDateTime SECOND_AUGUST_MONDAY_23_59 = LocalDateTime.of(2022, 8, 8, 23, 59);

    static final LocalDateTime THIRD_AUGUST_MONDAY_00_00 = LocalDateTime.of(2022, 8, 15, 0, 0);

    static final LocalDateTime THIRD_AUGUST_MONDAY_23_59 = LocalDateTime.of(2022, 8, 15, 23, 59);

    static final LocalDateTime FOURTH_AUGUST_MONDAY_00_00 = LocalDateTime.of(2022, 8, 22, 0, 0);

    static final LocalDateTime FIRST_SEPTEMBER_MONDAY_00_00 = LocalDateTime.of(2022, 9, 5, 0, 0);

    @BeforeEach
    void setupTestScenario() {
        this.testPrefix = getTestPrefix();
        var course = courseUtilService.createCourse();
        course.setTimeZone(exampleTimeZone);
        courseRepository.save(course);
        exampleCourseId = course.getId();
        exampleConfigurationId = tutorialGroupUtilService.createTutorialGroupConfiguration(exampleCourseId, LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1)).getId();
    }

    // === Abstract Methods ===
    abstract String getTestPrefix();

    // === Paths ===
    String getTutorialGroupsPath(Long courseId) {
        return "/api/courses/" + courseId + "/tutorial-groups";
    }

    String getTutorialGroupsPath(Long courseId, Long tutorialGroupId) {
        return this.getTutorialGroupsPath(courseId) + "/" + tutorialGroupId;
    }

    String getTutorialGroupsConfigurationPath(Long courseId) {
        return "/api/courses/" + courseId + "/tutorial-groups-configuration";
    }

    String getTutorialGroupsConfigurationPath(Long courseId, Long configurationId) {
        return this.getTutorialGroupsConfigurationPath(courseId) + "/" + configurationId;
    }

    String getTutorialGroupFreePeriodsPath() {
        return this.getTutorialGroupsConfigurationPath(exampleCourseId, exampleConfigurationId) + "/tutorial-free-periods";
    }

    String getTutorialGroupFreePeriodsPath(Long freePeriodId) {
        return this.getTutorialGroupFreePeriodsPath() + "/" + freePeriodId;
    }

    String getSessionsPathOfTutorialGroup(Long tutorialGroupId) {
        return this.getTutorialGroupsPath(this.exampleCourseId, tutorialGroupId) + "/sessions";
    }

    String getSessionsPathOfTutorialGroup(Long tutorialGroupId, Long sessionId) {
        return this.getSessionsPathOfTutorialGroup(tutorialGroupId) + "/" + sessionId;
    }

    // === UTILS ===
    TutorialGroupSession buildAndSaveExampleIndividualTutorialGroupSession(Long tutorialGroupId, LocalDateTime localDate) {
        return tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroupId, getExampleSessionStartOnDate(localDate.toLocalDate()),
                getExampleSessionEndOnDate(localDate.toLocalDate()), null);
    }

    TutorialGroupSession buildAndSaveExampleIndividualTutorialGroupSession(Long tutorialGroupId, LocalDate localDate, Integer attendanceCount) {
        return tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroupId, getExampleSessionStartOnDate(localDate), getExampleSessionEndOnDate(localDate),
                attendanceCount);
    }

    TutorialGroupsConfiguration buildExampleConfiguration(Long courseId) {
        TutorialGroupsConfiguration tutorialGroupsConfiguration = new TutorialGroupsConfiguration();
        tutorialGroupsConfiguration.setCourse(courseRepository.findById(courseId).orElseThrow());
        tutorialGroupsConfiguration.setTutorialPeriodStartInclusive(FIRST_AUGUST_MONDAY_00_00.toLocalDate().toString());
        tutorialGroupsConfiguration.setTutorialPeriodEndInclusive(FIRST_SEPTEMBER_MONDAY_00_00.toLocalDate().toString());
        tutorialGroupsConfiguration.setUseTutorialGroupChannels(true);
        tutorialGroupsConfiguration.setUsePublicTutorialGroupChannels(true);
        return tutorialGroupsConfiguration;
    }

    TutorialGroupSchedule buildExampleSchedule(LocalDate validFromInclusive, LocalDate validToInclusive) {
        TutorialGroupSchedule newTutorialGroupSchedule = new TutorialGroupSchedule();
        newTutorialGroupSchedule.setDayOfWeek(1);
        newTutorialGroupSchedule.setStartTime("10:00:00");
        newTutorialGroupSchedule.setEndTime("12:00:00");
        newTutorialGroupSchedule.setValidFromInclusive(validFromInclusive.toString());
        newTutorialGroupSchedule.setValidToInclusive(validToInclusive.toString());
        newTutorialGroupSchedule.setLocation("LoremIpsum");
        newTutorialGroupSchedule.setRepetitionFrequency(1);
        return newTutorialGroupSchedule;
    }

    TutorialGroup buildAndSaveTutorialGroupWithoutSchedule(String tutorLogin, String... studentLogins) {
        Set<User> students = Set.of();
        if (studentLogins != null) {
            students = Arrays.stream(studentLogins).map(login -> userRepository.findOneByLogin(login).orElseThrow()).collect(Collectors.toSet());
        }
        return tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum", 10, false, "Garching", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + tutorLogin).orElseThrow(), students);
    }

    TutorialGroup buildTutorialGroupWithoutSchedule(String tutorLogin) {
        var course = courseRepository.findByIdElseThrow(exampleCourseId);
        var tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle(generateRandomTitle());
        tutorialGroup.setTeachingAssistant(userRepository.findOneByLogin(testPrefix + tutorLogin).orElseThrow());
        return tutorialGroup;
    }

    TutorialGroup buildTutorialGroupWithExampleSchedule(LocalDate validFromInclusive, LocalDate validToInclusive, String tutorLogin) {
        var course = courseRepository.findByIdElseThrow(exampleCourseId);
        var newTutorialGroup = new TutorialGroup();
        newTutorialGroup.setCourse(course);
        newTutorialGroup.setTitle(generateRandomTitle());
        newTutorialGroup.setTeachingAssistant(userRepository.findOneByLogin(testPrefix + tutorLogin).orElseThrow());

        newTutorialGroup.setTutorialGroupSchedule(this.buildExampleSchedule(validFromInclusive, validToInclusive));

        return newTutorialGroup;
    }

    TutorialGroup setUpTutorialGroupWithSchedule(Long courseId, String tutorLogin) throws Exception {
        var newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(FIRST_AUGUST_MONDAY_00_00.toLocalDate(), SECOND_AUGUST_MONDAY_00_00.toLocalDate(), tutorLogin);
        var scheduleToCreate = newTutorialGroup.getTutorialGroupSchedule();
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(courseId), newTutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();

        newTutorialGroup = tutorialGroupTestRepository.findByIdElseThrow(persistedTutorialGroupId);
        this.assertTutorialGroupPersistedWithSchedule(newTutorialGroup, scheduleToCreate);
        return newTutorialGroup;
    }

    List<TutorialGroupSession> getTutorialGroupSessionsAscending(Long tutorialGroupId) {
        var sessions = new ArrayList<>(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId).stream().toList());
        sessions.sort(Comparator.comparing(TutorialGroupSession::getStart));
        return sessions;
    }

    ZonedDateTime getExampleSessionStartOnDate(LocalDate date) {
        return getDateTimeInExampleTimeZone(date, defaultSessionStartHour);
    }

    ZonedDateTime getExampleSessionEndOnDate(LocalDate date) {
        return getDateTimeInExampleTimeZone(date, defaultSessionEndHour);
    }

    ZonedDateTime getDateTimeInExampleTimeZone(LocalDate date, int hour) {
        return ZonedDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, 0, 0, 0, ZoneId.of(this.exampleTimeZone));
    }

    ZonedDateTime getDateTimeInBerlinTimeZone(LocalDate date, int hour) {
        return ZonedDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, 0, 0, 0, ZoneId.of("Europe/Berlin"));
    }

    // === ASSERTIONS ===

    void assertIndividualSessionIsActiveOnDate(TutorialGroupSession sessionToCheck, LocalDateTime date, Long tutorialGroupId) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.empty(), tutorialGroupId, getExampleSessionStartOnDate(date.toLocalDate()),
                getExampleSessionEndOnDate(date.toLocalDate()), "LoremIpsum", TutorialGroupSessionStatus.ACTIVE, null);
    }

    void assertIndividualSessionIsCancelledOnDate(TutorialGroupSession sessionToCheck, LocalDateTime date, Long tutorialGroupId, String statusExplanation) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.empty(), tutorialGroupId, getExampleSessionStartOnDate(date.toLocalDate()),
                getExampleSessionEndOnDate(date.toLocalDate()), "LoremIpsum", TutorialGroupSessionStatus.CANCELLED, statusExplanation);
    }

    void assertScheduledSessionIsActiveOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, TutorialGroupSchedule schedule) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.of(schedule.getId()), tutorialGroupId, getExampleSessionStartOnDate(date),
                getExampleSessionEndOnDate(date), schedule.getLocation(), TutorialGroupSessionStatus.ACTIVE, null);
    }

    void assertScheduledSessionIsCancelledOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, TutorialGroupSchedule schedule) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.of(schedule.getId()), tutorialGroupId, getExampleSessionStartOnDate(date),
                getExampleSessionEndOnDate(date), schedule.getLocation(), TutorialGroupSessionStatus.CANCELLED, null);
    }

    void assertTutorialGroupPersistedWithSchedule(TutorialGroup tutorialGroupToCheck, TutorialGroupSchedule expectedSchedule) {
        assertThat(tutorialGroupToCheck.getId()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule().getId()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule().sameSchedule(expectedSchedule)).isTrue();
    }

    void assertTutorialGroupSessionProperties(TutorialGroupSession tutorialGroupSessionToCheck, Optional<Long> expectedScheduleId, Long expectedTutorialGroupId,
            ZonedDateTime expectedStart, ZonedDateTime expectedEnd, String expectedLocation, TutorialGroupSessionStatus expectedStatus, String expectedStatusExplanation) {
        assertThat(tutorialGroupSessionToCheck.getStart()).isEqualTo(expectedStart);
        assertThat(tutorialGroupSessionToCheck.getEnd()).isEqualTo(expectedEnd);
        assertThat(tutorialGroupSessionToCheck.getTutorialGroup().getId()).isEqualTo(expectedTutorialGroupId);
        expectedScheduleId.ifPresent(scheduleId -> assertThat(tutorialGroupSessionToCheck.getTutorialGroupSchedule().getId()).isEqualTo(scheduleId));
        assertThat(tutorialGroupSessionToCheck.getLocation()).isEqualTo(expectedLocation);
        assertThat(tutorialGroupSessionToCheck.getStatus()).isEqualTo(expectedStatus);
        assertThat(tutorialGroupSessionToCheck.getStatusExplanation()).isEqualTo(expectedStatusExplanation);
    }

    void assertConfigurationStructure(TutorialGroupsConfiguration configuration, LocalDate expectedPeriodStart, LocalDate expectedPeriodEnd, Long courseId,
            Boolean expectedChannelModeEnabled, Boolean expectedChannelsPublic) {
        assertThat(configuration.getCourse().getId()).isEqualTo(courseId);
        assertThat(LocalDate.parse(configuration.getTutorialPeriodStartInclusive())).isEqualTo(expectedPeriodStart);
        assertThat(LocalDate.parse(configuration.getTutorialPeriodEndInclusive())).isEqualTo(expectedPeriodEnd);
        assertThat(configuration.getUseTutorialGroupChannels()).isEqualTo(expectedChannelModeEnabled);
        assertThat(configuration.getUsePublicTutorialGroupChannels()).isEqualTo(expectedChannelsPublic);
    }

    Channel asserTutorialGroupChannelIsCorrectlyConfigured(TutorialGroup tutorialGroup) {
        var configuration = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(tutorialGroup.getCourse().getId()).getTutorialGroupsConfiguration();

        Function<TutorialGroup, String> expectedTutorialGroupName = (TutorialGroup tg) -> {
            var cleanedTitle = tg.getTitle().replaceAll("\\s", "-").toLowerCase();
            return "tutorgroup-" + cleanedTitle.substring(0, Math.min(cleanedTitle.length(), 18));
        };
        var tutorialGroupFromDb = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroup.getId());

        var channelOptional = tutorialGroupTestRepository.getTutorialGroupChannel(tutorialGroupFromDb.getId());
        assertThat(channelOptional).isPresent();
        var channel = channelOptional.get();
        assertThat(channel.getName()).isEqualTo(expectedTutorialGroupName.apply(tutorialGroupFromDb));
        assertThat(channel.getIsPublic()).isEqualTo(configuration.getUsePublicTutorialGroupChannels());
        assertThat(channel.getIsArchived()).isFalse();
        assertThat(channel.getCreator()).isNull();
        assertThat(channel.getIsAnnouncementChannel()).isFalse();

        var members = conversationParticipantRepository.findConversationParticipantsByConversationId(channel.getId());
        var moderators = members.stream().filter(ConversationParticipant::getIsModerator).collect(Collectors.toSet());
        var nonModerators = members.stream().filter(participant -> !participant.getIsModerator()).collect(Collectors.toSet());

        var registeredStudents = tutorialGroupFromDb.getRegistrations().stream().map(TutorialGroupRegistration::getStudent).collect(Collectors.toSet());
        if (registeredStudents.isEmpty()) {
            assertThat(nonModerators).isEmpty();
        }
        else {
            assertThat(nonModerators).map(ConversationParticipant::getUser).containsExactlyInAnyOrderElementsOf(registeredStudents);
        }

        if (tutorialGroupFromDb.getTeachingAssistant() != null) {
            assertThat(moderators.stream().map(ConversationParticipant::getUser).collect(Collectors.toSet())).containsExactlyInAnyOrder(tutorialGroupFromDb.getTeachingAssistant());
        }
        else {
            assertThat(moderators).isEmpty();
        }
        return channel;
    }

    void assertTutorialGroupChannelDoesNotExist(TutorialGroup tutorialGroup) {
        var channelOptional = tutorialGroupTestRepository.getTutorialGroupChannel(tutorialGroup.getId());
        assertThat(channelOptional).isEmpty();
    }

    public static class RandomTutorialGroupGenerator {

        private static final String LOWERCASE_LETTERS = "abcdefghijklmnopqrstuvwxyz";

        private static final String NUMBERS = "0123456789";

        private static final String ALL_CHARS = LOWERCASE_LETTERS + NUMBERS;

        private static final Random RANDOM = new Random(1042001L);

        public static String generateRandomTitle() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
            }
            return sb.toString();
        }
    }

}
