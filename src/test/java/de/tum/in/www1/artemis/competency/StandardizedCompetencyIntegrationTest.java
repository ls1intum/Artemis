package de.tum.in.www1.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.KnowledgeArea;
import de.tum.in.www1.artemis.domain.competency.Source;
import de.tum.in.www1.artemis.domain.competency.StandardizedCompetency;
import de.tum.in.www1.artemis.repository.SourceRepository;
import de.tum.in.www1.artemis.repository.competency.KnowledgeAreaRepository;
import de.tum.in.www1.artemis.repository.competency.StandardizedCompetencyRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.competency.KnowledgeAreaDTO;

class StandardizedCompetencyIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "stdcompetencyintegrationtest";

    private static final long ID_NOT_EXISTS = StandardizedCompetencyUtilService.ID_NOT_EXISTS;

    private KnowledgeArea knowledgeArea;

    private StandardizedCompetency standardizedCompetency;

    private Source source;

    @Autowired
    private KnowledgeAreaRepository knowledgeAreaRepository;

    @Autowired
    private StandardizedCompetencyRepository standardizedCompetencyRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private StandardizedCompetencyUtilService standardizedCompetencyUtilService;

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        knowledgeArea = standardizedCompetencyUtilService.saveKnowledgeArea("Knowledge Area 0", "KA0", "KA description", null);
        standardizedCompetency = standardizedCompetencyUtilService.saveStandardizedCompetency("Competency 0", "SC description", CompetencyTaxonomy.ANALYZE, "1.0.0", knowledgeArea,
                null);

        source = new Source("Source 0", "Author 0", "localhost:8000");
        source = sourceRepository.save(source);
    }

    @Nested
    class PreAuthorize {

        private void testAllPreAuthorizeInstructor() throws Exception {
            request.get("/api/standardized-competencies/1", HttpStatus.FORBIDDEN, StandardizedCompetency.class);
            request.get("/api/standardized-competencies/for-tree-view", HttpStatus.FORBIDDEN, StandardizedCompetency.class);
            request.get("/api/standardized-competencies/knowledge-areas/1", HttpStatus.FORBIDDEN, KnowledgeArea.class);
        }

        private void testAllPreAuthorizeAdmin() throws Exception {
            request.post("/api/admin/standardized-competencies", new StandardizedCompetency(), HttpStatus.FORBIDDEN);
            request.put("/api/admin/standardized-competencies/1", new StandardizedCompetency(), HttpStatus.FORBIDDEN);
            request.delete("/api/admin/standardized-competencies/1", HttpStatus.FORBIDDEN);
            request.post("/api/admin/standardized-competencies/knowledge-areas", new KnowledgeArea(), HttpStatus.FORBIDDEN);
            request.put("/api/admin/standardized-competencies/knowledge-areas/1", new KnowledgeArea(), HttpStatus.FORBIDDEN);
            request.delete("/api/admin/standardized-competencies/knowledge-areas/1", HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldFailAsTutor() throws Exception {
            this.testAllPreAuthorizeAdmin();
            this.testAllPreAuthorizeInstructor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldFailAsStudent() throws Exception {
            this.testAllPreAuthorizeAdmin();
            this.testAllPreAuthorizeInstructor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldFailAsEditor() throws Exception {
            this.testAllPreAuthorizeAdmin();
            this.testAllPreAuthorizeInstructor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldFailAsInstructor() throws Exception {
            this.testAllPreAuthorizeAdmin();
            // do not call testAllPreAuthorizeInstructor, as these methods should succeed
        }
    }

    @Nested
    class AdminStandardizedCompetencyResource {

        @Nested
        class CreateStandardizedCompetency {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldCreateCompetency() throws Exception {
                var expectedCompetency = StandardizedCompetencyUtilService.buildStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null, knowledgeArea,
                        source);

                var actualCompetency = request.postWithResponseBody("/api/admin/standardized-competencies", expectedCompetency, StandardizedCompetency.class, HttpStatus.CREATED);

                assertThat(actualCompetency).usingRecursiveComparison().ignoringFields("id", "version").isEqualTo(expectedCompetency);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                // knowledge area/source that does not exist in the database is not allowed
                var knowledgeAreaNotExisting = StandardizedCompetencyUtilService.buildKnowledgeAreaNotExisting();
                var expectedCompetency = StandardizedCompetencyUtilService.buildStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null,
                        knowledgeAreaNotExisting, null);

                request.post("/api/admin/standardized-competencies", expectedCompetency, HttpStatus.NOT_FOUND);

                var sourceNotExisting = StandardizedCompetencyUtilService.buildSourceNotExisting();
                expectedCompetency = StandardizedCompetencyUtilService.buildStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null, knowledgeArea,
                        sourceNotExisting);

                request.post("/api/admin/standardized-competencies", expectedCompetency, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckStandardizedCompetencyValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequest(StandardizedCompetency competency) throws Exception {
                request.post("/api/admin/standardized-competencies", competency, HttpStatus.BAD_REQUEST);
            }
        }

        @Nested
        class UpdateStandardizedCompetency {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldUpdateCompetency() throws Exception {
                var newKnowledgeArea = standardizedCompetencyUtilService.saveKnowledgeArea("Knowledge Area", "KA", "KA description", null);

                standardizedCompetency.setTitle("New Title");
                standardizedCompetency.setDescription("New Description");
                standardizedCompetency.setKnowledgeArea(newKnowledgeArea);

                var actualCompetency = request.putWithResponseBody("/api/admin/standardized-competencies/" + standardizedCompetency.getId(), standardizedCompetency,
                        StandardizedCompetency.class, HttpStatus.OK);

                assertThat(actualCompetency).isEqualTo(standardizedCompetency);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                long validId = standardizedCompetency.getId();

                // competency with this id does not exist
                var competencyNotExisting = StandardizedCompetencyUtilService.buildStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, "",
                        knowledgeArea, null);
                competencyNotExisting.setId(ID_NOT_EXISTS);

                request.put("/api/admin/standardized-competencies/" + ID_NOT_EXISTS, competencyNotExisting, HttpStatus.NOT_FOUND);

                // knowledge area that does not exist in the database is not allowed
                var knowlegeAreaNotExisting = StandardizedCompetencyUtilService.buildKnowledgeAreaNotExisting();
                var invalidCompetency = StandardizedCompetencyUtilService.buildStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null,
                        knowlegeAreaNotExisting, source);
                invalidCompetency.setId(validId);

                request.put("/api/admin/standardized-competencies/" + validId, invalidCompetency, HttpStatus.NOT_FOUND);

                // source that does not exist in the database is not allowed
                var sourceNotExisting = StandardizedCompetencyUtilService.buildSourceNotExisting();
                invalidCompetency = StandardizedCompetencyUtilService.buildStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null, knowledgeArea,
                        sourceNotExisting);
                invalidCompetency.setId(validId);

                request.put("/api/admin/standardized-competencies/" + validId, invalidCompetency, HttpStatus.NOT_FOUND);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequestWhenIdsDontMatch() throws Exception {
                var competency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, "");
                competency.setId(1L);
                request.put("/api/admin/standardized-competencies/" + 2, competency, HttpStatus.BAD_REQUEST);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckStandardizedCompetencyValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequestForInvalidCompetency(StandardizedCompetency invalidCompetency) throws Exception {
                // get a valid id so the request does not fail because of this
                long validId = standardizedCompetency.getId();
                invalidCompetency.setId(validId);
                request.put("/api/admin/standardized-competencies/" + validId, invalidCompetency, HttpStatus.BAD_REQUEST);
            }
        }

        @Nested
        class DeleteStandardizedCompetency {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldDeleteCompetency() throws Exception {
                long deletedId = standardizedCompetency.getId();

                request.delete("/api/admin/standardized-competencies/" + deletedId, HttpStatus.OK);

                boolean exists = standardizedCompetencyRepository.existsById(deletedId);
                assertThat(exists).isFalse();
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                request.delete("/api/admin/standardized-competencies/" + ID_NOT_EXISTS, HttpStatus.NOT_FOUND);
            }
        }

        @Nested
        class CreateKnowledgeArea {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldCreateKnowledgeArea() throws Exception {
                var expectedKnowledgeArea = new KnowledgeArea("Knowledge Area 2", "KA2", "description");
                expectedKnowledgeArea.setParent(knowledgeArea);

                var actualKnowledgeArea = request.postWithResponseBody("/api/admin/standardized-competencies/knowledge-areas", expectedKnowledgeArea, KnowledgeArea.class,
                        HttpStatus.CREATED);

                assertThat(actualKnowledgeArea).usingRecursiveComparison().ignoringFields("id").isEqualTo(expectedKnowledgeArea);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                var expectedKnowledgeArea = new KnowledgeArea("Knowledge Area 2", "KA2", "description");
                // parent that does not exist in the database is not allowed
                var knowlegeAreaNotExisting = StandardizedCompetencyUtilService.buildKnowledgeAreaNotExisting();
                expectedKnowledgeArea.setParent(knowlegeAreaNotExisting);

                request.post("/api/admin/standardized-competencies/knowledge-areas", expectedKnowledgeArea, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckKnowledgeAreaValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequest(KnowledgeArea knowledgeArea) throws Exception {
                request.post("/api/admin/standardized-competencies/knowledge-areas", knowledgeArea, HttpStatus.BAD_REQUEST);
            }
        }

        @Nested
        class UpdateKnowledgeArea {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldUpdateKnowledgeArea() throws Exception {
                knowledgeArea.setTitle("new title");
                knowledgeArea.setShortTitle("new title");
                knowledgeArea.setDescription("new description");

                var actualKnowledgeArea = request.putWithResponseBody("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea.getId(), knowledgeArea,
                        KnowledgeArea.class, HttpStatus.OK);
                assertThat(actualKnowledgeArea).isEqualTo(knowledgeArea);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldMoveKnowledgeAreaToNewParent() throws Exception {
                var parent1 = standardizedCompetencyUtilService.saveKnowledgeArea("parent1", "p1", "", null);
                var parent2 = standardizedCompetencyUtilService.saveKnowledgeArea("parent2", "p2", "", null);
                var expectedKnowledgeArea = standardizedCompetencyUtilService.saveKnowledgeArea("knowledge area", "ka", "", parent1);
                expectedKnowledgeArea.setParent(parent2);

                var actualKnowledgeArea = request.putWithResponseBody("/api/admin/standardized-competencies/knowledge-areas/" + expectedKnowledgeArea.getId(),
                        expectedKnowledgeArea, KnowledgeArea.class, HttpStatus.OK);
                assertThat(actualKnowledgeArea).isEqualTo(expectedKnowledgeArea);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                // knowledge area with this id does not exist
                var knowledgeAreaNotExisting = new KnowledgeArea("KA", "KA", "");
                knowledgeAreaNotExisting.setId(ID_NOT_EXISTS);

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + ID_NOT_EXISTS, knowledgeAreaNotExisting, HttpStatus.NOT_FOUND);

                // parent that does not exist in the database is not allowed
                var parentNotExisting = StandardizedCompetencyUtilService.buildKnowledgeAreaNotExisting();
                knowledgeArea.setParent(parentNotExisting);

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea.getId(), knowledgeArea, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckKnowledgeAreaValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequest(KnowledgeArea invalidKnowledgeArea) throws Exception {
                long validId = knowledgeArea.getId();
                invalidKnowledgeArea.setId(validId);

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + validId, invalidKnowledgeArea, HttpStatus.BAD_REQUEST);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequestForCircularDependency() throws Exception {
                var knowledgeArea1 = standardizedCompetencyUtilService.saveKnowledgeArea("KA1", "KA1", "d1", null);
                var knowledgeArea2 = standardizedCompetencyUtilService.saveKnowledgeArea("KA2", "KA2", "d2", knowledgeArea1);
                var knowledgeArea3 = standardizedCompetencyUtilService.saveKnowledgeArea("KA3", "KA3", "d3", knowledgeArea2);

                knowledgeArea1.setParent(knowledgeArea3);

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea1.getId(), knowledgeArea1, HttpStatus.BAD_REQUEST);
            }
        }

        @Nested
        class DeleteKnowledgeArea {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                request.delete("/api/admin/standardized-competencies/knowledge-areas/" + ID_NOT_EXISTS, HttpStatus.NOT_FOUND);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldDeleteKnowledgeArea() throws Exception {
                long deletedId = knowledgeArea.getId();

                request.delete("/api/admin/standardized-competencies/knowledge-areas/" + deletedId, HttpStatus.OK);

                boolean exists = knowledgeAreaRepository.existsById(deletedId);
                assertThat(exists).isFalse();
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldDeleteSelfAndContents() throws Exception {
                var child1 = standardizedCompetencyUtilService.saveKnowledgeArea("child1", "c1", "", knowledgeArea);
                var child2 = standardizedCompetencyUtilService.saveKnowledgeArea("child1", "c1", "", child1);
                var competency = standardizedCompetencyUtilService.saveStandardizedCompetency("title", "", null, "1.0.0", knowledgeArea, null);

                request.delete("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea.getId(), HttpStatus.OK);

                var existingKnowledgeAreas = knowledgeAreaRepository.findAllById(List.of(knowledgeArea.getId(), child1.getId(), child2.getId()));
                assertThat(existingKnowledgeAreas).isEmpty();
                var competencyExists = standardizedCompetencyRepository.existsById(competency.getId());
                assertThat(competencyExists).isFalse();
            }
        }
    }

    @Nested
    class StandardizedCompetencyResource {

        @Nested
        class GetStandardizedCompetency {

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void shouldReturnStandardizedCompetency() throws Exception {
                var expectedCompetency = standardizedCompetencyUtilService.saveStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, "1.0.0",
                        knowledgeArea, source);

                var actualCompetency = request.get("/api/standardized-competencies/" + expectedCompetency.getId(), HttpStatus.OK, StandardizedCompetency.class);
                assertThat(actualCompetency).usingRecursiveComparison().isEqualTo(expectedCompetency);
            }

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void shouldReturn404() throws Exception {
                request.get("/api/standardized-competencies/" + ID_NOT_EXISTS, HttpStatus.NOT_FOUND, StandardizedCompetency.class);
            }
        }

        @Nested
        class GetAllForTreeView {

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void shouldGetAllKnowledgeAreasAndCompetencies() throws Exception {
                var knowledgeAreaA = standardizedCompetencyUtilService.saveKnowledgeArea("!!!A_Title", "A", "Description", null);
                var knowledgeAreaB = standardizedCompetencyUtilService.saveKnowledgeArea("B_Title", "B", "Description", knowledgeAreaA);
                var competency1 = standardizedCompetencyUtilService.saveStandardizedCompetency("Title", "Description", CompetencyTaxonomy.ANALYZE, "1.0.0", knowledgeAreaB, null);

                var knowledgeAreaTree = request.getList("/api/standardized-competencies/for-tree-view", HttpStatus.OK, KnowledgeAreaDTO.class);

                var actualKnowledgeAreaA = knowledgeAreaTree.get(0);
                var actualKnowledgeAreaB = actualKnowledgeAreaA.children().get(0);
                assertThat(actualKnowledgeAreaA).usingRecursiveComparison().comparingOnlyFields("id", "title", "shortTitle").isEqualTo(knowledgeAreaA);
                assertThat(actualKnowledgeAreaB).usingRecursiveComparison().comparingOnlyFields("id", "title", "shortTitle").isEqualTo(knowledgeAreaB);
                assertThat(actualKnowledgeAreaB.competencies().get(0)).usingRecursiveComparison().comparingOnlyFields("id", "title").isEqualTo(competency1);
            }
        }

        @Nested
        class GetKnowledgeArea {

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void shouldReturnKnowledgeArea() throws Exception {
                var expectedKnowledgeArea = standardizedCompetencyUtilService.saveKnowledgeArea("Knowledge Area 2", "KA2", "description", knowledgeArea);

                var child1 = standardizedCompetencyUtilService.saveKnowledgeArea("Child 1", "C1", "description", expectedKnowledgeArea);
                var child2 = standardizedCompetencyUtilService.saveKnowledgeArea("Child 2", "C2", "description", expectedKnowledgeArea);
                var competency = standardizedCompetencyUtilService.saveStandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, "", expectedKnowledgeArea,
                        null);
                expectedKnowledgeArea.setChildren(Set.of(child1, child2));
                expectedKnowledgeArea.setCompetencies(Set.of(competency));

                var actualKnowledgeArea = request.get("/api/standardized-competencies/knowledge-areas/" + expectedKnowledgeArea.getId(), HttpStatus.OK, KnowledgeArea.class);

                assertThat(actualKnowledgeArea).usingRecursiveComparison().ignoringFields("competencies", "children").isEqualTo(expectedKnowledgeArea);
                assertThat(actualKnowledgeArea.getChildren()).containsAll(expectedKnowledgeArea.getChildren());
                assertThat(actualKnowledgeArea.getCompetencies()).containsAll(expectedKnowledgeArea.getCompetencies());
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturn404() throws Exception {
            request.get("/api/standardized-competencies/knowledge-areas/" + ID_NOT_EXISTS, HttpStatus.NOT_FOUND, KnowledgeArea.class);
        }
    }
}
