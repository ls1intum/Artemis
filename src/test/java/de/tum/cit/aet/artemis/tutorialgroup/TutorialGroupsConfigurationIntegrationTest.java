package de.tum.cit.aet.artemis.tutorialgroup;

import static de.tum.cit.aet.artemis.tutorialgroup.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.user.util.UserFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupConfigurationDTO;

class TutorialGroupsConfigurationIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    private static final String TEST_PREFIX = "tutorialgroupconfiguration";

    Long exampleTutorialGroupId;

    private Long courseId;

    @BeforeEach
    @Override
    void setupTestScenario() {
        super.setupTestScenario();
        userUtilService.addUsers(this.testPrefix, 1, 2, 1, 1);
        if (userRepository.findOneByLogin(testPrefix + "instructor42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "instructor42"));
        }
        this.exampleTutorialGroupId = tutorialGroupUtilService
                .createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                        userRepository.findOneByLogin(testPrefix + "tutor1").orElseThrow(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").orElseThrow()))
                .getId();
    }

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

    @BeforeEach
    void deleteExistingConfiguration() {
        var course = courseUtilService.createCourse();
        course.setTimeZone(exampleTimeZone);
        courseRepository.save(course);
        courseId = course.getId();

    }

    private void deleteExampleConfiguration() {
        Course course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(courseId);
        TutorialGroupsConfiguration configuration = course.getTutorialGroupsConfiguration();
        if (configuration != null) {
            course.setTutorialGroupsConfiguration(null);
            configuration.setCourse(null);
            courseRepository.save(course);
            tutorialGroupsConfigurationRepository.delete(configuration);
        }
    }

    void testJustForInstructorEndpoints() throws Exception {
        var configuration = tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);
        request.putWithResponseBody(getTutorialGroupsConfigurationPath(courseId, configuration.getId()), TutorialGroupConfigurationDTO.of(configuration),
                TutorialGroupConfigurationDTO.class, HttpStatus.FORBIDDEN);
        this.deleteExampleConfiguration();
        request.postWithResponseBody(getTutorialGroupsConfigurationPath(courseId), TutorialGroupConfigurationDTO.of(buildExampleConfiguration(courseId)),
                TutorialGroupConfigurationDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getOneOfCourse_asStudent_shouldReturnTutorialGroupsConfiguration() throws Exception {
        // given
        var configuration = tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);
        // when
        var configurationFromRequest = request.get(this.getTutorialGroupsConfigurationPath(courseId), HttpStatus.OK, TutorialGroupConfigurationDTO.class);
        // then
        var expected = TutorialGroupConfigurationDTO.of(configuration);
        var normalizedActual = withNormalizedFreePeriods(configurationFromRequest);
        assertThat(normalizedActual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupsConfiguration() throws Exception {
        TutorialGroupConfigurationDTO configurationFromRequest = request.postWithResponseBody(getTutorialGroupsConfigurationPath(courseId),
                TutorialGroupConfigurationDTO.of(buildExampleConfiguration(courseId)), TutorialGroupConfigurationDTO.class, HttpStatus.CREATED);
        assertThat(configurationFromRequest).isNotNull();
        assertThat(configurationFromRequest.id()).isNotNull();
        var persisted = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId).orElseThrow();
        this.assertConfigurationStructure(persisted, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY, courseId, true, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_invalidDateFormat_shouldReturnBadRequest() throws Exception {
        var exampleConfig = buildExampleConfiguration(courseId);
        // not in correct uuuu-MM-dd format
        exampleConfig.setTutorialPeriodStartInclusive("2022-11-25T23:00:00.000Z");
        request.postWithResponseBody(getTutorialGroupsConfigurationPath(courseId), TutorialGroupConfigurationDTO.of(exampleConfig), TutorialGroupConfigurationDTO.class,
                HttpStatus.BAD_REQUEST);
        exampleConfig = buildExampleConfiguration(courseId);
        // not in correct uuuu-MM-dd format
        exampleConfig.setTutorialPeriodEndInclusive("2022-11-25T23:00:00.000Z");
        request.postWithResponseBody(getTutorialGroupsConfigurationPath(courseId), TutorialGroupConfigurationDTO.of(exampleConfig), TutorialGroupConfigurationDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_configurationAlreadyExists_shouldReturnBadRequest() throws Exception {
        // given
        tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);
        // when
        request.postWithResponseBody(getTutorialGroupsConfigurationPath(courseId), TutorialGroupConfigurationDTO.of(buildExampleConfiguration(courseId)),
                TutorialGroupConfigurationDTO.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId)).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_periodChange_deleteTutorialGroupFreePeriodsAndIndividualSessionsAndRecreateScheduledSessions() throws Exception {
        // given
        var configuration = tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);

        // when
        configuration.setTutorialPeriodEndInclusive(FIRST_SEPTEMBER_MONDAY.toString());
        request.putWithResponseBody(getTutorialGroupsConfigurationPath(courseId, configuration.getId()), TutorialGroupConfigurationDTO.of(configuration),
                TutorialGroupConfigurationDTO.class, HttpStatus.OK);
        // then
        configuration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(configuration.getId());
        this.assertConfigurationStructure(configuration, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY, courseId, true, true);
    }

    /**
     * Note: With this test we want to ensure that jackson can deserialize the tutorial group configuration if it is indirectly sent with another entity.
     * There was a bug that caused the deserialization to fail, as the date format checkers were put directly into the setter of date and time.
     * The problem was that jackson tried to deserialize the date and time with the date and time format checkers active, which failed. These checkers
     * should only be active in a direct creation / update case to ensure uuuu-MM-dd format in the database.
     *
     * @throws Exception if the request fails
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void persistEntityWithIndirectConnectionToConfiguration_dateAsFullIsoString_shouldNotThrowDeserializationException() throws Exception {
        // given
        tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);
        var course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(courseId);
        var configuration = course.getTutorialGroupsConfiguration();
        // this date format should not throw an error here, even though it is not the uuuu-MM-dd format we use in the database as it neither updates nor creates the configuration
        configuration.setTutorialPeriodStartInclusive("2022-11-25T23:00:00.000Z");
        configuration.setTutorialPeriodEndInclusive("2022-11-25T23:00:00.000Z");

        TextExercise textExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2), course);
        // the exercise is now indirectly connected to the configuration, and jackson will try to deserialize the configuration
        textExercise.setCourse(course);
        textExercise.setChannelName("testchannelname");
        request.postWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_switchUseTutorialGroupSetting_shouldCreateAndThenDeleteTutorialGroupChannels() throws Exception {
        // given
        var configuration = tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);
        var tutorialGroupWithSchedule = setUpTutorialGroupWithSchedule(courseId, "tutor1");
        this.assertConfigurationStructure(configuration, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY, courseId, true, true);
        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroupWithSchedule);
        // when
        configuration.setUseTutorialGroupChannels(false);
        request.putWithResponseBody(getTutorialGroupsConfigurationPath(courseId, configuration.getId()), TutorialGroupConfigurationDTO.of(configuration),
                TutorialGroupConfigurationDTO.class, HttpStatus.OK);
        // then
        configuration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(configuration.getId());
        this.assertConfigurationStructure(configuration, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY, courseId, false, true);
        assertTutorialGroupChannelDoesNotExist(tutorialGroupWithSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_switchUsePublicChannelsSetting_shouldSwitchChannelModeOfTutorialGroupChannels() throws Exception {
        // given
        var configuration = tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);
        var tutorialGroupWithSchedule = setUpTutorialGroupWithSchedule(courseId, "tutor1");
        this.assertConfigurationStructure(configuration, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY, courseId, true, true);
        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroupWithSchedule);
        // when
        configuration.setUsePublicTutorialGroupChannels(false);
        request.putWithResponseBody(getTutorialGroupsConfigurationPath(courseId, configuration.getId()), TutorialGroupConfigurationDTO.of(configuration),
                TutorialGroupConfigurationDTO.class, HttpStatus.OK);
        // then
        configuration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(configuration.getId());
        this.assertConfigurationStructure(configuration, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY, courseId, true, false);
        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroupWithSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_timeZoneChange_deleteTutorialGroupFreePeriodsAndIndividualSessionsAndRecreateScheduledSessions() throws Exception {
        // given
        var configuration = tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, FIRST_AUGUST_MONDAY, FIRST_SEPTEMBER_MONDAY);
        var tutorialGroupWithSchedule = setUpTutorialGroupWithSchedule(courseId, "tutor1");
        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroupWithSchedule.getId()).orElseThrow();
        this.buildAndSaveExampleIndividualTutorialGroupSession(tutorialGroupWithSchedule.getId(), FIRST_SEPTEMBER_MONDAY_00_00);
        tutorialGroupUtilService.addTutorialGroupFreePeriod(configuration.getId(), FOURTH_AUGUST_MONDAY_00_00, FOURTH_AUGUST_MONDAY_00_00, "Holiday");

        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroupWithSchedule.getId());
        assertThat(sessions).hasSize(3);
        var firstAugustMondaySession = sessions.getFirst();
        var secondAugustMondaySession = sessions.get(1);
        var firstSeptemberMondaySession = sessions.get(2);
        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, FIRST_AUGUST_MONDAY, tutorialGroupWithSchedule.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(secondAugustMondaySession, SECOND_AUGUST_MONDAY, tutorialGroupWithSchedule.getId(), persistedSchedule);
        this.assertIndividualSessionIsActiveOnDate(firstSeptemberMondaySession, FIRST_SEPTEMBER_MONDAY_00_00, tutorialGroupWithSchedule.getId());
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(courseId)).hasSize(1);

        // when
        // change time zone to berlin and change the end period
        var course = courseRepository.findByIdForUpdateElseThrow(courseId);
        course.setTimeZone("Europe/Berlin");
        course.setTutorialGroupsConfiguration(null);

        request.performMvcRequest(courseTestService.buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());

        course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(courseId);
        assertThat(course.getTutorialGroupsConfiguration()).isNotNull();

        sessions = this.getTutorialGroupSessionsAscending(tutorialGroupWithSchedule.getId());
        assertThat(sessions).hasSize(2);
        firstAugustMondaySession = sessions.getFirst();
        secondAugustMondaySession = sessions.get(1);

        this.assertTutorialGroupSessionProperties(firstAugustMondaySession, Optional.of(persistedSchedule.getId()), tutorialGroupWithSchedule.getId(),
                getDateTimeInBerlinTimeZone(FIRST_AUGUST_MONDAY, defaultSessionStartHour), getDateTimeInBerlinTimeZone(FIRST_AUGUST_MONDAY, defaultSessionEndHour),
                persistedSchedule.getLocation(), TutorialGroupSessionStatus.ACTIVE, null);

        this.assertTutorialGroupSessionProperties(secondAugustMondaySession, Optional.of(persistedSchedule.getId()), tutorialGroupWithSchedule.getId(),
                getDateTimeInBerlinTimeZone(SECOND_AUGUST_MONDAY, defaultSessionStartHour), getDateTimeInBerlinTimeZone(SECOND_AUGUST_MONDAY, defaultSessionEndHour),
                persistedSchedule.getLocation(), TutorialGroupSessionStatus.ACTIVE, null);
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(courseId)).hasSize(0);

    }

    @Nested
    class TutorialGroupConfigurationDTOTests {

        @Nested
        class FromTests {

            @Test
            void shouldThrowNullPointerExceptionWhenDtoIsNull() {
                assertThatThrownBy(() -> TutorialGroupConfigurationDTO.from(null)).isInstanceOf(NullPointerException.class);
            }

            @Test
            void shouldReturnEntityWithoutFreePeriodsWhenDtoHasNoFreePeriods() {
                var dto = new TutorialGroupConfigurationDTO(14L, "2024-01-01", "2024-02-01", true, false, null);

                var actual = TutorialGroupConfigurationDTO.from(dto);

                assertThat(actual.getTutorialPeriodStartInclusive()).isEqualTo("2024-01-01");
                assertThat(actual.getTutorialPeriodEndInclusive()).isEqualTo("2024-02-01");
                assertThat(actual.getUseTutorialGroupChannels()).isTrue();
                assertThat(actual.getUsePublicTutorialGroupChannels()).isFalse();
                assertThat(actual.getTutorialGroupFreePeriods()).isEmpty();
            }

            @Test
            void shouldReturnEntityWithFreePeriodsWhenDtoContainsFreePeriods() {
                var freePeriodDTO = new TutorialGroupConfigurationDTO.TutorialGroupFreePeriodDTO(18L, "2024-01-10T10:00:00Z", "2024-01-10T12:00:00Z", "Holiday");

                var dto = new TutorialGroupConfigurationDTO(1L, "2024-01-01", "2024-02-01", true, true, Set.of(freePeriodDTO));

                var actual = TutorialGroupConfigurationDTO.from(dto);

                assertThat(actual.getTutorialGroupFreePeriods()).hasSize(1);
            }
        }

        @Nested
        class FreePeriodFromTests {

            @Test
            void shouldThrowNullPointerExceptionWhenFreePeriodDtoIsNull() {
                assertThatThrownBy(() -> TutorialGroupConfigurationDTO.TutorialGroupFreePeriodDTO.from(null)).isInstanceOf(NullPointerException.class);
            }

            @Test
            void shouldThrowBadRequestWhenStartDateIsInvalid() {
                var invalid = new TutorialGroupConfigurationDTO.TutorialGroupFreePeriodDTO(1L, "invalid-date", null, "");

                assertThatThrownBy(() -> TutorialGroupConfigurationDTO.TutorialGroupFreePeriodDTO.from(invalid)).as("free period start date should be ISO 8601")
                        .isInstanceOf(BadRequestAlertException.class);
            }

            @Test
            void shouldThrowBadRequestWhenEndDateIsInvalid() {
                var invalid = new TutorialGroupConfigurationDTO.TutorialGroupFreePeriodDTO(1L, "2024-01-10T10:00:00Z", "invalid-date", "");

                assertThatThrownBy(() -> TutorialGroupConfigurationDTO.TutorialGroupFreePeriodDTO.from(invalid)).as("free period end date should be ISO 8601")
                        .isInstanceOf(BadRequestAlertException.class);
            }
        }

        @Nested
        class OfTests {

            @Test
            void shouldThrowNullPointerExceptionWhenEntityIsNull() {
                assertThatThrownBy(() -> TutorialGroupConfigurationDTO.of(null)).isInstanceOf(NullPointerException.class);
            }

            @Test
            void shouldReturnEmptyFreePeriodsWhenEntityFreePeriodsIsNull() {
                var entity = new TutorialGroupsConfiguration();
                entity.setId(5L);
                entity.setTutorialPeriodStartInclusive("2024-01-01");
                entity.setTutorialPeriodEndInclusive("2024-02-01");
                entity.setUseTutorialGroupChannels(true);
                entity.setUsePublicTutorialGroupChannels(true);
                entity.setTutorialGroupFreePeriods(null);

                var actual = TutorialGroupConfigurationDTO.of(entity);

                assertThat(actual.tutorialGroupFreePeriods()).isEmpty();
            }

            @Test
            void shouldMapFreePeriodsCorrectlyWhenEntityContainsFreePeriods() {
                var freePeriod = new TutorialGroupFreePeriod();
                freePeriod.setId(42L);
                freePeriod.setStart(ZonedDateTime.parse("2024-01-10T10:00:00Z"));
                freePeriod.setEnd(ZonedDateTime.parse("2024-01-10T12:00:00Z"));
                freePeriod.setReason("Holiday");

                var entity = new TutorialGroupsConfiguration();
                entity.setId(7L);
                entity.setTutorialPeriodStartInclusive("2024-01-01");
                entity.setTutorialPeriodEndInclusive("2024-02-01");
                entity.setUseTutorialGroupChannels(true);
                entity.setUsePublicTutorialGroupChannels(true);
                entity.setTutorialGroupFreePeriods(Set.of(freePeriod));

                var actual = TutorialGroupConfigurationDTO.of(entity);

                assertThat(actual.tutorialGroupFreePeriods()).hasSize(1);
                var mapped = actual.tutorialGroupFreePeriods().iterator().next();

                assertThat(mapped.id()).isEqualTo(42L);
                assertThat(ZonedDateTime.parse(mapped.start())).isEqualTo(ZonedDateTime.parse("2024-01-10T10:00:00Z"));
                assertThat(ZonedDateTime.parse(mapped.end())).isEqualTo(ZonedDateTime.parse("2024-01-10T12:00:00Z"));
                assertThat(mapped.reason()).isEqualTo("Holiday");
            }

            @Test
            void shouldSerializeAndDeserializeCorrectly() throws Exception {
                ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

                var dto = new TutorialGroupConfigurationDTO(1L, "2024-01-01", "2024-02-01", true, false, Set.of());

                var json = mapper.writeValueAsString(dto);
                var back = mapper.readValue(json, TutorialGroupConfigurationDTO.class);

                assertThat(back.tutorialPeriodStartInclusive()).isEqualTo("2024-01-01");
                assertThat(back.tutorialPeriodEndInclusive()).isEqualTo("2024-02-01");
                assertThat(back.useTutorialGroupChannels()).isTrue();
                assertThat(back.usePublicTutorialGroupChannels()).isFalse();
                assertThat(back.tutorialGroupFreePeriods()).isEmpty();
            }
        }

        @Nested
        class FreePeriodOfTests {

            @Test
            void shouldThrowNullPointerExceptionWhenFreePeriodEntityIsNull() {
                assertThatThrownBy(() -> TutorialGroupConfigurationDTO.TutorialGroupFreePeriodDTO.of(null)).isInstanceOf(NullPointerException.class);
            }
        }
    }

    private TutorialGroupConfigurationDTO withNormalizedFreePeriods(TutorialGroupConfigurationDTO dto) {
        return new TutorialGroupConfigurationDTO(dto.id(), dto.tutorialPeriodStartInclusive(), dto.tutorialPeriodEndInclusive(), dto.useTutorialGroupChannels(),
                dto.usePublicTutorialGroupChannels(), dto.tutorialGroupFreePeriods() == null ? Set.of() : dto.tutorialGroupFreePeriods());
    }
}
