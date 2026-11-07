package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.CourseHomeTargetKind;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeLearningDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseHomeRepository;

/** Loads constant-size scalar candidates, applying visibility in the database before paging. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeLearningService {

    private final ExerciseHomeRepository exercises;

    private final AuthorizationCheckService authorization;

    public CourseHomeLearningService(ExerciseHomeRepository exercises, AuthorizationCheckService authorization) {
        this.exercises = exercises;
        this.authorization = authorization;
    }

    /**
     * Loads the authorized, bounded Home summary without resource graphs or read-state writes.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated user
     * @param asOf     the single server timestamp for this snapshot
     * @param since    the preserved new-content baseline
     * @return submission counts and bounded, currently eligible exercise candidates
     */
    public CourseHomeLearningDTO load(long courseId, User user, Instant asOf, Instant since) {
        if (!authorization.isAtLeastStudentInCourse(courseId)) {
            throw new AccessForbiddenException("course", courseId);
        }
        var now = ZonedDateTime.ofInstant(asOf, ZoneOffset.UTC);
        var baseline = ZonedDateTime.ofInstant(since, ZoneOffset.UTC);
        var counts = exercises.countForHome(courseId, user.getId(), now, now.plusHours(72));
        var one = PageRequest.of(0, 1);
        List<CourseHomeLearningDTO.CandidateDTO> candidates = new ArrayList<>();
        add(candidates, exercises.findDeadline(courseId, user.getId(), now, now.plusHours(72), one), CourseHomeWelcomeCase.DEADLINE_SOON);
        add(candidates, exercises.findInProgress(courseId, user.getId(), now, one), CourseHomeWelcomeCase.IN_PROGRESS);
        add(candidates, exercises.findFeedback(courseId, user.getId(), now, baseline, one), CourseHomeWelcomeCase.NEW_FEEDBACK);
        add(candidates, exercises.findNewAction(courseId, user.getId(), now, baseline, one), CourseHomeWelcomeCase.NEW_EXERCISE);
        add(candidates, exercises.findAvailableAction(courseId, user.getId(), now, one), CourseHomeWelcomeCase.CAUGHT_UP);
        var updates = exercises.findUpdates(courseId, user.getId(), now, baseline, PageRequest.of(0, 3)).stream().map(row -> item(row, CourseHomeWelcomeCase.NEW_EXERCISE))
                .toList();
        var changes = exercises.findChanges(courseId, user.getId(), now, now.plusHours(72));
        var nextRefresh = Stream
                .of(changes.getReleaseDate(), changes.getStartDate(), changes.getDueDate(),
                        changes.getDueOutsideWindow() == null ? null : changes.getDueOutsideWindow().minusHours(72), changes.getFeedbackDate(), changes.getQuizFeedbackDate(),
                        exercises.findNextQuizBatchStart(courseId, user.getId(), now), exercises.findNextQuizBatchEnd(courseId, user.getId(), now))
                .filter(Objects::nonNull).map(ZonedDateTime::toInstant).filter(date -> date.isAfter(asOf)).min(Instant::compareTo).orElse(null);
        return new CourseHomeLearningDTO(new CourseHomeDTO.ProgressDTO(counts.getReleased(), counts.getSubmitted(), counts.getDue(), counts.getDueSubmitted()), candidates, updates,
                nextRefresh);
    }

    private void add(List<CourseHomeLearningDTO.CandidateDTO> candidates, List<ExerciseHomeRepository.Item> rows, CourseHomeWelcomeCase reason) {
        rows.forEach(row -> candidates.add(new CourseHomeLearningDTO.CandidateDTO(reason, item(row, reason), true)));
    }

    private CourseHomeDTO.ItemDTO item(ExerciseHomeRepository.Item row, CourseHomeWelcomeCase reason) {
        return new CourseHomeDTO.ItemDTO(row.getTitle(), new CourseHomeTargetDTO(CourseHomeTargetKind.EXERCISE, row.getId(), null, null),
                row.getActivityAt() == null ? null : row.getActivityAt().toInstant(), row.getDueDate() == null ? null : row.getDueDate().toInstant(), reason.name());
    }
}
