package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

public class CourseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseService courseService;

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
        SecurityUtils.setAuthorizationObject();
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

        var submission1 = new TextSubmission();
        submission1.text("text of text submission1");
        submission1.setLanguage(Language.ENGLISH);
        submission1.setSubmitted(true);
        submission1.setSubmissionDate(ZonedDateTime.now());
        submission1.setParticipation(participation1);
        submission1.setSubmissionDate(now);

        var submission2 = new TextSubmission();
        submission2.text("text of text submission2");
        submission2.setLanguage(Language.ENGLISH);
        submission2.setSubmitted(true);
        submission2.setSubmissionDate(ZonedDateTime.now());
        submission2.setParticipation(participation2);
        submission2.setSubmissionDate(now);
        submissionRepository.save(submission1);
        submissionRepository.save(submission2);

        var activeStudents = courseService.getActiveStudents(course.getId());
        assertThat(activeStudents.length).isEqualTo(4);
        assertThat(activeStudents).isEqualTo(new Integer[] { 0, 0, 0, 2 });
    }
}
