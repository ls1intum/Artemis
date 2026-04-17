package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.MaliciousParticipationAnalysisRunResultNotification;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchRequestDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchTriggerResponseDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class DeimosBatchService {

    private static final Logger log = LoggerFactory.getLogger(DeimosBatchService.class);

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final DeimosAnalysisService deimosAnalysisService;

    private final CourseNotificationService courseNotificationService;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TaskExecutor taskExecutor;

    private static final long MAX_PARTICIPATIONS_PER_RUN = 5000;

    private static final long MAX_MANUAL_WINDOW_DAYS = 31;

    private static final int PAGE_SIZE = 200;

    public DeimosBatchService(ProgrammingSubmissionRepository programmingSubmissionRepository, DeimosAnalysisService deimosAnalysisService,
            CourseNotificationService courseNotificationService, CourseRepository courseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.deimosAnalysisService = deimosAnalysisService;
        this.courseNotificationService = courseNotificationService;
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.taskExecutor = taskExecutor;
    }

    public DeimosBatchTriggerResponseDTO triggerCourseBatch(long courseId, DeimosBatchRequestDTO request, User triggerUser) {
        validateManualRequest(request);
        String runId = UUID.randomUUID().toString();
        taskExecutor.execute(() -> runManualBatch(runId, DeimosBatchScope.COURSE, courseId, request.from(), request.to(), triggerUser));
        return new DeimosBatchTriggerResponseDTO(runId, "ACCEPTED");
    }

    public DeimosBatchTriggerResponseDTO triggerExerciseBatch(long exerciseId, DeimosBatchRequestDTO request, User triggerUser) {
        validateManualRequest(request);
        String runId = UUID.randomUUID().toString();
        taskExecutor.execute(() -> runManualBatch(runId, DeimosBatchScope.EXERCISE, exerciseId, request.from(), request.to(), triggerUser));
        return new DeimosBatchTriggerResponseDTO(runId, "ACCEPTED");
    }

    private void runManualBatch(String runId, DeimosBatchScope scope, long scopeId, ZonedDateTime from, ZonedDateTime to, User triggerUser) {
        List<Long> participationIds = switch (scope) {
            case COURSE -> collectParticipationIds(pageable -> programmingSubmissionRepository.findParticipationIdsForCourseInRange(scopeId, from, to, pageable));
            case EXERCISE -> collectParticipationIds(pageable -> programmingSubmissionRepository.findParticipationIdsForExerciseInRange(scopeId, from, to, pageable));
        };

        DeimosBatchSummaryDTO summary = deimosAnalysisService.analyze(runId, DeimosTriggerType.MANUAL, scope, from, to, participationIds);
        sendManualRunFinishedNotification(scope, scopeId, summary, triggerUser);
        log.info("Deimos manual batch {} finished with {} analyzed participations", runId, summary.analyzed());
    }

    private void sendManualRunFinishedNotification(DeimosBatchScope scope, long scopeId, DeimosBatchSummaryDTO summary, User triggerUser) {
        long courseId;
        String courseTitle;
        String courseIconUrl;
        String scopeTitle;

        if (scope == DeimosBatchScope.COURSE) {
            courseId = scopeId;
            courseTitle = courseRepository.getCourseTitle(scopeId);
            if (courseTitle == null) {
                log.warn("Skipping Deimos completion notification: could not resolve course title for course {}", scopeId);
                return;
            }
            courseIconUrl = courseRepository.getCourseIconById(scopeId);
            scopeTitle = courseTitle;
        }
        else if (scope == DeimosBatchScope.EXERCISE) {
            var exerciseScopeInfo = programmingExerciseRepository.findDeimosExerciseScopeInfoById(scopeId).orElse(null);
            if (exerciseScopeInfo == null) {
                log.warn("Skipping Deimos completion notification: could not resolve exercise {}", scopeId);
                return;
            }

            courseId = exerciseScopeInfo.courseId();
            courseTitle = exerciseScopeInfo.courseTitle();
            courseIconUrl = exerciseScopeInfo.courseIconUrl();
            scopeTitle = exerciseScopeInfo.exerciseTitle();
        }
        else {
            return;
        }

        var notification = new MaliciousParticipationAnalysisRunResultNotification(courseId, courseTitle, courseIconUrl, scopeTitle, summary.analyzed(), summary.maliciousCount(),
                summary.benignCount(), summary.failed());
        courseNotificationService.sendCourseNotification(notification, List.of(triggerUser));
    }

    private List<Long> collectParticipationIds(Function<Pageable, Slice<Long>> sliceProvider) {
        List<Long> ids = new ArrayList<>();
        Pageable pageable = Pageable.ofSize(PAGE_SIZE);
        while (true) {
            Slice<Long> slice = sliceProvider.apply(pageable);
            ids.addAll(slice.getContent());
            if (ids.size() > MAX_PARTICIPATIONS_PER_RUN) {
                throw new IllegalArgumentException("Deimos batch exceeds configured participation limit");
            }
            if (!slice.hasNext()) {
                break;
            }
            pageable = slice.nextPageable();
        }
        return ids;
    }

    private void validateManualRequest(DeimosBatchRequestDTO request) {
        if (request.from().isAfter(request.to())) {
            throw new IllegalArgumentException("The start date must be before or equal to the end date");
        }
        long days = Duration.between(request.from(), request.to()).toDays();
        if (days > MAX_MANUAL_WINDOW_DAYS) {
            throw new IllegalArgumentException("The selected window exceeds the configured maximum");
        }
    }
}
