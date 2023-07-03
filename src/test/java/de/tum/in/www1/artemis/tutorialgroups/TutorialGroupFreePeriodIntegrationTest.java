package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.tutorialgroups.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.END_OF_DAY;
import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.START_OF_DAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupFreePeriodResource;

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
                Language.ENGLISH.name(), userRepository.findOneByLogin(testPrefix + "tutor1").get(), Collections.emptySet()).getId();
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
        var freePeriod = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        request.get(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), HttpStatus.FORBIDDEN, TutorialGroupFreePeriod.class);
        request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday"), TutorialGroupFreePeriod.class,
                HttpStatus.FORBIDDEN);
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), createTutorialGroupFreePeriodDTO(secondAugustMonday, "Another Holiday"),
                TutorialGroupFreePeriod.class, HttpStatus.FORBIDDEN);
        request.delete(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), HttpStatus.FORBIDDEN);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(freePeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOneOfConfiguration_asInstructor_shouldReturnTutorialGroupFreePeriod() throws Exception {
        // given
        var freePeriod = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        // when
        var freePeriodFromRequest = request.get(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), HttpStatus.OK, TutorialGroupFreePeriod.class);
        // then
        assertThat(freePeriodFromRequest).isEqualTo(freePeriod);

        // cleanup
        tutorialGroupFreePeriodRepository.deleteById(freePeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupFreePeriod() throws Exception {
        // given
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
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
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).get();

        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingIndividualSession_shouldCancelSession() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");

        // when
        var createdPeriod = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);

        // then
        var sessions = this.getTutorialGroupSessionsAscending(exampleTutorialGroupId);
        var firstMondayOfAugustSession = sessions.get(0);
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleTutorialGroupId, null);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(createdPeriod.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        // given
        var freeDay = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
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
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").get())).getId();

        // given
        var firstMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(groupId, firstAugustMonday);
        var secondMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(groupId, secondAugustMonday);

        var firstOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        var periodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), firstOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();

        firstMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        secondMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(secondMondayOfAugustSession.getId());
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, groupId, null);
        assertIndividualSessionIsActiveOnDate(secondMondayOfAugustSession, secondAugustMonday, groupId);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();
        assertThat(secondMondayOfAugustSession.getTutorialGroupFreePeriod()).isNull();

        var secondOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(secondAugustMonday, "Another Holiday");
        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + periodId, secondOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.OK);

        // then
        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        secondMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(secondMondayOfAugustSession.getId());
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNull();
        assertThat(secondMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();
        assertIndividualSessionIsActiveOnDate(firstMondayOfAugustSession, firstAugustMonday, groupId);
        assertIndividualSessionIsCancelledOnDate(secondMondayOfAugustSession, secondAugustMonday, groupId, null);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());
        tutorialGroupSessionRepository.deleteById(secondMondayOfAugustSession.getId());
        tutorialGroupFreePeriodRepository.deleteById(periodId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        // given
        var firstMondayOfAugustFreeDay = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var secondMondayOfAugustFreeDay = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, secondAugustMonday, "Another Holiday");
        var dto = createTutorialGroupFreePeriodDTO(secondAugustMonday, "Holiday");

        var numberOfFreePeriods = tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId).size();
        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + firstMondayOfAugustFreeDay.getId(), dto, TutorialGroupFreePeriod.class, HttpStatus.BAD_REQUEST);

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
        var firstMondayOfAugustFreeDay = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Another Holiday");
        var numberOfFreePeriods = tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId).size();

        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + firstMondayOfAugustFreeDay.getId(), dto, TutorialGroupFreePeriod.class, HttpStatus.OK);

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
        var firstMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);

        var firstOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        var periodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), firstOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();

        firstMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());

        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleTutorialGroupId, null);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNotNull();

        // when
        request.delete(getTutorialGroupFreePeriodsPath() + periodId, HttpStatus.NO_CONTENT);

        // then
        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        assertIndividualSessionIsActiveOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleTutorialGroupId);
        assertThat(firstMondayOfAugustSession.getTutorialGroupFreePeriod()).isNull();

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstMondayOfAugustSession.getId());

    }

    private TutorialGroupFreePeriodResource.TutorialGroupFreePeriodDTO createTutorialGroupFreePeriodDTO(LocalDate date, String reason) {
        return new TutorialGroupFreePeriodResource.TutorialGroupFreePeriodDTO(date, reason);
    }

    private void assertTutorialGroupFreePeriodProperties(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        assertThat(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getId()).isEqualTo(exampleConfigurationId);
    }

    private void assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(TutorialGroupFreePeriod freePeriod, TutorialGroupFreePeriodResource.TutorialGroupFreePeriodDTO dto) {
        assertThat(freePeriod.getStart()).isEqualTo(ZonedDateTime.of(dto.date(), START_OF_DAY, ZoneId.of(exampleTimeZone)));
        assertThat(freePeriod.getEnd()).isEqualTo(ZonedDateTime.of(dto.date(), END_OF_DAY, ZoneId.of(exampleTimeZone)));
        assertThat(freePeriod.getReason()).isEqualTo(dto.reason());
    }

}
