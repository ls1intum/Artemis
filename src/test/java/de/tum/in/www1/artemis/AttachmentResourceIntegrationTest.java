package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class AttachmentResourceIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    CustomAuditEventRepository auditEventRepo;

    @Autowired
    AttachmentRepository attachmentRepository;

    @Autowired
    UserRepository userRepo;

    @Autowired
    LectureRepository lectureRepository;

    private Attachment attachment;

    private Lecture lecture;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);

        attachment = new Attachment().attachmentType(AttachmentType.FILE).link("files/temp/example.txt").name("example");

        lecture = new Lecture().title("test").description("test");
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createAttachment() throws Exception {
        var actualAttachment = request.postWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.CREATED);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).get();
        assertThat(actualAttachment).isEqualTo(expectedAttachment);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createAttachment_idExists() throws Exception {
        attachment.setId(1L);
        request.postWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        attachment.setName("new name");
        var actualAttachment = request.putWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.CREATED);
        var expectedAttachment = attachmentRepository.findById(actualAttachment.getId()).get();
        assertThat(actualAttachment.getName()).isEqualTo("new name");
        assertThat(actualAttachment).isEqualToIgnoringGivenFields(expectedAttachment, "name");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateAttachment_noId() throws Exception {
        attachment.setName("new name");
        request.putWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAttachment() throws Exception {
        attachment = attachmentRepository.save(attachment);
        attachment.setName("new name");
        var actualAttachment = request.get("/api/attachments/" + attachment.getId(), HttpStatus.OK, Attachment.class);
        assertThat(actualAttachment).isEqualTo(attachment);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getAttachmentsForLecture() throws Exception {
        lecture = lectureRepository.save(lecture);
        attachment.setLecture(lecture);
        attachment = attachmentRepository.save(attachment);
        var actualAttachments = request.getList("/api/lectures/" + lecture.getId() + "/attachments", HttpStatus.OK, Attachment.class);
        assertThat(actualAttachments.size()).isEqualTo(1);
        assertThat(actualAttachments.stream().findFirst().get()).isEqualTo(attachment);
    }
}
