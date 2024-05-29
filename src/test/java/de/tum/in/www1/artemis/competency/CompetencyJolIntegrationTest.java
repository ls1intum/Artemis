package de.tum.in.www1.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.repository.competency.CompetencyJolRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO;

class CompetencyJolIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "competencyjolintegrationtest";

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private CompetencyJolRepository competencyJOLRepository;

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

        private void sendRequest(long competencyId, short jolValue, HttpStatus status) throws Exception {
            request.put(apiURL(competencyId, jolValue), null, status);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnBadRequestForInvalidValue() throws Exception {
            sendRequest(competency[0].getId(), (short) 123, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnBadRequestForCompetencyNotInCourse() throws Exception {
            sendRequest(competencyNotInCourse.getId(), (short) 3, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        public void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(competency[0].getId(), (short) 3, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldCreateJOL() throws Exception {
            short jolValue = 3;
            sendRequest(competency[0].getId(), jolValue, HttpStatus.OK);
            final var jol = competencyJOLRepository.findLatestByCompetencyIdAndUserId(competency[0].getId(), student.getId());
            assertThat(jol).isPresent();
            assertThat(jol.get().getValue()).isEqualTo(jolValue);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldUpdateJOL() throws Exception {
            competencyUtilService.createJOL(competency[0], student, (short) 123, ZonedDateTime.now().minusDays(1));
            short jolValue = 3;
            sendRequest(competency[0].getId(), jolValue, HttpStatus.OK);
            final var jol = competencyJOLRepository.findLatestByCompetencyIdAndUserId(competency[0].getId(), student.getId());
            assertThat(jol).isPresent();
            assertThat(jol.get().getValue()).isEqualTo(jolValue);
        }
    }

    @Nested
    class GetLatestJudgementOfLearningForCompetency {

        private String apiURL(long competencyId) {
            return "/api/courses/" + courseId + "/competencies/" + competencyId + "/jol";
        }

        private CompetencyJolDTO sendRequest(long competencyId, HttpStatus status) throws Exception {
            return request.get(apiURL(competencyId), status, CompetencyJolDTO.class);
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
            short jolValue = 123;
            competencyUtilService.createJOL(competency[0], student, jolValue, ZonedDateTime.now());
            final var jol = sendRequest(competency[0].getId(), HttpStatus.OK);
            assertThat(jol.jolValue()).isEqualTo(jolValue);
        }
    }

    @Nested
    class GetLatestJudgementOfLearningForCourse {

        private String apiURL() {
            return "/api/courses/" + courseId + "/competencies/jol";
        }

        private Map<Long, CompetencyJolDTO> sendRequest(HttpStatus status) throws Exception {
            return request.getMap(apiURL(), status, Long.class, CompetencyJolDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        public void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        public void shouldReturnValues() throws Exception {
            final var jol0 = competencyUtilService.createJOL(competency[0], student, (short) 123, ZonedDateTime.now());
            final var jol1 = competencyUtilService.createJOL(competency[1], student, (short) 8, ZonedDateTime.now());
            final var jolMap = sendRequest(HttpStatus.OK);
            assertThat(jolMap).isNotNull();
            final var expectedMap = Map.of(competency[0].getId(), CompetencyJolDTO.of(jol0), competency[1].getId(), CompetencyJolDTO.of(jol1));
            expectedMap.forEach((expKey, expValue) -> {
                final var val = jolMap.get(expKey);
                assertThat(val.competencyId()).isEqualTo(expValue.competencyId());
                assertThat(val.jolValue()).isEqualTo(expValue.jolValue());
                assertThat(val.judgementTime()).isEqualTo(expValue.judgementTime());
            });
        }
    }
}
