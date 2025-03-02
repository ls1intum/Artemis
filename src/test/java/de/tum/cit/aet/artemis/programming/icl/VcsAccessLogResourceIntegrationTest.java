package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

class VcsAccessLogResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "vcsaccesslogresourcetest";

    private ProgrammingExercise programmingExercise;

    private String defaultParams;

    SearchTermPageableSearchDTO<String> search;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        defaultParams = "&page=1&pageSize=25&sortingOrder=ASCENDING&sortedColumn=id";
        search = pageableSearchUtilService.configureSearch("");
    }

    @AfterEach
    void tearDown() {
        vcsAccessLogRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLogOfStudent() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "instructor1");
        var user = userTestRepository.getUser();
        saveAccessLogs(user, participation);
        var studentRequestPath = "/api/programming-exercises/" + participation.getExercise().getId() + "/repository/USER/vcs-access-log?repositoryId=" + participation.getId();

        var result = request.getSearchResult(studentRequestPath + defaultParams, HttpStatus.OK, VcsAccessLogDTO.class, new LinkedMultiValueMap<>());

        var accessLogsList = result.getResultsOnPage();
        assertThat(result.getNumberOfPages()).isEqualTo(2);
        assertThat(accessLogsList).hasSize(25);
        VcsAccessLogDTO firstEntry = accessLogsList.getFirst();
        assertThat(firstEntry.userId()).isEqualTo(user.getId());
        assertThat(firstEntry.commitHash()).isEqualTo("hash:1");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLogOfStudent_sortedDescendingByTimestamp() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "instructor1");
        var user = userTestRepository.getUser();
        saveAccessLogs(user, participation);
        var studentRequestPath = "/api/programming-exercises/" + participation.getExercise().getId() + "/repository/USER/vcs-access-log?repositoryId=" + participation.getId();
        defaultParams = "&page=1&pageSize=25&sortingOrder=DESCENDING&sortedColumn=timestamp";

        var result = request.getSearchResult(studentRequestPath + defaultParams, HttpStatus.OK, VcsAccessLogDTO.class, new LinkedMultiValueMap<>());

        var accessLogsList = result.getResultsOnPage();
        assertThat(result.getNumberOfPages()).isEqualTo(2);
        VcsAccessLogDTO firstEntry = accessLogsList.getFirst();
        assertThat(firstEntry.userId()).isEqualTo(user.getId());
        assertThat(firstEntry.commitHash()).isEqualTo("hash:30");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLogForTemplateParticipation() throws Exception {
        var user = userTestRepository.getUser();
        saveAccessLogs(user, programmingExercise.getTemplateParticipation());
        var templateRequestPath = "/api/programming-exercises/" + programmingExercise.getId() + "/repository/TEMPLATE/vcs-access-log?repositoryId=0";

        var result = request.getSearchResult(templateRequestPath + defaultParams, HttpStatus.OK, VcsAccessLogDTO.class, new LinkedMultiValueMap<>());

        var accessLogsList = result.getResultsOnPage();
        assertThat(result.getNumberOfPages()).isEqualTo(2);
        assertThat(accessLogsList).hasSize(25);
        VcsAccessLogDTO firstEntry = accessLogsList.getFirst();
        assertThat(firstEntry.userId()).isEqualTo(user.getId());
        assertThat(firstEntry.commitHash()).isEqualTo("hash:1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLogForSolutionParticipation() throws Exception {
        var user = userTestRepository.getUser();
        saveAccessLogs(user, programmingExercise.getSolutionParticipation());
        var solutionRequestPath = "/api/programming-exercises/" + programmingExercise.getId() + "/repository/SOLUTION/vcs-access-log?repositoryId=0";

        var result = request.getSearchResult(solutionRequestPath + defaultParams, HttpStatus.OK, VcsAccessLogDTO.class, new LinkedMultiValueMap<>());

        var accessLogsList = result.getResultsOnPage();
        assertThat(result.getNumberOfPages()).isEqualTo(2);
        assertThat(accessLogsList).hasSize(25);
        VcsAccessLogDTO firstEntry = accessLogsList.getFirst();
        assertThat(firstEntry.userId()).isEqualTo(user.getId());
        assertThat(firstEntry.commitHash()).isEqualTo("hash:1");
    }

    private void saveAccessLogs(User user, Participation participation) {
        var newAccessLogs = new ArrayList<VcsAccessLog>();
        for (int i = 1; i <= 30; i++) {
            newAccessLogs
                    .add(new VcsAccessLog(user, participation, "instructor", "instructorMail@mail.de", RepositoryActionType.READ, AuthenticationMechanism.SSH, "hash:" + i, ""));
        }
        vcsAccessLogRepository.saveAll(newAccessLogs);
    }

}
