package de.tum.cit.aet.artemis.hyperion.service;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCourseSettingsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCompetencyDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolDeletionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolItemDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolPageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolProgressDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Starts pool builds and answers what the pool page shows: progress, the items with their decisions and lecture units, and the course's settings.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolService {

    /** The largest page the question list serves. */
    public static final int MAX_PAGE_SIZE = 500;

    private final HyperionQuizQuestionPoolBuildService buildService;

    private final HyperionQuizQuestionPoolTaskService taskService;

    private final QuizQuestionPoolItemRepository itemRepository;

    private final HyperionCourseSettingsService settingsService;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<LectureUnitApi> lectureUnitApi;

    public HyperionQuizQuestionPoolService(HyperionQuizQuestionPoolBuildService buildService, HyperionQuizQuestionPoolTaskService taskService,
            QuizQuestionPoolItemRepository itemRepository, HyperionCourseSettingsService settingsService, Optional<CourseCompetencyApi> courseCompetencyApi,
            Optional<LectureUnitApi> lectureUnitApi) {
        this.buildService = buildService;
        this.taskService = taskService;
        this.itemRepository = itemRepository;
        this.settingsService = settingsService;
        this.courseCompetencyApi = courseCompetencyApi;
        this.lectureUnitApi = lectureUnitApi;
    }

    /**
     * Tops the course's pool up to its target and starts a build in the background, unless one is already running anywhere in the cluster.
     *
     * @param course the course
     * @param userId the user the token usage is attributed to; may be {@code null}
     * @return whether a build was started, and how many items were added
     */
    public QuizQuestionPoolBuildStartDTO startBuild(Course course, @Nullable Long userId) {
        long courseId = course.getId();
        String leaseToken = buildService.tryStart(courseId);
        if (leaseToken == null) {
            return new QuizQuestionPoolBuildStartDTO(false, 0);
        }
        int enqueued;
        try {
            enqueued = buildService.enqueue(course);
            taskService.runBuildAsync(courseId, userId, leaseToken);
        }
        catch (RuntimeException e) {
            buildService.release(courseId, leaseToken);
            throw e;
        }
        return new QuizQuestionPoolBuildStartDTO(true, enqueued);
    }

    /**
     * Asks the course's running build to stop after its current step; the build then drops the items it has not generated yet.
     *
     * @param courseId the course
     * @throws ConflictException if no build of the course is running
     */
    public void cancelBuild(long courseId) {
        if (!buildService.requestCancel(courseId)) {
            throw new ConflictException("No build of this quiz question pool is running", "QuizQuestionPool", "notRunning");
        }
    }

    /**
     * Summarises the state of the course's pool, including the settings the running build planned its cells with.
     *
     * @param courseId the course
     * @return the progress
     */
    public QuizQuestionPoolProgressDTO getProgress(long courseId) {
        Map<QuizQuestionPoolItemState, Integer> counts = new EnumMap<>(QuizQuestionPoolItemState.class);
        int accepted = 0;
        int rejected = 0;
        for (QuizQuestionPoolStateCountDTO stateCount : itemRepository.countByStateForCourse(courseId)) {
            counts.merge(stateCount.state(), (int) stateCount.count(), Integer::sum);
            if (Boolean.TRUE.equals(stateCount.accepted())) {
                accepted += (int) stateCount.count();
            }
            else if (Boolean.FALSE.equals(stateCount.accepted())) {
                rejected += (int) stateCount.count();
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int toGenerate = counts.getOrDefault(QuizQuestionPoolItemState.PENDING, 0) + counts.getOrDefault(QuizQuestionPoolItemState.GENERATING, 0);
        int toJudge = counts.getOrDefault(QuizQuestionPoolItemState.GENERATED, 0) + counts.getOrDefault(QuizQuestionPoolItemState.FILTERING, 0);
        int judged = counts.getOrDefault(QuizQuestionPoolItemState.FILTERED, 0);
        int failed = counts.getOrDefault(QuizQuestionPoolItemState.FAILED_GENERATION, 0) + counts.getOrDefault(QuizQuestionPoolItemState.FAILED_FILTER, 0);
        HyperionCourseSettings buildSettings = buildService.settingsOfRunningBuild(courseId);
        return new QuizQuestionPoolProgressDTO(buildService.isRunning(courseId), total, toGenerate, toJudge, judged, accepted, rejected, failed,
                buildSettings == null ? null : HyperionCourseSettingsDTO.of(buildSettings));
    }

    /**
     * Returns one page of the course's pool with the competency titles and lecture unit names of its items.
     *
     * @param courseId     the course
     * @param competencyId the competency to restrict to, or {@code null} for the whole pool
     * @param flagged      whether to return only questions flagged for review, those awaiting a decision first
     * @param page         the zero-based page
     * @param size         the page size, at most {@link #MAX_PAGE_SIZE}
     * @return the page
     * @throws BadRequestAlertException if the page or the size is out of range
     */
    public QuizQuestionPoolPageDTO getItems(long courseId, @Nullable Long competencyId, boolean flagged, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestAlertException("The page must be at least 0 and the size between 1 and " + MAX_PAGE_SIZE, "QuizQuestionPool", "pageOutOfRange");
        }
        Page<QuizQuestionPoolItem> items = flagged ? itemRepository.findFlaggedPageForCourse(courseId, PageRequest.of(page, size))
                : itemRepository.findPageForCourse(courseId, competencyId, PageRequest.of(page, size));
        Map<Long, String> titles = courseCompetencyApi.map(api -> api.findAllForCourse(courseId)).orElse(Set.of()).stream()
                .collect(Collectors.toMap(CourseCompetency::getId, CourseCompetency::getTitle, (first, second) -> first));
        Map<Long, String> unitNames = unitNamesOf(items.getContent());
        List<QuizQuestionPoolItemDTO> dtos = items.getContent().stream().map(item -> toDTO(item, titles.get(item.getCompetencyId()), unitNames.get(item.getLectureUnitId())))
                .toList();
        List<QuizQuestionPoolCompetencyDTO> competencies = itemRepository.findCompetencyIdsForCourse(courseId).stream()
                .map(id -> new QuizQuestionPoolCompetencyDTO(id, titles.get(id))).toList();
        return new QuizQuestionPoolPageDTO(dtos, items.getTotalElements(), competencies);
    }

    private Map<Long, String> unitNamesOf(List<QuizQuestionPoolItem> items) {
        Set<Long> unitIds = items.stream().map(QuizQuestionPoolItem::getLectureUnitId).collect(Collectors.toSet());
        if (unitIds.isEmpty() || lectureUnitApi.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (LectureUnit unit : lectureUnitApi.get().findAllByIds(unitIds)) {
            if (unit.getName() != null) {
                names.put(unit.getId(), unit.getName());
            }
        }
        return names;
    }

    /**
     * Returns the course's Hyperion settings.
     *
     * @param courseId the course
     * @return the settings
     */
    public HyperionCourseSettingsDTO getSettings(long courseId) {
        return HyperionCourseSettingsDTO.of(settingsService.getSettings(courseId));
    }

    /**
     * Replaces the course's Hyperion settings.
     *
     * @param courseId the course
     * @param settings the settings to store
     * @return the stored settings
     */
    public HyperionCourseSettingsDTO updateSettings(long courseId, HyperionCourseSettingsDTO settings) {
        return HyperionCourseSettingsDTO.of(settingsService.updateSettings(courseId, settings.toSettings()));
    }

    /**
     * Records the review decision on one pool question.
     *
     * @param courseId    the course the question must belong to
     * @param questionId  the pool item
     * @param reviewState the decision
     * @return the updated item
     * @throws EntityNotFoundException  if no such item exists in the course
     * @throws BadRequestAlertException if the item has not been judged yet
     */
    public QuizQuestionPoolItemDTO updateReviewState(long courseId, long questionId, QuizQuestionReviewState reviewState) {
        QuizQuestionPoolItem item = itemRepository.findByIdElseThrow(questionId);
        if (!Objects.equals(item.getCourse().getId(), courseId)) {
            throw new EntityNotFoundException("QuizQuestionPoolItem", questionId);
        }
        if (item.getState() != QuizQuestionPoolItemState.FILTERED) {
            throw new BadRequestAlertException("Only judged questions can be reviewed", "QuizQuestionPool", "notJudged");
        }
        ZonedDateTime now = ZonedDateTime.now();
        itemRepository.updateReviewState(questionId, reviewState, now);
        item.setReviewState(reviewState);
        item.setUpdatedAt(now);
        String title = courseCompetencyApi.map(api -> api.findAllForCourse(courseId)).orElse(Set.of()).stream()
                .filter(competency -> Objects.equals(competency.getId(), item.getCompetencyId())).map(CourseCompetency::getTitle).findFirst().orElse(null);
        return toDTO(item, title, unitNamesOf(List.of(item)).get(item.getLectureUnitId()));
    }

    /**
     * Deletes one item of the course's pool. The next build generates a new question for its place.
     *
     * @param courseId   the course
     * @param questionId the pool item
     * @throws EntityNotFoundException if no such item exists in the course
     * @throws ConflictException       if a build of the course is running
     */
    public void deleteItem(long courseId, long questionId) {
        QuizQuestionPoolItem item = itemRepository.findByIdElseThrow(questionId);
        if (!Objects.equals(item.getCourse().getId(), courseId)) {
            throw new EntityNotFoundException("QuizQuestionPoolItem", questionId);
        }
        rejectWhileBuilding(courseId);
        itemRepository.deleteById(questionId);
    }

    /**
     * Deletes every item of the course's pool, or every item of one competency. The next build generates them anew.
     *
     * @param courseId     the course
     * @param competencyId the competency to clear, or {@code null} for the whole pool
     * @return how many items were deleted
     * @throws ConflictException if a build of the course is running
     */
    public QuizQuestionPoolDeletionDTO deleteItems(long courseId, @Nullable Long competencyId) {
        rejectWhileBuilding(courseId);
        return new QuizQuestionPoolDeletionDTO(itemRepository.deleteAllForCourse(courseId, competencyId));
    }

    private void rejectWhileBuilding(long courseId) {
        if (buildService.isRunning(courseId)) {
            throw new ConflictException("A build of this quiz question pool is running", "QuizQuestionPool", "buildRunning");
        }
    }

    private static QuizQuestionPoolItemDTO toDTO(QuizQuestionPoolItem item, @Nullable String competencyTitle, @Nullable String lectureUnitName) {
        return new QuizQuestionPoolItemDTO(item.getId(), item.getCompetencyId(), competencyTitle, item.getLectureUnitId(), lectureUnitName, item.getLanguage(),
                item.getQuestionType(), item.getDifficulty(), item.getState(), item.getReviewState(), flaggedOf(item), item.getQuestion(),
                item.getDecision() == null ? null : QuizQuestionReviewFlags.withFlags(item.getDecision()), item.getFailure(), item.getGeneratorModel(), item.getJudgeModel(),
                item.getUpdatedAt());
    }

    /** The stored flag, derived from the decision for items judged before the flag was stored. */
    private static @Nullable Boolean flaggedOf(QuizQuestionPoolItem item) {
        if (item.getFlagged() != null || item.getDecision() == null || item.getAccepted() == null) {
            return item.getFlagged();
        }
        return item.getAccepted() && QuizQuestionReviewFlags.isFlagged(item.getDecision());
    }
}
