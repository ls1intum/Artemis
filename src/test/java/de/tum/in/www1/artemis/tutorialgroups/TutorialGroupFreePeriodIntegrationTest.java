package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.END_OF_DAY;
import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.START_OF_DAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.google.common.collect.ImmutableSet;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreePeriodRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupFreePeriodResource;

public class TutorialGroupFreePeriodIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    DatabaseUtilService databaseUtilService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    @Autowired
    TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    Long exampleCourseId;

    Long exampleConfigurationId;

    Long exampleOneTutorialGroupId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        // creating the users student1-student10, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(10, 10, 10, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("editor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        var course = this.database.createCourse();
        exampleCourseId = course.getId();

        exampleConfigurationId = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, "Europe/Bucharest", LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1))
                .getId();

        exampleOneTutorialGroupId = databaseUtilService
                .createTutorialGroup(exampleCourseId, "ExampleTitle1", "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH, userRepository.findOneByLogin("tutor1").get(),
                        ImmutableSet.of(userRepository.findOneByLogin("student1").get(), userRepository.findOneByLogin("student2").get(),
                                userRepository.findOneByLogin("student3").get(), userRepository.findOneByLogin("student4").get(), userRepository.findOneByLogin("student5").get()))
                .getId();
    }

    private void testJustForInstructorEndpoints() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor42", roles = "INSTRUCTOR")
    void request_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void request_asTutor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void request_asStudent_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void request_asEditor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    ///

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void getOneOfConfiguration_asInstructor_shouldReturnTutorialGroupFreePeriod() throws Exception {
        var freePeriod = databaseUtilService.addTutorialGroupFreeDay(exampleOneTutorialGroupId, LocalDate.of(2022, 8, 1), "Holiday");
        var freePeriodFromRequest = request.get(
                "/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods/" + freePeriod.getId(), HttpStatus.OK,
                TutorialGroupFreePeriod.class);
        assertThat(freePeriodFromRequest).isEqualTo(freePeriod);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupFreePeriod() throws Exception {
        var dto = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 1), "Holiday");
        var freePeriodId = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods",
                dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();
        var persistedFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(freePeriodId);
        this.assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(persistedFreePeriod, dto);
        this.assertTutorialGroupFreePeriodProperties(persistedFreePeriod);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingScheduledSession_shouldCancelSession() throws Exception {
        addScheduleToTutorialGroup();
        var schedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(exampleOneTutorialGroupId).get();
        var dto = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 1), "Holiday");
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods", dto,
                TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdElseThrow(exampleOneTutorialGroupId);
        var sessions = getSessionAscending(tutorialGroup);
        var session = sessions.get(0);
        assertSessionStructure(sessions.get(0), schedule, tutorialGroup, getUTCZonedDate(2022, 8, 1, 9), getUTCZonedDate(2022, 8, 1, 10), TutorialGroupSessionStatus.CANCELLED,
                "Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingIndividualSession_shouldCancelSession() throws Exception {
        var sessionId = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("UTC"))).getId();
        var dto = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 1), "Holiday");
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods", dto,
                TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();
        var session = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdElseThrow(exampleOneTutorialGroupId);
        assertSessionStructure(session, null, tutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.CANCELLED, "Holiday");

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        databaseUtilService.addTutorialGroupFreeDay(exampleOneTutorialGroupId, LocalDate.of(2022, 8, 1), "Holiday");
        var dto = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 1), "Holiday");
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods", dto,
                TutorialGroupFreePeriod.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_asInstructor_shouldActivatePreviouslyCancelledSessionsAndCancelNowOverlappingSessions() throws Exception {
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdElseThrow(exampleOneTutorialGroupId);
        var sessionOneId = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("UTC"))).getId();
        var sessionTwoId = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 2, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 2, 11, 0, 0, 0, ZoneId.of("UTC"))).getId();

        var cancelFirstSessionDTO = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 1), "Holiday");
        var periodId = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods",
                cancelFirstSessionDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();
        var firstSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionOneId);
        var secondSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionTwoId);
        assertSessionStructure(firstSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.CANCELLED,
                "Holiday");
        assertSessionStructure(secondSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 2, 10), getUTCZonedDate(2022, 8, 2, 11), TutorialGroupSessionStatus.ACTIVE, null);

        var cancelSecondSessionDTO = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 2), "Another Holiday");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods/" + periodId,
                cancelSecondSessionDTO, TutorialGroupFreePeriod.class, HttpStatus.OK);
        firstSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionOneId);
        secondSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionTwoId);
        assertSessionStructure(firstSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(secondSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 2, 10), getUTCZonedDate(2022, 8, 2, 11), TutorialGroupSessionStatus.CANCELLED,
                "Another Holiday");

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        var periodId = databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, LocalDate.of(2022, 8, 1), "Holiday");
        databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, LocalDate.of(2022, 8, 2), "Another Holiday");

        var dto = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 2), "Holiday");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods/" + periodId, dto,
                TutorialGroupFreePeriod.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_justReasonChange_shouldUpdateFreePeriodReason() throws Exception {
        var periodId = databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, LocalDate.of(2022, 8, 1), "Holiday").getId();
        var dto = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 1), "Another Holiday   ");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods/" + periodId, dto,
                TutorialGroupFreePeriod.class, HttpStatus.OK);
        var freePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(periodId);
        this.assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(freePeriod, dto);
        this.assertTutorialGroupFreePeriodProperties(freePeriod);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void delete_asInstructor_shouldActivatePreviouslyCancelledSessionsOnThatDate() throws Exception {
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdElseThrow(exampleOneTutorialGroupId);

        var sessionOneId = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("UTC"))).getId();
        var sessionTwoId = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 12, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 13, 0, 0, 0, ZoneId.of("UTC"))).getId();
        var cancelSessionsDTO = createTutorialGroupFreePeriodDTO(LocalDate.of(2022, 8, 1), "Holiday");
        var periodId = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods",
                cancelSessionsDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();

        var firstSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionOneId);
        var secondSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionTwoId);
        assertSessionStructure(firstSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.CANCELLED,
                "Holiday");
        assertSessionStructure(secondSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 1, 12), getUTCZonedDate(2022, 8, 1, 13), TutorialGroupSessionStatus.CANCELLED,
                "Holiday");

        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + exampleConfigurationId + "/tutorial-free-periods/" + periodId,
                HttpStatus.NO_CONTENT);

        firstSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionOneId);
        secondSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionTwoId);
        assertSessionStructure(firstSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(secondSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 1, 12), getUTCZonedDate(2022, 8, 1, 13), TutorialGroupSessionStatus.ACTIVE, null);

    }

    private TutorialGroupFreePeriodResource.TutorialGroupFreePeriodDTO createTutorialGroupFreePeriodDTO(LocalDate date, String reason) {
        return new TutorialGroupFreePeriodResource.TutorialGroupFreePeriodDTO(date, reason);
    }

    private void assertTutorialGroupFreePeriodProperties(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        assertThat(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getId()).isEqualTo(exampleConfigurationId);
    }

    private void assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(TutorialGroupFreePeriod freePeriod, TutorialGroupFreePeriodResource.TutorialGroupFreePeriodDTO dto) {
        assertThat(freePeriod.getStart()).isEqualTo(ZonedDateTime.of(dto.date(), START_OF_DAY, ZoneId.of("Europe/Bucharest")));
        assertThat(freePeriod.getEnd()).isEqualTo(ZonedDateTime.of(dto.date(), END_OF_DAY, ZoneId.of("Europe/Bucharest")));
        assertThat(freePeriod.getReason()).isEqualTo(dto.reason());
    }

    private List<TutorialGroupSession> getSessionAscending(TutorialGroup tutorialGroup) {
        var sessions = new ArrayList<>(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId()).stream().toList());
        sessions.sort(Comparator.comparing(TutorialGroupSession::getStart));
        return sessions;
    }

    private TutorialGroup addScheduleToTutorialGroup() throws Exception {
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdElseThrow(exampleOneTutorialGroupId);
        tutorialGroup.setTutorialGroupSchedule(createSchedule(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 8), 1));
        return request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class, HttpStatus.OK);
    }

    private TutorialGroupSchedule createSchedule(LocalDate validFromInclusive, LocalDate validToInclusive, Integer weekday) {
        TutorialGroupSchedule newTutorialGroupSchedule = new TutorialGroupSchedule();
        newTutorialGroupSchedule.setDayOfWeek(weekday); // Monday
        newTutorialGroupSchedule.setStartTime(LocalTime.of(12, 0, 0).toString());
        newTutorialGroupSchedule.setEndTime(LocalTime.of(13, 0, 0).toString());
        // monday before dst
        newTutorialGroupSchedule.setValidFromInclusive(validFromInclusive.toString());
        // monday after dst
        newTutorialGroupSchedule.setValidToInclusive(validToInclusive.toString());
        newTutorialGroupSchedule.setLocation("LoremIpsum");
        // every week
        newTutorialGroupSchedule.setRepetitionFrequency(1);
        return newTutorialGroupSchedule;
    }

    private static void assertSessionStructure(TutorialGroupSession session, TutorialGroupSchedule schedule, TutorialGroup tutorialGroup, ZonedDateTime start, ZonedDateTime end,
            TutorialGroupSessionStatus status, String status_explanation) {
        assertThat(session.getStart()).isEqualTo(start);
        assertThat(session.getEnd()).isEqualTo(end);
        assertThat(session.getTutorialGroup().getId()).isEqualTo(tutorialGroup.getId());

        if (schedule != null) {
            assertThat(session.getLocation()).isEqualTo(schedule.getLocation());
            assertThat(session.getTutorialGroupSchedule().getId()).isEqualTo(schedule.getId());
        }
        else {
            assertThat(session.getTutorialGroupSchedule()).isNull();
        }
        assertThat(session.getStatus()).isEqualTo(status);
        assertThat(session.getStatusExplanation()).isEqualTo(status_explanation);
    }

    private static ZonedDateTime getUTCZonedDate(int year, int month, int dayOfMonth, int hour) {
        return ZonedDateTime.of(year, month, dayOfMonth, hour, 0, 0, 0, ZoneId.of("UTC"));
    }

}
