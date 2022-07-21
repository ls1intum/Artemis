package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

class AttachmentUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Lecture lecture1;

    private Attachment attachment;

    private AttachmentUnit attachmentUnit;

    @BeforeEach
    void initTestCase() throws Exception {
        this.database.addUsers(1, 1, 0, 1);
        this.attachment = ModelFactory.generateAttachment(null);
        this.attachment.setLink("files/temp/example.txt");
        this.lecture1 = this.database.createCourseWithLecture(true);
        this.attachmentUnit = new AttachmentUnit();
        this.attachmentUnit.setDescription("Lorem Ipsum");

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/attachment-units", attachmentUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture1.getId() + "/attachment-units", attachmentUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/0", HttpStatus.FORBIDDEN, AttachmentUnit.class);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_asInstructor_shouldCreateAttachmentUnit() throws Exception {
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
    void createAttachmentUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/attachment-units", attachmentUnit, AttachmentUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_asInstructor_shouldUpdateAttachmentUnit() throws Exception {
        persistAttachmentUnitWithLecture();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_asInstructor_shouldKeepOrdering() throws Exception {
        persistAttachmentUnitWithLecture();

        // Add a second lecture unit
        AttachmentUnit attachmentUnit = database.createAttachmentUnit(false);
        lecture1.addLectureUnit(attachmentUnit);
        lecture1 = lectureRepository.save(lecture1);

        List<LectureUnit> orderedUnits = lecture1.getLectureUnits();

        // Updating the lecture unit should not influence order
        request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units", attachmentUnit, AttachmentUnit.class, HttpStatus.OK);

        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistAttachmentUnitWithLecture() {
        this.attachmentUnit = attachmentUnitRepository.save(this.attachmentUnit);
        lecture1 = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get();
        lecture1.addLectureUnit(this.attachmentUnit);
        lecture1 = lectureRepository.save(lecture1);
        this.attachmentUnit = (AttachmentUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void updateAttachmentUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        persistAttachmentUnitWithLecture();
        this.attachment.setAttachmentUnit(this.attachmentUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentUnit.setDescription("Changed");
        this.attachmentUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units", this.attachmentUnit, AttachmentUnit.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_noId_shouldReturnBadRequest() throws Exception {
        persistAttachmentUnitWithLecture();

        this.attachment.setAttachmentUnit(this.attachmentUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentUnit.setDescription("Changed");
        this.attachmentUnit.setId(null);
        this.attachmentUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units", this.attachmentUnit, AttachmentUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAttachmentUnit_correctId_shouldReturnAttachmentUnit() throws Exception {
        persistAttachmentUnitWithLecture();

        this.attachmentUnit.setAttachment(this.attachment);
        this.attachment.setAttachmentUnit(this.attachmentUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentUnit = this.attachmentUnitRepository.save(this.attachmentUnit);

        // 1. check the database call directly
        this.attachmentUnit = this.attachmentUnitRepository.findByIdElseThrow(this.attachmentUnit.getId());
        assertThat(this.attachmentUnit.getAttachment()).isEqualTo(this.attachment);

        // 2. check the REST call
        this.attachmentUnit = request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/" + this.attachmentUnit.getId(), HttpStatus.OK, AttachmentUnit.class);
        assertThat(this.attachmentUnit.getAttachment()).isEqualTo(this.attachment);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteAttachmentUnit_withAttachment_shouldDeleteAttachment() throws Exception {
        var persistedAttachmentUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/attachment-units", attachmentUnit, AttachmentUnit.class,
                HttpStatus.CREATED);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        this.attachment.setAttachmentUnit(persistedAttachmentUnit);
        var persistedAttachment = request.postWithResponseBody("/api/attachments", attachment, Attachment.class, HttpStatus.CREATED);

        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + persistedAttachmentUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/" + persistedAttachment.getId(), HttpStatus.NOT_FOUND, Attachment.class);
    }
}
