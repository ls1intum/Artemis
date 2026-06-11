package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseRefDTO;

class CourseRefDTOTest {

    @Test
    void fromReturnsNullForNullInput() {
        assertThat(CourseRefDTO.from(null)).isNull();
    }

    @Test
    void fromCopiesIdentityAndDisplayFields() {
        Course course = new Course();
        course.setId(7L);
        course.setTitle("Software Engineering");
        course.setShortName("SE");
        course.setColor("#3273dc");

        CourseRefDTO ref = CourseRefDTO.from(course);

        assertThat(ref).isNotNull();
        assertThat(ref.id()).isEqualTo(7L);
        assertThat(ref.title()).isEqualTo("Software Engineering");
        assertThat(ref.shortName()).isEqualTo("SE");
        assertThat(ref.color()).isEqualTo("#3273dc");
    }

    @Test
    void fromTolerantOfMissingOptionalFields() {
        Course course = new Course();
        course.setId(8L);

        CourseRefDTO ref = CourseRefDTO.from(course);

        assertThat(ref).isNotNull();
        assertThat(ref.id()).isEqualTo(8L);
        assertThat(ref.title()).isNull();
        assertThat(ref.shortName()).isNull();
        assertThat(ref.color()).isNull();
    }
}
