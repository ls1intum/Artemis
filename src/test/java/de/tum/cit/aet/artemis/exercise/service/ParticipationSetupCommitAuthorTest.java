package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.InitialCommitAuthorMode;
import de.tum.cit.aet.artemis.course.dto.InitialCommitAuthorDTO;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.vcs.SetupCommit;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;

/**
 * The setup commit of a student repository is authored by the identity the course chose, whether the exercise was created, imported or existed before the choice.
 */
class ParticipationSetupCommitAuthorTest {

    private final VersionControlService vcs = mock(VersionControlService.class);

    private final CourseConfigurationRepository courseConfigurationRepository = mock(CourseConfigurationRepository.class);

    private ParticipationService service;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    private Supplier<LocalVCRepositoryUri> sourceUri;

    @BeforeEach
    void setUp() {
        var uriService = mock(UriService.class);
        var participations = mock(ProgrammingExerciseStudentParticipationTestRepository.class);
        var gitService = mock(GitService.class);
        when(gitService.getPlaceholderIdentity()).thenReturn(new PersonIdent("Artemis", "artemis@example.com"));
        service = new ParticipationService(Optional.empty(), Optional.of(vcs), null, null, participations, null, null, null, uriService, null, null, null, null, Optional.empty(),
                courseConfigurationRepository, gitService);
        var course = new Course();
        course.setShortName("te");
        exercise = new ProgrammingExercise();
        exercise.setId(42L);
        exercise.setCourse(course);
        exercise.setShortName("st");
        exercise.generateAndSetProjectKey();
        var student = new User();
        student.setLogin("student");
        participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student);
        participation.setInitializationState(InitializationState.UNINITIALIZED);
        var source = new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-template.git");
        sourceUri = () -> source;
        when(uriService.getRepositorySlugFromRepositoryUri(source)).thenReturn("test-template");
        when(participations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vcs.copyRepositoryWithoutHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), any()))
                .thenReturn(new LocalVCRepositoryUri("https://artemis.example.org/git/TEST/test-student.git"));
    }

    private PersonIdent authorOfTheCopy() {
        ReflectionTestUtils.invokeMethod(service, "copyRepository", exercise, sourceUri, (Supplier<String>) () -> "main", (Supplier<Map<String, byte[]>>) Map::of, participation);
        ArgumentCaptor<SetupCommit> setupCommit = ArgumentCaptor.forClass(SetupCommit.class);
        verify(vcs).copyRepositoryWithoutHistory(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), setupCommit.capture());
        return setupCommit.getValue().author();
    }

    @Test
    void thePlaceholderOfTheCourseAuthorsTheSetupCommit() {
        when(courseConfigurationRepository.findInitialCommitAuthorByExerciseId(anyLong()))
                .thenReturn(Optional.of(new InitialCommitAuthorDTO(InitialCommitAuthorMode.PLACEHOLDER, null, null)));

        PersonIdent author = authorOfTheCopy();

        assertThat(author.getName()).isEqualTo("Artemis");
        assertThat(author.getEmailAddress()).isEqualTo("artemis@example.com");
    }

    @Test
    void aCustomIdentityOfTheCourseAuthorsTheSetupCommit() {
        when(courseConfigurationRepository.findInitialCommitAuthorByExerciseId(42L))
                .thenReturn(Optional.of(new InitialCommitAuthorDTO(InitialCommitAuthorMode.CUSTOM, "Course Team", "team@university.example")));

        PersonIdent author = authorOfTheCopy();

        assertThat(author.getName()).isEqualTo("Course Team");
        assertThat(author.getEmailAddress()).isEqualTo("team@university.example");
    }

    @Test
    void withoutAChoiceTheAuthorOfTheFirstCommitOfTheTemplateIsKept() {
        when(courseConfigurationRepository.findInitialCommitAuthorByExerciseId(anyLong()))
                .thenReturn(Optional.of(new InitialCommitAuthorDTO(InitialCommitAuthorMode.EXERCISE_CREATOR, null, null)));

        assertThat(authorOfTheCopy()).isNull();
    }

    @Test
    void aCourseWithoutAConfigurationKeepsTheAuthorOfTheFirstCommitOfTheTemplate() {
        when(courseConfigurationRepository.findInitialCommitAuthorByExerciseId(anyLong())).thenReturn(Optional.empty());

        assertThat(authorOfTheCopy()).isNull();
    }
}
