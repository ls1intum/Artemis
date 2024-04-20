package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.tutorialgroups.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.client.HttpClientErrorException;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.web.rest.dto.TutorialGroupFreePeriodDTO;

class TutorialGroupFreePeriodIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    Long exampleTutorialGroupId;

    @BeforeEach
    void setupTestScenario() {
        super.setupTestScenario();
        userUtilService.addUsers(this.testPrefix, 1, 1, 1, 1);
        if (userRepository.findOneByLogin(testPrefix + "instructor42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "instructor42"));
        }
        this.exampleTutorialGroupId = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1",
                Language.ENGLISH.name(), userRepository.findOneByLogin(testPrefix + "tutor1").orElseThrow(), Collections.emptySet()).getId();
    }

    private static final String TEST_PREFIX = "tutorialgroupfreeperiod";

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void request_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void request_asTutor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void request_asStudent_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void request_asEditor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    void testJustForInstructorEndpoints() throws Exception {
        var freePeriod = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        request.get(getTutorialGroupFreePeriodsPath(freePeriod.getId()), HttpStatus.FORBIDDEN, TutorialGroupFreePeriod.class);
        request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday"),
                TutorialGroupFreePeriod.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath(freePeriod.getId()),
                createTutorialGroupFreePeriodDTO(SECOND_AUGUST_MONDAY_00_00, SECOND_AUGUST_MONDAY_23_59, "Another Holiday"), TutorialGroupFreePeriod.class, HttpStatus.FORBIDDEN);
        request.delete(getTutorialGroupFreePeriodsPath(freePeriod.getId()), HttpStatus.FORBIDDEN);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(freePeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOneOfConfiguration_asInstructor_shouldReturnTutorialGroupFreePeriod() throws Exception {
        // given
        var freePeriod = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        // when
        var freePeriodFromRequest = request.get(getTutorialGroupFreePeriodsPath(freePeriod.getId()), HttpStatus.OK, TutorialGroupFreePeriod.class);
        // then
        assertThat(freePeriodFromRequest).isEqualTo(freePeriod);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(freePeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupFreePeriod() throws Exception {
        // given
        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        // when
        var freePeriodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();
        // then
        var persistedFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(freePeriodId);
        this.assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(persistedFreePeriod, dto);
        this.assertTutorialGroupFreePeriodProperties(persistedFreePeriod);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(persistedFreePeriod.getId());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingScheduledSession_shouldCancelSession() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).orElseThrow();

        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_startDateIsAfterEndDate_shouldNotCreateFreePeriod() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).orElseThrow();

        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_23_59, FIRST_AUGUST_MONDAY_00_00, "Holiday");
        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, HttpClientErrorException.BadRequest.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(createdPeriod).isNull();
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsActiveOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_freePeriodEndTimeMatchesSessionStartTime_shouldNotCancelSession() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).orElseThrow();

        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_08_00, FIRST_AUGUST_MONDAY_10_00, "Holiday");
        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsActiveOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_freePeriodStartTimeMatchesSessionEndTime_shouldNotCancelSession() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).orElseThrow();

        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_12_00, FIRST_AUGUST_MONDAY_13_00, "Holiday");
        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsActiveOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_partialOverlapsWithExistingScheduledSession_shouldCancelSession_PeriodWithinDay() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).orElseThrow();

        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_10_00, FIRST_AUGUST_MONDAY_13_00, "Holiday");
        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_noOverlapsWithExistingScheduledSessionOnSameDay_shouldNotCancelSession_PeriodWithinDay() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).orElseThrow();

        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_13_00, FIRST_AUGUST_MONDAY_18_00, "Holiday");
        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsActiveOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingIndividualSession_shouldCancelSession() throws Exception {
        // given
        this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, FIRST_AUGUST_MONDAY_00_00);
        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");

        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);

        // then
        var sessions = this.getTutorialGroupSessionsAscending(exampleTutorialGroupId);
        var firstMondayOfAugustSession = sessions.get(0);
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, exampleTutorialGroupId, null);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithSessionAlreadyCancelledDueToFreePeriod_shouldNotUpdateTutorialFreePeriod() throws Exception {
        // given
        this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, FIRST_AUGUST_MONDAY_00_00);
        TutorialGroupFreePeriodDTO dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_10_00, FIRST_AUGUST_MONDAY_11_00, "Holiday");
        TutorialGroupFreePeriodDTO dto2 = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_11_00, FIRST_AUGUST_MONDAY_13_00, "Holiday");

        // when
        TutorialGroupFreePeriod createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        TutorialGroupFreePeriod createdPeriod2 = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto2, TutorialGroupFreePeriod.class, HttpStatus.CREATED);

        // then
        List<TutorialGroupSession> sessions = this.getTutorialGroupSessionsAscending(exampleTutorialGroupId);
        TutorialGroupSession firstMondayOfAugustSession = sessions.get(0);
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, exampleTutorialGroupId, null);
        assert (firstMondayOfAugustSession.getTutorialGroupFreePeriod().getId()).equals(createdPeriod.getId());
        assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(firstMondayOfAugustSession.getTutorialGroupFreePeriod(), dto);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void delete_cancelledSessionStillOverlapsWithAnotherFreePeriod_shouldNotActivateSession() throws Exception {
        // given
        TutorialGroupSession firstMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, FIRST_AUGUST_MONDAY_00_00);
        TutorialGroupFreePeriodDTO dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_10_00.atZone(ZoneId.of(exampleTimeZone)).toLocalDateTime(),
                FIRST_AUGUST_MONDAY_11_00.atZone(ZoneId.of(exampleTimeZone)).toLocalDateTime(), "Holiday");
        TutorialGroupFreePeriodDTO dto2 = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_11_00.atZone(ZoneId.of(exampleTimeZone)).toLocalDateTime(),
                FIRST_AUGUST_MONDAY_13_00.atZone(ZoneId.of(exampleTimeZone)).toLocalDateTime(), "Holiday");

        TutorialGroupFreePeriod createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        TutorialGroupFreePeriod createdPeriod2 = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto2, TutorialGroupFreePeriod.class, HttpStatus.CREATED);

        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, exampleTutorialGroupId, null);

        // when
        request.delete(getTutorialGroupFreePeriodsPath(createdPeriod.getId()), HttpStatus.NO_CONTENT);

        // then
        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, exampleTutorialGroupId, null);
        assert (firstMondayOfAugustSession.getTutorialGroupFreePeriod().getId()).equals(createdPeriod2.getId());

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        // given
        var freeDay = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        var numberOfFreePeriods = tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId).size();
        // when
        request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).hasSize(numberOfFreePeriods);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(freeDay.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_asInstructor_shouldActivatePreviouslyCancelledSessionsAndCancelNowOverlappingSessions() throws Exception {
        // given
        var groupId = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").orElseThrow(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").orElseThrow())).getId();

        // given
        var firstMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(groupId, FIRST_AUGUST_MONDAY_00_00);
        var secondMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(groupId, SECOND_AUGUST_MONDAY_00_00);

        var firstOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        var periodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), firstOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();

        firstMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        secondMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(secondMondayOfAugustSession.getId());
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, groupId, null);
        assertIndividualSessionIsActiveOnDate(secondMondayOfAugustSession, SECOND_AUGUST_MONDAY_00_00, groupId);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();
        assertThat(secondMondayOfAugustSession.getTutorialGroupFreePeriod()).isNull();

        var secondOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(SECOND_AUGUST_MONDAY_00_00, SECOND_AUGUST_MONDAY_23_59, "Another Holiday");
        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath(periodId), secondOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.OK);

        // then
        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        secondMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(secondMondayOfAugustSession.getId());
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNull();
        assertThat(secondMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();
        assertIndividualSessionIsActiveOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, groupId);
        assertIndividualSessionIsCancelledOnDate(secondMondayOfAugustSession, SECOND_AUGUST_MONDAY_00_00, groupId, null);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupSessionRepository.deleteById(secondMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(periodId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        // given
        var firstMondayOfAugustFreeDay = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59,
                "Holiday");
        var secondMondayOfAugustFreeDay = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, SECOND_AUGUST_MONDAY_00_00, SECOND_AUGUST_MONDAY_23_59,
                "Another Holiday");
        var dto = createTutorialGroupFreePeriodDTO(SECOND_AUGUST_MONDAY_00_00, SECOND_AUGUST_MONDAY_23_59, "Holiday");

        var numberOfFreePeriods = tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId).size();
        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath(firstMondayOfAugustFreeDay.getId()), dto, TutorialGroupFreePeriod.class, HttpStatus.BAD_REQUEST);

        // then
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).hasSize(numberOfFreePeriods);
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).contains(firstMondayOfAugustFreeDay,
                secondMondayOfAugustFreeDay);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(firstMondayOfAugustFreeDay.getId());
        tutorialGroupFreePeriodRepository.deleteById(secondMondayOfAugustFreeDay.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_justReasonChange_shouldUpdateFreePeriodReason() throws Exception {
        // given
        var firstMondayOfAugustFreeDay = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59,
                "Holiday");
        var dto = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Another Holiday");
        var numberOfFreePeriods = tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId).size();

        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath(firstMondayOfAugustFreeDay.getId()), dto, TutorialGroupFreePeriod.class, HttpStatus.OK);

        // then
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).hasSize(numberOfFreePeriods);
        var updatedFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(firstMondayOfAugustFreeDay.getId());
        assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(updatedFreePeriod, dto);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(firstMondayOfAugustFreeDay.getId());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void delete_asInstructor_shouldActivatePreviouslyCancelledSessionsOnThatDate() throws Exception {
        // given
        var firstMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, FIRST_AUGUST_MONDAY_00_00);

        var firstOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(FIRST_AUGUST_MONDAY_00_00, FIRST_AUGUST_MONDAY_23_59, "Holiday");
        var periodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), firstOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();

        firstMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());

        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, exampleTutorialGroupId, null);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();

        // when
        request.delete(getTutorialGroupFreePeriodsPath(periodId), HttpStatus.NO_CONTENT);

        // then
        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        assertIndividualSessionIsActiveOnDate(firstMondayOfAugustSession, FIRST_AUGUST_MONDAY_00_00, exampleTutorialGroupId);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNull();

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());

    }

    private TutorialGroupFreePeriodDTO createTutorialGroupFreePeriodDTO(LocalDateTime startDate, LocalDateTime endDate, String reason) {
        return new TutorialGroupFreePeriodDTO(startDate, endDate, reason);
    }

    private void assertTutorialGroupFreePeriodProperties(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        assertThat(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getId()).isEqualTo(exampleConfigurationId);
    }

    private void assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(TutorialGroupFreePeriod freePeriod, TutorialGroupFreePeriodDTO dto) {
        assertThat(freePeriod.getStart()).isEqualTo(ZonedDateTime.of(dto.startDate().toLocalDate(), dto.startDate().toLocalTime(), ZoneId.of(exampleTimeZone)));
        assertThat(freePeriod.getEnd()).isEqualTo(ZonedDateTime.of(dto.endDate().toLocalDate(), dto.endDate().toLocalTime(), ZoneId.of(exampleTimeZone)));
        assertThat(freePeriod.getReason()).isEqualTo(dto.reason());
    }

}
