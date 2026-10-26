package de.tum.cit.aet.artemis.course.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeGeneratedWelcomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeQueryDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTeachingTeamDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeVisitAcknowledgmentDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeWelcomeRequestDTO;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeService;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeTeachingTeamService;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeVisitService;

/** Student Home: reads are independent from successful-render acknowledgments and optional wording. */
@FeatureUsage(UserFeature.COURSE_OVERVIEW)
@RestController
@Lazy
@Profile(PROFILE_CORE)
@RequestMapping("api/course/")
public class CourseHomeResource {

    private final UserRepository users;

    private final CourseHomeService home;

    private final CourseHomeTeachingTeamService team;

    private final CourseHomeVisitService visits;

    private final de.tum.cit.aet.artemis.course.service.home.CourseHomeWelcomeService welcomes;

    public CourseHomeResource(UserRepository users, CourseHomeService home, CourseHomeTeachingTeamService team, CourseHomeVisitService visits,
            de.tum.cit.aet.artemis.course.service.home.CourseHomeWelcomeService welcomes) {
        this.users = users;
        this.home = home;
        this.team = team;
        this.visits = visits;
        this.welcomes = welcomes;
    }

    @GetMapping("courses/{courseId}/home")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CourseHomeDTO> get(@PathVariable long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant since) {
        Instant asOf = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return ResponseEntity.ok(home.load(courseId, users.getUserWithAuthorities(), new CourseHomeQueryDTO(since), asOf));
    }

    @GetMapping("courses/{courseId}/home/teaching-team")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CourseHomeTeachingTeamDTO> teachingTeam(@PathVariable long courseId, @RequestParam(defaultValue = "0") int page) {
        home.checkAccess(courseId);
        return ResponseEntity.ok(team.load(courseId, page, 6));
    }

    @PostMapping("courses/{courseId}/home/visits")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Void> acknowledge(@PathVariable long courseId, @Valid @RequestBody CourseHomeVisitAcknowledgmentDTO request) {
        home.checkAccess(courseId);
        var user = users.getUserWithAuthorities();
        visits.acknowledge(courseId, user.getId(), request.snapshotAt(), request.observedVariantCursor(), Instant.now());
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns optional encouragement within the bounded request deadline.
     *
     * @param courseId the authorized course identifier
     * @param request  the fingerprint and requested language
     * @return generated encouragement or the deterministic fallback marker
     */
    @PostMapping("courses/{courseId}/home/welcome")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<CourseHomeGeneratedWelcomeDTO> welcome(@PathVariable long courseId, @Valid @RequestBody CourseHomeWelcomeRequestDTO request) {
        return ResponseEntity.ok(welcomes.generateBounded(courseId, users.getUserWithAuthorities(), request, Instant.now().truncatedTo(ChronoUnit.MILLIS)));
    }
}
