package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.repository.ApollonDiagramRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ApollonDiagramResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    ApollonDiagramRepository apollonDiagramRepository;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    CourseService courseService;

    @Autowired
    UserService userService;

    @Autowired
    RequestUtilService request;

    private ApollonDiagram apollonDiagram;

    private Course course1;

    private Course course2;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);
        userRepo.save(ModelFactory.generateActivatedUser("tutor2"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));

        apollonDiagram = ModelFactory.generateApollonDiagram(DiagramType.ActivityDiagram, "activityDiagram1");

        course1 = ModelFactory.generateCourse(1L, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "instructor");
        course2 = ModelFactory.generateCourse(2L, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "instructor");
        courseRepo.save(course1);
        courseRepo.save(course2);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
        apollonDiagram = null;
        apollonDiagramRepository.deleteAll();
        course1 = null;
        course2 = null;
        courseRepo.deleteAll();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateApollonDiagram_CREATED() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram response = request.postWithResponseBody("/api/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.CREATED);
        assertThat(response.getTitle()).as("title is the same").isEqualTo(apollonDiagram.getTitle());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCreateApollonDiagram_BAD_REQUEST() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagram.setId(1L);
        request.post("/api/apollon-diagrams", apollonDiagram, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testCreateApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        request.postWithResponseBody("/api/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdateApollonDiagram_OK() throws Exception {
        // Geht nicht durch
        apollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        apollonDiagram.setTitle("updated title");
        apollonDiagram.setDiagramType(DiagramType.ClassDiagram);
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram response = request.putWithResponseBody("/api/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.OK);

        assertThat(response.getDiagramType()).as("diagram type updated").isEqualByComparingTo(DiagramType.ClassDiagram);
        assertThat(response.getTitle()).as("title updated").isEqualTo("updated title");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdateApollonDiagram_CREATED() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        request.put("/api/apollon-diagrams", apollonDiagram, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testUpdateApollonDiagram_AccessForbidden() throws Exception {
        // Geht durch
        apollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        apollonDiagram.setTitle("updated title");
        apollonDiagram.setDiagramType(DiagramType.ClassDiagram);
        apollonDiagram.setCourseId(course1.getId());
        request.putWithResponseBody("/api/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetAllApollonDiagram_OK() throws Exception {
        apollonDiagramRepository.save(apollonDiagram);
        List response = request.get("/api/apollon-diagrams", HttpStatus.OK, List.class);
        assertThat(response.isEmpty()).as("response is not empty").isFalse();
        assertThat(response.size()).as("response has length 1 ").isEqualTo(1);

        ApollonDiagram newApollonDiagram = ModelFactory.generateApollonDiagram(DiagramType.CommunicationDiagram, "new title");
        apollonDiagramRepository.save(newApollonDiagram);
        List updatedResponse = request.get("/api/apollon-diagrams", HttpStatus.OK, List.class);
        assertThat(updatedResponse.isEmpty()).as("updated response is not empty").isFalse();
        assertThat(updatedResponse.size()).as("updated response has length 2").isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetDiagramsByCourse() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagramRepository.save(apollonDiagram);
        List responseCourse1 = request.get("/api/apollon-diagrams/list/" + course1.getId(), HttpStatus.OK, List.class);
        assertThat(responseCourse1.isEmpty()).as("response is not empty").isFalse();
        assertThat(responseCourse1.size()).as("response has length 1 ").isEqualTo(1);

        ApollonDiagram newDiagramCourse1 = ModelFactory.generateApollonDiagram(DiagramType.ClassDiagram, "new title");
        newDiagramCourse1.setCourseId(course1.getId());
        apollonDiagramRepository.save(newDiagramCourse1);
        List updatedResponseCourse1 = request.get("/api/apollon-diagrams/list/" + course1.getId(), HttpStatus.OK, List.class);
        assertThat(updatedResponseCourse1.isEmpty()).as("updated response is not empty").isFalse();
        assertThat(updatedResponseCourse1.size()).as("updated response has length 2").isEqualTo(2);

        ApollonDiagram newDiagramCourse2 = ModelFactory.generateApollonDiagram(DiagramType.ClassDiagram, "newer title");
        newDiagramCourse2.setCourseId(course2.getId());
        apollonDiagramRepository.save(newDiagramCourse2);
        updatedResponseCourse1 = request.get("/api/apollon-diagrams/list/" + course1.getId(), HttpStatus.OK, List.class);
        assertThat(updatedResponseCourse1.isEmpty()).as("updated response is not empty").isFalse();
        assertThat(updatedResponseCourse1.size()).as("updated response has length 2").isEqualTo(2);

        List responseCourse2 = request.get("/api/apollon-diagrams/list/" + course2.getId(), HttpStatus.OK, List.class);
        assertThat(responseCourse2.isEmpty()).as("updated response is not empty").isFalse();
        assertThat(responseCourse2.size()).as("updated response has length 1").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testGetDiagramsByCourse_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagramRepository.save(apollonDiagram);
        request.get("/api/apollon-diagrams/list/1", HttpStatus.FORBIDDEN, List.class);
        request.get("/api/apollon-diagrams/list/2", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetApollonDiagram() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        ApollonDiagram response = request.get("/api/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK, ApollonDiagram.class);
        assertThat(response).as("response is not null").isNotNull();
        request.get("/api/apollon-diagrams/" + apollonDiagram.getId() + 1, HttpStatus.NOT_FOUND, ApollonDiagram.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testGetApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        request.get("/api/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.FORBIDDEN, ApollonDiagram.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteApollonDiagram() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);
        request.delete("/api/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK);
        assertThat(apollonDiagramRepository.findAll().isEmpty()).as("repository is empty").isTrue();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testDeleteApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);
        request.delete("/api/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.FORBIDDEN);
    }
}
