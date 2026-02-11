package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertFileUploadExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertQuizExerciseExistsInWeaviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseFromEditorDTO;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

/**
 * Integration tests verifying that CRUD operations on Quiz, Text, and FileUpload exercises
 * via REST endpoints correctly synchronize exercise metadata with Weaviate.
 * <p>
 * These tests extend {@link AbstractSpringIntegrationLocalCILocalVCTest} which provides
 * the Weaviate Testcontainer infrastructure required for these assertions.
 */
@EnabledIf("isWeaviateEnabled")
class ExerciseCRUDWeaviateIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "excrudweaviate";

    @Autowired(required = false)
    private WeaviateService weaviateService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Course course;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course);
    }

    // -- Quiz Exercise Tests --

    @Nested
    class QuizExerciseWeaviateTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateQuizExercise_insertsIntoWeaviate() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
            quizExercise.setDuration(3600);

            QuizExercise created = createQuizViaEndpoint(quizExercise);
            assertThat(created).isNotNull();

            assertQuizExerciseExistsInWeaviate(weaviateService, created);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateQuizExercise_updatesInWeaviate() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
            quizExercise.setDuration(3600);
            QuizExercise created = createQuizViaEndpoint(quizExercise);
            assertThat(created).isNotNull();

            created.setTitle("Updated Quiz Title");
            QuizExercise updated = updateQuizViaEndpoint(created);
            assertThat(updated).isNotNull();
            assertThat(updated.getTitle()).isEqualTo("Updated Quiz Title");

            assertQuizExerciseExistsInWeaviate(weaviateService, updated);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteQuizExercise_removesFromWeaviate() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusHours(5), null, QuizMode.SYNCHRONIZED);
            quizExercise.setDuration(3600);
            QuizExercise created = createQuizViaEndpoint(quizExercise);
            assertThat(created).isNotNull();

            assertQuizExerciseExistsInWeaviate(weaviateService, created);

            request.delete("/api/quiz/quiz-exercises/" + created.getId(), HttpStatus.OK);

            assertExerciseNotInWeaviate(weaviateService, created.getId());
        }

        private QuizExercise createQuizViaEndpoint(QuizExercise quizExercise) throws Exception {
            String url = "/api/quiz/courses/" + quizExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/quiz-exercises";
            QuizExerciseCreateDTO dto = QuizExerciseCreateDTO.of(quizExercise);
            var builder = MockMvcRequestBuilders.multipart(HttpMethod.POST, url);
            builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto))).contentType(MediaType.MULTIPART_FORM_DATA);
            MvcResult result = request.performMvcRequest(builder).andExpect(status().isCreated()).andReturn();
            request.restoreSecurityContext();
            return objectMapper.readValue(result.getResponse().getContentAsString(), QuizExercise.class);
        }

        private QuizExercise updateQuizViaEndpoint(QuizExercise quizExercise) throws Exception {
            QuizExerciseFromEditorDTO dto = new QuizExerciseFromEditorDTO(quizExercise.getTitle(), quizExercise.getChannelName(), quizExercise.getCategories(), null,
                    quizExercise.getDifficulty(), quizExercise.getDuration(), quizExercise.isRandomizeQuestionOrder(), quizExercise.getQuizMode(), quizExercise.getQuizBatches(),
                    quizExercise.getReleaseDate(), quizExercise.getStartDate(), quizExercise.getDueDate(), quizExercise.getIncludedInOverallScore(),
                    quizExercise.getQuizQuestions());
            var builder = MockMvcRequestBuilders.multipart(HttpMethod.PATCH, "/api/quiz/quiz-exercises/" + quizExercise.getId());
            builder.file(new MockMultipartFile("exercise", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto))).contentType(MediaType.MULTIPART_FORM_DATA);
            MvcResult result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
            request.restoreSecurityContext();
            return objectMapper.readValue(result.getResponse().getContentAsString(), QuizExercise.class);
        }
    }

    // -- Text Exercise Tests --

    @Nested
    class TextExerciseWeaviateTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateTextExercise_insertsIntoWeaviate() throws Exception {
            TextExercise textExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now().minusDays(1), null, null, course);
            textExercise.setTitle("Weaviate Text Exercise");
            textExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));

            TextExercise created = request.postWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);

            assertExerciseExistsInWeaviate(weaviateService, created);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateTextExercise_updatesInWeaviate() throws Exception {
            TextExercise textExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now().minusDays(1), null, null, course);
            textExercise.setTitle("Original Text Title");
            textExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));
            TextExercise created = request.postWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);

            created.setTitle("Updated Text Title");
            TextExercise updated = request.putWithResponseBody("/api/text/text-exercises", created, TextExercise.class, HttpStatus.OK);

            assertThat(updated.getTitle()).isEqualTo("Updated Text Title");
            assertExerciseExistsInWeaviate(weaviateService, updated);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteTextExercise_removesFromWeaviate() throws Exception {
            TextExercise textExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now().minusDays(1), null, null, course);
            textExercise.setTitle("Text To Delete");
            textExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));
            TextExercise created = request.postWithResponseBody("/api/text/text-exercises", textExercise, TextExercise.class, HttpStatus.CREATED);
            long exerciseId = created.getId();

            assertExerciseExistsInWeaviate(weaviateService, created);

            request.delete("/api/text/text-exercises/" + exerciseId, HttpStatus.OK);

            assertExerciseNotInWeaviate(weaviateService, exerciseId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testImportTextExercise_insertsIntoWeaviate() throws Exception {
            TextExercise sourceExercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now().minusDays(1), null, null, course);
            sourceExercise.setTitle("Source Text Exercise");
            sourceExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));
            sourceExercise = textExerciseRepository.save(sourceExercise);

            Course targetCourse = courseUtilService.addEmptyCourse();
            var exerciseToImport = new TextExercise();
            exerciseToImport.setCourse(targetCourse);
            exerciseToImport.setMaxPoints(10.0);
            exerciseToImport.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));

            TextExercise imported = request.postWithResponseBody("/api/text/text-exercises/import/" + sourceExercise.getId(), exerciseToImport, TextExercise.class,
                    HttpStatus.CREATED);

            assertExerciseExistsInWeaviate(weaviateService, imported);
        }
    }

    // -- FileUpload Exercise Tests --

    @Nested
    class FileUploadExerciseWeaviateTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testCreateFileUploadExercise_insertsIntoWeaviate() throws Exception {
            FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), null, null, "pdf,png", course);
            fileUploadExercise.setTitle("Weaviate FileUpload Exercise");
            fileUploadExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));

            FileUploadExercise created = request.postWithResponseBody("/api/fileupload/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);

            assertFileUploadExerciseExistsInWeaviate(weaviateService, created);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testUpdateFileUploadExercise_updatesInWeaviate() throws Exception {
            FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), null, null, "pdf,png", course);
            fileUploadExercise.setTitle("Original FileUpload Title");
            fileUploadExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));
            FileUploadExercise created = request.postWithResponseBody("/api/fileupload/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);

            created.setTitle("Updated FileUpload Title");
            FileUploadExercise updated = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + created.getId(), UpdateFileUploadExerciseDTO.of(created),
                    FileUploadExercise.class, HttpStatus.OK);

            assertThat(updated.getTitle()).isEqualTo("Updated FileUpload Title");
            assertFileUploadExerciseExistsInWeaviate(weaviateService, updated);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testDeleteFileUploadExercise_removesFromWeaviate() throws Exception {
            FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), null, null, "pdf,png", course);
            fileUploadExercise.setTitle("FileUpload To Delete");
            fileUploadExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));
            FileUploadExercise created = request.postWithResponseBody("/api/fileupload/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);
            long exerciseId = created.getId();

            assertFileUploadExerciseExistsInWeaviate(weaviateService, created);

            request.delete("/api/fileupload/file-upload-exercises/" + exerciseId, HttpStatus.OK);

            assertExerciseNotInWeaviate(weaviateService, exerciseId);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testImportFileUploadExercise_insertsIntoWeaviate() throws Exception {
            FileUploadExercise sourceExercise = FileUploadExerciseFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), null, null, "pdf,png", course);
            sourceExercise.setTitle("Source FileUpload Exercise");
            sourceExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));
            sourceExercise = fileUploadExerciseRepository.save(sourceExercise);

            Course targetCourse = courseUtilService.addEmptyCourse();
            courseUtilService.enableMessagingForCourse(targetCourse);
            sourceExercise.setCourse(targetCourse);
            sourceExercise.setChannelName("test-" + UUID.randomUUID().toString().substring(0, 8));

            FileUploadExercise imported = request.postWithResponseBody("/api/fileupload/file-upload-exercises/import/" + sourceExercise.getId(), sourceExercise,
                    FileUploadExercise.class, HttpStatus.CREATED);

            assertFileUploadExerciseExistsInWeaviate(weaviateService, imported);
        }
    }
}
