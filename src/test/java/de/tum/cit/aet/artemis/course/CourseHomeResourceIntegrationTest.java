package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeQueryDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTeachingTeamDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeVisitAcknowledgmentDTO;
import de.tum.cit.aet.artemis.course.repository.CourseHomeVisitRepository;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeService;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeVisitService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

@WithMockUser(username = CourseHomeResourceIntegrationTest.PREFIX + "student1", roles = "USER")
class CourseHomeResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homeresource";

    @Autowired
    private CourseHomeService home;

    @Autowired
    private CourseHomeVisitService visitService;

    @Autowired
    private CourseHomeVisitRepository visits;

    @Autowired
    private TextExerciseUtilService exercises;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 2, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
    }

    @Test
    void firstVisitIsReadOnlyAndCarriesTheCompleteDeterministicCore() throws Exception {
        var before = visits.count();
        var result = get();
        assertThat(result.course().id()).isEqualTo(course.getId());
        assertThat(result.visit().firstVisit()).isTrue();
        assertThat(result.visit().newSince()).isEqualTo(result.asOf().minusSeconds(7 * 86400));
        assertThat(result.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.FIRST_VISIT);
        assertThat(result.welcome().aiEligible()).isFalse();
        assertThat(result.welcome().contextFingerprint()).hasSize(64);
        assertThat(result.teachingTeam().items()).isNotEmpty();
        assertThat(visits.count()).isEqualTo(before);
        get();
        assertThat(visits.count()).isEqualTo(before);
    }

    @Test
    void successfulRenderAcknowledgmentAdvancesOnceAndPreservesNewBaselineOnRefresh() throws Exception {
        var initial = get();
        var ack = new CourseHomeVisitAcknowledgmentDTO(initial.visit().snapshotAt(), initial.visit().variantCursor());
        request.postWithoutResponseBody(url() + "/visits", ack, HttpStatus.NO_CONTENT);
        request.postWithoutResponseBody(url() + "/visits", ack, HttpStatus.NO_CONTENT);
        var next = get();
        assertThat(next.visit().firstVisit()).isFalse();
        assertThat(next.visit().variantCursor()).isEqualTo(initial.visit().variantCursor() + 1);
        assertThat(next.visit().previousVisitAt()).isEqualTo(initial.visit().snapshotAt());
        var refreshed = request.get(url() + "?since=" + initial.visit().newSince(), HttpStatus.OK, CourseHomeDTO.class);
        assertThat(refreshed.visit().newSince()).isEqualTo(initial.visit().newSince());
    }

    @Test
    void deadlinesProvideARefreshBoundaryEvenWhileThePresentationChanges() {
        var base = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var exercise = exercises.createIndividualTextExercise(course, base.minusDays(1), base.plusHours(25), base.plusDays(4));
        var student = userUtilService.getUserByLogin(PREFIX + "student1");
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        visitService.acknowledge(course.getId(), student.getId(), now.minusSeconds(1), 0, now);
        var result = home.load(course.getId(), student, new CourseHomeQueryDTO(null), now);
        assertThat(result.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.DEADLINE_SOON);
        assertThat(result.nextRefreshAt()).isEqualTo(exercise.getDueDate().minusHours(24).toInstant());
        var boundary = home.load(course.getId(), student, new CourseHomeQueryDTO(result.visit().newSince()), exercise.getDueDate().minusHours(24).toInstant());
        assertThat(boundary.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.DEADLINE_TODAY);
    }

    @Test
    void futureReadFiltersAreRejected() throws Exception {
        request.get(url() + "?since=" + Instant.now().plusSeconds(3600), HttpStatus.BAD_REQUEST, CourseHomeDTO.class);
    }

    @Test
    void anotherUsersVisitCannotBeSelectedThroughInput() throws Exception {
        var other = userUtilService.getUserByLogin(PREFIX + "student2");
        var now = Instant.now();
        visitService.acknowledge(course.getId(), other.getId(), now, 0, now);
        var result = request.get(url() + "?userId=" + other.getId(), HttpStatus.OK, CourseHomeDTO.class);
        assertThat(result.visit().firstVisit()).isTrue();
    }

    @Test
    void teamPaginationUsesTheSamePublicProjection() throws Exception {
        var team = request.get(url() + "/teaching-team?page=0", HttpStatus.OK, CourseHomeTeachingTeamDTO.class);
        assertThat(team.items()).hasSize(2);
        request.get(url() + "/teaching-team?page=-1", HttpStatus.BAD_REQUEST, CourseHomeTeachingTeamDTO.class);
    }

    @Test
    @WithAnonymousUser
    void allRoutesRequireAuthentication() throws Exception {
        request.get(url(), HttpStatus.UNAUTHORIZED, CourseHomeDTO.class);
        request.get(url() + "/teaching-team?page=0", HttpStatus.UNAUTHORIZED, CourseHomeTeachingTeamDTO.class);
        request.postWithoutResponseBody(url() + "/visits", new CourseHomeVisitAcknowledgmentDTO(Instant.now(), 0), HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = PREFIX + "outsider", roles = "USER")
    void allRoutesRejectNonmembers() throws Exception {
        userUtilService.createAndSaveUser(PREFIX + "outsider");
        request.get(url(), HttpStatus.FORBIDDEN, CourseHomeDTO.class);
        request.get(url() + "/teaching-team?page=0", HttpStatus.FORBIDDEN, CourseHomeTeachingTeamDTO.class);
        request.postWithoutResponseBody(url() + "/visits", new CourseHomeVisitAcknowledgmentDTO(Instant.now(), 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = PREFIX + "instructor1", roles = "USER")
    void staffHomeStillUsesReleasedStudentView() throws Exception {
        var base = ZonedDateTime.now();
        exercises.createIndividualTextExercise(course, base.plusDays(1), base.plusDays(2), base.plusDays(3));
        assertThat(get().progress().releasedExercises()).isZero();
        request.get(url() + "/teaching-team?page=0", HttpStatus.OK, CourseHomeTeachingTeamDTO.class);
        var initial = get();
        request.postWithoutResponseBody(url() + "/visits", new CourseHomeVisitAcknowledgmentDTO(initial.visit().snapshotAt(), initial.visit().variantCursor()),
                HttpStatus.NO_CONTENT);
        assertThat(get().visit().firstVisit()).isFalse();
    }

    private CourseHomeDTO get() throws Exception {
        return request.get(url(), HttpStatus.OK, CourseHomeDTO.class);
    }

    private String url() {
        return "/api/course/courses/" + course.getId() + "/home";
    }
}
