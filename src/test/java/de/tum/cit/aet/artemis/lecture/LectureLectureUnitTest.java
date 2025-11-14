package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

/**
 * Unit tests for the interplay between Lecture and its LectureUnits
 * (AttachmentVideoUnit, ExerciseUnit, TextUnit, OnlineUnit),
 * focusing on lectureUnitOrder, back-references and defensive handling
 * of invalid input, without involving Hibernate / DB.
 */
class LectureLectureUnitTest {

    // ----------------------------------------------------------------
    // Helper methods
    // ----------------------------------------------------------------

    private AttachmentVideoUnit attachmentUnit(long id) {
        AttachmentVideoUnit u = new AttachmentVideoUnit();
        u.setId(id);
        u.setName("Attachment " + id);
        return u;
    }

    private ExerciseUnit exerciseUnit(long id) {
        ExerciseUnit u = new ExerciseUnit();
        u.setId(id);
        u.setName("Exercise " + id);
        return u;
    }

    private TextUnit textUnit(long id) {
        TextUnit u = new TextUnit();
        u.setId(id);
        u.setName("Text " + id);
        return u;
    }

    private OnlineUnit onlineUnit(long id) {
        OnlineUnit u = new OnlineUnit();
        u.setId(id);
        u.setName("Online " + id);
        return u;
    }

    /**
     * Access the private field lectureUnitOrder via reflection for assertions.
     */
    private int getLectureUnitOrder(LectureUnit unit) {
        try {
            Field field = LectureUnit.class.getDeclaredField("lectureUnitOrder");
            field.setAccessible(true);
            return (int) field.get(unit);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to read lectureUnitOrder via reflection", e);
        }
    }

    /**
     * Asserts that:
     * - Lecture's lectureUnits contain exactly the expected units in the given order.
     * - Each unit has lectureUnitOrder == its index in the list.
     */
    private void assertLectureUnitsInOrder(Lecture lecture, LectureUnit... expected) {
        List<LectureUnit> units = lecture.getLectureUnits();
        assertThat(units).containsExactly(expected);

        for (int i = 0; i < expected.length; i++) {
            LectureUnit u = expected[i];

            assertThat(units.get(i)).as("unit at index %s", i).isSameAs(u);

            assertThat(getLectureUnitOrder(u)).as("lectureUnitOrder of unit %s at index %s", u.getId(), i).isEqualTo(i);
        }
    }

    private void assertLectureUnitsHaveSequentialOrders(Lecture lecture) {
        List<LectureUnit> units = lecture.getLectureUnits();
        for (int i = 0; i < units.size(); i++) {
            assertThat(getLectureUnitOrder(units.get(i))).as("lectureUnitOrder at index %s", i).isEqualTo(i);
        }
    }

    // ----------------------------------------------------------------
    // Tests
    // ----------------------------------------------------------------

    @Test
    void addLectureUnit_shouldSetLectureAndSequentialOrders_mixedSubtypes() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        ExerciseUnit exercise = exerciseUnit(2L);
        OnlineUnit online = onlineUnit(3L);
        AttachmentVideoUnit attachment = attachmentUnit(4L);

        lecture.addLectureUnit(text);
        lecture.addLectureUnit(exercise);
        lecture.addLectureUnit(online);
        lecture.addLectureUnit(attachment);

        // Back-references
        assertThat(text.getLecture()).isSameAs(lecture);
        assertThat(exercise.getLecture()).isSameAs(lecture);
        assertThat(online.getLecture()).isSameAs(lecture);
        assertThat(attachment.getLecture()).isSameAs(lecture);

