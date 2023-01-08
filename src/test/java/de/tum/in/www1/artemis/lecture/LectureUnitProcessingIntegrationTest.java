package de.tum.in.www1.artemis.lecture;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
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
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;

public class LectureUnitProcessingIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "lectureunitprocessingintegration";

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private LectureUnitInformationDTO lectureUnitSplits;

    private Lecture lecture1;

    private Attachment attachment;

    private AttachmentUnit attachmentUnit;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void initTestCase() {
        this.database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        // this.lectureUnitSplits = Collections.singletonList(new LectureUnitSplitDTO("Unit Name", ZonedDateTime.now(), 1, 20));
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testAll_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void splitLectureFile_asInstructor_shouldGetUnitsInformation() throws Exception {
        var createResult = request.getMvc().perform(buildGetSplitInformation()).andExpect(status().isOk()).andReturn();
        LectureUnitInformationDTO lectureUnitSplitInfo = mapper.readValue(createResult.getResponse().getContentAsString(), LectureUnitInformationDTO.class);
        System.out.println(lectureUnitSplitInfo.lectureUnitDTOS().get(0).unitName());
        // var attachmentUnit = mapper.readValue(createResult.getResponse().getContentAsString(), AttachmentUnit.class);
        // var attachment = attachmentUnit.getAttachment();
        // attachmentUnit.setDescription("Changed");
        // var updateResult = request.getMvc().perform(buildUpdateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isOk()).andReturn();
        // attachmentUnit = mapper.readValue(updateResult.getResponse().getContentAsString(), AttachmentUnit.class);
        // assertThat(attachmentUnit.getDescription()).isEqualTo("Changed");
        // // testing if bidirectional relationship is kept
        // attachmentUnit = attachmentUnitRepository.findById(attachmentUnit.getId()).get();
        // attachment = attachmentRepository.findById(attachment.getId()).get();
        // assertThat(attachmentUnit.getAttachment()).isEqualTo(attachment);
        // assertThat(attachment.getAttachmentUnit()).isEqualTo(attachmentUnit);
    }

    private void testAllPreAuthorize() throws Exception {
        request.getMvc().perform(buildSplitAndCreateAttachmentUnits(lectureUnitSplits.lectureUnitDTOS())).andExpect(status().isForbidden());
        request.getMvc().perform(buildGetSplitInformation()).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder buildSplitAndCreateAttachmentUnits(@NotNull List<LectureUnitSplitDTO> lectureUnitSplits) throws Exception {
        var lectureUnitSplitPart = new MockMultipartFile("lectureUnitSplitDTOs", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(lectureUnitSplits).getBytes());
        var filePart = createLecturePdf();

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lecture1.getId() + "/attachment-units/split").file(lectureUnitSplitPart).file(filePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private MockHttpServletRequestBuilder buildGetSplitInformation() throws IOException {
        var filePart = createLecturePdf();

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lecture1.getId() + "/process-units").file(filePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    /**
     * Generates a lecture pdf file with 20 pages and with 2 slides that contain Outline
     *
     * @return MockMultipartFile lecture file
     * @throws IOException
     */
    private MockMultipartFile createLecturePdf() throws IOException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument doc1 = new PDDocument()) {

            for (int i = 0; i < 20; i++) {
                doc1.addPage(new PDPage());
                PDPageContentStream contentStream = new PDPageContentStream(doc1, doc1.getPage(i));

                if (i == 7 || i == 14) {
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.TIMES_ROMAN, 12);
                    contentStream.newLineAtOffset(25, 500);
                    contentStream.showText("Outline");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("First Unit");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("Second Unit");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("Third Unit");
                    contentStream.endText();
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
            doc1.save(outputStream);

            return new MockMultipartFile("file", "lectureFile.pdf", "application/json", outputStream.toByteArray());
        }
    }

}
