package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeLearningService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

@Isolated("Measures SessionFactory-wide entity-load statistics without concurrent test queries.")
@WithMockUser(username = CourseHomeLearningIntegrationTest.PREFIX + "student1", roles = "USER")
class CourseHomeLearningIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homelearning";

    @Autowired
    private CourseHomeLearningService learning;

    @Autowired
    private TextExerciseUtilService textExercises;

    @Autowired
    private StudentParticipationTestRepository participations;

    @Autowired
    private SubmissionTestRepository submissions;

    @Autowired
    private TeamUtilService teams;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository groups;

    @Autowired
    private de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService variantGroups;

    @Autowired
    private de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository quizzes;

    @Autowired
    private de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService configurations;

    @Autowired
    private de.tum.cit.aet.artemis.exam.util.ExamUtilService examFixtures;

    private Course course;

    private User student;

    private ZonedDateTime base;

    private boolean statisticsEnabledBefore;

    @BeforeEach
    void setup() {
        statisticsEnabledBefore = entityManagerFactory.unwrap(SessionFactory.class).getStatistics().isStatisticsEnabled();
        userUtilService.addUsers(PREFIX, 2, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        student = userUtilService.getUserByLogin(PREFIX + "student1");
        base = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    @AfterEach
    void restoreStatistics() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        statistics.setStatisticsEnabled(statisticsEnabledBefore);
    }

    @Test
    void futureReleaseAndStartBecomeEligibleAtTheReturnedBoundary() {
        var release = base.plusMinutes(1);
        var exercise = exercise(release, base.plusDays(3));
        exercise.setStartDate(release.plusMinutes(1));
        exerciseRepository.saveAndFlush(exercise);
        var initial = learning.load(course.getId(), student, base.toInstant(), base.minusDays(7).toInstant());
        assertThat(initial.progress().releasedExercises()).isZero();
        assertThat(initial.candidates()).isEmpty();
        assertThat(initial.nextRefreshAt()).isEqualTo(release.toInstant());
        var published = learning.load(course.getId(), student, initial.nextRefreshAt(), base.minusDays(7).toInstant());
        assertThat(published.progress().releasedExercises()).isOne();
        assertThat(published.candidates()).isEmpty();
        assertThat(published.nextRefreshAt()).isEqualTo(exercise.getStartDate().toInstant());
        var opened = learning.load(course.getId(), student, published.nextRefreshAt(), base.minusDays(7).toInstant());
        assertThat(opened.candidates()).anyMatch(candidate -> candidate.item().target().id() == exercise.getId());
    }

    @Test
    void countsOnlyReleasedEligibleWorkAndOwnedSubmissions() {
        var submitted = exercise(base.minusHours(1), base.plusDays(2));
        submit(submitted, student, true);
        var open = exercise(base.minusHours(1), base.plusDays(2));
        submit(open, userUtilService.getUserByLogin(PREFIX + "student2"), true);
        exercise(base.plusDays(1), base.plusDays(2));
        var snapshot = learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(snapshot.progress().releasedExercises()).isEqualTo(2);
        assertThat(snapshot.progress().submittedExercises()).isEqualTo(1);
        assertThat(snapshot.candidates()).filteredOn(c -> c.welcomeCase() == CourseHomeWelcomeCase.DEADLINE_SOON).extracting(c -> c.item().target().id())
                .containsExactly(open.getId());
    }

    @Test
    void teamSubmissionBelongsToItsMembersAndUnassignedTeamsDoNotCount() {
        var assigned = textExercises.createTeamTextExercise(course, base.minusDays(1), base.plusDays(2), base.plusDays(4));
        var unassigned = textExercises.createTeamTextExercise(course, base.minusDays(1), base.plusDays(2), base.plusDays(4));
        var team = teams.createTeam(Set.of(student), userUtilService.getUserByLogin(PREFIX + "tutor1"), assigned, PREFIX + "team");
        submit(assigned, team, true);
        var result = learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.progress().releasedExercises()).isEqualTo(1);
        assertThat(result.progress().submittedExercises()).isEqualTo(1);
        assertThat(result.candidates()).noneMatch(c -> c.item().target().id() == unassigned.getId());
    }

    @Test
    void personalExtensionKeepsWorkActionableAfterTheOriginalDeadline() {
        var extended = exercise(base.minusDays(2), base.minusDays(1));
        var participation = participation(extended, student);
        participation.setIndividualDueDate(base.plusDays(1));
        participations.saveAndFlush(participation);
        var result = learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.candidates()).anyMatch(c -> c.item().target().id() == extended.getId() && c.item().dueAt().equals(base.plusDays(1).toInstant()));
    }

    @Test
    void immediatelyPublishedUndatedExerciseIsNew() {
        var undated = exercise(null, base.plusDays(5));
        var result = learning.load(course.getId(), student, Instant.now(), base.minusHours(1).toInstant());
        assertThat(result.updates()).extracting(i -> i.target().id()).contains(undated.getId());
    }

    @Test
    void summariesStayBoundedAndQueriesDoNotGrowWithTheCourse() {
        for (int i = 0; i < 10; i++)
            exercise(base.minusDays(1), base.plusDays(5));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        queryInterceptor.startQueryCount();
        learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        long smallQueries = queryInterceptor.getQueryCount();
        for (int i = 0; i < 990; i++)
            exercise(base.minusDays(1), base.plusDays(5));
        statistics.clear();
        queryInterceptor.startQueryCount();
        var large = learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(large.progress().releasedExercises()).isEqualTo(1000);
        assertThat(large.updates()).hasSizeLessThanOrEqualTo(3);
        assertThat(large.candidates()).hasSizeLessThanOrEqualTo(6);
        assertThat(queryInterceptor.getQueryCount()).isPositive().isLessThanOrEqualTo(smallQueries + 2);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }

    @Test
    void interchangeableVariantsCountOnceAndOneSubmissionCompletesTheChoice() {
        var a = exercise(base.minusDays(1), base.plusDays(2));
        var b = exercise(base.minusDays(1), base.plusDays(2));
        var group = new de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup();
        group.setCourse(course);
        group.setTitle("Choose one language");
        group.setReleaseDate(base.minusDays(1));
        group.setDueDate(base.plusDays(2));
        group = groups.saveAndFlush(group);
        variantGroups.assignToGroup(a, group);
        variantGroups.assignToGroup(b, group);
        submit(a, student, true);
        var result = learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.progress().releasedExercises()).isEqualTo(1);
        assertThat(result.progress().submittedExercises()).isEqualTo(1);
        assertThat(result.candidates()).noneMatch(c -> c.item().target().id() == b.getId());
    }

    @Test
    void includesVisibleCourseQuizAndExcludesExamExercises() {
        var quiz = de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory.createQuiz(course, base.minusDays(1), base.plusDays(2),
                de.tum.cit.aet.artemis.quiz.domain.QuizMode.INDIVIDUAL);
        quiz = quizzes.saveAndFlush(quiz);
        configurations.initialize(quiz);
        var exam = examFixtures.addExamWithExerciseGroup(course, true);
        var examExercise = textExercises.createTextExerciseForExam(exam.getExerciseGroups().getFirst());
        var result = learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.progress().releasedExercises()).isEqualTo(1);
        assertThat(result.updates()).noneMatch(i -> i.target().id() == examExercise.getId());
    }

    @Test
    void feedbackIsOnlyNewAfterAssessmentIsReleased() {
        var exercise = exercise(base.minusDays(2), base.minusDays(1));
        var submission = submit(exercise, student, true);
        var feedback = new de.tum.cit.aet.artemis.assessment.domain.Result();
        feedback.setExerciseId(exercise.getId());
        feedback.setSubmission(submission);
        feedback.setAssessor(userUtilService.getUserByLogin(PREFIX + "tutor1"));
        feedback.setAssessmentType(de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL);
        feedback.setRated(true);
        feedback.setScore(0.0);
        feedback.setCompletionDate(base.minusMinutes(10));
        resultRepository.saveAndFlush(feedback);
        var hidden = learning.load(course.getId(), student, Instant.now(), base.minusHours(1).toInstant());
        assertThat(hidden.candidates()).noneMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.NEW_FEEDBACK);
        assertThat(hidden.nextRefreshAt()).isEqualTo(exercise.getAssessmentDueDate().toInstant());
        var scheduled = learning.load(course.getId(), student, hidden.nextRefreshAt(), base.minusHours(1).toInstant());
        assertThat(scheduled.candidates()).anyMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.NEW_FEEDBACK);
        exercise.setAssessmentDueDate(base.minusMinutes(1));
        exerciseRepository.saveAndFlush(exercise);
        var released = learning.load(course.getId(), student, Instant.now(), base.minusHours(1).toInstant());
        assertThat(released.candidates()).anyMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.NEW_FEEDBACK);
        assertThat(released.progress().submittedExercises()).isEqualTo(1);
    }

    @Test
    void anEndedCourseWithUnfinishedWorkDoesNotBecomeComplete() {
        for (int i = 0; i < 8; i++) {
            var exercise = exercise(base.minusDays(2), base.minusDays(1));
            if (i < 6)
                submit(exercise, student, true);
        }
        course.setEndDate(base.minusHours(1));
        courseRepository.saveAndFlush(course);
        var result = learning.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.progress().releasedExercises()).isEqualTo(8);
        assertThat(result.progress().submittedExercises()).isEqualTo(6);
        assertThat(result.progress().currentlyDueExercises()).isZero();
    }

    private TextExercise exercise(ZonedDateTime release, ZonedDateTime due) {
        return textExercises.createIndividualTextExercise(course, release, due, base.plusDays(7));
    }

    private StudentParticipation participation(Exercise exercise, Participant participant) {
        var p = new StudentParticipation();
        p.setExercise(exercise);
        p.setParticipant(participant);
        p.setInitializationState(InitializationState.INITIALIZED);
        p.setInitializationDate(base.minusHours(1));
        return participations.saveAndFlush(p);
    }

    private TextSubmission submit(Exercise exercise, Participant participant, boolean submitted) {
        var p = participation(exercise, participant);
        var submission = new TextSubmission();
        submission.setText("My reasoning");
        submission.setParticipation(p);
        submission.setSubmitted(submitted);
        submission.setSubmissionDate(base.minusMinutes(30));
        return submissions.saveAndFlush(submission);
    }
}