        assertLectureUnitsInOrder(lecture, text, exercise, online, attachment);
    }

    @Test
    void addLectureUnit_nullShouldBeIgnored() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);

        lecture.addLectureUnit(null);
        lecture.addLectureUnit(text);

        assertLectureUnitsInOrder(lecture, text);
    }

    @Test
    void removeLectureUnit_shouldRemoveAndReindexOrders_mixedSubtypes() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        ExerciseUnit exercise = exerciseUnit(2L);
        OnlineUnit online = onlineUnit(3L);

        lecture.addLectureUnit(text);
        lecture.addLectureUnit(exercise);
        lecture.addLectureUnit(online);

        // Remove middle unit
        lecture.removeLectureUnit(exercise);

        // removed unit must lose back-reference
        assertThat(exercise.getLecture()).isNull();

        // Remaining units should be [text, online] with orders [0, 1]
        assertLectureUnitsInOrder(lecture, text, online);
    }

    @Test
    void removeLectureUnit_nullShouldBeIgnored() {
        Lecture lecture = new Lecture();
        ExerciseUnit exercise = exerciseUnit(2L);

        lecture.addLectureUnit(exercise);
        lecture.removeLectureUnit(null);

        assertLectureUnitsInOrder(lecture, exercise);
    }

    @Test
    void removeLectureUnitById_shouldRemoveCorrectUnit() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        ExerciseUnit exercise = exerciseUnit(2L);
        OnlineUnit online = onlineUnit(3L);

        lecture.addLectureUnit(text);
        lecture.addLectureUnit(exercise);
        lecture.addLectureUnit(online);

        lecture.removeLectureUnitById(2L);

        assertLectureUnitsInOrder(lecture, text, online);
    }

    @Test
    void removeLectureUnitById_nullIdShouldBeIgnored() {
        Lecture lecture = new Lecture();
        OnlineUnit online = onlineUnit(3L);

        lecture.addLectureUnit(online);
        lecture.removeLectureUnitById(null);

        assertLectureUnitsInOrder(lecture, online);
    }

    @Test
    void setLectureUnits_shouldReplaceCollectionAndSetBackReferencesAndOrders() {
        Lecture lecture = new Lecture();

        TextUnit oldText = textUnit(1L);
        ExerciseUnit oldExercise = exerciseUnit(2L);
        lecture.addLectureUnit(oldText);
        lecture.addLectureUnit(oldExercise);

        OnlineUnit newOnline = onlineUnit(10L);
        AttachmentVideoUnit newAttachment = attachmentUnit(11L);

        lecture.setLectureUnits(Arrays.asList(newOnline, newAttachment));

        // Only new units should remain
        assertThat(lecture.getLectureUnits()).containsExactly(newOnline, newAttachment);

        // New units have back-reference set
        assertThat(newOnline.getLecture()).isSameAs(lecture);
        assertThat(newAttachment.getLecture()).isSameAs(lecture);

        // Orders re-assigned starting at 0
        assertLectureUnitsInOrder(lecture, newOnline, newAttachment);
    }

    @Test
    void setLectureUnits_nullListShouldClearAndKeepValidOrder() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        lecture.addLectureUnit(text);

        lecture.setLectureUnits(null);

        assertThat(lecture.getLectureUnits()).isEmpty();
    }

    @Test
    void reorderLectureUnits_shouldFollowGivenOrderOfIds() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        ExerciseUnit exercise = exerciseUnit(2L);
        OnlineUnit online = onlineUnit(3L);

        lecture.addLectureUnit(text);     // id 1
        lecture.addLectureUnit(exercise); // id 2
        lecture.addLectureUnit(online);   // id 3

        // New order: [online, text, exercise]
        lecture.reorderLectureUnits(List.of(3L, 1L, 2L));

        assertLectureUnitsInOrder(lecture, online, text, exercise);
    }

    @Test
    void reorderLectureUnits_withMissingIdsShouldStillKeepAllUnitsAndSequentialOrders() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        ExerciseUnit exercise = exerciseUnit(2L);
        OnlineUnit online = onlineUnit(3L);

        lecture.addLectureUnit(text);
        lecture.addLectureUnit(exercise);
        lecture.addLectureUnit(online);

        // Only specify some ids; current implementation will push
        // "missing" ones (indexOf == -1) first.
        lecture.reorderLectureUnits(List.of(2L)); // only exercise explicitly mentioned

        // All units must still be present in some order
        assertThat(lecture.getLectureUnits()).containsExactlyInAnyOrder(text, exercise, online);

        // In any case, lectureUnitOrder must be sequential 0..2
        assertLectureUnitsHaveSequentialOrders(lecture);
    }

    @Test
    void manualSetLectureUnitsAndExplicitUpdateLectureUnitOrder_shouldStayConsistent() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        ExerciseUnit exercise = exerciseUnit(2L);
        OnlineUnit online = onlineUnit(3L);

        lecture.addLectureUnit(text);
        lecture.addLectureUnit(exercise);
        lecture.addLectureUnit(online);

        // Simulate API that directly sets the list (already supported by Lecture)
        lecture.setLectureUnits(Arrays.asList(online, text, exercise));

        // updateLectureUnitOrder is called in setLectureUnits, but we call again
        // to ensure idempotency
        lecture.updateLectureUnitOrder();

        assertLectureUnitsInOrder(lecture, online, text, exercise);
    }

    @Test
    void getLectureUnits_shouldBeUnmodifiable() {
        Lecture lecture = new Lecture();
        TextUnit text = textUnit(1L);
        lecture.addLectureUnit(text);

        List<LectureUnit> view = lecture.getLectureUnits();

        assertThat(view).containsExactly(text);

        // mutation of the returned list should fail
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> view.add(exerciseUnit(2L))).isInstanceOf(UnsupportedOperationException.class);
    }
}
