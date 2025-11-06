package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;

/**
 * Unit tests for {@link CompetencyExpertToolsService}.
 * Tests the tools provided to the Competency Expert sub-agent for managing competencies.
 */
@ExtendWith(MockitoExtension.class)
class CompetencyExpertToolsServiceTest {

    @Mock
    private CompetencyRepository competencyRepository;

    @Mock
    private CourseTestRepository courseRepository;

    private ObjectMapper objectMapper;

    private CompetencyExpertToolsService competencyExpertToolsService;

    private Course testCourse;

    private Competency testCompetency;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        competencyExpertToolsService = new CompetencyExpertToolsService(objectMapper, competencyRepository, courseRepository);

        // Setup test course
        testCourse = new Course();
        testCourse.setId(123L);
        testCourse.setTitle("Software Engineering");
        testCourse.setDescription("A comprehensive software engineering course covering OOP, design patterns, and testing.");

        // Setup test competency
        testCompetency = new Competency();
        testCompetency.setId(1L);
        testCompetency.setTitle("Object-Oriented Programming");
        testCompetency.setDescription("Understanding core OOP principles including inheritance, polymorphism, and encapsulation");
        testCompetency.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
        testCompetency.setCourse(testCourse);
    }

    @Nested
    class GetCourseCompetencies {

        @Test
        void shouldReturnAllCompetenciesForValidCourse() throws JsonProcessingException {
            Competency competency1 = new Competency();
            competency1.setId(1L);
            competency1.setTitle("OOP");
            competency1.setDescription("Object-Oriented Programming basics");
            competency1.setTaxonomy(CompetencyTaxonomy.APPLY);

            Competency competency2 = new Competency();
            competency2.setId(2L);
            competency2.setTitle("Design Patterns");
            competency2.setDescription("Common software design patterns");
            competency2.setTaxonomy(CompetencyTaxonomy.ANALYZE);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findAllByCourseId(123L)).thenReturn(Set.of(competency1, competency2));

            String actualResult = competencyExpertToolsService.getCourseCompetencies(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("courseId").asLong()).isEqualTo(123L);
            assertThat(actualJsonNode.get("competencies")).isNotNull();
            assertThat(actualJsonNode.get("competencies").isArray()).isTrue();
            assertThat(actualJsonNode.get("competencies").size()).isEqualTo(2);
        }

        @Test
        void shouldReturnEmptyListWhenNoCompetenciesExist() throws JsonProcessingException {
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findAllByCourseId(123L)).thenReturn(Set.of());

            String actualResult = competencyExpertToolsService.getCourseCompetencies(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("courseId").asLong()).isEqualTo(123L);
            assertThat(actualJsonNode.get("competencies").size()).isZero();
        }

        @Test
        void shouldReturnErrorWhenCourseNotFound() throws JsonProcessingException {
            when(courseRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyExpertToolsService.getCourseCompetencies(999L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("Course not found");
        }

        @Test
        void shouldHandleCompetenciesWithNullTaxonomy() throws JsonProcessingException {
            Competency competencyWithoutTaxonomy = new Competency();
            competencyWithoutTaxonomy.setId(3L);
            competencyWithoutTaxonomy.setTitle("Basic Concepts");
            competencyWithoutTaxonomy.setDescription("Fundamental concepts");
            competencyWithoutTaxonomy.setTaxonomy(null);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findAllByCourseId(123L)).thenReturn(Set.of(competencyWithoutTaxonomy));

            String actualResult = competencyExpertToolsService.getCourseCompetencies(123L);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            JsonNode actualCompetencies = actualJsonNode.get("competencies");
            assertThat(actualCompetencies.get(0).get("taxonomy").asText()).isEmpty();
        }
    }

    @Nested
    class GetCourseDescription {

        @Test
        void shouldReturnCourseDescriptionWhenCourseExists() {
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));

            String actualResult = competencyExpertToolsService.getCourseDescription(123L);

            assertThat(actualResult).isEqualTo(testCourse.getDescription());
        }

        @Test
        void shouldReturnEmptyStringWhenCourseNotFound() {
            when(courseRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyExpertToolsService.getCourseDescription(999L);

            assertThat(actualResult).isEmpty();
        }

        @Test
        void shouldReturnEmptyStringWhenCourseHasNoDescription() {
            Course courseWithoutDescription = new Course();
            courseWithoutDescription.setId(456L);
            courseWithoutDescription.setDescription(null);

            when(courseRepository.findById(456L)).thenReturn(Optional.of(courseWithoutDescription));

            String actualResult = competencyExpertToolsService.getCourseDescription(456L);
            assertThat(actualResult).isNotNull();
        }
    }

    @Nested
    class PreviewCompetencies {

        @Test
        void shouldPreviewSingleCompetencyInCorrectFormat() throws JsonProcessingException {
            CompetencyOperation operation = new CompetencyOperation(null, "Data Structures", "Understanding arrays, lists, trees, and graphs", CompetencyTaxonomy.UNDERSTAND);

            String actualResult = competencyExpertToolsService.previewCompetencies(123L, List.of(operation), null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("preview").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("competency")).isNotNull();
            assertThat(actualJsonNode.get("competency").get("title").asText()).isEqualTo("Data Structures");
            assertThat(actualJsonNode.get("competency").get("taxonomy").asText()).isEqualTo("UNDERSTAND");
            assertThat(actualJsonNode.get("competency").get("icon").asText()).isEqualTo("comments");
        }

        @Test
        void shouldPreviewMultipleCompetenciesInBatchFormat() throws JsonProcessingException {
            CompetencyOperation op1 = new CompetencyOperation(null, "Algorithms", "Sorting and searching algorithms", CompetencyTaxonomy.APPLY);
            CompetencyOperation op2 = new CompetencyOperation(null, "Testing", "Unit and integration testing", CompetencyTaxonomy.EVALUATE);

            String actualResult = competencyExpertToolsService.previewCompetencies(123L, List.of(op1, op2), null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("batchPreview").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("count").asInt()).isEqualTo(2);
            assertThat(actualJsonNode.get("competencies").size()).isEqualTo(2);
        }

        @Test
        void shouldIncludeCompetencyIdForUpdateOperations() throws JsonProcessingException {
            CompetencyOperation updateOperation = new CompetencyOperation(1L, "Updated Title", "Updated description", CompetencyTaxonomy.ANALYZE);

            String actualResult = competencyExpertToolsService.previewCompetencies(123L, List.of(updateOperation), null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("competencyId").asLong()).isEqualTo(1L);
            assertThat(actualJsonNode.get("competency").get("competencyId").asLong()).isEqualTo(1L);
        }

        @Test
        void shouldIncludeViewOnlyFlagWhenProvided() throws JsonProcessingException {
            CompetencyOperation operation = new CompetencyOperation(null, "Read-only preview", "For viewing only", CompetencyTaxonomy.REMEMBER);

            String actualResult = competencyExpertToolsService.previewCompetencies(123L, List.of(operation), true);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("viewOnly").asBoolean()).isTrue();
        }

        @Test
        void shouldMapAllTaxonomiesToCorrectIcons() throws JsonProcessingException {
            Map<CompetencyTaxonomy, String> expectedTaxonomyIconMap = Map.of(CompetencyTaxonomy.REMEMBER, "brain", CompetencyTaxonomy.UNDERSTAND, "comments",
                    CompetencyTaxonomy.APPLY, "pen-fancy", CompetencyTaxonomy.ANALYZE, "magnifying-glass", CompetencyTaxonomy.EVALUATE, "plus-minus", CompetencyTaxonomy.CREATE,
                    "cubes-stacked");

            for (Map.Entry<CompetencyTaxonomy, String> entry : expectedTaxonomyIconMap.entrySet()) {
                CompetencyOperation operation = new CompetencyOperation(null, "Test", "Test description", entry.getKey());

                String actualResult = competencyExpertToolsService.previewCompetencies(123L, List.of(operation), null);

                JsonNode actualJsonNode = objectMapper.readTree(actualResult);
                String actualIcon = actualJsonNode.get("competency").get("icon").asText();
                assertThat(actualIcon).as("Icon for taxonomy %s should be %s", entry.getKey(), entry.getValue()).isEqualTo(entry.getValue());
            }
        }

        @Test
        void shouldReturnErrorWhenNoCompetenciesProvided() throws JsonProcessingException {
            String actualResult = competencyExpertToolsService.previewCompetencies(123L, List.of(), null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No competencies provided");
        }

        @Test
        void shouldReturnErrorWhenCompetenciesListIsNull() throws JsonProcessingException {
            String actualResult = competencyExpertToolsService.previewCompetencies(123L, null, null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
        }
    }

    @Nested
    class SaveCompetencies {

        @Test
        void shouldCreateNewCompetencySuccessfully() throws JsonProcessingException {
            CompetencyOperation createOperation = new CompetencyOperation(null, "New Competency", "A brand new competency", CompetencyTaxonomy.APPLY);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> {
                Competency savedCompetency = invocation.getArgument(0);
                savedCompetency.setId(10L);
                return savedCompetency;
            });

            String actualResult = competencyExpertToolsService.saveCompetencies(123L, List.of(createOperation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isZero();
            assertThat(actualJsonNode.get("failed").asInt()).isZero();
            assertThat(actualJsonNode.get("message").asText()).contains("1 competency created");

            verify(competencyRepository, times(1)).save(any(Competency.class));
            assertThat(competencyExpertToolsService.wasCompetencyCreated()).isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).isTrue();
        }

        @Test
        void shouldUpdateExistingCompetencySuccessfully() throws JsonProcessingException {
            CompetencyOperation updateOperation = new CompetencyOperation(1L, "Updated Title", "Updated description", CompetencyTaxonomy.ANALYZE);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findById(1L)).thenReturn(Optional.of(testCompetency));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String actualResult = competencyExpertToolsService.saveCompetencies(123L, List.of(updateOperation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isZero();
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("message").asText()).contains("1 competency updated");

            ArgumentCaptor<Competency> competencyCaptor = ArgumentCaptor.forClass(Competency.class);
            verify(competencyRepository, times(1)).save(competencyCaptor.capture());
            Competency actualSavedCompetency = competencyCaptor.getValue();
            assertThat(actualSavedCompetency.getTitle()).isEqualTo("Updated Title");
            assertThat(actualSavedCompetency.getDescription()).isEqualTo("Updated description");
            assertThat(actualSavedCompetency.getTaxonomy()).isEqualTo(CompetencyTaxonomy.ANALYZE);

            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).isTrue();
        }

        @Test
        void shouldHandleBatchOperationsWithMixedCreateAndUpdate() throws JsonProcessingException {
            CompetencyOperation createOp = new CompetencyOperation(null, "New Competency", "Description", CompetencyTaxonomy.REMEMBER);
            CompetencyOperation updateOp = new CompetencyOperation(1L, "Updated", "Updated desc", CompetencyTaxonomy.CREATE);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findById(1L)).thenReturn(Optional.of(testCompetency));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String actualResult = competencyExpertToolsService.saveCompetencies(123L, List.of(createOp, updateOp));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("failed").asInt()).isZero();
            assertThat(actualJsonNode.get("message").asText()).contains("1 competency created").contains("1 competency updated");

            verify(competencyRepository, times(2)).save(any(Competency.class));
        }

        @Test
        void shouldContinueOnPartialFailuresAndReportErrors() throws JsonProcessingException {
            CompetencyOperation validOp = new CompetencyOperation(null, "Valid", "Valid description", CompetencyTaxonomy.APPLY);
            CompetencyOperation invalidOp = new CompetencyOperation(999L, "Invalid", "Non-existent ID", CompetencyTaxonomy.UNDERSTAND);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findById(999L)).thenReturn(Optional.empty());
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String actualResult = competencyExpertToolsService.saveCompetencies(123L, List.of(validOp, invalidOp));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isFalse();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isZero();
            assertThat(actualJsonNode.get("failed").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("errors")).isNotNull();
            assertThat(actualJsonNode.get("errors").get(0).asText()).contains("Competency not found with ID: 999");
        }

        @Test
        void shouldReturnErrorWhenCourseNotFound() throws JsonProcessingException {
            CompetencyOperation operation = new CompetencyOperation(null, "Test", "Test description", CompetencyTaxonomy.APPLY);

            when(courseRepository.findById(999L)).thenReturn(Optional.empty());

            String actualResult = competencyExpertToolsService.saveCompetencies(999L, List.of(operation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("Course not found");

            verify(competencyRepository, never()).save(any(Competency.class));
        }

        @Test
        void shouldReturnErrorWhenNoCompetenciesProvided() throws JsonProcessingException {
            String actualResult = competencyExpertToolsService.saveCompetencies(123L, List.of());

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("No competencies provided");

            verify(competencyRepository, never()).save(any(Competency.class));
        }

        @Test
        void shouldTrimTitleWhitespaceDuringUpdate() {
            CompetencyOperation updateOperation = new CompetencyOperation(1L, "  Title with spaces  ", "Description", CompetencyTaxonomy.APPLY);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findById(1L)).thenReturn(Optional.of(testCompetency));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            competencyExpertToolsService.saveCompetencies(123L, List.of(updateOperation));

            ArgumentCaptor<Competency> competencyCaptor = ArgumentCaptor.forClass(Competency.class);
            verify(competencyRepository).save(competencyCaptor.capture());
            Competency actualSavedCompetency = competencyCaptor.getValue();
            assertThat(actualSavedCompetency.getTitle()).isEqualTo("Title with spaces");
        }

        @Test
        void shouldHandleExceptionDuringSaveGracefully() throws JsonProcessingException {
            CompetencyOperation operation = new CompetencyOperation(null, "Test", "Test description", CompetencyTaxonomy.APPLY);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.save(any(Competency.class))).thenThrow(new RuntimeException("Database error"));

            String actualResult = competencyExpertToolsService.saveCompetencies(123L, List.of(operation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isFalse();
            assertThat(actualJsonNode.get("failed").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("errors").get(0).asText()).contains("Database error");
        }
    }

    @Nested
    class StateTracking {

        @Test
        void shouldTrackCompetencyCreationState() {
            assertThat(competencyExpertToolsService.wasCompetencyCreated()).as("Initially, no competency should be created").isFalse();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).as("Initially, no competency should be modified").isFalse();

            CompetencyOperation createOperation = new CompetencyOperation(null, "New", "Description", CompetencyTaxonomy.APPLY);
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            competencyExpertToolsService.saveCompetencies(123L, List.of(createOperation));

            assertThat(competencyExpertToolsService.wasCompetencyCreated()).as("After creation, flag should be true").isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).as("After creation, modified flag should be true").isTrue();
        }

        @Test
        void shouldTrackCompetencyUpdateState() {
            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).as("Initially, no competency should be updated").isFalse();

            CompetencyOperation updateOperation = new CompetencyOperation(1L, "Updated", "Description", CompetencyTaxonomy.APPLY);
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findById(1L)).thenReturn(Optional.of(testCompetency));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            competencyExpertToolsService.saveCompetencies(123L, List.of(updateOperation));

            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).as("After update, flag should be true").isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).as("After update, modified flag should be true").isTrue();
        }

        @Test
        void shouldNotTrackStateWhenOperationFails() {
            CompetencyOperation failedOperation = new CompetencyOperation(999L, "Invalid", "Description", CompetencyTaxonomy.APPLY);
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findById(999L)).thenReturn(Optional.empty());

            competencyExpertToolsService.saveCompetencies(123L, List.of(failedOperation));

            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).as("Failed operation should not set update flag").isFalse();
            assertThat(competencyExpertToolsService.wasCompetencyCreated()).as("Failed operation should not set create flag").isFalse();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).as("Failed operation should not set modified flag").isFalse();
        }
    }
}
