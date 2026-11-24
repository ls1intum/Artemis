package de.tum.cit.aet.artemis.hyperion.service;

import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolSelectionCandidateDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolSelectionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;

/**
 * Draws questions from a course's quiz question pool for a new quiz.
 * <p>
 * A draw is deterministic and involves no model call. It takes questions round-robin over the requested competencies, within a
 * competency over the lecture units and within a unit over the question types and difficulties. At every level the round-robin starts
 * with the queue holding the question drawn least often and longest ago, and within each queue it takes those questions first, so
 * consecutive draws move on to other questions even when every cell holds only one. Eligible are judged questions whose material has not
 * changed and that are approved, or await a decision after the filter accepted them without flagging them for review: an excluded question
 * never takes part, and one flagged for review or rejected by the filter only once approved. Every drawn question's selection count and
 * time are updated, so repeated draws rotate through the pool.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@Service
public class HyperionQuizQuestionPoolSelectionService {

    /** Least often drawn first, then never drawn before longest ago, then by id. */
    static final Comparator<QuizQuestionPoolItem> DRAW_ORDER = Comparator.comparingInt(QuizQuestionPoolItem::getSelectionCount)
            .thenComparing(QuizQuestionPoolItem::getLastSelectedAt, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingLong(QuizQuestionPoolItem::getId);

    private final QuizQuestionPoolItemRepository itemRepository;

    public HyperionQuizQuestionPoolSelectionService(QuizQuestionPoolItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * The outcome of one draw.
     *
     * @param items                         the drawn items in draw order, fewer than requested when the pool held fewer eligible ones
     * @param competencyIdsWithoutQuestions requested competencies for which the pool holds no eligible question
     */
    public record Selection(List<QuizQuestionPoolItem> items, List<Long> competencyIdsWithoutQuestions) {
    }

    /**
     * Draws questions for one quiz and records the draw on every drawn item.
     *
     * @param courseId the course whose pool to draw from
     * @param request  the competencies, lecture units, count, question types and difficulties to draw
     * @return the drawn items and the competencies that yielded nothing
     */
    public Selection select(long courseId, QuizQuestionPoolSelectionRequestDTO request) {
        List<Long> competencyIds = request.competencyIds().stream().distinct().toList();
        List<QuizQuestionPoolItem> eligible = findEligible(courseId, competencyIds, request.lectureUnitIds(), request.questionTypes(), request.difficulties());
        List<QuizQuestionPoolItem> drawn = draw(eligible, competencyIds, request.count());
        ZonedDateTime now = ZonedDateTime.now();
        for (QuizQuestionPoolItem item : drawn) {
            item.setSelectionCount(item.getSelectionCount() + 1);
            item.setLastSelectedAt(now);
            item.setUpdatedAt(now);
        }
        if (!drawn.isEmpty()) {
            itemRepository.recordSelection(drawn.stream().map(QuizQuestionPoolItem::getId).toList(), now);
        }
        List<Long> empty = competencyIds.stream().filter(competencyId -> eligible.stream().noneMatch(item -> item.getCompetencyId().equals(competencyId))).toList();
        return new Selection(drawn, empty);
    }

    /**
     * Finds the questions a draw may take, in draw order: judged questions whose material has not changed and that are approved, or await a
     * decision after the filter accepted them without flagging them for review. Every filter left {@code null} or empty matches everything.
     *
     * @param courseId       the course
     * @param competencyIds  the competencies the questions were generated for
     * @param lectureUnitIds the lecture units whose material grounds the questions
     * @param questionTypes  the question types
     * @param difficulties   the difficulties
     * @return the matching items, least often and longest ago drawn first
     */
    public List<QuizQuestionPoolItem> findEligible(long courseId, @Nullable Collection<Long> competencyIds, @Nullable Collection<Long> lectureUnitIds,
            @Nullable Collection<QuizQuestionGenerationType> questionTypes, @Nullable Collection<QuizQuestionDifficulty> difficulties) {
        Collection<Long> competencies = competencyIds == null || competencyIds.isEmpty() ? itemRepository.findCompetencyIdsForCourse(courseId) : competencyIds;
        if (competencies.isEmpty()) {
            return List.of();
        }
        return itemRepository.findAllForSelection(courseId, competencies, QuizQuestionPoolItemState.FILTERED).stream().filter(item -> item.getQuestion() != null)
                .filter(item -> matches(lectureUnitIds, item.getLectureUnitId())).filter(item -> matches(questionTypes, item.getQuestionType()))
                .filter(item -> matches(difficulties, item.getDifficulty())).sorted(DRAW_ORDER).toList();
    }

    /**
     * Counts the questions a draw may take, per competency, lecture unit, question type and difficulty: judged questions whose
     * material has not changed and that are approved, or await a decision after the filter accepted them without flagging them for review.
     *
     * @param courseId the course
     * @return one count per cell that holds such questions
     */
    public List<QuizQuestionPoolSelectionCandidateDTO> candidates(long courseId) {
        return itemRepository.countSelectionCandidates(courseId, QuizQuestionPoolItemState.FILTERED);
    }

    private static <T> boolean matches(@Nullable Collection<T> wanted, T value) {
        return wanted == null || wanted.isEmpty() || wanted.contains(value);
    }

    /**
     * Takes up to {@code count} items round-robin over the competencies, within a competency over its lecture units and within a unit
     * over its cells of question type and difficulty. Every level visits its queues in the order in which their first item appears in
     * {@code eligible}, and every queue holds its items in that order.
     *
     * @param eligible      the items to draw from, already in draw order
     * @param competencyIds the competencies to draw from
     * @param count         how many items to take at most
     * @return the drawn items in draw order
     */
    static List<QuizQuestionPoolItem> draw(List<QuizQuestionPoolItem> eligible, List<Long> competencyIds, int count) {
        Set<Long> requested = new HashSet<>(competencyIds);
        Map<Long, Map<Long, Map<Cell, Deque<QuizQuestionPoolItem>>>> queues = new LinkedHashMap<>();
        for (QuizQuestionPoolItem item : eligible) {
            if (requested.contains(item.getCompetencyId())) {
                queues.computeIfAbsent(item.getCompetencyId(), competencyId -> new LinkedHashMap<>()).computeIfAbsent(item.getLectureUnitId(), unitId -> new LinkedHashMap<>())
                        .computeIfAbsent(new Cell(item.getQuestionType(), item.getDifficulty()), cell -> new ArrayDeque<>()).add(item);
            }
        }
        List<Source> competencies = new ArrayList<>();
        for (Map<Long, Map<Cell, Deque<QuizQuestionPoolItem>>> units : queues.values()) {
            List<Source> unitSources = new ArrayList<>();
            for (Map<Cell, Deque<QuizQuestionPoolItem>> cells : units.values()) {
                List<Source> cellSources = new ArrayList<>();
                for (Deque<QuizQuestionPoolItem> cell : cells.values()) {
                    cellSources.add(cell::pollFirst);
                }
                unitSources.add(new RoundRobin(cellSources));
            }
            competencies.add(new RoundRobin(unitSources));
        }
        Source root = new RoundRobin(competencies);
        List<QuizQuestionPoolItem> drawn = new ArrayList<>();
        while (drawn.size() < count) {
            QuizQuestionPoolItem item = root.next();
            if (item == null) {
                break;
            }
            drawn.add(item);
        }
        return drawn;
    }

    /** The question type and difficulty of a cell within a lecture unit. */
    private record Cell(QuizQuestionGenerationType type, QuizQuestionDifficulty difficulty) {
    }

    /** A queue of items; {@link #next()} returns {@code null} once it is exhausted. */
    @FunctionalInterface
    private interface Source {

        @Nullable
        QuizQuestionPoolItem next();
    }

    /** Visits its sources in turn, skipping exhausted ones, and is exhausted once all of them are. */
    private static final class RoundRobin implements Source {

        private final List<Source> sources;

        private int cursor;

        private RoundRobin(List<Source> sources) {
            this.sources = sources;
        }

        @Override
        @Nullable
        public QuizQuestionPoolItem next() {
            for (int attempt = 0; attempt < sources.size(); attempt++) {
                Source source = sources.get(cursor);
                cursor = (cursor + 1) % sources.size();
                QuizQuestionPoolItem item = source.next();
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
    }
}
