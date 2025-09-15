package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.service.RepositoryUriConversionUtil;

class RepositoryUriConversionUtilTest {

    @BeforeEach
    void setUp() {
        RepositoryUriConversionUtil.overrideServerUrlForCurrentThread("https://vc.example.org/");
    }

    @AfterEach
    void tearDown() {
        RepositoryUriConversionUtil.clearServerUrlOverrideForCurrentThread();
    }

    @Test
    void toFullRepositoryUriReturnsNullForNullOrEmpty() {
        assertThat(RepositoryUriConversionUtil.toFullRepositoryUri(null)).isNull();
        assertThat(RepositoryUriConversionUtil.toFullRepositoryUri("")).isNull();
    }

    @Test
    void toShortRepositoryUriReturnsNullForNullOrEmpty() {
        assertThat(RepositoryUriConversionUtil.toShortRepositoryUri(null)).isNull();
        assertThat(RepositoryUriConversionUtil.toShortRepositoryUri("")).isNull();
    }

    @Test
    void toFullRepositoryUriBuildsExpectedUrlAndNormalizesBase() {
        String full = RepositoryUriConversionUtil.toFullRepositoryUri("project/exercise-name");
        assertThat(full).isEqualTo("https://vc.example.org/git/project/exercise-name.git");
    }

    @Test
    void toShortRepositoryUriStripsPrefixAndSuffix() {
        String shortUri = RepositoryUriConversionUtil.toShortRepositoryUri("https://vc.example.org/git/my-course/my-exercise.git");
        assertThat(shortUri).isEqualTo("my-course/my-exercise");
    }

    @Test
    void toShortRepositoryUriStripsPrefixEvenIfSuffixMissing() {
        String shortUri = RepositoryUriConversionUtil.toShortRepositoryUri("https://vc.example.org/git/alpha/beta");
        assertThat(shortUri).isEqualTo("alpha/beta");
    }

    @Test
    void toShortRepositoryUriThrowsIfGitPrefixMissing() {
        assertThatIllegalArgumentException().isThrownBy(() -> RepositoryUriConversionUtil.toShortRepositoryUri("https://vc.example.org/alpha/beta.git"))
                .withMessageContaining("/git/");
    }

    @Test
    void overrideIsPerThreadAndInheritedByChildThreads() throws Exception {
        AtomicReference<String> childResult = new AtomicReference<>();
        Thread child = new Thread(() -> childResult.set(RepositoryUriConversionUtil.toFullRepositoryUri("x/y")));
        child.start();
        child.join();

        assertThat(childResult.get()).as("Child thread should inherit override").isEqualTo("https://vc.example.org/git/x/y.git");

        RepositoryUriConversionUtil.overrideServerUrlForCurrentThread("http://localhost:48888///");
        String currentThreadUrl = RepositoryUriConversionUtil.toFullRepositoryUri("x/y");
        assertThat(currentThreadUrl).isEqualTo("http://localhost:48888/git/x/y.git");
    }
}
