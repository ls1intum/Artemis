package de.tum.cit.aet.artemis.buildagent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class RepositoryInfoTest {

    private static RepositoryInfo info(String name, String[] uris, String[] dirs) {
        return new RepositoryInfo(name, RepositoryType.USER, RepositoryType.TESTS, "assignment", "test", "solution", uris, dirs);
    }

    @Test
    void equalsAndHashCode_comparesArrayContent() {
        var a = info("repo", new String[] { "u1", "u2" }, new String[] { "d1" });
        var b = info("repo", new String[] { "u1", "u2" }, new String[] { "d1" });

        assertThat(a).isEqualTo(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void equals_differsOnAnyComponent() {
        var base = info("repo", new String[] { "u1" }, new String[] { "d1" });

        assertThat(base).isNotEqualTo(null).isNotEqualTo("repo");
        assertThat(base).isNotEqualTo(info("other", new String[] { "u1" }, new String[] { "d1" }));
        assertThat(base).isNotEqualTo(info("repo", new String[] { "u2" }, new String[] { "d1" }));
        assertThat(base).isNotEqualTo(info("repo", new String[] { "u1" }, new String[] { "d2" }));
        assertThat(base).isNotEqualTo(
                new RepositoryInfo("repo", RepositoryType.TEMPLATE, RepositoryType.TESTS, "assignment", "test", "solution", new String[] { "u1" }, new String[] { "d1" }));
        assertThat(base).isNotEqualTo(
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.SOLUTION, "assignment", "test", "solution", new String[] { "u1" }, new String[] { "d1" }));
        assertThat(base).isNotEqualTo(new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.TESTS, "x", "test", "solution", new String[] { "u1" }, new String[] { "d1" }));
        assertThat(base)
                .isNotEqualTo(new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.TESTS, "assignment", "x", "solution", new String[] { "u1" }, new String[] { "d1" }));
        assertThat(base)
                .isNotEqualTo(new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.TESTS, "assignment", "test", "x", new String[] { "u1" }, new String[] { "d1" }));
    }

    @Test
    void equalsAndHashCode_nullArraysAreEqual() {
        var a = info("repo", null, null);
        var b = info("repo", null, null);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void toString_containsArrayElements() {
        var text = info("repo", new String[] { "u1", "u2" }, new String[] { "d1" }).toString();

        assertThat(text).startsWith("RepositoryInfo[repositoryName=repo").contains("repositoryType=user", "triggeredByPushTo=tests", "assignmentRepositoryUri=assignment",
                "testRepositoryUri=test", "solutionRepositoryUri=solution", "auxiliaryRepositoryUris=[u1, u2]", "auxiliaryRepositoryCheckoutDirectories=[d1]");
    }
}
