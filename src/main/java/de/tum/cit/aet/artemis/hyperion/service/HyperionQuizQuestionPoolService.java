package de.tum.cit.aet.artemis.hyperion.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCourseSettingsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolItemDTO;
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
     * Tops the course's pool up to its target and starts a build in the background, unless one is already running on this node.
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
     * Summarises the state of the course's pool.
     *
     * @param courseId the course
     * @return the progress
     */
    public QuizQuestionPoolProgressDTO getProgress(long courseId) {
        Map<QuizQuestionPoolItemState, Integer> counts = new EnumMap<>(QuizQuestionPoolItemState.class);
        for (QuizQuestionPoolStateCountDTO stateCount : itemRepository.countByStateForCourse(courseId)) {
            counts.put(stateCount.state(), (int) stateCount.count());
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int toGenerate = counts.getOrDefault(QuizQuestionPoolItemState.PENDING, 0) + counts.getOrDefault(QuizQuestionPoolItemState.GENERATING, 0);
        int toJudge = counts.getOrDefault(QuizQuestionPoolItemState.GENERATED, 0) + counts.getOrDefault(QuizQuestionPoolItemState.FILTERING, 0);
        int judged = counts.getOrDefault(QuizQuestionPoolItemState.FILTERED, 0);
        int failed = counts.getOrDefault(QuizQuestionPoolItemState.FAILED_GENERATION, 0) + counts.getOrDefault(QuizQuestionPoolItemState.FAILED_FILTER, 0);
        int accepted = itemRepository.countByCourseIdAndAccepted(courseId, true);
        int rejected = itemRepository.countByCourseIdAndAccepted(courseId, false);
        return new QuizQuestionPoolProgressDTO(buildService.isRunning(courseId), total, toGenerate, toJudge, judged, accepted, rejected, failed);
    }

    /**
     * Lists the items of the course's pool, optionally restricted to one competency, in cell order.
     *
     * @param courseId     the course
     * @param competencyId the competency to restrict to, or {@code null} for the whole pool
     * @return the items with their questions and decisions
     */
    public List<QuizQuestionPoolItemDTO> getItems(long courseId, @Nullable Long competencyId) {
        List<QuizQuestionPoolItem> items = competencyId == null ? itemRepository.findAllByCourseIdOrdered(courseId)
                : itemRepository.findAllByCourseIdAndCompetencyIdOrdered(courseId, competencyId);
        Map<Long, String> titles = courseCompetencyApi.map(api -> api.findAllForCourse(courseId)).orElse(Set.of()).stream()
                .collect(Collectors.toMap(CourseCompetency::getId, CourseCompetency::getTitle, (first, second) -> first));
        Map<Long, String> unitNames = unitNamesOf(items);
        return items.stream().map(item -> toDTO(item, titles.get(item.getCompetencyId()), unitNames.get(item.getLectureUnitId()))).toList();
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

    private static QuizQuestionPoolItemDTO toDTO(QuizQuestionPoolItem item, @Nullable String competencyTitle, @Nullable String lectureUnitName) {
        return new QuizQuestionPoolItemDTO(item.getId(), item.getCompetencyId(), competencyTitle, item.getLectureUnitId(), lectureUnitName, item.getLanguage(),
                item.getQuestionType(), item.getDifficulty(), item.getState(), item.getReviewState(), item.getQuestion(), item.getDecision(), item.getFailure(),
                item.getGeneratorModel(), item.getJudgeModel(), item.getUpdatedAt());
    }
}
