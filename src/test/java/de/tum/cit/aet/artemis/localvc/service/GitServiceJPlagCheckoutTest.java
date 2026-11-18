package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.exception.GitException;

class GitServiceJPlagCheckoutTest {

    @Test
    void getOrCheckoutRepositoryForJPlag_withoutRepositoryUri_throws() {
        var participation = mock(ProgrammingExerciseParticipation.class);
        var gitService = new GitService();

        assertThatThrownBy(() -> gitService.getOrCheckoutRepositoryForJPlag(participation, Path.of("target", "jplag"))).isInstanceOf(GitException.class)
                .hasMessageContaining("no repository URI");
    }
}
