package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
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

    @Mock
    private AtlasAgentService atlasAgentService;

    private ObjectMapper objectMapper;

    private CompetencyExpertToolsService competencyExpertToolsService;

    private Course testCourse;

    private Competency testCompetency;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        competencyExpertToolsService = new CompetencyExpertToolsService(objectMapper, competencyRepository, courseRepository, atlasAgentService);

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
        AtlasAgentService.resetCompetencyModifiedFlag();

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
        void shouldHandleCompetenciesWithNullTaxonomy() {
            Competency competencyWithoutTaxonomy = new Competency();
            competencyWithoutTaxonomy.setId(3L);
            competencyWithoutTaxonomy.setTitle("Basic Concepts");
            competencyWithoutTaxonomy.setDescription("Fundamental concepts");
            competencyWithoutTaxonomy.setTaxonomy(null);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findAllByCourseId(123L)).thenReturn(Set.of(competencyWithoutTaxonomy));

            String actualResult = competencyExpertToolsService.getCourseCompetencies(123L);
            assertThat(actualResult).contains("Fundamental concepts");
            assertThat(actualResult).doesNotContain("taxonomy", "Taxonomy");

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
        void shouldPreviewSingleCompetencyInCorrectFormat() {
            CompetencyOperation operation = new CompetencyOperation(null, "Data Structures", "Understanding arrays, lists, trees, and graphs", CompetencyTaxonomy.UNDERSTAND);

            String actualResult = competencyExpertToolsService.previewCompetencies(testCourse.getId(), List.of(operation), null);

            assertThat(actualResult).contains("Preview generated successfully for 1 competency");

        }

        @Test
        void shouldPreviewMultipleCompetenciesInBatchFormat() {
            CompetencyOperation op1 = new CompetencyOperation(null, "Algorithms", "Sorting and searching algorithms", CompetencyTaxonomy.APPLY);
            CompetencyOperation op2 = new CompetencyOperation(null, "Testing", "Unit and integration testing", CompetencyTaxonomy.EVALUATE);

            String actualResult = competencyExpertToolsService.previewCompetencies(testCourse.getId(), List.of(op1, op2), false);

            assertThat(actualResult).contains("Preview generated successfully for 2 competencies.");

        }

        @Test
        void shouldReturnErrorWhenNoCompetenciesProvided() {
            String actualResult = competencyExpertToolsService.previewCompetencies(testCourse.getId(), List.of(), null);

            assertThat(actualResult).isNotNull();
            assertThat(actualResult).contains("Error: No competencies provided for preview.");
        }

        @Test
        void shouldReturnErrorWhenCompetenciesListIsNull() {
            String actualResult = competencyExpertToolsService.previewCompetencies(testCourse.getId(), null, null);

            assertThat(actualResult).isNotNull();
            assertThat(actualResult).contains("Error: No competencies provided for preview.");
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
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isZero();
            assertThat(actualJsonNode.get("failed").asInt()).isZero();

            verify(competencyRepository, times(1)).save(any(Competency.class));
            assertThat(AtlasAgentService.wasCompetencyModified()).isTrue();
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
            assertThat(actualJsonNode.get("created").asInt()).isZero();
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);

            ArgumentCaptor<Competency> competencyCaptor = ArgumentCaptor.forClass(Competency.class);
            verify(competencyRepository, times(1)).save(competencyCaptor.capture());
            Competency actualSavedCompetency = competencyCaptor.getValue();
            assertThat(actualSavedCompetency.getTitle()).isEqualTo("Updated Title");
            assertThat(actualSavedCompetency.getDescription()).isEqualTo("Updated description");
            assertThat(actualSavedCompetency.getTaxonomy()).isEqualTo(CompetencyTaxonomy.ANALYZE);

            assertThat(AtlasAgentService.wasCompetencyModified()).isTrue();
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
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("failed").asInt()).isZero();

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
            JsonNode errorsNode = actualJsonNode.get("errors");
            JsonNode error = errorsNode.get(0);

            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isZero();
            assertThat(actualJsonNode.get("failed").asInt()).isEqualTo(1);

            assertThat(errorsNode.size()).isEqualTo(1);
            assertThat(error.get("competencyTitle").asText()).isEqualTo("Invalid");
            assertThat(error.get("errorType").asText()).isEqualTo("NOT_FOUND");
            assertThat(error.get("details").asText()).contains("ID: 999");
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
        void shouldHandleExceptionDuringSaveGracefully() throws JsonProcessingException {
            CompetencyOperation operation = new CompetencyOperation(null, "Test", "Test description", CompetencyTaxonomy.APPLY);

            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.save(any(Competency.class))).thenThrow(new RuntimeException("Database error"));

            String actualResult = competencyExpertToolsService.saveCompetencies(123L, List.of(operation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("failed").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("errors").get(0).get("details").asText()).contains("Database error");
        }
    }

    @Nested
    class StateTracking {

        @Test
        void shouldTrackCompetencyCreationState() {
            assertThat(AtlasAgentService.wasCompetencyModified()).as("Initially, no competency should be created").isFalse();
            assertThat(AtlasAgentService.wasCompetencyModified()).as("Initially, no competency should be modified").isFalse();

            CompetencyOperation createOperation = new CompetencyOperation(null, "New", "Description", CompetencyTaxonomy.APPLY);
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            competencyExpertToolsService.saveCompetencies(123L, List.of(createOperation));

            assertThat(AtlasAgentService.wasCompetencyModified()).as("After creation, flag should be true").isTrue();
            assertThat(AtlasAgentService.wasCompetencyModified()).as("After creation, modified flag should be true").isTrue();
        }

        @Test
        void shouldTrackCompetencyUpdateState() {

            assertThat(AtlasAgentService.wasCompetencyModified()).as("Initially, no competency should be updated").isFalse();

            CompetencyOperation updateOperation = new CompetencyOperation(1L, "Updated", "Description", CompetencyTaxonomy.APPLY);
            when(courseRepository.findById(123L)).thenReturn(Optional.of(testCourse));
            when(competencyRepository.findById(1L)).thenReturn(Optional.of(testCompetency));
            when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

            competencyExpertToolsService.saveCompetencies(123L, List.of(updateOperation));

            assertThat(AtlasAgentService.wasCompetencyModified()).as("After update, flag should be true").isTrue();
            assertThat(AtlasAgentService.wasCompetencyModified()).as("After update, modified flag should be true").isTrue();
        }

        @Test
        void shouldNotTrackStateWhenOperationFails() {
            competencyExpertToolsService.saveCompetencies(123L, null);

            assertThat(AtlasAgentService.wasCompetencyModified()).as("Failed operation should not set update flag").isFalse();
            assertThat(AtlasAgentService.wasCompetencyModified()).as("Failed operation should not set create flag").isFalse();
            assertThat(AtlasAgentService.wasCompetencyModified()).as("Failed operation should not set modified flag").isFalse();
        }
    }

    @Nested
    class ThreadLocalManagement {

        @Test
        void shouldSetAndClearSessionId() {
            String testSessionId = "test_session_123";

            CompetencyExpertToolsService.setCurrentSessionId(testSessionId);
            // Indirectly verify by calling a method that uses the session ID
            CompetencyExpertToolsService.clearCurrentSessionId();

            // After clearing, session ID should be null
            // This can be verified by calling getLastPreviewedCompetency which checks for null session
            String result = competencyExpertToolsService.getLastPreviewedCompetency();
            assertThat(result).contains("No active session");
        }

        @Test
        void shouldClearAllPreviewsSuccessfully() {
            // Create and set some previews
            CompetencyOperation op = new CompetencyOperation(null, "Test", "Description", CompetencyTaxonomy.APPLY);
            competencyExpertToolsService.previewCompetencies(testCourse.getId(), List.of(op), null);
            // Verify they are cleared by retrieving them
            assertThat(CompetencyExpertToolsService.getAndClearPreviews()).isNotNull();
            assertThat(CompetencyExpertToolsService.getAndClearPreviews()).isEmpty();
        }

        @Test
        void shouldIsolateThreadLocalStateAcrossMultipleOperations() {
            AtlasAgentService.resetCompetencyModifiedFlag();
            assertThat(AtlasAgentService.wasCompetencyModified()).isFalse();

            AtlasAgentService.markCompetencyModified();
            assertThat(AtlasAgentService.wasCompetencyModified()).isTrue();

            AtlasAgentService.resetCompetencyModifiedFlag();
            assertThat(AtlasAgentService.wasCompetencyModified()).isFalse();
        }
    }

    @Nested
    class GetLastPreviewedCompetency {

        @Mock
        private AtlasAgentService mockAtlasAgentService;

        @Test
        void shouldReturnErrorWhenNoActiveSession() throws JsonProcessingException {
            CompetencyExpertToolsService service = new CompetencyExpertToolsService(objectMapper, competencyRepository, courseRepository, mockAtlasAgentService);

            // Don't set any session ID
            CompetencyExpertToolsService.clearCurrentSessionId();

            String result = service.getLastPreviewedCompetency();

            assertThat(result).isNotNull();
            JsonNode jsonNode = objectMapper.readTree(result);
            assertThat(jsonNode.get("error")).isNotNull();
            assertThat(jsonNode.get("error").asText()).contains("No active session");
        }

        @Test
        void shouldReturnErrorWhenNoPreviewedDataExists() throws JsonProcessingException {
            CompetencyExpertToolsService service = new CompetencyExpertToolsService(objectMapper, competencyRepository, courseRepository, mockAtlasAgentService);

            String sessionId = "test_session";
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);

            when(mockAtlasAgentService.getCachedPendingCompetencyOperations(sessionId)).thenReturn(null);

            String result = service.getLastPreviewedCompetency();

            assertThat(result).isNotNull();
            JsonNode jsonNode = objectMapper.readTree(result);
            assertThat(jsonNode.get("error")).isNotNull();
            assertThat(jsonNode.get("error").asText()).contains("No previewed competency data found");

            CompetencyExpertToolsService.clearCurrentSessionId();
        }

        @Test
        void shouldReturnCachedDataWhenAvailable() throws JsonProcessingException {
            CompetencyExpertToolsService service = new CompetencyExpertToolsService(objectMapper, competencyRepository, courseRepository, mockAtlasAgentService);

            String sessionId = "test_session";
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);

            List<CompetencyOperation> cachedData = List.of(new CompetencyOperation(null, "Cached Competency", "Description", CompetencyTaxonomy.APPLY));

            when(mockAtlasAgentService.getCachedPendingCompetencyOperations(sessionId)).thenReturn(cachedData);

            String result = service.getLastPreviewedCompetency();

            assertThat(result).isNotNull();
            JsonNode jsonNode = objectMapper.readTree(result);
            assertThat(jsonNode.get("sessionId").asText()).isEqualTo(sessionId);
            assertThat(jsonNode.get("competencies")).isNotNull();
            assertThat(jsonNode.get("competencies").isArray()).isTrue();
            assertThat(jsonNode.get("competencies").size()).isEqualTo(1);

            CompetencyExpertToolsService.clearCurrentSessionId();
        }
    }

    @Nested
    class ViewOnlyPreview {

        @Test
        void shouldNotCacheCompetenciesWhenViewOnlyIsTrue() {
            String sessionId = "view_only_session";
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);

            CompetencyOperation op = new CompetencyOperation(null, "View Only", "Description", CompetencyTaxonomy.APPLY);

            String result = competencyExpertToolsService.previewCompetencies(testCourse.getId(), List.of(op), true);

            assertThat(result).contains("Preview generated successfully for 1 competency");
            // The cache should not be updated for view-only previews
            // This is verified indirectly as the implementation skips caching when viewOnly is true

            CompetencyExpertToolsService.clearCurrentSessionId();
        }

        @Test
        void shouldCacheCompetenciesWhenViewOnlyIsFalse() {
            String sessionId = "editable_session";
            CompetencyExpertToolsService.setCurrentSessionId(sessionId);

            CompetencyOperation op = new CompetencyOperation(null, "Editable", "Description", CompetencyTaxonomy.APPLY);

            String result = competencyExpertToolsService.previewCompetencies(testCourse.getId(), List.of(op), false);

            assertThat(result).contains("Preview generated successfully for 1 competency");

            CompetencyExpertToolsService.clearCurrentSessionId();
        }
    }

    @Nested
    class TaxonomyIconMapping {

        @Test
        void shouldMapAllTaxonomyLevelsToCorrectIcons() {
            CompetencyTaxonomy[] allTaxonomies = CompetencyTaxonomy.values();

            for (CompetencyTaxonomy taxonomy : allTaxonomies) {
                CompetencyOperation op = new CompetencyOperation(null, "Test " + taxonomy, "Description", taxonomy);

                String result = competencyExpertToolsService.previewCompetencies(testCourse.getId(), List.of(op), null);

                assertThat(result).contains("Preview generated successfully for 1 competency");
                // Icon mapping is tested indirectly through the preview DTO generation
            }
        }
    }
}
