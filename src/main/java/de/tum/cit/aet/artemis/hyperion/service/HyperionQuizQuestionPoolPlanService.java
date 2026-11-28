package de.tum.cit.aet.artemis.hyperion.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.ApiConditionNotPresentException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolLectureUnit;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCellCountDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolPlanDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolLectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Plans a course's quiz question pool: one cell per combination of competency, lecture unit linked to it, language, question type and
 * difficulty, sized by the course's settings. Before planning, every lecture unit is reconciled with the material it was last built
 * from: the unreviewed questions of a unit whose material changed or disappeared are replaced, and its approved ones are flagged.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolPlanService {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolPlanService.class);

    private final HyperionQuizQuestionPoolMaterialService materialService;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final QuizQuestionPoolItemRepository itemRepository;

    private final HyperionCourseSettingsService settingsService;

    private final QuizQuestionPoolLectureUnitRepository lectureUnitRepository;

    public HyperionQuizQuestionPoolPlanService(HyperionQuizQuestionPoolMaterialService materialService, Optional<CourseCompetencyApi> courseCompetencyApi,
            QuizQuestionPoolItemRepository itemRepository, HyperionCourseSettingsService settingsService, QuizQuestionPoolLectureUnitRepository lectureUnitRepository) {
        this.materialService = materialService;
        this.courseCompetencyApi = courseCompetencyApi;
        this.itemRepository = itemRepository;
        this.settingsService = settingsService;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    /**
     * One cell of the pool grid.
     */
    public record Cell(long competencyId, long lectureUnitId, QuizQuestionGenerationLanguage language, QuizQuestionGenerationType questionType, QuizQuestionDifficulty difficulty) {
    }

    /**
     * Plans the target size of every cell of the grid, in a fixed order: for each competency and lecture unit, the single-choice count
     * and, while multiple-choice questions are enabled, the multiple-choice count are split over the difficulties by the course's
     * shares. A difficulty whose share comes to no question gets no cell.
     *
     * @param unitIdsByCompetency the groundable lecture units linked to each competency
     * @param language            the language to generate in
     * @param settings            the course's settings
     * @return the target per cell, ordered by competency, lecture unit, question type, then difficulty
     */
    static Map<Cell, Integer> planCells(Map<Long, ? extends List<Long>> unitIdsByCompetency, QuizQuestionGenerationLanguage language, HyperionCourseSettings settings) {
        Map<QuizQuestionGenerationType, Integer> countsByType = new EnumMap<>(QuizQuestionGenerationType.class);
        countsByType.put(QuizQuestionGenerationType.SINGLE_CHOICE, settings.singleChoiceQuestionsPerLectureUnit());
        if (settings.multipleChoiceEnabled()) {
            countsByType.put(QuizQuestionGenerationType.MULTIPLE_CHOICE, settings.multipleChoiceQuestionsPerLectureUnit());
        }
        QuizQuestionDifficulty[] difficulties = QuizQuestionDifficulty.values();
        int[] percents = new int[difficulties.length];
        for (int index = 0; index < difficulties.length; index++) {
            percents[index] = settings.percentOf(difficulties[index]);
        }
        Map<Cell, Integer> targets = new LinkedHashMap<>();
        for (Map.Entry<Long, ? extends List<Long>> entry : new TreeMap<>(unitIdsByCompetency).entrySet()) {
            for (Long lectureUnitId : entry.getValue().stream().sorted().toList()) {
                for (Map.Entry<QuizQuestionGenerationType, Integer> typeCount : countsByType.entrySet()) {
                    putDifficultyCells(targets, new Cell(entry.getKey(), lectureUnitId, language, typeCount.getKey(), difficulties[0]), typeCount.getValue(), percents);
                }
            }
        }
        return targets;
    }

    /** Puts one cell per difficulty that receives at least one of the type's questions, varying only the difficulty of the given cell. */
    private static void putDifficultyCells(Map<Cell, Integer> targets, Cell cell, int count, int[] percents) {
        QuizQuestionDifficulty[] difficulties = QuizQuestionDifficulty.values();
        int[] perDifficulty = splitByPercent(count, percents);
        for (int index = 0; index < difficulties.length; index++) {
            if (perDifficulty[index] > 0) {
                targets.put(new Cell(cell.competencyId(), cell.lectureUnitId(), cell.language(), cell.questionType(), difficulties[index]), perDifficulty[index]);
            }
        }
    }

    /**
     * Splits a count over shares given in percent so that the parts add up to the count: every part receives the whole number of its
     * share, and the remainder goes one by one to the parts with the largest fractions, earlier parts first on a tie.
     *
     * @param count    the count to split
     * @param percents the shares in percent, adding up to 100
     * @return one part per share, in the order of the shares
     */
    static int[] splitByPercent(int count, int[] percents) {
        int[] parts = new int[percents.length];
        int[] fractions = new int[percents.length];
        int assigned = 0;
        for (int index = 0; index < percents.length; index++) {
            int scaled = count * percents[index];
            parts[index] = scaled / 100;
            fractions[index] = scaled % 100;
            assigned += parts[index];
        }
        for (int remainder = count - assigned; remainder > 0; remainder--) {
            int largest = 0;
            for (int index = 1; index < percents.length; index++) {
                if (fractions[index] > fractions[largest]) {
                    largest = index;
                }
            }
            parts[largest]++;
            fractions[largest] = -1;
        }
        return parts;
    }

    /**
     * Reconciles the course's lecture units with the material they were last built from and tops every cell of the pool up to its
     * target, creating the missing items in state {@link QuizQuestionPoolItemState#PENDING}. Cells exist for every competency and each
     * lecture unit linked to it that carries readable material and is not excluded. A unit whose material changed loses its
     * unreviewed items and keeps its approved ones flagged; an excluded unit loses its unreviewed items and gets no cells.
     * New items take the indices above the highest index of their cell, so a cell whose items were partly deleted gets no duplicate index.
     *
     * @param course   the course
     * @param settings the settings to plan the cells with
     * @return the number of items created
     */
    public int enqueue(Course course, HyperionCourseSettings settings) {
        CourseCompetencyApi api = courseCompetencyApi.orElseThrow(() -> new ApiConditionNotPresentException(CourseCompetencyApi.class, "artemis.atlas.enabled"));
        List<Long> competencyIds = api.findAllForCourse(course.getId()).stream().map(CourseCompetency::getId).sorted().toList();
        Map<Long, QuizQuestionPoolLectureUnit> records = lectureUnitRepository.findAllByCourseId(course.getId()).stream()
                .collect(Collectors.toMap(QuizQuestionPoolLectureUnit::getLectureUnitId, Function.identity(), (first, second) -> first));
        ZonedDateTime now = ZonedDateTime.now();
        Map<Long, List<Long>> unitIdsByCompetency = new LinkedHashMap<>();
        Set<Long> reconciled = new HashSet<>();
        Map<Reconciliation, Integer> reconciliations = new EnumMap<>(Reconciliation.class);
        for (Map.Entry<Long, List<LectureUnit>> entry : materialService.findGroundableUnits(competencyIds).entrySet()) {
            List<Long> unitIds = new ArrayList<>();
            for (LectureUnit unit : entry.getValue()) {
                if (reconciled.add(unit.getId())) {
                    reconciliations.merge(reconcile(course, unit, records, now), 1, Integer::sum);
                }
                if (!records.get(unit.getId()).isExcluded()) {
                    unitIds.add(unit.getId());
                }
            }
            if (!unitIds.isEmpty()) {
                unitIdsByCompetency.put(entry.getKey(), unitIds);
            }
        }
        for (QuizQuestionPoolLectureUnit record : records.values()) {
            if (!reconciled.contains(record.getLectureUnitId()) && record.getId() != null && !record.isExcluded() && record.getFingerprint() != null) {
                reconciliations.merge(reconcileRemovedMaterial(course, record.getLectureUnitId(), now), 1, Integer::sum);
            }
        }
        lectureUnitRepository.saveAll(records.values().stream().filter(record -> record.getId() == null).toList());
        Map<Cell, Integer> targets = planCells(unitIdsByCompetency, languageOf(course), settings);
        Map<Cell, QuizQuestionPoolCellCountDTO> existingByCell = countsByCell(course.getId());
        List<QuizQuestionPoolItem> created = new ArrayList<>();
        for (Map.Entry<Cell, Integer> target : targets.entrySet()) {
            Cell cell = target.getKey();
            QuizQuestionPoolCellCountDTO counts = existingByCell.get(cell);
            int existing = counts == null ? 0 : (int) counts.count();
            int nextIndex = counts == null ? 0 : Math.max(existing, counts.highestIndex() + 1);
            for (int index = nextIndex; index < nextIndex + target.getValue() - existing; index++) {
                QuizQuestionPoolItem item = new QuizQuestionPoolItem();
                item.setCourse(course);
                item.setCompetencyId(cell.competencyId());
                item.setLectureUnitId(cell.lectureUnitId());
                item.setLanguage(cell.language());
                item.setQuestionType(cell.questionType());
                item.setDifficulty(cell.difficulty());
                item.setItemIndex(index);
                created.add(item);
            }
        }
        itemRepository.saveAll(created);
        log.info("Quiz question pool of course [{}]: {} of {} competencies have lecture material, {} cells, {} new items, units {}", course.getId(), unitIdsByCompetency.size(),
                competencyIds.size(), targets.size(), created.size(), reconciliations);
        return created.size();
    }

    private enum Reconciliation {
        NEW, UNCHANGED, CHANGED, MATERIAL_REMOVED, EXCLUDED
    }

    /**
     * Computes the pool a build would plan with the course's stored settings, without creating or changing anything. Excluded lecture
     * units get no cells; a change of a unit's material is only detected when a build reconciles the unit.
     *
     * @param course the course
     * @return how many questions the pool holds once such a build is complete, and how many that build would add
     */
    public QuizQuestionPoolPlanDTO plan(Course course) {
        Map<Cell, Integer> targets = targetsOf(course, settingsService.getSettings(course.getId()));
        Map<Cell, QuizQuestionPoolCellCountDTO> existingByCell = countsByCell(course.getId());
        int planned = 0;
        int missing = 0;
        for (Map.Entry<Cell, Integer> target : targets.entrySet()) {
            QuizQuestionPoolCellCountDTO counts = existingByCell.get(target.getKey());
            planned += target.getValue();
            missing += Math.max(0, target.getValue() - (counts == null ? 0 : (int) counts.count()));
        }
        return new QuizQuestionPoolPlanDTO(planned, missing);
    }

    /** The target of every cell under the given settings, over the competencies and their groundable lecture units that are not excluded. */
    private Map<Cell, Integer> targetsOf(Course course, HyperionCourseSettings settings) {
        CourseCompetencyApi api = courseCompetencyApi.orElseThrow(() -> new ApiConditionNotPresentException(CourseCompetencyApi.class, "artemis.atlas.enabled"));
        List<Long> competencyIds = api.findAllForCourse(course.getId()).stream().map(CourseCompetency::getId).sorted().toList();
        Set<Long> excluded = lectureUnitRepository.findAllByCourseId(course.getId()).stream().filter(QuizQuestionPoolLectureUnit::isExcluded)
                .map(QuizQuestionPoolLectureUnit::getLectureUnitId).collect(Collectors.toSet());
        Map<Long, List<Long>> unitIdsByCompetency = new LinkedHashMap<>();
        materialService.findGroundableUnits(competencyIds).forEach((competencyId, units) -> {
            List<Long> unitIds = units.stream().map(LectureUnit::getId).filter(unitId -> !excluded.contains(unitId)).toList();
            if (!unitIds.isEmpty()) {
                unitIdsByCompetency.put(competencyId, unitIds);
            }
        });
        return planCells(unitIdsByCompetency, languageOf(course), settings);
    }

    /** The items of every cell whose material is unchanged, and the highest item index of the cell. */
    private Map<Cell, QuizQuestionPoolCellCountDTO> countsByCell(long courseId) {
        return itemRepository.countByCellForCourse(courseId).stream().collect(
                Collectors.toMap(count -> new Cell(count.competencyId(), count.lectureUnitId(), count.language(), count.questionType(), count.difficulty()), Function.identity()));
    }

    /**
     * Compares one lecture unit with the record of its last build and applies the consequences to the pool. A missing record is created
     * in {@code records}, unsaved, for the caller to insert; an existing one is updated through
     * {@link QuizQuestionPoolLectureUnitRepository#updateBuildState}, which leaves the exclusion an instructor may change meanwhile untouched.
     */
    private Reconciliation reconcile(Course course, LectureUnit unit, Map<Long, QuizQuestionPoolLectureUnit> records, ZonedDateTime now) {
        long unitId = unit.getId();
        QuizQuestionPoolLectureUnit record = records.get(unitId);
        if (record == null) {
            record = new QuizQuestionPoolLectureUnit();
            record.setCourse(course);
            record.setLectureUnitId(unitId);
            record.setFingerprint(materialService.fingerprintOf(unit));
            record.setLastBuiltAt(now);
            record.setUpdatedAt(now);
            records.put(unitId, record);
            return Reconciliation.NEW;
        }
        if (record.isExcluded()) {
            itemRepository.deleteAllByLectureUnitExceptReviewState(course.getId(), unitId, QuizQuestionReviewState.APPROVED);
            return Reconciliation.EXCLUDED;
        }
        String fingerprint = materialService.fingerprintOf(unit);
        Reconciliation reconciliation = Reconciliation.UNCHANGED;
        if (record.getFingerprint() == null) {
            reconciliation = Reconciliation.NEW;
        }
        else if (!Objects.equals(fingerprint, record.getFingerprint())) {
            replaceQuestions(course.getId(), unitId, now, "changed");
            reconciliation = Reconciliation.CHANGED;
        }
        lectureUnitRepository.updateBuildState(course.getId(), unitId, fingerprint, now);
        return reconciliation;
    }

    /**
     * Applies the removal of a lecture unit's material: a unit that was built before but no longer yields readable material, or no longer
     * belongs to a competency, loses its unreviewed items, keeps its approved ones flagged, and gets no cells until it has material again.
     */
    private Reconciliation reconcileRemovedMaterial(Course course, long unitId, ZonedDateTime now) {
        replaceQuestions(course.getId(), unitId, now, "lost its material");
        lectureUnitRepository.updateBuildState(course.getId(), unitId, null, now);
        return Reconciliation.MATERIAL_REMOVED;
    }

    /** Deletes the unit's items that are not approved and flags the approved ones as built from earlier material. */
    private void replaceQuestions(long courseId, long unitId, ZonedDateTime now, String reason) {
        int deleted = itemRepository.deleteAllByLectureUnitExceptReviewState(courseId, unitId, QuizQuestionReviewState.APPROVED);
        int flagged = itemRepository.markMaterialChangedByLectureUnit(courseId, unitId, now);
        log.info("Lecture unit [{}] of course [{}] {}: {} unreviewed items deleted, {} approved items flagged", unitId, courseId, reason, deleted, flagged);
    }

    static QuizQuestionGenerationLanguage languageOf(Course course) {
        return course.getLanguage() == Language.GERMAN ? QuizQuestionGenerationLanguage.DE : QuizQuestionGenerationLanguage.EN;
    }

}
