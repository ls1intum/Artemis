package de.tum.cit.aet.artemis.lecture;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AttachmentVideoUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "attachmentunitintegrationtest"; // only lower case is supported

    private static final int SLIDE_COUNT = 3;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentUnitTestRepository attachmentUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private SlideTestRepository slideRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    private Lecture lecture1;

    private Attachment attachment;

    private AttachmentVideoUnit attachmentVideoUnit;

    private Competency competency;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.attachment = LectureFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("temp/example.txt");
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        this.attachmentVideoUnit = new AttachmentVideoUnit();
        this.attachmentVideoUnit.setDescription("Lorem Ipsum");

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        competency = competencyUtilService.createCompetency(lecture1.getCourse());
    }

    private void testAllPreAuthorize() throws Exception {
        request.performMvcRequest(buildUpdateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
        request.performMvcRequest(buildCreateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-units/42", HttpStatus.FORBIDDEN, AttachmentVideoUnit.class);
    }

    private MockHttpServletRequestBuilder buildUpdateAttachmentUnit(@NotNull AttachmentVideoUnit attachmentVideoUnit, @NotNull Attachment attachment) throws Exception {
        return buildUpdateAttachmentUnit(attachmentVideoUnit, attachment, null, true);
    }

    private MockHttpServletRequestBuilder buildUpdateAttachmentUnit(@NotNull AttachmentVideoUnit attachmentVideoUnit, @NotNull Attachment attachment, String fileContent,
            boolean contentType) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = buildUpdateAttachmentUnit(attachmentVideoUnit, attachment, fileContent);
        if (contentType) {
            builder.contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        }
        return builder;
    }

    private MockMultipartHttpServletRequestBuilder buildUpdateAttachmentUnit(@NotNull AttachmentVideoUnit attachmentVideoUnit, @NotNull Attachment attachment, String fileContent)
            throws Exception {
        var attachmentUnitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentVideoUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());

        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-units/" + attachmentVideoUnit.getId());
        if (fileContent != null) {
            var filePart = createAttachmentUnitPdf();
            builder.file(filePart);
        }

        return builder.file(attachmentUnitPart).file(attachmentPart);
    }

    private MockHttpServletRequestBuilder buildCreateAttachmentUnit(@NotNull AttachmentVideoUnit attachmentVideoUnit, @NotNull Attachment attachment) throws Exception {
        var attachmentUnitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentVideoUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());
        var filePart = createAttachmentUnitPdf();

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-units").file(attachmentUnitPart).file(attachmentPart)
                .file(filePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    /**
     * Generates an attachment unit pdf file with 5 pages
     *
     * @return MockMultipartFile attachment unit pdf file
     */
    private MockMultipartFile createAttachmentUnitPdf() throws IOException {

        var font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = new PDDocument()) {

            for (int i = 1; i <= SLIDE_COUNT; i++) {
                document.addPage(new PDPage());
                PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(i - 1));

                if (i == 2) {
                    contentStream.beginText();
                    contentStream.setFont(font, 12);
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
                contentStream.setFont(font, 12);
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
        this.attachmentVideoUnit.setId(42L);
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.attachmentVideoUnit.setId(42L);
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureAttachmentUnitWithSameFileName() throws Exception {
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentUnit(true);
        lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(attachmentVideoUnit));

        String fileName = Path.of(attachmentVideoUnit.getAttachment().getLink()).getFileName().toString();
        MockMultipartHttpServletRequestBuilder attachmentUnitBuilder = buildUpdateAttachmentUnit(attachmentVideoUnit, attachmentVideoUnit.getAttachment(), null);
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/json", "test".getBytes());
        attachmentUnitBuilder.file(file).contentType(MediaType.MULTIPART_FORM_DATA_VALUE).param("keepFilename", "true");
        AttachmentVideoUnit updatedAttachmentVideoUnit = request.getObjectMapper()
                .readValue(request.performMvcRequest(attachmentUnitBuilder).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), AttachmentVideoUnit.class);
        String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, updatedAttachmentVideoUnit.getAttachment().getLink());
        request.getFile(requestUrl, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_asInstructor_shouldCreateAttachmentUnit() throws Exception {
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var result = request.performMvcRequest(buildCreateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        var persistedAttachment = persistedAttachmentUnit.getAttachment();
        assertThat(persistedAttachment.getId()).isNotNull();
        var updatedAttachmentUnit = attachmentUnitRepository.findOneWithCompetencyLinksById(persistedAttachmentUnit.getId());
        // Wait for async operation to complete (after attachment unit is saved, the file gets split into slides)
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(persistedAttachmentUnit.getId())).hasSize(SLIDE_COUNT));
        assertThat(updatedAttachmentUnit.getAttachment()).isEqualTo(persistedAttachment);
        assertThat(updatedAttachmentUnit.getAttachment().getName()).isEqualTo("LoremIpsum");
        assertThat(updatedAttachmentUnit.getCompetencyLinks()).anyMatch(link -> link.getCompetency().getId().equals(competency.getId()));
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(updatedAttachmentUnit));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createAttachmentUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.performMvcRequest(buildCreateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_withUnitId_shouldReturnBadRequest() throws Exception {
        attachmentVideoUnit.setId(99L);
        request.performMvcRequest(buildCreateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnit_withAttachmentId_shouldReturnBadRequest() throws Exception {
        attachment.setId(99L);
        request.performMvcRequest(buildCreateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_asInstructor_shouldUpdateAttachmentUnit() throws Exception {
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var createResult = request.performMvcRequest(buildCreateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var attachmentUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        var attachment = attachmentUnit.getAttachment();
        attachmentUnit.setDescription("Changed");
        // Wait for async operation to complete (after attachment unit is saved, the file gets split into slides)
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(attachmentUnit.getId())).hasSize(SLIDE_COUNT));
        List<Slide> oldSlides = slideRepository.findAllByAttachmentVideoUnitId(attachmentUnit.getId());
        var updateResult = request.performMvcRequest(buildUpdateAttachmentUnit(attachmentUnit, attachment, "new File", true)).andExpect(status().isOk()).andReturn();
        AttachmentVideoUnit attachmentVideoUnit1 = mapper.readValue(updateResult.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        assertThat(attachmentVideoUnit1.getDescription()).isEqualTo("Changed");
        // Wait for async operation to complete (after attachment unit is updated, the new file gets split into slides)
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit1.getId())).hasSize(SLIDE_COUNT));
        List<Slide> updatedSlides = slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit1.getId());
        assertThat(oldSlides).isNotEqualTo(updatedSlides);
        // testing if bidirectional relationship is kept
        AttachmentVideoUnit attachmentVideoUnit2 = attachmentUnitRepository.findById(attachmentVideoUnit1.getId()).orElseThrow();
        attachment = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertThat(attachmentVideoUnit2.getAttachment()).isEqualTo(attachment);
        assertThat(attachment.getAttachmentVideoUnit()).isEqualTo(attachmentVideoUnit2);
        assertThat(attachmentVideoUnit1.getCompetencyLinks()).anyMatch(link -> link.getCompetency().getId().equals(competency.getId()));
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(attachmentUnit), eq(Optional.of(attachmentUnit)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_asInstructor_shouldKeepOrdering() throws Exception {
        persistAttachmentUnitWithLecture();

        // Add a second lecture unit
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentUnit(false);
        lecture1.addLectureUnit(attachmentVideoUnit);
        lecture1 = lectureRepository.save(lecture1);

        List<LectureUnit> orderedUnits = lecture1.getLectureUnits();

        // Updating the lecture unit should not influence order
        request.performMvcRequest(buildUpdateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isOk());

        SecurityUtils.setAuthorizationObject();
        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistAttachmentUnitWithLecture() {
        this.attachmentVideoUnit = attachmentUnitRepository.saveAndFlush(this.attachmentVideoUnit);
        lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow();
        lecture1.addLectureUnit(this.attachmentVideoUnit);
        lecture1 = lectureRepository.saveAndFlush(lecture1);
        this.attachmentVideoUnit = (AttachmentVideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream()
                .findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void updateAttachmentUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        persistAttachmentUnitWithLecture();
        request.performMvcRequest(buildUpdateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_withoutAttachment_shouldUpdateAttachmentUnit() throws Exception {
        persistAttachmentUnitWithLecture();
        request.performMvcRequest(buildUpdateAttachmentUnit(attachmentVideoUnit, null)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentUnit_correctId_shouldReturnAttachmentVideoUnit() throws Exception {
        persistAttachmentUnitWithLecture();

        this.attachmentVideoUnit.setAttachment(this.attachment);
        this.attachment.setAttachmentVideoUnit(this.attachmentVideoUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentVideoUnit = this.attachmentUnitRepository.save(this.attachmentVideoUnit);
        competencyUtilService.linkLectureUnitToCompetency(competency, attachmentVideoUnit);

        // 1. check the database call directly
        this.attachmentVideoUnit = this.attachmentUnitRepository.findByIdElseThrow(this.attachmentVideoUnit.getId());
        assertThat(this.attachmentVideoUnit.getAttachment()).isEqualTo(this.attachment);

        // 2. check the REST call
        this.attachmentVideoUnit = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-units/" + this.attachmentVideoUnit.getId(), HttpStatus.OK,
                AttachmentVideoUnit.class);
        assertThat(this.attachmentVideoUnit.getAttachment()).isEqualTo(this.attachment);
        assertThat(this.attachmentVideoUnit.getCompetencyLinks()).anyMatch(link -> link.getCompetency().getId().equals(competency.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachmentUnit_withAttachment_shouldDeleteAttachment() throws Exception {
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var result = request.performMvcRequest(buildCreateAttachmentUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        assertThat(persistedAttachmentUnit.getId()).isNotNull();
        assertThat(slideRepository.findAllByAttachmentVideoUnitId(persistedAttachmentUnit.getId())).hasSize(0);
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + persistedAttachmentUnit.getId(), HttpStatus.OK);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-units/" + persistedAttachmentUnit.getId(), HttpStatus.NOT_FOUND, AttachmentVideoUnit.class);
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(persistedAttachmentUnit), eq(Optional.empty()));
    }
}
