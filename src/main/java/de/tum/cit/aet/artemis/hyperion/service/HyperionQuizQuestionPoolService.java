package de.tum.cit.aet.artemis.hyperion.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuildTrigger;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolLectureUnit;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCourseSettingsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCompetencyDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolDeletionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolItemDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLectureUnitCountDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLectureUnitDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolPageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolPlanDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolProgressDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolBuildRepository;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolLectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Starts pool builds and answers what the pool page shows: progress with the last build, the items with their decisions and lecture
 * units, the lecture units themselves with their counts and exclusion, and the course's settings.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolService.class);

    /** The largest page the question list serves. */
    public static final int MAX_PAGE_SIZE = 500;

    private final HyperionQuizQuestionPoolBuildService buildService;

    private final HyperionQuizQuestionPoolTaskService taskService;

    private final QuizQuestionPoolItemRepository itemRepository;

    private final HyperionCourseSettingsService settingsService;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<LectureUnitApi> lectureUnitApi;

    private final QuizQuestionPoolLectureUnitRepository lectureUnitRepository;

    private final QuizQuestionPoolBuildRepository buildRepository;

    private final HyperionQuizQuestionPoolMaterialService materialService;

    private final CourseRepository courseRepository;

    private final HyperionQuizQuestionPoolModelService modelService;

    public HyperionQuizQuestionPoolService(HyperionQuizQuestionPoolBuildService buildService, HyperionQuizQuestionPoolTaskService taskService,
            QuizQuestionPoolItemRepository itemRepository, HyperionCourseSettingsService settingsService, Optional<CourseCompetencyApi> courseCompetencyApi,
            Optional<LectureUnitApi> lectureUnitApi, QuizQuestionPoolLectureUnitRepository lectureUnitRepository, QuizQuestionPoolBuildRepository buildRepository,
            HyperionQuizQuestionPoolMaterialService materialService, CourseRepository courseRepository, HyperionQuizQuestionPoolModelService modelService) {
        this.buildService = buildService;
        this.taskService = taskService;
        this.itemRepository = itemRepository;
        this.settingsService = settingsService;
        this.courseCompetencyApi = courseCompetencyApi;
        this.lectureUnitApi = lectureUnitApi;
        this.lectureUnitRepository = lectureUnitRepository;
        this.buildRepository = buildRepository;
        this.materialService = materialService;
        this.courseRepository = courseRepository;
        this.modelService = modelService;
    }

    /**
     * Tops the course's pool up to its target and starts a build in the background, unless one is already running anywhere in the cluster.
     *
     * @param course the course
     * @param userId the user the token usage is attributed to; may be {@code null}
     * @return whether a build was started, and how many items were added
     * @throws de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException if the pool has no model endpoint configured
     */
    public QuizQuestionPoolBuildStartDTO startBuild(Course course, @Nullable Long userId) {
        long courseId = course.getId();
        if (!modelService.isConfigured()) {
            log.info("Quiz question pool build of course [{}] requested by {} is refused: no model is configured for the pool", courseId, currentUser());
            throw HyperionQuizQuestionPoolModelService.notConfigured();
        }
        String leaseToken = buildService.tryStart(courseId);
        if (leaseToken == null) {
            log.info("Quiz question pool build of course [{}] requested by {} is not started: a build of the course is already running", courseId, currentUser());
            return new QuizQuestionPoolBuildStartDTO(false, 0);
        }
        int enqueued;
        long buildId;
        try {
            enqueued = buildService.enqueue(course);
            buildId = buildService.recordStart(course, QuizQuestionPoolBuildTrigger.MANUAL, enqueued).getId();
        }
        catch (RuntimeException e) {
            buildService.release(courseId, leaseToken);
            throw e;
        }
        try {
            taskService.runBuildAsync(courseId, userId, buildId, leaseToken);
        }
        catch (RuntimeException e) {
            try {
                buildService.recordDispatchFailure(buildId);
            }
            catch (RuntimeException recordFailure) {
                e.addSuppressed(recordFailure);
            }
            finally {
                buildService.release(courseId, leaseToken);
            }
            throw e;
        }
        HyperionCourseSettings settings = buildService.settingsOfRunningBuild(courseId);
        log.info("Quiz question pool build of course [{}] started by {}: {} new items, {}, generator {}, judge {}", courseId, currentUser(), enqueued,
                settings == null ? "settings unknown" : HyperionQuizQuestionPoolBuildService.describe(settings), modelService.generatorModel(), modelService.judgeModel());
        return new QuizQuestionPoolBuildStartDTO(true, enqueued);
    }

    private static String currentUser() {
        return SecurityUtils.getCurrentUserLogin().orElse("an unknown user");
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
        log.info("Quiz question pool build of course [{}]: {} asked the build to stop", courseId, currentUser());
    }

    /**
     * Computes the pool a build would plan with the course's stored settings.
     *
     * @param course the course
     * @return the planned questions and how many of them the next build would add
     */
    public QuizQuestionPoolPlanDTO getPlan(Course course) {
        return buildService.plan(course);
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
        QuizQuestionPoolBuildDTO lastBuild = buildRepository.findAllByCourseIdNewestFirst(courseId, PageRequest.of(0, 1)).stream().findFirst().map(QuizQuestionPoolBuildDTO::of)
                .orElse(null);
        return new QuizQuestionPoolProgressDTO(buildService.isRunning(courseId), total, toGenerate, toJudge, judged, accepted, rejected, failed,
                buildSettings == null ? null : HyperionCourseSettingsDTO.of(buildSettings), lastBuild, modelService.isConfigured());
    }

    /**
     * Lists the lecture units the pool can generate from, per competency they are linked to, with their counts and exclusion.
     *
     * @param courseId the course
     * @return the units in competency order, each with the competency it is listed under
     */
    public List<QuizQuestionPoolLectureUnitDTO> getLectureUnits(long courseId) {
        if (courseCompetencyApi.isEmpty()) {
            return List.of();
        }
        Map<Long, String> titles = new HashMap<>();
        List<Long> competencyIds = new ArrayList<>();
        courseCompetencyApi.get().findAllForCourse(courseId).stream().sorted((first, second) -> Long.compare(first.getId(), second.getId())).forEach(competency -> {
            competencyIds.add(competency.getId());
            titles.put(competency.getId(), competency.getTitle());
        });
        Map<Long, QuizQuestionPoolLectureUnit> records = lectureUnitRepository.findAllByCourseId(courseId).stream()
                .collect(Collectors.toMap(QuizQuestionPoolLectureUnit::getLectureUnitId, record -> record, (first, second) -> first));
        Map<Long, QuizQuestionPoolLectureUnitCountDTO> counts = itemRepository.countByLectureUnitForCourse(courseId, QuizQuestionReviewState.APPROVED).stream()
                .collect(Collectors.toMap(QuizQuestionPoolLectureUnitCountDTO::lectureUnitId, count -> count, (first, second) -> first));
        List<QuizQuestionPoolLectureUnitDTO> units = new ArrayList<>();
        materialService.findGroundableUnits(competencyIds).forEach((competencyId, lectureUnits) -> {
            for (LectureUnit unit : lectureUnits) {
                QuizQuestionPoolLectureUnit record = records.get(unit.getId());
                QuizQuestionPoolLectureUnitCountDTO count = counts.get(unit.getId());
                units.add(new QuizQuestionPoolLectureUnitDTO(competencyId, titles.get(competencyId), unit.getId(), unit.getName(),
                        unit.getLecture() != null ? unit.getLecture().getTitle() : null, record != null && record.isExcluded(), count != null && count.materialChanged() > 0,
                        count == null ? 0 : count.total().intValue(), count == null ? 0 : count.approved().intValue(), record == null ? null : record.getLastBuiltAt()));
            }
        });
        return units;
    }

    /**
     * Records whether a lecture unit is excluded from generation. Excluding a unit removes its unreviewed questions at once and keeps the
     * approved ones; including it again lets the next build fill its cells.
     *
     * @param courseId      the course
     * @param lectureUnitId the lecture unit, which must belong to a lecture of the course
     * @param excluded      whether the unit is excluded
     * @throws EntityNotFoundException if the unit does not exist in the course
     * @throws ConflictException       if the unit is to be excluded while a build of the course is running; excluding holds the course's build
     *                                     lease, so no build starts while the unit's questions are deleted
     */
    public void updateLectureUnitRelevance(long courseId, long lectureUnitId, boolean excluded) {
        LectureUnit unit = materialService.findUnits(List.of(lectureUnitId)).get(lectureUnitId);
        if (unit == null || unit.getLecture() == null || unit.getLecture().getCourse() == null || !Objects.equals(unit.getLecture().getCourse().getId(), courseId)) {
            throw new EntityNotFoundException("LectureUnit", lectureUnitId);
        }
        if (excluded) {
            whileHoldingTheBuildLease(courseId, () -> storeRelevance(courseId, lectureUnitId, true));
        }
        else {
            storeRelevance(courseId, lectureUnitId, false);
        }
        log.info("Quiz question pool of course [{}]: {} {} lecture unit [{}]", courseId, currentUser(), excluded ? "excluded" : "included", lectureUnitId);
    }

    /**
     * Stores whether the lecture unit is excluded and, when it is, deletes its questions that are not approved. An existing record is
     * updated in its exclusion only, so a build reconciling the unit at the same time keeps what it records. A unit without a record
     * counts as included, so including it creates none.
     */
    private void storeRelevance(long courseId, long lectureUnitId, boolean excluded) {
        ZonedDateTime now = ZonedDateTime.now();
        if (lectureUnitRepository.updateExcluded(courseId, lectureUnitId, excluded, now) == 0 && excluded) {
            QuizQuestionPoolLectureUnit created = new QuizQuestionPoolLectureUnit();
            created.setCourse(courseRepository.findByIdElseThrow(courseId));
            created.setLectureUnitId(lectureUnitId);
            created.setExcluded(true);
            created.setUpdatedAt(now);
            lectureUnitRepository.save(created);
        }
        if (excluded) {
            itemRepository.deleteAllByLectureUnitExceptReviewState(courseId, lectureUnitId, QuizQuestionReviewState.APPROVED);
        }
    }

    /**
     * Returns one page of the course's pool with the competency titles and lecture unit names of its items.
     *
     * @param courseId     the course
     * @param competencyId the competency to restrict to, or {@code null} for the whole pool
     * @param flagged      whether to return only questions flagged for review, those awaiting a decision first; judged questions whose flag
     *                         was never stored get it stored first
     * @param page         the zero-based page
     * @param size         the page size, at most {@link #MAX_PAGE_SIZE}
     * @return the page
     * @throws BadRequestAlertException if the page or the size is out of range
     */
    public QuizQuestionPoolPageDTO getItems(long courseId, @Nullable Long competencyId, boolean flagged, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestAlertException("The page must be at least 0 and the size between 1 and " + MAX_PAGE_SIZE, "QuizQuestionPool", "pageOutOfRange");
        }
        if (flagged) {
            storeMissingFlags(courseId);
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
        return new QuizQuestionPoolPageDTO(dtos, items.getTotalElements(), competencies, flagged ? itemRepository.countFlaggedPendingForCourse(courseId) : null);
    }

    /**
     * Stores the review flag of the course's judged items that were judged before the flag was stored, so that the flagged queue finds them.
     */
    private void storeMissingFlags(long courseId) {
        for (QuizQuestionPoolItem item : itemRepository.findAllWithoutFlagForCourse(courseId, QuizQuestionPoolItemState.FILTERED)) {
            Boolean flag = flaggedOf(item);
            if (flag != null) {
                itemRepository.storeFlag(item.getId(), flag);
            }
        }
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
        HyperionCourseSettings previous = settingsService.getSettings(courseId);
        HyperionCourseSettings stored = settingsService.updateSettings(courseId, settings.toSettings());
        if (previous != null && !previous.equals(stored)) {
            log.info("Quiz question pool settings of course [{}] changed by {}: {} -> {}", courseId, currentUser(), HyperionQuizQuestionPoolBuildService.describe(previous),
                    HyperionQuizQuestionPoolBuildService.describe(stored));
        }
        return HyperionCourseSettingsDTO.of(stored);
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
     * @throws ConflictException       if a build of the course is running; the deletion holds the course's build lease, so no build starts
     *                                     while it runs
     */
    public void deleteItem(long courseId, long questionId) {
        QuizQuestionPoolItem item = itemRepository.findByIdElseThrow(questionId);
        if (!Objects.equals(item.getCourse().getId(), courseId)) {
            throw new EntityNotFoundException("QuizQuestionPoolItem", questionId);
        }
        whileHoldingTheBuildLease(courseId, () -> itemRepository.deleteById(questionId));
        log.info("Quiz question pool of course [{}]: {} deleted question [{}]", courseId, currentUser(), questionId);
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
        int[] deleted = new int[1];
        whileHoldingTheBuildLease(courseId, () -> deleted[0] = itemRepository.deleteAllForCourse(courseId, competencyId));
        log.info("Quiz question pool of course [{}]: {} deleted {} questions of {}", courseId, currentUser(), deleted[0],
                competencyId == null ? "the whole pool" : "competency [" + competencyId + "]");
        return new QuizQuestionPoolDeletionDTO(deleted[0]);
    }

    /**
     * Runs a change of the pool while holding the course's build lease, so that no build can start, enqueue or work on the pool in the
     * meantime. The lease is released afterwards, whatever the outcome.
     *
     * @throws ConflictException if a build of the course holds the lease
     */
    private void whileHoldingTheBuildLease(long courseId, Runnable change) {
        String leaseToken = buildService.tryStart(courseId);
        if (leaseToken == null) {
            throw new ConflictException("A build of this quiz question pool is running", "QuizQuestionPool", "buildRunning");
        }
        try {
            change.run();
        }
        finally {
            buildService.release(courseId, leaseToken);
        }
    }

    private static QuizQuestionPoolItemDTO toDTO(QuizQuestionPoolItem item, @Nullable String competencyTitle, @Nullable String lectureUnitName) {
        return new QuizQuestionPoolItemDTO(item.getId(), item.getCompetencyId(), competencyTitle, item.getLectureUnitId(), lectureUnitName, item.getLanguage(),
                item.getQuestionType(), item.getDifficulty(), item.getState(), item.getReviewState(), flaggedOf(item), item.isMaterialChanged(), item.getQuestion(),
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

    /**
     * Seeds sample quiz question pool items for testing and local development.
     *
     * @param courseId the course ID
     * @return the number of seeded items
     */
    public int seedSampleQuestions(long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Long competencyId = 1L;
        if (courseCompetencyApi.isPresent()) {
            Set<CourseCompetency> competencies = courseCompetencyApi.get().findAllForCourse(courseId);
            if (!competencies.isEmpty()) {
                competencyId = competencies.iterator().next().getId();
            }
        }
        Long lectureUnitId = 1L;

        List<QuizQuestionPoolItem> existing = itemRepository.findAllPracticeEligibleByCourseId(courseId);
        if (existing.size() >= 5) {
            return existing.size();
        }

        List<QuizQuestionPoolItem> items = new ArrayList<>();
        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.EASY, 0, "Single Responsibility Principle",
                "What is the core definition of the Single Responsibility Principle (SRP) in software engineering?",
                List.of(new GeneratedQuizAnswerOptionDTO("A class or module should have one, and only one, reason to change.", true, "Think about actors and requirements.",
                        "SRP states that a module should be responsible to one, and only one, actor or reason to change."),
                        new GeneratedQuizAnswerOptionDTO("A class must contain only a single public method.", false, "Consider class cohesion.",
                                "SRP is about cohesive responsibility, not limiting the method count."),
                        new GeneratedQuizAnswerOptionDTO("A component should be authored by only one software developer.", false, null,
                                "Developer assignment has nothing to do with architectural responsibilities."),
                        new GeneratedQuizAnswerOptionDTO("A class must handle both business logic and persistence.", false, null,
                                "Combining business logic with persistence violates SRP.")),
                "Think about reasons to change and cohesive actors.",
                "The Single Responsibility Principle (SRP) states that each module or class should have one reason to change."));

        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.EASY, 1, "Model-View-Controller Roles",
                "In the Model-View-Controller (MVC) architectural pattern, what is the primary role of the Model?",
                List.of(new GeneratedQuizAnswerOptionDTO("Managing domain data, application state, and business rules.", true, "Focus on the core domain layer.",
                        "The Model encapsulates the business logic, domain entities, and data access."),
                        new GeneratedQuizAnswerOptionDTO("Capturing user UI gestures and painting pixels on the screen.", false, null, "That is the responsibility of the View."),
                        new GeneratedQuizAnswerOptionDTO("Dispatching network packets across distributed servers.", false, null,
                                "Networking and transport are infrastructure concerns."),
                        new GeneratedQuizAnswerOptionDTO("Compiling stylesheets and HTML templates.", false, null, "Template rendering belongs to the presentation/view layer.")),
                "Consider which MVC component owns business rules and state.", "The Model is responsible for managing data, state, and business logic."));

        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.MEDIUM, 0, "Layered Architecture Invariants",
                "In a classic 3-tier Layered Architecture, what is the primary invariant regarding communication between layers?",
                List.of(new GeneratedQuizAnswerOptionDTO("Layers depend unidirectionally from top to bottom (presentation -> domain -> data).", true,
                        "Consider dependency hierarchy.", "Lower layers should remain decoupled from upper layers."),
                        new GeneratedQuizAnswerOptionDTO("The presentation tier communicates directly with the database layer to optimize speed.", false, null,
                                "Bypassing intermediate tiers violates layering encapsulation."),
                        new GeneratedQuizAnswerOptionDTO("All layers must maintain cyclic bidirectional dependencies.", false, null,
                                "Cyclic dependencies undermine modularity and maintainability."),
                        new GeneratedQuizAnswerOptionDTO("The database layer invokes view components directly.", false, null,
                                "Database layers must not depend on presentation layers.")),
                "Think about unidirectional dependencies and separation of concerns.",
                "In layered architectures, dependencies strictly point downward from upper to lower layers."));

        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.MEDIUM, 1, "Dependency Inversion Principle",
                "What does the Dependency Inversion Principle (DIP) state regarding high-level and low-level modules?",
                List.of(new GeneratedQuizAnswerOptionDTO("High-level modules should not depend on low-level modules; both should depend on abstractions.", true,
                        "Focus on abstraction boundaries.", "DIP states that high-level policies should depend on abstractions (interfaces), not concrete implementations."),
                        new GeneratedQuizAnswerOptionDTO("High-level modules should directly instantiate concrete low-level classes.", false, null,
                                "Direct instantiation creates tight coupling to concrete classes."),
                        new GeneratedQuizAnswerOptionDTO("Abstractions should depend on implementation details.", false, null,
                                "DIP explicitly states details should depend on abstractions, not vice versa."),
                        new GeneratedQuizAnswerOptionDTO("Low-level modules must dictate the design of high-level interfaces.", false, null,
                                "Interfaces should be client-oriented, not driven by low-level details.")),
                "Think about interfaces and dependency direction.", "DIP decouples high-level policy from low-level details by introducing abstraction boundaries."));

        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.MEDIUM, 2, "Benefits of Loose Coupling",
                "Why is loose coupling an essential goal in software architecture?",
                List.of(new GeneratedQuizAnswerOptionDTO("It minimizes ripple effects, enabling components to be tested and evolved independently.", true,
                        "Consider change propagation and isolation.", "Loose coupling isolates changes to individual modules, reducing maintenance cost and testing friction."),
                        new GeneratedQuizAnswerOptionDTO("It eliminates the need for software design patterns entirely.", false, null,
                                "Patterns frequently aim to achieve loose coupling."),
                        new GeneratedQuizAnswerOptionDTO("It guarantees double the execution speed by skipping function calls.", false, null,
                                "Coupling affects maintainability and modularity, not raw CPU execution speed."),
                        new GeneratedQuizAnswerOptionDTO("It ensures all modules share the same global variables.", false, null,
                                "Global mutable state actually creates tight coupling.")),
                "Think about maintainability and independence of modules.", "Loose coupling allows modules to be changed, tested, and maintained independently."));

        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.HARD, 0, "Microservices vs Modular Monolith",
                "Which fundamental trade-off is introduced when migrating from a modular monolith to distributed microservices?",
                List.of(new GeneratedQuizAnswerOptionDTO("Increased operational complexity and the need for eventual consistency across services.", true,
                        "Consider distribution, networks, and transactions.",
                        "Microservices exchange in-process calls for network calls, introducing partial failures, latency, and distributed state coordination."),
                        new GeneratedQuizAnswerOptionDTO("Inability to deploy or scale services independently.", false, null,
                                "Independent deployment is the primary motivation for microservices."),
                        new GeneratedQuizAnswerOptionDTO("Elimination of network communication overhead.", false, null,
                                "Microservices increase network communication significantly."),
                        new GeneratedQuizAnswerOptionDTO("Free distributed ACID transactions without two-phase commit overhead.", false, null,
                                "Distributed transactions are notoriously complex and often replaced with Sagas and eventual consistency.")),
                "Consider network boundaries and data consistency challenges.", "Microservices introduce distributed system fallacies, latency, and data consistency challenges."));

        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.HARD, 1, "Hexagonal Architecture Ports",
                "In Hexagonal Architecture (Ports and Adapters), what is the purpose of an Inbound (Driving) Port?",
                List.of(new GeneratedQuizAnswerOptionDTO("An interface declared by the application core that driving adapters (e.g. controllers) invoke.", true,
                        "Think about who defines the port vs who implements it.", "Inbound ports expose use cases that external entry points call to drive the domain logic."),
                        new GeneratedQuizAnswerOptionDTO("A JDBC driver implementation that communicates with a relational database.", false, null,
                                "Database interactions use Outbound (Driven) ports and adapters."),
                        new GeneratedQuizAnswerOptionDTO("A physical network firewall port.", false, null,
                                "Inbound ports are logical software abstractions, not network hardware."),
                        new GeneratedQuizAnswerOptionDTO("A queue listener that is private to the storage engine.", false, null,
                                "Storage mechanisms act as driven/secondary adapters.")),
                "Remember: the core defines the port, external driving adapters call it.",
                "Inbound ports define the use-case interface offered by the domain to driving adapters."));

        items.add(createSampleItem(course, competencyId, lectureUnitId, QuizQuestionDifficulty.HARD, 2, "CAP Theorem Consistency Trade-offs",
                "According to the CAP theorem, what does a system choose when it favors Availability and Partition Tolerance (AP)?",
                List.of(new GeneratedQuizAnswerOptionDTO("It continues processing requests during a network split, accepting eventual consistency rather than strict consistency.",
                        true, "Consider what AP sacrifices.",
                        "In an AP system, replicas remain available during partitions but their data may temporarily diverge until the network heals."),
                        new GeneratedQuizAnswerOptionDTO("It rejects all write operations until the partition is fully resolved.", false, null,
                                "Rejecting writes to maintain consistency is characteristic of CP systems."),
                        new GeneratedQuizAnswerOptionDTO("It achieves instant strict serializability across all nodes with zero latency.", false, null,
                                "Strict serializability cannot be maintained across disconnected nodes without blocking."),
                        new GeneratedQuizAnswerOptionDTO("It avoids the need for network communication entirely.", false, null,
                                "Partition tolerance deals specifically with network communication failures.")),
                "Think about what AP systems guarantee when partitions occur.",
                "AP systems prioritize availability by allowing reads and writes to succeed despite partitions, sacrificing immediate consistency."));

        itemRepository.saveAll(items);
        log.info("Seeded {} quiz question pool items for course [{}]", items.size(), courseId);
        return items.size();
    }

    private QuizQuestionPoolItem createSampleItem(Course course, Long competencyId, Long lectureUnitId, QuizQuestionDifficulty difficulty, int itemIndex, String title,
            String questionText, List<GeneratedQuizAnswerOptionDTO> options, String hint, String explanation) {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setCourse(course);
        item.setCompetencyId(competencyId);
        item.setLectureUnitId(lectureUnitId);
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(QuizQuestionGenerationType.MULTIPLE_CHOICE);
        item.setDifficulty(difficulty);
        item.setItemIndex(itemIndex);
        item.setState(QuizQuestionPoolItemState.FILTERED);
        item.setReviewState(QuizQuestionReviewState.APPROVED);
        item.setAccepted(true);
        item.setGenerationAttempts(1);
        item.setFilterAttempts(1);
        item.setGeneratorModel("seeded");
        item.setJudgeModel("seeded");
        item.setUpdatedAt(ZonedDateTime.now());

        GeneratedQuizQuestionDTO questionDto = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.MULTIPLE_CHOICE, title, questionText, options, hint, explanation);
        item.setQuestion(questionDto);
        return item;
    }
}
