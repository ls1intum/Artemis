package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.repository.ApollonDiagramRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ApollonDiagramResourceIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ApollonDiagramRepository apollonDiagramRepository;

    @Autowired
    RequestUtilService request;

    private ApollonDiagram apollonDiagram;

    @BeforeEach
    public void initTestCase() {
        apollonDiagram = ModelFactory.generateApollonDiagram(DiagramType.ActivityDiagram, "activityDiagram1");
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
        apollonDiagram = null;
        apollonDiagramRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateApollonDiagram_CREATED() throws Exception {
        ApollonDiagram response = request.postWithResponseBody("/api/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.CREATED);
        assertThat(response.getTitle()).as("title is the same").isEqualTo(apollonDiagram.getTitle());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateApollonDiagram_BAD_REQUEST() throws Exception {
        apollonDiagram.setId(1L);
        request.post("/api/apollon-diagrams", apollonDiagram, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdateApollonDiagram_OK() throws Exception {
        apollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        apollonDiagram.setTitle("updated title");
        apollonDiagram.setDiagramType(DiagramType.ClassDiagram);
        ApollonDiagram response = request.putWithResponseBody("/api/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.OK);

        assertThat(response.getDiagramType()).as("diagram type updated").isEqualByComparingTo(DiagramType.ClassDiagram);
        assertThat(response.getTitle()).as("title updated").isEqualTo("updated title");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdateApollonDiagram_CREATED() throws Exception {
        request.put("/api/apollon-diagrams", apollonDiagram, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAllApollonDiagram_OK() throws Exception {
        apollonDiagramRepository.save(apollonDiagram);
        List<ApollonDiagram> response = request.get("/api/apollon-diagrams", HttpStatus.OK, List.class);
        assertThat(response.isEmpty()).as("response is not empty").isFalse();
        assertThat(response.size()).as("response has length 1 ").isEqualTo(1);

        ApollonDiagram newApollonDiagram = ModelFactory.generateApollonDiagram(DiagramType.CommunicationDiagram, "new title");
        apollonDiagramRepository.save(newApollonDiagram);
        List<ApollonDiagram> updatedResponse = request.get("/api/apollon-diagrams", HttpStatus.OK, List.class);
        assertThat(updatedResponse.isEmpty()).as("updated response is not empty").isFalse();
        assertThat(updatedResponse.size()).as("updated response has length 2").isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetApollonDiagram() throws Exception {
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        ApollonDiagram response = request.get("/api/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK, ApollonDiagram.class);
        assertThat(response).as("response is not null").isNotNull();
        request.get("/api/apollon-diagrams/" + apollonDiagram.getId() + 1, HttpStatus.NOT_FOUND, ApollonDiagram.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteApollonDiagram() throws Exception {
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);
        request.delete("/api/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK);
        assertThat(apollonDiagramRepository.findAll().isEmpty()).as("repository is empty").isTrue();
    }
}
