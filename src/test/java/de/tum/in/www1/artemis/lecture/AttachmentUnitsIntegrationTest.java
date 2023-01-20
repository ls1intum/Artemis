package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;

class AttachmentUnitsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "attachmentunitsintegrationtest";

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    private LectureUnitInformationDTO lectureUnitSplits;

    private Lecture lecture1;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void initTestCase() {
        this.database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lecture1 = this.database.createCourseWithLecture(true);
        List<LectureUnitSplitDTO> units = new ArrayList<>();
        this.lectureUnitSplits = new LectureUnitInformationDTO(units, 1, true);
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
        // TODO: move code of the next 3 lines into RequestUtilService and simply invoke request.postWithMultipartFile() --> potentially create a customized version
        var createResult = request.getMvc().perform(buildGetSplitInformation()).andExpect(status().isOk()).andReturn();
        LectureUnitInformationDTO lectureUnitSplitInfo = mapper.readValue(createResult.getResponse().getContentAsString(), LectureUnitInformationDTO.class);
        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void splitLectureFile_asInstructor_shouldCreateAttachmentUnits() throws Exception {
        // TODO: move code of the next 3 lines into RequestUtilService and simply invoke request.postWithMultipartFile() --> potentially create a customized version
        var splitResult = request.getMvc().perform(buildGetSplitInformation()).andExpect(status().isOk()).andReturn();
        LectureUnitInformationDTO lectureUnitSplitInfo = mapper.readValue(splitResult.getResponse().getContentAsString(), LectureUnitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), false);

        // TODO: move code of the next 3 lines into RequestUtilService and simply invoke request.postWithMultipartFile() --> potentially create a customized version
        var createUnitsResult = request.getMvc().perform(buildSplitAndCreateAttachmentUnits(lectureUnitSplitInfo)).andExpect(status().isOk()).andReturn();
        List<AttachmentUnit> attachmentUnits = mapper.readValue(createUnitsResult.getResponse().getContentAsString(),
                mapper.getTypeFactory().constructCollectionType(List.class, AttachmentUnit.class));

        assertThat(attachmentUnits).hasSize(2);

        List<Long> attachmentUnitIds = attachmentUnits.stream().map(AttachmentUnit::getId).toList();
        List<AttachmentUnit> attachmentUnitList = attachmentUnitRepository.findAllById(attachmentUnitIds);

        assertThat(attachmentUnitList).hasSize(2);
        assertThat(attachmentUnitList).isEqualTo(attachmentUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void splitLectureFile_asInstructor_shouldCreateAttachmentUnits_and_removeBreakSlides() throws Exception {
        // TODO: move code of the next 3 lines into RequestUtilService and simply invoke request.postWithMultipartFile() --> potentially create a customized version
        var splitResult = request.getMvc().perform(buildGetSplitInformation()).andExpect(status().isOk()).andReturn();
        LectureUnitInformationDTO lectureUnitSplitInfo = mapper.readValue(splitResult.getResponse().getContentAsString(), LectureUnitInformationDTO.class);
        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);
        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), true);

        // TODO: move code of the next 3 lines into RequestUtilService and simply invoke request.postWithMultipartFile() --> potentially create a customized version
        var createUnitsResult = request.getMvc().perform(buildSplitAndCreateAttachmentUnits(lectureUnitSplitInfo)).andExpect(status().isOk()).andReturn();
        List<AttachmentUnit> attachmentUnits = mapper.readValue(createUnitsResult.getResponse().getContentAsString(),
                mapper.getTypeFactory().constructCollectionType(List.class, AttachmentUnit.class));
        assertThat(attachmentUnits).hasSize(2);

        List<Long> attachmentUnitIds = attachmentUnits.stream().map(AttachmentUnit::getId).toList();
        List<AttachmentUnit> attachmentUnitList = attachmentUnitRepository.findAllById(attachmentUnitIds);
        String attachmentPath = attachmentUnitList.get(0).getAttachment().getLink();
        byte[] fileBytes = request.get(attachmentPath, HttpStatus.OK, byte[].class);

        try (PDDocument document = PDDocument.load(fileBytes)) {
            // 12 is the number of pages for the first unit without the break slide
            assertThat(document.getNumberOfPages()).isEqualTo(12);
            document.close();
        }
        assertThat(attachmentUnitList).hasSize(2);
        assertThat(attachmentUnitList).isEqualTo(attachmentUnits);
    }

    private void testAllPreAuthorize() throws Exception {
        // TODO: move code of the next 3 lines into RequestUtilService and simply invoke request.postWithMultipartFile() --> potentially create a customized version
        request.getMvc().perform(buildSplitAndCreateAttachmentUnits(lectureUnitSplits)).andExpect(status().isForbidden());
        request.getMvc().perform(buildGetSplitInformation()).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder buildSplitAndCreateAttachmentUnits(@NotNull LectureUnitInformationDTO lectureUnitInformationDTO) throws Exception {
        var lectureUnitSplitPart = new MockMultipartFile("lectureUnitInformationDTO", "", MediaType.APPLICATION_JSON_VALUE,
                mapper.writeValueAsString(lectureUnitInformationDTO).getBytes());
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

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = new PDDocument()) {

            for (int i = 1; i <= 20; i++) {
                document.addPage(new PDPage());
                PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(i - 1));

                if (i == 6 || i == 13) {
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.TIMES_ROMAN, 12);
                    contentStream.newLineAtOffset(25, -15);
                    contentStream.showText("itp20..");
                    contentStream.newLineAtOffset(25, 500);
                    contentStream.showText("Break");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("Have fun");
                    contentStream.close();
                    continue;
                }

                if (i == 7 || i == 14) {
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.TIMES_ROMAN, 12);
                    contentStream.newLineAtOffset(25, -15);
                    contentStream.showText("itp20..");
                    contentStream.newLineAtOffset(25, 500);
                    contentStream.showText("Outline");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("First Unit");
                    contentStream.newLineAtOffset(0, -15);
                    contentStream.showText("Second Unit");
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
            document.save(outputStream);
            document.close();
            return new MockMultipartFile("file", "lectureFile.pdf", "application/json", outputStream.toByteArray());
        }
    }

}
