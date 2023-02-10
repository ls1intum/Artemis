package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserDTO;

public class ExamUserIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "examuser";

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository userRepo;

    private Course course1;

    private Exam exam1;

    private static final int NUMBER_OF_STUDENTS = 5;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 0, 0, 1);
        // Add users that are not in the course
        database.createAndSaveUser(TEST_PREFIX + "student42", passwordService.hashPassword(ModelFactory.USER_PASSWORD));
        database.createAndSaveUser(TEST_PREFIX + "instructor6", passwordService.hashPassword(ModelFactory.USER_PASSWORD));

        var student1 = database.getUserByLogin(TEST_PREFIX + "student1");
        var student2 = database.getUserByLogin(TEST_PREFIX + "student2");
        var student3 = database.getUserByLogin(TEST_PREFIX + "student3");
        var student4 = database.getUserByLogin(TEST_PREFIX + "student4");
        course1 = database.addEmptyCourse();

        // same registration number as in test pdf file
        student1.setGroups(Set.of(course1.getStudentGroupName()));
        student1.setRegistrationNumber("03756882");
        userRepo.save(student1);
        student2.setGroups(Set.of(course1.getStudentGroupName()));
        student2.setRegistrationNumber("03756883");
        userRepo.save(student2);
        student3.setGroups(Set.of(course1.getStudentGroupName()));
        student3.setRegistrationNumber("03756884");
        userRepo.save(student3);
        student4.setGroups(Set.of(course1.getStudentGroupName()));
        student4.setRegistrationNumber("03756885");
        userRepo.save(student4);

        exam1 = database.addActiveExamWithRegisteredUser(course1, student2);
        exam1 = database.addExerciseGroupsAndExercisesToExam(exam1, false);
        exam1 = examRepository.save(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamUser_DidCheckFields() throws Exception {
        ExamUserDTO examUserDTO = new ExamUserDTO(TEST_PREFIX + "student2", "", "", "", "", "", "", "", true, true, true, true, "");
        var examUserResponse = request.getMvc().perform(buildUpdateExamUser(examUserDTO, false)).andExpect(status().isOk()).andReturn();
        ExamUser examUser = mapper.readValue(examUserResponse.getResponse().getContentAsString(), ExamUser.class);
        assertThat(examUser.getDidCheckRegistrationNumber()).isTrue();
        assertThat(examUser.getDidCheckImage()).isTrue();
        assertThat(examUser.getDidCheckName()).isTrue();
        assertThat(examUser.getDidCheckLogin()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddStudentsToExamWithSeatAndRoomFields() throws Exception {
        List<ExamUserDTO> examUserDTOs = new ArrayList<>();
        ExamUserDTO examUserDTO1 = new ExamUserDTO(TEST_PREFIX + "student1", "", "", "03756882", "", "", "101", "11", true, true, true, true, "");
        ExamUserDTO examUserDTO2 = new ExamUserDTO(TEST_PREFIX + "student2", "", "", "03756883", "", "", "102", "11", true, true, true, true, "");
        examUserDTOs.add(examUserDTO1);
        examUserDTOs.add(examUserDTO2);

        List<ExamUserDTO> responseNotFoundExamUsers = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", examUserDTOs,
                ExamUserDTO.class, HttpStatus.OK);
        assertThat(responseNotFoundExamUsers.size()).isEqualTo(0);
        Exam exam = examRepository.findWithExamUsersById(exam1.getId()).orElseThrow();
        var examUsers = exam.getExamUsers();
        assertThat(examUsers.size()).isEqualTo(2);

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
        List<ExamUserDTO> examUserDTOs = new ArrayList<>();
        ExamUserDTO examUserDTO1 = new ExamUserDTO(TEST_PREFIX + "student1", "", "", "03756882", "", "", "101", "11", true, true, true, true, "");
        ExamUserDTO examUserDTO3 = new ExamUserDTO(TEST_PREFIX + "student3", "", "", "03756884", "", "", "101", "11", true, true, true, true, "");
        ExamUserDTO examUserDTO4 = new ExamUserDTO(TEST_PREFIX + "student4", "", "", "03756885", "", "", "102", "11", true, true, true, true, "");
        examUserDTOs.add(examUserDTO1);
        examUserDTOs.add(examUserDTO3);
        examUserDTOs.add(examUserDTO4);

        // add students to exam with respective registration numbers, same as in pdf test file
        List<ExamUserDTO> responseNotFoundExamUsers = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", examUserDTOs,
                ExamUserDTO.class, HttpStatus.OK);
        assertThat(responseNotFoundExamUsers.size()).isEqualTo(0);

        // upload exam user images
        var imageUploadResponse = request.getMvc().perform(buildUploadExamUserImages()).andExpect(status().isOk()).andReturn();
        List<String> notFoundExamUsersRegistrationNumbers = mapper.readValue(imageUploadResponse.getResponse().getContentAsString(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        assertThat(notFoundExamUsersRegistrationNumbers.size()).isEqualTo(0);

        // check if exam users have been updated with the images
        Exam exam = examRepository.findByIdWithExamUsersElseThrow(exam1.getId());
        // 4 exam users, 3 new and 1 already existing
        assertThat(exam.getExamUsers().size()).isEqualTo(4);
        exam.getExamUsers().forEach(eu -> {
            assertThat(eu.getStudentImagePath()).isNotNull();
            assertThat(eu.getStudentImagePath()).isNotNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamUserDidCheckFieldsAndSigningImage() throws Exception {
        ExamUserDTO examUserDTO = new ExamUserDTO(TEST_PREFIX + "student2", "", "", "", "", "", "", "", true, true, true, true, "");
        var examUserResponse = request.getMvc().perform(buildUpdateExamUser(examUserDTO, true)).andExpect(status().isOk()).andReturn();
        ExamUser examUser = mapper.readValue(examUserResponse.getResponse().getContentAsString(), ExamUser.class);
        assertThat(examUser.getDidCheckRegistrationNumber()).isTrue();
        assertThat(examUser.getDidCheckImage()).isTrue();
        assertThat(examUser.getDidCheckName()).isTrue();
        assertThat(examUser.getDidCheckLogin()).isTrue();
        assertThat(examUser.getSigningImagePath()).isNotNull();
    }

    private MockHttpServletRequestBuilder buildUpdateExamUser(@NotNull ExamUserDTO examUserDTO, boolean hasSigned) throws Exception {
        var examUserPart = new MockMultipartFile("examUserDTO", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(examUserDTO).getBytes());
        if (hasSigned) {
            var signingImage = loadFile("classpath:test-data/exam-users", "examUserSigningImage.png");
            return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-users").file(examUserPart)
                    .file(signingImage).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        }
        else {
            return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-users").file(examUserPart)
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        }
    }

    private MockHttpServletRequestBuilder buildUploadExamUserImages() throws Exception {
        var signingImage = loadFile("classpath:test-data/exam-users", "studentsWithImages.pdf");

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-users-save-images").file(signingImage)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MockMultipartFile loadFile(String path, String fileName) throws Exception {
        File signingImage = new File(ResourceUtils.getFile(path), fileName);
        FileInputStream input = new FileInputStream(signingImage);
        return new MockMultipartFile("file", signingImage.getName(), "image/png", IOUtils.toByteArray(input));
    }

}
