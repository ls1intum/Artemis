package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.competency.PyrisCompetencyExtractionInputDTO;
import de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.competency.PyrisCompetencyRecommendationDTO;
import de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.IrisCompetencyGenerationService;

class IrisCompetencyGenerationIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscompetencyintegration";

    @Autowired
    IrisCompetencyGenerationService irisCompetencyGenerationService;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        course = courseUtilService.createCourse();
        activateIrisGlobally();
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void generateCompetencies_asEditor_shouldSucceed() throws Exception {
        String courseDescription = "Cool course description";
        var currentCompetencies = new PyrisCompetencyRecommendationDTO[] { new PyrisCompetencyRecommendationDTO("test title", "test description", CompetencyTaxonomy.UNDERSTAND), };

        // Expect that a request is sent to Pyris having the following characteristics
        irisRequestMockProvider.mockRunCompetencyExtractionResponseAnd(dto -> {
            var token = dto.execution().settings().authenticationToken();
            assertThat(token).isNotNull();
            assertThat(dto.courseDescription()).contains(courseDescription);
            assertThat(dto.currentCompetencies()).containsExactly(currentCompetencies);
            assertThat(dto.taxonomyOptions()).isNotEmpty();
            assertThat(dto.maxN()).isPositive();
        });

        // Send a request to the Artemis server as if the user had clicked the button in the UI
        request.postWithoutResponseBody("/api/courses/" + course.getId() + "/course-competencies/generate-from-description",
                new PyrisCompetencyExtractionInputDTO(courseDescription, currentCompetencies), HttpStatus.ACCEPTED);

        PyrisCompetencyRecommendationDTO expected = new PyrisCompetencyRecommendationDTO("test title", "test description", CompetencyTaxonomy.UNDERSTAND);
        List<PyrisCompetencyRecommendationDTO> recommendations = List.of(expected, expected, expected);
        List<PyrisStageDTO> stages = List.of(new PyrisStageDTO("Generating Competencies", 10, PyrisStageState.DONE, null));

        // In the real system, this would be triggered by Pyris via a REST call to the Artemis server
        irisCompetencyGenerationService.handleStatusUpdate(TEST_PREFIX + "editor1", course.getId(), new PyrisCompetencyStatusUpdateDTO(stages, recommendations));

        ArgumentCaptor<PyrisCompetencyStatusUpdateDTO> argumentCaptor = ArgumentCaptor.forClass(PyrisCompetencyStatusUpdateDTO.class);
        verify(websocketMessagingService, timeout(200).times(3)).sendMessageToUser(eq(TEST_PREFIX + "editor1"), eq("/topic/iris/competencies/" + course.getId()),
                argumentCaptor.capture());

        List<PyrisCompetencyStatusUpdateDTO> allValues = argumentCaptor.getAllValues();
        assertThat(allValues.get(0).stages()).hasSize(2);
        assertThat(allValues.get(0).result()).isNull();
        assertThat(allValues.get(1).stages()).hasSize(2);
        assertThat(allValues.get(1).result()).isNull();
        assertThat(allValues.get(2).stages()).hasSize(1);
        assertThat(allValues.get(2).result()).isEqualTo(recommendations);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testAll_asStudent_shouldReturnForbidden() throws Exception {
        testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testAll_asTutor_shouldReturnForbidden() throws Exception {
        testAllPreAuthorize();
    }

    void testAllPreAuthorize() throws Exception {
        request.post("/api/courses/" + course.getId() + "/course-competencies/generate-from-description",
                new PyrisCompetencyExtractionInputDTO("a", new PyrisCompetencyRecommendationDTO[] {}), HttpStatus.FORBIDDEN);
    }
}
