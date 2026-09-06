package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;

/**
 * The round trip that decides whether the decoupling holds.
 * <p>
 * The client is served a URL for each of these fields and sends it back, unchanged, in the next update of the same entity. Several write paths take that value and assign it
 * straight to the entity, so the only thing that keeps a URL out of the column is the setter reducing what it is given to a filename. This asserts that for every field, and it
 * asserts the shape of the value that is handed out again, because a getter that returned the filename would break every client that appends it to {@code api/core/files/}.
 */
class StoredFileReferenceTest {

    @Test
    void anAttachmentOfALectureKeepsOnlyTheFilename() {
        Lecture lecture = new Lecture();
        lecture.setId(4L);
        Attachment attachment = new Attachment();
        attachment.setLecture(lecture);

        assertRoundTrip(attachment, Attachment::setLink, Attachment::getLink, "slides.pdf", "attachments/lectures/4/slides.pdf");
        assertRoundTrip(attachment, Attachment::setLink, Attachment::getLink, "attachments/lecture/4/slides.pdf", "attachments/lectures/4/slides.pdf");
    }

    @Test
    void anAttachmentOfAnAttachmentVideoUnitKeepsOnlyTheFilename() {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(8L);
        Attachment attachment = new Attachment();
        attachment.setAttachmentVideoUnit(unit);

        assertRoundTrip(attachment, Attachment::setLink, Attachment::getLink, "slides.pdf", "attachments/attachment-video-units/8/slides.pdf");
        assertRoundTrip(attachment, Attachment::setStudentVersion, Attachment::getStudentVersion, "slides.pdf", "attachments/attachment-video-units/8/student/slides.pdf");
    }

    /**
     * An attachment may point at a document hosted elsewhere instead of at a file this application stores. That value has to survive the round trip untouched.
     */
    @Test
    void anAttachmentPointingSomewhereElseIsLeftAlone() {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(8L);
        Attachment attachment = new Attachment();
        attachment.setAttachmentVideoUnit(unit);

        attachment.setLink("https://example.org/lecture-notes.pdf");

        assertThat(attachment.getLink()).isEqualTo("https://example.org/lecture-notes.pdf");
    }

    @Test
    void aCourseIconKeepsOnlyTheFilename() {
        Course course = new Course();
        course.setId(3L);

        assertRoundTrip(course, Course::setCourseIcon, Course::getCourseIcon, "icon.png", "courses/3/icons/icon.png");
        assertRoundTrip(course, Course::setCourseIcon, Course::getCourseIcon, "course/icons/3/icon.png", "courses/3/icons/icon.png");
    }

    @Test
    void aProfilePictureKeepsOnlyTheFilename() {
        User user = new User();
        user.setId(7L);

        assertRoundTrip(user, User::setImageUrl, User::getImageUrl, "picture.png", "users/7/profile-pictures/picture.png");
        assertRoundTrip(user, User::setImageUrl, User::getImageUrl, "user/profile-pictures/7/picture.png", "users/7/profile-pictures/picture.png");
    }

    /**
     * The Iris bot's picture is a static asset shipped with the client rather than an upload, so it is not a filename and must not be turned into one.
     */
    @Test
    void aStaticAssetPictureIsLeftAlone() {
        User user = new User();
        user.setId(7L);

        user.setImageUrl("/public/images/iris/iris-logo-small.png");

        assertThat(user.getImageUrl()).isEqualTo("/public/images/iris/iris-logo-small.png");
    }

    @Test
    void theImagesOfAnExamUserKeepOnlyTheFilename() {
        ExamUser examUser = new ExamUser();
        examUser.setId(9L);

        assertRoundTrip(examUser, ExamUser::setSigningImagePath, ExamUser::getSigningImagePath, "signature.png", "exam-users/9/signatures/signature.png");
        assertRoundTrip(examUser, ExamUser::setSigningImagePath, ExamUser::getSigningImagePath, "exam-user/signatures/9/signature.png", "exam-users/9/signatures/signature.png");
        assertRoundTrip(examUser, ExamUser::setStudentImagePath, ExamUser::getStudentImagePath, "photo.png", "exam-users/9/photo.png");
        assertRoundTrip(examUser, ExamUser::setStudentImagePath, ExamUser::getStudentImagePath, "exam-user/9/photo.png", "exam-users/9/photo.png");
    }

    @Test
    void aDragAndDropBackgroundKeepsOnlyTheFilename() {
        DragAndDropQuestion question = new DragAndDropQuestion();
        question.setId(5L);

        assertRoundTrip(question, DragAndDropQuestion::setBackgroundFilePath, DragAndDropQuestion::servedBackgroundFilePath, "background.jpg",
                "drag-and-drop/questions/5/backgrounds/background.jpg");
        assertRoundTrip(question, DragAndDropQuestion::setBackgroundFilePath, DragAndDropQuestion::servedBackgroundFilePath, "drag-and-drop/backgrounds/5/background.jpg",
                "drag-and-drop/questions/5/backgrounds/background.jpg");
        assertThat(question.getBackgroundFilePath()).isEqualTo("background.jpg");
    }

