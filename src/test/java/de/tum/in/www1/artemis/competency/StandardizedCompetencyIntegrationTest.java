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
import de.tum.in.www1.artemis.web.rest.dto.competency.StandardizedCompetencyDTO;
import de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency.KnowledgeAreaResultDTO;
import de.tum.in.www1.artemis.web.rest.dto.standardizedCompetency.StandardizedCompetencyResultDTO;

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
            request.get("/api/standardized-competencies/for-tree-view", HttpStatus.FORBIDDEN, KnowledgeAreaResultDTO.class);
            request.get("/api/standardized-competencies/knowledge-areas/1", HttpStatus.FORBIDDEN, KnowledgeArea.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldFailAsTutor() throws Exception {
            this.testAllPreAuthorizeInstructor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldFailAsStudent() throws Exception {
            this.testAllPreAuthorizeInstructor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldFailAsEditor() throws Exception {
            this.testAllPreAuthorizeInstructor();
        }
    }

    @Nested
    class AdminStandardizedCompetencyResource {

        @Nested
        class CreateStandardizedCompetency {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldCreateCompetency() throws Exception {
                var expectedCompetency = new StandardizedCompetencyDTO("Competency", "description", CompetencyTaxonomy.ANALYZE, null, knowledgeArea.getId(), source.getId());

                var actualCompetency = request.postWithResponseBody("/api/admin/standardized-competencies", expectedCompetency, StandardizedCompetencyDTO.class,
                        HttpStatus.CREATED);

                assertThat(actualCompetency).usingRecursiveComparison().ignoringFields("id", "version").isEqualTo(expectedCompetency);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                var expectedCompetency = new StandardizedCompetencyDTO("Competency", "description", CompetencyTaxonomy.ANALYZE, null, ID_NOT_EXISTS, source.getId());
                request.post("/api/admin/standardized-competencies", expectedCompetency, HttpStatus.NOT_FOUND);

                expectedCompetency = new StandardizedCompetencyDTO("Competency", "description", CompetencyTaxonomy.ANALYZE, null, knowledgeArea.getId(), ID_NOT_EXISTS);
                request.post("/api/admin/standardized-competencies", expectedCompetency, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckStandardizedCompetencyValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequest(StandardizedCompetencyDTO competencyDTO) throws Exception {
                request.post("/api/admin/standardized-competencies", competencyDTO, HttpStatus.BAD_REQUEST);

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

                var actualCompetency = request.putWithResponseBody("/api/admin/standardized-competencies/" + standardizedCompetency.getId(),
                        StandardizedCompetencyUtilService.toDTO(standardizedCompetency), StandardizedCompetencyResultDTO.class, HttpStatus.OK);

                assertThat(actualCompetency).isEqualTo(StandardizedCompetencyResultDTO.of(standardizedCompetency));
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                long validId = standardizedCompetency.getId();

                // competency with this id does not exist
                var competencyNotExisting = new StandardizedCompetencyDTO("Competency", "description", CompetencyTaxonomy.ANALYZE, null, knowledgeArea.getId(), source.getId());
                request.put("/api/admin/standardized-competencies/" + ID_NOT_EXISTS, competencyNotExisting, HttpStatus.NOT_FOUND);

                // knowledge area that does not exist in the database is not allowed
                var invalidCompetency = new StandardizedCompetencyDTO("Competency", "description", CompetencyTaxonomy.ANALYZE, null, ID_NOT_EXISTS, source.getId());
                request.put("/api/admin/standardized-competencies/" + validId, invalidCompetency, HttpStatus.NOT_FOUND);

                // source that does not exist in the database is not allowed
                invalidCompetency = new StandardizedCompetencyDTO("Competency", "description", CompetencyTaxonomy.ANALYZE, null, knowledgeArea.getId(), ID_NOT_EXISTS);

                request.put("/api/admin/standardized-competencies/" + validId, invalidCompetency, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckStandardizedCompetencyValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequestForInvalidCompetency(StandardizedCompetencyDTO competencyDTO) throws Exception {
                // get a valid id so the request does not fail because of this
                long validId = standardizedCompetency.getId();
                request.put("/api/admin/standardized-competencies/" + validId, competencyDTO, HttpStatus.BAD_REQUEST);
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
                // explicitly set collections to null instead of empty lists for the equal assert
                expectedKnowledgeArea.setChildren(null);
                expectedKnowledgeArea.setCompetencies(null);

                var actualKnowledgeArea = request.postWithResponseBody("/api/admin/standardized-competencies/knowledge-areas",
                        StandardizedCompetencyUtilService.toDTO(expectedKnowledgeArea), KnowledgeAreaResultDTO.class, HttpStatus.CREATED);

                assertThat(actualKnowledgeArea).usingRecursiveComparison().ignoringFields("id").isEqualTo(KnowledgeAreaResultDTO.of(expectedKnowledgeArea));
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                // parent that does not exist in the database is not allowed
                var expectedKnowledgeArea = new KnowledgeAreaDTO("Knowledge Area 2", "KA2", "description", ID_NOT_EXISTS);

                request.post("/api/admin/standardized-competencies/knowledge-areas", expectedKnowledgeArea, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckKnowledgeAreaValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequest(KnowledgeAreaDTO knowledgeAreaDTO) throws Exception {
                request.post("/api/admin/standardized-competencies/knowledge-areas", knowledgeAreaDTO, HttpStatus.BAD_REQUEST);
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
                // explicitly set collections to null instead of empty lists for the equal assert
                knowledgeArea.setChildren(null);
                knowledgeArea.setCompetencies(null);

                var actualKnowledgeArea = request.putWithResponseBody("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea.getId(),
                        StandardizedCompetencyUtilService.toDTO(knowledgeArea), KnowledgeAreaResultDTO.class, HttpStatus.OK);
                assertThat(actualKnowledgeArea).isEqualTo(KnowledgeAreaResultDTO.of(knowledgeArea));
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldMoveKnowledgeAreaToNewParent() throws Exception {
                var parent1 = standardizedCompetencyUtilService.saveKnowledgeArea("parent1", "p1", "description", null);
                var parent2 = standardizedCompetencyUtilService.saveKnowledgeArea("parent2", "p2", "description", null);
                var knowledgeArea = standardizedCompetencyUtilService.saveKnowledgeArea("knowledge area", "ka", "description", parent1);
                knowledgeArea.setParent(parent2);
                // explicitly set collections to null instead of empty lists for the equal assert
                knowledgeArea.setChildren(null);
                knowledgeArea.setCompetencies(null);

                var actualKnowledgeArea = request.putWithResponseBody("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea.getId(),
                        StandardizedCompetencyUtilService.toDTO(knowledgeArea), KnowledgeAreaResultDTO.class, HttpStatus.OK);
                assertThat(actualKnowledgeArea).isEqualTo(KnowledgeAreaResultDTO.of(knowledgeArea));
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                // knowledge area with this id does not exist
                var knowledgeAreaNotExisting = new KnowledgeAreaDTO("KA", "KA", "", null);

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + ID_NOT_EXISTS, knowledgeAreaNotExisting, HttpStatus.NOT_FOUND);

                // parent that does not exist in the database is not allowed
                var invalidKnowledgeArea = new KnowledgeAreaDTO("KA", "KA", "", ID_NOT_EXISTS);

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea.getId(), invalidKnowledgeArea, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(StandardizedCompetencyUtilService.CheckKnowledgeAreaValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequest(KnowledgeAreaDTO knowledgeAreaDTO) throws Exception {
                long validId = knowledgeArea.getId();

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + validId, knowledgeAreaDTO, HttpStatus.BAD_REQUEST);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequestForCircularDependency() throws Exception {
                var knowledgeArea1 = standardizedCompetencyUtilService.saveKnowledgeArea("KA1", "KA1", "d1", null);
                var knowledgeArea2 = standardizedCompetencyUtilService.saveKnowledgeArea("KA2", "KA2", "d2", knowledgeArea1);
                var knowledgeArea3 = standardizedCompetencyUtilService.saveKnowledgeArea("KA3", "KA3", "d3", knowledgeArea2);

                knowledgeArea1.setParent(knowledgeArea3);

                request.put("/api/admin/standardized-competencies/knowledge-areas/" + knowledgeArea1.getId(), StandardizedCompetencyUtilService.toDTO(knowledgeArea1),
                        HttpStatus.BAD_REQUEST);
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
                assertThat(actualCompetency).isEqualTo(expectedCompetency);
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
                knowledgeAreaB.setCompetencies(Set.of(competency1));
                knowledgeAreaA.setChildren(Set.of(knowledgeAreaB));
                // explicitly set collections to null instead of empty lists for the equal assert
                knowledgeAreaB.setChildren(null);
                knowledgeAreaA.setCompetencies(null);

                var knowledgeAreaTree = request.getList("/api/standardized-competencies/for-tree-view", HttpStatus.OK, KnowledgeAreaResultDTO.class);

                var actualKnowledgeAreaA = knowledgeAreaTree.getFirst();
                var actualKnowledgeAreaB = actualKnowledgeAreaA.children().getFirst();
                assertThat(actualKnowledgeAreaA).isEqualTo(KnowledgeAreaResultDTO.of(knowledgeAreaA));
                assertThat(actualKnowledgeAreaB).isEqualTo(KnowledgeAreaResultDTO.of(knowledgeAreaB));
                assertThat(actualKnowledgeAreaB.competencies().getFirst()).isEqualTo(StandardizedCompetencyResultDTO.of(competency1));
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

                assertThat(actualKnowledgeArea).isEqualTo(expectedKnowledgeArea);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturn404() throws Exception {
            request.get("/api/standardized-competencies/knowledge-areas/" + ID_NOT_EXISTS, HttpStatus.NOT_FOUND, KnowledgeArea.class);
        }
    }
}
