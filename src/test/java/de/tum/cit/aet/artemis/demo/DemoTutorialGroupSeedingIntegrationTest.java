package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.communication.api.CommunicationDemoApi;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseAvailableTabsService;
import de.tum.cit.aet.artemis.demo.service.DemoCourseContentSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSummaryDTO;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;

/**
 * Tests the tutorial groups that the {@code demo} profile seeds into the demo course.
 * <p>
 * Like {@link DemoDataSeedingIntegrationTest}, every test seeds the whole demo course and must stay correct whatever ran before it, because the demo course persists in the
 * shared test database.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoTutorialGroupSeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // The titles and the reason identify the demo tutorial groups and the free period on every startup, so they are spelled out here.
    private static final String MONDAY_GROUP = "Monday Tutorial";

    private static final String THURSDAY_GROUP = "Thursday Online";

    private static final String FREE_PERIOD_REASON = "Reading Week";

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private AccountDemoApi accountDemoApi;

    @Autowired
    private CourseDemoApi courseDemoApi;

    @Autowired
    private DemoExerciseSeedingService demoExerciseSeedingService;

    @Autowired
    private AssessmentDemoApi assessmentDemoApi;

    @Autowired
    private CommunicationDemoApi communicationDemoApi;

    @Autowired
    private TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    private TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    private TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private CourseAvailableTabsService courseAvailableTabsService;

    @Test
    void configuresTutorialGroupsOverTheWholeCourse() {
        seed();

        Course course = demoCourse();
        ZoneId timeZone = ZoneId.of(course.getTimeZone());
        TutorialGroupsConfiguration configuration = configuration(course);
        assertThat(configuration.getTutorialPeriodStartInclusive()).as("the tutorial period starts with the course, in the time zone of the course")
                .isEqualTo(course.getStartDate().withZoneSameInstant(timeZone).toLocalDate().toString());
        assertThat(configuration.getTutorialPeriodEndInclusive()).as("the tutorial period ends with the course, in the time zone of the course")
                .isEqualTo(course.getEndDate().withZoneSameInstant(timeZone).toLocalDate().toString());
        assertThat(configuration.getUseTutorialGroupChannels()).as("every tutorial group has a channel").isTrue();
        assertThat(configuration.getUsePublicTutorialGroupChannels()).as("the channels of the tutorial groups are public").isTrue();
    }

    @Test
    void seedsWeeklyTutorialGroupsLedByTheDemoTutor() {
        seed();

        Course course = demoCourse();
        TutorialGroupsConfiguration configuration = configuration(course);
        TutorialGroup monday = demoGroup(course, MONDAY_GROUP);
        TutorialGroup thursday = demoGroup(course, THURSDAY_GROUP);
        assertThat(List.of(monday, thursday)).allSatisfy(group -> {
            assertThat(group.getTeachingAssistant().getLogin()).as("the demo tutor leads %s", group.getTitle()).isEqualTo(AccountDemoApi.DEMO_TUTOR_LOGIN);
            assertThat(group.getLanguage()).isEqualTo("English");
            assertThat(group.getCapacity()).isEqualTo(15);
            assertThat(group.getAdditionalInformation()).as("%s says what students practise in it", group.getTitle()).contains("software architecture", "algorithms", "modeling");
        });
        assertThat(monday.getIsOnline()).as("the Monday tutorial meets on campus").isFalse();
        assertThat(monday.getCampus()).isNotBlank();
        assertThat(thursday.getIsOnline()).as("the Thursday tutorial meets online").isTrue();

        assertMeetsWeekly(monday, configuration, DayOfWeek.MONDAY, LocalTime.of(10, 0), "Room 01.07.014");
        assertMeetsWeekly(thursday, configuration, DayOfWeek.THURSDAY, LocalTime.of(16, 0), "Online via video call");
        assertThat(registeredLogins(monday)).as("the demo student and four classmates meet on campus").containsExactlyInAnyOrderElementsOf(studentLogins().subList(0, 5));
        assertThat(registeredLogins(thursday)).as("the remaining six classmates meet online").containsExactlyInAnyOrderElementsOf(studentLogins().subList(5, 11));
        assertChannelOfGroup(monday);
        assertChannelOfGroup(thursday);
    }

    @Test
    void readingWeekCancelsTheSessionsOfItsWeek() {
        seed();

        Course course = demoCourse();
        ZoneId timeZone = ZoneId.of(course.getTimeZone());
        TutorialGroupFreePeriod readingWeek = readingWeek(course);
        ZonedDateTime start = readingWeek.getStart().withZoneSameInstant(timeZone);
        ZonedDateTime end = readingWeek.getEnd().withZoneSameInstant(timeZone);
        assertThat(start.getDayOfWeek()).as("the reading week starts on a Monday").isEqualTo(DayOfWeek.MONDAY);
        assertThat(start.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(end.toLocalDate()).as("the reading week ends on the Sunday of the same week").isEqualTo(start.toLocalDate().plusDays(6));
        assertThat(end.toLocalTime()).isEqualTo(LocalTime.of(23, 59));

        assertCancelledByReadingWeek(demoGroup(course, MONDAY_GROUP), readingWeek);
        assertCancelledByReadingWeek(demoGroup(course, THURSDAY_GROUP), readingWeek);
    }

    @Test
    @WithMockUser(username = AccountDemoApi.DEMO_STUDENT_LOGIN, roles = "USER")
    void demoStudentFindsTheirTutorialGroup() throws Exception {
        seed();

        Course course = demoCourse();
        assertThat(courseAvailableTabsService.getAvailableTabs(course, userTestRepository.findOneByLogin(AccountDemoApi.DEMO_STUDENT_LOGIN).orElseThrow()).tutorialGroups())
                .as("the course shows its tutorial groups").isTrue();

        Map<String, TutorialGroupSummaryDTO> groups = request
                .getList("/api/tutorialgroup/courses/" + course.getId() + "/tutorial-groups", HttpStatus.OK, TutorialGroupSummaryDTO.class).stream()
                .collect(Collectors.toMap(TutorialGroupSummaryDTO::title, Function.identity()));
        assertThat(groups.get(MONDAY_GROUP).isUserRegistered()).as("the demo student is registered for the Monday tutorial").isTrue();
        assertThat(groups.get(THURSDAY_GROUP).isUserRegistered()).as("the demo student is not registered for the Thursday tutorial").isNotEqualTo(true);
        assertThat(groups.get(MONDAY_GROUP).averageAttendance()).as("four of the five students attended the last Monday tutorial").isEqualTo(4);
        assertThat(groups.get(THURSDAY_GROUP).averageAttendance()).as("five of the six students attended the last Thursday tutorial").isEqualTo(5);
        assertThat(List.of(groups.get(MONDAY_GROUP), groups.get(THURSDAY_GROUP))).allSatisfy(group -> {
            assertThat(group.nextSession()).as("%s has an upcoming session", group.title()).isNotNull();
            assertThat(group.nextSession().status()).isEqualTo(TutorialGroupSessionStatus.ACTIVE);
            assertThat(group.nextSession().start()).isAfter(ZonedDateTime.now().minusHours(3));
        });
    }

    @Test
    void seedingTwiceCreatesNothingNew() {
        seed();
        TutorialGroupData afterFirstRun = snapshot();

        seed();

        assertThat(snapshot()).as("seeding again neither creates nor replaces the configuration, tutorial groups, sessions, registrations, channels or free periods")
                .isEqualTo(afterFirstRun);
    }

    @Test
    @WithMockUser(username = AccountDemoApi.DEMO_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void recreatesDeletedTutorialGroup() throws Exception {
        seed();
        TutorialGroupData seeded = snapshot();
        GroupData deleted = seeded.groups().get(MONDAY_GROUP);
        request.delete("/api/tutorialgroup/courses/" + demoCourse().getId() + "/tutorial-groups/" + deleted.groupId(), HttpStatus.NO_CONTENT);

        seed();

        TutorialGroupData reseeded = snapshot();
        GroupData recreated = reseeded.groups().get(MONDAY_GROUP);
        assertThat(recreated.groupId()).as("the deleted tutorial group is created again").isNotEqualTo(deleted.groupId());
        assertThat(recreated.sessionIds()).as("with the sessions of its schedule").hasSameSizeAs(deleted.sessionIds()).doesNotContainAnyElementsOf(deleted.sessionIds());
        assertThat(recreated.registrationIds()).as("with its students").hasSameSizeAs(deleted.registrationIds());
        assertThat(recreated.channelId()).as("with a channel of its own").isNotEqualTo(deleted.channelId());
        assertThat(reseeded.groups().get(THURSDAY_GROUP)).as("the other tutorial group is left alone").isEqualTo(seeded.groups().get(THURSDAY_GROUP));
        assertThat(reseeded.configurationId()).isEqualTo(seeded.configurationId());
        assertThat(reseeded.freePeriodIds()).as("the reading week is left alone").isEqualTo(seeded.freePeriodIds());

        Course course = demoCourse();
        TutorialGroup monday = demoGroup(course, MONDAY_GROUP);
        assertMeetsWeekly(monday, configuration(course), DayOfWeek.MONDAY, LocalTime.of(10, 0), "Room 01.07.014");
        assertCancelledByReadingWeek(monday, readingWeek(course));
        assertChannelOfGroup(monday);
    }

    @Test
    void seedsWithoutTutorialGroupModule() {
        seed();
        TutorialGroupData beforeRun = snapshot();

        DemoDataSeedingService withoutTutorialGroups = new DemoDataSeedingService(accountDemoApi, courseDemoApi, demoExerciseSeedingService,
                new DemoCourseContentSeedingService(Optional.empty(), Optional.empty(), assessmentDemoApi, Optional.empty(), communicationDemoApi));
        DemoSeeding.seed(withoutTutorialGroups);

        assertThat(snapshot()).as("a disabled tutorial group module leaves the existing tutorial groups alone").isEqualTo(beforeRun);
    }

    private void seed() {
        DemoSeeding.seed(demoDataSeedingService);
    }

    private Course demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).getFirst();
    }

    private TutorialGroupsConfiguration configuration(Course course) {
        return tutorialGroupsConfigurationRepository.findByCourseId(course.getId()).orElseThrow();
    }

    /**
     * The tutorial group with the given title, with its tutor, registrations, schedule, sessions and channel.
     */
    private TutorialGroup demoGroup(Course course, String title) {
        List<TutorialGroup> groups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantRegistrationsAndSchedule(course.getId()).stream()
                .filter(group -> title.equals(group.getTitle())).toList();
        assertThat(groups).as("the demo course has exactly one tutorial group '%s'", title).hasSize(1);
        return groups.getFirst();
    }

    private TutorialGroupFreePeriod readingWeek(Course course) {
        List<TutorialGroupFreePeriod> freePeriods = tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(course.getId()).stream()
                .filter(freePeriod -> FREE_PERIOD_REASON.equals(freePeriod.getReason())).toList();
        assertThat(freePeriods).as("the demo course has exactly one reading week").hasSize(1);
        return freePeriods.getFirst();
    }

    private static List<String> studentLogins() {
        return Stream.concat(Stream.of(AccountDemoApi.DEMO_STUDENT_LOGIN), IntStream.rangeClosed(1, 10).mapToObj(number -> AccountDemoApi.DEMO_PEER_LOGIN_PREFIX + number))
                .toList();
    }

    private static List<String> registeredLogins(TutorialGroup group) {
        return group.getRegistrations().stream().map(registration -> registration.getStudent().getLogin()).toList();
    }

    /**
     * Asserts that the group meets for two hours from the given time on the given day of every week of the tutorial period in the time zone of the course, and that its tutor
     * recorded the attendance of one session that has taken place.
     */
    private static void assertMeetsWeekly(TutorialGroup group, TutorialGroupsConfiguration configuration, DayOfWeek day, LocalTime start, String location) {
        LocalTime end = start.plusHours(2);
        TutorialGroupSchedule schedule = group.getTutorialGroupSchedule();
        assertThat(schedule.getDayOfWeek()).as("%s meets on %s", group.getTitle(), day).isEqualTo(day.getValue());
        assertThat(LocalTime.parse(schedule.getStartTime())).isEqualTo(start);
        assertThat(LocalTime.parse(schedule.getEndTime())).isEqualTo(end);
        assertThat(schedule.getRepetitionFrequency()).as("%s meets every week", group.getTitle()).isOne();
        assertThat(schedule.getLocation()).isEqualTo(location);
        assertThat(schedule.getValidToInclusive()).as("%s meets until the tutorial period ends", group.getTitle()).isEqualTo(configuration.getTutorialPeriodEndInclusive());

        ZoneId timeZone = ZoneId.of(group.getCourse().getTimeZone());
        LocalDate firstDay = LocalDate.parse(configuration.getTutorialPeriodStartInclusive()).with(TemporalAdjusters.nextOrSame(day));
        List<LocalDate> everyWeek = firstDay.datesUntil(LocalDate.parse(configuration.getTutorialPeriodEndInclusive()).plusDays(1), Period.ofWeeks(1)).toList();
        List<TutorialGroupSession> sessions = group.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart)).toList();
        assertThat(sessions).extracting(session -> session.getStart().withZoneSameInstant(timeZone).toLocalDate())
                .as("%s has a session on every %s of the tutorial period", group.getTitle(), day).containsExactlyElementsOf(everyWeek);
        assertThat(sessions).as("the sessions of %s follow the schedule in the time zone of the course", group.getTitle()).allSatisfy(session -> {
            assertThat(session.getStart().withZoneSameInstant(timeZone).toLocalTime()).isEqualTo(start);
            assertThat(session.getEnd().withZoneSameInstant(timeZone).toLocalTime()).isEqualTo(end);
            assertThat(session.getLocation()).isEqualTo(location);
        });
        assertThat(sessions).filteredOn(session -> session.getAttendanceCount() != null).as("the tutor of %s recorded the attendance of a session", group.getTitle())
                .singleElement().satisfies(session -> {
                    assertThat(session.getEnd()).as("the session has taken place").isBefore(ZonedDateTime.now());
                    assertThat(session.getAttendanceCount()).as("all but one student attended").isEqualTo(group.getRegistrations().size() - 1);
                    assertThat(session.getTutorialGroupSchedule()).as("like every edited session, it no longer follows the schedule").isNull();
                });
    }

    private static void assertCancelledByReadingWeek(TutorialGroup group, TutorialGroupFreePeriod readingWeek) {
        Map<Boolean, List<TutorialGroupSession>> sessionsByOverlap = group.getTutorialGroupSessions().stream()
                .collect(Collectors.partitioningBy(session -> session.getStart().isBefore(readingWeek.getEnd()) && session.getEnd().isAfter(readingWeek.getStart())));
        assertThat(sessionsByOverlap.get(true)).as("the reading week cancels the session of %s in that week", group.getTitle()).singleElement().satisfies(session -> {
            assertThat(session.getStatus()).isEqualTo(TutorialGroupSessionStatus.CANCELLED);
            assertThat(session.getTutorialGroupFreePeriod().getId()).as("the cancelled session names the reading week as its reason").isEqualTo(readingWeek.getId());
        });
        assertThat(sessionsByOverlap.get(false)).as("the other sessions of %s take place", group.getTitle())
                .allSatisfy(session -> assertThat(session.getStatus()).isEqualTo(TutorialGroupSessionStatus.ACTIVE));
    }

    private void assertChannelOfGroup(TutorialGroup group) {
        assertThat(group.getTutorialGroupChannel()).as("%s has a channel", group.getTitle()).isNotNull();
        assertThat(group.getTutorialGroupChannel().getIsPublic()).as("the channel of %s is public", group.getTitle()).isTrue();
        Set<ConversationParticipant> members = conversationParticipantRepository.findConversationParticipantsByConversationId(group.getTutorialGroupChannel().getId());
        assertThat(members).extracting(member -> member.getUser().getLogin()).as("the students of %s and their tutor are members of its channel", group.getTitle())
                .containsExactlyInAnyOrderElementsOf(Stream.concat(registeredLogins(group).stream(), Stream.of(AccountDemoApi.DEMO_TUTOR_LOGIN)).toList());
        assertThat(members).filteredOn(ConversationParticipant::getIsModerator).extracting(member -> member.getUser().getLogin())
                .as("the tutor moderates the channel of %s", group.getTitle()).containsExactly(AccountDemoApi.DEMO_TUTOR_LOGIN);
    }

    /**
     * Captures the identities of the tutorial group data of the demo course, so that the idempotency assertions detect replaced records as well as new ones.
     */
    private TutorialGroupData snapshot() {
        Course course = demoCourse();
        Map<String, GroupData> groups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantRegistrationsAndSchedule(course.getId()).stream()
                .collect(Collectors.toMap(TutorialGroup::getTitle, group -> new GroupData(group.getId(), group.getTutorialGroupSchedule().getId(),
                        ids(group.getTutorialGroupSessions()), ids(group.getRegistrations()), group.getTutorialGroupChannel().getId())));
        return new TutorialGroupData(configuration(course).getId(), ids(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(course.getId())), groups);
    }

    private static Set<Long> ids(Collection<? extends DomainObject> entities) {
        return entities.stream().map(DomainObject::getId).collect(Collectors.toSet());
    }

    private record GroupData(long groupId, long scheduleId, Set<Long> sessionIds, Set<Long> registrationIds, long channelId) {
    }

    private record TutorialGroupData(long configurationId, Set<Long> freePeriodIds, Map<String, GroupData> groups) {
    }
}
