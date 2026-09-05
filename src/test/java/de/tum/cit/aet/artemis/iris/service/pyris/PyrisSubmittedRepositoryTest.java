package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Pure unit tests for {@link PyrisDTOService#buildSubmittedRepository} - the submitted-vs-local diff set. No Spring
 * context: the result is a pure function of committed + uncommitted files + language, so the committed side can be
 * controlled directly (the integration path never checks out a real committed repository).
 */
class PyrisSubmittedRepositoryTest {

    private static final ProgrammingLanguage JAVA = ProgrammingLanguage.JAVA;

    @Test
    void changedExistingCodeFile_carriesCommittedContent() {
        var committed = Map.of("src/Main.java", "OLD");
        var uncommitted = Map.of("src/Main.java", "NEW");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).containsExactly(entry("src/Main.java", "OLD"));
    }

    @Test
    void newLocalCodeFile_carriesEmptySubmittedSide_whenCommittedSetWasRead() {
        var committed = Map.of("src/Existing.java", "x");
        var uncommitted = Map.of("src/New.java", "brand new");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).containsExactly(entry("src/New.java", ""));
    }

    @Test
    void readableButEmptyCommittedSet_stillMarksLocalFilesAsNew() {
        // A repository that checks out fine but holds no file of the exercise language filters down to an empty map.
        // That is a fact about its contents, not about the fetch, so the student's local files are genuinely new and
        // must keep their all-added baseline. Deriving readability from the map being empty lost exactly this case.
        var committed = Map.<String, String>of();
        var uncommitted = Map.of("src/Main.java", "code");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).containsExactly(entry("src/Main.java", ""));
    }

    @Test
    void unchangedCodeFile_isSkipped() {
        var committed = Map.of("src/Main.java", "same");
        var uncommitted = Map.of("src/Main.java", "same");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).isEmpty();
    }

    @Test
    void nonLanguageFile_isExcluded() {
        var committed = Map.of("src/Main.java", "x");
        var uncommitted = Map.of("README.md", "# changed");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, true)).isEmpty();
    }

    @Test
    void unreadableCommittedSet_doesNotFabricateAllAddedDiff() {
        // Repo fetch failed / no commits yet. Do NOT claim every changed file is "new".
        var committed = Map.<String, String>of();
        var uncommitted = Map.of("src/Main.java", "code");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, JAVA, false)).isEmpty();
    }

    @Test
    void nullLanguage_treatsAllFilesAsCode() {
        // language == null => no extension filtering, mirroring getFilteredRepositoryContents.
        var committed = Map.of("README.md", "OLD");
        var uncommitted = Map.of("README.md", "NEW");
        assertThat(PyrisDTOService.buildSubmittedRepository(committed, uncommitted, null, true)).containsExactly(entry("README.md", "OLD"));
    }
}
