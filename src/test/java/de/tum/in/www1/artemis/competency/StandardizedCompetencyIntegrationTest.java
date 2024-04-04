package de.tum.in.www1.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
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

    private static final long ID_NOT_EXISTS = -1000L;

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

    static class CheckStandardizedCompetencyValidationProvider implements ArgumentsProvider {

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext extensionContext) {
            var competencies = new ArrayList<StandardizedCompetency>();
            // invalid title
            competencies.add(new StandardizedCompetency("  ", "valid description", null, null));
            competencies.add(new StandardizedCompetency(null, "valid description", null, null));
            competencies.add(new StandardizedCompetency("0".repeat(StandardizedCompetency.MAX_TITLE_LENGTH + 1), "valid description", null, null));
            // invalid description
            competencies.add(new StandardizedCompetency("valid title", "0".repeat(StandardizedCompetency.MAX_DESCRIPTION_LENGTH + 1), null, null));
            return competencies.stream().map(Arguments::of);
        }
    }

    static class CheckKnowledgeAreaValidationProvider implements ArgumentsProvider {

        @Override
        public Stream<Arguments> provideArguments(ExtensionContext extensionContext) {
            var knowledgeAreas = new ArrayList<KnowledgeArea>();
            // invalid title
            knowledgeAreas.add(new KnowledgeArea(" ", "shortTitle", ""));
            knowledgeAreas.add(new KnowledgeArea(null, "shortTitle", ""));
            knowledgeAreas.add(new KnowledgeArea("0".repeat(KnowledgeArea.MAX_TITLE_LENGTH + 1), "shortTitle", ""));
            // invalid short title
            knowledgeAreas.add(new KnowledgeArea("title", "  ", ""));
            knowledgeAreas.add(new KnowledgeArea("title", "0".repeat(KnowledgeArea.MAX_SHORT_TITLE_LENGTH + 1), ""));
            // invalid description
            knowledgeAreas.add(new KnowledgeArea("title", "shortTitle", "0".repeat(KnowledgeArea.MAX_DESCRIPTION_LENGTH + 1)));
            return knowledgeAreas.stream().map(Arguments::of);
        }
    }

    // TODO: methods to create objects that dont exist ^^
    // TODO: remove id const -> notExists.getId() in url

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        knowledgeArea = new KnowledgeArea("Knowledge Area 0", "KA0", "KA description");
        knowledgeArea = knowledgeAreaRepository.save(knowledgeArea);

        standardizedCompetency = new StandardizedCompetency("Competency 0", "SC description", CompetencyTaxonomy.ANALYZE, "1.0.0");
        standardizedCompetency.setKnowledgeArea(knowledgeArea);
        standardizedCompetency = standardizedCompetencyRepository.save(standardizedCompetency);

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
                var expectedCompetency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null);
                expectedCompetency.setKnowledgeArea(knowledgeArea);
                expectedCompetency.setSource(source);

                var actualCompetency = request.postWithResponseBody("/api/admin/standardized-competencies", expectedCompetency, StandardizedCompetency.class, HttpStatus.CREATED);

                assertThat(actualCompetency).usingRecursiveComparison().ignoringFields("id", "version").isEqualTo(expectedCompetency);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                // knowledge area/source that does not exist in the database is not allowed
                var expectedCompetency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null);
                var knowledgeAreaNotExisting = new KnowledgeArea();
                knowledgeAreaNotExisting.setId(ID_NOT_EXISTS);
                expectedCompetency.setKnowledgeArea(knowledgeAreaNotExisting);

                request.post("/api/admin/standardized-competencies", expectedCompetency, HttpStatus.NOT_FOUND);

                expectedCompetency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null);
                expectedCompetency.setKnowledgeArea(knowledgeArea);
                var sourceNotExisting = new Source();
                sourceNotExisting.setId(ID_NOT_EXISTS);
                expectedCompetency.setSource(sourceNotExisting);

                request.post("/api/admin/standardized-competencies", expectedCompetency, HttpStatus.NOT_FOUND);
            }

            @ParameterizedTest
            @ArgumentsSource(CheckStandardizedCompetencyValidationProvider.class)
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
                var newKnowledgeArea = new KnowledgeArea("Knowledge Area", "KA", "KA description");
                newKnowledgeArea = knowledgeAreaRepository.save(newKnowledgeArea);

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
                // competency with this id does not exist
                var competencyNotExisting = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, "");
                competencyNotExisting.setKnowledgeArea(knowledgeArea);
                competencyNotExisting.setId(ID_NOT_EXISTS);

                request.put("/api/admin/standardized-competencies/" + ID_NOT_EXISTS, competencyNotExisting, HttpStatus.NOT_FOUND);

                // knowledge area/source that does not exist in the database is not allowed
                long validId = standardizedCompetency.getId();

                var invalidCompetency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null);
                invalidCompetency.setId(validId);
                var knowlegeAreaNotExisting = new KnowledgeArea();
                knowlegeAreaNotExisting.setId(ID_NOT_EXISTS);
                invalidCompetency.setKnowledgeArea(knowlegeAreaNotExisting);

                request.put("/api/admin/standardized-competencies/" + validId, invalidCompetency, HttpStatus.NOT_FOUND);

                invalidCompetency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, null);
                invalidCompetency.setKnowledgeArea(knowledgeArea);
                invalidCompetency.setId(validId);
                var sourceNotExisting = new Source();
                sourceNotExisting.setId(ID_NOT_EXISTS);
                invalidCompetency.setSource(sourceNotExisting);

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
            @ArgumentsSource(CheckStandardizedCompetencyValidationProvider.class)
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequestForInvalidCompetency(StandardizedCompetency competency) throws Exception {
                // get a valid id so the request does not fail because of this
                long validId = standardizedCompetency.getId();
                competency.setId(validId);
                request.put("/api/admin/standardized-competencies/" + validId, competency, HttpStatus.BAD_REQUEST);
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
                var knowlegeAreaNotExisting = new KnowledgeArea();
                knowlegeAreaNotExisting.setId(ID_NOT_EXISTS);
                expectedKnowledgeArea.setParent(knowlegeAreaNotExisting);

                request.post("/api/admin/standardized-competencies/knowledge-areas", expectedKnowledgeArea, HttpStatus.NOT_FOUND);
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequest() throws Exception {
                // empty title is not allowed
                var expectedKnowledgeArea = new KnowledgeArea("  ", "  ", "description");

                request.post("/api/admin/standardized-competencies/knowledge-areas", expectedKnowledgeArea, HttpStatus.BAD_REQUEST);
            }
        }

        @Nested
        class UpdateKnowledgeArea {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldUpdateKnowledgeArea() throws Exception {

            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldMoveKnowledgeAreaToNewParent() throws Exception {

            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {
                // TODO: 404 if new parent does not exist.
            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturnBadRequestForCircularDependency() throws Exception {

            }
        }

        @Nested
        class DeleteKnowledgeArea {

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldReturn404() throws Exception {

            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldDeleteKnowledgeArea() throws Exception {

            }

            @Test
            @WithMockUser(username = "admin", roles = "ADMIN")
            void shouldDeleteSelfAndContents() throws Exception {
                // TODO: add code when cascade changes are merged.
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
                var expectedCompetency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, "1.0.0");
                expectedCompetency.setKnowledgeArea(knowledgeArea);
                expectedCompetency.setSource(source);
                expectedCompetency = standardizedCompetencyRepository.save(expectedCompetency);

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
                var knowledgeAreaA = new KnowledgeArea("!!!A_Title", "A", "Description");
                knowledgeAreaA = knowledgeAreaRepository.save(knowledgeAreaA);
                var knowledgeAreaB = new KnowledgeArea("B_Title", "B", "Description");
                knowledgeAreaB.setParent(knowledgeAreaA);
                knowledgeAreaB = knowledgeAreaRepository.save(knowledgeAreaB);
                var competency1 = new StandardizedCompetency("Title", "Description", CompetencyTaxonomy.ANALYZE, "1.0.0");
                competency1.setKnowledgeArea(knowledgeAreaB);
                competency1 = standardizedCompetencyRepository.save(competency1);

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
                var expectedKnowledgeArea = new KnowledgeArea("Knowledge Area 2", "KA2", "description");
                expectedKnowledgeArea.setParent(knowledgeArea);
                expectedKnowledgeArea = knowledgeAreaRepository.save(expectedKnowledgeArea);

                var child1 = new KnowledgeArea("Child 1", "C1", "description");
                child1.setParent(expectedKnowledgeArea);
                child1 = knowledgeAreaRepository.save(child1);
                var child2 = new KnowledgeArea("Child 2", "C2", "description");
                child2.setParent(expectedKnowledgeArea);
                child2 = knowledgeAreaRepository.save(child2);
                expectedKnowledgeArea.setChildren(Set.of(child1, child2));

                var competency = new StandardizedCompetency("Competency", "description", CompetencyTaxonomy.ANALYZE, "");
                competency.setKnowledgeArea(expectedKnowledgeArea);
                competency = standardizedCompetencyRepository.save(competency);
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
