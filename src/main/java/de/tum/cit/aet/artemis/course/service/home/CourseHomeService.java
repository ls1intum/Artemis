package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.course.domain.CourseHomeTargetKind;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeQueryDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeWelcomeDTO;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.repository.CourseHomeRepository;
import de.tum.cit.aet.artemis.course.service.CourseAvailableTabsService;

/** Assembles one authorized, bounded snapshot. Reading it does not acknowledge a visit or invoke AI. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeService {

    private final CourseHomeRepository courses;

    private final CourseConfigurationRepository configurations;

    private final CourseHomeVisitService visits;

    private final CourseHomeLearningService learning;

    private final CourseHomeTeachingTeamService team;

    private final CourseAvailableTabsService tabs;

    private final CourseHomeContextService context;

    private final AuthorizationCheckService authorization;

    private final CourseHomeSectionsService optionalSections;

    private final CourseHomeAiPolicyService aiPolicy;

    public CourseHomeService(CourseHomeRepository courses, CourseConfigurationRepository configurations, CourseHomeVisitService visits, CourseHomeLearningService learning,
            CourseHomeTeachingTeamService team, CourseAvailableTabsService tabs, CourseHomeContextService context, AuthorizationCheckService authorization,
            CourseHomeSectionsService optionalSections, CourseHomeAiPolicyService aiPolicy) {
        this.courses = courses;
        this.configurations = configurations;
        this.visits = visits;
        this.learning = learning;
        this.team = team;
        this.tabs = tabs;
        this.context = context;
        this.authorization = authorization;
        this.aiPolicy = aiPolicy;
        this.optionalSections = optionalSections;
    }

    public void checkAccess(long courseId) {
        header(courseId);
    }

    private CourseHomeRepository.Header header(long courseId) {
        if (!authorization.isAtLeastStudentInCourse(courseId)) {
            throw new AccessForbiddenException("course", courseId);
        }
        return courses.findHeaderById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
    }

    /**
     * Loads the authorized, bounded Home summary without resource graphs or read-state writes.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated user
     * @param query    the requested new-content baseline
     * @param asOf     the single server timestamp for this snapshot
     * @return the authorized contextual Home snapshot
     */
    public CourseHomeDTO load(long courseId, User user, CourseHomeQueryDTO query, Instant asOf) {
        var header = header(courseId);
        Instant snapshot = asOf.truncatedTo(ChronoUnit.MILLIS);
        var visit = visits.read(courseId, user.getId());
        Instant previous = visit.lastSeenAt() == null ? null : visit.lastSeenAt().isAfter(snapshot) ? snapshot : visit.lastSeenAt();
        Instant since = query.since() != null ? query.since() : previous != null ? previous : snapshot.minus(7, ChronoUnit.DAYS);
        if (since.isAfter(snapshot) || since.isBefore(Instant.EPOCH)) {
            throw new BadRequestAlertException("Invalid Home update baseline", "courseHome", "invalidSince");
        }
        var info = new CourseHomeDTO.CourseInfoDTO(header.getId(), header.getTitle(), header.getDescription(),
                header.getLanguage() == null ? null : header.getLanguage().getShortName(), instant(header.getStartDate()), instant(header.getEndDate()),
                configurations.findStudentWelcomeContextByCourseId(courseId).orElse(null));
        var capabilities = tabs.getAvailableTabs(courseId, user, header.getCourseInformationSharingConfiguration(), header.getLearningPathsEnabled());
        var work = learning.load(courseId, user, snapshot, since);
        var staff = team.load(courseId, 0, 3);
        var lectures = optionalSections.lectures(courseId, user, snapshot, since);
        var exams = optionalSections.exams(courseId, user, snapshot, since);
        var discussions = optionalSections.discussions(courseId, user, snapshot, since);
        var chats = optionalSections.chats(courseId, user, snapshot, since, capabilities.iris());
        List<CourseHomeContext.Candidate> candidates = new ArrayList<>();
        work.candidates().forEach(c -> candidates.add(candidate(c.welcomeCase(), c.item(), c.actionable())));
        lectures.items().forEach(i -> candidates.add(candidate(CourseHomeWelcomeCase.NEW_LECTURE, i, "NEW_LECTURE".equals(i.reason()))));
        exams.items().forEach(i -> candidates.add(candidate(CourseHomeWelcomeCase.EXAM_SOON, i, true)));
        if (discussions.announcement() != null) {
            candidates.add(candidate(CourseHomeWelcomeCase.IMPORTANT_ANNOUNCEMENT, discussions.announcement(), true));
        }
        discussions.faqs().forEach(i -> candidates.add(candidate(CourseHomeWelcomeCase.NEW_FAQ, i, true)));
        discussions.questions().items().stream().filter(q -> q.unreadReplies() != null && q.unreadReplies() > 0)
                .forEach(q -> candidates.add(new CourseHomeContext.Candidate(CourseHomeWelcomeCase.UNREAD_REPLY,
                        new CourseHomeTargetDTO(CourseHomeTargetKind.THREAD, q.postId(), q.conversationId(), q.focusReplyId()), q.title(), null, q.latestActivity(), true)));
        if (work.candidates().stream().noneMatch(c -> c.welcomeCase() == CourseHomeWelcomeCase.CAUGHT_UP) && !chats.items().isEmpty()) {
            var chat = chats.items().getFirst();
            candidates.add(new CourseHomeContext.Candidate(CourseHomeWelcomeCase.CAUGHT_UP, new CourseHomeTargetDTO(CourseHomeTargetKind.IRIS_SESSION, chat.id(), null, null),
                    chat.title() == null ? "" : chat.title(), null, chat.lastActivity(), true));
        }
        var selection = context.select(new CourseHomeContext(snapshot, info.startDate(), info.endDate(), previous == null, previous, candidates));
        var meaningful = context.select(new CourseHomeContext(snapshot, info.startDate(), info.endDate(), false, snapshot, candidates));
        var sections = Map.of("lectures", lectures.status(), "exams", exams.status(), "faq",
                discussions.questions().status() == CourseHomeSectionStatus.UNAVAILABLE ? CourseHomeSectionStatus.UNAVAILABLE : CourseHomeSectionStatus.AVAILABLE);
        String fingerprint = fingerprint(info, work.progress(), meaningful, sections, discussions.questions().status(), chats.status());
        var welcome = new CourseHomeWelcomeDTO(selection.welcomeCase(), Math.floorMod(visit.variantCursor(), 3), Map.of("courseTitle", info.title()), fingerprint,
                aiPolicy.canGenerate(user.getId()));
        List<CourseHomeDTO.ItemDTO> updates = new ArrayList<>(work.updates());
        lectures.items().stream().filter(i -> "NEW_LECTURE".equals(i.reason())).forEach(updates::add);
        var boundedUpdates = updates.stream().sorted(Comparator.comparing(CourseHomeDTO.ItemDTO::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(i -> i.target().kind()).thenComparingLong(i -> i.target().id())).limit(6).toList();
        var nextRefreshAt = nextRefresh(snapshot, info, candidates, work.nextRefreshAt(), lectures.nextRefreshAt(), exams.nextRefreshAt());
        return new CourseHomeDTO(snapshot, new CourseHomeDTO.VisitDTO(previous == null, previous, snapshot, visit.variantCursor(), since), info, capabilities, work.progress(),
                welcome, selection.nextStep(), boundedUpdates, discussions.announcement(), discussions.faqs(), staff, discussions.questions(), chats, sections, nextRefreshAt);
    }

    private CourseHomeContext.Candidate candidate(CourseHomeWelcomeCase reason, CourseHomeDTO.ItemDTO item, boolean actionable) {
        return new CourseHomeContext.Candidate(reason, item.target(), item.title(), item.dueAt(), item.updatedAt(), actionable);
    }

    private @Nullable Instant instant(@Nullable ZonedDateTime date) {
        return date == null ? null : date.toInstant();
    }

    private @Nullable Instant nextRefresh(Instant now, CourseHomeDTO.CourseInfoDTO course, List<CourseHomeContext.Candidate> candidates, @Nullable Instant learningRefresh,
            @Nullable Instant lectureRefresh, @Nullable Instant examRefresh) {
        List<Instant> boundaries = new ArrayList<>();
        java.util.stream.Stream.of(learningRefresh, lectureRefresh, examRefresh).filter(java.util.Objects::nonNull).forEach(boundaries::add);
        if (course.startDate() != null) {
            boundaries.add(course.startDate());
        }
        if (course.endDate() != null) {
            boundaries.add(course.endDate());
        }
        for (var c : candidates) {
            if (c.dueAt() != null) {
                boundaries.add(c.dueAt());
                if (c.target().kind() == CourseHomeTargetKind.EXERCISE) {
                    boundaries.add(c.dueAt().minus(24, ChronoUnit.HOURS));
                    boundaries.add(c.dueAt().minus(72, ChronoUnit.HOURS));
                }
            }
        }
        return boundaries.stream().filter(d -> d.isAfter(now)).min(Comparator.naturalOrder()).orElse(null);
    }

    private String fingerprint(CourseHomeDTO.CourseInfoDTO course, CourseHomeDTO.ProgressDTO progress, CourseHomeSelection selection, Map<String, CourseHomeSectionStatus> sections,
            CourseHomeSectionStatus questions, CourseHomeSectionStatus chats) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var next = selection.nextStep();
            var target = next == null ? null : next.target();
            Object[] values = { course.id(), course.title(), course.description(), course.language(), course.startDate(), course.endDate(), course.studentWelcomeContext(),
                    progress.releasedExercises(), progress.submittedExercises(), progress.currentlyDueExercises(), progress.currentlyDueSubmitted(), selection.welcomeCase(),
                    next == null ? null : next.title(), next == null ? null : next.updatedAt(), next == null ? null : next.dueAt(), next == null ? null : next.reason(),
                    target == null ? null : target.kind(), target == null ? null : target.id(), target == null ? null : target.conversationId(),
                    target == null ? null : target.focusReplyId(), sections.get("lectures"), sections.get("exams"), sections.get("faq"), questions, chats };
            for (Object value : values) {
                if (value == null) {
                    digest.update("-1:".getBytes(StandardCharsets.UTF_8));
                }
                else {
                    byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
                    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) ':');
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JVM", ex);
        }
    }
}
