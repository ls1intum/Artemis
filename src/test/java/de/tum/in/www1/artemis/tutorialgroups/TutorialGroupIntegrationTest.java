package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION;
import static de.tum.in.www1.artemis.tutorialgroups.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.google.common.collect.ImmutableSet;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource.TutorialGroupRegistrationImportDTO;

class TutorialGroupIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    private static final String TEST_PREFIX = "tutorialgroup";

    Long exampleOneTutorialGroupId;

    Long exampleTwoTutorialGroupId;

    @BeforeEach
    void setupTestScenario() {
        super.setupTestScenario();
        this.database.addUsers(this.testPrefix, 4, 2, 1, 1);
        if (userRepository.findOneByLogin(testPrefix + "instructor42").isEmpty()) {
            userRepository.save(ModelFactory.generateActivatedUser(testPrefix + "instructor42"));
        }
        // Add registration number to student 3
        User student3 = userRepository.findOneByLogin(testPrefix + "student3").get();
        // random number with maximal 20 digits
        student3.setRegistrationNumber(new SecureRandom().nextInt(1000000000) + "");
        userRepository.save(student3);

        // Add registration number to student 4
        User student4 = userRepository.findOneByLogin(testPrefix + "student4").get();
        // random number with maximal 20 digits
        student4.setRegistrationNumber(new SecureRandom().nextInt(1000000000) + "");
        userRepository.save(student4);

        var course = this.database.createCourse();
        course.setTimeZone(exampleTimeZone);
        courseRepository.save(course);

        exampleCourseId = course.getId();

        exampleConfigurationId = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1)).getId();

        exampleOneTutorialGroupId = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1",
                Language.ENGLISH.name(), userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").get())).getId();

        exampleTwoTutorialGroupId = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum2", 10, true, "LoremIpsum2", Language.GERMAN.name(),
                userRepository.findOneByLogin(testPrefix + "tutor2").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student2").get())).getId();
        tutorialGroupChannelManagementService.createChannelForTutorialGroup(tutorialGroupRepository.findByIdElseThrow(exampleOneTutorialGroupId));
        tutorialGroupChannelManagementService.createChannelForTutorialGroup(tutorialGroupRepository.findByIdElseThrow(exampleTwoTutorialGroupId));

    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    void testJustForInstructorEndpoints() throws Exception {
        request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), buildTutorialGroupWithoutSchedule("tutor1"), TutorialGroup.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId,
                new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get(),
                        "Lorem Ipsum", true),
                TutorialGroup.class, HttpStatus.FORBIDDEN);
        request.delete(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId, HttpStatus.FORBIDDEN);
        request.postListWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/register-multiple", new HashSet<>(), StudentDTO.class,
                HttpStatus.FORBIDDEN);
        request.getList(getTutorialGroupsPath(exampleCourseId) + "campus-values", HttpStatus.FORBIDDEN, String.class);
        request.getList(getTutorialGroupsPath(exampleCourseId) + "language-values", HttpStatus.FORBIDDEN, String.class);

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTitle_asUser_shouldReturnTitle() throws Exception {
        // when
        var tutorialGroupTitle = request.get("/api/tutorial-groups/" + exampleOneTutorialGroupId + "/title", HttpStatus.OK, String.class);
        // then
        var title = tutorialGroupRepository.findById(exampleOneTutorialGroupId).get().getTitle();
        assertThat(tutorialGroupTitle).isEqualTo(title);
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void getAllForCourse_asStudent_shouldHidePrivateInformation(boolean loadFromService) throws Exception {
        var tutorialGroupsOfCourse = getTutorialGroupsOfExampleCourse(loadFromService, TEST_PREFIX + "student1");
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).contains(exampleOneTutorialGroupId, exampleTwoTutorialGroupId);
        for (var tutorialGroup : tutorialGroupsOfCourse) { // private information hidden
            verifyPrivateInformationIsHidden(tutorialGroup);
        }
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    @ValueSource(booleans = { true, false })
    void getAllForCourse_asEditor_shouldHidePrivateInformation(boolean loadFromService) throws Exception {
        var tutorialGroupsOfCourse = getTutorialGroupsOfExampleCourse(loadFromService, TEST_PREFIX + "editor1");
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).contains(exampleOneTutorialGroupId, exampleTwoTutorialGroupId);
        for (var tutorialGroup : tutorialGroupsOfCourse) { // private information hidden
            verifyPrivateInformationIsHidden(tutorialGroup);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllForCourse_asTutorOfOneGroup_shouldShowPrivateInformationForOwnGroup(boolean loadFromService) throws Exception {
        var tutorialGroupsOfCourse = getTutorialGroupsOfExampleCourse(loadFromService, TEST_PREFIX + "tutor1");
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).contains(exampleOneTutorialGroupId, exampleTwoTutorialGroupId);
        var groupWhereTutor = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(exampleOneTutorialGroupId)).findFirst().get();
        verifyPrivateInformationIsShown(groupWhereTutor);

        var groupWhereNotTutor = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(exampleTwoTutorialGroupId)).findFirst().get();
        verifyPrivateInformationIsHidden(groupWhereNotTutor);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllForCourse_asInstructorOfCourse_shouldShowPrivateInformation(boolean loadFromService) throws Exception {
        var tutorialGroupsOfCourse = getTutorialGroupsOfExampleCourse(loadFromService, TEST_PREFIX + "instructor1");
        assertThat(tutorialGroupsOfCourse).hasSize(2);
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).containsExactlyInAnyOrder(exampleOneTutorialGroupId,
                exampleTwoTutorialGroupId);
        var group1 = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(exampleOneTutorialGroupId)).findFirst().get();
        verifyPrivateInformationIsShown(group1);
        var group2 = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(exampleTwoTutorialGroupId)).findFirst().get();
        verifyPrivateInformationIsShown(group2);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getOneOfCourse_asStudent_shouldHidePrivateInformation(boolean loadFromService) throws Exception {
        oneOfCoursePrivateInfoHiddenTest(loadFromService, TEST_PREFIX + "student1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getOneOfCourse_asEditor_shouldHidePrivateInformation(boolean loadFromService) throws Exception {
        oneOfCoursePrivateInfoHiddenTest(loadFromService, TEST_PREFIX + "editor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getOneOfCourse_asTutorOfGroup_shouldShowPrivateInformation(boolean loadFromService) throws Exception {
        oneOfCoursePrivateInfoShownTest(loadFromService, TEST_PREFIX + "tutor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOneOfCourse_asInstructor_shouldShowPrivateInformation(boolean loadFromService) throws Exception {
        oneOfCoursePrivateInfoShownTest(loadFromService, TEST_PREFIX + "instructor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getOneOfCourse_asNotTutorOfGroup_shouldHidePrivateInformation(boolean loadFromService) throws Exception {
        oneOfCoursePrivateInfoHiddenTest(loadFromService, TEST_PREFIX + "tutor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getUniqueLanguageValues_TwoUniqueValues_ShouldReturnBoth() throws Exception {
        var languageValues = request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups/language-values", HttpStatus.OK, String.class);
        assertThat(languageValues).containsExactlyInAnyOrder(Language.ENGLISH.name(), Language.GERMAN.name());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_NoSessions_AverageNull(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] {}, null, useSingleEndpoint);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_NoSessionWithAttendanceData_AverageNull(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { null }, null, useSingleEndpoint);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_lastThreeSessionsWithoutAttendanceData_AverageNull(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, null, null, null }, null, useSingleEndpoint);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_OneSession_AverageIsAttendanceOfSession(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { 8 }, 8, useSingleEndpoint);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_OneSessionOfTheLastThreeHasAttendanceData_AverageIsAttendanceOfSession(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, 8, null, null }, 8, useSingleEndpoint);
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, null, 8, null }, 8, useSingleEndpoint);
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, null, null, 8 }, 8, useSingleEndpoint);

    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_TwoSessions_AverageIsArithmeticMean(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { 8, 5 }, 7, useSingleEndpoint);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_TwoSessionsOfTheLastThreeHaveAttendanceData_AverageIsArithmeticMean(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, null, 8, 5 }, 7, useSingleEndpoint);
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, 8, null, 5 }, 7, useSingleEndpoint);
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, 8, 5, null }, 7, useSingleEndpoint);

    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_ThreeSessions_AverageIsArithmeticMean(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { 8, 5, 3 }, 5, useSingleEndpoint);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void averageAttendanceCalculationTest_MoreThanThreeSessions_AverageIsArithmeticMeanOfLastThree(boolean useSingleEndpoint) throws Exception {
        this.averageAttendanceTestScaffold(new Integer[] { 99, 99, 8, 5, 3 }, 5, useSingleEndpoint);
    }

    /**
     * Attendance Test Scaffold
     *
     * @param attendance        for each attendance value a session will be created in order (with a difference of 1 day)
     * @param expectedAverage   expected average in the tutorial group
     * @param useSingleEndpoint uses the single endpoint if true, otherwise the multiple endpoint
     * @throws Exception
     */
    void averageAttendanceTestScaffold(Integer[] attendance, Integer expectedAverage, boolean useSingleEndpoint) throws Exception {
        // given
        var tutorialGroupId = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of()).getId();
        var sessionToSave = new ArrayList<TutorialGroupSession>();
        var date = firstAugustMonday;
        for (Integer att : attendance) {
            var session = databaseUtilService.createIndividualTutorialGroupSession(tutorialGroupId, getExampleSessionStartOnDate(date), getExampleSessionEndOnDate(date), att);
            sessionToSave.add(session);
            date = date.plusDays(1);
        }
        Collections.shuffle(sessionToSave);
        var savedSessions = tutorialGroupSessionRepository.saveAllAndFlush(sessionToSave);
        assertThat(savedSessions).hasSize(attendance.length);

        TutorialGroup tutorialGroup;
        if (useSingleEndpoint) {
            tutorialGroup = request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroupId, HttpStatus.OK, TutorialGroup.class);
        }
        else {
            tutorialGroup = request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class).stream()
                    .filter(tg -> tg.getId().equals(tutorialGroupId)).findFirst().get();
        }

        // then
        assertThat(tutorialGroup.getAverageAttendance()).isEqualTo(expectedAverage);

        // cleanup
        tutorialGroupSessionRepository.deleteAllInBatch(savedSessions);
        tutorialGroupRepository.deleteById(tutorialGroupId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroup() throws Exception {
        // when
        var persistedTutorialGroup = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), buildTutorialGroupWithoutSchedule("tutor1"), TutorialGroup.class,
                HttpStatus.CREATED);
        // then
        assertThat(persistedTutorialGroup.getId()).isNotNull();
        asserTutorialGroupChannelIsCorrectlyConfigured(persistedTutorialGroup);

        // cleanup
        tutorialGroupRepository.deleteById(persistedTutorialGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_WithIdInBody_shouldReturnBadRequest() throws Exception {
        // given
        var tutorialGroup = buildTutorialGroupWithoutSchedule("tutor1");
        tutorialGroup.setId(22L);
        var numberOfTutorialGroups = tutorialGroupRepository.findAllByCourseId(exampleCourseId).size();
        // when
        request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupRepository.findAllByCourseId(exampleCourseId)).hasSize(numberOfTutorialGroups);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void create_tutorialGroupWithTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        var existingTitle = tutorialGroupRepository.findById(exampleOneTutorialGroupId).get().getTitle();
        // given
        var tutorialGroup = buildTutorialGroupWithoutSchedule("tutor1");
        tutorialGroup.setTitle(existingTitle);

        var numberOfTutorialGroups = tutorialGroupRepository.findAllByCourseId(exampleCourseId).size();
        // when
        request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupRepository.findAllByCourseId(exampleCourseId)).hasSize(numberOfTutorialGroups);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void delete_asInstructor_shouldDeleteTutorialGroup() throws Exception {
        // given
        var persistedTutorialGroup = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), buildTutorialGroupWithoutSchedule("tutor1"), TutorialGroup.class,
                HttpStatus.CREATED);
        assertThat(persistedTutorialGroup.getId()).isNotNull();
        var channel = asserTutorialGroupChannelIsCorrectlyConfigured(persistedTutorialGroup);

        var user = database.getUserByLogin(testPrefix + "tutor1");

        // create test post in the channel of the tutorial group
        Post post = new Post();
        post.setAuthor(user);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setConversation(channel);
        database.changeUser(testPrefix + "tutor1");
        Post createdPost = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/messages", post, Post.class, HttpStatus.CREATED);
        assertThat(createdPost.getConversation().getId()).isEqualTo(channel.getId());
        database.changeUser(testPrefix + "instructor1");

        request.delete(getTutorialGroupsPath(exampleCourseId) + persistedTutorialGroup.getId(), HttpStatus.NO_CONTENT);
        // then
        request.get(getTutorialGroupsPath(exampleCourseId) + persistedTutorialGroup.getId(), HttpStatus.NOT_FOUND, TutorialGroup.class);
        assertTutorialGroupChannelDoesNotExist(persistedTutorialGroup);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_asInstructor_shouldUpdateTutorialGroup() throws Exception {
        // given
        var existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        var newRandomTitle = generateRandomTitle();
        existingTutorialGroup.setTitle(newRandomTitle);

        // when
        var updatedTutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId,
                new TutorialGroupResource.TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true), TutorialGroup.class, HttpStatus.OK);

        // then
        assertThat(updatedTutorialGroup.getTitle()).isEqualTo(newRandomTitle);
        asserTutorialGroupChannelIsCorrectlyConfigured(updatedTutorialGroup);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAssignedTeachingAssistant_asInstructor_shouldUpdateTutorialGroupAndChannel() throws Exception {
        // given
        var existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTeachingAssistant(database.getUserByLogin(testPrefix + "tutor2"));

        // when
        var updatedTutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId,
                new TutorialGroupResource.TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true), TutorialGroup.class, HttpStatus.OK);

        // then
        assertThat(updatedTutorialGroup.getTeachingAssistant().getLogin()).isEqualTo(testPrefix + "tutor2");
        asserTutorialGroupChannelIsCorrectlyConfigured(updatedTutorialGroup);

        // reset teaching assistant to tutor 1
        existingTutorialGroup.setTeachingAssistant(database.getUserByLogin(testPrefix + "tutor1"));
        updatedTutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId,
                new TutorialGroupResource.TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true), TutorialGroup.class, HttpStatus.OK);
        assertThat(updatedTutorialGroup.getTeachingAssistant().getLogin()).isEqualTo(testPrefix + "tutor1");
        asserTutorialGroupChannelIsCorrectlyConfigured(updatedTutorialGroup);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_withTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        // given
        var existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        var originalTitle = existingTutorialGroup.getTitle() + "";
        var existingGroupTwo = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleTwoTutorialGroupId).get();
        existingTutorialGroup.setTitle("  " + existingGroupTwo.getTitle() + " ");
        // when
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId,
                new TutorialGroupResource.TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true), TutorialGroup.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get().getTitle()).isEqualTo(originalTitle);
        asserTutorialGroupChannelIsCorrectlyConfigured(existingTutorialGroup);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_withoutId_shouldReturnBadRequest() throws Exception {
        // then
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId, buildTutorialGroupWithoutSchedule("tutor1"), TutorialGroup.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void registerStudent_asTutorOfGroup_shouldAllowRegistration() throws Exception {
        this.registerStudentAllowedTest(TEST_PREFIX + "tutor1", false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void registerStudent_asNotTutorOfGroup_shouldForbidRegistration() throws Exception {
        this.registerStudentForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void registerStudent_asStudent_shouldForbidRegistration() throws Exception {
        this.registerStudentForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void registerStudent_asEditor_shouldForbidRegistration() throws Exception {
        this.registerStudentForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerStudent_asInstructor_shouldAllowRegistration() throws Exception {
        this.registerStudentAllowedTest(TEST_PREFIX + "instructor1", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerStudent_studentNotFound_shouldReturnNotFound() throws Exception {
        // then
        request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/register/" + "studentXX", HttpStatus.NOT_FOUND,
                new LinkedMultiValueMap<>());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerStudent_studentRegistered_shouldReturnNoContent() throws Exception {
        // given
        var student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        // when
        request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/register/" + student1.getLogin(), HttpStatus.NO_CONTENT,
                new LinkedMultiValueMap<>());
        // then
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student1);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deregisterStudent_asTutorOfGroup_shouldAllowDeregistration() throws Exception {
        this.deregisterStudentAllowedTest(TEST_PREFIX + "tutor1", false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_asInstructor_shouldAllowDeregistration() throws Exception {
        this.deregisterStudentAllowedTest(TEST_PREFIX + "instructor1", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void deregisterStudent_asNotTutorOfGroup_shouldForbidDeregistration() throws Exception {
        this.deregisterStudentForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deregisterStudent_asStudent_shouldForbidDeregistration() throws Exception {
        this.deregisterStudentForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void deregisterStudent_asEditor_shouldForbidDeregistration() throws Exception {
        this.deregisterStudentForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_studentNotRegistered_shouldReturnNoContent() throws Exception {
        // given
        var student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        // when
        request.delete(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/deregister/" + student3.getLogin(), HttpStatus.NO_CONTENT,
                new LinkedMultiValueMap<>());
        // then
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).doesNotContain(student3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deregisterStudent_studentNotFound_shouldReturnNotFound() throws Exception {
        // then
        request.delete(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/deregister/" + "studentXX", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerMultipleStudents_asInstructor_shouldRegisterStudents() throws Exception {
        // given
        var instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();
        var student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        student3.setRegistrationNumber("number3");
        userRepository.saveAndFlush(student3);

        var studentsToAdd = new ArrayList<StudentDTO>();

        var studentInCourse = new StudentDTO();
        studentInCourse.setLogin(student3.getLogin());
        studentInCourse.setRegistrationNumber(student3.getRegistrationNumber());

        var studentNotInCourse = new StudentDTO();
        studentNotInCourse.setLogin(TEST_PREFIX + "studentXX");
        studentNotInCourse.setRegistrationNumber("numberXX");

        studentsToAdd.add(studentInCourse);
        studentsToAdd.add(studentNotInCourse);
        // when
        List<StudentDTO> notFoundStudents = request.postListWithResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/register-multiple",
                studentsToAdd, StudentDTO.class, HttpStatus.OK);
        // then
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student3);
        assertThat(notFoundStudents).containsExactly(studentNotInCourse);
        verify(singleUserNotificationService, times(1)).notifyStudentAboutRegistrationToTutorialGroup(tutorialGroup, student3, instructor1);
        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);

        // remove registration of student 6 again
        // remove registration again
        var registration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup, student3, INSTRUCTOR_REGISTRATION)
                .get();
        tutorialGroupRegistrationRepository.delete(registration);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_justTutorialGroupTitle_shouldCreateTutorialGroups() throws Exception {
        // given
        var freshTitleOne = "freshTitleOne";
        var freshTitleTwo = "freshTitleTwo";

        var existingTitle = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get().getTitle();
        var regNullStudent = new TutorialGroupRegistrationImportDTO(freshTitleOne, null);
        var regBlankStudent = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO("", "", "", ""));
        var regStudentPropertiesNull = new TutorialGroupRegistrationImportDTO(freshTitleOne, new StudentDTO(null, null, null, null));
        var regExistingTutorialGroup = new TutorialGroupRegistrationImportDTO(existingTitle, null);
        assertTutorialWithTitleDoesNotExistInDb(freshTitleOne);
        assertTutorialWithTitleDoesNotExistInDb(freshTitleTwo);
        assertTutorialGroupWithTitleExistsInDb(existingTitle);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(regNullStudent);
        tutorialGroupRegistrations.add(regBlankStudent);
        tutorialGroupRegistrations.add(regExistingTutorialGroup);
        tutorialGroupRegistrations.add(regStudentPropertiesNull);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertThat(importResult).hasSize(4);
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::importSuccessful)).allMatch(status -> status.equals(true));
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::error)).allMatch(Objects::isNull);
        var regBlankExpected = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO(null, null, null, null));
        var studentPropertiesNullExpected = new TutorialGroupRegistrationImportDTO(freshTitleOne, new StudentDTO(null, null, null, null));
        assertThat(importResult.stream()).containsExactlyInAnyOrder(regNullStudent, regBlankExpected, regExistingTutorialGroup, studentPropertiesNullExpected);

        var instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();
        assertImportedTutorialGroupWithTitleInDB(freshTitleOne, new HashSet<>(), instructor1);
        assertImportedTutorialGroupWithTitleInDB(freshTitleTwo, new HashSet<>(), instructor1);
        assertTutorialGroupWithTitleExistsInDb(existingTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_tutorialGroupTitleAndStudents_shouldCreateTutorialAndRegisterStudents() throws Exception {
        // given
        var group1Id = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").get())).getId();

        var group2Id = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student2").get())).getId();

        var group1 = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group1Id).get();
        var group2 = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group2Id).get();

        // we test with student1 that the student will be deregistered from the old tutorial group
        var student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), student1);
        // we test with student3 that a previously unregistered student will be registered to an existing tutorial group
        var student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        assertUserIsNotRegisteredInATutorialGroup(student3);
        // we test with student4 that a previously unregistered student will be registered to a fresh tutorial group
        var freshTitle = "freshTitle";
        assertTutorialWithTitleDoesNotExistInDb(freshTitle);
        var student4 = userRepository.findOneByLogin(TEST_PREFIX + "student4").get();
        assertUserIsNotRegisteredInATutorialGroup(student4);
        // we test with student2 that a previously registered student will be registered to a fresh tutorial group
        var student2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        assertUserIsRegisteredInTutorialWithTitle(group2.getTitle(), student2);

        // student 1 from existing group1 to existing group 2
        // + test if identifying just with login works
        var student1Reg = new TutorialGroupRegistrationImportDTO(group2.getTitle(), new StudentDTO(student1.getLogin(), student1.getFirstName(), student1.getLastName(), ""));

        // student 3 to existing group 1
        // + test if identifying just with registration number works
        var student3Reg = new TutorialGroupRegistrationImportDTO(group1.getTitle(),
                new StudentDTO("", student3.getFirstName(), student3.getLastName(), student3.getRegistrationNumber()));

        // student 4 to fresh tutorial group
        // + test if identifying with both login and registration number works
        var student4Reg = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student4));

        // student 2 to fresh tutorial group
        var student2Reg = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student2));

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(student1Reg);
        tutorialGroupRegistrations.add(student3Reg);
        tutorialGroupRegistrations.add(student4Reg);
        tutorialGroupRegistrations.add(student2Reg);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertThat(importResult.size()).isEqualTo(4);
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::importSuccessful)).allMatch(status -> status.equals(true));
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::error)).allMatch(Objects::isNull);
        assertThat(importResult.stream()).containsExactlyInAnyOrder(student1Reg, student3Reg, student4Reg, student2Reg);

        assertUserIsRegisteredInTutorialWithTitle(group2.getTitle(), student1);
        assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), student3);

        var instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();
        assertImportedTutorialGroupWithTitleInDB(freshTitle, Set.of(student4, student2), instructor1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_withoutTitle_shouldNotCreateTutorialGroup() throws Exception {

        // given
        var group1Id = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").get())).getId();
        var group1 = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group1Id).get();
        var student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), student1);

        // given
        var emptyTitle = "";
        var reg = new TutorialGroupRegistrationImportDTO(emptyTitle, new StudentDTO(student1));
        assertTutorialWithTitleDoesNotExistInDb(emptyTitle);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(reg);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        assertThat(importResult).hasSize(1);
        assertTutorialWithTitleDoesNotExistInDb(emptyTitle);
        var importResultDTO = importResult.get(0);
        assertThat(importResultDTO.importSuccessful()).isFalse();
        assertThat(importResultDTO.error()).isEqualTo(TutorialGroupResource.TutorialGroupImportErrors.NO_TITLE);
        // student1 should still be registered in the old tutorial group
        assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), student1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_titleButNonExistingStudent_shouldStillCreateTutorialGroupButNoRegistration() throws Exception {
        // given
        var freshTitle = generateRandomTitle();
        var reg = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO("notExisting", "firstName", "firstName1", ""));
        assertTutorialWithTitleDoesNotExistInDb(freshTitle);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(reg);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        var instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();
        assertImportedTutorialGroupWithTitleInDB(freshTitle, new HashSet<>(), instructor1);
        assertThat(importResult).hasSize(1);
        var importResultDTO = importResult.get(0);
        assertThat(importResultDTO.importSuccessful()).isFalse();
        assertThat(importResultDTO.error()).isEqualTo(TutorialGroupResource.TutorialGroupImportErrors.NO_USER_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importRegistrations_titleButSameStudentToMultipleGroups_shouldStillCreateTutorialGroupsButNoRegistration() throws Exception {

        // given
        var freshTitle = generateRandomTitle();
        var freshTitleTwo = generateRandomTitle();
        // given
        var group1Id = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").get())).getId();

        var group2Id = databaseUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student2").get())).getId();

        var group1 = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group1Id).get();
        tutorialGroupRegistrationRepository.deleteAllByStudent(userRepository.findOneByLogin(testPrefix + "student3").get());
        tutorialGroupRegistrationRepository.deleteAllByStudent(userRepository.findOneByLogin(testPrefix + "student4").get());
        var group2 = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group2Id).get();
        tutorialGroupChannelManagementService.createChannelForTutorialGroup(group1);
        tutorialGroupChannelManagementService.createChannelForTutorialGroup(group2);

        var student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), student1);
        var student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        assertUserIsNotRegisteredInATutorialGroup(student3);

        var reg1 = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student1));
        var reg2 = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO(student1));
        var reg3 = new TutorialGroupRegistrationImportDTO(freshTitle, new StudentDTO(student3));
        var reg4 = new TutorialGroupRegistrationImportDTO(freshTitleTwo, new StudentDTO(student3));
        assertTutorialWithTitleDoesNotExistInDb(freshTitle);
        assertTutorialWithTitleDoesNotExistInDb(freshTitleTwo);

        var tutorialGroupRegistrations = new ArrayList<TutorialGroupRegistrationImportDTO>();
        tutorialGroupRegistrations.add(reg1);
        tutorialGroupRegistrations.add(reg2);
        tutorialGroupRegistrations.add(reg3);
        tutorialGroupRegistrations.add(reg4);
        // when
        var importResult = sendImportRequest(tutorialGroupRegistrations);
        // then
        var instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();
        assertImportedTutorialGroupWithTitleInDB(freshTitle, new HashSet<>(), instructor1);
        assertThat(importResult).hasSize(4);
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::importSuccessful)).allMatch(status -> status.equals(false));
        assertThat(importResult.stream().map(TutorialGroupRegistrationImportDTO::error)).allMatch(TutorialGroupResource.TutorialGroupImportErrors.MULTIPLE_REGISTRATIONS::equals);
        assertThat(importResult.stream()).containsExactlyInAnyOrder(reg1, reg2, reg3, reg4);
        // should still be registered in the old tutorial group
        assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), student1);
        assertUserIsNotRegisteredInATutorialGroup(student3);

        asserTutorialGroupChannelIsCorrectlyConfigured(group1);
        asserTutorialGroupChannelIsCorrectlyConfigured(group2);
    }

    private List<TutorialGroupRegistrationImportDTO> sendImportRequest(List<TutorialGroupRegistrationImportDTO> tutorialGroupRegistrations) throws Exception {
        return request.postListWithResponseBody(getTutorialGroupsPath(exampleCourseId) + "import", tutorialGroupRegistrations, TutorialGroupRegistrationImportDTO.class,
                HttpStatus.OK);
    }

    private void assertTutorialWithTitleDoesNotExistInDb(String title) {
        assertThat(tutorialGroupRepository.existsByTitleAndCourseId(title, exampleCourseId)).isFalse();
    }

    private void assertTutorialGroupWithTitleExistsInDb(String title) {
        assertThat(tutorialGroupRepository.existsByTitleAndCourseId(title, exampleCourseId)).isTrue();
        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroupRepository.findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(title, exampleCourseId).get());
    }

    private void assertUserIsRegisteredInTutorialWithTitle(String expectedTitle, User expectedStudent) {
        assertThat(tutorialGroupRegistrationRepository.existsByTutorialGroupTitleAndStudentAndType(expectedTitle, expectedStudent, INSTRUCTOR_REGISTRATION)).isTrue();
    }

    private void assertUserIsNotRegisteredInATutorialGroup(User expectedStudent) {
        assertThat(tutorialGroupRegistrationRepository.countByStudentAndTutorialGroupCourseIdAndType(expectedStudent, exampleCourseId, INSTRUCTOR_REGISTRATION)).isZero();
    }

    private void assertImportedTutorialGroupWithTitleInDB(String expectedTitle, Set<User> expectedRegisteredStudents, User requestingUser) {
        assertTutorialGroupWithTitleInDB(expectedTitle, expectedRegisteredStudents, INSTRUCTOR_REGISTRATION, false, null, 1, "Campus", Language.GERMAN.name(), requestingUser);
    }

    private void assertTutorialGroupWithTitleInDB(String expectedTitle, Set<User> expectedRegisteredStudents, TutorialGroupRegistrationType expectedRegistrationType,
            Boolean isOnline, String additionalInformation, Integer capacity, String campus, String language, User teachingAssistant) {
        var tutorialGroupOptional = tutorialGroupRepository.findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(expectedTitle, exampleCourseId);
        assertThat(tutorialGroupOptional).isPresent();
        var tutorialGroup = tutorialGroupOptional.get();
        assertThat(tutorialGroup.getIsOnline()).isEqualTo(isOnline);
        assertThat(tutorialGroup.getAdditionalInformation()).isEqualTo(additionalInformation);
        assertThat(tutorialGroup.getCapacity()).isEqualTo(capacity);
        assertThat(tutorialGroup.getCampus()).isEqualTo(campus);
        assertThat(tutorialGroup.getLanguage()).isEqualTo(language);
        assertThat(tutorialGroup.getTeachingAssistant()).isEqualTo(teachingAssistant);
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).containsExactlyInAnyOrderElementsOf(expectedRegisteredStudents);
        // assert that all registrations are instructor registrations (always the case for import)
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getType)).allMatch(regType -> regType.equals(expectedRegistrationType));
        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);
    }

    private void verifyPrivateInformationIsHidden(TutorialGroup tutorialGroup) {
        assertThat(tutorialGroup.getRegistrations()).isNullOrEmpty();
        assertThat(tutorialGroup.getTeachingAssistant()).isNull();
        assertThat(tutorialGroup.getCourse()).isNull();
    }

    private void verifyPrivateInformationIsShown(TutorialGroup tutorialGroup) {
        assertThat(tutorialGroup.getRegistrations()).isNotNull();
        assertThat(tutorialGroup.getRegistrations()).isNotEmpty();
        assertThat(tutorialGroup.getTeachingAssistant()).isNotNull();
        assertThat(tutorialGroup.getCourse()).isNotNull();
    }

    private void oneOfCoursePrivateInfoHiddenTest(boolean loadFromService, String userLogin) throws Exception {
        var tutorialGroup = getTutorialGroupOfExampleCourse(loadFromService, userLogin);
        assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
        verifyPrivateInformationIsHidden(tutorialGroup);
    }

    private void oneOfCoursePrivateInfoShownTest(boolean loadFromService, String userLogin) throws Exception {
        var tutorialGroup = getTutorialGroupOfExampleCourse(loadFromService, userLogin);
        assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
        verifyPrivateInformationIsShown(tutorialGroup);
    }

    private TutorialGroup getTutorialGroupOfExampleCourse(boolean loadFromService, String userLogin) throws Exception {
        if (loadFromService) {
            var user = userRepository.findOneByLogin(userLogin).get();
            var course = courseRepository.findById(exampleCourseId).get();
            return tutorialGroupService.getOneOfCourse(course, user, exampleOneTutorialGroupId);
        }
        else {
            return request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.OK, TutorialGroup.class);
        }
    }

    private List<TutorialGroup> getTutorialGroupsOfExampleCourse(boolean loadFromService, String userLogin) throws Exception {
        if (loadFromService) {
            var user = userRepository.findOneByLogin(userLogin).get();
            var course = courseRepository.findById(exampleCourseId).get();
            return tutorialGroupService.findAllForCourse(course, user).stream().toList();
        }
        else {
            return request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
        }
    }

    private void registerStudentAllowedTest(String loginOfResponsibleUser, boolean expectTutorNotification) throws Exception {
        var responsibleUser = database.getUserByLogin(loginOfResponsibleUser);
        var student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/register/" + student3.getLogin(), HttpStatus.NO_CONTENT,
                new LinkedMultiValueMap<>());
        var tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(student3);
        verify(singleUserNotificationService, times(1)).notifyStudentAboutRegistrationToTutorialGroup(tutorialGroup, student3, responsibleUser);
        if (expectTutorNotification) {
            verify(singleUserNotificationService, times(1)).notifyTutorAboutRegistrationToTutorialGroup(tutorialGroup, student3, responsibleUser);
        }
        else {
            verify(singleUserNotificationService, times(0)).notifyTutorAboutRegistrationToTutorialGroup(tutorialGroup, student3, responsibleUser);
        }

        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);

        // remove registration again
        var registration = tutorialGroupRegistrationRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup, student3, INSTRUCTOR_REGISTRATION)
                .get();
        tutorialGroupRegistrationRepository.delete(registration);
    }

    private void registerStudentForbiddenTest() throws Exception {
        var student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/register/" + student3.getLogin(), HttpStatus.FORBIDDEN,
                new LinkedMultiValueMap<>());
    }

    private void deregisterStudentAllowedTest(String loginOfResponsibleUser, boolean expectTutorNotification) throws Exception {
        var responsibleUser = database.getUserByLogin(loginOfResponsibleUser);
        var student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        request.delete(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/deregister/" + student1.getLogin(), HttpStatus.NO_CONTENT);
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).get();
        assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).doesNotContain(student1);
        verify(singleUserNotificationService, times(1)).notifyStudentAboutDeregistrationFromTutorialGroup(tutorialGroup, student1, responsibleUser);
        if (expectTutorNotification) {
            verify(singleUserNotificationService, times(1)).notifyTutorAboutDeregistrationFromTutorialGroup(tutorialGroup, student1, responsibleUser);
        }
        else {
            verify(singleUserNotificationService, times(0)).notifyTutorAboutDeregistrationFromTutorialGroup(tutorialGroup, student1, responsibleUser);
        }
        asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);

        // reset registration
        var registration = new TutorialGroupRegistration();
        registration.setStudent(student1);
        registration.setTutorialGroup(tutorialGroup);
        registration.setType(INSTRUCTOR_REGISTRATION);
        tutorialGroupRegistrationRepository.save(registration);
    }

    private void deregisterStudentForbiddenTest() throws Exception {
        var student1 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        request.delete(getTutorialGroupsPath(exampleCourseId) + exampleOneTutorialGroupId + "/deregister/" + student1.getLogin(), HttpStatus.FORBIDDEN);
    }

}
