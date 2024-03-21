package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
import de.tum.in.www1.artemis.service.LectureUnitProcessingService;
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
    private LectureUnitProcessingService lectureUnitProcessingService;

    private LectureUnitInformationDTO lectureUnitSplits;

    private Lecture lecture1;

    private Lecture invalidLecture;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        this.invalidLecture = lectureUtilService.createLecture(null, null);
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAll_LectureWithoutCourse_shouldReturnBadRequest() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        request.postWithMultipartFile("/api/lectures/" + invalidLecture.getId() + "/attachment-units/upload", null, "upload", createLectureFile(true), String.class,
                HttpStatus.BAD_REQUEST);
        request.get("/api/lectures/" + invalidLecture.getId() + "/attachment-units/data/any-file", HttpStatus.BAD_REQUEST, LectureUnitInformationDTO.class);
        request.get("/api/lectures/" + invalidLecture.getId() + "/attachment-units/slides-to-remove/any-file", HttpStatus.BAD_REQUEST, LectureUnitInformationDTO.class, params);
        request.postListWithResponseBody("/api/lectures/" + invalidLecture.getId() + "/attachment-units/split/any-file", lectureUnitSplits, AttachmentUnit.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAll_WrongLecture_shouldReturnNotFound() throws Exception {
        // Tests that files created for another lecture are not accessible
        // even by instructors of other lectures
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(invalidLecture.getId(), lectureFile);
        Path filePath = lectureUnitProcessingService.getPathForTempFilename(invalidLecture.getId(), filename);

        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/" + filename, HttpStatus.NOT_FOUND, LectureUnitInformationDTO.class);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/slides-to-remove/" + filename, HttpStatus.NOT_FOUND, LectureUnitInformationDTO.class, params);
        request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units/split/" + filename, lectureUnitSplits, AttachmentUnit.class,
                HttpStatus.NOT_FOUND);
        assertThat(Files.exists(filePath)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAll_IOException_ShouldReturnInternalServerError() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "");

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readAllBytes(any())).thenThrow(IOException.class);
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
            request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/" + filename, HttpStatus.INTERNAL_SERVER_ERROR, LectureUnitInformationDTO.class);
            request.getList("/api/lectures/" + lecture1.getId() + "/attachment-units/slides-to-remove/" + filename, HttpStatus.INTERNAL_SERVER_ERROR, Integer.class, params);
            request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units/split/" + filename, lectureUnitSplits, AttachmentUnit.class,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void uploadSlidesForProcessing_asInstructor_shouldGetFilename() throws Exception {
        var filePart = createLectureFile(true);

        String uploadInfo = request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/attachment-units/upload", null, "upload", filePart, String.class, HttpStatus.OK);
        assertThat(uploadInfo).contains(".pdf");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void uploadSlidesForProcessing_asInstructor_shouldThrowError() throws Exception {
        var filePartWord = createLectureFile(false);
        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/attachment-units/upload", null, "upload", filePartWord, LectureUnitInformationDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentUnitsData_asInstructor_shouldGetUnitsInformation() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/" + filename, HttpStatus.OK,
                LectureUnitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAttachmentUnitsData_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/" + filename, HttpStatus.BAD_REQUEST, LectureUnitInformationDTO.class);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/non-existent-file", HttpStatus.NOT_FOUND, LectureUnitInformationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSlidesToRemove_asInstructor_shouldGetUnitsInformation() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        List<Integer> removedSlides = request.getList("/api/lectures/" + lecture1.getId() + "/attachment-units/slides-to-remove/" + filename, HttpStatus.OK, Integer.class, params);

        assertThat(removedSlides).hasSize(2);
        // index is one lower than in createLectureFile because the loop starts at 1.
        assertThat(removedSlides).contains(5, 6);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSlidesToRemove_asInstructor_shouldThrowError() throws Exception {
        var lectureFile = createLectureFile(false);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "Break, Example Solution");

        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/slides-to-remove/" + filename, HttpStatus.BAD_REQUEST, LectureUnitInformationDTO.class, params);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/slides-to-remove/non-existent-file", HttpStatus.NOT_FOUND, LectureUnitInformationDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnits_asInstructor_shouldCreateAttachmentUnits() throws Exception {
        var lectureFile = createLectureFile(true);
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/" + filename, HttpStatus.OK,
                LectureUnitInformationDTO.class);

        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), "");

        List<AttachmentUnit> attachmentUnits = request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units/split/" + filename, lectureUnitSplitInfo,
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
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        LectureUnitInformationDTO lectureUnitSplitInfo = request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/" + filename, HttpStatus.OK,
                LectureUnitInformationDTO.class);
        assertThat(lectureUnitSplitInfo.units()).hasSize(2);
        assertThat(lectureUnitSplitInfo.numberOfPages()).isEqualTo(20);

        var commaSeparatedKeyPhrases = String.join(",", new String[] { "Break", "Example solution" });
        lectureUnitSplitInfo = new LectureUnitInformationDTO(lectureUnitSplitInfo.units(), lectureUnitSplitInfo.numberOfPages(), commaSeparatedKeyPhrases);

        List<AttachmentUnit> attachmentUnits = request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units/split/" + filename, lectureUnitSplitInfo,
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
        String filename = manualFileUpload(lecture1.getId(), lectureFile);

        request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units/split/" + filename, lectureUnitSplits, AttachmentUnit.class,
                HttpStatus.BAD_REQUEST);
        request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units/split/non-existent-file", lectureUnitSplits, AttachmentUnit.class,
                HttpStatus.NOT_FOUND);
    }

    private void testAllPreAuthorize() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commaSeparatedKeyPhrases", "");

        request.postWithMultipartFile("/api/lectures/" + lecture1.getId() + "/attachment-units/upload", null, "upload", createLectureFile(true), String.class,
                HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/data/any-file", HttpStatus.FORBIDDEN, LectureUnitInformationDTO.class);
        request.get("/api/lectures/" + lecture1.getId() + "/attachment-units/slides-to-remove/any-file", HttpStatus.FORBIDDEN, LectureUnitInformationDTO.class, params);
        request.postListWithResponseBody("/api/lectures/" + lecture1.getId() + "/attachment-units/split/any-file", lectureUnitSplits, AttachmentUnit.class, HttpStatus.FORBIDDEN);
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
    private String manualFileUpload(long lectureId, MockMultipartFile file) throws IOException {
        return lectureUnitProcessingService.saveTempFileForProcessing(lectureId, file, 10);
    }
}
