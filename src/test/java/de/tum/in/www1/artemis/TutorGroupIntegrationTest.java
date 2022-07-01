package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TutorGroup;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.Weekday;

class TutorGroupIntegrationTest {

    @Test
    void testTutorGroup() {
        var tutor = new User();
        var course = new Course();

        TutorGroup tutorGroup = new TutorGroup();
        tutorGroup.setName("test");
        tutorGroup.setCapacity(10);
        tutorGroup.setWeekday(Weekday.FRIDAY);
        tutorGroup.setTimeSlot("Fr 10-12");
        tutorGroup.setLanguage(Language.ENGLISH);
        tutorGroup.setRoom("Zoom");
        tutorGroup.setTutor(tutor);
        tutorGroup.setStudents(Set.of(tutor));
        tutorGroup.setCourse(course);

        assertThat(tutorGroup.getName()).isEqualTo("test");
        assertThat(tutorGroup.getCapacity()).isEqualTo(10);
        assertThat(tutorGroup.getWeekday()).isEqualTo(Weekday.FRIDAY);
        assertThat(tutorGroup.getTimeSlot()).isEqualTo("Fr 10-12");
        assertThat(tutorGroup.getLanguage()).isEqualTo(Language.ENGLISH);
        assertThat(tutorGroup.getRoom()).isEqualTo("Zoom");
        assertThat(tutorGroup.getTutor()).isEqualTo(tutor);
        assertThat(tutorGroup.getStudents()).isEqualTo(Set.of(tutor));
        assertThat(tutorGroup.getCourse()).isEqualTo(course);
    }

}
