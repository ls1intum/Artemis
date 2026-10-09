package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class PyrisJobAccessTest {

    @Test
    void autonomousTutorJob_deniesAccessWhenTheExamCourseIsMasked() {
        var exercise = new TextExercise();
        exercise.setExerciseGroup(new ExerciseGroup());
        var job = new AutonomousTutorJob("job", 1L, 42L);

        assertThat(job.canAccess(exercise)).isFalse();
    }

    @Test
    void autonomousTutorJob_checksTheResolvedCourse() {
        var exercise = new TextExercise();
        var course = new Course();
        course.setId(42L);
        exercise.setCourse(course);
        var job = new AutonomousTutorJob("job", 1L, 42L);

        assertThat(job.canAccess(exercise)).isTrue();
        course.setId(43L);
        assertThat(job.canAccess(exercise)).isFalse();
    }

    @Test
    void chatJob_keepsItsExerciseSpecificAccessRule() {
        var exercise = new TextExercise();
        exercise.setId(7L);
        var job = new ChatJob("job", 42L, 1L, 7L, null, null, null);

        assertThat(job.canAccess(exercise)).isTrue();
        exercise.setId(8L);
        assertThat(job.canAccess(exercise)).isFalse();
    }
}
