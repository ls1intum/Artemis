package de.tum.cit.aet.artemis.tutorialgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.OneToOneChatTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.CreateOrUpdateTutorialGroupRequestDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupDetailDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupExportDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupImportDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupScheduleDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupStudentDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupStudentImportDataDTO;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupImportErrors;

class TutorialGroupIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    private static final String TEST_PREFIX = "tutorialgroup";

    private User firstCourseTutor1;

    private User firstCourseTutor2;

    private User firstCourseStudent1;

    private User firstCourseStudent2;

    private User firstCourseStudent3;

    private User firstCourseStudent4;

    private TutorialGroup firstCourseTutorialGroup1;

    private Channel firstCourseChannel1;

    private List<TutorialGroupSession> firstCourseTutorialGroupSessions1;

    private TutorialGroup firstCourseTutorialGroup2;

    private TutorialGroupSession firstCourseTutorialGroupTwoSession;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private OneToOneChatTestRepository oneToOneChatRepository;

    @BeforeEach
    @Override
    void setupTestScenario() {
        super.setupTestScenario();

        UsersInCourseOne usersInCourseOne = createAndSaveUsersInCourseOneData();
        firstCourseTutor1 = usersInCourseOne.tutor1();
        firstCourseTutor2 = usersInCourseOne.tutor2();
        firstCourseStudent1 = usersInCourseOne.student1();
        firstCourseStudent2 = usersInCourseOne.student2();
        firstCourseStudent3 = usersInCourseOne.student3();
        firstCourseStudent4 = usersInCourseOne.student4();

        TutorialGroupOneInCourseOneData tutorialGroupOneInCourseOneData = createAndSaveTutorialGroupOneInCourseOneData(firstCourseTutor1, firstCourseStudent1);
        firstCourseTutorialGroup1 = tutorialGroupOneInCourseOneData.group();
        firstCourseChannel1 = tutorialGroupOneInCourseOneData.channel();
        firstCourseTutorialGroupSessions1 = tutorialGroupOneInCourseOneData.sessions();

        TutorialGroupTwoInCourseOneData tutorialGroupTwoInCourseOneData = createAndSaveTutorialGroupTwoInCourseOneData(firstCourseTutor2, firstCourseStudent2);
        firstCourseTutorialGroup2 = tutorialGroupTwoInCourseOneData.group();
        firstCourseTutorialGroupTwoSession = tutorialGroupTwoInCourseOneData.session();

        createAndSaveUsersInCourseTwoData();
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
        var languageValues = request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/language-values", HttpStatus.OK, String.class);
        assertThat(languageValues).containsExactlyInAnyOrder(Language.ENGLISH.name(), Language.GERMAN.name());
    }

    @Test
    @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
    void getUniqueLanguageValues_asTutor_shouldReturnForbidden() throws Exception {
        request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/language-values", HttpStatus.FORBIDDEN, String.class);
    }

    @Nested
    class GetTutorialGroupsTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void getTutorialGroupsForCourse_asStudent_shouldHidePrivateInformation() throws Exception {
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
            assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(Collectors.toSet())).contains(firstCourseTutorialGroup1.getId(),
                    firstCourseTutorialGroup2.getId());
            for (TutorialGroup tutorialGroup : tutorialGroupsOfCourse) {
                verifyPrivateInformationIsHidden(tutorialGroup);
            }
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getTutorialGroupsForCourse_asEditor_shouldHidePrivateInformation() throws Exception {
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
            assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(Collectors.toSet())).contains(firstCourseTutorialGroup1.getId(),
                    firstCourseTutorialGroup2.getId());
            for (var tutorialGroup : tutorialGroupsOfCourse) { // private information hidden
                verifyPrivateInformationIsHidden(tutorialGroup);
            }
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void getTutorialGroupsForCourse_asTutorOfOneGroup_shouldShowPrivateInformationForOwnGroup() throws Exception {
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
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
            var tutorialGroupsOfCourse = request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
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
            String url = "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId();
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnInternalServerErrorIfCourseHasNoTimezone() throws Exception {
            Course course = courseRepository.findById(exampleCourseId).orElseThrow();
            course.setTimeZone(null);
            courseRepository.save(course);
            String url = "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId();
            request.get(url, HttpStatus.INTERNAL_SERVER_ERROR, String.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnNotFoundIfTutorialGroupDoesNotExist() throws Exception {
            String nonExistentTutorialGroupId = "-1";
            String url = "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + nonExistentTutorialGroupId;
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
            TutorialGroupSession individualSession = firstCourseTutorialGroupSessions1.get(5);

            String url = "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId();
            TutorialGroupDetailDataDTO groupDTO = request.get(url, HttpStatus.OK, TutorialGroupDetailDataDTO.class);
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
            assertThat(sessionDTOs).hasSize(6);
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

            TutorialGroupSessionDTO sixthSessionDTO = sessionDTOs.get(5);
            assertGroupDTOHasCorrectFields(sixthSessionDTO, individualSession);
            assertGroupDTOHasCorrectFlags(sixthSessionDTO, false, false, true, false);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnResponseWithoutTutorChatIdIfNoChatExists() throws Exception {
            TutorialGroup group = buildTutorialGroupWithoutSchedule("firstcoursetutor1");
            tutorialGroupTestRepository.save(group);
            TutorialGroupSchedule schedule = buildExampleSchedule(FIRST_AUGUST_MONDAY_00_00.toLocalDate(), SECOND_AUGUST_MONDAY_00_00.toLocalDate());
            schedule.setTutorialGroup(group);
            tutorialGroupScheduleTestRepository.save(schedule);

            String url = "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + group.getId();
            TutorialGroupDetailDataDTO response = request.get(url, HttpStatus.OK, TutorialGroupDetailDataDTO.class);
            assertThat(response.tutorChatId()).isNull();
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_STUDENT1_LOGIN, roles = "USER")
        void shouldReturnResponseWithTutorChatIdIfChatExists() throws Exception {
            TutorialGroup group = buildTutorialGroupWithoutSchedule("firstcoursetutor1");
            tutorialGroupTestRepository.save(group);
            TutorialGroupSchedule schedule = buildExampleSchedule(FIRST_AUGUST_MONDAY_00_00.toLocalDate(), SECOND_AUGUST_MONDAY_00_00.toLocalDate());
            schedule.setTutorialGroup(group);
            tutorialGroupScheduleTestRepository.save(schedule);

            Course course = courseRepository.findById(exampleCourseId).orElseThrow();
            var oneToOneChat = new OneToOneChat();
            oneToOneChat.setCourse(course);
            oneToOneChat.setCreator(firstCourseStudent1);
            oneToOneChatRepository.save(oneToOneChat);

            ConversationParticipant participationOfUserA = ConversationParticipant.createWithDefaultValues(firstCourseStudent1, oneToOneChat);
            ConversationParticipant participationOfUserB = ConversationParticipant.createWithDefaultValues(firstCourseTutor1, oneToOneChat);
            conversationParticipantRepository.saveAll(List.of(participationOfUserA, participationOfUserB));

            String url = "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + group.getId();
            TutorialGroupDetailDataDTO response = request.get(url, HttpStatus.OK, TutorialGroupDetailDataDTO.class);
            assertThat(response.tutorChatId()).isEqualTo(oneToOneChat.getId());
        }
    }

    @Nested
    class GetTutorialGroupScheduleTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getTutorialGroupSchedule_withSchedule_shouldReturnSchedule() throws Exception {
            var response = request.get("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/schedule", HttpStatus.OK,
                    TutorialGroupScheduleDTO.class);

            assertThat(response.firstSessionStart()).isEqualTo(FIRST_AUGUST_MONDAY_13_00);
            assertThat(response.firstSessionEnd()).isEqualTo(FIRST_AUGUST_MONDAY_14_00);
            assertThat(response.repetitionFrequency()).isEqualTo(1);
            assertThat(response.tutorialPeriodEnd()).isEqualTo(FIFTH_AUGUST_MONDAY);
            assertThat(response.location()).isEqualTo("01.05.13");
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getTutorialGroupSchedule_withoutSchedule_shouldReturnNoContent() throws Exception {
            request.get("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup2.getId() + "/schedule", HttpStatus.NO_CONTENT,
                    String.class);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getTutorialGroupSchedule_wrongCourse_shouldReturnNotFound() throws Exception {
            request.get("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/schedule", HttpStatus.NOT_FOUND,
                    String.class);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getTutorialGroupSchedule_asEditorNotInCourse_shouldReturnForbidden() throws Exception {
            request.get("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/schedule", HttpStatus.FORBIDDEN,
                    String.class);
        }
    }

    @Nested
    class CreateTutorialGroupTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void create_asEditorWithScheduleSet_shouldCreateTutorialGroup() throws Exception {
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO = new TutorialGroupScheduleDTO(FIRST_AUGUST_MONDAY_10_00, FIRST_AUGUST_MONDAY_12_00, 1, FOURTH_AUGUST_MONDAY,
                    "01.03.12");
            CreateOrUpdateTutorialGroupRequestDTO createAndUpdateTutorialGroupDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mo 10", firstCourseTutor1.getId(), "English",
                    false, "Garching", 10, "Bring you machine.", tutorialGroupScheduleDTO);

            Long tutorialGroupId = request.postWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, Long.class,
                    HttpStatus.OK);

            TutorialGroup tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(tutorialGroupId);
            assertTutorialGroupHasExpectedProperties(tutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor1, 0);

            TutorialGroupSchedule tutorialGroupSchedule = tutorialGroup.getTutorialGroupSchedule();
            assertTutorialGroupScheduleHasExpectedProperties(tutorialGroupSchedule, tutorialGroupScheduleDTO);

            List<TutorialGroupSession> sessions = tutorialGroup.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart)).toList();
            assertTutorialGroupSessionsConformingToScheduleHaveExpectedProperties(sessions, tutorialGroup, tutorialGroupScheduleDTO);

            Channel channel = tutorialGroup.getTutorialGroupChannel();
            assertTutorialGroupChannelHasExpectedProperties(channel, tutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void create_asEditorWithScheduleSetCoveringDSTChange_shouldCreateTutorialGroup() throws Exception {
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO = new TutorialGroupScheduleDTO(MONDAY_BEFORE_DST_SWITCH_10_00, MONDAY_BEFORE_DST_SWITCH_12_00, 1,
                    MONDAY_AFTER_DST_SWITCH, "01.03.12");
            CreateOrUpdateTutorialGroupRequestDTO createAndUpdateTutorialGroupDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mo DST", firstCourseTutor1.getId(), "English",
                    false, "Garching", 10, "Bring you machine.", tutorialGroupScheduleDTO);

            Long tutorialGroupId = request.postWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, Long.class,
                    HttpStatus.OK);

            TutorialGroup tutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(tutorialGroupId);
            assertTutorialGroupHasExpectedProperties(tutorialGroup, createAndUpdateTutorialGroupDTO, firstCourseTutor1, 0);

            TutorialGroupSchedule tutorialGroupSchedule = tutorialGroup.getTutorialGroupSchedule();
            assertTutorialGroupScheduleHasExpectedProperties(tutorialGroupSchedule, tutorialGroupScheduleDTO);

            List<TutorialGroupSession> sessions = tutorialGroup.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart)).toList();
            assertThat(sessions).hasSize(2);
            ZoneId timeZone = ZoneId.of(exampleTimeZone);
            TutorialGroupSession firstSession = sessions.getFirst();
            assertThat(firstSession.getStart().withZoneSameInstant(timeZone).toLocalDateTime()).isEqualTo(MONDAY_BEFORE_DST_SWITCH_10_00);
            assertThat(firstSession.getEnd().withZoneSameInstant(timeZone).toLocalDateTime()).isEqualTo(MONDAY_BEFORE_DST_SWITCH_12_00);
            TutorialGroupSession secondSession = sessions.get(1);
            assertThat(secondSession.getStart().withZoneSameInstant(timeZone).toLocalDateTime()).isEqualTo(MONDAY_AFTER_DST_SWITCH_10_00);
            assertThat(secondSession.getEnd().withZoneSameInstant(timeZone).toLocalDateTime()).isEqualTo(MONDAY_AFTER_DST_SWITCH_12_00);

            Channel channel = tutorialGroup.getTutorialGroupChannel();
            assertTutorialGroupChannelHasExpectedProperties(channel, tutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void create_asEditorWithoutScheduleSet_shouldCreateTutorialGroup() throws Exception {
            CreateOrUpdateTutorialGroupRequestDTO createAndUpdateTutorialGroupDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mo 10", firstCourseTutor1.getId(), "English",
                    false, "Garching", 10, "Bring you machine.", null);

            Long tutorialGroupId = request.postWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, Long.class,
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
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void create_asEditorWithTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
            var existingTitle = tutorialGroupTestRepository.findById(firstCourseTutorialGroup1.getId()).orElseThrow().getTitle();
            CreateOrUpdateTutorialGroupRequestDTO createAndUpdateTutorialGroupDTO = new CreateOrUpdateTutorialGroupRequestDTO(existingTitle, firstCourseTutor1.getId(), "English",
                    false, "Garching", 10, "Bring you machine.", null);

            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", createAndUpdateTutorialGroupDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void create_asEditorWithNoTutorialGroupsConfiguration_shouldReturnBadRequest() throws Exception {
            Course course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(exampleCourseId);
            TutorialGroupsConfiguration configuration = course.getTutorialGroupsConfiguration();
            course.setTutorialGroupsConfiguration(null);
            configuration.setCourse(null);
            courseRepository.save(course);
            tutorialGroupsConfigurationRepository.delete(configuration);

            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mo 10", firstCourseTutor1.getId(),
                    "English", false, "Garching", 10, "Bring you machine.", null);

            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", createOrUpdateTutorialGroupRequestDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void create_asEditorOfOtherCourse_shouldReturnForbidden() throws Exception {
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mo 10", firstCourseTutor1.getId(),
                    "English", false, "Garching", 10, "Bring you machine.", null);

            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups", createOrUpdateTutorialGroupRequestDTO, HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class UpdateTutorialGroupTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithOldAndNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            TutorialGroupSession individualSession = firstCourseTutorialGroupSessions1.get(5);
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO = new TutorialGroupScheduleDTO(FIRST_AUGUST_MONDAY_10_00, FIRST_AUGUST_MONDAY_12_00, 1, FOURTH_AUGUST_MONDAY,
                    "01.03.12");
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mo 10 Updated", firstCourseTutor2.getId(),
                    "English", false, "Garching", 10, "Bring your machine.", tutorialGroupScheduleDTO);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup1.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createOrUpdateTutorialGroupRequestDTO, firstCourseTutor2, 1);

            TutorialGroupSchedule updatedTutorialGroupSchedule = updatedTutorialGroup.getTutorialGroupSchedule();
            assertTutorialGroupScheduleHasExpectedProperties(updatedTutorialGroupSchedule, tutorialGroupScheduleDTO);

            List<TutorialGroupSession> updatedSessions = updatedTutorialGroup.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart))
                    .toList();
            assertThat(updatedSessions).contains(individualSession);
            updatedSessions = updatedSessions.stream().filter(session -> !session.equals(individualSession)).toList();
            assertTutorialGroupSessionsConformingToScheduleHaveExpectedProperties(updatedSessions, updatedTutorialGroup, tutorialGroupScheduleDTO);

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup1.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor2);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithOldWithoutNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            TutorialGroupSession individualSession = firstCourseTutorialGroupSessions1.get(5);
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mo 13 Updated", firstCourseTutor2.getId(),
                    "English", true, "Munich", 12, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup1.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createOrUpdateTutorialGroupRequestDTO, firstCourseTutor2, 1);

            assertThat(updatedTutorialGroup.getTutorialGroupSchedule()).isNull();

            Set<TutorialGroupSession> sessions = updatedTutorialGroup.getTutorialGroupSessions();
            assertThat(sessions).containsExactly(individualSession);

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup1.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor2);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithoutOldWithNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            TutorialGroupScheduleDTO tutorialGroupScheduleDTO = new TutorialGroupScheduleDTO(FIRST_AUGUST_MONDAY_10_00, FIRST_AUGUST_MONDAY_12_00, 1, FOURTH_AUGUST_MONDAY,
                    "01.03.12");
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Tue 13 Updated", firstCourseTutor1.getId(),
                    "English", false, "Garching", 25, "Bring your machine.", tutorialGroupScheduleDTO);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup2.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup2.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createOrUpdateTutorialGroupRequestDTO, firstCourseTutor1, 1);

            TutorialGroupSchedule updatedTutorialGroupSchedule = updatedTutorialGroup.getTutorialGroupSchedule();
            assertTutorialGroupScheduleHasExpectedProperties(updatedTutorialGroupSchedule, tutorialGroupScheduleDTO);

            List<TutorialGroupSession> updatedSessions = updatedTutorialGroup.getTutorialGroupSessions().stream().sorted(Comparator.comparing(TutorialGroupSession::getStart))
                    .toList();
            assertThat(updatedSessions).contains(firstCourseTutorialGroupTwoSession);
            updatedSessions = updatedSessions.stream().filter(session -> !session.equals(firstCourseTutorialGroupTwoSession)).toList();
            assertTutorialGroupSessionsConformingToScheduleHaveExpectedProperties(updatedSessions, updatedTutorialGroup, tutorialGroupScheduleDTO);

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup2.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithoutOldAndNewSchedule_shouldUpdateTutorialGroup() throws Exception {
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Tue 15", firstCourseTutor1.getId(),
                    "English", false, "Garching", 15, "Updated information without schedule.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup2.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.NO_CONTENT);

            TutorialGroup updatedTutorialGroup = tutorialGroupTestRepository
                    .findByIdWithTeachingAssistantAndRegistrationsAndScheduleAndSessionsElseThrow(firstCourseTutorialGroup2.getId());
            assertTutorialGroupHasExpectedProperties(updatedTutorialGroup, createOrUpdateTutorialGroupRequestDTO, firstCourseTutor1, 1);

            assertThat(updatedTutorialGroup.getTutorialGroupSchedule()).isNull();

            Set<TutorialGroupSession> sessions = updatedTutorialGroup.getTutorialGroupSessions();
            assertThat(sessions).containsExactly(firstCourseTutorialGroupTwoSession);

            Channel updatedChannel = tutorialGroupTestRepository.getTutorialGroupChannel(firstCourseTutorialGroup2.getId()).orElseThrow();
            assertTutorialGroupChannelHasExpectedProperties(updatedChannel, updatedTutorialGroup, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithoutOldTutorialGroup_shouldReturnNotFound() throws Exception {
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mon 15", firstCourseTutor1.getId(),
                    "English", false, "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1", createOrUpdateTutorialGroupRequestDTO, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithoutMatchingCourse_shouldReturnBadRequest() throws Exception {
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mon 15", firstCourseTutor1.getId(),
                    "English", false, "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithNonExistingNewTutor_shouldReturnNotFound() throws Exception {
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mon 15", -1L, "English", false, "Garching",
                    15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithoutTutorialGroupsConfiguration_shouldReturnBadRequest() throws Exception {
            Course course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(exampleCourseId);
            TutorialGroupsConfiguration configuration = course.getTutorialGroupsConfiguration();
            course.setTutorialGroupsConfiguration(null);
            configuration.setCourse(null);
            courseRepository.save(course);
            tutorialGroupsConfigurationRepository.delete(configuration);

            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mon 15", firstCourseTutor1.getId(),
                    "English", false, "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorWithoutCourseTimezone_shouldReturnBadRequest() throws Exception {
            Course course = courseRepository.findByIdElseThrow(exampleCourseId);
            course.setTimeZone(null);
            courseRepository.save(course);

            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mon 15", firstCourseTutor1.getId(),
                    "English", false, "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void update_asEditorOfOtherCourse_shouldReturnForbidden() throws Exception {
            CreateOrUpdateTutorialGroupRequestDTO createOrUpdateTutorialGroupRequestDTO = new CreateOrUpdateTutorialGroupRequestDTO("TG Mon 15", firstCourseTutor1.getId(),
                    "English", false, "Garching", 15, "Updated information.", null);

            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(),
                    createOrUpdateTutorialGroupRequestDTO, HttpStatus.FORBIDDEN);
        }
    }

    private void assertTutorialGroupHasExpectedProperties(TutorialGroup tutorialGroup, CreateOrUpdateTutorialGroupRequestDTO tutorialGroupDTO, User expectedTutor,
            int expectedRegistrationCount) {
        assertThat(tutorialGroup.getCourse().getId()).isEqualTo(exampleCourseId);
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

    private void assertTutorialGroupSessionsConformingToScheduleHaveExpectedProperties(List<TutorialGroupSession> sessions, TutorialGroup tutorialGroup,
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
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void delete_asInstructor_shouldDeleteTutorialGroup() throws Exception {
            Long tutorialGroupId = firstCourseTutorialGroup1.getId();

            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroupId, HttpStatus.NO_CONTENT);

            assertThat(tutorialGroupTestRepository.findById(tutorialGroupId)).isEmpty();
            assertThat(tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroupId)).isEmpty();
            assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId)).isEmpty();
            assertThat(tutorialGroupTestRepository.getTutorialGroupChannel(tutorialGroupId)).isEmpty();
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void delete_asInstructorWithNonExistingGroup_shouldReturnNotFound() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1", HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void delete_asInstructorWithNonMatchingCourse_shouldReturnBadRequest() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void delete_asInstructorOfOtherCourse_shouldReturnForbidden() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId(), HttpStatus.FORBIDDEN);
        }

    }

    @Nested
    class BatchRegisterStudentsTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void batchRegister_asTutorOfGroup_shouldReturnNoContent() throws Exception {
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/batch-register",
                    List.of(FIRST_COURSE_STUDENT3_LOGIN, FIRST_COURSE_STUDENT4_LOGIN), HttpStatus.NO_CONTENT);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent3, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);
            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent4, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);

            var channelParticipants = conversationParticipantRepository.findConversationParticipantsByConversationId(firstCourseChannel1.getId()).stream()
                    .map(ConversationParticipant::getUser).collect(Collectors.toSet());
            assertThat(channelParticipants).contains(firstCourseStudent1, firstCourseStudent3, firstCourseStudent4, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void batchRegister_asEditorOfCourse_shouldReturnNoContent() throws Exception {
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/batch-register",
                    List.of(FIRST_COURSE_STUDENT3_LOGIN, FIRST_COURSE_STUDENT4_LOGIN), HttpStatus.NO_CONTENT);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent3, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);
            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent4, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);

            var channelParticipants = conversationParticipantRepository.findConversationParticipantsByConversationId(firstCourseChannel1.getId()).stream()
                    .map(ConversationParticipant::getUser).collect(Collectors.toSet());
            assertThat(channelParticipants).contains(firstCourseStudent1, firstCourseStudent3, firstCourseStudent4, firstCourseTutor1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void batchRegister_asTutorOfOtherGroup_shouldReturnForbidden() throws Exception {
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/batch-register",
                    List.of(FIRST_COURSE_STUDENT3_LOGIN, FIRST_COURSE_STUDENT4_LOGIN), HttpStatus.FORBIDDEN);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent3, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isZero();
            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent4, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isZero();
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void batchRegister_asEditorOfOtherCourse_shouldReturnForbidden() throws Exception {
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/batch-register",
                    List.of(FIRST_COURSE_STUDENT3_LOGIN, FIRST_COURSE_STUDENT4_LOGIN), HttpStatus.FORBIDDEN);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent3, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isZero();
            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent4, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isZero();
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void batchRegister_asTutorWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/batch-register",
                    List.of(FIRST_COURSE_STUDENT3_LOGIN, FIRST_COURSE_STUDENT4_LOGIN), HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void batchRegister_asTutorWithoutMatchingCourse_shouldReturnBadRequest() throws Exception {
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/batch-register",
                    List.of(FIRST_COURSE_STUDENT3_LOGIN, FIRST_COURSE_STUDENT4_LOGIN), HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class DeregisterStudentTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void deregisterStudent_asTutorOfGroup_shouldReturnNoContent() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/deregister/" + FIRST_COURSE_STUDENT1_LOGIN,
                    HttpStatus.NO_CONTENT);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent1, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isZero();

            var channelParticipants = conversationParticipantRepository.findConversationParticipantsByConversationId(firstCourseChannel1.getId()).stream()
                    .map(ConversationParticipant::getUser).collect(Collectors.toSet());
            assertThat(channelParticipants).contains(firstCourseTutor1);
            assertThat(channelParticipants).doesNotContain(firstCourseStudent1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void deregisterStudent_asEditorOfCourse_shouldReturnNoContent() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/deregister/" + FIRST_COURSE_STUDENT1_LOGIN,
                    HttpStatus.NO_CONTENT);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent1, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isZero();

            var channelParticipants = conversationParticipantRepository.findConversationParticipantsByConversationId(firstCourseChannel1.getId()).stream()
                    .map(ConversationParticipant::getUser).collect(Collectors.toSet());
            assertThat(channelParticipants).contains(firstCourseTutor1);
            assertThat(channelParticipants).doesNotContain(firstCourseStudent1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void deregisterStudent_asTutorOfOtherGroup_shouldReturnForbidden() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/deregister/" + FIRST_COURSE_STUDENT1_LOGIN,
                    HttpStatus.FORBIDDEN);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent1, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void deregisterStudent_asEditorOfOtherCourse_shouldReturnForbidden() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/deregister/" + FIRST_COURSE_STUDENT1_LOGIN,
                    HttpStatus.FORBIDDEN);

            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent1, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void deregisterStudent_asTutorWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/deregister/" + FIRST_COURSE_STUDENT1_LOGIN, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void deregisterStudent_asTutorWithoutMatchingCourse_shouldReturnBadRequest() throws Exception {
            request.delete(
                    "/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/deregister/" + FIRST_COURSE_STUDENT1_LOGIN,
                    HttpStatus.BAD_REQUEST);
        }

    }

    @Nested
    class SearchUnregisteredStudentsTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void searchUnregisteredStudents_asTutorOfGroupWithoutMatchingCourse_shouldReturnNotFound() throws Exception {
            request.getList("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId()
                    + "/unregistered-students?loginOrName=student&pageIndex=0&pageSize=10", HttpStatus.NOT_FOUND, TutorialGroupStudentDTO.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void searchUnregisteredStudents_asTutorOfOtherGroup_shouldReturnForbidden() throws Exception {
            request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId()
                    + "/unregistered-students?loginOrName=student&pageIndex=0&pageSize=10", HttpStatus.FORBIDDEN, TutorialGroupStudentDTO.class);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void searchUnregisteredStudents_asEditorOfOtherCourse_shouldReturnForbidden() throws Exception {
            request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId()
                    + "/unregistered-students?loginOrName=student&pageIndex=0&pageSize=10", HttpStatus.FORBIDDEN, TutorialGroupStudentDTO.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void searchUnregisteredStudents_asTutorOfGroup_shouldReturnStudents() throws Exception {
            var unregisteredStudents = request.getList("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId()
                    + "/unregistered-students?loginOrName=student&pageIndex=0&pageSize=10", HttpStatus.OK, TutorialGroupStudentDTO.class);

            assertThat(unregisteredStudents).extracting(TutorialGroupStudentDTO::login, TutorialGroupStudentDTO::name, TutorialGroupStudentDTO::registrationNumber).containsExactly(
                    tuple(FIRST_COURSE_STUDENT2_LOGIN, FIRST_COURSE_STUDENT2_LOGIN + "First " + FIRST_COURSE_STUDENT2_LOGIN + "Last", null),
                    tuple(FIRST_COURSE_STUDENT3_LOGIN, FIRST_COURSE_STUDENT3_LOGIN + "First " + FIRST_COURSE_STUDENT3_LOGIN + "Last", "3"),
                    tuple(FIRST_COURSE_STUDENT4_LOGIN, FIRST_COURSE_STUDENT4_LOGIN + "First " + FIRST_COURSE_STUDENT4_LOGIN + "Last", "4"));
        }
    }

    @Nested
    class GetRegisteredStudentsTests {

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void getRegisteredStudents_asEditorOfCourseWithoutMatchingCourse_shouldReturnNotFound() throws Exception {
            request.getSet("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/registered-students",
                    HttpStatus.NOT_FOUND, TutorialGroupStudentDTO.class);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void getRegisteredStudents_asTutorOfOtherCourse_shouldReturnForbidden() throws Exception {
            request.getSet("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/registered-students", HttpStatus.FORBIDDEN,
                    TutorialGroupStudentDTO.class);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void getRegisteredStudents_asTutorOfCourse_shouldReturnOk() throws Exception {
            var registeredStudents = request.getSet(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/registered-students", HttpStatus.OK,
                    TutorialGroupStudentDTO.class);

            assertThat(registeredStudents).extracting(TutorialGroupStudentDTO::login, TutorialGroupStudentDTO::name, TutorialGroupStudentDTO::registrationNumber)
                    .containsExactlyInAnyOrder(tuple(FIRST_COURSE_STUDENT1_LOGIN, FIRST_COURSE_STUDENT1_LOGIN + "First " + FIRST_COURSE_STUDENT1_LOGIN + "Last", null));
        }
    }

    @Nested
    class ImportRegistrationsTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importRegistrations_asInstructorOfCourseWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            request.postListWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/import-registrations",
                    List.of(new TutorialGroupStudentImportDataDTO(FIRST_COURSE_STUDENT3_LOGIN, null)), TutorialGroupStudentImportDataDTO.class, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importRegistrations_asInstructorOfCourseWithoutMatchingCourse_shouldReturnBadRequest() throws Exception {
            request.postListWithResponseBody("/api/tutorialgroup/courses/" + exampleCourse2Id + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/import-registrations",
                    List.of(new TutorialGroupStudentImportDataDTO(FIRST_COURSE_STUDENT3_LOGIN, null)), TutorialGroupStudentImportDataDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importRegistrations_asInstructorOfOtherCourse_shouldReturnForbidden() throws Exception {
            request.postListWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/import-registrations",
                    List.of(new TutorialGroupStudentImportDataDTO(FIRST_COURSE_STUDENT3_LOGIN, null)), TutorialGroupStudentImportDataDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importRegistrations_asInstructorOfCourse_shouldReturnOk() throws Exception {
            var existingStudentDto = new TutorialGroupStudentImportDataDTO(FIRST_COURSE_STUDENT3_LOGIN, null);
            var notFoundStudentDto = new TutorialGroupStudentImportDataDTO("doesNotExist", null);
            var importDtos = List.of(existingStudentDto, notFoundStudentDto);

            var notFoundStudents = request.postListWithResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/import-registrations", importDtos,
                    TutorialGroupStudentImportDataDTO.class, HttpStatus.OK);

            assertThat(notFoundStudents).containsExactly(notFoundStudentDto);
            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(firstCourseStudent3, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isEqualTo(1);

            var channelParticipants = conversationParticipantRepository.findConversationParticipantsByConversationId(firstCourseChannel1.getId()).stream()
                    .map(ConversationParticipant::getUser).collect(Collectors.toSet());
            assertThat(channelParticipants).contains(firstCourseStudent1, firstCourseStudent3, firstCourseTutor1);
        }
    }

    @Nested
    class ImportAndExportTutorialGroupsTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importTutorialGroupsWithRegistrations_shouldWorkAsExpected() throws Exception {
            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup1.getTitle(), firstCourseStudent1);
            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup2.getTitle(), firstCourseStudent2);
            assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent3);
            assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent4);

            var freshTitle = "fresh-group";
            assertTutorialWithTitleDoesNotExistInDb(freshTitle);

            // from existing group to existing group
            var firstImport = new TutorialGroupImportDataDTO(firstCourseTutorialGroup2.getTitle(),
                    new StudentDTO(firstCourseStudent1.getLogin(), firstCourseStudent1.getFirstName(), firstCourseStudent1.getLastName(), "", ""), null, null, null, null, null);

            // from no group to existing group
            var secondImport = new TutorialGroupImportDataDTO(firstCourseTutorialGroup1.getTitle(), new StudentDTO("", firstCourseStudent3.getFirstName(),
                    firstCourseStudent3.getLastName(), firstCourseStudent3.getRegistrationNumber(), firstCourseStudent3.getEmail()), null, null, null, null, null);

            // from no group to fresh group
            var thirdImport = new TutorialGroupImportDataDTO(freshTitle, new StudentDTO(firstCourseStudent4), null, null, null, null, null);

            // from existing group to fresh group
            var fourthImport = new TutorialGroupImportDataDTO(freshTitle, new StudentDTO(firstCourseStudent2), null, null, null, null, null);

            var importResult = request.postListWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/import",
                    List.of(firstImport, secondImport, thirdImport, fourthImport), TutorialGroupImportDataDTO.class, HttpStatus.OK);

            assertThat(importResult).hasSize(4);
            assertThat(importResult.stream().map(TutorialGroupImportDataDTO::importSuccessful)).allMatch(Boolean.TRUE::equals);
            assertThat(importResult.stream().map(TutorialGroupImportDataDTO::error)).allMatch(Objects::isNull);
            assertThat(importResult).containsExactlyInAnyOrder(firstImport, secondImport, thirdImport, fourthImport);

            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup2.getTitle(), firstCourseStudent1);
            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup1.getTitle(), firstCourseStudent3);
            assertImportedTutorialGroupWithTitleInDb(freshTitle, Set.of(firstCourseStudent2, firstCourseStudent4), FIRST_COURSE_INSTRUCTOR1_LOGIN);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importTutorialGroupsWithRegistrations_withoutTitle_shouldNotCreateTutorialGroup() throws Exception {
            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup1.getTitle(), firstCourseStudent1);

            var emptyTitle = "";
            var registration = new TutorialGroupImportDataDTO(emptyTitle, new StudentDTO(firstCourseStudent1), null, null, null, null, null);

            var importResult = request.postListWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/import", List.of(registration),
                    TutorialGroupImportDataDTO.class, HttpStatus.OK);

            assertThat(importResult).hasSize(1);
            assertTutorialWithTitleDoesNotExistInDb(emptyTitle);
            assertThat(importResult.getFirst().importSuccessful()).isFalse();
            assertThat(importResult.getFirst().error()).isEqualTo(TutorialGroupImportErrors.NO_TITLE);
            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup1.getTitle(), firstCourseStudent1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importTutorialGroupsWithRegistrations_titleButNonExistingStudent_shouldStillCreateTutorialGroupButNoRegistration() throws Exception {
            var freshTitle = "missing-student";
            var registration = new TutorialGroupImportDataDTO(freshTitle, new StudentDTO("notExisting", "firstName", "lastName", "", ""), null, null, null, null, null);
            assertTutorialWithTitleDoesNotExistInDb(freshTitle);

            var importResult = request.postListWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/import", List.of(registration),
                    TutorialGroupImportDataDTO.class, HttpStatus.OK);

            assertImportedTutorialGroupWithTitleInDb(freshTitle, Set.of(), FIRST_COURSE_INSTRUCTOR1_LOGIN);
            assertThat(importResult).hasSize(1);
            assertThat(importResult.getFirst().importSuccessful()).isFalse();
            assertThat(importResult.getFirst().error()).isEqualTo(TutorialGroupImportErrors.NO_USER_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void importTutorialGroupsWithRegistrations_titleButSameStudentToMultipleGroups_shouldStillCreateTutorialGroupsButNoRegistration() throws Exception {
            var freshTitleOne = "duplicate-group-1";
            var freshTitleTwo = "duplicate-group-2";

            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup1.getTitle(), firstCourseStudent1);
            assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent3);

            var reg1 = new TutorialGroupImportDataDTO(freshTitleOne, new StudentDTO(firstCourseStudent1), null, null, null, null, null);
            var reg2 = new TutorialGroupImportDataDTO(freshTitleTwo, new StudentDTO(firstCourseStudent1), null, null, null, null, null);
            var reg3 = new TutorialGroupImportDataDTO(freshTitleOne, new StudentDTO(firstCourseStudent3), null, null, null, null, null);
            var reg4 = new TutorialGroupImportDataDTO(freshTitleTwo, new StudentDTO(firstCourseStudent3), null, null, null, null, null);

            var importResult = request.postListWithResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/import", List.of(reg1, reg2, reg3, reg4),
                    TutorialGroupImportDataDTO.class, HttpStatus.OK);

            assertImportedTutorialGroupWithTitleInDb(freshTitleOne, Set.of(), FIRST_COURSE_INSTRUCTOR1_LOGIN);
            assertImportedTutorialGroupWithTitleInDb(freshTitleTwo, Set.of(), FIRST_COURSE_INSTRUCTOR1_LOGIN);
            assertThat(importResult).hasSize(4);
            assertThat(importResult.stream().map(TutorialGroupImportDataDTO::importSuccessful)).allMatch(Boolean.FALSE::equals);
            assertThat(importResult.stream().map(TutorialGroupImportDataDTO::error)).allMatch(TutorialGroupImportErrors.MULTIPLE_REGISTRATIONS::equals);
            assertThat(importResult).containsExactlyInAnyOrder(reg1, reg2, reg3, reg4);

            assertUserIsRegisteredInTutorialWithTitle(firstCourseTutorialGroup1.getTitle(), firstCourseStudent1);
            assertUserIsNotRegisteredInATutorialGroup(firstCourseStudent3);

            asserTutorialGroupChannelIsCorrectlyConfigured(firstCourseTutorialGroup1);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void testCSVContentWithSampleData() throws Exception {
            tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle1", "SampleCampus1", 10, false, "SampleInfo1", "ENGLISH", firstCourseTutor1,
                    Set.of(firstCourseStudent1));
            tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle2", "SampleCampus2", 20, true, "SampleInfo2", "GERMAN", firstCourseTutor1,
                    Set.of(firstCourseStudent2));

            var params = new LinkedMultiValueMap<String, String>();
            params.add("fields", "ID,Title,Campus,Language,Capacity,IsOnline");
            String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/csv").queryParams(params).toUriString();
            String csvContent = request.get(url, HttpStatus.OK, String.class);

            assertThat(csvContent).contains("ID,Title,Campus,Language,Capacity,IsOnline");
            assertThat(csvContent).contains("SampleTitle1,SampleInfo1,ENGLISH,10");
            assertThat(csvContent).contains("SampleTitle2,SampleInfo2,GERMAN,20");
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void testExportTutorialGroupsToJSON() throws Exception {
            var params = new LinkedMultiValueMap<String, String>();
            params.add("fields", "ID,Title,Campus,Language");
            String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
            String jsonResponse = request.get(url, HttpStatus.OK, String.class);

            assertThat(jsonResponse).contains("id");
            assertThat(jsonResponse).contains("title");
            assertThat(jsonResponse).contains("campus");
            assertThat(jsonResponse).contains("language");
            assertThat(jsonResponse).contains("TG Mo 13");
            assertThat(jsonResponse).contains("TG Tue 13");
            assertThat(jsonResponse).contains("Garching");
            assertThat(jsonResponse).contains("ENGLISH");
            assertThat(jsonResponse).contains("GERMAN");
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void testExportTutorialGroupsToCSVWithAllFields() throws Exception {
            var params = new LinkedMultiValueMap<String, String>();
            params.add("fields", "ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Students");
            String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/csv").queryParams(params).toUriString();
            String csvContent = request.get(url, HttpStatus.OK, String.class);

            assertThat(csvContent).contains(
                    "ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Registration Number,First Name,Last Name");
            assertThat(csvContent).contains("TG Mo 13");
            assertThat(csvContent).contains("TG Tue 13");
            assertThat(csvContent).contains("Garching");
            assertThat(csvContent).contains("ENGLISH");
            assertThat(csvContent).contains("GERMAN");
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void testJSONContentWithSampleData() throws Exception {
            tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle1", "SampleCampus1", 10, false, "SampleInfo1", "ENGLISH", firstCourseTutor1,
                    Set.of(firstCourseStudent1));
            tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle2", "SampleCampus2", 20, true, "SampleInfo2", "GERMAN", firstCourseTutor1,
                    Set.of(firstCourseStudent2));

            var params = new LinkedMultiValueMap<String, String>();
            params.add("fields", "ID,Title,Campus,Language,Capacity,Is Online");
            String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
            String jsonResponse = request.get(url, HttpStatus.OK, String.class);

            assertThat(jsonResponse).contains("id");
            assertThat(jsonResponse).contains("title");
            assertThat(jsonResponse).contains("campus");
            assertThat(jsonResponse).contains("language");
            assertThat(jsonResponse).contains("capacity");
            assertThat(jsonResponse).contains("isOnline");
            assertThat(jsonResponse).contains("SampleTitle1");
            assertThat(jsonResponse).contains("SampleInfo1");
            assertThat(jsonResponse).contains("ENGLISH");
            assertThat(jsonResponse).contains("10");
            assertThat(jsonResponse).contains("SampleTitle2");
            assertThat(jsonResponse).contains("SampleInfo2");
            assertThat(jsonResponse).contains("GERMAN");
            assertThat(jsonResponse).contains("20");
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void testExportTutorialGroupsToJSONWithAllFields() throws Exception {
            var params = new LinkedMultiValueMap<String, String>();
            params.add("fields", "ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Students");
            String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
            String jsonResponse = request.get(url, HttpStatus.OK, String.class);

            assertThat(jsonResponse).contains("id");
            assertThat(jsonResponse).contains("title");
            assertThat(jsonResponse).contains("campus");
            assertThat(jsonResponse).contains("language");
            assertThat(jsonResponse).contains("additionalInformation");
            assertThat(jsonResponse).contains("capacity");
            assertThat(jsonResponse).contains("isOnline");
            assertThat(jsonResponse).contains("dayOfWeek");
            assertThat(jsonResponse).contains("startTime");
            assertThat(jsonResponse).contains("endTime");
            assertThat(jsonResponse).contains("location");
            assertThat(jsonResponse).contains("students");
            assertThat(jsonResponse).contains("TG Mo 13");
            assertThat(jsonResponse).contains("TG Tue 13");
            assertThat(jsonResponse).contains("Garching");
            assertThat(jsonResponse).contains("ENGLISH");
            assertThat(jsonResponse).contains("GERMAN");
            assertThat(jsonResponse).contains("SampleInfo1");
            assertThat(jsonResponse).contains("SampleInfo2");
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_INSTRUCTOR1_LOGIN, roles = "INSTRUCTOR")
        void testJSONContentWithSampleDataIncludingOptionalFields() throws Exception {
            tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle1", "SampleCampus1", 10, false, "SampleInfo1", "ENGLISH", firstCourseTutor1,
                    Set.of(firstCourseStudent1));
            tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "SampleTitle2", "SampleCampus2", 20, true, "SampleInfo2", "GERMAN", firstCourseTutor1,
                    Set.of(firstCourseStudent2));

            var params = new LinkedMultiValueMap<String, String>();
            params.add("fields", "ID,Title,Campus,Language,Additional Information,Capacity,Is Online,Day of Week,Start Time,End Time,Location,Students");
            String url = UriComponentsBuilder.fromPath("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/export/json").queryParams(params).toUriString();
            List<TutorialGroupExportDataDTO> exportData = request.getList(url, HttpStatus.OK, TutorialGroupExportDataDTO.class);

            assertThat(exportData).isNotEmpty();

            // createTutorialGroup signature: (courseId, title, additionalInformation, capacity, isOnline, campus, language, tutor, students)
            // Verify sample group 1
            assertThat(exportData).anySatisfy(dto -> {
                assertThat(dto.title()).isEqualTo("SampleTitle1");
                assertThat(dto.additionalInformation()).isEqualTo("SampleCampus1");
                assertThat(dto.campus()).isEqualTo("SampleInfo1");
                assertThat(dto.language()).isEqualTo("ENGLISH");
                assertThat(dto.capacity()).isEqualTo(10);
                assertThat(dto.isOnline()).isFalse();
                assertThat(dto.dayOfWeek()).isEqualTo("None");
                assertThat(dto.students()).isNotEmpty();
            });

            // Verify sample group 2
            assertThat(exportData).anySatisfy(dto -> {
                assertThat(dto.title()).isEqualTo("SampleTitle2");
                assertThat(dto.additionalInformation()).isEqualTo("SampleCampus2");
                assertThat(dto.campus()).isEqualTo("SampleInfo2");
                assertThat(dto.language()).isEqualTo("GERMAN");
                assertThat(dto.capacity()).isEqualTo(20);
                assertThat(dto.isOnline()).isTrue();
                assertThat(dto.dayOfWeek()).isEqualTo("None");
                assertThat(dto.students()).isNotEmpty();
            });
        }

        private void assertUserIsRegisteredInTutorialWithTitle(String title, User user) {
            var tutorialGroup = tutorialGroupTestRepository.findAllByCourseId(exampleCourseId).stream().filter(group -> title.equals(group.getTitle())).findFirst().orElseThrow();
            var registeredUsers = tutorialGroupRegistrationTestRepository.findAllByTutorialGroupAndType(tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)
                    .stream().map(registration -> registration.getStudent().getId()).collect(Collectors.toSet());
            assertThat(registeredUsers).contains(user.getId());
        }

        private void assertUserIsNotRegisteredInATutorialGroup(User user) {
            assertThat(tutorialGroupRegistrationTestRepository.countByStudentAndTutorialGroupCourseIdAndType(user, exampleCourseId,
                    TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)).isZero();
        }

        private void assertTutorialWithTitleDoesNotExistInDb(String title) {
            assertThat(tutorialGroupTestRepository.findAllByCourseId(exampleCourseId).stream().noneMatch(group -> title.equals(group.getTitle()))).isTrue();
        }

        private void assertImportedTutorialGroupWithTitleInDb(String title, Set<User> expectedStudents, String expectedTutorLogin) {
            var tutorialGroup = tutorialGroupTestRepository.findAllByCourseId(exampleCourseId).stream().filter(group -> title.equals(group.getTitle())).findFirst().orElseThrow();
            assertThat(tutorialGroup.getTeachingAssistant()).isNotNull();
            assertThat(tutorialGroup.getTeachingAssistant().getLogin()).isEqualTo(expectedTutorLogin);
            var registeredStudents = tutorialGroupRegistrationTestRepository.findAllByTutorialGroupAndType(tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION)
                    .stream().map(registration -> registration.getStudent().getId()).collect(Collectors.toSet());
            assertThat(registeredStudents).containsExactlyInAnyOrderElementsOf(expectedStudents.stream().map(User::getId).collect(Collectors.toSet()));
        }
    }
}
