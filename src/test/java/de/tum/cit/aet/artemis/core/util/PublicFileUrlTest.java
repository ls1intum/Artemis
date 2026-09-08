package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

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
     * One case per served file type that carries a filename, with the URL template written out literally so that changing a template has to change this file too.
     * <p>
     * The filename is a parameter, and the segment it is expected to produce is a second one, because the same set of cases is reused to pin how a filename is encoded. Writing
     * the expected segment out rather than deriving it is what makes a change to the encoding fail here instead of going unnoticed.
     *
     * @param filename        the filename to build every case with
     * @param expectedSegment the last path segment the URL is expected to end in
     * @return the descriptor and the URL it is expected to produce, for every file type that takes a filename
     */
    private static List<PublicFileUrlCase> casesFor(String filename, String expectedSegment) {
        return List.of(new PublicFileUrlCase(new PublicFileUrl.CourseIcon(3L, filename), "files/courses/3/icons/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.ProfilePicture(7L, filename), "files/users/7/profile-pictures/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.ExamUserSignature(8L, filename), "files/exam-users/8/signatures/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.ExamUserImage(9L, filename), "files/exam-users/9/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.DragAndDropBackground(42L, filename), "files/drag-and-drop/questions/42/backgrounds/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.DragItem(7L, 2L, filename), "files/drag-and-drop/questions/7/drag-items/2/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.LectureAttachment(4L, filename), "files/attachments/lectures/4/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.AttachmentVideoUnitFile(5L, filename), "files/attachments/attachment-video-units/5/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.StudentVersionSlides(5L, filename), "files/attachments/attachment-video-units/5/student/" + expectedSegment),
                new PublicFileUrlCase(new PublicFileUrl.FileUploadSubmission(7L, 9L, filename), "files/file-upload-exercises/7/submissions/9/" + expectedSegment));
    }

    /**
     * @param filename the filename to build every case with, which is also what every URL is expected to end in
     * @return the descriptor and the URL it is expected to produce, for every file type that takes a filename
     */
    private static List<PublicFileUrlCase> casesFor(String filename) {
        return casesFor(filename, filename);
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
     * Pins that a non-ASCII filename is percent-encoded as UTF-8 rather than written into the URL as it stands. Sanitization reduces every filename this release writes to
     * {@code [A-Za-z0-9._-]}, so this is about the values that were stored before it did.
     */
    @Test
    void shouldPercentEncodeANonAsciiFilename() {
        assertThat(new PublicFileUrl.CourseIcon(3L, "fübar-é.png").url()).hasToString("files/courses/3/icons/f%C3%BCbar-%C3%A9.png");

        for (PublicFileUrlCase testCase : casesFor("fübar-é.png", "f%C3%BCbar-%C3%A9.png")) {
            assertThat(testCase.url().url()).as("URL of %s", testCase.url()).hasToString(testCase.expectedUrl());
        }
    }

    /**
     * Pins that a filename containing a space produces a usable URL rather than an {@link IllegalArgumentException}.
     * <p>
     * This is the case that matters most. Building a URL is now on the read path of every entity that carries a file, and a filename stored before filenames were sanitized may
     * contain a space, so a throw here would turn one unlucky row into a failed response for the whole lecture, course or exam it belongs to.
     */
    @Test
    void shouldPercentEncodeASpaceInAFilename() {
        for (PublicFileUrlCase testCase : casesFor("my file.png", "my%20file.png")) {
            assertThat(testCase.url().url()).as("URL of %s", testCase.url()).hasToString(testCase.expectedUrl());
        }
    }

    /**
     * Pins that a {@code #} in a filename is escaped instead of starting a URI fragment, so the whole filename stays in the path where the server can read it back.
     */
    @Test
    void shouldPercentEncodeAHashSoItCannotStartAFragment() {
        var url = new PublicFileUrl.CourseIcon(3L, "a#b.png").url();

        assertThat(url).hasToString("files/courses/3/icons/a%23b.png");
        assertThat(url.getPath()).isEqualTo("files/courses/3/icons/a#b.png");
        assertThat(url.getFragment()).isNull();
    }

    /**
     * Pins that a filename cannot forge a path segment. Sanitization replaces every separator, so this is defence in depth rather than a reachable case.
     */
    @Test
    void shouldPercentEncodeASlashSoAFilenameCannotForgeASegment() {
        assertThat(new PublicFileUrl.CourseIcon(3L, "../../etc/passwd").url()).hasToString("files/courses/3/icons/..%2F..%2Fetc%2Fpasswd");
    }

    /**
     * Pins that the encoding and {@link FileSystemLocation#storedFilename} are inverses of each other.
     * <p>
     * A client is served the URL of a file and sends the same string back in the next update of the entity. If the two did not agree, the escaped form would be stored and the
     * next read would escape it again, so the URL would point at a file that does not exist.
     */
    @Test
    void shouldStoreTheOriginalFilenameAgainWhenTheServedUrlComesBack() {
        for (String filename : List.of("file.png", "my file.png", "a#b.png", "fübar-é.png", "100% done.pdf")) {
            String served = new PublicFileUrl.LectureAttachment(4L, filename).clientPath();

            assertThat(FileSystemLocation.storedFilename(served)).as("round trip of %s", filename).isEqualTo(filename);
        }
    }

    /**
     * Pins that a bare per cent sign in a stored filename survives being stored again. It is not a valid escape, so decoding it would throw; such a filename is kept verbatim
     * instead.
     */
    @Test
    void shouldKeepAFilenameWhosePerCentSignIsNotAnEscape() {
        assertThat(FileSystemLocation.storedFilename("100%.pdf")).isEqualTo("100%.pdf");
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
