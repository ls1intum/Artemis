package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.END_OF_DAY;
import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.START_OF_DAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupFreePeriodResource;

public class TutorialGroupFreePeriodIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    @Override
    void testJustForInstructorEndpoints() throws Exception {
        var freePeriod = databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        request.get(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), HttpStatus.FORBIDDEN, TutorialGroupFreePeriod.class);
        request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday"), TutorialGroupFreePeriod.class,
                HttpStatus.FORBIDDEN);
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), createTutorialGroupFreePeriodDTO(secondAugustMonday, "Another Holiday"),
                TutorialGroupFreePeriod.class, HttpStatus.FORBIDDEN);
        request.delete(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void getOneOfConfiguration_asInstructor_shouldReturnTutorialGroupFreePeriod() throws Exception {
        // given
        var freePeriod = databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        // when
        var freePeriodFromRequest = request.get(getTutorialGroupFreePeriodsPath() + freePeriod.getId(), HttpStatus.OK, TutorialGroupFreePeriod.class);
        // then
        assertThat(freePeriodFromRequest).isEqualTo(freePeriod);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupFreePeriod() throws Exception {
        // given
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        // when
        var freePeriodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();
        // then
        var persistedFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(freePeriodId);
        this.assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(persistedFreePeriod, dto);
        this.assertTutorialGroupFreePeriodProperties(persistedFreePeriod);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingScheduledSession_shouldCancelSession() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule();
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroup.getId()).get();

        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        // when
        request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        var firstMondayOfAugustSession = sessions.get(0);
        assertScheduledSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, tutorialGroup.getId(), persistedSchedule);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingIndividualSession_shouldCancelSession() throws Exception {
        // given
        this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");

        // when
        request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.CREATED);

        // then
        var sessions = this.getTutorialGroupSessionsAscending(exampleOneTutorialGroupId);
        var firstMondayOfAugustSession = sessions.get(0);
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleOneTutorialGroupId, "Holiday");

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        // given
        databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        // when
        request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), dto, TutorialGroupFreePeriod.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleConfigurationId)).hasSize(1);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_asInstructor_shouldActivatePreviouslyCancelledSessionsAndCancelNowOverlappingSessions() throws Exception {
        // given
        var firstMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        var secondMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, secondAugustMonday);

        var firstOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        var periodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), firstOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();

        firstMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        secondMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(secondMondayOfAugustSession.getId());
        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleOneTutorialGroupId, "Holiday");
        assertIndividualSessionIsActiveOnDate(secondMondayOfAugustSession, secondAugustMonday, exampleOneTutorialGroupId);

        var secondOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(secondAugustMonday, "Another Holiday");
        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + periodId, secondOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.OK);

        // then
        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        secondMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(secondMondayOfAugustSession.getId());
        assertIndividualSessionIsActiveOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleOneTutorialGroupId);
        assertIndividualSessionIsCancelledOnDate(secondMondayOfAugustSession, secondAugustMonday, exampleOneTutorialGroupId, "Another Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_overlapsWithExistingFreePeriod_shouldReturnBadRequest() throws Exception {
        // given
        var firstMondayOfAugustFreeDay = databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var secondMondayOfAugustFreeDay = databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, secondAugustMonday, "Another Holiday");
        var dto = createTutorialGroupFreePeriodDTO(secondAugustMonday, "Holiday");

        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + firstMondayOfAugustFreeDay.getId(), dto, TutorialGroupFreePeriod.class, HttpStatus.BAD_REQUEST);

        // then
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).hasSize(2);
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).containsExactlyInAnyOrder(firstMondayOfAugustFreeDay,
                secondMondayOfAugustFreeDay);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_justReasonChange_shouldUpdateFreePeriodReason() throws Exception {
        // given
        var firstMondayOfAugustFreeDay = databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var dto = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Another Holiday");

        // when
        request.putWithResponseBody(getTutorialGroupFreePeriodsPath() + firstMondayOfAugustFreeDay.getId(), dto, TutorialGroupFreePeriod.class, HttpStatus.OK);

        // then
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).hasSize(1);
        var updatedFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(firstMondayOfAugustFreeDay.getId());
        assertTutorialGroupFreePeriodCreatedCorrectlyFromDTO(updatedFreePeriod, dto);

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void delete_asInstructor_shouldActivatePreviouslyCancelledSessionsOnThatDate() throws Exception {
        // given
        var firstMondayOfAugustSession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);

        var firstOfAugustFreeDayDTO = createTutorialGroupFreePeriodDTO(firstAugustMonday, "Holiday");
        var periodId = request.postWithResponseBody(getTutorialGroupFreePeriodsPath(), firstOfAugustFreeDayDTO, TutorialGroupFreePeriod.class, HttpStatus.CREATED).getId();

        firstMondayOfAugustSession = this.tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());

        assertIndividualSessionIsCancelledOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleOneTutorialGroupId, "Holiday");

        // when
        request.delete(getTutorialGroupFreePeriodsPath() + periodId, HttpStatus.NO_CONTENT);

        // then
        firstMondayOfAugustSession = tutorialGroupSessionRepository.findByIdElseThrow(firstMondayOfAugustSession.getId());
        assertIndividualSessionIsActiveOnDate(firstMondayOfAugustSession, firstAugustMonday, exampleOneTutorialGroupId);
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
