package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.junit.jupiter.api.AfterEach;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
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

    @Autowired
    private ObjectMapper mapper;

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
        request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isForbidden());
        request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment, "Hello World")).andExpect(status().isForbidden());
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/42", HttpStatus.FORBIDDEN, AttachmentUnit.class);
    }

    private MockHttpServletRequestBuilder buildUpdateAttachmentUnit(@NotNull AttachmentUnit attachmentUnit, @NotNull Attachment attachment) throws Exception {
        return buildUpdateAttachmentUnit(attachmentUnit, attachment, null);
    }

    private MockHttpServletRequestBuilder buildUpdateAttachmentUnit(@NotNull AttachmentUnit attachmentUnit, @NotNull Attachment attachment, String fileContent) throws Exception {
        var attachmentUnitPart = new MockMultipartFile("attachmentUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());

        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/lectures/" + lecture1.getId() + "/attachment-units/" + attachmentUnit.getId());
        if (fileContent != null) {
            var filePart = new MockMultipartFile("file", "", MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes(StandardCharsets.UTF_8));
            builder.file(filePart);
        }

        return builder.file(attachmentUnitPart).file(attachmentPart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MockHttpServletRequestBuilder buildCreateAttachmentUnit(@NotNull AttachmentUnit attachmentUnit, @NotNull Attachment attachment, @NotNull String fileContent)
            throws Exception {
        var attachmentUnitPart = new MockMultipartFile("attachmentUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());
        var filePart = new MockMultipartFile("file", "testFile.pdf", MediaType.TEXT_PLAIN_VALUE, fileContent.getBytes(StandardCharsets.UTF_8));

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lecture1.getId() + "/attachment-units").file(attachmentUnitPart).file(attachmentPart)
                .file(filePart).with(csrf()).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.attachmentUnit.setId(42L);
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.attachmentUnit.setId(42L);
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_asInstructor_shouldCreateAttachmentUnit() throws Exception {
        var result = request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment, "Hello World")).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentUnit.class);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        var persistedAttachment = persistedAttachmentUnit.getAttachment();
        assertThat(persistedAttachment.getId()).isNotNull();
        var updatedAttachmentUnit = attachmentUnitRepository.findById(persistedAttachmentUnit.getId()).get();
        assertThat(updatedAttachmentUnit.getAttachment()).isEqualTo(persistedAttachment);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void createAttachmentUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment, "Hello World")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_asInstructor_shouldUpdateAttachmentUnit() throws Exception {
        var createResult = request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment, "Hello World")).andExpect(status().isCreated()).andReturn();
        var attachmentUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentUnit.class);
        var attachment = attachmentUnit.getAttachment();
        attachmentUnit.setDescription("Changed");
        var updateResult = request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isOk()).andReturn();
        attachmentUnit = mapper.readValue(updateResult.getResponse().getContentAsString(), AttachmentUnit.class);
        assertThat(attachmentUnit.getDescription()).isEqualTo("Changed");
        // testing if bidirectional relationship is kept
        attachmentUnit = attachmentUnitRepository.findById(attachmentUnit.getId()).get();
        attachment = attachmentRepository.findById(attachment.getId()).get();
        assertThat(attachmentUnit.getAttachment()).isEqualTo(attachment);
        assertThat(attachment.getAttachmentUnit()).isEqualTo(attachmentUnit);
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
        request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isOk());

        SecurityUtils.setAuthorizationObject();
        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistAttachmentUnitWithLecture() {
        this.attachmentUnit = attachmentUnitRepository.saveAndFlush(this.attachmentUnit);
        lecture1 = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get();
        lecture1.addLectureUnit(this.attachmentUnit);
        lecture1 = lectureRepository.saveAndFlush(lecture1);
        this.attachmentUnit = (AttachmentUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void updateAttachmentUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        persistAttachmentUnitWithLecture();

        request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isForbidden());
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
        var result = request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment, "Hello World")).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentUnit.class);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();

        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + persistedAttachmentUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/" + persistedAttachmentUnit.getAttachment().getId(), HttpStatus.NOT_FOUND, Attachment.class);
    }
}
