package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.FilePathType;

class PublicFileUrlTest {

    private record PublicFileUrlCase(PublicFileUrl url, String expectedUrl) {
    }

    /**
     * One case per served file type that carries a filename, with the URL template written out literally so that changing a template has to change this file too. The filename
     * is a parameter because the same set of cases is reused to pin how a filename is encoded, and there the expectation is deliberately assembled by plain concatenation: if
     * {@link PublicFileUrl} ever starts to escape a filename, these assertions fail rather than the change going unnoticed.
     *
     * @param filename the filename to build every case with
     * @return the descriptor and the URL it is expected to produce, for every file type that takes a filename
     */
    private static List<PublicFileUrlCase> casesFor(String filename) {
        return List.of(new PublicFileUrlCase(new PublicFileUrl.CourseIcon(3L, filename), "files/courses/3/icons/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.ProfilePicture(7L, filename), "files/users/7/profile-pictures/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.ExamUserSignature(8L, filename), "files/exam-users/8/signatures/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.ExamUserImage(9L, filename), "files/exam-users/9/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.DragAndDropBackground(42L, filename), "files/drag-and-drop/questions/42/backgrounds/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.DragItem(7L, 2L, filename), "files/drag-and-drop/questions/7/drag-items/2/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.LectureAttachment(4L, filename), "files/attachments/lectures/4/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.AttachmentVideoUnitFile(5L, filename), "files/attachments/attachment-video-units/5/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.StudentVersionSlides(5L, filename), "files/attachments/attachment-video-units/5/student/" + filename),
                new PublicFileUrlCase(new PublicFileUrl.FileUploadSubmission(7L, 9L, filename), "files/file-upload-exercises/7/submissions/9/" + filename));
    }

    @Test
    void shouldBuildTheUrlOfEveryFileTypeThatCarriesAFilename() {
        for (PublicFileUrlCase testCase : casesFor("file.png")) {
            assertThat(testCase.url().url()).as("URL of %s", testCase.url()).hasToString(testCase.expectedUrl());
        }
    }

    @Test
    void shouldBuildTheSlideUrlFromTheSlideIdAlone() {
        // A slide is the one served file whose URL carries no filename: FileResource looks the slide up by its id and reads the filename off the slide.
        assertThat(new PublicFileUrl.Slide(11L).url()).hasToString("files/slides/11");
    }

    /**
     * Pins that a filename is passed through unencoded. Sanitization reduces every stored filename to {@code [A-Za-z0-9._-]} long before it reaches a URL, so this cannot come
     * up in production, but it has to fail loudly if a later phase starts escaping a filename, because that would change what the client receives.
     */
    @Test
    void shouldPassANonAsciiFilenameThroughUnencoded() {
        assertThat(new PublicFileUrl.CourseIcon(3L, "fübar-é.png").url()).hasToString("files/courses/3/icons/fübar-é.png");

        for (PublicFileUrlCase testCase : casesFor("fübar-é.png")) {
            assertThat(testCase.url().url()).as("URL of %s", testCase.url()).hasToString(testCase.expectedUrl());
        }
    }

    /**
     * Pins that a filename containing a space is rejected instead of silently producing an unusable URL. Sanitization replaces every space with an underscore before a file is
     * written, so this is a guard rather than a limitation any caller can hit.
     */
    @Test
    void shouldRejectAFilenameContainingASpace() {
        for (PublicFileUrlCase testCase : casesFor("my file.png")) {
            assertThatExceptionOfType(IllegalArgumentException.class).as("URL of %s", testCase.url()).isThrownBy(() -> testCase.url().url())
                    .withMessageContaining("Illegal character in path");
        }
    }

    /**
     * Pins that a {@code #} in a filename starts a URI fragment, so the URL keeps the whole filename when it is written out but loses everything from the {@code #} onwards when
     * it is read back as a path. Sanitization removes the character, so no stored file can reach this; the assertion exists so that the asymmetry is documented rather than
     * discovered.
     */
    @Test
    void shouldTreatAHashInAFilenameAsAFragment() {
        var url = new PublicFileUrl.CourseIcon(3L, "a#b.png").url();

        assertThat(url).hasToString("files/courses/3/icons/a#b.png");
        assertThat(url.getPath()).isEqualTo("files/courses/3/icons/a");
        assertThat(url.getFragment()).isEqualTo("b.png");
    }

    /**
     * Every file type served over REST has to have a URL template, and every template has to be covered above. A new {@link FilePathType} therefore fails the build until
     * someone has decided what its URL is, and a new record fails the build until it has a test case. {@link FilePathType#TEMPORARY} is the one type with no template, because a
     * temporary file is not served over REST at all.
     */
    @Test
    void shouldCoverEveryServedFilePathTypeExactlyOnce() {
        List<PublicFileUrl> covered = Stream.concat(casesFor("file.png").stream().map(PublicFileUrlCase::url), Stream.of(new PublicFileUrl.Slide(11L))).toList();

        assertThat(covered).hasSize(PublicFileUrl.class.getPermittedSubclasses().length);
        assertThat(covered.stream().map(PublicFileUrl::filePathType).collect(Collectors.toSet()))
                .isEqualTo(Arrays.stream(FilePathType.values()).filter(type -> type != FilePathType.TEMPORARY).collect(Collectors.toSet()));
    }
}
