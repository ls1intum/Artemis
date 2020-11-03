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
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class AttachmentUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    AttachmentRepository attachmentRepository;

    @Autowired
    AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    UserRepository userRepository;

    Lecture lecture1;

    Attachment attachment;

    AttachmentUnit attachmentUnit;

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(1, 1, 1);
        this.attachment = new Attachment().attachmentType(AttachmentType.FILE).link("files/temp/example.txt").name("example");
        this.lecture1 = this.database.createCourseWithLecture(true);
        this.attachmentUnit = new AttachmentUnit();
        this.attachmentUnit.setDescription("Lorem Ipsum");
        this.attachmentUnit.setLecture(this.lecture1);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/attachment-units", attachmentUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture1.getId() + "/attachment-units", attachmentUnit, HttpStatus.FORBIDDEN);
        request.get("/api/attachment-units/0", HttpStatus.FORBIDDEN, AttachmentUnit.class);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createAttachmentUnit_asInstructor_shouldCreateAttachmentUnit() throws Exception {
        var persistedAttachmentUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/attachment-units", attachmentUnit, AttachmentUnit.class,
                HttpStatus.CREATED);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        this.attachment.setAttachmentUnit(persistedAttachmentUnit);
        var persistedAttachment = request.postWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.CREATED);
        assertThat(persistedAttachment.getId()).isNotNull();
        assertThat(persistedAttachment.getAttachmentUnit()).isEqualTo(persistedAttachmentUnit);
        var updatedAttachmentUnit = attachmentUnitRepository.findById(persistedAttachmentUnit.getId()).get();
        assertThat(updatedAttachmentUnit.getAttachment()).isEqualTo(persistedAttachment);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void createAttachmentUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/attachment-units", attachmentUnit, AttachmentUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateAttachmentUnit_asInstructor_shouldUpdateAttachmentUnit() throws Exception {
        this.attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        this.attachment.setAttachmentUnit(this.attachmentUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentUnit.setDescription("Changed");
        this.attachmentUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units", this.attachmentUnit, AttachmentUnit.class, HttpStatus.OK);
        assertThat(this.attachmentUnit.getDescription()).isEqualTo("Changed");
        // testing if bidirectional relationship is kept
        this.attachmentUnit = attachmentUnitRepository.findById(this.attachmentUnit.getId()).get();
        this.attachment = attachmentRepository.findById(this.attachment.getId()).get();
        assertThat(this.attachmentUnit.getAttachment()).isEqualTo(this.attachment);
        assertThat(this.attachment.getAttachmentUnit()).isEqualTo(this.attachmentUnit);

    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void updateAttachmentUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        this.attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        this.attachment.setAttachmentUnit(this.attachmentUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentUnit.setDescription("Changed");
        this.attachmentUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units", this.attachmentUnit, AttachmentUnit.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateAttachmentUnit_noId_shouldReturnBadRequest() throws Exception {
        this.attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        this.attachment.setAttachmentUnit(this.attachmentUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentUnit.setDescription("Changed");
        this.attachmentUnit.setId(null);
        this.attachmentUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units", this.attachmentUnit, AttachmentUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAttachmentUnit_correctId_shouldReturnAttachmentUnit() throws Exception {
        this.attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        this.attachment.setAttachmentUnit(this.attachmentUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentUnit = request.get("/api/attachment-units/" + this.attachmentUnit.getId(), HttpStatus.OK, AttachmentUnit.class);
        assertThat(this.attachmentUnit.getAttachment()).isEqualTo(this.attachment);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteAttachmentUnit_withAttachment_shouldDeleteAttachment() throws Exception {
        var persistedAttachmentUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/attachment-units", attachmentUnit, AttachmentUnit.class,
                HttpStatus.CREATED);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        this.attachment.setAttachmentUnit(persistedAttachmentUnit);
        var persistedAttachment = request.postWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.CREATED);

        request.delete("/api/lecture-units/" + persistedAttachmentUnit.getId(), HttpStatus.OK);
        request.get("/api/attachments/" + persistedAttachment.getId(), HttpStatus.NOT_FOUND, Attachment.class);

    }

}
