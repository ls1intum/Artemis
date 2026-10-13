package de.tum.cit.aet.artemis.metis.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService;

/**
 * Canned answers of the Metis LLM for tests that stub {@link MetisLlmService#complete}. The JSON field names are the
 * ones of the structured output that generation, regeneration and rating parse.
 */
public final class MetisLlmTestResponses {

    /** The model every canned completion reports. */
    public static final String TEST_MODEL = "test-model";

    private MetisLlmTestResponses() {
    }

    /**
     * Wraps a raw answer as the transport returns it.
     *
     * @param json the raw text of the answer
     * @return the completion, reported by {@link #TEST_MODEL}
     */
    public static MetisLlmService.Completion completion(String json) {
        return new MetisLlmService.Completion(json, TEST_MODEL);
    }

    /**
     * The answer of a generation, or of a regeneration with exactly one question, numbered from 1, e.g.
     * {@code {"questions":[{"number":1,"question":"...","timeLimitSeconds":120}]}}.
     *
     * @param questions  the question texts in order
     * @param timeLimits the time limit of each question in seconds, in the same order
     * @return the JSON text
     */
    public static String questionsJson(List<String> questions, List<Integer> timeLimits) {
        if (questions.size() != timeLimits.size()) {
            throw new IllegalArgumentException("Every question needs exactly one time limit");
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("number", index + 1);
            entry.put("question", questions.get(index));
            entry.put("timeLimitSeconds", timeLimits.get(index));
            entries.add(entry);
        }
        return JsonObjectMapper.get().writeValueAsString(Map.of("questions", entries));
    }

    /**
     * The answer of a rating, e.g. {@code {"score":4,"rationale":"...","followUpQuestion":"","followUpTimeLimitSeconds":0}}.
     *
     * @param score                    the rating
     * @param rationale                the explanation for the instructor
     * @param followUpQuestion         the follow-up question, empty for none
     * @param followUpTimeLimitSeconds the time limit of the follow-up question, 0 for none
     * @return the JSON text
     */
    public static String ratingJson(int score, String rationale, String followUpQuestion, int followUpTimeLimitSeconds) {
        Map<String, Object> rating = new LinkedHashMap<>();
        rating.put("score", score);
        rating.put("rationale", rationale);
        rating.put("followUpQuestion", followUpQuestion);
        rating.put("followUpTimeLimitSeconds", followUpTimeLimitSeconds);
        return JsonObjectMapper.get().writeValueAsString(rating);
    }
}
