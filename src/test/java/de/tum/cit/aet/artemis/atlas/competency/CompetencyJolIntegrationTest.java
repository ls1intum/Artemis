package de.tum.cit.aet.artemis.atlas.competency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyJolPairDTO;
import de.tum.cit.aet.artemis.core.domain.User;

class CompetencyJolIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "competencyjolintegrationtest";

    private final Competency[] competency = new Competency[3];

    private CompetencyProgress competencyProgress;

    private Competency competencyNotInCourse;

    private User student;

    private long courseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        final var course = courseUtilService.createCourse();
        courseId = course.getId();
        competency[0] = competencyUtilService.createCompetency(course);
        competency[1] = competencyUtilService.createCompetency(course);
        competency[2] = competencyUtilService.createCompetency(course);

        competencyNotInCourse = competencyUtilService.createCompetency(null);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        competencyProgress = competencyProgressUtilService.createCompetencyProgress(competency[0], student, 25, 1);

        userUtilService.addStudent(TEST_PREFIX + "otherstudents", TEST_PREFIX + "otherstudent1");
    }

    @AfterEach
    void tearDown() {
        competencyJolRepository.deleteAll();
    }

    @Nested
    class SetJudgementOfLearning {

        private String apiURL(long competencyId, int jolValue) {
            return "/api/courses/" + courseId + "/course-competencies/" + competencyId + "/jol/" + jolValue;
        }

        private void sendRequest(long competencyId, short jolValue, HttpStatus status) throws Exception {
            request.put(apiURL(competencyId, jolValue), null, status);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnBadRequestForInvalidValue() throws Exception {
            sendRequest(competency[0].getId(), (short) 123, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnBadRequestForCompetencyNotInCourse() throws Exception {
            sendRequest(competencyNotInCourse.getId(), (short) 3, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(competency[0].getId(), (short) 3, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldCreateJOL() throws Exception {
            short jolValue = 3;
            sendRequest(competency[0].getId(), jolValue, HttpStatus.OK);
            final var jol = competencyJolRepository.findLatestByCompetencyIdAndUserId(competency[0].getId(), student.getId());
            assertThat(jol).isPresent();
            assertThat(jol.get().getValue()).isEqualTo(jolValue);
            assertThat(jol.get().getCompetencyConfidence()).isEqualTo(competencyProgress.getConfidence());
            assertThat(jol.get().getCompetencyProgress()).isEqualTo(competencyProgress.getProgress());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldUpdateJOL() throws Exception {
            competencyUtilService.createJol(competency[0], student, (short) 123, ZonedDateTime.now().minusDays(1), 0.0, 0.0);
            short jolValue = 3;
            sendRequest(competency[0].getId(), jolValue, HttpStatus.OK);
            final var jol = competencyJolRepository.findLatestByCompetencyIdAndUserId(competency[0].getId(), student.getId());
            assertThat(jol).isPresent();
            assertThat(jol.get().getValue()).isEqualTo(jolValue);
            assertThat(jol.get().getCompetencyConfidence()).isEqualTo(competencyProgress.getConfidence());
            assertThat(jol.get().getCompetencyProgress()).isEqualTo(competencyProgress.getProgress());
        }
    }

    @Nested
    class GetLatestJudgementOfLearningForCompetency {

        private String apiURL(long competencyId) {
            return "/api/courses/" + courseId + "/course-competencies/" + competencyId + "/jol";
        }

        private CompetencyJolPairDTO sendRequest(long competencyId, HttpStatus status) throws Exception {
            return request.get(apiURL(competencyId), status, CompetencyJolPairDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnBadRequestForCompetencyNotInCourse() throws Exception {
            sendRequest(competencyNotInCourse.getId(), HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(competency[0].getId(), HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnNullForBothWhenNotExists() throws Exception {
            final var jol = sendRequest(competency[0].getId(), HttpStatus.OK);
            assertThat(jol.current()).isNull();
            assertThat(jol.prior()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnValue() throws Exception {
            short jolValue1 = 123;
            competencyUtilService.createJol(competency[0], student, jolValue1, ZonedDateTime.now().minusDays(1), 0.25, 0.25);
            final var jol1 = sendRequest(competency[0].getId(), HttpStatus.OK);
            assertThat(jol1.current().jolValue()).isEqualTo(jolValue1);
            assertThat(jol1.prior()).isNull();

            short jolValue2 = 111;
            competencyUtilService.createJol(competency[0], student, jolValue2, ZonedDateTime.now(), 0.25, 0.25);
            final var jol2 = sendRequest(competency[0].getId(), HttpStatus.OK);
            assertThat(jol2.current().jolValue()).isEqualTo(jolValue2);
            assertThat(jol2.prior().jolValue()).isEqualTo(jolValue1);
        }
    }

    @Nested
    class GetLatestJudgementOfLearningForCourse {

        private String apiURL() {
            return "/api/courses/" + courseId + "/course-competencies/jol";
        }

        private Map<Long, CompetencyJolPairDTO> sendRequest(HttpStatus status) throws Exception {
            return request.getMap(apiURL(), status, Long.class, CompetencyJolPairDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "otherstudent1", roles = "USER")
        void shouldReturnForbiddenForStudentNotInCourse() throws Exception {
            sendRequest(HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnValues() throws Exception {
            final var jol00 = competencyUtilService.createJol(competency[0], student, (short) 123, ZonedDateTime.now().minusDays(1), 0.25, 0.25);
            final var jol01 = competencyUtilService.createJol(competency[0], student, (short) 123, ZonedDateTime.now(), 0.23, 0.22);
            final var jol1 = competencyUtilService.createJol(competency[1], student, (short) 8, ZonedDateTime.now(), 0.1, 0.2);
            final var jolMap = sendRequest(HttpStatus.OK);
            assertThat(jolMap).isNotNull();
            final var expectedMap = Map.of(competency[0].getId(), CompetencyJolPairDTO.of(jol01, jol00), competency[1].getId(), CompetencyJolPairDTO.of(jol1, null));
            expectedMap.forEach((expKey, expValue) -> {
                final var current = jolMap.get(expKey).current();
                final var expCurrent = expValue.current();
                assertThat(current.competencyId()).isEqualTo(expCurrent.competencyId());
                assertThat(current.jolValue()).isEqualTo(expCurrent.jolValue());
                assertThat(current.competencyProgress()).isEqualTo(expCurrent.competencyProgress());
                assertThat(current.competencyConfidence()).isEqualTo(expCurrent.competencyConfidence());

                final var prior = jolMap.get(expKey).prior();
                final var expPrior = expValue.prior();
                if (expPrior == null) {
                    assertThat(prior).isNull();
                }
                else {
                    assertThat(prior.competencyId()).isEqualTo(expPrior.competencyId());
                    assertThat(prior.jolValue()).isEqualTo(expPrior.jolValue());
                    assertThat(prior.competencyProgress()).isEqualTo(expPrior.competencyProgress());
                    assertThat(prior.competencyConfidence()).isEqualTo(expPrior.competencyConfidence());
                }
            });
        }
    }
}
