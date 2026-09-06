package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.core.FilePathType;

/**
 * The location on disk of every stored file type.
 * <p>
 * The expectations spell the directories out rather than deriving them, because they are where files already are: a
 * change that makes one of these assertions fail is a change that makes the server stop finding files it wrote earlier.
 * The directory names are deliberately not the ones in the served URL, which is the whole point of separating the two.
 */
class FileSystemLocationTest {

    private static Path rootPath;

    @BeforeAll
    static void setup() {
        rootPath = readFileUploadPathFromConfig();
        FilePathConverter.setFileUploadPath(rootPath);
    }

    @SuppressWarnings("unchecked")
    private static Path readFileUploadPathFromConfig() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = FileSystemLocationTest.class.getClassLoader().getResourceAsStream("config/application-artemis.yml")) {
            Map<String, Object> config = yaml.load(inputStream);
            Map<String, Object> artemis = (Map<String, Object>) config.get("artemis");
            return Path.of((String) artemis.get("file-upload-path"));
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to read file-upload-path from the test configuration", e);
        }
    }

    private record LocationCase(FileSystemLocation location, Path expectedPath) {
    }

    /**
     * One case per file type, built from a bare filename, which is what a column holds once the stored value is only a filename.
     */
    private static List<LocationCase> casesFor(String filename) {
        return List.of(new LocationCase(new FileSystemLocation.Temporary(filename), rootPath.resolve("images").resolve("temp").resolve(filename)),
                new LocationCase(new FileSystemLocation.CourseIcon(filename), rootPath.resolve("images").resolve("course").resolve("icons").resolve(filename)),
                new LocationCase(new FileSystemLocation.ProfilePicture(filename), rootPath.resolve("images").resolve("user").resolve("profile-pictures").resolve(filename)),
                new LocationCase(new FileSystemLocation.ExamUserSignature(filename), rootPath.resolve("images").resolve("exam-user").resolve("signatures").resolve(filename)),
                new LocationCase(new FileSystemLocation.ExamUserImage(42L, filename), rootPath.resolve("images").resolve("exam-user").resolve("42").resolve(filename)),
                new LocationCase(new FileSystemLocation.DragAndDropBackground(filename),
                        rootPath.resolve("images").resolve("drag-and-drop").resolve("backgrounds").resolve(filename)),
                new LocationCase(new FileSystemLocation.DragItem(filename), rootPath.resolve("images").resolve("drag-and-drop").resolve("drag-items").resolve(filename)),
                new LocationCase(new FileSystemLocation.LectureAttachment(4L, filename), rootPath.resolve("attachments").resolve("lecture").resolve("4").resolve(filename)),
                new LocationCase(new FileSystemLocation.AttachmentVideoUnitFile(4L, filename),
                        rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve(filename)),
                new LocationCase(new FileSystemLocation.StudentVersionSlides(4L, filename),
                        rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("student").resolve(filename)),
                new LocationCase(new FileSystemLocation.Slide(4L, 1, filename),
                        rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("slide").resolve("1").resolve(filename)),
                new LocationCase(new FileSystemLocation.FileUploadSubmission(7L, 9L, filename), FilePathConverter.buildFileUploadSubmissionPath(7L, 9L).resolve(filename)));
    }

    @Test
    void shouldResolveEveryFileTypeFromItsFilename() {
        for (LocationCase testCase : casesFor("file.png")) {
            assertThat(testCase.location().path()).as(testCase.location().getClass().getSimpleName()).isEqualTo(testCase.expectedPath());
        }
    }

    /**
     * Every file type has a case, and each one names a distinct {@link FilePathType}, so a new type cannot be added without a location.
     */
    @Test
    void shouldCoverEveryFileType() {
        List<FileSystemLocation> covered = casesFor("file.png").stream().map(LocationCase::location).toList();

        assertThat(covered).hasSize(FileSystemLocation.class.getPermittedSubclasses().length);
        assertThat(covered.stream().map(FileSystemLocation::filePathType).collect(Collectors.toSet())).containsExactlyInAnyOrder(FilePathType.values());
    }

    /**
     * The columns still hold values in the URL-shaped form the write path emits, so the same location has to come out of one of those. Both spellings the serving endpoint
     * answers to are covered, since a value can have been written in either.
     */
    @Test
    void shouldResolveALegacyStoredValueToTheSameLocation() {
        assertThat(new FileSystemLocation.Temporary("temp/file.tmp").path()).isEqualTo(rootPath.resolve("images").resolve("temp").resolve("file.tmp"));

        assertThat(new FileSystemLocation.CourseIcon("course/icons/3/icon.png").path())
                .isEqualTo(rootPath.resolve("images").resolve("course").resolve("icons").resolve("icon.png"));
        assertThat(new FileSystemLocation.CourseIcon("courses/3/icons/icon.png").path())
                .isEqualTo(rootPath.resolve("images").resolve("course").resolve("icons").resolve("icon.png"));

        assertThat(new FileSystemLocation.ProfilePicture("user/profile-pictures/7/avatar.jpg").path())
                .isEqualTo(rootPath.resolve("images").resolve("user").resolve("profile-pictures").resolve("avatar.jpg"));
        assertThat(new FileSystemLocation.ProfilePicture("users/7/profile-pictures/avatar.jpg").path())
                .isEqualTo(rootPath.resolve("images").resolve("user").resolve("profile-pictures").resolve("avatar.jpg"));

        assertThat(new FileSystemLocation.ExamUserSignature("exam-user/signatures/8/sign.png").path())
                .isEqualTo(rootPath.resolve("images").resolve("exam-user").resolve("signatures").resolve("sign.png"));
        assertThat(new FileSystemLocation.ExamUserImage(42L, "exam-user/42/photo.jpg").path())
                .isEqualTo(rootPath.resolve("images").resolve("exam-user").resolve("42").resolve("photo.jpg"));

        assertThat(new FileSystemLocation.DragAndDropBackground("drag-and-drop/backgrounds/42/bg.png").path())
                .isEqualTo(rootPath.resolve("images").resolve("drag-and-drop").resolve("backgrounds").resolve("bg.png"));
        assertThat(new FileSystemLocation.DragItem("drag-and-drop/questions/42/drag-items/5/item.png").path())
                .isEqualTo(rootPath.resolve("images").resolve("drag-and-drop").resolve("drag-items").resolve("item.png"));

        assertThat(new FileSystemLocation.LectureAttachment(4L, "attachments/lecture/4/slides.pdf").path())
                .isEqualTo(rootPath.resolve("attachments").resolve("lecture").resolve("4").resolve("slides.pdf"));
        assertThat(new FileSystemLocation.AttachmentVideoUnitFile(4L, "attachments/attachment-unit/4/file.pdf").path())
                .isEqualTo(rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("file.pdf"));
        assertThat(new FileSystemLocation.StudentVersionSlides(4L, "attachments/attachment-unit/4/student/notes.pdf").path())
                .isEqualTo(rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("student").resolve("notes.pdf"));
        assertThat(new FileSystemLocation.Slide(4L, 1, "attachments/attachment-unit/4/slide/1/slide1.png").path())
                .isEqualTo(rootPath.resolve("attachments").resolve("attachment-unit").resolve("4").resolve("slide").resolve("1").resolve("slide1.png"));

        assertThat(new FileSystemLocation.FileUploadSubmission(7L, 9L, "file-upload-exercises/7/submissions/9/solution.txt").path())
                .isEqualTo(FilePathConverter.buildFileUploadSubmissionPath(7L, 9L).resolve("solution.txt"));
    }

    /**
     * A value that has lost the metadata it once carried still resolves, because the metadata now comes from the entity. This is what makes the order of the remaining steps
     * free: a column that is shortened to a bare filename needs no change here.
     */
    @Test
    void shouldNotDependOnAnyLeadingSegment() {
        assertThat(new FileSystemLocation.LectureAttachment(4L, "slides.pdf").path())
                .isEqualTo(new FileSystemLocation.LectureAttachment(4L, "attachments/lecture/9999/slides.pdf").path());
    }

    /**
     * A caller holding a served URL can locate the same file without restating its metadata, for every type but a slide: a slide URL names the slide, while the image is stored
     * under its unit and its number, so that one has to go through the slide row.
     */
    @Test
    void shouldLocateTheFileOfAServedUrl() {
        assertThat(FileSystemLocation.of(new PublicFileUrl.CourseIcon(3L, "icon.png"))).contains(new FileSystemLocation.CourseIcon("icon.png"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.ProfilePicture(7L, "avatar.jpg"))).contains(new FileSystemLocation.ProfilePicture("avatar.jpg"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.ExamUserSignature(8L, "sign.png"))).contains(new FileSystemLocation.ExamUserSignature("sign.png"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.ExamUserImage(9L, "photo.jpg"))).contains(new FileSystemLocation.ExamUserImage(9L, "photo.jpg"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.DragAndDropBackground(42L, "bg.png"))).contains(new FileSystemLocation.DragAndDropBackground("bg.png"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.DragItem(7L, 2L, "item.png"))).contains(new FileSystemLocation.DragItem("item.png"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.LectureAttachment(4L, "slides.pdf"))).contains(new FileSystemLocation.LectureAttachment(4L, "slides.pdf"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.AttachmentVideoUnitFile(5L, "file.pdf"))).contains(new FileSystemLocation.AttachmentVideoUnitFile(5L, "file.pdf"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.StudentVersionSlides(5L, "notes.pdf"))).contains(new FileSystemLocation.StudentVersionSlides(5L, "notes.pdf"));
        assertThat(FileSystemLocation.of(new PublicFileUrl.FileUploadSubmission(7L, 9L, "solution.txt")))
                .contains(new FileSystemLocation.FileUploadSubmission(7L, 9L, "solution.txt"));

        assertThat(FileSystemLocation.of(new PublicFileUrl.Slide(11L))).isEqualTo(Optional.empty());
    }

    /**
     * Every served URL type is covered by the bridge above, so a new one cannot be added without deciding how its file is located.
     */
    @Test
    void shouldCoverEveryServedUrlType() {
        List<PublicFileUrl> urls = Stream.<PublicFileUrl>of(new PublicFileUrl.CourseIcon(3L, "icon.png"), new PublicFileUrl.ProfilePicture(7L, "avatar.jpg"),
                new PublicFileUrl.ExamUserSignature(8L, "sign.png"), new PublicFileUrl.ExamUserImage(9L, "photo.jpg"), new PublicFileUrl.DragAndDropBackground(42L, "bg.png"),
                new PublicFileUrl.DragItem(7L, 2L, "item.png"), new PublicFileUrl.LectureAttachment(4L, "slides.pdf"), new PublicFileUrl.AttachmentVideoUnitFile(5L, "file.pdf"),
                new PublicFileUrl.StudentVersionSlides(5L, "notes.pdf"), new PublicFileUrl.Slide(11L), new PublicFileUrl.FileUploadSubmission(7L, 9L, "solution.txt")).toList();

        assertThat(urls).hasSize(PublicFileUrl.class.getPermittedSubclasses().length);
        assertThat(urls.stream().map(PublicFileUrl::getClass).distinct().count()).isEqualTo(urls.size());
        assertThat(Arrays.stream(FilePathType.values()).filter(type -> type != FilePathType.TEMPORARY).toList())
                .containsExactlyInAnyOrderElementsOf(urls.stream().map(PublicFileUrl::filePathType).collect(Collectors.toSet()));
    }
}
