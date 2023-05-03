package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotNull;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.Slide;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

class AttachmentUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "attachmentunitintegrationtest"; // only lower case is supported

    private static final int SLIDE_COUNT = 5;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private SlideRepository slideRepository;

    private Lecture lecture1;

    private Attachment attachment;

    private AttachmentUnit attachmentUnit;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void initTestCase() {
        this.database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.attachment = ModelFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("files/temp/example.txt");
        this.lecture1 = this.database.createCourseWithLecture(true);
        this.attachmentUnit = new AttachmentUnit();
        this.attachmentUnit.setDescription("Lorem Ipsum");

        // Add users that are not in the course
        database.createAndSaveUser(TEST_PREFIX + "student42");
        database.createAndSaveUser(TEST_PREFIX + "tutor42");
        database.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    private void testAllPreAuthorize() throws Exception {
        request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isForbidden());
        request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isForbidden());
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
            var filePart = createAttachmentUnitPdf();
            builder.file(filePart);
        }

        return builder.file(attachmentUnitPart).file(attachmentPart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MockHttpServletRequestBuilder buildCreateAttachmentUnit(@NotNull AttachmentUnit attachmentUnit, @NotNull Attachment attachment) throws Exception {
        var attachmentUnitPart = new MockMultipartFile("attachmentUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());
        var filePart = createAttachmentUnitPdf();

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lecture1.getId() + "/attachment-units").file(attachmentUnitPart).file(attachmentPart)
                .file(filePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    /**
     * Generates an attachment unit pdf file with 5 pages
     *
     * @return MockMultipartFile attachment unit pdf file
     */
    private MockMultipartFile createAttachmentUnitPdf() throws IOException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = new PDDocument()) {

            for (int i = 1; i <= SLIDE_COUNT; i++) {
                document.addPage(new PDPage());
                PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(i - 1));

                if (i == 2) {
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.TIMES_ROMAN, 12);
                    contentStream.newLineAtOffset(25, -15);
                    contentStream.showText("itp20..");
                    contentStream.newLineAtOffset(25, 500);
                    contentStream.showText("Outline");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("First Unit");
                    contentStream.close();
                    continue;
                }
                contentStream.beginText();
                contentStream.setFont(PDType1Font.TIMES_ROMAN, 12);
                contentStream.newLineAtOffset(25, 500);
                String text = "This is the sample document";
                contentStream.showText(text);
                contentStream.endText();
                contentStream.close();
            }
            document.save(outputStream);
            document.close();
            return new MockMultipartFile("file", "lectureFile.pdf", "application/json", outputStream.toByteArray());
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.attachmentUnit.setId(42L);
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.attachmentUnit.setId(42L);
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_asInstructor_shouldCreateAttachmentUnit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        var result = request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentUnit.class);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        var persistedAttachment = persistedAttachmentUnit.getAttachment();
        assertThat(persistedAttachment.getId()).isNotNull();
        var updatedAttachmentUnit = attachmentUnitRepository.findById(persistedAttachmentUnit.getId()).get();
        // Wait for async operation to complete (after attachment unit is saved, the file gets split into slides)
        latch.await(10, TimeUnit.SECONDS);
        assertThat(slideRepository.findAllByAttachmentUnitId(persistedAttachmentUnit.getId())).hasSize(SLIDE_COUNT);
        assertThat(updatedAttachmentUnit.getAttachment()).isEqualTo(persistedAttachment);
        assertThat(updatedAttachmentUnit.getAttachment().getName()).isEqualTo("LoremIpsum");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createAttachmentUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_withUnitId_shouldReturnBadRequest() throws Exception {
        attachmentUnit.setId(99L);
        request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_withAttachmentId_shouldReturnBadRequest() throws Exception {
        attachment.setId(99L);
        request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_asInstructor_shouldUpdateAttachmentUnit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        var createResult = request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var attachmentUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentUnit.class);
        var attachment = attachmentUnit.getAttachment();
        attachmentUnit.setDescription("Changed");
        // Wait for async operation to complete (after attachment unit is saved, the file gets split into slides)
        latch.await(10, TimeUnit.SECONDS);
        assertThat(slideRepository.findAllByAttachmentUnitId(attachmentUnit.getId())).hasSize(SLIDE_COUNT);
        List<Slide> oldSlides = slideRepository.findAllByAttachmentUnitId(attachmentUnit.getId());
        CountDownLatch latch1 = new CountDownLatch(1);
        var updateResult = request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isOk()).andReturn();
        attachmentUnit = mapper.readValue(updateResult.getResponse().getContentAsString(), AttachmentUnit.class);
        assertThat(attachmentUnit.getDescription()).isEqualTo("Changed");
        // Wait for async operation to complete (after attachment unit is updated, the new file gets split into slides)
        latch1.await(10, TimeUnit.SECONDS);
        assertThat(slideRepository.findAllByAttachmentUnitId(attachmentUnit.getId())).hasSize(SLIDE_COUNT);
        List<Slide> updatedSlides = slideRepository.findAllByAttachmentUnitId(attachmentUnit.getId());
        assertThat(oldSlides).isNotEqualTo(updatedSlides);
        // testing if bidirectional relationship is kept
        attachmentUnit = attachmentUnitRepository.findById(attachmentUnit.getId()).get();
        attachment = attachmentRepository.findById(attachment.getId()).get();
        assertThat(attachmentUnit.getAttachment()).isEqualTo(attachment);
        assertThat(attachment.getAttachmentUnit()).isEqualTo(attachmentUnit);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void updateAttachmentUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        persistAttachmentUnitWithLecture();
        request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_withoutLecture_shouldReturnConflict() throws Exception {
        persistAttachmentUnitWithLecture();
        attachmentUnit.setLecture(null);
        request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachmentUnit_withAttachment_shouldDeleteAttachment() throws Exception {
        var result = request.getMvc().perform(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentUnit.class);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        assertThat(slideRepository.findAllByAttachmentUnitId(persistedAttachmentUnit.getId())).hasSize(0);
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + persistedAttachmentUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/" + persistedAttachmentUnit.getId(), HttpStatus.NOT_FOUND, Attachment.class);
    }
}
