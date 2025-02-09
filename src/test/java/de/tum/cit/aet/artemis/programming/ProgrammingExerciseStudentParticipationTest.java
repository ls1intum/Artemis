package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.dto.RepoUrlProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

public class ProgrammingExerciseStudentParticipationTest extends AbstractProgrammingIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "pespinttest";

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoUrl() throws Exception {
        Course course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        programmingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");

        var encodedUrl = URLEncoder.encode(participation.getRepositoryUri(), StandardCharsets.UTF_8);
        RepoUrlProgrammingStudentParticipationDTO participationDTO = request.get("/api/programming-exercise-participations/repo-url?repoUrl=" + encodedUrl, HttpStatus.OK,
                RepoUrlProgrammingStudentParticipationDTO.class);

        assertThat(participationDTO.id()).isEqualTo(participation.getId());
        assertThat(participationDTO.exercise().id()).isEqualTo(participation.getExercise().getId());
        assertThat(participationDTO.exercise().course().id()).isEqualTo(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(participationDTO.exercise().testCases()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoUrlWithSubmissionAndResult() throws Exception {
        Course course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        programmingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test1");

        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");

        var earlyResult = participationUtilService.createSubmissionAndResult(participation, 1, true);
        var laterResult = participationUtilService.createSubmissionAndResult(participation, 1, true);
        var laterSubmission = laterResult.getSubmission();

        var encodedUrl = URLEncoder.encode(participation.getRepositoryUri(), StandardCharsets.UTF_8);
        RepoUrlProgrammingStudentParticipationDTO participationDTO = request.get("/api/programming-exercise-participations/repo-url?repoUrl=" + encodedUrl, HttpStatus.OK,
                RepoUrlProgrammingStudentParticipationDTO.class);

        assertThat(participationDTO.id()).isEqualTo(participation.getId());
        assertThat(participationDTO.exercise().id()).isEqualTo(participation.getExercise().getId());
        assertThat(participationDTO.exercise().course().id()).isEqualTo(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());

        assertThat(participationDTO.submissions()).hasSize(1);
        assertThat(participationDTO.submissions().toArray(RepoUrlProgrammingStudentParticipationDTO.RepoUrlSubmissionDTO[]::new)[0].id()).isEqualTo(laterSubmission.getId());
        for (var submission : participationDTO.submissions()) {
            assertThat(submission.results()).hasSize(1);
            assertThat(submission.results().toArray(RepoUrlProgrammingStudentParticipationDTO.RepoUrlResultDTO[]::new)[0].id()).isEqualTo(laterResult.getId());
        }

        assertThat(participationDTO.exercise().testCases()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoUrlNotFound() throws Exception {
        Course course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        programmingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");

        URI repoUrl;
        Optional<ProgrammingExerciseStudentParticipation> foundParticipation;
        do {
            repoUrl = new URI(participation.getRepositoryUri());
            repoUrl = new URI(repoUrl.getScheme(), repoUrl.getUserInfo(), repoUrl.getHost(), repoUrl.getPort(), "/" + UUID.randomUUID().toString(), repoUrl.getQuery(),
                    repoUrl.getFragment());
            foundParticipation = programmingExerciseStudentParticipationRepository.findByRepositoryUri(repoUrl.toString());
        }
        while (foundParticipation.isPresent());

        var encodedUrl = URLEncoder.encode(repoUrl.toString(), StandardCharsets.UTF_8);
        String body = request.get("/api/programming-exercise-participations/repo-url?repoUrl=" + encodedUrl, HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoUrlNotVisible() throws Exception {
        Course course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        programmingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student2");

        var encodedUrl = URLEncoder.encode(participation.getRepositoryUri(), StandardCharsets.UTF_8);
        String body = request.get("/api/programming-exercise-participations/repo-url?repoUrl=" + encodedUrl, HttpStatus.FORBIDDEN, String.class);
    }
}
