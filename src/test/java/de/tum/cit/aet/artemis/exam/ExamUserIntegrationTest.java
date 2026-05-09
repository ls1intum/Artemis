package de.tum.cit.aet.artemis.exam;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserForRegistrationDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.ExamStudentDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUserAttendanceCheckDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUserDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUsersNotFoundDTO;
import de.tum.cit.aet.artemis.exam.dto.ExportExamUserDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

class ExamUserIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "examuser";

    @Autowired
    private ExamUserRepository examUserRepository;

    private Course course1;

    private Exam exam1;

    private Course course2;

    private Exam exam2;

    private final List<LocalRepository> studentRepos = new ArrayList<>();

    private static final int NUMBER_OF_STUDENTS = 4;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 0, 1);
        // Add users that are not in the course

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var student3 = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        var student4 = userUtilService.getUserByLogin(TEST_PREFIX + "student4");
        course1 = courseUtilService.addEmptyCourse();

        // same registration number as in test pdf file
        student1.setRegistrationNumber("03756882");
        userTestRepository.save(student1);

        student2.setRegistrationNumber("03756883");
        userTestRepository.save(student2);

        student3.setRegistrationNumber("03756884");
        userTestRepository.save(student3);

        student4.setRegistrationNumber("03756885");
        userTestRepository.save(student4);

        exam1 = examUtilService.addActiveExamWithRegisteredUser(course1, student2);
        exam1 = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, false);
        exam1 = examRepository.save(exam1);

        programmingExerciseTestService.setup(this, versionControlService);
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
        for (LocalRepository studentRepo : studentRepos) {
            studentRepo.resetLocalRepo();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateExamUser_DidCheckFields() throws Exception {
        ExamUserDTO examUserDTO = new ExamUserDTO(TEST_PREFIX + "student2", "", "", "", "", "", "", "", true, true, true, true, "", null, null, null, null, null, null, null);
        var examUserResponse = request.performMvcRequest(buildUpdateExamUser(examUserDTO, false, course1.getId(), exam1.getId())).andExpect(status().isOk()).andReturn();
        ExamUser examUser = objectMapper.readValue(examUserResponse.getResponse().getContentAsString(), ExamUser.class);
        assertThat(examUser.getDidCheckRegistrationNumber()).isTrue();
        assertThat(examUser.getDidCheckImage()).isTrue();
        assertThat(examUser.getDidCheckName()).isTrue();
        assertThat(examUser.getDidCheckLogin()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentsToExamWithSeatAndRoomFields() throws Exception {
        List<ExamUserDTO> examUserDTOs = new ArrayList<>();
        ExamUserDTO examUserDTO1 = new ExamUserDTO(TEST_PREFIX + "student1", "", "", "03756882", "", "", "101", "11", true, true, true, true, "", null, null, null, null, null,
                null, null);
        ExamUserDTO examUserDTO2 = new ExamUserDTO(TEST_PREFIX + "student2", "", "", "03756883", "", "", "102", "11", true, true, true, true, "", null, null, null, null, null,
                null, null);
        examUserDTOs.add(examUserDTO1);
        examUserDTOs.add(examUserDTO2);

        List<ExamUserDTO> responseNotFoundExamUsers = request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students",
                examUserDTOs, ExamUserDTO.class, OK);
        assertThat(responseNotFoundExamUsers).isEmpty();
        Exam exam = examRepository.findWithExamUsersById(exam1.getId()).orElseThrow();
        var examUsers = exam.getExamUsers();
        assertThat(examUsers).hasSize(2);

        examUsers.forEach(eu -> {
            assertThat(eu.getSigningImagePath()).isNullOrEmpty();
            assertThat(eu.getStudentImagePath()).isNullOrEmpty();
            assertThat(eu.getPlannedRoom()).isNotNull();
            assertThat(eu.getPlannedSeat()).isNotNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUploadExamUserImages() throws Exception {
        // registration number is important for the test, exam users should have same registration number as in the test pdf file
        // student2 already exists in the exam and there is no need to add it again
        final var examUserDTOs = getExamUserDTOS();

        // add students to exam with respective registration numbers, same as in pdf test file
        List<ExamUserDTO> responseNotFoundExamUsers = request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students",
                examUserDTOs, ExamUserDTO.class, OK);
        assertThat(responseNotFoundExamUsers).isEmpty();

        // upload exam user images
        var imageUploadResponse = request.performMvcRequest(buildUploadExamUserImages(course1.getId(), exam1.getId())).andExpect(status().isOk()).andReturn();
        ExamUsersNotFoundDTO examUsersNotFoundDTO = objectMapper.readValue(imageUploadResponse.getResponse().getContentAsString(), ExamUsersNotFoundDTO.class);

        assertThat(examUsersNotFoundDTO.numberOfUsersNotFound()).isZero();

        // check if exam users have been updated with the images
        Exam exam = examRepository.findByIdWithExamUsersElseThrow(exam1.getId());
        // 4 exam users, 3 new and 1 already existing
        assertThat(exam.getExamUsers()).hasSize(4);
        for (ExamUser examUser : exam.getExamUsers()) {
            assertThat(examUser.getStudentImagePath()).isNotNull();
            String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, examUser.getStudentImagePath());
            assertThat(request.getFile(requestUrl, HttpStatus.OK)).isNotEmpty();
        }

        // re-upload the same file, should not change anything

        // upload exam user images
        imageUploadResponse = request.performMvcRequest(buildUploadExamUserImages(course1.getId(), exam1.getId())).andExpect(status().isOk()).andReturn();
        examUsersNotFoundDTO = objectMapper.readValue(imageUploadResponse.getResponse().getContentAsString(), ExamUsersNotFoundDTO.class);

        assertThat(examUsersNotFoundDTO.numberOfUsersNotFound()).isZero();

        // check if exam users have been updated with the images
        exam = examRepository.findByIdWithExamUsersElseThrow(exam1.getId());
        // 4 exam users, 3 new and 1 already existing
        assertThat(exam.getExamUsers()).hasSize(4);
        for (ExamUser examUser : exam.getExamUsers()) {
            assertThat(examUser.getStudentImagePath()).isNotNull();
            String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, examUser.getStudentImagePath());
            assertThat(request.getFile(requestUrl, HttpStatus.OK)).isNotEmpty();
        }
    }

    private static List<ExamUserDTO> getExamUserDTOS() {
        List<ExamUserDTO> examUserDTOs = new ArrayList<>();
        ExamUserDTO examUserDTO1 = new ExamUserDTO(TEST_PREFIX + "student1", "", "", "03756882", "", "", "101", "11", true, true, true, true, "", null, null, null, null, null,
                null, null);
        ExamUserDTO examUserDTO3 = new ExamUserDTO(TEST_PREFIX + "student3", "", "", "03756884", "", "", "101", "11", true, true, true, true, "", null, null, null, null, null,
                null, null);
        ExamUserDTO examUserDTO4 = new ExamUserDTO(TEST_PREFIX + "student4", "", "", "03756885", "", "", "102", "11", true, true, true, true, "", null, null, null, null, null,
                null, null);
        examUserDTOs.add(examUserDTO1);
        examUserDTOs.add(examUserDTO3);
        examUserDTOs.add(examUserDTO4);
        return examUserDTOs;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateExamUserDidCheckFieldsAndSigningImage() throws Exception {
        ExamUserDTO examUserDTO = new ExamUserDTO(TEST_PREFIX + "student2", "", "", "", "", "", "", "", true, true, true, true, "", null, null, null, null, null, null, null);
        var examUserResponse = request.performMvcRequest(buildUpdateExamUser(examUserDTO, true, course1.getId(), exam1.getId())).andExpect(status().isOk()).andReturn();
        ExamUser examUser = objectMapper.readValue(examUserResponse.getResponse().getContentAsString(), ExamUser.class);
        assertThat(examUser.getDidCheckRegistrationNumber()).isTrue();
        assertThat(examUser.getDidCheckImage()).isTrue();
        assertThat(examUser.getDidCheckName()).isTrue();
        assertThat(examUser.getDidCheckLogin()).isTrue();
        assertThat(examUser.getSigningImagePath()).isNotNull();
    }

    // TODO enable again - figure out why for one exercise the participations are not created
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testVerifyExamUserAttendance() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false, true);
        long examId = studentExams.getFirst().getExam().getId();

        final HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "foo");
        headers.set("X-Artemis-Client-Fingerprint", "bar");
        headers.set("X-Forwarded-For", "10.0.28.1");

        for (var studentExam : studentExams) {
            var user = studentExam.getUser();
            userUtilService.changeUser(user.getLogin());
            var response = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.OK,
                    StudentExam.class, headers);
            assertThat(response).isEqualTo(studentExam);
            assertThat(response.isStarted()).isTrue();
            assertThat(response.getExercises()).hasSize(exam2.getNumberOfExercisesInExam());

            assertThat(studentExamRepository.findById(studentExam.getId()).orElseThrow().isStarted()).isTrue();
        }

        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // update exam user attendance
        ExamUserDTO examUserDTO = new ExamUserDTO(TEST_PREFIX + "student1", "", "", "", "", "", "", "", true, true, true, true, "", null, null, null, null, null, null, null);
        var examUserResponse = request.performMvcRequest(buildUpdateExamUser(examUserDTO, true, course2.getId(), exam2.getId())).andExpect(status().isOk()).andReturn();
        ExamUser examUser = objectMapper.readValue(examUserResponse.getResponse().getContentAsString(), ExamUser.class);
        assertThat(examUser.getDidCheckRegistrationNumber()).isTrue();
        assertThat(examUser.getDidCheckImage()).isTrue();
        assertThat(examUser.getDidCheckName()).isTrue();
        assertThat(examUser.getDidCheckLogin()).isTrue();

        // as instructor, verify the attendance of the students
        List<ExamUserAttendanceCheckDTO> examUsersWhoDidNotSign = request.getList("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/verify-exam-users",
                HttpStatus.OK, ExamUserAttendanceCheckDTO.class);
        // one student (student1) signed, the other 3 did not
        assertThat(examUsersWhoDidNotSign).hasSize(3);
        List<Long> unsignedExamUserIds = new ArrayList<>();
        for (var examUserAttendanceCheckDTO : examUsersWhoDidNotSign) {
            unsignedExamUserIds.add(examUserAttendanceCheckDTO.id());
            assertThat(examUserAttendanceCheckDTO.started()).isTrue();
            assertThat(examUserAttendanceCheckDTO.login()).isNotEqualTo(TEST_PREFIX + "student1");
            assertThat(examUserAttendanceCheckDTO.signingImagePath()).isNull();
        }

        verifySignedExamUsersHaveSignature(examId, unsignedExamUserIds);

        // update exam user attendance to override signature
        ExamUserDTO examUserUpdateDTO = new ExamUserDTO(TEST_PREFIX + "student1", "", "", "", "", "", "", "", true, true, true, true, "", null, null, null, null, null, null, null);
        var examUserUpdateResponse = request.performMvcRequest(buildUpdateExamUser(examUserUpdateDTO, true, course2.getId(), exam2.getId())).andExpect(status().isOk()).andReturn();
        ExamUser updatedExamUser = objectMapper.readValue(examUserUpdateResponse.getResponse().getContentAsString(), ExamUser.class);
        assertThat(updatedExamUser.getDidCheckRegistrationNumber()).isTrue();
        assertThat(updatedExamUser.getDidCheckImage()).isTrue();
        assertThat(updatedExamUser.getDidCheckName()).isTrue();
        assertThat(updatedExamUser.getDidCheckLogin()).isTrue();

        verifySignedExamUsersHaveSignature(examId, unsignedExamUserIds);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testVerifyAttendance() throws Exception {
        // User started an exam, but attendance wasn't checked yet
        var attendanceCheckResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance", HttpStatus.OK, Boolean.class);
        assertThat(attendanceCheckResponse).isFalse();

        // Verify attendance
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        ExamUserDTO examUserDTO = new ExamUserDTO(TEST_PREFIX + "student2", "", "", "", "", "", "", "", true, true, true, true, "", null, null, null, null, null, null, null);
        var examUserResponse = request.performMvcRequest(buildUpdateExamUser(examUserDTO, true, course1.getId(), exam1.getId())).andExpect(status().isOk()).andReturn();
        ExamUser examUser = objectMapper.readValue(examUserResponse.getResponse().getContentAsString(), ExamUser.class);
        assertThat(examUser.getDidCheckRegistrationNumber()).isTrue();
        assertThat(examUser.getDidCheckImage()).isTrue();
        assertThat(examUser.getDidCheckName()).isTrue();
        assertThat(examUser.getDidCheckLogin()).isTrue();
        assertThat(examUser.getSigningImagePath()).isNotEmpty();

        // Switch back to student2
        userUtilService.changeUser(TEST_PREFIX + "student2");

        // Check attendance again
        attendanceCheckResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance", HttpStatus.OK, Boolean.class);
        assertThat(attendanceCheckResponse).isTrue();
    }

    private void verifySignedExamUsersHaveSignature(long examId, List<Long> unsignedExamUserIds) throws Exception {
        Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        assertThat(exam.getExamUsers()).hasSize(4);
        List<ExamUser> signedUsers = exam.getExamUsers().stream().filter(eu -> !unsignedExamUserIds.contains(eu.getId())).toList();
        assertThat(signedUsers).hasSize(1);
        for (var user : signedUsers) {
            assertThat(user.getSigningImagePath()).isNotNull();
            String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, user.getSigningImagePath());
            assertThat(request.getFile(requestUrl, HttpStatus.OK)).isNotEmpty();
        }
    }

    private MockMultipartHttpServletRequestBuilder buildUpdateExamUser(@NonNull ExamUserDTO examUserDTO, boolean hasSigned, long courseId, long examId) throws Exception {
        var examUserPart = new MockMultipartFile("examUserDTO", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsString(examUserDTO).getBytes());
        if (hasSigned) {
            var signingImage = loadFile("classpath:test-data/exam-users", "examUserSigningImage.png");
            return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/exam/courses/" + courseId + "/exams/" + examId + "/exam-users").file(examUserPart).file(signingImage)
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        }
        else {
            return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/exam/courses/" + courseId + "/exams/" + examId + "/exam-users").file(examUserPart)
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        }
    }

    private MockMultipartHttpServletRequestBuilder buildUploadExamUserImages(long courseId, long examId) throws Exception {
        var signingImage = loadFile("classpath:test-data/exam-users", "studentsWithImages.pdf");

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/exam/courses/" + courseId + "/exams/" + examId + "/exam-users-save-images").file(signingImage)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MockMultipartFile loadFile(String path, String fileName) throws Exception {
        Path signingPath = ResourceUtils.getFile(path).toPath().resolve(fileName);
        File signingImage = signingPath.toFile();
        try (InputStream input = Files.newInputStream(signingPath)) {
            return new MockMultipartFile("file", signingImage.getName(), "image/png", input.readAllBytes());
        }
    }

    private List<StudentExam> prepareStudentExamsForConduction(boolean early, boolean setFields) throws Exception {

        ZonedDateTime visibleDate;
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        if (early) {
            startDate = ZonedDateTime.now().plusHours(1);
            endDate = ZonedDateTime.now().plusHours(3);
        }
        else {
            // If the exam is prepared only 5 minutes before the release date, the repositories of the students are unlocked as well.
            startDate = ZonedDateTime.now().plusMinutes(6);
            endDate = ZonedDateTime.now().plusMinutes(8);
        }

        visibleDate = ZonedDateTime.now().minusMinutes(15);
        // --> 2 min = 120s working time

        // all registered students
        Set<User> registeredStudents = new HashSet<>();
        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
            registeredStudents.add(userUtilService.getUserByLogin(TEST_PREFIX + "student" + i));
        }

        var studentExams = programmingExerciseTestService.prepareStudentExamsForConduction(TEST_PREFIX, visibleDate, startDate, endDate, registeredStudents, studentRepos);
        Exam exam = examRepository.findByIdElseThrow(studentExams.getFirst().getExam().getId());
        Course course = exam.getCourse();

        if (!early) {
            // simulate "wait" for exam to start
            exam.setStartDate(ZonedDateTime.now());
            exam.setEndDate(ZonedDateTime.now().plusMinutes(2));
            examRepository.save(exam);
        }

        if (setFields) {
            exam2 = exam;
            course2 = course;
        }
        return studentExams;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_returnsAllRegisteredStudents() throws Exception {
        var paginationExam = createPaginationTestExam();
        var params = pageParams("");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamStudentsPaged_allowedForTutor() throws Exception {
        var paginationExam = createPaginationTestExam();
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, pageParams(""));
        assertThat(result).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExamStudentsPaged_forbiddenForStudent() throws Exception {
        request.getList(pagedUrl(exam1), HttpStatus.FORBIDDEN, ExamStudentDTO.class, pageParams(""));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_filterSubmitted_returnsOnlySubmittedStudents() throws Exception {
        var paginationExam = createPaginationTestExam();
        var params = pageParams("Submitted");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(result.getFirst().progress()).isEqualTo(ExamStudentDTO.PROGRESS_SUBMITTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_filterStarted_returnsOnlyStartedStudents() throws Exception {
        var paginationExam = createPaginationTestExam();
        var params = pageParams("Started");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student2");
        assertThat(result.getFirst().progress()).isEqualTo(ExamStudentDTO.PROGRESS_STARTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_filterNotStarted_returnsOnlyNotStartedStudents() throws Exception {
        var paginationExam = createPaginationTestExam();
        var params = pageParams("NotStarted");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student3");
        assertThat(result.getFirst().progress()).isEqualTo(ExamStudentDTO.PROGRESS_NOT_STARTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_filterExamMissing_returnsOnlyExamMissingStudents() throws Exception {
        var paginationExam = createPaginationTestExam();
        var params = pageParams("ExamMissing");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student4");
        assertThat(result.getFirst().progress()).isEqualTo(ExamStudentDTO.PROGRESS_EXAM_MISSING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_filterDidNotAttend_returnsExamMissingAndNotStartedStudents() throws Exception {
        var paginationExam = createPaginationTestExam();
        // student3 (NotStarted) + student4 (ExamMissing) = 2 students who did not attend
        var params = pageParams("DidNotAttend");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(ExamStudentDTO::login).toList()).containsExactlyInAnyOrder(TEST_PREFIX + "student3", TEST_PREFIX + "student4");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_filterAttendanceChecked_returnsFullyCheckedStudents() throws Exception {
        var paginationExam = createPaginationTestExam();
        var params = pageParams("AttendanceChecked");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(result.getFirst().didCheckImage()).isTrue();
        assertThat(result.getFirst().didCheckLogin()).isTrue();
        assertThat(result.getFirst().didCheckName()).isTrue();
        assertThat(result.getFirst().didCheckRegistrationNumber()).isTrue();
        assertThat(result.getFirst().signingImagePath()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_filterAttendanceNotChecked_returnsStartedWithIncompleteChecks() throws Exception {
        var paginationExam = createPaginationTestExam();
        var params = pageParams("AttendanceNotChecked");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        // student2 started but attendance not fully checked
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_searchByLogin_returnsMatchingStudent() throws Exception {
        var paginationExam = createPaginationTestExam();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "0");
        params.add("pageSize", "50");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "name");
        params.add("searchTerm", TEST_PREFIX + "student1");
        List<ExamStudentDTO> result = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStudentsPaged_secondPage_returnsCorrectSubset() throws Exception {
        var paginationExam = createPaginationTestExam();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "0");
        params.add("pageSize", "2");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "login");
        params.add("searchTerm", "");
        List<ExamStudentDTO> page0 = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(page0).hasSize(2);

        params.set("page", "1");
        List<ExamStudentDTO> page1 = request.getList(pagedUrl(paginationExam), HttpStatus.OK, ExamStudentDTO.class, params);
        assertThat(page1).hasSize(2);

        // all logins across both pages are distinct
        var allLogins = new ArrayList<>(page0.stream().map(ExamStudentDTO::login).toList());
        allLogins.addAll(page1.stream().map(ExamStudentDTO::login).toList());
        assertThat(allLogins).doesNotHaveDuplicates().hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchUsersForExamRegistration_byLogin_returnsMatchingUsers() throws Exception {
        List<UserForRegistrationDTO> result = request.getList(searchUrl(course1.getId(), exam1.getId()), HttpStatus.OK, UserForRegistrationDTO.class,
                searchParams(TEST_PREFIX + "student", 0, 10));
        assertThat(result).hasSize(4);
        assertThat(result).allMatch(u -> u.login().startsWith(TEST_PREFIX + "student"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchUsersForExamRegistration_marksAlreadyRegisteredUser() throws Exception {
        // student2 is the only one registered for exam1 in @BeforeEach
        List<UserForRegistrationDTO> result = request.getList(searchUrl(course1.getId(), exam1.getId()), HttpStatus.OK, UserForRegistrationDTO.class,
                searchParams(TEST_PREFIX + "student2", 0, 10));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student2");
        assertThat(result.getFirst().isRegistered()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchUsersForExamRegistration_nonRegisteredUsersNotFlagged() throws Exception {
        // student1 is not registered for exam1
        List<UserForRegistrationDTO> result = request.getList(searchUrl(course1.getId(), exam1.getId()), HttpStatus.OK, UserForRegistrationDTO.class,
                searchParams(TEST_PREFIX + "student1", 0, 10));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(result.getFirst().isRegistered()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchUsersForExamRegistration_paginationRespectsPageSize() throws Exception {
        List<UserForRegistrationDTO> result = request.getList(searchUrl(course1.getId(), exam1.getId()), HttpStatus.OK, UserForRegistrationDTO.class,
                searchParams(TEST_PREFIX + "student", 0, 2));
        assertThat(result).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSearchUsersForExamRegistration_noResultsForUnknownTerm() throws Exception {
        List<UserForRegistrationDTO> result = request.getList(searchUrl(course1.getId(), exam1.getId()), HttpStatus.OK, UserForRegistrationDTO.class,
                searchParams("zzz_no_match_zzz", 0, 10));
        assertThat(result).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSearchUsersForExamRegistration_forbiddenForTutor() throws Exception {
        request.getList(searchUrl(course1.getId(), exam1.getId()), HttpStatus.FORBIDDEN, UserForRegistrationDTO.class, searchParams(TEST_PREFIX + "student", 0, 10));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSearchUsersForExamRegistration_forbiddenForStudent() throws Exception {
        request.getList(searchUrl(course1.getId(), exam1.getId()), HttpStatus.FORBIDDEN, UserForRegistrationDTO.class, searchParams(TEST_PREFIX + "student", 0, 10));
    }

    private String searchUrl(long courseId, long examId) {
        return "/api/exam/courses/" + courseId + "/exams/" + examId + "/students/search";
    }

    private MultiValueMap<String, String> searchParams(String searchTerm, int page, int size) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("searchTerm", searchTerm);
        params.add("page", String.valueOf(page));
        params.add("size", String.valueOf(size));
        return params;
    }

    private String pagedUrl(Exam exam) {
        return "/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exam-students/paged";
    }

    private MultiValueMap<String, String> pageParams(String filterProp) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "0");
        params.add("pageSize", "50");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "name");
        params.add("searchTerm", "");
        if (!filterProp.isBlank()) {
            params.add("filterProp", filterProp);
        }
        return params;
    }

    /**
     * Creates a fresh exam on {@code course1} with 4 students in distinct states:
     * <ul>
     * <li>student1 – submitted, attendance fully checked</li>
     * <li>student2 – started, attendance not checked</li>
     * <li>student3 – not started (has StudentExam, started = false)</li>
     * <li>student4 – exam missing (no StudentExam)</li>
     * </ul>
     */
    private Exam createPaginationTestExam() {
        var s1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var s2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var s3 = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        var s4 = userUtilService.getUserByLogin(TEST_PREFIX + "student4");

        Exam exam = new Exam();
        exam.setCourse(course1);
        exam.setTitle("Pagination Test Exam");
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setWorkingTime(2 * 60 * 60);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(2));
        exam.setTestExam(false);
        exam = examRepository.save(exam);

        // Register all 4 students
        examUserRepository.save(buildExamUser(exam, s1, true, "signing/path/student1.png"));
        examUserRepository.save(buildExamUser(exam, s2, false, null));
        examUserRepository.save(buildExamUser(exam, s3, false, null));
        examUserRepository.save(buildExamUser(exam, s4, false, null));

        // student1: submitted
        StudentExam se1 = buildStudentExam(exam, s1);
        se1.setStartedAndStartDate(ZonedDateTime.now().minusMinutes(30));
        se1.setSubmitted(true);
        se1.setSubmissionDate(ZonedDateTime.now().minusMinutes(10));
        studentExamRepository.save(se1);

        // student2: started only
        StudentExam se2 = buildStudentExam(exam, s2);
        se2.setStartedAndStartDate(ZonedDateTime.now().minusMinutes(20));
        studentExamRepository.save(se2);

        // student3: has StudentExam but not started
        studentExamRepository.save(buildStudentExam(exam, s3));

        // student4: no StudentExam (ExamMissing)

        return exam;
    }

    private ExamUser buildExamUser(Exam exam, User user, boolean attendanceChecked, String signingImagePath) {
        ExamUser eu = new ExamUser();
        eu.setUser(user);
        eu.setExam(exam);
        if (attendanceChecked) {
            eu.setDidCheckImage(true);
            eu.setDidCheckName(true);
            eu.setDidCheckLogin(true);
            eu.setDidCheckRegistrationNumber(true);
            eu.setSigningImagePath(signingImagePath);
        }
        return eu;
    }

    private StudentExam buildStudentExam(Exam exam, User user) {
        StudentExam se = new StudentExam();
        se.setExam(exam);
        se.setUser(user);
        se.setTestRun(false);
        se.setWorkingTime(exam.getDuration());
        return se;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportExamUsers() throws Exception {
        List<ExamUserDTO> examUserDTOs = getExamUserDTOS();
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", examUserDTOs, ExamUserDTO.class, OK);

        List<ExportExamUserDTO> exportedUsers = request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/export-students", HttpStatus.OK,
                ExportExamUserDTO.class);

        assertThat(exportedUsers).hasSize(4);

        ExportExamUserDTO user = exportedUsers.stream().filter(u -> (TEST_PREFIX + "student1").equals(u.login())).findFirst().orElseThrow();

        assertThat(user.login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(user.matriculationNumber()).isEqualTo("03756882");
        assertThat(user.name()).isNotBlank();
        assertThat(user.email()).isNotBlank();

        assertThat(user.room()).isNotNull();
        assertThat(user.seat()).isNotNull();
    }

}
