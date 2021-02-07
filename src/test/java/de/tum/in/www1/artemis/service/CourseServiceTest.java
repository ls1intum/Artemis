package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CourseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void getActiveStudents() {
        var course = database.addEmptyCourse();
        var now = ZonedDateTime.now();
        var exercise = ModelFactory.generateTextExercise(now, now, now, course);
        course.addExercises(exercise);
        exercise = exerciseRepo.save(exercise);

        var users = database.addUsers(2, 0, 0);
        var student1 = users.get(0);
        var participation1 = new StudentParticipation();
        participation1.setParticipant(student1);
        participation1.exercise(exercise);
        studentParticipationRepo.save(participation1);
        var student2 = users.get(1);
        var participation2 = new StudentParticipation();
        participation2.setParticipant(student2);
        participation2.exercise(exercise);
        studentParticipationRepo.save(participation2);

        var submission1 = ModelFactory.generateTextSubmission("text of text submission1", Language.ENGLISH, true);
        submission1.setParticipation(participation1);
        submission1.setSubmissionDate(now.minusDays(1));
        var submission2 = ModelFactory.generateTextSubmission("text of text submission2", Language.ENGLISH, true);
        submission2.setParticipation(participation2);
        submission2.setSubmissionDate(now.minusDays(1));
        submissionRepository.save(submission1);
        submissionRepository.save(submission2);

        var activeStudents = courseService.getActiveStudents(course.getId());
        assertThat(activeStudents.length).isEqualTo(4);
        assertThat(activeStudents).isEqualTo(new Integer[] { 0, 0, 0, 2 });
    }
}
