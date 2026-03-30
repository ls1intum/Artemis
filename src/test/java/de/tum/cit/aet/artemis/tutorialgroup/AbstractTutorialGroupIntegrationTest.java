package de.tum.cit.aet.artemis.tutorialgroup;

import static de.tum.cit.aet.artemis.tutorialgroup.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseTestService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupRegistrationTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupScheduleTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

public abstract class AbstractTutorialGroupIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // Repositories
    @Autowired
    protected TutorialGroupTestRepository tutorialGroupTestRepository;

    @Autowired
    protected TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    protected TutorialGroupScheduleTestRepository tutorialGroupScheduleTestRepository;

    @Autowired
    protected TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    protected TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    protected TutorialGroupRegistrationTestRepository tutorialGroupRegistrationTestRepository;

    @Autowired
    protected UserTestRepository userRepository;

    @Autowired
    protected CourseTestRepository courseRepository;

    @Autowired
    protected ChannelRepository channelRepository;

    @Autowired
    protected ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    protected PostTestRepository postRepository;

    @Autowired
    protected TutorialGroupService tutorialGroupService;

    @Autowired
    protected TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    @Autowired
    protected TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected UserUtilService userUtilService;

    @Autowired
    protected ConversationUtilService conversationUtilService;

    @Autowired
    protected CourseTestService courseTestService;

    static final LocalDate FIRST_AUGUST_MONDAY = LocalDate.of(2022, 8, 1);

    static final LocalDate SECOND_AUGUST_MONDAY = LocalDate.of(2022, 8, 8);

    static final LocalDate FOURTH_AUGUST_MONDAY = LocalDate.of(2022, 8, 22);

    static final LocalDate FIFTH_AUGUST_MONDAY = LocalDate.of(2022, 8, 29);

    static final LocalDate FIRST_SEPTEMBER_MONDAY = LocalDate.of(2022, 9, 5);

    static final LocalDateTime FIRST_AUGUST_MONDAY_00_00 = LocalDateTime.of(2022, 8, 1, 0, 0);

    static final LocalDate MONDAY_AFTER_DST_SWITCH = LocalDate.of(2020, 3, 30);

    static final LocalDateTime MONDAY_BEFORE_DST_SWITCH_10_00 = LocalDateTime.of(2020, 3, 23, 10, 0);

    static final LocalDateTime MONDAY_BEFORE_DST_SWITCH_12_00 = LocalDateTime.of(2020, 3, 23, 12, 0);

    static final LocalDateTime MONDAY_AFTER_DST_SWITCH_10_00 = LocalDateTime.of(2020, 3, 30, 10, 0);

    static final LocalDateTime MONDAY_AFTER_DST_SWITCH_12_00 = LocalDateTime.of(2020, 3, 30, 12, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_08_00 = LocalDateTime.of(2022, 8, 1, 8, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_10_00 = LocalDateTime.of(2022, 8, 1, 10, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_11_00 = LocalDateTime.of(2022, 8, 1, 11, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_12_00 = LocalDateTime.of(2022, 8, 1, 12, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_13_00 = LocalDateTime.of(2022, 8, 1, 13, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_14_00 = LocalDateTime.of(2022, 8, 1, 14, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_18_00 = LocalDateTime.of(2022, 8, 1, 18, 0);

    static final LocalDateTime FIRST_AUGUST_MONDAY_23_59 = LocalDateTime.of(2022, 8, 1, 23, 59);

    static final LocalDateTime SECOND_AUGUST_MONDAY_00_00 = LocalDateTime.of(2022, 8, 8, 0, 0);

    static final LocalDateTime SECOND_AUGUST_MONDAY_23_59 = LocalDateTime.of(2022, 8, 8, 23, 59);

    static final LocalDateTime FOURTH_AUGUST_MONDAY_00_00 = LocalDateTime.of(2022, 8, 22, 0, 0);

    static final LocalDateTime FIRST_SEPTEMBER_MONDAY_00_00 = LocalDateTime.of(2022, 9, 5, 0, 0);

    static final LocalDateTime FIRST_SEPTEMBER_MONDAY_10_00 = LocalDateTime.of(2022, 9, 5, 10, 0);

    static final LocalDateTime FIRST_SEPTEMBER_MONDAY_12_00 = LocalDateTime.of(2022, 9, 5, 12, 0);

    static final LocalDateTime SECOND_SEPTEMBER_MONDAY_10_00 = LocalDateTime.of(2022, 9, 12, 10, 0);

    static final LocalDateTime SECOND_SEPTEMBER_MONDAY_12_00 = LocalDateTime.of(2022, 9, 12, 12, 0);

    protected static final String FIRST_COURSE_INSTRUCTOR1_LOGIN = "firstcourseinstructor1";

    protected static final String FIRST_COURSE_EDITOR1_LOGIN = "firstcourseeditor1";

    protected static final String FIRST_COURSE_TUTOR1_LOGIN = "firstcoursetutor1";

    protected static final String FIRST_COURSE_TUTOR2_LOGIN = "firstcoursetutor2";

    protected static final String FIRST_COURSE_STUDENT1_LOGIN = "firstcoursestudent1";

    protected static final String FIRST_COURSE_STUDENT2_LOGIN = "firstcoursestudent2";

    protected static final String FIRST_COURSE_STUDENT3_LOGIN = "firstcoursestudent3";

    protected static final String FIRST_COURSE_STUDENT4_LOGIN = "firstcoursestudent4";

    protected static final String SECOND_COURSE_EDITOR1_LOGIN = "secondcourseeditor1";

    protected static final String SECOND_COURSE_TUTOR1_LOGIN = "secondcoursetutor1";

    protected static final String SECOND_COURSE_INSTRUCTOR1_LOGIN = "secondcourseinstructor1";

    Course exampleCourse;

    Long exampleCourseId;

    Long exampleConfigurationId;

    Course exampleCourse2;

    Long exampleCourse2Id;

    String exampleTimeZone = "Europe/Bucharest";

    String testPrefix = "";

    Integer defaultSessionStartHour = 10;

    Integer defaultSessionEndHour = 12;

    @BeforeEach
    void setupTestScenario() {
        this.testPrefix = getTestPrefix();
        var firstCourse = courseUtilService.createCourseWithUserPrefix(testPrefix + "firstCourse");
        firstCourse.setTimeZone(exampleTimeZone);
        this.exampleCourse = courseRepository.save(firstCourse);
        exampleCourseId = firstCourse.getId();
        exampleConfigurationId = tutorialGroupUtilService.createTutorialGroupConfiguration(exampleCourseId, LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1)).getId();

        var secondCourse = courseUtilService.createCourseWithUserPrefix(testPrefix + "secondCourse");
        secondCourse.setTimeZone(exampleTimeZone);
        this.exampleCourse2 = courseRepository.save(secondCourse);
        exampleCourse2Id = secondCourse.getId();
    }

    abstract String getTestPrefix();

    // === Creation of Test Scenarios ===

    protected record UsersInCourseOne(User instructor, User tutor1, User tutor2, User student1, User student2, User student3, User student4) {
    };

    protected record UsersInCourseTwo(User instructor, User editor, User tutor) {
    };

    protected record TutorialGroupOneInCourseOneData(TutorialGroup group, TutorialGroupSchedule schedule, List<TutorialGroupSession> sessions, Channel channel) {
    };

    protected record TutorialGroupTwoInCourseOneData(TutorialGroup group, TutorialGroupSession session, Channel channel) {
    };

    protected record TutorialGroupOneInCourseTwoData(TutorialGroup group, List<TutorialGroupSession> sessions) {
    };

    UsersInCourseOne createAndSaveUsersInCourseOneData() {
        userUtilService.addStudent(exampleCourse.getStudentGroupName(), FIRST_COURSE_STUDENT1_LOGIN);
        userUtilService.addStudent(exampleCourse.getStudentGroupName(), FIRST_COURSE_STUDENT2_LOGIN);
        userUtilService.addStudent(exampleCourse.getStudentGroupName(), FIRST_COURSE_STUDENT3_LOGIN);
        userUtilService.addStudent(exampleCourse.getStudentGroupName(), FIRST_COURSE_STUDENT4_LOGIN);
        userUtilService.addTeachingAssistant(exampleCourse.getTeachingAssistantGroupName(), FIRST_COURSE_TUTOR1_LOGIN);
        userUtilService.addTeachingAssistant(exampleCourse.getTeachingAssistantGroupName(), FIRST_COURSE_TUTOR2_LOGIN);
        userUtilService.addEditor(exampleCourse.getEditorGroupName(), FIRST_COURSE_EDITOR1_LOGIN);
        userUtilService.addInstructor(exampleCourse.getInstructorGroupName(), FIRST_COURSE_INSTRUCTOR1_LOGIN);

        var instructor = userRepository.findOneByLogin(FIRST_COURSE_INSTRUCTOR1_LOGIN).orElseThrow();
        var tutor1 = userRepository.findOneByLogin(FIRST_COURSE_TUTOR1_LOGIN).orElseThrow();
        var tutor2 = userRepository.findOneByLogin(FIRST_COURSE_TUTOR2_LOGIN).orElseThrow();
        var student1 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT1_LOGIN).orElseThrow();
        var student2 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT2_LOGIN).orElseThrow();
        var student3 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT3_LOGIN).orElseThrow();
        var student4 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT4_LOGIN).orElseThrow();

        student3.setRegistrationNumber("3");
        userRepository.save(student3);
        student4.setRegistrationNumber("4");
        userRepository.save(student4);

        return new UsersInCourseOne(instructor, tutor1, tutor2, student1, student2, student3, student4);
    }

    UsersInCourseTwo createAndSaveUsersInCourseTwoData() {
        userUtilService.addTeachingAssistant(exampleCourse2.getTeachingAssistantGroupName(), SECOND_COURSE_TUTOR1_LOGIN);
        userUtilService.addEditor(exampleCourse2.getEditorGroupName(), SECOND_COURSE_EDITOR1_LOGIN);
        userUtilService.addInstructor(exampleCourse2.getInstructorGroupName(), SECOND_COURSE_INSTRUCTOR1_LOGIN);

        var instructor = userRepository.findOneByLogin(SECOND_COURSE_INSTRUCTOR1_LOGIN).orElseThrow();
        var editor = userRepository.findOneByLogin(SECOND_COURSE_EDITOR1_LOGIN).orElseThrow();
        var tutor = userRepository.findOneByLogin(SECOND_COURSE_TUTOR1_LOGIN).orElseThrow();

        return new UsersInCourseTwo(instructor, editor, tutor);
    }

    TutorialGroupOneInCourseOneData createAndSaveTutorialGroupOneInCourseOneData(User tutor, User student) {
        var tutorialGroup = tutorialGroupUtilService.createAndSaveTutorialGroup(exampleCourse.getId(), "TG Mo 13", "SampleInfo1", 10, false, "Garching", Language.ENGLISH.name(),
                tutor, Set.of(student));

        TutorialGroupSchedule schedule = tutorialGroupUtilService.createAndSaveTutorialGroupSchedule(tutorialGroup, 1, "13:00:00", "14:00:00", 1, FIRST_AUGUST_MONDAY.toString(),
                FIFTH_AUGUST_MONDAY.toString(), "01.05.13");

        var scheduleConformingSessions = tutorialGroupUtilService.createAndSaveRegularSessionsFromTutorialGroupSchedule(exampleCourse, tutorialGroup, schedule);
        scheduleConformingSessions.getFirst().setStatus(TutorialGroupSessionStatus.CANCELLED);
        scheduleConformingSessions.get(1).setLocation("new room");
        scheduleConformingSessions.get(2).setStart(scheduleConformingSessions.get(2).getStart().plusHours(2));
        scheduleConformingSessions.get(2).setEnd(scheduleConformingSessions.get(2).getEnd().plusHours(2));
        scheduleConformingSessions.get(3).setStart(scheduleConformingSessions.get(3).getStart().plusDays(1));
        scheduleConformingSessions.get(3).setEnd(scheduleConformingSessions.get(3).getEnd().plusDays(1));
        scheduleConformingSessions.get(4).setAttendanceCount(10);
        List<TutorialGroupSession> sessions = new LinkedList<>(scheduleConformingSessions);

        TutorialGroupSession individualSession = tutorialGroupUtilService.createTutorialGroupSession(ZonedDateTime.of(SECOND_SEPTEMBER_MONDAY_10_00, ZoneId.of(exampleTimeZone)),
                ZonedDateTime.of(SECOND_SEPTEMBER_MONDAY_12_00, ZoneId.of(exampleTimeZone)), "01.05.13", null, TutorialGroupSessionStatus.ACTIVE, null, tutorialGroup);
        sessions.add(individualSession);
        tutorialGroupSessionRepository.saveAllAndFlush(sessions);

        var channel = tutorialGroupChannelManagementService.createChannelForTutorialGroup(tutorialGroup);

        return new TutorialGroupOneInCourseOneData(tutorialGroup, schedule, sessions, channel);
    }

    TutorialGroupTwoInCourseOneData createAndSaveTutorialGroupTwoInCourseOneData(User tutor, User student) {
        var tutorialGroup = tutorialGroupUtilService.createAndSaveTutorialGroup(exampleCourse.getId(), "TG Tue 13", "SampleInfo2", 20, true, null, Language.GERMAN.name(), tutor,
                Set.of(student));
        TutorialGroupSession session = tutorialGroupUtilService.createTutorialGroupSession(ZonedDateTime.of(SECOND_SEPTEMBER_MONDAY_10_00, ZoneId.of(exampleTimeZone)),
                ZonedDateTime.of(SECOND_SEPTEMBER_MONDAY_12_00, ZoneId.of(exampleTimeZone)), "01.05.13", null, TutorialGroupSessionStatus.ACTIVE, null, tutorialGroup);
        tutorialGroupSessionRepository.saveAndFlush(session);
        var channel = tutorialGroupChannelManagementService.createChannelForTutorialGroup(tutorialGroup);
        return new TutorialGroupTwoInCourseOneData(tutorialGroup, session, channel);
    }

    TutorialGroupOneInCourseTwoData createAndSaveTutorialGroupOneInCourseTwoData(User tutor) {
        TutorialGroup group = tutorialGroupUtilService.createAndSaveTutorialGroup(exampleCourse2.getId(), "TG Wed 10", "SampleInfo3", 15, false, "01.05.12",
                Language.ENGLISH.name(), tutor, Set.of());
        TutorialGroupSession session = tutorialGroupUtilService.createIndividualTutorialGroupSession(group.getId(),
                ZonedDateTime.of(SECOND_SEPTEMBER_MONDAY_10_00, ZoneId.of(exampleTimeZone)), ZonedDateTime.of(SECOND_SEPTEMBER_MONDAY_12_00, ZoneId.of(exampleTimeZone)), null);
        return new TutorialGroupOneInCourseTwoData(group, List.of(session));
    }

    TutorialGroupSession buildAndSaveExampleIndividualTutorialGroupSession(Long tutorialGroupId, LocalDateTime localDate) {
        return tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroupId, getExampleSessionStartOnDate(localDate.toLocalDate()),
                getExampleSessionEndOnDate(localDate.toLocalDate()), null);
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

    TutorialGroup buildTutorialGroupWithoutSchedule(String tutorLogin) {
        var course = courseRepository.findByIdElseThrow(exampleCourseId);
        var tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle(generateRandomTitle());
        tutorialGroup.setTeachingAssistant(userRepository.findOneByLogin(tutorLogin).orElseThrow());
        tutorialGroup.setCapacity(15);
        tutorialGroup.setCampus("Garching");
        return tutorialGroup;
    }

    TutorialGroup setUpTutorialGroupWithSchedule(Long courseId, String tutorLogin) throws Exception {
        var tutor = userRepository.findOneByLogin(testPrefix + tutorLogin).orElseThrow();
        var tutorialGroup = tutorialGroupUtilService.createAndSaveTutorialGroup(courseId, "TG Mo 13", "SampleInfo1", 15, false, "Garching", Language.ENGLISH.name(), tutor,
                Set.of());
        TutorialGroupSchedule schedule = tutorialGroupUtilService.createAndSaveTutorialGroupSchedule(tutorialGroup, 1, "10:00:00", "12:00:00", 1, FIRST_AUGUST_MONDAY.toString(),
                SECOND_AUGUST_MONDAY.toString(), "LoremIpsum");
        tutorialGroupUtilService.createAndSaveRegularSessionsFromTutorialGroupSchedule(exampleCourse, tutorialGroup, schedule);
        tutorialGroupChannelManagementService.createChannelForTutorialGroup(tutorialGroup);
        this.assertTutorialGroupPersistedWithSchedule(tutorialGroup, schedule);
        return tutorialGroup;
    }

    // === UTILS ===

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

    String getTutorialGroupsConfigurationPath(Long courseId) {
        return "/api/tutorialgroup/courses/" + courseId + "/tutorial-groups-configuration";
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

    void assertGroupDTOHasCorrectFields(TutorialGroupSessionDTO dto, TutorialGroupSession session) {
        assertThat(dto.start().toInstant()).isEqualTo(session.getStart().toInstant());
        assertThat(dto.end().toInstant()).isEqualTo(session.getEnd().toInstant());
        assertThat(dto.location()).isEqualTo(session.getLocation());
        assertThat(dto.attendanceCount()).isEqualTo(session.getAttendanceCount());
    }

    void assertGroupDTOHasCorrectFlags(TutorialGroupSessionDTO dto, boolean expectIsCancelled, boolean expectLocationChanged, boolean expectTimeChanged,
            boolean expectDateChanged) {
        assertThat(dto.isCancelled()).isEqualTo(expectIsCancelled);
        assertThat(dto.locationChanged()).isEqualTo(expectLocationChanged);
        assertThat(dto.timeChanged()).isEqualTo(expectTimeChanged);
        assertThat(dto.dateChanged()).isEqualTo(expectDateChanged);
    }
}
