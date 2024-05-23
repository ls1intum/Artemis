package de.tum.in.www1.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyJOL;
import de.tum.in.www1.artemis.repository.competency.CompetencyJOLRepository;

class CompetencyJOLIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "competencyjolintegrationtest";

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private CompetencyJOLRepository competencyJOLRepository;

    private Competency competency;

    private User student;

    private long courseId;

    @BeforeEach
    public void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        final var course = courseUtilService.createCourse();
        courseId = course.getId();
        competency = competencyUtilService.createCompetency(course);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    public void shouldReturnBadRequestForInvalidValue() throws Exception {
        request.put("/api/courses/" + courseId + "/competencies/" + competency.getId() + "/jol/" + 1337, null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    public void shouldCreateJOL() throws Exception {
        int jolValue = 3;
        request.put("/api/courses/" + courseId + "/competencies/" + competency.getId() + "/jol/" + jolValue, null, HttpStatus.OK);
        final var jol = competencyJOLRepository.findByCompetencyIdAndUserId(competency.getId(), student.getId());
        assertThat(jol).isPresent();
        assertThat(jol.get().getValue()).isEqualTo(jolValue);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    public void shouldUpdateJOL() throws Exception {
        final var jol = new CompetencyJOL();
        jol.setCompetency(competency);
        jol.setUser(student);
        jol.setValue(1337);

        competencyJOLRepository.save(jol);

        int jolValue = 3;

        request.put("/api/courses/" + courseId + "/competencies/" + competency.getId() + "/jol/" + jolValue, null, HttpStatus.OK);
        final var updatedJol = competencyJOLRepository.findByCompetencyIdAndUserId(competency.getId(), student.getId());
        assertThat(updatedJol).isPresent();
        assertThat(updatedJol.get().getValue()).isEqualTo(jolValue);
    }
}