    /**
     * A drag item is the one served file reference the client assembles for itself, from the filename plus the two ids it already has, so the value it receives and sends back is
     * the filename.
     */
    @Test
    void aDragItemPictureKeepsOnlyTheFilename() {
        DragItem dragItem = new DragItem();

        dragItem.setPictureFilePath("drag-and-drop/questions/5/drag-items/2/item.png");
        assertThat(dragItem.getPictureFilePath()).isEqualTo("item.png");

        dragItem.pictureFilePath("drag-and-drop/drag-items/2/item.png");
        assertThat(dragItem.getPictureFilePath()).isEqualTo("item.png");
    }

    /**
     * A slide image is a storage key rather than a served path, so what goes in and what comes out are both the filename.
     */
    @Test
    void aSlideImageKeepsOnlyTheFilename() {
        Slide slide = new Slide();

        slide.setSlideImagePath("attachments/attachment-unit/8/slide/3/slide.png");

        assertThat(slide.getSlideImagePath()).isEqualTo("slide.png");
    }

    /**
     * The one field whose client-facing value does not come from its own getter: the question stores the filename, and the served path is a second, derived Jackson property under
     * the same name. Serializing and deserializing it is the only way to see that the two do not collide and that the round trip still reduces.
     */
    @Test
    void aDragAndDropBackgroundSerializesAsTheServedPathAndDeserializesAsTheFilename() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DragAndDropQuestion question = new DragAndDropQuestion();
        question.setId(5L);
        question.setBackgroundFilePath("background.jpg");

        JsonNode json = mapper.readTree(mapper.writeValueAsString(question));
        assertThat(json.get("backgroundFilePath").asText()).isEqualTo("drag-and-drop/questions/5/backgrounds/background.jpg");

        DragAndDropQuestion sentBack = mapper.readValue(json.toString(), DragAndDropQuestion.class);
        assertThat(sentBack.getBackgroundFilePath()).isEqualTo("background.jpg");
    }

    /**
     * The four columns sized after {@link FileUtil#GENERATED_FILENAME_MAX_LENGTH} hold a name the server generates in full, and the arithmetic behind that width is stated in
     * that constant's documentation rather than executed anywhere. This executes it, so that lengthening a type prefix or allowing a longer file extension fails here instead of
     * failing an insert in production. The bound is checked for every file path type, not only the four, because the prefixes come from one switch and a new longest entry in it
     * is exactly the change that would break the sum.
     */
    @Test
    void everyFilenameTheServerGeneratesInFullFitsTheColumnsSizedForOne() {
        String longestExtension = FileUtil.allowedFileExtensions.stream().max(Comparator.comparingInt(String::length)).orElseThrow();

        for (FilePathType filePathType : FilePathType.values()) {
            String generated = FileUtil.generateFilename(FileUtil.generateTargetFilenameBase(filePathType), "a." + longestExtension, false);
            assertThat(generated).as("generated filename for %s", filePathType).hasSizeLessThanOrEqualTo(FileUtil.GENERATED_FILENAME_MAX_LENGTH);
        }
    }

    /**
     * The one of the four that keeps a filename rather than generating a UUID. The name it keeps is not the one a user gave a file: an exam user photo arrives as raw bytes and
     * the service names the part itself, so the result is still bounded.
     */
    @Test
    void theKeptNameOfAnExamUserPhotoFitsTheSameWidth() {
        String generated = FileUtil.generateFilename(FileUtil.generateTargetFilenameBase(FilePathType.EXAM_USER_IMAGE), "student_image.png", true);

        assertThat(generated).hasSizeLessThanOrEqualTo(FileUtil.GENERATED_FILENAME_MAX_LENGTH);
    }

    /**
     * Writes the given value, asserts that reading it back gives the served path, and then writes that served path again to prove the round trip has reached a fixed point rather
     * than growing a segment on every update.
     *
     * @param entity       the entity holding the field
     * @param setter       the setter a write path or the deserializer calls
     * @param getter       the accessor the client-facing value comes from
     * @param written      the value being stored, either a filename or a value that still carries a whole path
     * @param expectedPath the path the client is served for it
     */
    private static <T> void assertRoundTrip(T entity, BiConsumer<T, String> setter, Function<T, String> getter, String written, String expectedPath) {
        setter.accept(entity, written);
        assertThat(getter.apply(entity)).isEqualTo(expectedPath);

        setter.accept(entity, getter.apply(entity));
        assertThat(getter.apply(entity)).isEqualTo(expectedPath);
    }
}
