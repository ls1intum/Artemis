package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.SlideRepository;
import de.tum.in.www1.artemis.service.LectureUnitProcessingService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.LectureUnitInformationDTO;

class PyrisLectureIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrislectureingestiontest";

    @MockBean
    private PyrisWebhookService pyrisWebhookService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private SlideRepository slideRepository;

    @Autowired
    private LectureUnitProcessingService lectureUnitProcessingService;

    private Lecture lecture1;

    private AttachmentUnit attachmentUnitWithSlides;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        Course course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture1 = course1.getLectures().stream().findFirst().orElseThrow();
        this.lecture1.setTitle("Lecture " + new Random().nextInt()); // needed for search by title
        this.lecture1.setTitle("Lecture " + lecture1.getId()); // needed for search by title
        this.lecture1 = lectureRepository.save(this.lecture1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        int numberOfSlides = 2;
        this.attachmentUnitWithSlides = lectureUtilService.createAttachmentUnitWithSlides(numberOfSlides);
        lecture1 = lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(attachmentUnitWithSlides));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        slideRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_shouldCallPyrisIngestionIfSlidesInLectureUnit() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().getFirst().getId();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnitId, HttpStatus.OK);
        lecture1 = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(attachmentUnitWithSlides.getId());
        verify(pyrisWebhookService).executeLectureIngestionPipeline(false, Collections.singletonList(attachmentUnitWithSlides));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importLecture_shouldCallPyrisIngestion() throws Exception {
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);

        Lecture lecture = request.postWithResponseBody("/api/lectures/import/" + lecture1.getId() + "?courseId=" + course2.getId(), null, Lecture.class, HttpStatus.CREATED);

        // Assert that all lecture units (except exercise units) were copied
        assertThat(lecture.getLectureUnits().stream().map(LectureUnit::getName).toList()).containsExactlyElementsOf(
                this.lecture1.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(LectureUnit::getName).toList());

        assertThat(lecture.getAttachments().stream().map(Attachment::getName).toList())
                .containsExactlyElementsOf(this.lecture1.getAttachments().stream().map(Attachment::getName).toList());

        verify(pyrisWebhookService).executeLectureIngestionPipeline(true, Collections.singletonList((AttachmentUnit) lecture.getLectureUnits().getFirst()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAttachmentUnits_asInstructor_shouldCallPyrisIngestion() throws Exception {
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

        verify(pyrisWebhookService).executeLectureIngestionPipeline(true, Collections.singletonList(attachmentUnits.get(0)));
        verify(pyrisWebhookService).executeLectureIngestionPipeline(true, Collections.singletonList(attachmentUnits.get(1)));
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
