package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchRequestDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchTriggerResponseDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosMaliciousParticipationLink;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class DeimosBatchService {

    private static final String DEIMOS_ANALYSIS_COMPLETE_EMAIL_SUBJECT = "email.deimos.analysisComplete.title";

    private static final String DEIMOS_ANALYSIS_COMPLETE_EMAIL_TEMPLATE = "mail/deimos/deimosAnalysisCompleteEmail";

    private static final Logger log = LoggerFactory.getLogger(DeimosBatchService.class);

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final DeimosAnalysisService deimosAnalysisService;

    private final MailSendingService mailSendingService;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TaskExecutor taskExecutor;

    private final URL artemisServerUrl;

    private static final long MAX_PARTICIPATIONS_PER_RUN = 5000;

    private static final long MAX_MANUAL_WINDOW_DAYS = 31;

    private static final int PAGE_SIZE = 200;

    public DeimosBatchService(ProgrammingSubmissionRepository programmingSubmissionRepository, DeimosAnalysisService deimosAnalysisService, MailSendingService mailSendingService,
            CourseRepository courseRepository, ProgrammingExerciseRepository programmingExerciseRepository, @Value("${server.url}") URL artemisServerUrl,
            @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.deimosAnalysisService = deimosAnalysisService;
        this.mailSendingService = mailSendingService;
        this.courseRepository = courseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.artemisServerUrl = artemisServerUrl;
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
        String scopeTitle;

        if (scope == DeimosBatchScope.COURSE) {
            courseId = scopeId;
            courseTitle = courseRepository.getCourseTitle(scopeId);
            if (courseTitle == null) {
                log.warn("Skipping Deimos completion email: could not resolve course title for course {}", scopeId);
                return;
            }
            scopeTitle = courseTitle;
        }
        else if (scope == DeimosBatchScope.EXERCISE) {
            var exerciseScopeInfo = programmingExerciseRepository.findDeimosExerciseScopeInfoById(scopeId).orElse(null);
            if (exerciseScopeInfo == null) {
                log.warn("Skipping Deimos completion email: could not resolve exercise {}", scopeId);
                return;
            }

            courseId = exerciseScopeInfo.courseId();
            courseTitle = exerciseScopeInfo.courseTitle();
            scopeTitle = exerciseScopeInfo.exerciseTitle();
        }
        else {
            return;
        }

        String base = artemisServerUrl.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String notificationUrl = base + "/courses/" + courseId;

        List<DeimosMaliciousParticipationLink> maliciousParticipationLinks = new ArrayList<>();
        for (DeimosBatchSummaryDTO.ParticipationAnalysis analysis : summary.analyzedParticipations()) {
            if (!analysis.malicious()) {
                continue;
            }
            if (analysis.exerciseId() <= 0) {
                log.warn("Omitting deep link in Deimos completion email: malicious participation {} has no exercise id", analysis.participationId());
                continue;
            }
            String participationUrl = base + "/course-management/" + courseId + "/programming-exercises/" + analysis.exerciseId() + "/participations/" + analysis.participationId()
                    + "/submissions";
            String rationale = analysis.rationale() != null ? analysis.rationale().strip() : "";
            maliciousParticipationLinks.add(new DeimosMaliciousParticipationLink(participationUrl, analysis.participationId(), rationale));
        }

        Map<String, Object> emailContext = new HashMap<>();
        emailContext.put("courseId", courseId);
        emailContext.put("courseTitle", courseTitle);
        emailContext.put("scopeTitle", scopeTitle);
        emailContext.put("analyzed", summary.analyzed());
        emailContext.put("maliciousCount", summary.maliciousCount());
        emailContext.put("benignCount", summary.benignCount());
        emailContext.put("failed", summary.failed());
        emailContext.put("notificationUrl", notificationUrl);
        emailContext.put("maliciousParticipationLinks", maliciousParticipationLinks);
        mailSendingService.buildAndSendAsync(triggerUser, DEIMOS_ANALYSIS_COMPLETE_EMAIL_SUBJECT, DEIMOS_ANALYSIS_COMPLETE_EMAIL_TEMPLATE, emailContext);
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
