package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

class GenerationWorkspaceServiceTest {

    @Test
    void sessionSpec_disablesNetworkEgressByDefault() {
        ProgrammingLanguageConfiguration languageConfiguration = mock(ProgrammingLanguageConfiguration.class);
        when(languageConfiguration.getImage(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_GRADLE))).thenReturn("java-image");
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        GenerationWorkspaceService service = new GenerationWorkspaceService(mock(), languageConfiguration, mock(), mock());

        var spec = service.sessionSpec(exercise);

        assertThat(spec.image()).isEqualTo("java-image");
        assertThat(spec.runConfig().network()).isEqualTo("none");
    }
}
