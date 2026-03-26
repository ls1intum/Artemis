package de.tum.cit.aet.artemis.tutorialgroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.OneToOneChatTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.CreateAndUpdateTutorialGroupDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupDetailDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupScheduleDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionDTO;

class TutorialGroupIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    private static final String TEST_PREFIX = "tutorialgroup";

    private static final String FIRST_COURSE_PREFIX = TEST_PREFIX + "firstcourse";

    private static final String SECOND_COURSE_PREFIX = TEST_PREFIX + "secondcourse";

    private static final String FIRST_COURSE_INSTRUCTOR1_LOGIN = TEST_PREFIX + "firstcourseinstructor1";

    private static final String FIRST_COURSE_TUTOR1_LOGIN = TEST_PREFIX + "firstcoursetutor1";

    private static final String FIRST_COURSE_TUTOR2_LOGIN = TEST_PREFIX + "firstcoursetutor2";

    private static final String FIRST_COURSE_STUDENT1_LOGIN = TEST_PREFIX + "firstcoursestudent1";

    private static final String FIRST_COURSE_STUDENT2_LOGIN = TEST_PREFIX + "firstcoursestudent2";

    private static final String FIRST_COURSE_STUDENT3_LOGIN = TEST_PREFIX + "firstcoursestudent3";

    private static final String FIRST_COURSE_STUDENT4_LOGIN = TEST_PREFIX + "firstcoursestudent4";

    private static final String SECOND_COURSE_EDITOR1_LOGIN = TEST_PREFIX + "secondcourseeditor1";

    private User firstCourseInstructor1;

    private User firstCourseTutor1;

    private User firstCourseTutor2;

    private User firstCourseStudent1;

    private User firstCourseStudent2;

    private User firstCourseStudent3;

    private User firstCourseStudent4;

    private Long secondCourseId;

    private User secondCourseEditor1;

    private TutorialGroup firstCourseTutorialGroup1;

    private TutorialGroup firstCourseTutorialGroup2;

    private Channel firstCourseChannel1;

    private Channel firstCourseChannel2;

    private List<TutorialGroupSession> firstCourseTutorialGroupSessions1;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private OneToOneChatTestRepository oneToOneChatRepository;

    @BeforeEach
    @Override
    void setupTestScenario() {
        super.setupTestScenario();
        Course course = courseUtilService.createCourseWithUserPrefix(FIRST_COURSE_PREFIX);
        course.setTimeZone(timeZone);
        courseRepository.save(course);
        courseId = course.getId();

        userUtilService.addStudent(course.getStudentGroupName(), FIRST_COURSE_STUDENT1_LOGIN);
        userUtilService.addStudent(course.getStudentGroupName(), FIRST_COURSE_STUDENT2_LOGIN);
        userUtilService.addStudent(course.getStudentGroupName(), FIRST_COURSE_STUDENT3_LOGIN);
        userUtilService.addStudent(course.getStudentGroupName(), FIRST_COURSE_STUDENT4_LOGIN);
        userUtilService.addTeachingAssistant(course.getTeachingAssistantGroupName(), FIRST_COURSE_TUTOR1_LOGIN);
        userUtilService.addTeachingAssistant(course.getTeachingAssistantGroupName(), FIRST_COURSE_TUTOR2_LOGIN);
        userUtilService.addEditor(course.getEditorGroupName(), testPrefix + "editor1");
        userUtilService.addInstructor(course.getInstructorGroupName(), FIRST_COURSE_INSTRUCTOR1_LOGIN);

        firstCourseInstructor1 = userRepository.findOneByLogin(FIRST_COURSE_INSTRUCTOR1_LOGIN).orElseThrow();
        firstCourseTutor1 = userRepository.findOneByLogin(FIRST_COURSE_TUTOR1_LOGIN).orElseThrow();
        firstCourseTutor2 = userRepository.findOneByLogin(FIRST_COURSE_TUTOR2_LOGIN).orElseThrow();
        firstCourseStudent1 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT1_LOGIN).orElseThrow();
        firstCourseStudent2 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT2_LOGIN).orElseThrow();
        firstCourseStudent3 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT3_LOGIN).orElseThrow();
        firstCourseStudent4 = userRepository.findOneByLogin(FIRST_COURSE_STUDENT4_LOGIN).orElseThrow();

        firstCourseStudent3.setRegistrationNumber("3");
        userRepository.save(firstCourseStudent3);
        firstCourseStudent4.setRegistrationNumber("4");
        userRepository.save(firstCourseStudent4);

        configurationId = tutorialGroupUtilService.createTutorialGroupConfiguration(courseId, LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1)).getId();

        firstCourseTutorialGroup1 = tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "TG Mo 13", null, 10, false, "Garching", Language.ENGLISH.name(),
                firstCourseTutor1, Set.of(firstCourseStudent1));
        TutorialGroupSchedule tutorialGroupSchedule1 = tutorialGroupUtilService.createAndSaveTutorialGroupSchedule(firstCourseTutorialGroup1, 1, "13:00:00", "14:00:00", 1,
                AUGUST_FIRST_MONDAY.toString(), AUGUST_FIFTH_MONDAY.toString(), "01.05.13");
        firstCourseTutorialGroupSessions1 = tutorialGroupUtilService.createAndSaveTutorialGroupSessions(course, firstCourseTutorialGroup1, tutorialGroupSchedule1);
        firstCourseChannel1 = tutorialGroupChannelManagementService.createChannelForTutorialGroup(firstCourseTutorialGroup1);

        firstCourseTutorialGroup2 = tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "TG Tue 13", null, 20, true, null, Language.GERMAN.name(),
                firstCourseTutor2, Set.of(firstCourseStudent2));
        firstCourseChannel2 = tutorialGroupChannelManagementService.createChannelForTutorialGroup(firstCourseTutorialGroup2);

        var secondCourse = courseUtilService.createCourseWithUserPrefix(SECOND_COURSE_PREFIX);
        secondCourse.setTimeZone(timeZone);
        secondCourse = courseRepository.save(secondCourse);
        secondCourseId = secondCourse.getId();
        userUtilService.addEditor(secondCourse.getEditorGroupName(), SECOND_COURSE_EDITOR1_LOGIN);
        secondCourseEditor1 = userRepository.findOneByLogin(SECOND_COURSE_EDITOR1_LOGIN).orElseThrow();
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
    void getTitle_asUser_shouldReturnTitle() throws Exception {
        var tutorialGroupTitle = request.get("/api/tutorialgroup/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(tutorialGroupTitle).isEqualTo(firstCourseTutorialGroup1.getTitle());
    }

    @Test
    @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
    void getUniqueLanguageValues_TwoUniqueValues_ShouldReturnBoth() throws Exception {
        var languageValues = request.getList("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/language-values", HttpStatus.OK, String.class);
        assertThat(languageValues).containsExactlyInAnyOrder(Language.ENGLISH.name(), Language.GERMAN.name());
    }

    @Test
    @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
    void getUniqueLanguageValues_asTutor_shouldReturnForbidden() throws Exception {
        request.getList("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/language-values", HttpStatus.FORBIDDEN, String.class);
    }

    @Nested
    class GetTutorialGroupsTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void getTutorialGroupsForCourse_asStudent_shouldHidePrivateInformation() throws Exception {
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
            assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(Collectors.toSet())).contains(firstCourseTutorialGroup1.getId(),
                    firstCourseTutorialGroup2.getId());
            for (TutorialGroup tutorialGroup : tutorialGroupsOfCourse) {
                verifyPrivateInformationIsHidden(tutorialGroup);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void getTutorialGroupsForCourse_asEditor_shouldHidePrivateInformation() throws Exception {
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
            assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(Collectors.toSet())).contains(firstCourseTutorialGroup1.getId(),
                    firstCourseTutorialGroup2.getId());
            for (var tutorialGroup : tutorialGroupsOfCourse) { // private information hidden
                verifyPrivateInformationIsHidden(tutorialGroup);
            }
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void getTutorialGroupsForCourse_asTutorOfOneGroup_shouldShowPrivateInformationForOwnGroup() throws Exception {
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
            assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(Collectors.toSet())).contains(firstCourseTutorialGroup1.getId(),
                    firstCourseTutorialGroup2.getId());
            var groupWhereTutor = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(firstCourseTutorialGroup1.getId())).findFirst()
                    .orElseThrow();
            verifyPrivateInformationIsShown(groupWhereTutor);

            var groupWhereNotTutor = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(firstCourseTutorialGroup2.getId())).findFirst()
                    .orElseThrow();
            verifyPrivateInformationIsHidden(groupWhereNotTutor);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void getTutorialGroupsForCourse_asInstructorOfCourse_shouldShowPrivateInformation() throws Exception {
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
            assertThat(tutorialGroupsOfCourse).hasSize(2);
            assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(Collectors.toSet())).contains(firstCourseTutorialGroup1.getId(),
                    firstCourseTutorialGroup2.getId());
            var group1 = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(firstCourseTutorialGroup1.getId())).findFirst().orElseThrow();
            verifyPrivateInformationIsShown(group1);
            var group2 = tutorialGroupsOfCourse.stream().filter(tutorialGroup -> tutorialGroup.getId().equals(firstCourseTutorialGroup2.getId())).findFirst().orElseThrow();
            verifyPrivateInformationIsShown(group2);
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
    }

    @Nested
    class GetTutorialGroupTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnForbiddenIfCourseDoesNotExist() throws Exception {
            String nonExistentCourseId = "-1";
            String url = "/api/tutorialgroup/courses/" + nonExistentCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId();
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenIfUserNotInCourse() throws Exception {
            String url = "/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId();
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnInternalServerErrorIfCourseHasNoTimezone() throws Exception {
            Course course = courseRepository.findById(courseId).orElseThrow();
            course.setTimeZone(null);
            courseRepository.save(course);
            String url = "/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId();
            request.get(url, HttpStatus.INTERNAL_SERVER_ERROR, String.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnNotFoundIfTutorialGroupDoesNotExist() throws Exception {
            String nonExistentTutorialGroupId = "-1";
            String url = "/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + nonExistentTutorialGroupId;
            request.get(url, HttpStatus.NOT_FOUND, String.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnCorrectGroupWithCorrectSessions() throws Exception {
            TutorialGroupSession cancelledSession = firstCourseTutorialGroupSessions1.getFirst();
            TutorialGroupSession relocatedSession = firstCourseTutorialGroupSessions1.get(1);
            TutorialGroupSession changedTimeSession = firstCourseTutorialGroupSessions1.get(2);
            TutorialGroupSession changedDateSession = firstCourseTutorialGroupSessions1.get(3);
            TutorialGroupSession attendanceCountSession = firstCourseTutorialGroupSessions1.get(4);

            String url = "/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId();
            TutorialGroupDetailDTO groupDTO = request.get(url, HttpStatus.OK, TutorialGroupDetailDTO.class);
            assertThat(groupDTO.id()).isEqualTo(firstCourseTutorialGroup1.getId());
            assertThat(groupDTO.title()).isEqualTo(firstCourseTutorialGroup1.getTitle());
            assertThat(groupDTO.language()).isEqualTo(firstCourseTutorialGroup1.getLanguage());
            assertThat(groupDTO.isOnline()).isEqualTo(firstCourseTutorialGroup1.getIsOnline());
            assertThat(groupDTO.tutorName()).isEqualTo(firstCourseTutor1.getName());
            assertThat(groupDTO.tutorLogin()).isEqualTo(firstCourseTutor1.getLogin());
            assertThat(groupDTO.capacity()).isEqualTo(firstCourseTutorialGroup1.getCapacity());
            assertThat(groupDTO.campus()).isEqualTo(firstCourseTutorialGroup1.getCampus());
            assertThat(groupDTO.groupChannelId()).isEqualTo(firstCourseChannel1.getId());

            List<TutorialGroupSessionDTO> sessionDTOs = groupDTO.sessions();
            assertThat(sessionDTOs).hasSize(5);
            TutorialGroupSessionDTO firstSessionDTO = sessionDTOs.getFirst();
            assertGroupDTOHasCorrectFields(firstSessionDTO, cancelledSession);
            assertGroupDTOHasCorrectFlags(firstSessionDTO, true, false, false, false);

            TutorialGroupSessionDTO secondSessionDTO = sessionDTOs.get(1);
            assertGroupDTOHasCorrectFields(secondSessionDTO, relocatedSession);
            assertGroupDTOHasCorrectFlags(secondSessionDTO, false, true, false, false);

            TutorialGroupSessionDTO thirdSessionDTO = sessionDTOs.get(2);
            assertGroupDTOHasCorrectFields(thirdSessionDTO, changedTimeSession);
            assertGroupDTOHasCorrectFlags(thirdSessionDTO, false, false, true, false);

            TutorialGroupSessionDTO fourthSessionDTO = sessionDTOs.get(3);
            assertGroupDTOHasCorrectFields(fourthSessionDTO, changedDateSession);
            assertGroupDTOHasCorrectFlags(fourthSessionDTO, false, false, false, true);

            TutorialGroupSessionDTO fifthSessionDTO = sessionDTOs.get(4);
            assertGroupDTOHasCorrectFields(fifthSessionDTO, attendanceCountSession);
            assertGroupDTOHasCorrectFlags(fifthSessionDTO, false, false, false, false);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnResponseWithoutTutorChatIdIfNoChatExists() throws Exception {
            TutorialGroup group = buildTutorialGroupWithoutSchedule("firstcoursetutor1");
            tutorialGroupTestRepository.save(group);
            TutorialGroupSchedule schedule = buildExampleSchedule(AUGUST_FIRST_MONDAY_00_00.toLocalDate(), AUGUST_SECOND_MONDAY_00_00.toLocalDate());
            schedule.setTutorialGroup(group);
            tutorialGroupScheduleTestRepository.save(schedule);

            String url = "/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + group.getId();
            TutorialGroupDetailDTO response = request.get(url, HttpStatus.OK, TutorialGroupDetailDTO.class);
            assertThat(response.tutorChatId()).isNull();
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnResponseWithTutorChatIdIfChatExists() throws Exception {
            TutorialGroup group = buildTutorialGroupWithoutSchedule("firstcoursetutor1");
            tutorialGroupTestRepository.save(group);
            TutorialGroupSchedule schedule = buildExampleSchedule(AUGUST_FIRST_MONDAY_00_00.toLocalDate(), AUGUST_SECOND_MONDAY_00_00.toLocalDate());
            schedule.setTutorialGroup(group);
            tutorialGroupScheduleTestRepository.save(schedule);

            Course course = courseRepository.findById(courseId).orElseThrow();
            var oneToOneChat = new OneToOneChat();
            oneToOneChat.setCourse(course);
            oneToOneChat.setCreator(firstCourseStudent1);
            oneToOneChatRepository.save(oneToOneChat);

            ConversationParticipant participationOfUserA = ConversationParticipant.createWithDefaultValues(firstCourseStudent1, oneToOneChat);
            ConversationParticipant participationOfUserB = ConversationParticipant.createWithDefaultValues(firstCourseTutor1, oneToOneChat);
            conversationParticipantRepository.saveAll(List.of(participationOfUserA, participationOfUserB));

            String url = "/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + group.getId();
            TutorialGroupDetailDTO response = request.get(url, HttpStatus.OK, TutorialGroupDetailDTO.class);
            assertThat(response.tutorChatId()).isEqualTo(oneToOneChat.getId());
        }
    }

    @Nested
    class GetTutorialGroupScheduleTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void getTutorialGroupSchedule_withSchedule_shouldReturnSchedule() throws Exception {
            var response = request.get("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/schedule", HttpStatus.OK,
                    TutorialGroupScheduleDTO.class);

            assertThat(response.firstSessionStart()).isEqualTo(AUGUST_FIRST_MONDAY_13_00);
            assertThat(response.firstSessionEnd()).isEqualTo(AUGUST_FIRST_MONDAY_14_00);
            assertThat(response.repetitionFrequency()).isEqualTo(1);
            assertThat(response.tutorialPeriodEnd()).isEqualTo(AUGUST_FIFTH_MONDAY);
            assertThat(response.location()).isEqualTo("01.05.13");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void getTutorialGroupSchedule_withoutSchedule_shouldReturnNoContent() throws Exception {
            request.get("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup2.getId() + "/schedule", HttpStatus.NO_CONTENT, String.class);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getTutorialGroupSchedule_wrongCourse_shouldReturnNotFound() throws Exception {
            request.get("/api/tutorialgroup/courses/" + secondCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/schedule", HttpStatus.NOT_FOUND, String.class);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getTutorialGroupSchedule_asEditorNotInCourse_shouldReturnForbidden() throws Exception {
            request.get("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/schedule", HttpStatus.FORBIDDEN, String.class);
        }
    }

    @Nested
    class CreateTutorialGroupTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void create_asEditorWithScheduleSet_shouldCreateTutorialGroup() throws Exception {
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO = new TutorialGroupScheduleDTO(AUGUST_FIRST_MONDAY_10_00, AUGUST_FIRST_MONDAY_12_00, 1, AUGUST_FOURTH_MONDAY,
                    "01.03.12");
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mo 10", firstCourseTutor1.getId(), "English", false,
                    "Garching", 10, "Bring you machine.", tutorialGroupScheduleDTO);

            Long tutorialGroupId = request.postWithResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, Long.class,
                    HttpStatus.OK);

            TutorialGroup tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(tutorialGroupId);
            assertTutorialGroupHasExpectedProperties(tutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor1, 0);

            TutorialGroupSchedule tutorialGroupSchedule = tutorialGroup.getTutorialGroupSchedule();
            assertTutorialGroupScheduleHasExpectedProperties(tutorialGroupSchedule, tutorialGroupScheduleDTO);

            List<TutorialGroupSession> sessions = tutorialGroup.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart)).toList();
            assertTutorialGroupSessionsHaveExpectedProperties(sessions, tutorialGroup, tutorialGroupScheduleDTO);

            Channel channel = tutorialGroup.getTutorialGroupChannel();
            assertTutorialGroupChannelHasExpectedProperties(channel, tutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void create_asEditorWithoutScheduleSet_shouldCreateTutorialGroup() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mo 10", firstCourseTutor1.getId(), "English", false,
                    "Garching", 10, "Bring you machine.", null);

            Long tutorialGroupId = request.postWithResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, Long.class,
                    HttpStatus.OK);

            TutorialGroup tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(tutorialGroupId);
            assertTutorialGroupHasExpectedProperties(tutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor1, 0);

            TutorialGroupSchedule tutorialGroupSchedule = tutorialGroup.getTutorialGroupSchedule();
            assertThat(tutorialGroupSchedule).isNull();

            Set<TutorialGroupSession> sessions = tutorialGroup.getTutorialGroupSessions();
            assertThat(sessions).isEmpty();

            Channel channel = tutorialGroup.getTutorialGroupChannel();
            assertTutorialGroupChannelHasExpectedProperties(channel, tutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void create_asEditorWithTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
            var existingTitle = tutorialGroupTestRepository.findById(firstCourseTutorialGroup1.getId()).orElseThrow().getTitle();
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO(existingTitle, firstCourseTutor1.getId(), "English", false,
                    "Garching", 10, "Bring you machine.", null);

            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void create_asEditorWithNoTutorialGroupsConfiguration_shouldReturnBadRequest() throws Exception {
            Course course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(courseId);
            TutorialGroupsConfiguration configuration = course.getTutorialGroupsConfiguration();
            course.setTutorialGroupsConfiguration(null);
            configuration.setCourse(null);
            courseRepository.save(course);
            tutorialGroupsConfigurationRepository.delete(configuration);

            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mo 10", firstCourseTutor1.getId(), "English", false,
                    "Garching", 10, "Bring you machine.", null);

            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void create_asEditorOfOtherCourse_shouldReturnForbidden() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mo 10", firstCourseTutor1.getId(), "English", false,
                    "Garching", 10, "Bring you machine.", null);

            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, HttpStatus.FORBIDDEN);
        }

        // TODO: check notifications in tests
    }

    @Nested
    class UpdateTutorialGroupTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithOldAndNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO = new TutorialGroupScheduleDTO(AUGUST_FIRST_MONDAY_10_00, AUGUST_FIRST_MONDAY_12_00, 1, AUGUST_FOURTH_MONDAY,
                    "01.03.12");
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mo 10 Updated", firstCourseTutor2.getId(), "English", false,
                    "Garching", 10, "Bring your machine.", tutorialGroupScheduleDTO);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup1.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor2, 1);

            TutorialGroupSchedule updatedTutorialGroupSchedule = updatedTutorialGroup.getTutorialGroupSchedule();
            assertTutorialGroupScheduleHasExpectedProperties(updatedTutorialGroupSchedule, tutorialGroupScheduleDTO);

            List<TutorialGroupSession> updatedSessions = updatedTutorialGroup.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart))
                    .toList();
            assertTutorialGroupSessionsHaveExpectedProperties(updatedSessions, updatedTutorialGroup, tutorialGroupScheduleDTO);

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup1.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor2);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithOldWithoutNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mo 13 Updated", firstCourseTutor2.getId(), "English", true,
                    "Munich", 12, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup1.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor2, 1);

            assertThat(updatedTutorialGroup.getTutorialGroupSchedule()).isNull();
            assertThat(updatedTutorialGroup.getTutorialGroupSessions()).isEmpty();

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup1.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor2);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithoutOldWithNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO = new TutorialGroupScheduleDTO(AUGUST_FIRST_MONDAY_10_00, AUGUST_FIRST_MONDAY_12_00, 1, AUGUST_FOURTH_MONDAY,
                    "01.03.12");
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Tue 13 Updated", firstCourseTutor1.getId(), "English", false,
                    "Garching", 25, "Bring your machine.", tutorialGroupScheduleDTO);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup2.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup2.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor1, 1);

            TutorialGroupSchedule updatedTutorialGroupSchedule = updatedTutorialGroup.getTutorialGroupSchedule();
            assertTutorialGroupScheduleHasExpectedProperties(updatedTutorialGroupSchedule, tutorialGroupScheduleDTO);

            List<TutorialGroupSession> updatedSessions = updatedTutorialGroup.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart))
                    .toList();
            assertTutorialGroupSessionsHaveExpectedProperties(updatedSessions, updatedTutorialGroup, tutorialGroupScheduleDTO);

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup2.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithoutOldAndNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Tue 15", firstCourseTutor1.getId(), "English", false,
                    "Garching", 15, "Updated information without schedule.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup2.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup2.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor1, 1);

            assertThat(updatedTutorialGroup.getTutorialGroupSchedule()).isNull();
            assertThat(updatedTutorialGroup.getTutorialGroupSessions()).isEmpty();

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup2.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithoutOldTutorialGroup_shouldReturnNotFound() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mon 15", firstCourseTutor1.getId(), "English", false,
                    "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/-1", createAndUpdateTutorialGroupDTO, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithoutMatchingCourse_shouldReturnBadRequest() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mon 15", firstCourseTutor1.getId(), "English", false,
                    "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + secondCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createAndUpdateTutorialGroupDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithNonExistingNewTutor_shouldReturnNotFound() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mon 15", -1L, "English", false, "Garching", 15,
                    "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithoutTutorialGroupsConfiguration_shouldReturnBadRequest() throws Exception {
            Course course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(courseId);
            TutorialGroupsConfiguration configuration = course.getTutorialGroupsConfiguration();
            course.setTutorialGroupsConfiguration(null);
            configuration.setCourse(null);
            courseRepository.save(course);
            tutorialGroupsConfigurationRepository.delete(configuration);

            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mon 15", firstCourseTutor1.getId(), "English", false,
                    "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void update_asEditorWithoutCourseTimezone_shouldReturnBadRequest() throws Exception {
            Course course = courseRepository.findByIdElseThrow(courseId);
            course.setTimeZone(null);
            courseRepository.save(course);

            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mon 15", firstCourseTutor1.getId(), "English", false,
                    "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorOfOtherCourse_shouldReturnForbidden() throws Exception {
            CreateAndUpdateTutorialGroupDTO createAndUpdateTutorialGroupDTO = new CreateAndUpdateTutorialGroupDTO("TG Mon 15", firstCourseTutor1.getId(), "English", false,
                    "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), createAndUpdateTutorialGroupDTO,
                    HttpStatus.FORBIDDEN);
        }

        // TODO: add notification cases
        // TODO: check what happens if channels enabled changes -> is the logic in create/update implemented correctly for all scenarios?
    }

    private void assertTutorialGroupHasExpectedProperties(TutorialGroup tutorialGroup, CreateAndUpdateTutorialGroupDTO tutorialGroupDTO, User expectedTutor,
            int expectedRegistrationCount) {
        assertThat(tutorialGroup.getCourse().getId()).isEqualTo(courseId);
        assertThat(tutorialGroup.getTitle()).isEqualTo(tutorialGroupDTO.title());
        assertThat(tutorialGroup.getAdditionalInformation()).isEqualTo(tutorialGroupDTO.additionalInformation());
        assertThat(tutorialGroup.getCapacity()).isEqualTo(tutorialGroupDTO.capacity());
        assertThat(tutorialGroup.getIsOnline()).isEqualTo(tutorialGroupDTO.isOnline());
        assertThat(tutorialGroup.getCampus()).isEqualTo(tutorialGroupDTO.campus());
        assertThat(tutorialGroup.getLanguage()).isEqualTo(tutorialGroupDTO.language());
        assertThat(tutorialGroup.getTeachingAssistant()).isEqualTo(expectedTutor);
        assertThat(tutorialGroup.getRegistrations()).hasSize(expectedRegistrationCount);
    }

    private void assertTutorialGroupScheduleHasExpectedProperties(TutorialGroupSchedule tutorialGroupSchedule, TutorialGroupScheduleDTO tutorialGroupScheduleDTO) {
        assertThat(tutorialGroupSchedule.getDayOfWeek()).isEqualTo(tutorialGroupScheduleDTO.firstSessionStart().getDayOfWeek().getValue());
        assertThat(LocalTime.parse(tutorialGroupSchedule.getStartTime())).isEqualTo(tutorialGroupScheduleDTO.firstSessionStart().toLocalTime());
        assertThat(LocalTime.parse(tutorialGroupSchedule.getEndTime())).isEqualTo(tutorialGroupScheduleDTO.firstSessionEnd().toLocalTime());
        assertThat(tutorialGroupSchedule.getRepetitionFrequency()).isEqualTo(tutorialGroupScheduleDTO.repetitionFrequency());
        assertThat(LocalDate.parse(tutorialGroupSchedule.getValidFromInclusive())).isEqualTo(tutorialGroupScheduleDTO.firstSessionStart().toLocalDate());
        assertThat(LocalDate.parse(tutorialGroupSchedule.getValidToInclusive())).isEqualTo(tutorialGroupScheduleDTO.tutorialPeriodEnd());
        assertThat(tutorialGroupSchedule.getLocation()).isEqualTo(tutorialGroupScheduleDTO.location());
    }

    private void assertTutorialGroupSessionsHaveExpectedProperties(List<TutorialGroupSession> sessions, TutorialGroup tutorialGroup,
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO) {
        var courseTimeZone = ZoneId.of(tutorialGroup.getCourse().getTimeZone());
        var expectedSessionStart = ZonedDateTime.of(tutorialGroupScheduleDTO.firstSessionStart(), courseTimeZone);
        var expectedSessionEnd = ZonedDateTime.of(tutorialGroupScheduleDTO.firstSessionEnd(), courseTimeZone);
        var tutorialPeriodEnd = tutorialGroupScheduleDTO.tutorialPeriodEnd();
        var repetitionFrequency = tutorialGroupScheduleDTO.repetitionFrequency();
        var expectedNumberOfSessions = 0;
        var nextSessionDate = tutorialGroupScheduleDTO.firstSessionStart().toLocalDate();
        while (!nextSessionDate.isAfter(tutorialPeriodEnd)) {
            expectedNumberOfSessions++;
            nextSessionDate = nextSessionDate.plusWeeks(repetitionFrequency);
        }

        assertThat(sessions).hasSize(expectedNumberOfSessions);
        for (var session : sessions) {
            assertThat(session.getStart()).isEqualTo(expectedSessionStart);
            assertThat(session.getEnd()).isEqualTo(expectedSessionEnd);
            assertThat(session.getLocation()).isEqualTo(tutorialGroupScheduleDTO.location());
            assertThat(session.getAttendanceCount()).isNull();
            expectedSessionStart = expectedSessionStart.plusWeeks(repetitionFrequency);
            expectedSessionEnd = expectedSessionEnd.plusWeeks(repetitionFrequency);
        }
    }

    private void assertTutorialGroupChannelHasExpectedProperties(Channel channel, TutorialGroup tutorialGroup, User currentTutor) {
        var cleanedTitle = tutorialGroup.getTitle().replaceAll("\\s", "-").toLowerCase();
        var expectedChannelName = "tutorgroup-" + cleanedTitle.substring(0, Math.min(cleanedTitle.length(), 18));
        assertThat(channel).isNotNull();
        assertThat(channel.getName()).isEqualTo(expectedChannelName);
        assertThat(channel.getIsPublic()).isTrue();
        assertThat(channel.getIsAnnouncementChannel()).isFalse();
        assertThat(channel.getIsArchived()).isFalse();
        assertThat(channel.getIsCourseWide()).isFalse();
        assertThat(channel.getCourse()).isEqualTo(tutorialGroup.getCourse());
        assertThat(channel.getCreator()).isNull();

        var participants = conversationParticipantRepository.findConversationParticipantsByConversationId(channel.getId());
        assertThat(participants).filteredOn(participant -> Boolean.TRUE.equals(participant.getIsModerator())).singleElement()
                .satisfies(participant -> assertThat(participant.getUser()).isEqualTo(currentTutor));
    }

    @Nested
    class DeleteTutorialGroupTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "EDITOR")
        void delete_asInstructor_shouldDeleteTutorialGroup() throws Exception {

        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "EDITOR")
        void delete_asInstructorWithNonExistingGroup_shouldReturnNotFound() throws Exception {

        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "EDITOR")
        void delete_asInstructorWithNonMatchingCourse_shouldReturnBadRequest() throws Exception {

        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "EDITOR")
        void delete_asInstructorOfOtherCourse_shouldReturnForbidden() throws Exception {

        }

        // TODO; add notification case
    }

    @Nested
    class BatchRegisterStudentsTests {

    }

    @Nested
    class DeregisterStudentTests {

    }

    @Nested
    class SearchUnregisteredStudentsTests {

    }

    @Nested
    class GetRegisteredStudentsTests {

    }

    @Nested
    class ImportRegistrationsTests {

    }

    @Nested
    class ImportAndExportTutorialGroupsTests {

        /*
         * @Test
         * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
         * void testCSVContentWithSampleData() throws Exception {
         * // given
         * tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle1", "SampleCampus1", 10, false, "SampleInfo1", "ENGLISH", firstCourseTutor1,
         * Set.of(firstCourseStudent1));
         * tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle2", "SampleCampus2", 20, true, "SampleInfo2", "GERMAN", firstCourseTutor1,
         * Set.of(firstCourseStudent2));
         * // when
         * var params = new LinkedMultiValueMap<String, String>();
         * params.add("fields", "ID,Title,Campus,Language,Capacity,IsOnline");
         * String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/csv").queryParams(params).toUriString();
         * String csvContent = request.get(url, HttpStatus.OK, String.class);
         * // then
         * assertThat(csvContent).contains("ID,Title,Campus,Language,Capacity,IsOnline");
         * assertThat(csvContent).contains("SampleTitle1,SampleInfo1,ENGLISH,10");
         * assertThat(csvContent).contains("SampleTitle2,SampleInfo2,GERMAN,20");
         * }
         * @Test
         * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
         * void testExportTutorialGroupsToJSON() throws Exception {
         * // when
         * var params = new LinkedMultiValueMap<String, String>();
         * params.add("fields", "ID,Title,Campus,Language");
         * String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
         * String jsonResponse = request.get(url, HttpStatus.OK, String.class);
         * // then
         * assertThat(jsonResponse).contains("id");
         * assertThat(jsonResponse).contains("title");
         * assertThat(jsonResponse).contains("campus");
         * assertThat(jsonResponse).contains("language");
         * assertThat(jsonResponse).contains("LoremIpsum1");
         * assertThat(jsonResponse).contains("LoremIpsum2");
         * }
         * @Test
         * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
         * void testExportTutorialGroupsToCSVWithAllFields() throws Exception {
         * // when
         * var params = new LinkedMultiValueMap<String, String>();
         * params.add("fields", "ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Students");
         * String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/csv").queryParams(params).toUriString();
         * String csvContent = request.get(url, HttpStatus.OK, String.class);
         * // then
         * assertThat(csvContent)
         * .contains("ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Registration Number,First Name,Last Name");
         * assertThat(csvContent).contains("LoremIpsum1");
         * assertThat(csvContent).contains("LoremIpsum2");
         * }
         * @Test
         * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
         * void testJSONContentWithSampleData() throws Exception {
         * // given
         * tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle1", "SampleCampus1", 10, false, "SampleInfo1", "ENGLISH", firstCourseTutor1,
         * Set.of(firstCourseStudent1));
         * tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle2", "SampleCampus2", 20, true, "SampleInfo2", "GERMAN", firstCourseTutor1,
         * Set.of(firstCourseStudent2));
         * // when
         * var params = new LinkedMultiValueMap<String, String>();
         * params.add("fields", "ID,Title,Campus,Language,Capacity,Is Online");
         * String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
         * String jsonResponse = request.get(url, HttpStatus.OK, String.class);
         * // then
         * assertThat(jsonResponse).contains("id");
         * assertThat(jsonResponse).contains("title");
         * assertThat(jsonResponse).contains("campus");
         * assertThat(jsonResponse).contains("language");
         * assertThat(jsonResponse).contains("capacity");
         * assertThat(jsonResponse).contains("isOnline");
         * assertThat(jsonResponse).contains("SampleTitle1");
         * assertThat(jsonResponse).contains("SampleInfo1");
         * assertThat(jsonResponse).contains("ENGLISH");
         * assertThat(jsonResponse).contains("10");
         * assertThat(jsonResponse).contains("SampleTitle2");
         * assertThat(jsonResponse).contains("SampleInfo2");
         * assertThat(jsonResponse).contains("GERMAN");
         * assertThat(jsonResponse).contains("20");
         * }
         * @Test
         * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
         * void testExportTutorialGroupsToJSONWithAllFields() throws Exception {
         * // when
         * var params = new LinkedMultiValueMap<String, String>();
         * params.add("fields", "ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Students");
         * String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
         * String jsonResponse = request.get(url, HttpStatus.OK, String.class);
         * // then
         * assertThat(jsonResponse).contains("id");
         * assertThat(jsonResponse).contains("title");
         * assertThat(jsonResponse).contains("campus");
         * assertThat(jsonResponse).contains("language");
         * assertThat(jsonResponse).contains("additionalInformation");
         * assertThat(jsonResponse).contains("capacity");
         * assertThat(jsonResponse).contains("isOnline");
         * assertThat(jsonResponse).contains("dayOfWeek");
         * assertThat(jsonResponse).doesNotContain("startTime"); // value is not defined
         * assertThat(jsonResponse).doesNotContain("endTime"); // value is not defined
         * assertThat(jsonResponse).doesNotContain("location"); // value is not defined
         * assertThat(jsonResponse).contains("students");
         * assertThat(jsonResponse).contains("LoremIpsum1");
         * assertThat(jsonResponse).contains("LoremIpsum2");
         * }
         * @Test
         * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
         * void testJSONContentWithSampleDataIncludingOptionalFields() throws Exception {
         * // given
         * tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle1", "SampleCampus1", 10, false, "SampleInfo1", "ENGLISH", firstCourseTutor1,
         * Set.of(firstCourseStudent1));
         * tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle2", "SampleCampus2", 20, true, "SampleInfo2", "GERMAN", firstCourseTutor1,
         * Set.of(firstCourseStudent2));
         * // when
         * var params = new LinkedMultiValueMap<String, String>();
         * params.add("fields", "ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Students");
         * String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
         * String jsonResponse = request.get(url, HttpStatus.OK, String.class);
         * // then
         * assertThat(jsonResponse).contains("id");
         * assertThat(jsonResponse).contains("title");
         * assertThat(jsonResponse).contains("campus");
         * assertThat(jsonResponse).contains("language");
         * assertThat(jsonResponse).contains("additionalInformation");
         * assertThat(jsonResponse).contains("capacity");
         * assertThat(jsonResponse).contains("isOnline");
         * assertThat(jsonResponse).contains("dayOfWeek");
         * assertThat(jsonResponse).doesNotContain("startTime"); // value is not defined
         * assertThat(jsonResponse).doesNotContain("endTime"); // value is not defined
         * assertThat(jsonResponse).doesNotContain("location"); // value is not defined
         * assertThat(jsonResponse).contains("students");
         * assertThat(jsonResponse).contains("SampleTitle1");
         * assertThat(jsonResponse).contains("SampleInfo1");
         * assertThat(jsonResponse).contains("ENGLISH");
         * assertThat(jsonResponse).contains("10");
         * assertThat(jsonResponse).contains("SampleTitle2");
         * assertThat(jsonResponse).contains("SampleInfo2");
         * assertThat(jsonResponse).contains("GERMAN");
         * assertThat(jsonResponse).contains("20");
         * }
         */

    }

    /*
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_NoSessions_AverageNull(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{}, null, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_NoSessionWithAttendanceData_AverageNull(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{null}, null, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_lastThreeSessionsWithoutAttendanceData_AverageNull(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, null, null, null}, 99, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_OneSession_AverageIsAttendanceOfSession(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{8}, 8, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_OneSessionOfTheLastThreeHasAttendanceData_AverageIsAttendanceOfSession(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, 8, null, null}, 69, useSingleEndpoint);
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, null, 8, null}, 69, useSingleEndpoint);
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, null, null, 8}, 69, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_TwoSessions_AverageIsArithmeticMean(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{8, 5}, 7, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_TwoSessionsOfTheLastThreeHaveAttendanceData_AverageIsArithmeticMean(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, null, 8, 5}, 37, useSingleEndpoint);
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, 8, null, 5}, 37, useSingleEndpoint);
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, 8, 5, null}, 37, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_ThreeSessions_AverageIsArithmeticMean(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{8, 5, 3}, 5, useSingleEndpoint);
     * }
     * @ParameterizedTest
     * @ValueSource(booleans = {true, false})
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void averageAttendanceCalculationTest_MoreThanThreeSessions_AverageIsArithmeticMeanOfLastThree(boolean useSingleEndpoint) throws Exception {
     * this.averageAttendanceTestScaffold(new Integer[]{99, 99, 8, 5, 3}, 5, useSingleEndpoint);
     * }
     * // Attendance Test Scaffold
     * //
     * // @param attendance for each attendance value a session will be created in order (with a difference of 1 day)
     * // @param expectedAverage expected average in the tutorial group
     * //@param useSingleEndpoint uses the single endpoint if true, otherwise the multiple endpoint
     * //@throws Exception an exception might occur
     * void averageAttendanceTestScaffold(Integer[] attendance, Integer expectedAverage, boolean useSingleEndpoint) throws Exception {
     * // given
     * var tutorialGroupId = tutorialGroupUtilService
     * .createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(), firstCourseTutor1, Set.of()).getId();
     * var sessionToSave = new ArrayList<TutorialGroupSession>();
     * var date = FIRST_AUGUST_MONDAY_00_00;
     * for (Integer att : attendance) {
     * var session = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroupId, getExampleSessionStartOnDate(date.toLocalDate()),
     * getExampleSessionEndOnDate(date.toLocalDate()), att);
     * sessionToSave.add(session);
     * date = date.plusDays(1);
     * }
     * Collections.shuffle(sessionToSave);
     * var savedSessions = tutorialGroupSessionRepository.saveAllAndFlush(sessionToSave);
     * assertThat(savedSessions).hasSize(attendance.length);
     * TutorialGroup tutorialGroup;
     * if (useSingleEndpoint) {
     * tutorialGroup = request.get("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroupId, HttpStatus.OK, TutorialGroup.class);
     * } else {
     * tutorialGroup = request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class).stream()
     * .filter(tg -> tg.getId().equals(tutorialGroupId)).findFirst().orElseThrow();
     * }
     * // then
     * assertThat(tutorialGroup.getAverageAttendance()).isEqualTo(expectedAverage);
     * // cleanup
     * tutorialGroupSessionRepository.deleteAllInBatch(savedSessions);
     * tutorialGroupTestRepository.deleteById(tutorialGroupId);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void delete_asInstructor_shouldDeleteTutorialGroup() throws Exception {
     * courseUtilService.enableMessagingForCourse(courseRepository.findByIdElseThrow(exampleCourseId));
     * // given
     * var persistedTutorialGroup = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), buildTutorialGroupDTOWithoutSchedule("firstCourseTutor1"),
     * TutorialGroup.class,
     * HttpStatus.CREATED);
     * assertThat(persistedTutorialGroup.getId()).isNotNull();
     * var channel = asserTutorialGroupChannelIsCorrectlyConfigured(persistedTutorialGroup);
     * var user = userUtilService.getUserByLogin(testPrefix + "firstCourseTutor1");
     * // create test post in the channel of the tutorial group
     * Post post = new Post();
     * post.setAuthor(user);
     * post.setDisplayPriority(DisplayPriority.NONE);
     * post.setConversation(channel);
     * userUtilService.changeUser(testPrefix + "firstCourseTutor1");
     * Post createdPost = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/messages", post, Post.class, HttpStatus.CREATED);
     * assertThat(createdPost.getConversation().getId()).isEqualTo(channel.getId());
     * userUtilService.changeUser(testPrefix + "firstCourseInstructor1");
     * request.delete(getTutorialGroupsPath(exampleCourseId, persistedTutorialGroup.getId()), HttpStatus.NO_CONTENT);
     * // then
     * request.get(getTutorialGroupsPath(exampleCourseId, persistedTutorialGroup.getId()), HttpStatus.NOT_FOUND, TutorialGroup.class);
     * assertTutorialGroupChannelDoesNotExist(persistedTutorialGroup);
     * persistedTutorialGroup.getRegistrations().forEach(registration -> verify(websocketMessagingService, timeout(2000).times(1))
     * .sendMessage(eq("/topic/user/" + registration.getStudent().getId() + "/notifications/tutorial-groups"), any()));
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void update_asInstructor_shouldUpdateTutorialGroup() throws Exception {
     * // given
     * var existingTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * var newRandomTitle = generateRandomTitle();
     * existingTutorialGroup.setTitle(newRandomTitle);
     * // when
     * var updatedTutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId),
     * new TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true), TutorialGroup.class, HttpStatus.OK);
     * // then
     * assertThat(updatedTutorialGroup.getTitle()).isEqualTo(newRandomTitle);
     * asserTutorialGroupChannelIsCorrectlyConfigured(updatedTutorialGroup);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void updateAssignedTeachingAssistant_asInstructor_shouldUpdateTutorialGroupAndChannel() throws Exception {
     * // given
     * var existingTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * existingTutorialGroup.setTeachingAssistant(userUtilService.getUserByLogin(testPrefix + "firstCourseTutor2"));
     * // when
     * var updatedTutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId),
     * new TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true), TutorialGroup.class, HttpStatus.OK);
     * // then
     * assertThat(updatedTutorialGroup.getTeachingAssistant().getLogin()).isEqualTo(testPrefix + "firstCourseTutor2");
     * asserTutorialGroupChannelIsCorrectlyConfigured(updatedTutorialGroup);
     * // reset teaching assistant to tutor 1
     * existingTutorialGroup.setTeachingAssistant(userUtilService.getUserByLogin(testPrefix + "firstCourseTutor1"));
     * updatedTutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId),
     * new TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true), TutorialGroup.class, HttpStatus.OK);
     * assertThat(updatedTutorialGroup.getTeachingAssistant().getLogin()).isEqualTo(testPrefix + "firstCourseTutor1");
     * asserTutorialGroupChannelIsCorrectlyConfigured(updatedTutorialGroup);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void update_withTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
     * // given
     * var existingTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * var originalTitle = existingTutorialGroup.getTitle();
     * var existingGroupTwo = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleTwoTutorialGroupId).orElseThrow();
     * existingTutorialGroup.setTitle("  " + existingGroupTwo.getTitle() + " ");
     * // when
     * request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId), new TutorialGroupUpdateDTO(existingTutorialGroup, "Lorem Ipsum", true),
     * TutorialGroup.class, HttpStatus.BAD_REQUEST);
     * // then
     * assertThat(tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow().getTitle())
     * .isEqualTo(originalTitle);
     * asserTutorialGroupChannelIsCorrectlyConfigured(existingTutorialGroup);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void update_withoutId_shouldReturnBadRequest() throws Exception {
     * // then
     * request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId), buildTutorialGroupWithoutSchedule("firstCourseTutor1"), TutorialGroup.class,
     * HttpStatus.BAD_REQUEST);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseTutor1", roles = "TA")
     * void registerStudent_asTutorOfGroup_shouldAllowRegistration() throws Exception {
     * this.registerStudentAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseTutor2", roles = "TA")
     * void registerStudent_asNotTutorOfGroup_shouldForbidRegistration() throws Exception {
     * this.registerStudentForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseStudent1", roles = "USER")
     * void registerStudent_asStudent_shouldForbidRegistration() throws Exception {
     * this.registerStudentForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
     * void registerStudent_asEditor_shouldForbidRegistration() throws Exception {
     * this.registerStudentForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void registerStudent_asInstructor_shouldAllowRegistration() throws Exception {
     * this.registerStudentAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void registerStudent_studentNotFound_shouldReturnNotFound() throws Exception {
     * // then
     * request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/register/" + "studentXX", HttpStatus.NOT_FOUND,
     * new LinkedMultiValueMap<>());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void registerStudent_studentRegistered_shouldReturnNoContent() throws Exception {
     * // when
     * request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/register/" + firstCourseStudent1.getLogin(), HttpStatus.NO_CONTENT,
     * new LinkedMultiValueMap<>());
     * // then
     * var tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(firstCourseStudent1);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseTutor1", roles = "TA")
     * void deregisterStudent_asTutorOfGroup_shouldAllowDeregistration() throws Exception {
     * this.deregisterStudentAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void deregisterStudent_asInstructor_shouldAllowDeregistration() throws Exception {
     * this.deregisterStudentAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseTutor2", roles = "TA")
     * void deregisterStudent_asNotTutorOfGroup_shouldForbidDeregistration() throws Exception {
     * this.deregisterStudentForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseStudent1", roles = "USER")
     * void deregisterStudent_asStudent_shouldForbidDeregistration() throws Exception {
     * this.deregisterStudentForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
     * void deregisterStudent_asEditor_shouldForbidDeregistration() throws Exception {
     * this.deregisterStudentForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void deregisterStudent_studentNotRegistered_shouldReturnNoContent() throws Exception {
     * // when
     * request.delete(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/deregister/" + firstCourseStudent3.getLogin(), HttpStatus.NO_CONTENT,
     * new LinkedMultiValueMap<>());
     * // then
     * TutorialGroup tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).doesNotContain(firstCourseStudent3);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void deregisterStudent_studentNotFound_shouldReturnNotFound() throws Exception {
     * // then
     * request.delete(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/deregister/" + "studentXX", HttpStatus.NOT_FOUND);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void registerMultipleStudents_asInstructor_shouldRegisterStudents() throws Exception {
     * // given
     * firstCourseStudent3.setRegistrationNumber("number3");
     * userRepository.saveAndFlush(firstCourseStudent3);
     * var studentsToAdd = new ArrayList<StudentDTO>();
     * var studentInCourse = new StudentDTO(firstCourseStudent3.getLogin(), null, null, firstCourseStudent3.getRegistrationNumber(), null);
     * var studentNotInCourse = new StudentDTO(TEST_PREFIX + "studentXX", null, null, "numberXX", null);
     * studentsToAdd.add(studentInCourse);
     * studentsToAdd.add(studentNotInCourse);
     * // when
     * List<StudentDTO> notFoundStudents = request.postListWithResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/register-multiple",
     * studentsToAdd, StudentDTO.class, HttpStatus.OK);
     * // then
     * var tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(firstCourseStudent3);
     * assertThat(notFoundStudents).containsExactly(studentNotInCourse);
     * asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);
     * // remove registration of student 6 again
     * // remove registration again
     * var registration = tutorialGroupRegistrationTestRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup, firstCourseStudent3,
     * INSTRUCTOR_REGISTRATION)
     * .orElseThrow();
     * tutorialGroupRegistrationTestRepository.delete(registration);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void importRegistrations_justTutorialGroupTitle_shouldCreateTutorialGroups() throws Exception {
     * // given
     * var freshTitleOne = "freshTitleOne";
     * var freshTitleTwo = "freshTitleTwo";
     * var existingTitle = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow().getTitle();
     * var regNullStudent = new TutorialGroupImportDTO(freshTitleOne, null, null, null, null, null, null);
     * var regBlankStudent = new TutorialGroupImportDTO(freshTitleTwo, new StudentDTO("", "", "", "", ""), null, null, null, null, null);
     * var regStudentPropertiesNull = new TutorialGroupImportDTO(freshTitleOne, new StudentDTO(null, null, null, null, null), null, null, null, null, null);
     * var regExistingTutorialGroup = new TutorialGroupImportDTO(existingTitle, null, null, null, null, null, null);
     * assertTutorialWithTitleDoesNotExistInDb(freshTitleOne);
     * assertTutorialWithTitleDoesNotExistInDb(freshTitleTwo);
     * assertTutorialGroupWithTitleExistsInDb(existingTitle);
     * var tutorialGroupRegistrations = new ArrayList<TutorialGroupImportDTO>();
     * tutorialGroupRegistrations.add(regNullStudent);
     * tutorialGroupRegistrations.add(regBlankStudent);
     * tutorialGroupRegistrations.add(regExistingTutorialGroup);
     * tutorialGroupRegistrations.add(regStudentPropertiesNull);
     * // when
     * var importResult = sendImportRequest(tutorialGroupRegistrations);
     * // then
     * assertThat(importResult).hasSize(4);
     * var regBlankExpected = new TutorialGroupImportDTO(freshTitleTwo, new StudentDTO(null, null, null, null, null), null, null, null, null, null);
     * assertThat(importResult.stream()).containsExactlyInAnyOrder(regNullStudent, regBlankExpected, regExistingTutorialGroup, regStudentPropertiesNull);
     * assertImportedTutorialGroupWithTitleInDB(freshTitleOne, new HashSet<>(), firstCourseInstructor1);
     * assertImportedTutorialGroupWithTitleInDB(freshTitleTwo, new HashSet<>(), firstCourseInstructor1);
     * assertTutorialGroupWithTitleExistsInDb(existingTitle);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void importRegistrations_tutorialGroupTitleAndStudents_shouldCreateTutorialAndRegisterStudents() throws Exception {
     * // given
     * var group1Id = tutorialGroupUtilService
     * .createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(), firstCourseTutor1,
     * Set.of(firstCourseStudent1)).getId();
     * var group2Id = tutorialGroupUtilService
     * .createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(), firstCourseTutor1,
     * Set.of(firstCourseStudent2)).getId();
     * var group1 = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group1Id).orElseThrow();
     * var group2 = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group2Id).orElseThrow();
     * // we test with firstCourseStudent1 that the student will be deregistered from the old tutorial group
     * assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), firstCourseStudent1);
     * // we test with firstCourseStudent3 that a previously unregistered student will be registered to an existing tutorial group
     * assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent3);
     * // we test with firstCourseStudent4 that a previously unregistered student will be registered to a fresh tutorial group
     * var freshTitle = "freshTitle";
     * assertTutorialWithTitleDoesNotExistInDb(freshTitle);
     * assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent4);
     * // we test with firstCourseStudent2 that a previously registered student will be registered to a fresh tutorial group
     * assertUserIsRegisteredInTutorialWithTitle(group2.getTitle(), firstCourseStudent2);
     * // student 1 from existing group1 to existing group 2
     * // + test if identifying just with login works
     * var student1Reg = new TutorialGroupImportDTO(group2.getTitle(), new StudentDTO(firstCourseStudent1.getLogin(), firstCourseStudent1.getFirstName(),
     * firstCourseStudent1.getLastName(), "", ""), null, null,
     * null, null, null);
     * // student 3 to existing group 1
     * // + test if identifying just with registration number works
     * var student3Reg = new TutorialGroupImportDTO(group1.getTitle(),
     * new StudentDTO("", firstCourseStudent3.getFirstName(), firstCourseStudent3.getLastName(), firstCourseStudent3.getRegistrationNumber(), firstCourseStudent3.getEmail()), null,
     * null, null, null, null);
     * // student 4 to fresh tutorial group
     * // + test if identifying with both login and registration number works
     * var student4Reg = new TutorialGroupImportDTO(freshTitle, new StudentDTO(firstCourseStudent4), null, null, null, null, null);
     * // student 2 to fresh tutorial group
     * var student2Reg = new TutorialGroupImportDTO(freshTitle, new StudentDTO(firstCourseStudent2), null, null, null, null, null);
     * var tutorialGroupRegistrations = new ArrayList<TutorialGroupImportDTO>();
     * tutorialGroupRegistrations.add(student1Reg);
     * tutorialGroupRegistrations.add(student3Reg);
     * tutorialGroupRegistrations.add(student4Reg);
     * tutorialGroupRegistrations.add(student2Reg);
     * // when
     * var importResult = sendImportRequest(tutorialGroupRegistrations);
     * // then
     * assertThat(importResult.size()).isEqualTo(4);
     * assertThat(importResult.stream().map(TutorialGroupImportDTO::importSuccessful)).allMatch(status -> status.equals(true));
     * assertThat(importResult.stream().map(TutorialGroupImportDTO::error)).allMatch(Objects::isNull);
     * assertThat(importResult.stream()).containsExactlyInAnyOrder(student1Reg, student3Reg, student4Reg, student2Reg);
     * assertUserIsRegisteredInTutorialWithTitle(group2.getTitle(), firstCourseStudent1);
     * assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), firstCourseStudent3);
     * assertImportedTutorialGroupWithTitleInDB(freshTitle, Set.of(firstCourseStudent4, firstCourseStudent2), firstCourseInstructor1);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void importRegistrations_withoutTitle_shouldNotCreateTutorialGroup() throws Exception {
     * // given
     * var group1Id = tutorialGroupUtilService
     * .createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(), firstCourseTutor1,
     * Set.of(firstCourseStudent1)).getId();
     * var group1 = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group1Id).orElseThrow();
     * assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), firstCourseStudent1);
     * // given
     * var emptyTitle = "";
     * var reg = new TutorialGroupImportDTO(emptyTitle, new StudentDTO(firstCourseStudent1), null, null, null, null, null);
     * assertTutorialWithTitleDoesNotExistInDb(emptyTitle);
     * var tutorialGroupRegistrations = new ArrayList<TutorialGroupImportDTO>();
     * tutorialGroupRegistrations.add(reg);
     * // when
     * var importResult = sendImportRequest(tutorialGroupRegistrations);
     * // then
     * assertThat(importResult).hasSize(1);
     * assertTutorialWithTitleDoesNotExistInDb(emptyTitle);
     * var importResultDTO = importResult.getFirst();
     * assertThat(importResultDTO.importSuccessful()).isFalse();
     * assertThat(importResultDTO.error()).isEqualTo(TutorialGroupImportErrors.NO_TITLE);
     * // firstCourseStudent1 should still be registered in the old tutorial group
     * assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), firstCourseStudent1);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void importRegistrations_titleButNonExistingStudent_shouldStillCreateTutorialGroupButNoRegistration() throws Exception {
     * // given
     * var freshTitle = generateRandomTitle();
     * var reg = new TutorialGroupImportDTO(freshTitle, new StudentDTO("notExisting", "firstName", "firstName1", "", ""), null, null, null, null, null);
     * assertTutorialWithTitleDoesNotExistInDb(freshTitle);
     * var tutorialGroupRegistrations = new ArrayList<TutorialGroupImportDTO>();
     * tutorialGroupRegistrations.add(reg);
     * // when
     * var importResult = sendImportRequest(tutorialGroupRegistrations);
     * // then
     * assertImportedTutorialGroupWithTitleInDB(freshTitle, new HashSet<>(), firstCourseInstructor1);
     * assertThat(importResult).hasSize(1);
     * var importResultDTO = importResult.getFirst();
     * assertThat(importResultDTO.importSuccessful()).isFalse();
     * assertThat(importResultDTO.error()).isEqualTo(TutorialGroupImportErrors.NO_USER_FOUND);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void importRegistrations_titleButSameStudentToMultipleGroups_shouldStillCreateTutorialGroupsButNoRegistration() throws Exception {
     * // given
     * var freshTitle = generateRandomTitle();
     * var freshTitleTwo = generateRandomTitle();
     * // given
     * var group1Id = tutorialGroupUtilService
     * .createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(), firstCourseTutor1,
     * Set.of(firstCourseStudent1)).getId();
     * var group2Id = tutorialGroupUtilService
     * .createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH.name(), firstCourseTutor1,
     * Set.of(firstCourseStudent2)).getId();
     * var group1 = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group1Id).orElseThrow();
     * tutorialGroupRegistrationTestRepository.deleteAllByStudent(firstCourseStudent3);
     * tutorialGroupRegistrationTestRepository.deleteAllByStudent(firstCourseStudent4);
     * var group2 = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(group2Id).orElseThrow();
     * tutorialGroupChannelManagementService.createChannelForTutorialGroup(group1);
     * tutorialGroupChannelManagementService.createChannelForTutorialGroup(group2);
     * assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), firstCourseStudent1);
     * assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent3);
     * var reg1 = new TutorialGroupImportDTO(freshTitle, new StudentDTO(firstCourseStudent1), null, null, null, null, null);
     * var reg2 = new TutorialGroupImportDTO(freshTitleTwo, new StudentDTO(firstCourseStudent1), null, null, null, null, null);
     * var reg3 = new TutorialGroupImportDTO(freshTitle, new StudentDTO(firstCourseStudent3), null, null, null, null, null);
     * var reg4 = new TutorialGroupImportDTO(freshTitleTwo, new StudentDTO(firstCourseStudent3), null, null, null, null, null);
     * assertTutorialWithTitleDoesNotExistInDb(freshTitle);
     * assertTutorialWithTitleDoesNotExistInDb(freshTitleTwo);
     * var tutorialGroupRegistrations = new ArrayList<TutorialGroupImportDTO>();
     * tutorialGroupRegistrations.add(reg1);
     * tutorialGroupRegistrations.add(reg2);
     * tutorialGroupRegistrations.add(reg3);
     * tutorialGroupRegistrations.add(reg4);
     * // when
     * var importResult = sendImportRequest(tutorialGroupRegistrations);
     * // then
     * assertImportedTutorialGroupWithTitleInDB(freshTitle, new HashSet<>(), firstCourseInstructor1);
     * assertThat(importResult).hasSize(4);
     * assertThat(importResult.stream().map(TutorialGroupImportDTO::importSuccessful)).allMatch(status -> status.equals(false));
     * assertThat(importResult.stream().map(TutorialGroupImportDTO::error)).allMatch(TutorialGroupImportErrors.MULTIPLE_REGISTRATIONS::equals);
     * assertThat(importResult.stream()).containsExactlyInAnyOrder(reg1, reg2, reg3, reg4);
     * // should still be registered in the old tutorial group
     * assertUserIsRegisteredInTutorialWithTitle(group1.getTitle(), firstCourseStudent1);
     * assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent3);
     * asserTutorialGroupChannelIsCorrectlyConfigured(group1);
     * asserTutorialGroupChannelIsCorrectlyConfigured(group2);
     * }
     * @Test
     * @WithMockUser(username = "admin", roles = "ADMIN")
     * void testDeleteCourseWithTutorialGroups() throws Exception {
     * var channel = tutorialGroupTestRepository.getTutorialGroupChannel(exampleOneTutorialGroupId).orElseThrow();
     * var post = conversationUtilService.addMessageToConversation(TEST_PREFIX + "firstCourseStudent1", channel);
     * request.delete("/api/core/admin/courses/" + exampleCourseId, HttpStatus.OK);
     * assertThat(courseRepository.findById(exampleCourseId)).isEmpty();
     * assertThat(tutorialGroupTestRepository.findAllByCourseId(exampleCourseId)).isEmpty();
     * assertThat(tutorialGroupTestRepository.getTutorialGroupChannel(exampleOneTutorialGroupId)).isEmpty();
     * assertThat(tutorialGroupTestRepository.getTutorialGroupChannel(exampleTwoTutorialGroupId)).isEmpty();
     * assertThat(tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(exampleCourseId)).isEmpty();
     * assertThat(postRepository.findById(post.getId())).isEmpty();
     * }
     * @Test
     * @WithMockUser(username = "admin", roles = "ADMIN")
     * void testDeleteCourseWithTutorialGroupsAndSessions() throws Exception {
     * // Create a tutorial group with sessions via schedule
     * var tutorialGroupWithSessions = setUpTutorialGroupWithSchedule(exampleCourseId, "firstCourseTutor1");
     * var tutorialGroupId = tutorialGroupWithSessions.getId();
     * // Verify sessions exist before deletion
     * var sessionsBeforeDeletion = tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId);
     * assertThat(sessionsBeforeDeletion).isNotEmpty();
     * // Delete the course
     * request.delete("/api/core/admin/courses/" + exampleCourseId, HttpStatus.OK);
     * // Verify everything is deleted
     * assertThat(courseRepository.findById(exampleCourseId)).isEmpty();
     * assertThat(tutorialGroupTestRepository.findAllByCourseId(exampleCourseId)).isEmpty();
     * assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId)).isEmpty();
     * assertThat(tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(exampleCourseId)).isEmpty();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void testDeleteTutorialGroupWithSessions() throws Exception {
     * // Create a tutorial group with sessions via schedule
     * var tutorialGroupWithSessions = setUpTutorialGroupWithSchedule(exampleCourseId, "firstCourseTutor1");
     * var tutorialGroupId = tutorialGroupWithSessions.getId();
     * // Verify sessions exist before deletion
     * var sessionsBeforeDeletion = tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId);
     * assertThat(sessionsBeforeDeletion).isNotEmpty();
     * // Delete the tutorial group
     * request.delete(getTutorialGroupsPath(exampleCourseId, tutorialGroupId), HttpStatus.NO_CONTENT);
     * // Verify tutorial group and sessions are deleted
     * assertThat(tutorialGroupTestRepository.findById(tutorialGroupId)).isEmpty();
     * assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId)).isEmpty();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void importRegistrations_withAdditionalHeaders_shouldCreateTutorialGroupWithDetails() throws Exception {
     * var freshTitle = "freshTitle";
     * var student1Reg = new TutorialGroupImportDTO(freshTitle, new StudentDTO(firstCourseStudent1), "Main Campus", 30, "German", "Some info", true);
     * assertTutorialWithTitleDoesNotExistInDb(freshTitle);
     * var tutorialGroupRegistrations = new ArrayList<TutorialGroupImportDTO>();
     * tutorialGroupRegistrations.add(student1Reg);
     * var importResult = sendImportRequest(tutorialGroupRegistrations);
     * assertThat(importResult).hasSize(1);
     * assertThat(importResult.getFirst().importSuccessful()).isTrue();
     * assertThat(importResult.getFirst().error()).isNull();
     * assertTutorialGroupWithTitleInDB(freshTitle, Set.of(firstCourseStudent1), true, "Some info", 30, "Main Campus", "German", firstCourseInstructor1);
     * }
     * private List<TutorialGroupImportDTO> sendImportRequest(List<TutorialGroupImportDTO> tutorialGroupRegistrations) throws Exception {
     * return request.postListWithResponseBody(getTutorialGroupsPath(exampleCourseId) + "/import", tutorialGroupRegistrations, TutorialGroupImportDTO.class, HttpStatus.OK);
     * }
     * private void assertTutorialWithTitleDoesNotExistInDb(String title) {
     * assertThat(tutorialGroupTestRepository.existsByTitleAndCourseId(title, exampleCourseId)).isFalse();
     * }
     * private void assertTutorialGroupWithTitleExistsInDb(String title) {
     * assertThat(tutorialGroupTestRepository.existsByTitleAndCourseId(title, exampleCourseId)).isTrue();
     * asserTutorialGroupChannelIsCorrectlyConfigured(
     * tutorialGroupTestRepository.findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(title, exampleCourseId).orElseThrow());
     * }
     * private void assertUserIsRegisteredInTutorialWithTitle(String expectedTitle, User expectedStudent) {
     * assertThat(tutorialGroupRegistrationTestRepository.existsByTutorialGroupTitleAndStudentAndType(expectedTitle, expectedStudent, INSTRUCTOR_REGISTRATION)).isTrue();
     * }
     * private void assertUserIsNotRegisteredInATutorialGroup(User expectedStudent) {
     * assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(expectedStudent, exampleCourseId, INSTRUCTOR_REGISTRATION)).isZero();
     * }
     * private void assertImportedTutorialGroupWithTitleInDB(String expectedTitle, Set<User> expectedRegisteredStudents, User requestingUser) {
     * assertTutorialGroupWithTitleInDB(expectedTitle, expectedRegisteredStudents, false, null, 1, "Campus", Language.GERMAN.name(), requestingUser);
     * }
     * private void assertTutorialGroupWithTitleInDB(String expectedTitle, Set<User> expectedRegisteredStudents, Boolean isOnline, String additionalInformation, Integer capacity,
     * String campus, String language, User teachingAssistant) {
     * var tutorialGroupOptional = tutorialGroupTestRepository.findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(expectedTitle, exampleCourseId);
     * assertThat(tutorialGroupOptional).isPresent();
     * var tutorialGroup = tutorialGroupOptional.get();
     * assertThat(tutorialGroup.getIsOnline()).isEqualTo(isOnline);
     * assertThat(tutorialGroup.getAdditionalInformation()).isEqualTo(additionalInformation);
     * assertThat(tutorialGroup.getCapacity()).isEqualTo(capacity);
     * assertThat(tutorialGroup.getCampus()).isEqualTo(campus);
     * assertThat(tutorialGroup.getLanguage()).isEqualTo(language);
     * assertThat(tutorialGroup.getTeachingAssistant()).isEqualTo(teachingAssistant);
     * assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).containsExactlyInAnyOrderElementsOf(expectedRegisteredStudents);
     * // assert that all registrations are instructor registrations (always the case for import)
     * assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getType))
     * .allMatch(regType -> regType.equals(TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
     * asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);
     * }
     * private void oneOfCoursePrivateInfoHiddenTest(boolean loadFromService, String userLogin, boolean isAdminOrInstructor) throws Exception {
     * var tutorialGroup = getTutorialGroupOfExampleCourse(loadFromService, userLogin, isAdminOrInstructor);
     * assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
     * verifyPrivateInformationIsHidden(tutorialGroup);
     * }
     * private void oneOfCoursePrivateInfoShownTest(boolean loadFromService, String userLogin, boolean isAdminOrInstructor) throws Exception {
     * var tutorialGroup = getTutorialGroupOfExampleCourse(loadFromService, userLogin, isAdminOrInstructor);
     * assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
     * verifyPrivateInformationIsShown(tutorialGroup);
     * }
     * private TutorialGroup getTutorialGroupOfExampleCourse(boolean loadFromService, String userLogin, boolean isAdminOrInstructor) throws Exception {
     * if (loadFromService) {
     * var user = userRepository.findOneByLogin(userLogin).orElseThrow();
     * var course = courseRepository.findById(exampleCourseId).orElseThrow();
     * return tutorialGroupService.getOneOfCourse(course, exampleOneTutorialGroupId, user, isAdminOrInstructor);
     * } else {
     * return request.get("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.OK, TutorialGroup.class);
     * }
     * }
     * private void registerStudentAllowedTest() throws Exception {
     * request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/register/" + firstCourseStudent3.getLogin(), HttpStatus.NO_CONTENT,
     * new LinkedMultiValueMap<>());
     * var tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).contains(firstCourseStudent3);
     * asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);
     * // remove registration again
     * var registration = tutorialGroupRegistrationTestRepository.findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(tutorialGroup, firstCourseStudent3,
     * INSTRUCTOR_REGISTRATION)
     * .orElseThrow();
     * tutorialGroupRegistrationTestRepository.delete(registration);
     * }
     * private void registerStudentForbiddenTest() throws Exception {
     * request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/register/" + firstCourseStudent3.getLogin(), HttpStatus.FORBIDDEN,
     * new LinkedMultiValueMap<>());
     * }
     * private void deregisterStudentAllowedTest() throws Exception {
     * request.delete(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/deregister/" + firstCourseStudent1.getLogin(), HttpStatus.NO_CONTENT);
     * TutorialGroup tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessions(exampleOneTutorialGroupId).orElseThrow();
     * assertThat(tutorialGroup.getRegistrations().stream().map(TutorialGroupRegistration::getStudent)).doesNotContain(firstCourseStudent1);
     * asserTutorialGroupChannelIsCorrectlyConfigured(tutorialGroup);
     * // reset registration
     * var registration = new TutorialGroupRegistration();
     * registration.setStudent(firstCourseStudent1);
     * registration.setTutorialGroup(tutorialGroup);
     * registration.setType(INSTRUCTOR_REGISTRATION);
     * tutorialGroupRegistrationTestRepository.save(registration);
     * }
     * private void deregisterStudentForbiddenTest() throws Exception {
     * request.delete(getTutorialGroupsPath(exampleCourseId, exampleOneTutorialGroupId) + "/deregister/" + firstCourseStudent2.getLogin(), HttpStatus.FORBIDDEN);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void testExportTutorialGroupsToCSV() throws Exception {
     * // when
     * var params = new LinkedMultiValueMap<String, String>();
     * params.add("fields", "ID,Title,Campus,Language");
     * String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/csv").queryParams(params).toUriString();
     * String csvContent = request.get(url, HttpStatus.OK, String.class);
     * // then
     * assertThat(csvContent).contains("ID,Title,Campus,Language");
     * assertThat(csvContent).contains("LoremIpsum1");
     * assertThat(csvContent).contains("LoremIpsum2");
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void shouldSendTutorialGroupAssignedNotificationWhenTutorIsAssignedAndFeatureEnabled() throws Exception {
     * User firstCourseTutor2 = userRepository.findOneByLogin(testPrefix + "firstCourseTutor2").orElseThrow();
     * TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "Campus", 10, false, "Test location",
     * Language.ENGLISH.name(), firstCourseTutor2, Set.of());
     * final var updatedTutorialGroup = copyTutorialGroup(tutorialGroup, firstCourseTutor1);
     * TutorialGroupUpdateDTO updateDTO = new TutorialGroupUpdateDTO(updatedTutorialGroup, "Update notification text", true);
     * request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), updateDTO, TutorialGroup.class, HttpStatus.OK);
     * await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
     * List<CourseNotification> notifications = courseNotificationRepository.findAll();
     * boolean hasTutorialGroupAssignedNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(exampleCourseId))
     * .anyMatch(notification -> notification.getType() == 21);
     * assertThat(hasTutorialGroupAssignedNotification).isTrue();
     * });
     * }
     * private @NonNull TutorialGroup copyTutorialGroup(TutorialGroup tutorialGroup, User firstCourseTutor1) {
     * TutorialGroup updatedTutorialGroup = new TutorialGroup();
     * updatedTutorialGroup.setId(tutorialGroup.getId());
     * updatedTutorialGroup.setTitle(tutorialGroup.getTitle());
     * updatedTutorialGroup.setTeachingAssistant(firstCourseTutor1);
     * updatedTutorialGroup.setCapacity(tutorialGroup.getCapacity());
     * updatedTutorialGroup.setCampus(tutorialGroup.getCampus());
     * updatedTutorialGroup.setIsOnline(tutorialGroup.getIsOnline());
     * updatedTutorialGroup.setLanguage(tutorialGroup.getLanguage());
     * return updatedTutorialGroup;
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void shouldSendTutorialGroupUnassignedNotificationWhenTutorIsUnassignedAndFeatureEnabled() throws Exception {
     * TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "Campus", 10, false, "Test location",
     * Language.ENGLISH.name(), firstCourseTutor1, Set.of());
     * final var updatedTutorialGroup = copyTutorialGroup(tutorialGroup, null);
     * TutorialGroupUpdateDTO updateDTO = new TutorialGroupUpdateDTO(updatedTutorialGroup, "Update notification text", true);
     * request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), updateDTO, TutorialGroup.class, HttpStatus.OK);
     * await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
     * List<CourseNotification> notifications = courseNotificationRepository.findAll();
     * boolean hasTutorialGroupUnassignedNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(exampleCourseId))
     * .anyMatch(notification -> notification.getType() == 25);
     * assertThat(hasTutorialGroupUnassignedNotification).isTrue();
     * });
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void shouldSendRegisteredToTutorialGroupNotificationWhenStudentIsRegisteredAndFeatureEnabled() throws Exception {
     * TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "Campus", 10, false, "Test location",
     * Language.ENGLISH.name(), firstCourseTutor1, Set.of());
     * request.postWithoutResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()) + "/register/" + firstCourseStudent3.getLogin(), HttpStatus.NO_CONTENT,
     * new LinkedMultiValueMap<>());
     * await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
     * List<CourseNotification> notifications = courseNotificationRepository.findAll();
     * boolean hasRegisteredToTutorialGroupNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(exampleCourseId))
     * .anyMatch(notification -> notification.getType() == 23);
     * assertThat(hasRegisteredToTutorialGroupNotification).isTrue();
     * });
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "firstCourseInstructor1", roles = "INSTRUCTOR")
     * void shouldSendTutorialGroupDeletedNotificationWhenTutorialGroupIsDeletedAndFeatureEnabled() throws Exception {
     * TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "Campus", 10, false, "Test location",
     * Language.ENGLISH.name(), firstCourseTutor1, Set.of(firstCourseStudent1, firstCourseStudent2));
     * request.delete(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), HttpStatus.NO_CONTENT);
     * await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
     * List<CourseNotification> notifications = courseNotificationRepository.findAll();
     * boolean hasTutorialGroupDeletedNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(exampleCourseId))
     * .anyMatch(notification -> notification.getType() == 22);
     * assertThat(hasTutorialGroupDeletedNotification).isTrue();
     * });
     * }
     */

}
