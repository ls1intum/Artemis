package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupSessionResource;

public class TutorialGroupSessionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    void getOneOfCourse_asInstructor_shouldReturnTutorialGroupSession() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("UTC")));
        var sessionFromRequest = request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(), HttpStatus.OK,
                TutorialGroupSession.class);
        assertThat(sessionFromRequest).isEqualTo(session);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void createNewSession_asInstructor_shouldCreateSession() throws Exception {
        var dto = createSessionDTO(LocalDate.of(2022, 8, 1), LocalTime.of(10, 0), LocalTime.of(12, 0), "Room 303");
        var sessionId = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions", dto,
                TutorialGroupSession.class, HttpStatus.CREATED).getId();
        var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
        assertSessionProperties(persistedSession, "Room 303", TutorialGroupSessionStatus.ACTIVE, null);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void createNewSession_invalidSessionAsStartAfterEnd_shouldReturnBadRequest() throws Exception {
        var dto = createSessionDTO(LocalDate.of(2022, 8, 1), LocalTime.of(12, 0), LocalTime.of(10, 0), "Room 303");
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions", dto, TutorialGroupSession.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void createNewSession_overlapsWithExistingSession_shouldReturnBadRequest() throws Exception {
        databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));
        var dto = createSessionDTO(LocalDate.of(2022, 8, 1), LocalTime.of(9, 0), LocalTime.of(12, 0), "Room 303");
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions", dto, TutorialGroupSession.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void createNewSession_onTutorialGroupFreeDay_shouldCreateAsCancelled() throws Exception {
        databaseUtilService.createTutorialGroupFreeDay(exampleOneTutorialGroupId, LocalDate.of(2022, 8, 1), "Holiday");
        var dto = createSessionDTO(LocalDate.of(2022, 8, 1), LocalTime.of(10, 0), LocalTime.of(12, 0), "Room 303");
        var sessionId = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions", dto,
                TutorialGroupSession.class, HttpStatus.CREATED).getId();
        var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
        assertSessionProperties(persistedSession, "Room 303", TutorialGroupSessionStatus.CANCELLED, "Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_scheduledSession_shouldBeDisconnectedFromSchedule() throws Exception {
        addScheduleToTutorialGroup();
        var scheduleId = tutorialGroupScheduleRepository.findByTutorialGroup_Id(exampleOneTutorialGroupId).get().getId();
        var scheduledSessions = tutorialGroupSessionRepository.findAllByScheduleId(scheduleId);
        var session = scheduledSessions.stream().findAny().get();
        ;
        assertThat(session.getTutorialGroupSchedule()).isNotNull();
        var dto = createSessionDTO(LocalDate.of(2022, 10, 1), LocalTime.of(2, 0), LocalTime.of(2, 0), "Zoom");
        var updatedSessionId = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(),
                dto, TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(session.getId());
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertSessionProperties(updatedSession, "Zoom", TutorialGroupSessionStatus.ACTIVE, null);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_individualSession_shouldStillBeDisconnectedFromSchedule() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));

        assertThat(session.getTutorialGroupSchedule()).isNull();
        var dto = createSessionDTO(LocalDate.of(2022, 10, 1), LocalTime.of(2, 0), LocalTime.of(2, 0), "Zoom");
        var updatedSessionId = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(),
                dto, TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(session.getId());
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertSessionProperties(updatedSession, "Zoom", TutorialGroupSessionStatus.ACTIVE, null);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOverlapsWithOtherSession_shouldReturnBadRequest() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));
        databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 2, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 2, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));

        var dto = createSessionDTO(LocalDate.of(2022, 8, 2), LocalTime.of(9, 0), LocalTime.of(12, 0), "Zoom");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(), dto,
                TutorialGroupSession.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOverlapsWithPreviousTime_shouldUpdateCorrectly() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));

        var dto = createSessionDTO(LocalDate.of(2022, 8, 1), LocalTime.of(5, 0), LocalTime.of(20, 0), "Zoom");
        var updatedSessionId = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(),
                dto, TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(session.getId());
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertSessionProperties(updatedSession, "Zoom", TutorialGroupSessionStatus.ACTIVE, null);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_wasCancelled_shouldNowBeActiveAgaib() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));
        session.setStatusExplanation("Cancelled");
        session.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(session);

        var dto = createSessionDTO(LocalDate.of(2022, 8, 10), LocalTime.of(5, 0), LocalTime.of(20, 0), "Zoom");
        var updatedSessionId = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(),
                dto, TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(session.getId());
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertSessionProperties(updatedSession, "Zoom", TutorialGroupSessionStatus.ACTIVE, null);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOnTutorialGroupFreeDay_shouldUpdateAsCancelled() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));
        databaseUtilService.createTutorialGroupFreeDay(exampleOneTutorialGroupId, LocalDate.of(2022, 8, 10), "Holiday");
        var dto = createSessionDTO(LocalDate.of(2022, 8, 10), LocalTime.of(5, 0), LocalTime.of(20, 0), "Zoom");
        var updatedSessionId = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(),
                dto, TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(session.getId());
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertSessionProperties(updatedSession, "Zoom", TutorialGroupSessionStatus.CANCELLED, "Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void deleteSession_individualSession_shouldBeDeleted() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));
        assertThat(tutorialGroupSessionRepository.existsById(session.getId())).isTrue();
        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(), HttpStatus.NO_CONTENT);
        assertThat(tutorialGroupSessionRepository.existsById(session.getId())).isFalse();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void deleteSession_scheduledSession_shouldBeDeleted() throws Exception {
        addScheduleToTutorialGroup();
        var scheduleId = tutorialGroupScheduleRepository.findByTutorialGroup_Id(exampleOneTutorialGroupId).get().getId();
        var scheduledSessions = tutorialGroupSessionRepository.findAllByScheduleId(scheduleId);
        var session = scheduledSessions.stream().findAny().get();
        ;
        assertThat(session.getTutorialGroupSchedule()).isNotNull();
        assertThat(tutorialGroupSessionRepository.existsById(session.getId())).isTrue();
        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId(), HttpStatus.NO_CONTENT);
        assertThat(tutorialGroupSessionRepository.existsById(session.getId())).isFalse();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void cancelSession_asInstructor_shouldCancelSession() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));
        assertThat(session.getStatus()).isEqualTo(TutorialGroupSessionStatus.ACTIVE);
        assertThat(session.getStatusExplanation()).isNull();
        var statusDTO = new TutorialGroupSessionResource.TutorialGroupStatusDTO("Holiday");
        request.postWithoutLocation("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId() + "/cancel", statusDTO,
                HttpStatus.OK, null);
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
        assertThat(updatedSession.getStatus()).isEqualTo(TutorialGroupSessionStatus.CANCELLED);
        assertThat(updatedSession.getStatusExplanation()).isEqualTo("Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void activateCancelledSession_asInstructor_shouldActivateSession() throws Exception {
        var session = databaseUtilService.createIndividualTutorialGroupSession(exampleOneTutorialGroupId, ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("Europe/Bucharest")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("Europe/Bucharest")));
        session.setStatusExplanation("Cancelled");
        session.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(session);
        request.postWithoutLocation("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId + "/sessions/" + session.getId() + "/activate", null,
                HttpStatus.OK, null);
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
        assertThat(updatedSession.getStatus()).isEqualTo(TutorialGroupSessionStatus.ACTIVE);
        assertThat(updatedSession.getStatusExplanation()).isNull();
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

    private TutorialGroupSessionResource.TutorialGroupSessionDTO createSessionDTO(LocalDate date, LocalTime startTime, LocalTime endTime, String location) {
        return new TutorialGroupSessionResource.TutorialGroupSessionDTO(date, startTime, endTime, location);
    }

    private void assertSessionProperties(TutorialGroupSession session, String location, TutorialGroupSessionStatus status, String statusExplanation) {
        assertThat(session.getLocation()).isEqualTo(location);
        assertThat(session.getStatus()).isEqualTo(status);
        assertThat(session.getTutorialGroup().getId()).isEqualTo(exampleOneTutorialGroupId);
        assertThat(session.getStatusExplanation()).isEqualTo(statusExplanation);
    }

    private static void assertSessionCreatedCorrectlyFromDTO(TutorialGroupSession session, TutorialGroupSessionResource.TutorialGroupSessionDTO dto) {
        assertThat(session.getStart()).isEqualTo(ZonedDateTime.of(dto.date(), dto.startTime(), ZoneId.of("Europe/Bucharest")));
        assertThat(session.getEnd()).isEqualTo(ZonedDateTime.of(dto.date(), dto.endTime(), ZoneId.of("Europe/Bucharest")));
        assertThat(session.getLocation()).isEqualTo(dto.location());
        assertThat(session.getTutorialGroupSchedule()).isNull(); // individual session not connected to schedule
    }

}
