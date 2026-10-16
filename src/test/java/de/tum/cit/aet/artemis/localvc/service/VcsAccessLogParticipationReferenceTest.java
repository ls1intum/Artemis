package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Recording a rejected git authentication attributes the access log to the repository's participation and needs nothing
 * from it but its key, so the lookup reads an id and hands back an unloaded reference. Both halves of that only hold
 * against a database: the project key a test repository resolves by is mapped on a secondary table, and a reference has
 * to serve as a foreign key without ever being loaded.
 */
class VcsAccessLogParticipationReferenceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "vcsaccessref";

    @Autowired
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private ProgrammingExercise exercise;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    void resolvesTheSharedSolutionParticipationOfATestRepositoryByProjectKey() {
        long expectedId = solutionParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId()).getId();

        // A test repository has no participation of its own and no uri that identifies one, so it resolves by project key.
        var reference = programmingExerciseParticipationService.getParticipationReferenceForRepository(RepositoryType.TESTS.toString(), "not read for this type",
                exercise.getProjectKey());

        assertThat(reference).isPresent();
        assertThat(reference.get().getId()).isEqualTo(expectedId);
        assertThat(Hibernate.isInitialized(reference.get())).as("the participation is referenced, not loaded").isFalse();
    }

    @Test
    void resolvesAStudentParticipationByItsRepositoryUriWithoutTheGitService() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");

        // The uri of a fetch carries the git service it addresses, which is not part of the stored uri.
        var reference = programmingExerciseParticipationService.getParticipationReferenceForRepository(TEST_PREFIX + "student1",
                participation.getRepositoryUri() + "/git-upload-pack", exercise.getProjectKey());

        assertThat(reference).isPresent();
        assertThat(reference.get().getId()).isEqualTo(participation.getId());
        assertThat(Hibernate.isInitialized(reference.get())).as("the participation is referenced, not loaded").isFalse();
    }

    @Test
    void findsNothingForAProjectKeyThatBelongsToNoExercise() {
        assertThat(programmingExerciseParticipationService.getParticipationReferenceForRepository(RepositoryType.TESTS.toString(), "not read for this type", "NOSUCHPROJECTKEY"))
                .isEmpty();
    }

    @Test
    void writesAnAccessLogThroughAReferenceThatWasNeverLoaded() {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        var reference = programmingExerciseParticipationService.getParticipationReferenceForRepository(TEST_PREFIX + "student1", participation.getRepositoryUri(),
                exercise.getProjectKey());
        assertThat(reference).isPresent();

        // The log row holds the participation as a foreign key, which a reference satisfies without being loaded.
        vcsAccessLogRepository.save(new VcsAccessLog(user, (Participation) reference.get(), user.getName(), user.getEmail(), RepositoryActionType.CLONE_FAIL,
                AuthenticationMechanism.PASSWORD, "", "127.0.0.1"));

        assertThat(vcsAccessLogRepository.findAllByParticipationId(participation.getId())).hasSize(1);
    }
}
