package de.tum.cit.aet.artemis.atlas.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService;
import de.tum.cit.aet.artemis.atlas.service.CompetencyExpertToolsService.CompetencyOperation;
import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * Integration tests for {@link CompetencyExpertToolsService}.
 * Tests the service with real database interactions.
 */
class CompetencyExpertToolsServiceIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "competencyexperttools";

    @Autowired
    private CompetencyExpertToolsService competencyExpertToolsService;

    @Autowired
    private ObjectMapper objectMapper;

    private Course course;

    private Competency existingCompetency;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        course.setDescription("course description for testing");
        courseRepository.save(course);
        existingCompetency = competencyUtilService.createCompetency(course);
    }

    @Nested
    class GetCourseCompetenciesTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRetrieveAllCompetenciesForCourse() throws Exception {
            // Create additional competencies
            competencyUtilService.createCompetency(course, "1");
            competencyUtilService.createCompetency(course, "2");

            String actualResult = competencyExpertToolsService.getCourseCompetencies(course.getId());

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("courseId").asLong()).isEqualTo(course.getId());
            assertThat(actualJsonNode.get("competencies").size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnErrorForNonExistentCourse() throws Exception {
            Long nonExistentCourseId = 999999L;

            String actualResult = competencyExpertToolsService.getCourseCompetencies(nonExistentCourseId);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("error")).isNotNull();
            assertThat(actualJsonNode.get("error").asText()).contains("Course not found");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldIncludeAllCompetencyFieldsInResponse() throws Exception {
            String actualResult = competencyExpertToolsService.getCourseCompetencies(course.getId());

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            JsonNode actualCompetencies = actualJsonNode.get("competencies");
            assertThat(actualCompetencies.isArray()).isTrue();

            // Verify at least one competency exists with all expected fields
            assertThat(actualCompetencies.size()).isGreaterThan(0);
            JsonNode actualFirstCompetency = actualCompetencies.get(0);
            assertThat(actualFirstCompetency.has("id")).isTrue();
            assertThat(actualFirstCompetency.has("title")).isTrue();
            assertThat(actualFirstCompetency.has("description")).isTrue();
            assertThat(actualFirstCompetency.has("taxonomy")).isTrue();
        }
    }

    @Nested
    class GetCourseDescriptionTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnCourseDescription() {
            String expectedDescription = course.getDescription();
            String actualDescription = competencyExpertToolsService.getCourseDescription(course.getId());

            assertThat(actualDescription).isEqualTo(expectedDescription);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnEmptyStringForNonExistentCourse() {
            Long nonExistentCourseId = 999999L;

            String actualDescription = competencyExpertToolsService.getCourseDescription(nonExistentCourseId);

            assertThat(actualDescription).isEmpty();
        }
    }

    @Nested
    class PreviewCompetenciesTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldPreviewSingleNewCompetency() throws Exception {
            CompetencyOperation operation = new CompetencyOperation(null, "Software Testing", "Understanding testing methodologies and practices", CompetencyTaxonomy.UNDERSTAND);

            String actualResult = competencyExpertToolsService.previewCompetencies(course.getId(), List.of(operation), null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("preview").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("competency").get("title").asText()).isEqualTo("Software Testing");
            assertThat(actualJsonNode.get("competency").get("taxonomy").asText()).isEqualTo("UNDERSTAND");
            assertThat(actualJsonNode.get("competency").get("icon").asText()).isEqualTo("comments");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldPreviewMultipleCompetenciesInBatchMode() throws Exception {
            CompetencyOperation op1 = new CompetencyOperation(null, "Algorithms", "Algorithm design and analysis", CompetencyTaxonomy.ANALYZE);
            CompetencyOperation op2 = new CompetencyOperation(null, "Data Structures", "Common data structures and their usage", CompetencyTaxonomy.APPLY);
            CompetencyOperation op3 = new CompetencyOperation(null, "Complexity Theory", "Understanding computational complexity", CompetencyTaxonomy.EVALUATE);

            String actualResult = competencyExpertToolsService.previewCompetencies(course.getId(), List.of(op1, op2, op3), null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("batchPreview").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("count").asInt()).isEqualTo(3);
            assertThat(actualJsonNode.get("competencies").size()).isEqualTo(3);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldPreviewExistingCompetencyUpdate() throws Exception {
            CompetencyOperation updateOperation = new CompetencyOperation(existingCompetency.getId(), "Updated Title", "Updated description", CompetencyTaxonomy.CREATE);

            String actualResult = competencyExpertToolsService.previewCompetencies(course.getId(), List.of(updateOperation), null);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("preview").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("competencyId").asLong()).isEqualTo(existingCompetency.getId());
            assertThat(actualJsonNode.get("competency").get("competencyId").asLong()).isEqualTo(existingCompetency.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldIncludeViewOnlyFlagWhenRequested() throws Exception {
            CompetencyOperation operation = new CompetencyOperation(null, "Read Only Test", "Testing view-only mode", CompetencyTaxonomy.REMEMBER);

            String actualResult = competencyExpertToolsService.previewCompetencies(course.getId(), List.of(operation), true);

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("viewOnly").asBoolean()).isTrue();
        }
    }

    @Nested
    class SaveCompetenciesTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateNewCompetencyAndPersistToDatabase() throws Exception {
            CompetencyOperation createOperation = new CompetencyOperation(null, "Design Patterns", "Understanding common software design patterns", CompetencyTaxonomy.ANALYZE);

            int expectedInitialCount = competencyRepository.findAllByCourseId(course.getId()).size();

            String actualResult = competencyExpertToolsService.saveCompetencies(course.getId(), List.of(createOperation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);

            // Verify persistence
            int actualFinalCount = competencyRepository.findAllByCourseId(course.getId()).size();
            assertThat(actualFinalCount).isEqualTo(expectedInitialCount + 1);

            // Verify the competency details
            Competency actualCreatedCompetency = competencyRepository.findAllByCourseId(course.getId()).stream().filter(c -> "Design Patterns".equals(c.getTitle())).findFirst()
                    .orElseThrow();
            assertThat(actualCreatedCompetency.getDescription()).isEqualTo("Understanding common software design patterns");
            assertThat(actualCreatedCompetency.getTaxonomy()).isEqualTo(CompetencyTaxonomy.ANALYZE);
            assertThat(actualCreatedCompetency.getCourse().getId()).isEqualTo(course.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldUpdateExistingCompetencyInDatabase() throws Exception {
            String expectedNewTitle = "Updated OOP Competency";
            String expectedNewDescription = "Advanced object-oriented programming concepts";
            CompetencyTaxonomy expectedNewTaxonomy = CompetencyTaxonomy.CREATE;

            CompetencyOperation updateOperation = new CompetencyOperation(existingCompetency.getId(), expectedNewTitle, expectedNewDescription, expectedNewTaxonomy);

            String actualResult = competencyExpertToolsService.saveCompetencies(course.getId(), List.of(updateOperation));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);

            // Verify persistence
            Competency actualUpdatedCompetency = competencyRepository.findById(existingCompetency.getId()).orElseThrow();
            assertThat(actualUpdatedCompetency.getTitle()).isEqualTo(expectedNewTitle);
            assertThat(actualUpdatedCompetency.getDescription()).isEqualTo(expectedNewDescription);
            assertThat(actualUpdatedCompetency.getTaxonomy()).isEqualTo(expectedNewTaxonomy);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldHandleBatchCreateOperations() throws Exception {
            CompetencyOperation op1 = new CompetencyOperation(null, "Competency 1", "Description 1", CompetencyTaxonomy.REMEMBER);
            CompetencyOperation op2 = new CompetencyOperation(null, "Competency 2", "Description 2", CompetencyTaxonomy.UNDERSTAND);
            CompetencyOperation op3 = new CompetencyOperation(null, "Competency 3", "Description 3", CompetencyTaxonomy.APPLY);

            int expectedInitialCount = competencyRepository.findAllByCourseId(course.getId()).size();

            String actualResult = competencyExpertToolsService.saveCompetencies(course.getId(), List.of(op1, op2, op3));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(3);

            // Verify persistence
            int actualFinalCount = competencyRepository.findAllByCourseId(course.getId()).size();
            assertThat(actualFinalCount).isEqualTo(expectedInitialCount + 3);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldHandleMixedCreateAndUpdateOperations() throws Exception {
            CompetencyOperation createOp = new CompetencyOperation(null, "New Competency", "New description", CompetencyTaxonomy.ANALYZE);
            CompetencyOperation updateOp = new CompetencyOperation(existingCompetency.getId(), "Updated Existing", "Updated description", CompetencyTaxonomy.EVALUATE);

            int expectedInitialCount = competencyRepository.findAllByCourseId(course.getId()).size();

            String actualResult = competencyExpertToolsService.saveCompetencies(course.getId(), List.of(createOp, updateOp));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isTrue();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isEqualTo(1);

            // Verify final count (one new competency added)
            int actualFinalCount = competencyRepository.findAllByCourseId(course.getId()).size();
            assertThat(actualFinalCount).isEqualTo(expectedInitialCount + 1);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldContinueOnPartialFailureAndReportErrors() throws Exception {
            CompetencyOperation validOp = new CompetencyOperation(null, "Valid Competency", "Valid description", CompetencyTaxonomy.APPLY);
            CompetencyOperation invalidOp = new CompetencyOperation(999999L, "Invalid Update", "Non-existent ID", CompetencyTaxonomy.UNDERSTAND);

            String actualResult = competencyExpertToolsService.saveCompetencies(course.getId(), List.of(validOp, invalidOp));

            assertThat(actualResult).isNotNull();
            JsonNode actualJsonNode = objectMapper.readTree(actualResult);
            assertThat(actualJsonNode.get("success").asBoolean()).isFalse();
            assertThat(actualJsonNode.get("created").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("updated").asInt()).isZero();
            assertThat(actualJsonNode.get("failed").asInt()).isEqualTo(1);
            assertThat(actualJsonNode.get("errors").isArray()).isTrue();
            assertThat(actualJsonNode.get("errors").get(0).asText()).contains("Competency not found");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldTrimTitleWhitespaceWhenUpdating() {
            String titleWithSpaces = "  Title With Spaces  ";
            CompetencyOperation updateOperation = new CompetencyOperation(existingCompetency.getId(), titleWithSpaces, "Description", CompetencyTaxonomy.APPLY);

            competencyExpertToolsService.saveCompetencies(course.getId(), List.of(updateOperation));

            Competency actualUpdatedCompetency = competencyRepository.findById(existingCompetency.getId()).orElseThrow();
            assertThat(actualUpdatedCompetency.getTitle()).isEqualTo("Title With Spaces");
        }
    }

    @Nested
    class StateTrackingTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldTrackCompetencyCreationState() {
            // Initially, no modification should be tracked
            assertThat(competencyExpertToolsService.wasCompetencyCreated()).isFalse();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).isFalse();

            // Perform creation
            CompetencyOperation createOp = new CompetencyOperation(null, "Track Create", "Test tracking", CompetencyTaxonomy.REMEMBER);
            competencyExpertToolsService.saveCompetencies(course.getId(), List.of(createOp));

            // State should be tracked
            assertThat(competencyExpertToolsService.wasCompetencyCreated()).as("Creation flag should be set after creating competency").isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).as("Modified flag should be set after creating competency").isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).as("Update flag should not be set after only creation").isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldTrackCompetencyUpdateState() {
            // Initially, no modification should be tracked
            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).isFalse();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).isFalse();

            // Perform update
            CompetencyOperation updateOp = new CompetencyOperation(existingCompetency.getId(), "Track Update", "Test tracking", CompetencyTaxonomy.ANALYZE);
            competencyExpertToolsService.saveCompetencies(course.getId(), List.of(updateOp));

            // State should be tracked
            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).as("Update flag should be set after updating competency").isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).as("Modified flag should be set after updating competency").isTrue();
            assertThat(competencyExpertToolsService.wasCompetencyCreated()).as("Creation flag should not be set after only update").isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldNotTrackStateForReadOnlyOperations() {
            // Perform read-only operations
            competencyExpertToolsService.getCourseCompetencies(course.getId());
            competencyExpertToolsService.getCourseDescription(course.getId());
            competencyExpertToolsService.previewCompetencies(course.getId(), List.of(new CompetencyOperation(null, "Preview", "Test", CompetencyTaxonomy.REMEMBER)), null);

            // State should not be tracked for read-only operations
            assertThat(competencyExpertToolsService.wasCompetencyCreated()).isFalse();
            assertThat(competencyExpertToolsService.wasCompetencyUpdated()).isFalse();
            assertThat(competencyExpertToolsService.wasCompetencyModified()).isFalse();
        }
    }
}
