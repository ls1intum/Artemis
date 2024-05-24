package de.tum.in.www1.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.repository.competency.CompetencyJOLRepository;

class CompetencyJOLIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "competencyjolintegrationtest";

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private CompetencyJOLRepository competencyJOLRepository;

    private final Competency[] competency = new Competency[3];

    private Competency competencyNotInCourse;

    private User student;

    private long courseId;

    @BeforeEach
    public void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        final var course = courseUtilService.createCourse();
        courseId = course.getId();
        competency[0] = competencyUtilService.createCompetency(course);
        competency[1] = competencyUtilService.createCompetency(course);
        competency[2] = competencyUtilService.createCompetency(course);
        competencyNotInCourse = competencyUtilService.createCompetency(null);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        userUtilService.addStudent(TEST_PREFIX + "otherstudents", TEST_PREFIX + "otherstudent1");
    }

    @AfterEach
    public void tearDown() {
        competencyJOLRepository.deleteAll();
    }

    @Nested
    class SetJudgementOfLearning {

        private String apiURL(long competencyId, int jolValue) {
            return "/api/courses/" + courseId + "/competencies/" + competencyId + "/jol/" + jolValue;
        }

        private void sendRequest(long competencyId, int jolValue, HttpStatus status) throws Exception {
            request.put(apiURL(competencyId, jolValue), null, status);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnBadRequestForInvalidValue() throws Exception {
            sendRequest(competency[0].getId(), 1337, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnBadRequestForCompetencyNotInCourse() throws Exception {
            sendRequest(competencyNotInCourse.getId(), 3, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        public void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(competency[0].getId(), 3, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldCreateJOL() throws Exception {
            int jolValue = 3;
            sendRequest(competency[0].getId(), jolValue, HttpStatus.OK);
            final var jol = competencyJOLRepository.findByCompetencyIdAndUserId(competency[0].getId(), student.getId());
            assertThat(jol).isPresent();
            assertThat(jol.get().getValue()).isEqualTo(jolValue);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldUpdateJOL() throws Exception {
            competencyUtilService.createJOL(competency[0], student, 1337);
            int jolValue = 3;
            sendRequest(competency[0].getId(), jolValue, HttpStatus.OK);
            final var jol = competencyJOLRepository.findByCompetencyIdAndUserId(competency[0].getId(), student.getId());
            assertThat(jol).isPresent();
            assertThat(jol.get().getValue()).isEqualTo(jolValue);
        }
    }

    @Nested
    class GetJudgementOfLearningForCompetency {

        private String apiURL(long competencyId) {
            return "/api/courses/" + courseId + "/competencies/" + competencyId + "/jol";
        }

        private Integer sendRequest(long competencyId, HttpStatus status) throws Exception {
            return request.get(apiURL(competencyId), status, Integer.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnBadRequestForCompetencyNotInCourse() throws Exception {
            sendRequest(competencyNotInCourse.getId(), HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        public void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(competency[0].getId(), HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnNullWhenNotExists() throws Exception {
            final var jol = sendRequest(competency[0].getId(), HttpStatus.OK);
            assertThat(jol).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnValue() throws Exception {
            final var jolValue = 1337;
            competencyUtilService.createJOL(competency[0], student, jolValue);
            final var jol = sendRequest(competency[0].getId(), HttpStatus.OK);
            assertThat(jol).isEqualTo(jolValue);
        }
    }

    @Nested
    class GetJudgementOfLearningForCourse {

        private String apiURL() {
            return "/api/courses/" + courseId + "/competencies/jol";
        }

        private Map<?, ?> sendRequest(HttpStatus status) throws Exception {
            return request.get(apiURL(), status, Map.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        public void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnValues() throws Exception {
            final var jolValues = new Integer[] { 1337, 8, null };
            competencyUtilService.createJOL(competency[0], student, jolValues[0]);
            competencyUtilService.createJOL(competency[1], student, jolValues[1]);
            final var jol = sendRequest(HttpStatus.OK);
            assertThat(jol).isNotNull();
            // Java interprets the returned map as a Map<String, Integer> instead of Map<Long, Integer> so we use the following workaround
            final var expectedMap = Map.of(competency[0].getId().toString(), jolValues[0], competency[1].getId().toString(), jolValues[1]);
            assertThat(jol).isEqualTo(expectedMap);
        }
    }
}
