package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspaceSnapshotTest {

    @ParameterizedTest
    @ValueSource(strings = { "", "/etc/passwd", "../other", "tests/../solution", "tests//Example.java", "./file", "tests/.git/config", "tests/.GIT/config", "tests\\Example.java",
            "C:/file", "tests/file\n.java", "tests/" })
    void rejectsUnsafePaths(String path) {
        assertThatIllegalArgumentException().isThrownBy(() -> file(path, 1, false));
    }

    @Test
    void preservesBinaryFilesAndDefensivelyCopiesBothWays() {
        byte[] content = { 0, -1, 42 };
        WorkspaceFile wrapper = new WorkspaceFile("tests/gradle/wrapper/gradle-wrapper.jar", content, false);
        content[0] = 99;
        byte[] read = wrapper.content();
        read[1] = 99;
        assertThat(wrapper.content()).containsExactly(0, -1, 42);
        assertThat(wrapper).isEqualTo(new WorkspaceFile(wrapper.path(), new byte[] { 0, -1, 42 }, false));
    }

    @Test
    void hashIncludesPathContentAndExecutableBitButNotInputOrder() {
        WorkspaceFile build = file("tests/build.gradle", 1, false);
        WorkspaceFile wrapper = file("tests/gradlew", 2, true);
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot(List.of(build, wrapper));
        assertThat(snapshot.sha256()).isEqualTo(new WorkspaceSnapshot(List.of(wrapper, build)).sha256());
        assertThat(snapshot.sha256()).isNotEqualTo(new WorkspaceSnapshot(List.of(build, file(wrapper.path(), 2, false))).sha256());
        assertThat(snapshot.sha256()).isNotEqualTo(new WorkspaceSnapshot(List.of(build, file(wrapper.path(), 3, true))).sha256());
        assertThat(snapshot.sha256()).isNotEqualTo(new WorkspaceSnapshot(List.of(build, file("tests/gradlew-other", 2, true))).sha256());
        assertThat(snapshot.sha256()).hasSize(64);
    }

    @Test
    void snapshotDoesNotShareTheMutableInputList() {
        var files = new ArrayList<>(List.of(file("tests/gradlew", 1, true)));
        var snapshot = new WorkspaceSnapshot(files);
        files.clear();
        assertThat(snapshot.files()).hasSize(1);
        org.assertj.core.api.Assertions.assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> snapshot.files().clear());
    }

    @Test
    void rejectsDuplicatePathsBeforeExtraction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkspaceSnapshot(List.of(file("tests/build.gradle", 1, false), file("tests/build.gradle", 2, true))));
    }

    @Test
    void rejectsFileDirectoryCollisionsRegardlessOfOrder() {
        WorkspaceFile parent = file("tests/a", 1, false);
        WorkspaceFile child = file("tests/a/source.java", 2, false);
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkspaceSnapshot(List.of(parent, child)));
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkspaceSnapshot(List.of(child, parent)));
    }

    @Test
    void rejectsOversizedFiles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkspaceFile("tests/large", new byte[WorkspaceFile.MAX_FILE_BYTES + 1], false));
    }

    @Test
    void rejectsTooManyFiles() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkspaceSnapshot(Collections.nCopies(WorkspaceSnapshot.MAX_FILES + 1, file("file", 1, false))));
    }

    @Test
    void rejectsOversizedTotalEvenWhenIndividualFilesFit() {
        List<WorkspaceFile> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            files.add(new WorkspaceFile("tests/file-" + i, new byte[WorkspaceFile.MAX_FILE_BYTES], false));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkspaceSnapshot(files));
    }

    private static WorkspaceFile file(String path, int value, boolean executable) {
        return new WorkspaceFile(path, new byte[] { (byte) value }, executable);
    }
}
