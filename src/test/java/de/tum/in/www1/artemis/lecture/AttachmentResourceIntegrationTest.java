package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;

class AttachmentResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "attachmentresourceintegrationtest";

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private Attachment attachment;

    private Lecture lecture;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 1, 0, 1);

        attachment = LectureFactory.generateAttachment(null);
        attachment.setLink("/api/files/temp/example.txt");

        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
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
        Attachment actualAttachment = request.postWithMultipartFile("/api/attachments", attachment, "attachment",
                new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "testContent".getBytes()), Attachment.class, HttpStatus.CREATED);
        assertThat(actualAttachment.getLink()).isNotNull();
        MvcResult file = request.performMvcRequest(get(actualAttachment.getLink())).andExpect(status().isOk()).andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andReturn();
        assertThat(file.getResponse().getContentAsByteArray()).isNotEmpty();
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).orElseThrow();
        assertThat(actualAttachment).isEqualTo(expectedAttachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachment_noFile() throws Exception {
        request.postWithMultipartFile("/api/attachments", attachment, "attachment", null, Attachment.class, HttpStatus.BAD_REQUEST);
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
        MockMultipartFile file = fileUpdate ? new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE, "testContent".getBytes()) : null;

        var actualAttachment = request.putWithMultipartFile("/api/attachments/" + attachment.getId(), attachment, "attachment", file, Attachment.class, HttpStatus.OK, params);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).orElseThrow();

        assertThat(actualAttachment.getName()).isEqualTo("new name");
        var ignoringFields = new String[] { "name", "fileService", "filePathService", "entityFileService", "prevLink", "lecture.lectureUnits", "lecture.posts", "lecture.course",
                "lecture.attachments" };
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
}
