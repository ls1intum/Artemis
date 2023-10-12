package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.SlideRepository;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitSplitDTO;

class AttachmentUnitsIntegrationTest extends AbstractSpringIntegrationIndependentTest {

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

    @Autowired
    private FileService fileService;

    @Autowired
    private FilePathService filePathService;

    private LectureUnitInformationDTO lectureUnitSplits;

    private Lecture lecture1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        List<LectureUnitSplitDTO> units = new ArrayList<>();
        this.lectureUnitSplits = new LectureUnitInformationDTO(units, 1, "Break");
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

    // Tests for uploadSlidesForProcessing()
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void uploadSlidesForProcessing_asInstructor_shouldGetFilename() throws Exception {
        var filePart = createLectureFile(true);

        String uploadInfo = request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units/upload", null, "upload", filePart, String.class, HttpStatus.OK);
        assertThat(uploadInfo).contains(".pdf");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void uploadSlidesForProcessing_asInstructor_shouldThrowError() throws Exception {
        var filePartWord = createLectureFile(false);
        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units/upload", null, "upload", filePartWord, LectureUnitInformationDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    // Tests for getAttachmentUnitsData

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentUnitsData_asInstructor_shouldGetUnitsInformation() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lectureFile);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.get("/api/lectures/" + lecture1.getId() + "/process-units/" + filename, HttpStatus.OK,
                LectureUnitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentUnitsData_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lectureFile);

        request.get("/api/lectures/" + lecture1.getId() + "/process-units/" + filename, HttpStatus.BAD_REQUEST, LectureUnitInformationDTO.class);
        request.get("/api/lectures/" + lecture1.getId() + "/process-units/non-existent-file", HttpStatus.NOT_FOUND, LectureUnitInformationDTO.class);
    }

    // Tests for getSlidesToRemove

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSlidesToRemove_asInstructor_shouldGetUnitsInformation() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        List<Integer> removedSlides = request.getList("/api/lectures/" + lecture1.getId() + "/process-units/slides-to-remove/" + filename, HttpStatus.OK, Integer.class, params);

