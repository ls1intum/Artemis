package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

class AttachmentResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Attachment attachment;

    private Lecture lecture;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(0, 1, 0, 1);

        attachment = ModelFactory.generateAttachment(null);
        attachment.setLink("files/temp/example.txt");

        var course = database.addCourseWithOneReleasedTextExercise();
        textExercise = (TextExercise) exerciseRepository.findAll().get(0);
        lecture = new Lecture();
        lecture.setTitle("test");
        lecture.setDescription("test");
        lecture.setCourse(course);
        lecture = lectureRepository.save(lecture);
        attachment.setLecture(lecture);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createAttachment() throws Exception {
        var actualAttachment = request.postWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.CREATED);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).get();
        assertThat(actualAttachment).isEqualTo(expectedAttachment);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createAttachment_idExists() throws Exception {
        attachment.setId(1L);
        request.postWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        attachment.setName("new name");
        var params = new LinkedMultiValueMap<String, String>();
        var notificationText = "notified!";
        params.add("notificationText", notificationText);

        var actualAttachment = request.putWithResponseBodyAndParams("/api/attachments", attachment, Attachment.class, HttpStatus.OK, params);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).get();
        assertThat(actualAttachment.getName()).isEqualTo("new name");
        var ignoringFields = new String[] { "name", "fileService", "prevLink", "lecture.lectureUnits", "lecture.posts", "lecture.course", "lecture.attachments" };
        assertThat(actualAttachment).usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(expectedAttachment);
        verify(groupNotificationService).notifyStudentGroupAboutAttachmentChange(actualAttachment, notificationText);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateAttachment_noId() throws Exception {
        attachment.setName("new name");
        request.putWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        attachment.setName("new name");
        var actualAttachment = request.get("/api/attachments/" + attachment.getId(), HttpStatus.OK, Attachment.class);
        assertThat(actualAttachment).isEqualTo(attachment);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAttachmentsForLecture() throws Exception {
        attachment = attachmentRepository.save(attachment);
        var actualAttachments = request.getList("/api/lectures/" + lecture.getId() + "/attachments", HttpStatus.OK, Attachment.class);
        assertThat(actualAttachments).hasSize(1);
        assertThat(actualAttachments.stream().findFirst()).contains(attachment);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.OK);
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_connectedToExercise() throws Exception {
        attachment.setLecture(null);
        attachment.setExercise(textExercise);
        attachment = attachmentRepository.save(attachment);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_noAttachment() throws Exception {
        request.delete("/api/attachments/1", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_noCourse() throws Exception {
        attachment = attachmentRepository.save(attachment);
        lecture.setCourse(null);
        lectureRepository.save(lecture);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteAttachment_notInstructorInACourse() throws Exception {
        var course = courseRepo.save(new Course());
        attachment = attachmentRepository.save(attachment);
        lecture.setCourse(course);
        lectureRepository.save(lecture);
        request.delete("/api/attachments/" + attachment.getId(), HttpStatus.FORBIDDEN);
    }
}
