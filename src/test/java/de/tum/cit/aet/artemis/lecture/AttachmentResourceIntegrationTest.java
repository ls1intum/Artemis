package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AttachmentResourceIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "attachmentresourceintegrationtest";

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Attachment attachment;

    private Lecture lecture;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 1, 0, 1);

        attachment = LectureFactory.generateAttachment(null);
        attachment = attachmentRepository.save(attachment);
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        lecture = new Lecture();
        lecture.setTitle("test");
        lecture.setDescription("test");
        lecture.setCourse(course);
        lecture = lectureRepository.save(lecture);
        attachment.setLecture(lecture);
        attachment.setLink("attachments/lecture/" + lecture.getId() + "/example.txt");
        attachment = attachmentRepository.save(attachment);
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

        var actualAttachment = request.putWithMultipartFile("/api/lecture/attachments/" + attachment.getId(), attachment, "attachment", file, AttachmentDTO.class, HttpStatus.OK,
                params);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.id()).orElseThrow();

        assertThat(actualAttachment.name()).isEqualTo("new name");
        assertThat(actualAttachment.id()).isEqualTo(expectedAttachment.getId());
        assertThat(actualAttachment.link()).isEqualTo(expectedAttachment.getLink());
        assertThat(actualAttachment.version()).isEqualTo(expectedAttachment.getVersion());
        assertThat(actualAttachment.attachmentType()).isEqualTo(expectedAttachment.getAttachmentType());
        assertThat(actualAttachment.lecture().id()).isEqualTo(lecture.getId());
        verify(groupNotificationService).notifyStudentGroupAboutAttachmentChange(expectedAttachment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        attachment.setName("new name");
        var actualAttachment = request.get("/api/lecture/attachments/" + attachment.getId(), HttpStatus.OK, AttachmentDTO.class);
        assertThat(actualAttachment.id()).isEqualTo(attachment.getId());
        assertThat(actualAttachment.link()).isEqualTo(attachment.getLink());
        assertThat(actualAttachment.lecture().id()).isEqualTo(lecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAttachmentsForLecture() throws Exception {
        attachment = attachmentRepository.save(attachment);
        var actualAttachments = request.getList("/api/lecture/lectures/" + lecture.getId() + "/attachments", HttpStatus.OK, AttachmentDTO.class);
        assertThat(actualAttachments).hasSize(1);
        assertThat(actualAttachments.getFirst().id()).isEqualTo(attachment.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        request.delete("/api/lecture/attachments/" + attachment.getId(), HttpStatus.OK);
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_connectedToExercise() throws Exception {
        attachment.setLecture(null);
        attachment.setExercise(textExercise);
        attachment = attachmentRepository.save(attachment);
        request.delete("/api/lecture/attachments/" + attachment.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_noAttachment() throws Exception {
        request.delete("/api/lecture/attachments/-1", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_noCourse() throws Exception {
        attachment = attachmentRepository.save(attachment);
        lecture.setCourse(null);
        lectureRepository.save(lecture);
        request.delete("/api/lecture/attachments/" + attachment.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_notInstructorInACourse() throws Exception {
        var course = courseRepository.save(new Course());
        attachment = attachmentRepository.save(attachment);
        lecture.setCourse(course);
        lectureRepository.save(lecture);
        request.delete("/api/lecture/attachments/" + attachment.getId(), HttpStatus.FORBIDDEN);
    }
}
