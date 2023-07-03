package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.SlideRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;

class AttachmentUnitsIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "attachmentunitsintegrationtest";

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private SlideRepository slideRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private LectureUnitInformationDTO lectureUnitSplits;

    private Lecture lecture1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        List<LectureUnitSplitDTO> units = new ArrayList<>();
        this.lectureUnitSplits = new LectureUnitInformationDTO(units, 1, true);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        slideRepository.deleteAll();
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
        var filePart = createLectureFile(true);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units", null, "process-units", filePart,
                LectureUnitInformationDTO.class, HttpStatus.OK);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void splitLectureFile_asInstructor_shouldCreateAttachmentUnits() throws Exception {
        var filePart = createLectureFile(true);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units", null, "process-units", filePart,
                LectureUnitInformationDTO.class, HttpStatus.OK);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), false);

        List<AttachmentUnit> attachmentUnits = List.of(request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/attachment-units/split", lectureUnitSplitInfo,
                "lectureUnitInformationDTO", filePart, AttachmentUnit[].class, HttpStatus.OK));

        assertThat(attachmentUnits).hasSize(2);
        assertThat(slideRepository.findAll()).hasSize(20); // 20 slides should be created for 2 attachment units

        List<Long> attachmentUnitIds = attachmentUnits.stream().map(AttachmentUnit::getId).toList();
        List<AttachmentUnit> attachmentUnitList = attachmentUnitRepository.findAllById(attachmentUnitIds);

        assertThat(attachmentUnitList).hasSize(2);
        assertThat(attachmentUnitList).isEqualTo(attachmentUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void splitLectureFile_asInstructor_shouldThrowError() throws Exception {
        var filePartWord = createLectureFile(false);
        // if trying to process not the right pdf file then it should throw server error
        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units", null, "process-units", filePartWord, LectureUnitInformationDTO.class,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void splitLectureFile_asInstructor_createAttachmentUnits_shouldThrowError() throws Exception {
        var filePartPDF = createLectureFile(true);
        var filePartWord = createLectureFile(false);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units", null, "process-units", filePartPDF,
                LectureUnitInformationDTO.class, HttpStatus.OK);

        // if trying to create multiple units with not the right pdf file then it should throw error
        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/attachment-units/split", lectureUnitSplitInfo, "lectureUnitInformationDTO", filePartWord,
                AttachmentUnit[].class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void splitLectureFile_asInstructor_shouldCreateAttachmentUnits_and_removeBreakSlides() throws Exception {
        var filePart = createLectureFile(true);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units", null, "process-units", filePart,
                LectureUnitInformationDTO.class, HttpStatus.OK);
        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);
        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), true);

        List<AttachmentUnit> attachmentUnits = List.of(request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/attachment-units/split", lectureUnitSplitInfo,
                "lectureUnitInformationDTO", filePart, AttachmentUnit[].class, HttpStatus.OK));
        assertThat(attachmentUnits).hasSize(2);
        assertThat(slideRepository.findAll()).hasSize(19); // 19 slides should be created for 2 attachment units (1 break slide is removed)

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
        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units", null, "process-units", createLectureFile(true), LectureUnitInformationDTO.class,
                HttpStatus.FORBIDDEN);
        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/attachment-units/split", lectureUnitSplits, "lectureUnitInformationDTO", createLectureFile(true),
                AttachmentUnit[].class, HttpStatus.FORBIDDEN);
    }

    /**
     * Generates a lecture file with 20 pages and with 2 slides that contain Outline
     *
     * @param shouldBePDF true if the file should be PDF, false if it should be word doc
     * @return MockMultipartFile lecture file
     */
    private MockMultipartFile createLectureFile(boolean shouldBePDF) throws IOException {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = new PDDocument()) {
            if (shouldBePDF) {
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
            return new MockMultipartFile("file", "lectureFileWord.doc", "application/msword", outputStream.toByteArray());
        }
    }

}
