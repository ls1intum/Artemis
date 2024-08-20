package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.iris.settings.IrisCourseSettings;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisStatusUpdateService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.domain.status.IngestionState;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.domain.status.PyrisStageState;
import de.tum.in.www1.artemis.user.UserUtilService;

class PyrisLectureIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrislectureingestiontest";

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    protected PyrisStatusUpdateService pyrisStatusUpdateService;

    @Autowired
    protected PyrisJobService pyrisJobService;

    @Autowired
    protected IrisSettingsRepository irisSettingsRepository;

    @Autowired
    protected LectureUnitRepository lectureUnitRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    private Attachment attachment;

    private Lecture lecture1;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        Course course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture1 = course1.getLectures().stream().findFirst().orElseThrow();
        this.lecture1.setTitle("Lecture " + lecture1.getId()); // needed for search by title
        this.lecture1 = lectureRepository.save(this.lecture1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        int numberOfSlides = 2;
        AttachmentUnit pdfAttachmentUnitWithSlides = lectureUtilService.createAttachmentUnitWithSlidesAndFile(numberOfSlides, true);
        AttachmentUnit imageAttachmentUnitWithSlides = lectureUtilService.createAttachmentUnitWithSlidesAndFile(numberOfSlides, false);
        lecture1 = lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(pdfAttachmentUnitWithSlides, imageAttachmentUnitWithSlides));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void autoIngestionWhenAttachmentUnitCreatedAndAutoUpdateEnabled() throws Exception {
        this.attachment = LectureFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("/api/files/temp/example.txt");
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        activateIrisFor(lecture1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(lecture1.getCourse());
        courseSettings.getIrisLectureIngestionSettings().setAutoIngestOnLectureAttachmentUpload(true);
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        var result = request.performMvcRequest(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentUnit.class);
        var updatedAttachmentUnit = attachmentUnitRepository.findById(persistedAttachmentUnit.getId()).orElseThrow();
        assertThat(updatedAttachmentUnit.getPyrisIngestionState()).isEqualTo(IngestionState.IN_PROGRESS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void noAutoIngestionWhenAttachmentUnitCreatedAndAutoUpdateDisabled() throws Exception {
        this.attachment = LectureFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("/api/files/temp/example.txt");
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        var result = request.performMvcRequest(buildCreateAttachmentUnit(attachmentUnit, attachment)).andExpect(status().isCreated()).andReturn();
        var persistedAttachmentUnit = mapper.readValue(result.getResponse().getContentAsString(), AttachmentUnit.class);
        var updatedAttachmentUnit = attachmentUnitRepository.findById(persistedAttachmentUnit.getId()).orElseThrow();
        assertThat(updatedAttachmentUnit.getPyrisIngestionState()).isEqualTo(IngestionState.NOT_STARTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestLecturesButtonInPyris() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.postWithResponseBody("/api/courses/" + lecture1.getCourse().getId() + "/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
        Optional<LectureUnit> optionalUnit = lectureUnitRepository.findById(lecture1.getLectureUnits().getFirst().getId());
        if (optionalUnit.isPresent() && optionalUnit.get() instanceof AttachmentUnit attachmentUnit) {
            assertThat(attachmentUnit.getPyrisIngestionState()).isEqualTo(IngestionState.IN_PROGRESS);
        }
        optionalUnit = lectureUnitRepository.findById(lecture1.getLectureUnits().getLast().getId());
        if (optionalUnit.isPresent() && optionalUnit.get() instanceof AttachmentUnit attachmentUnit) {
            assertThat(attachmentUnit.getPyrisIngestionState()).isEqualTo(IngestionState.NOT_STARTED);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestLectureUnitButtonInPyris() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.postWithResponseBody("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().getFirst().getId() + "/ingest", Optional.empty(),
                boolean.class, HttpStatus.OK);
        if (lectureUnitRepository.findById(lecture1.getLectureUnits().getFirst().getId()).isPresent()) {
            AttachmentUnit attachmentUnit = (AttachmentUnit) lectureUnitRepository.findById(lecture1.getLectureUnits().getFirst().getId()).get();
            assertThat(attachmentUnit.getPyrisIngestionState() == IngestionState.IN_PROGRESS).isTrue();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLecturefromPyrisDatabaseWithCourseSettingsEnabled() {
        activateIrisFor(lecture1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(lecture1.getCourse());
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of((AttachmentUnit) lecture1.getLectureUnits().getFirst()));
        assertThat(jobToken).isNotNull();
        jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of((AttachmentUnit) lecture1.getLectureUnits().getLast()));
        assertThat(jobToken).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddLectureToPyrisDBAddJobWithCourseSettingsEnabled() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.postWithResponseBody("/api/courses/" + lecture1.getCourse().getId() + "/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
        Optional<LectureUnit> optionalUnit = lectureUnitRepository.findById(lecture1.getLectureUnits().getFirst().getId());
        if (optionalUnit.isPresent() && optionalUnit.get() instanceof AttachmentUnit unit) {
            assertThat(unit.getPyrisIngestionState()).isEqualTo(IngestionState.IN_PROGRESS);
        }
        optionalUnit = lectureUnitRepository.findById(lecture1.getLectureUnits().getLast().getId());
        if (optionalUnit.isPresent() && optionalUnit.get() instanceof AttachmentUnit unit) {
            assertThat(unit.getPyrisIngestionState()).isEqualTo(IngestionState.NOT_STARTED);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneIngestionStateDone() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage), lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            Optional<LectureUnit> optionalUnit = lectureUnitRepository.findById(unit.getId());
            if (optionalUnit.isPresent() && optionalUnit.get() instanceof AttachmentUnit attachUnit) {
                assertThat(attachUnit.getPyrisIngestionState()).isEqualTo(IngestionState.DONE);
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage), lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testStageNotDoneKeepsAdditionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage), lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testStageNotDoneKeepsDeletionIngetionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage), lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testErrorStageRemovesDeletionIngetionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
            Optional<LectureUnit> optionalUnit = lectureUnitRepository.findById(unit.getId());
            if (optionalUnit.isPresent() && optionalUnit.get() instanceof AttachmentUnit attachUnit) {
                assertThat(attachUnit.getPyrisIngestionState()).isEqualTo(IngestionState.ERROR);
            }
        }
    }

    @Test
    void testErrorStageRemovesAdditionIngetionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
            Optional<LectureUnit> optionalUnit = lectureUnitRepository.findById(unit.getId());
            if (optionalUnit.isPresent() && optionalUnit.get() instanceof AttachmentUnit atUnit) {
                assertThat(atUnit.getPyrisIngestionState()).isEqualTo(IngestionState.ERROR);
            }
        }
    }

    @Test
    void testRunIdIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        String newJobToken = pyrisJobService.addIngestionWebhookJob();
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    @Test
    void testIngestionJobDone() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        String newJobToken = pyrisJobService.addIngestionWebhookJob();
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    /**
     * Generates an attachment unit pdf file with 5 pages
     *
     * @return MockMultipartFile attachment unit pdf file
     */
    private MockMultipartFile createAttachmentUnitPdf() throws IOException {

        var font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); PDDocument document = new PDDocument()) {

            for (int i = 1; i <= 3; i++) {
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

    private MockHttpServletRequestBuilder buildCreateAttachmentUnit(@NotNull AttachmentUnit attachmentUnit, @NotNull Attachment attachment) throws Exception {
        var attachmentUnitPart = new MockMultipartFile("attachmentUnit", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachmentUnit).getBytes());
        var attachmentPart = new MockMultipartFile("attachment", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(attachment).getBytes());
        var filePart = createAttachmentUnitPdf();

        return MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/lectures/" + lecture1.getId() + "/attachment-units").file(attachmentUnitPart).file(attachmentPart)
                .file(filePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

}
