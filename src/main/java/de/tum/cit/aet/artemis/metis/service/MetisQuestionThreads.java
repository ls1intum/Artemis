package de.tum.cit.aet.artemis.metis.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;

/**
 * Helpers over the questions of one comprehension check.
 * <p>
 * The questions of a check share one sequence of {@code questionOrder} values: main questions get 1 to N when they are generated, and a follow-up gets the next
 * free number when it is asked, so a follow-up of the first main question can come after the last main question in that sequence. A thread is a main question
 * followed by its follow-ups; the thread order lists the threads by their main question and every follow-up right after the question it follows up on.
 */
public final class MetisQuestionThreads {

    private static final Logger log = LoggerFactory.getLogger(MetisQuestionThreads.class);

    private static final Comparator<MetisQuestion> BY_ORDER = Comparator.comparingInt(MetisQuestion::getQuestionOrder);

    private MetisQuestionThreads() {
    }

    /**
     * Orders questions by thread: each main question, in the order of the main questions, directly followed by its follow-ups in their order. A follow-up whose
     * main question is not among the given questions is placed like a main question, by its own order, so that no question is lost.
     *
     * @param questions the questions of one check, in any order
     * @return the same questions in thread order
     */
    public static List<MetisQuestion> inThreadOrder(Collection<MetisQuestion> questions) {
        Set<Long> ids = questions.stream().map(MetisQuestion::getId).collect(Collectors.toSet());
        Map<Long, List<MetisQuestion>> followUpsByParent = questions.stream().filter(question -> question.isFollowUp() && ids.contains(question.getParentQuestionId()))
                .sorted(BY_ORDER).collect(Collectors.groupingBy(MetisQuestion::getParentQuestionId));
        List<MetisQuestion> ordered = new ArrayList<>(questions.size());
        questions.stream().filter(question -> !question.isFollowUp() || !ids.contains(question.getParentQuestionId())).sorted(BY_ORDER)
                .forEach(question -> appendThread(question, followUpsByParent, ordered));
        return ordered;
    }

    /**
     * Appends a question and, depth first, every question that follows up on it.
     *
     * @param question          the question to append
     * @param followUpsByParent the follow-ups of each question, keyed by the id of the question they follow up on, each list in question order
     * @param ordered           the list the thread is appended to
     */
    private static void appendThread(MetisQuestion question, Map<Long, List<MetisQuestion>> followUpsByParent, List<MetisQuestion> ordered) {
        ordered.add(question);
        for (MetisQuestion followUp : followUpsByParent.getOrDefault(question.getId(), List.of())) {
            appendThread(followUp, followUpsByParent, ordered);
        }
    }

    /**
     * The main questions, without their follow-ups.
     *
     * @param questions the questions of one check, in any order
     * @return the main questions in their order
     */
    public static List<MetisQuestion> mainQuestions(Collection<MetisQuestion> questions) {
        return questions.stream().filter(question -> !question.isFollowUp()).sorted(BY_ORDER).toList();
    }

    /**
     * The question that is open right now: shown to the student and not answered yet. At most one should exist; if several do, after a race between two
     * requests, the one with the lowest order counts and a warning is logged.
     *
     * @param questions the questions of one check, in any order
     * @return the open question, or empty when no question is open
     */
    public static Optional<MetisQuestion> currentQuestion(Collection<MetisQuestion> questions) {
        List<MetisQuestion> open = questions.stream().filter(question -> question.getShownAt() != null && question.getAnsweredAt() == null).sorted(BY_ORDER).toList();
        if (open.size() > 1) {
            log.warn("Metis check {} has {} open questions; the one with the lowest order counts as the current one", open.getFirst().getCheckId(), open.size());
        }
        return open.stream().findFirst();
    }

    /**
     * The main question the student gets next: the main question with the lowest order that has not been shown yet.
     *
     * @param questions the questions of one check, in any order
     * @return the next main question, or empty when every main question has been shown
     */
    public static Optional<MetisQuestion> nextUnshownMain(Collection<MetisQuestion> questions) {
        return questions.stream().filter(question -> !question.isFollowUp() && question.getShownAt() == null).min(BY_ORDER);
    }

    /**
     * How many follow-ups have been asked for one main question, which the configured maximum per main question caps.
     *
     * @param questions      the questions of one check, in any order
     * @param mainQuestionId the main question
     * @return the number of questions that follow up on it
     */
    public static int followUpCount(Collection<MetisQuestion> questions, long mainQuestionId) {
        return (int) questions.stream().filter(question -> Objects.equals(question.getParentQuestionId(), mainQuestionId)).count();
    }

    /**
     * The order a new question of the check gets: one more than the highest order so far, and 1 for a check without questions.
     *
     * @param questions the questions of one check, in any order
     * @return the next free order
     */
    public static int nextOrder(Collection<MetisQuestion> questions) {
        return questions.stream().mapToInt(MetisQuestion::getQuestionOrder).max().orElse(0) + 1;
    }
}
