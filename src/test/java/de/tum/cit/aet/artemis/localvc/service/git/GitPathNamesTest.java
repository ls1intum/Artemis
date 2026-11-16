package de.tum.cit.aet.artemis.localvc.service.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GitPathNamesTest {

    @ParameterizedTest
    @ValueSource(strings = { ".git", ".GIT", ".Git", ".git.", ".git ", ".git. .", "git~1", "GIT~1", ".git::$INDEX_ALLOCATION", ".g‌it", ".gi‎t", "﻿.git" })
    void namesOfTheMetadataDirectoryAreRecognized(String name) {
        assertThat(GitPathNames.isGitMetadataName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { ".gitignore", ".gitattributes", ".github", "git", "gitt~1", "git~10", "src", "my.git", "a.git.txt", "" })
    void otherNamesAreNot(String name) {
        assertThat(GitPathNames.isGitMetadataName(name)).isFalse();
    }

    @Test
    void anySegmentOfAPathIsChecked() {
        assertThat(GitPathNames.containsGitMetadataName("src/.git/config")).isTrue();
        assertThat(GitPathNames.containsGitMetadataName("src\\.GIT\\hooks\\pre-commit")).isTrue();
        assertThat(GitPathNames.containsGitMetadataName(".git")).isTrue();
        assertThat(GitPathNames.containsGitMetadataName("src/main/Main.java")).isFalse();
        assertThat(GitPathNames.containsGitMetadataName("docs/.gitignore")).isFalse();
    }
}