        assertThat(removedSlides).hasSize(2);
        // index is one lower than in createLectureFile because the loop starts at 1.
        assertThat(removedSlides).contains(5, 6);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSlidesToRemove_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        request.get("/api/lectures/" + lecture1.getId() + "/process-units/slides-to-remove/" + filename, HttpStatus.BAD_REQUEST, LectureUnitInformationDTO.class, params);
        request.get("/api/lectures/" + lecture1.getId() + "/process-units/slides-to-remove/non-existent-file", HttpStatus.NOT_FOUND, LectureUnitInformationDTO.class, params);
    }

    // Tests for createAttachmentUnits
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnits_asInstructor_shouldCreateAttachmentUnits() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lectureFile);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.get("/api/lectures/" + lecture1.getId() + "/process-units/" + filename, HttpStatus.OK,
                LectureUnitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), "");

        List<AttachmentUnit> attachmentUnits = request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/process-units/split/" + filename, lectureUnitSplitInfo,
                AttachmentUnit.class, HttpStatus.OK);

        assertThat(attachmentUnits).hasSize(2);
        assertThat(slideRepository.findAll()).hasSize(20); // 20 slides should be created for 2 attachment units

        List<Long> attachmentUnitIds = attachmentUnits.stream().map(AttachmentUnit::getId).toList();
        List<AttachmentUnit> attachmentUnitList = attachmentUnitRepository.findAllById(attachmentUnitIds);

        assertThat(attachmentUnitList).hasSize(2);
        assertThat(attachmentUnitList).isEqualTo(attachmentUnits);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnits_asInstructor_shouldRemoveSlides() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lectureFile);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.get("/api/lectures/" + lecture1.getId() + "/process-units/" + filename, HttpStatus.OK,
                LectureUnitInformationDTO.class);
        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        var commaSeparatedKeyPhrases = String.join(",", new String[] { "Break", "Example solution" });
        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), commaSeparatedKeyPhrases);

        List<AttachmentUnit> attachmentUnits = request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/process-units/split/" + filename, lectureUnitSplitInfo,
                AttachmentUnit.class, HttpStatus.OK);
        assertThat(attachmentUnits).hasSize(2);
        assertThat(slideRepository.findAll()).hasSize(18); // 18 slides should be created for 2 attachment units (1 break slide is removed and 1 solution slide is removed)

        List<Long> attachmentUnitIds = attachmentUnits.stream().map(AttachmentUnit::getId).toList();
        List<AttachmentUnit> attachmentUnitList = attachmentUnitRepository.findAllById(attachmentUnitIds);

        assertThat(attachmentUnitList).hasSize(2);
        assertThat(attachmentUnitList).isEqualTo(attachmentUnits);

        // first unit
        String attachmentPathFirstUnit = attachmentUnitList.get(0).getAttachment().getLink();
        byte[] fileBytesFirst = request.get(attachmentPathFirstUnit, HttpStatus.OK, byte[].class);

        try (PDDocument document = Loader.loadPDF(fileBytesFirst)) {
            // 5 is the number of pages for the first unit (after break and solution are removed)
            assertThat(document.getNumberOfPages()).isEqualTo(5);
        }

        // second unit
        String attachmentPathSecondUnit = attachmentUnitList.get(1).getAttachment().getLink();
        byte[] fileBytesSecond = request.get(attachmentPathSecondUnit, HttpStatus.OK, byte[].class);

        try (PDDocument document = Loader.loadPDF(fileBytesSecond)) {
            // 13 is the number of pages for the second unit
            assertThat(document.getNumberOfPages()).isEqualTo(13);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnits_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lectureFile);

        request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/process-units/split/" + filename, lectureUnitSplits, AttachmentUnit.class, HttpStatus.BAD_REQUEST);
        request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/process-units/split/non-existent-file", lectureUnitSplits, AttachmentUnit.class,
                HttpStatus.NOT_FOUND);
    }

    private void testAllPreAuthorize() throws Exception {
        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/process-units/upload", null, "upload", createLectureFile(true), String.class, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/process-units/any-file", HttpStatus.FORBIDDEN, LectureUnitInformationDTO.class);
        request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/process-units/split/any-file", lectureUnitSplits, AttachmentUnit.class, HttpStatus.FORBIDDEN);
    }

    /**
     * Generates a lecture file with 20 pages and with 2 slides that contain Outline
     *
     * @param shouldBePDF true if the file should be PDF, false if it should be word doc
     * @return MockMultipartFile lecture file
     */
    private MockMultipartFile createLectureFile(boolean shouldBePDF) throws IOException {

        var font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = new PDDocument()) {
            if (shouldBePDF) {
                for (int i = 1; i <= 20; i++) {
                    document.addPage(new PDPage());
                    PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(i - 1));

                    if (i == 6) {
                        contentStream.beginText();
                        contentStream.setFont(font, 12);
                        contentStream.newLineAtOffset(25, -15);
                        contentStream.showText("itp20..");
                        contentStream.newLineAtOffset(25, 500);
                        contentStream.showText("Break");
                        contentStream.newLineAtOffset(0, -15);
                        contentStream.showText("Have fun");
                        contentStream.endText();
                        contentStream.close();
                        continue;
                    }

                    if (i == 7) {
                        contentStream.beginText();
                        contentStream.setFont(font, 12);
                        contentStream.newLineAtOffset(25, -15);
                        contentStream.showText("itp20..");
                        contentStream.newLineAtOffset(25, 500);
                        contentStream.showText("Example solution");
                        contentStream.newLineAtOffset(0, -15);
                        contentStream.showText("First Unit");
                        contentStream.newLineAtOffset(0, -15);
                        contentStream.showText("Second Unit");
                        contentStream.endText();
                        contentStream.close();
                        continue;
                    }

                    if (i == 2 || i == 8) {
                        contentStream.beginText();
                        contentStream.setFont(font, 12);
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
            return new MockMultipartFile("file", "lectureFileWord.doc", "application/msword", outputStream.toByteArray());
        }
    }

    /**
     * Uploads a lecture file. Needed to test some errors (wrong filetype) and to keep test cases independent.
     *
     * @param file the file to be uploaded
     * @return String filename in the temp folder
     */
    private String manualFileUpload(MockMultipartFile file) throws IOException {
        URI fileURI = fileService.handleSaveFile(file, false, false);
        return filePathService.actualPathForPublicPath(fileURI).getFileName().toString();
    }

}
