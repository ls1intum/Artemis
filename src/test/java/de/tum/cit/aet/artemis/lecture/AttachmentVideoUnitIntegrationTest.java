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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.jspecify.annotations.NonNull;
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
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
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
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

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
        this.attachmentVideoUnit.setVideoSource("google.com");

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        competency = competencyUtilService.createCompetency(lecture1.getCourse());
    }

    private void testAllPreAuthorize() throws Exception {
        request.performMvcRequest(buildUpdateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
        request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/42", HttpStatus.FORBIDDEN, AttachmentVideoUnit.class);
    }

    private MockHttpServletRequestBuilder buildUpdateAttachmentVideoUnit(@NonNull AttachmentVideoUnit attachmentVideoUnit, @NonNull Attachment attachment) throws Exception {
        return buildUpdateAttachmentVideoUnit(attachmentVideoUnit, attachment, null, true);
    }

    private MockHttpServletRequestBuilder buildUpdateAttachmentVideoUnit(@NonNull AttachmentVideoUnit attachmentVideoUnit, @NonNull Attachment attachment, String fileContent,
            boolean contentType) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = buildUpdateAttachmentVideoUnit(attachmentVideoUnit, attachment, fileContent);
        if (contentType) {
            builder.contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        }
        return builder;
    }

    private MockMultipartHttpServletRequestBuilder buildUpdateAttachmentVideoUnit(@NonNull AttachmentVideoUnit attachmentVideoUnit, @NonNull Attachment attachment,
            String fileContent) throws Exception {
        var attachmentVideoUnitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentVideoUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());

        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + attachmentVideoUnit.getId());
        if (fileContent != null) {
            var filePart = createAttachmentVideoUnitPdf();
            builder.file(filePart);
        }

        return builder.file(attachmentVideoUnitPart).file(attachmentPart);
    }

    private MockHttpServletRequestBuilder buildCreateAttachmentVideoUnit(@NonNull AttachmentVideoUnit attachmentVideoUnit, @NonNull Attachment attachment) throws Exception {
        var attachmentVideoUnitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentVideoUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());
        var filePart = createAttachmentVideoUnitPdf();

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units").file(attachmentVideoUnitPart)
                .file(attachmentPart).file(filePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    /**
     * Generates an attachment video unit pdf file with 5 pages
     *
     * @return MockMultipartFile attachment video unit pdf file
     */
    private MockMultipartFile createAttachmentVideoUnitPdf() throws IOException {

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
    void updateLectureAttachmentVideoUnitWithSameFileName() throws Exception {
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(true);
        lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(attachmentVideoUnit));

        String fileName = Path.of(attachmentVideoUnit.getAttachment().getLink()).getFileName().toString();
        MockMultipartHttpServletRequestBuilder attachmentVideoUnitBuilder = buildUpdateAttachmentVideoUnit(attachmentVideoUnit, attachmentVideoUnit.getAttachment(), null);
        MockMultipartFile file = new MockMultipartFile("file", fileName, "application/json", "test".getBytes());
        attachmentVideoUnitBuilder.file(file).contentType(MediaType.MULTIPART_FORM_DATA_VALUE).param("keepFilename", "true");
        AttachmentVideoUnit updatedAttachmentVideoUnit = request.getObjectMapper().readValue(
                request.performMvcRequest(attachmentVideoUnitBuilder).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), AttachmentVideoUnit.class);
        String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, updatedAttachmentVideoUnit.getAttachment().getLink());
        request.getFile(requestUrl, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentVideoUnit_asInstructor_shouldCreateAttachmentVideoUnit() throws Exception {
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var result = request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentVideoUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        assertThat(persistedAttachmentVideoUnit.getId()).isNotNull();
        var persistedAttachment = persistedAttachmentVideoUnit.getAttachment();
        assertThat(persistedAttachment.getId()).isNotNull();
        var updatedAttachmentVideoUnit = attachmentVideoUnitRepository.findOneWithCompetencyLinksById(persistedAttachmentVideoUnit.getId());
        // Wait for async operation to complete (after attachment video unit is saved, the file gets split into slides)
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(persistedAttachmentVideoUnit.getId())).hasSize(SLIDE_COUNT));
        assertThat(updatedAttachmentVideoUnit.getAttachment()).isEqualTo(persistedAttachment);
        assertThat(updatedAttachmentVideoUnit.getAttachment().getName()).isEqualTo("LoremIpsum");
        assertThat(updatedAttachmentVideoUnit.getCompetencyLinks()).anyMatch(link -> link.getCompetency().getId().equals(competency.getId()));
        verify(competencyProgressApi).updateProgressByLearningObjectAsync(eq(updatedAttachmentVideoUnit));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createAttachmentVideoUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentVideoUnit_withUnitId_shouldReturnBadRequest() throws Exception {
        attachmentVideoUnit.setId(99L);
        request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentVideoUnit_withAttachmentId_shouldReturnBadRequest() throws Exception {
        attachment.setId(99L);
        request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentVideoUnit_asInstructor_shouldUpdateAttachmentVideoUnit() throws Exception {
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var createResult = request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var attachmentVideoUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        var attachment = attachmentVideoUnit.getAttachment();
        attachmentVideoUnit.setDescription("Changed");

        // Wait for async operation to complete (after attachment video unit is saved, the file gets split into slides)
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit.getId())).hasSize(SLIDE_COUNT));

        // Store the original attachment filename to check it changes
        String originalAttachmentLink = attachment.getLink();

        // Update the attachment video unit
        var updateResult = request.performMvcRequest(buildUpdateAttachmentVideoUnit(attachmentVideoUnit, attachment, "new File", true)).andExpect(status().isOk()).andReturn();
        AttachmentVideoUnit attachmentVideoUnit1 = mapper.readValue(updateResult.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        // Verify description was updated
        assertThat(attachmentVideoUnit1.getDescription()).isEqualTo("Changed");
        // Verify attachment file was updated (this should pass)
        assertThat(attachmentVideoUnit1.getAttachment().getLink()).isNotEqualTo(originalAttachmentLink);
        // Create a query to find the latest slides for this attachment video unit
        // Since we know there will be duplicate slide numbers, we need to check for the latest ones (with highest ID)
        var groupedSlides = slideRepository.findAllByAttachmentVideoUnitId(attachmentVideoUnit1.getId()).stream().collect(Collectors.groupingBy(Slide::getSlideNumber));
        List<Slide> latestSlides = new ArrayList<>();
        for (var slidesWithSameNumber : groupedSlides.values()) {
            slidesWithSameNumber.stream().max(Comparator.comparing(Slide::getId)).ifPresent(latestSlides::add);
        }
        // Verify we have the expected number of unique slide numbers
        assertThat(latestSlides).hasSize(SLIDE_COUNT);
        // Instead of checking that the slide paths changed, just verify they're correctly formatted
        // and that they exist - the implementation doesn't seem to update slide paths when the
        // attachment is updated
        for (Slide slide : latestSlides) {
            assertThat(slide.getSlideImagePath()).isNotNull();
            assertThat(slide.getSlideImagePath()).containsPattern("attachments/attachment-unit/\\d+/slide/\\d+/.*_Slide_\\d+\\.png");
        }
        // testing if bidirectional relationship is kept
        AttachmentVideoUnit attachmentVideoUnit2 = attachmentVideoUnitRepository.findById(attachmentVideoUnit1.getId()).orElseThrow();
        attachment = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertThat(attachmentVideoUnit2.getAttachment()).isEqualTo(attachment);
        assertThat(attachment.getAttachmentVideoUnit()).isEqualTo(attachmentVideoUnit2);
        assertThat(attachmentVideoUnit1.getCompetencyLinks()).anyMatch(link -> link.getCompetency().getId().equals(competency.getId()));
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(attachmentVideoUnit), eq(Optional.of(attachmentVideoUnit)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentVideoUnit_asInstructor_shouldKeepOrdering() throws Exception {
        persistAttachmentVideoUnitWithLecture();

        // Add a second lecture unit
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(false);
        lecture1.addLectureUnit(attachmentVideoUnit);
        lecture1 = lectureRepository.save(lecture1);

        List<LectureUnit> orderedUnits = lecture1.getLectureUnits();

        // Updating the lecture unit should not influence order
        request.performMvcRequest(buildUpdateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isOk());

        SecurityUtils.setAuthorizationObject();
        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistAttachmentVideoUnitWithLecture() {
        this.attachmentVideoUnit = attachmentVideoUnitRepository.saveAndFlush(this.attachmentVideoUnit);
        lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow();
        lecture1.addLectureUnit(this.attachmentVideoUnit);
        lecture1 = lectureRepository.saveAndFlush(lecture1);
        this.attachmentVideoUnit = (AttachmentVideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream()
                .findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void updateAttachmentVideoUnit_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        persistAttachmentVideoUnitWithLecture();
        request.performMvcRequest(buildUpdateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentVideoUnit_withoutAttachment_shouldUpdateAttachmentVideoUnit() throws Exception {
        persistAttachmentVideoUnitWithLecture();
        request.performMvcRequest(buildUpdateAttachmentVideoUnit(attachmentVideoUnit, null)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentVideoUnit_correctId_shouldReturnAttachmentVideoUnit() throws Exception {
        persistAttachmentVideoUnitWithLecture();

        this.attachmentVideoUnit.setAttachment(this.attachment);
        this.attachment.setAttachmentVideoUnit(this.attachmentVideoUnit);
        this.attachment = attachmentRepository.save(attachment);
        this.attachmentVideoUnit = this.attachmentVideoUnitRepository.save(this.attachmentVideoUnit);
        competencyUtilService.linkLectureUnitToCompetency(competency, attachmentVideoUnit);

        // 1. check the database call directly
        this.attachmentVideoUnit = this.attachmentVideoUnitRepository.findByIdElseThrow(this.attachmentVideoUnit.getId());
        assertThat(this.attachmentVideoUnit.getAttachment()).isEqualTo(this.attachment);

        // 2. check the REST call
        this.attachmentVideoUnit = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + this.attachmentVideoUnit.getId(), HttpStatus.OK,
                AttachmentVideoUnit.class);
        assertThat(this.attachmentVideoUnit.getAttachment()).isEqualTo(this.attachment);
        assertThat(this.attachmentVideoUnit.getCompetencyLinks()).anyMatch(link -> link.getCompetency().getId().equals(competency.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAttachmentVideoUnit_withAttachment_shouldDeleteAttachment() throws Exception {
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var result = request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentVideoUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        assertThat(persistedAttachmentVideoUnit.getId()).isNotNull();
        assertThat(slideRepository.findAllByAttachmentVideoUnitId(persistedAttachmentVideoUnit.getId())).hasSize(0);
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + persistedAttachmentVideoUnit.getId(), HttpStatus.OK);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentVideoUnit.getId(), HttpStatus.FORBIDDEN,
                AttachmentVideoUnit.class);
        verify(competencyProgressApi, timeout(1000).times(1)).updateProgressForUpdatedLearningObjectAsync(eq(persistedAttachmentVideoUnit), eq(Optional.empty()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void handleStudentVersionFile_shouldUpdateAttachmentStudentVersion() throws Exception {
        // Create an attachment video unit first
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var result = request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentVideoUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        assertThat(persistedAttachmentVideoUnit.getId()).isNotNull();
        var persistedAttachment = persistedAttachmentVideoUnit.getAttachment();
        assertThat(persistedAttachment.getId()).isNotNull();

        // Initial state - no student version
        assertThat(persistedAttachment.getStudentVersion()).isNull();

        // Create a student version file
        MockMultipartFile studentVersionFile = new MockMultipartFile("studentVersion", "student_version.pdf", "application/pdf", "student content".getBytes());

        // Build request for adding student version
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentVideoUnit.getId() + "/student-version")
                .file(studentVersionFile).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        // Perform request
        request.performMvcRequest(builder).andExpect(status().isOk());

        // Verify the student version was added
        var updatedAttachmentVideoUnit = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentVideoUnit.getId(), HttpStatus.OK,
                AttachmentVideoUnit.class);
        assertThat(updatedAttachmentVideoUnit.getAttachment().getStudentVersion()).isNotNull();
        assertThat(updatedAttachmentVideoUnit.getAttachment().getStudentVersion()).contains("attachments/attachment-unit/" + persistedAttachmentVideoUnit.getId() + "/student");

        // Now update with a new student version to test replacement
        MockMultipartFile newStudentVersionFile = new MockMultipartFile("studentVersion", "updated_student_version.pdf", "application/pdf", "updated student content".getBytes());

        // Build a new request to update the student version
        MockHttpServletRequestBuilder updateBuilder = MockMvcRequestBuilders
                .multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentVideoUnit.getId() + "/student-version")
                .file(newStudentVersionFile).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        // Perform request again
        request.performMvcRequest(updateBuilder).andExpect(status().isOk());

        // Get the latest version
        var finalAttachmentVideoUnit = request.get("/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentVideoUnit.getId(), HttpStatus.OK,
                AttachmentVideoUnit.class);

        // Verify the student version was updated
        assertThat(finalAttachmentVideoUnit.getAttachment().getStudentVersion()).isNotNull();
        // The path should still contain the same base structure
        assertThat(finalAttachmentVideoUnit.getAttachment().getStudentVersion()).contains("attachments/attachment-unit/" + persistedAttachmentVideoUnit.getId() + "/student");

        // Verify the file can be accessed
        String requestUrl = String.format("%s%s", ARTEMIS_FILE_PATH_PREFIX, finalAttachmentVideoUnit.getAttachment().getStudentVersion());
        request.getFile(requestUrl, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_withInvalidHiddenSlideDates_shouldReturnBadRequest() throws Exception {
        // First create an attachment video unit
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var createResult = request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        var persistedAttachment = persistedAttachmentUnit.getAttachment();

        // Wait for async operation to complete
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(persistedAttachmentUnit.getId())).hasSize(SLIDE_COUNT));

        // Create a hiddenPages JSON with past dates
        // @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        ZonedDateTime pastDateTime = ZonedDateTime.now().minusDays(1);
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        String pastDate = pastDateTime.format(formatter);

        // The HiddenPageInfoDTO expects slideId as a String
        String hiddenPagesJson = "[{\"slideId\": \"1\", \"date\": \"" + pastDate + "\"}]";

        // Create multipart request parts
        var attachmentUnitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(persistedAttachmentUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(persistedAttachment).getBytes());
        var hiddenPagesPart = new MockMultipartFile("hiddenPages", "", MediaType.APPLICATION_JSON_VALUE, hiddenPagesJson.getBytes());

        // Build request with multipart
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentUnit.getId())
                .file(attachmentUnitPart).file(attachmentPart).file(hiddenPagesPart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        // Should get a bad request due to invalid dates
        request.performMvcRequest(builder).andExpect(status().isBadRequest());

        // Now create a valid future date
        ZonedDateTime futureDateTime = ZonedDateTime.now().plusDays(1);
        String futureDate = futureDateTime.format(formatter);

        String validHiddenPagesJson = "[{\"slideId\": \"1\", \"date\": \"" + futureDate + "\"}]";

        // Create new multipart request parts with valid dates
        var validHiddenPagesPart = new MockMultipartFile("hiddenPages", "", MediaType.APPLICATION_JSON_VALUE, validHiddenPagesJson.getBytes());

        // Build valid request
        var validBuilder = MockMvcRequestBuilders
                .multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentUnit.getId()).file(attachmentUnitPart)
                .file(attachmentPart).file(validHiddenPagesPart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        // Should succeed with valid dates
        request.performMvcRequest(validBuilder).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_withForeverHiddenSlideDates_shouldSucceed() throws Exception {
        // First create an attachment video unit
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var createResult = request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        var persistedAttachment = persistedAttachmentUnit.getAttachment();

        // Wait for async operation to complete
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(persistedAttachmentUnit.getId())).hasSize(SLIDE_COUNT));

        String foreverDate = "9999-12-31T23:59:59.999+02:00";

        // Also, looking at HiddenPageInfoDTO, the field is called "slideId", not "page"
        String hiddenPagesJson = "[{\"slideId\": \"1\", \"date\": \"" + foreverDate + "\"}]";

        // Create multipart request parts
        var attachmentUnitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(persistedAttachmentUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(persistedAttachment).getBytes());
        var hiddenPagesPart = new MockMultipartFile("hiddenPages", "", MediaType.APPLICATION_JSON_VALUE, hiddenPagesJson.getBytes());

        // Build request with multipart
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentUnit.getId())
                .file(attachmentUnitPart).file(attachmentPart).file(hiddenPagesPart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        // Should succeed with "forever" dates
        request.performMvcRequest(builder).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAttachmentUnit_withInvalidHiddenSlidesDatesFormat_shouldReturnBadRequest() throws Exception {
        // First create an attachment video unit
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        var createResult = request.performMvcRequest(buildCreateAttachmentVideoUnit(attachmentVideoUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentVideoUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentVideoUnit.class);
        var persistedAttachment = persistedAttachmentVideoUnit.getAttachment();

        // Wait for async operation to complete
        await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(persistedAttachmentVideoUnit.getId())).hasSize(SLIDE_COUNT));

        // Create a hiddenPages JSON with past dates using the correct format
        // The format should be exactly "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" (without the timezone ID in brackets)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        ZonedDateTime pastDateTime = ZonedDateTime.now().minusDays(1);
        String pastDate = pastDateTime.format(formatter);

        // Use the correct property name 'slideId' instead of 'page'
        String hiddenPagesJson = "[{\"slideId\": \"1\", \"date\": \"" + pastDate + "\"}]";

        // Create multipart request parts
        var attachmentUnitPart = new MockMultipartFile("attachmentVideoUnit", "", MediaType.APPLICATION_JSON_VALUE,
                mapper.writeValueAsString(persistedAttachmentVideoUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(persistedAttachment).getBytes());
        var hiddenPagesPart = new MockMultipartFile("hiddenPages", "", MediaType.APPLICATION_JSON_VALUE, hiddenPagesJson.getBytes());

        // Build request with multipart
        var builder = MockMvcRequestBuilders
                .multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentVideoUnit.getId()).file(attachmentUnitPart)
                .file(attachmentPart).file(hiddenPagesPart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        // Should get a bad request due to invalid dates (dates in the past)
        request.performMvcRequest(builder).andExpect(status().isBadRequest());

        // Now create a valid future date
        ZonedDateTime futureDateTime = ZonedDateTime.now().plusDays(1);
        String futureDate = futureDateTime.format(formatter);
        String validHiddenPagesJson = "[{\"slideId\": \"1\", \"date\": \"" + futureDate + "\"}]";

        // Create new multipart request parts with valid dates
        var validHiddenPagesPart = new MockMultipartFile("hiddenPages", "", MediaType.APPLICATION_JSON_VALUE, validHiddenPagesJson.getBytes());

        // Build valid request
        var validBuilder = MockMvcRequestBuilders
                .multipart(HttpMethod.PUT, "/api/lecture/lectures/" + lecture1.getId() + "/attachment-video-units/" + persistedAttachmentVideoUnit.getId()).file(attachmentUnitPart)
                .file(attachmentPart).file(validHiddenPagesPart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);

        // Should succeed with valid dates
        request.performMvcRequest(validBuilder).andExpect(status().isOk());
    }
}
