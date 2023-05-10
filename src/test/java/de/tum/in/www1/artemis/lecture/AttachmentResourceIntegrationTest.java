package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.validation.constraints.NotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

class AttachmentResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "attachmentresourceintegrationtest";

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Attachment attachment;

    private Lecture lecture;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 0, 1, 0, 1);

        attachment = ModelFactory.generateAttachment(null);
        attachment.setLink("files/temp/example.txt");

        var course = database.addCourseWithOneReleasedTextExercise();
        textExercise = database.getFirstExerciseWithType(course, TextExercise.class);
        lecture = new Lecture();
        lecture.setTitle("test");
        lecture.setDescription("test");
        lecture.setCourse(course);
        lecture = lectureRepository.save(lecture);
        attachment.setLecture(lecture);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachment() throws Exception {
        MvcResult result = request.getMvc().perform(buildCreateAttachment(attachment, "testContent")).andExpect(status().isCreated()).andReturn();
        var actualAttachment = objectMapper.readValue(result.getResponse().getContentAsString(), Attachment.class);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).get();
        assertThat(actualAttachment).isEqualTo(expectedAttachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachment_noFile() throws Exception {
        request.getMvc().perform(buildCreateAttachment(attachment)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @ValueSource(booleans = { true, false })
    void updateAttachment(boolean fileUpdate) throws Exception {
        attachment = attachmentRepository.save(attachment);
        attachment.setName("new name");
        var params = new LinkedMultiValueMap<String, String>();
        var notificationText = "notified!";
        params.add("notificationText", notificationText);

        String fileContent = fileUpdate ? "testContent" : null;

        MvcResult result = request.getMvc().perform(buildUpdateAttachment(attachment.getId(), attachment, fileContent, params)).andExpect(status().isOk()).andReturn();
        var actualAttachment = objectMapper.readValue(result.getResponse().getContentAsString(), Attachment.class);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).get();
        assertThat(actualAttachment.getName()).isEqualTo("new name");
        var ignoringFields = new String[] { "name", "fileService", "prevLink", "lecture.lectureUnits", "lecture.posts", "lecture.course", "lecture.attachments" };
        assertThat(actualAttachment).usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(expectedAttachment);
        verify(groupNotificationService).notifyStudentGroupAboutAttachmentChange(actualAttachment, notificationText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        attachment.setName("new name");
        var actualAttachment = request.get("/api/attachments/" + attachment.getId(), HttpStatus.OK, Attachment.class);
        assertThat(actualAttachment).isEqualTo(attachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAttachmentsForLecture() throws Exception {
        attachment = attachmentRepository.save(attachment);
        var actualAttachments = request.getList("/api/lectures/" + lecture.getId() + "/attachments", HttpStatus.OK, Attachment.class);
        assertThat(actualAttachments).hasSize(1);
        assertThat(actualAttachments.stream().findFirst()).contains(attachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.OK);
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_connectedToExercise() throws Exception {
        attachment.setLecture(null);
        attachment.setExercise(textExercise);
        attachment = attachmentRepository.save(attachment);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_noAttachment() throws Exception {
        request.delete("/api/attachments/-1", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_noCourse() throws Exception {
        attachment = attachmentRepository.save(attachment);
        lecture.setCourse(null);
        lectureRepository.save(lecture);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_notInstructorInACourse() throws Exception {
        var course = courseRepo.save(new Course());
        attachment = attachmentRepository.save(attachment);
        lecture.setCourse(course);
        lectureRepository.save(lecture);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.FORBIDDEN);
    }

    private MockHttpServletRequestBuilder buildCreateAttachment(@NotNull Attachment attachment) throws JsonProcessingException {
        return buildCreateAttachment(attachment, null);
    }

    private MockHttpServletRequestBuilder buildCreateAttachment(@NotNull Attachment attachment, String fileContent) throws JsonProcessingException {
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(attachment));
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/attachments").file(attachmentPart);
        if (fileContent != null) {
            var filePart = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes());
            builder.file(filePart);
        }
        return builder.contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MockHttpServletRequestBuilder buildUpdateAttachment(@NotNull Long id, @NotNull Attachment attachment, String fileContent, LinkedMultiValueMap<String, String> params)
            throws JsonProcessingException {
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(attachment));
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/attachments/" + id).file(attachmentPart);
        if (fileContent != null) {
            var filePart = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes());
            builder.file(filePart);
        }
        return builder.params(params).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }
}
