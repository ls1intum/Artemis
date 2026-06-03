package de.tum.cit.aet.artemis.hyperion.service;

import java.util.ArrayList;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.iris.api.IrisLectureSearchApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Resolves selected competencies for a course and builds enriched context for LLM quiz question generation.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionCompetencyContextService {

    private static final Logger log = LoggerFactory.getLogger(HyperionCompetencyContextService.class);

    private static final int LECTURE_SEARCH_LIMIT = 20;

    private static final int MAX_EXERCISE_SUMMARIES = 10;

    private static final String PROMPT_EXTRACT_EXERCISE_INSIGHTS_SYSTEM = "/prompts/hyperion/extract_exercise_insights_system.st";

    private static final String PROMPT_EXTRACT_EXERCISE_INSIGHTS_USER = "/prompts/hyperion/extract_exercise_insights_user.st";

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    private final Optional<IrisLectureSearchApi> irisLectureSearchApi;

    private final Optional<LectureUnitApi> lectureUnitApi;

    private final Optional<HyperionPromptTemplateService> templateService;

    @Nullable
    private final ChatClient chatClient;

    public HyperionCompetencyContextService(Optional<CourseCompetencyApi> courseCompetencyApi, Optional<CompetencyRelationApi> competencyRelationApi,
            Optional<IrisLectureSearchApi> irisLectureSearchApi, Optional<LectureUnitApi> lectureUnitApi, Optional<HyperionPromptTemplateService> templateService,
            @Nullable ChatClient chatClient) {
        this.courseCompetencyApi = courseCompetencyApi;
        this.competencyRelationApi = competencyRelationApi;
        this.irisLectureSearchApi = irisLectureSearchApi;
        this.lectureUnitApi = lectureUnitApi;
        this.templateService = templateService;
        this.chatClient = chatClient;
    }

    /**
     * Returns whether competency-graph mode is available (Atlas module must be enabled).
     *
     * @return {@code true} if the Atlas module is present and competency APIs are available, {@code false} otherwise
     */
    public boolean isAvailable() {
        return courseCompetencyApi.isPresent();
    }

    /**
     * Holds enriched context about a set of selected competencies derived from the graph.
     *
     * @param competencies    the competencies in the selected set
     * @param relations       relations involving the selected competencies (ASSUMES / EXTENDS / MATCHES)
     * @param lectureSnippets relevant lecture content snippets extracted from linked units
     */
    public record CompetencyContext(List<CourseCompetency> competencies, Set<CompetencyRelation> relations, List<String> lectureSnippets) {
    }

    /**
     * Resolves the selected competencies and builds enriched context for the LLM prompt.
     *
     * @param courseId      the course to load competencies from
     * @param competencyIds the IDs of the selected competencies
     * @return enriched context for the LLM prompt
     */
    public CompetencyContext computeContext(long courseId, List<Long> competencyIds) {
        CourseCompetencyApi api = courseCompetencyApi
                .orElseThrow(() -> new BadRequestAlertException("Competency-graph mode requires the Atlas module", "QuizQuestionGeneration", "atlasNotEnabled"));
        List<CourseCompetency> allCompetencies = new ArrayList<>(api.findAllForCourse(courseId));

        Map<Long, CourseCompetency> competencyById = new HashMap<>();
        for (CourseCompetency c : allCompetencies) {
            if (c.getId() != null) {
                competencyById.put(c.getId(), c);
            }
        }

        List<CourseCompetency> selected = competencyIds.stream().map(competencyById::get).filter(Objects::nonNull).toList();

        Set<Long> distinctRequestedIds = competencyIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> selectedIds = selected.stream().map(CourseCompetency::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> missingIds = distinctRequestedIds.stream().filter(id -> !selectedIds.contains(id)).collect(Collectors.toSet());
        if (!missingIds.isEmpty()) {
            throw new BadRequestAlertException("Competencies not found in course " + courseId + ": " + missingIds, "QuizQuestionGeneration", "competencyNotFound");
        }

        Set<CompetencyRelation> relations = competencyRelationApi.map(relApi -> relApi.findRelationsInvolvingCompetencies(courseId, selectedIds)).orElse(Set.of());

        List<String> contextSnippets = gatherContextSnippets(courseId, selected);

        return new CompetencyContext(selected, relations, contextSnippets);
    }

    // Collects context snippets from three sources: Pyris semantic search, text units directly
    // linked to competencies, and LLM-summarized exercises linked to competencies.
    private List<String> gatherContextSnippets(long courseId, List<CourseCompetency> selected) {
        if (selected.isEmpty()) {
            return List.of();
        }

        List<String> snippets = new ArrayList<>();

        // Semantic search via Pyris: retrieves pre-indexed lecture content most relevant to the
        // selected competency titles, scoped to this course via the courseIds filter.
        if (irisLectureSearchApi.isPresent()) {
            String query = selected.stream().map(CourseCompetency::getTitle).filter(Objects::nonNull).collect(Collectors.joining(", "));
            if (!query.isBlank()) {
                irisLectureSearchApi.get().searchLectures(query, LECTURE_SEARCH_LIMIT, List.of(courseId)).stream()
                        .map(r -> formatSnippet(r.lectureName(), r.lectureUnitName(), r.snippet())).forEach(snippets::add);
            }
        }

        Set<Long> selectedIds = selected.stream().map(CourseCompetency::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (competencyRelationApi.isPresent() && !selectedIds.isEmpty()) {
            // Text units are fetched directly
            if (lectureUnitApi.isPresent()) {
                Set<Long> linkedUnitIds = competencyRelationApi.get().findLectureUnitIdsByCompetencyIds(selectedIds);
                if (!linkedUnitIds.isEmpty()) {
                    lectureUnitApi.get().findAllByIds(linkedUnitIds).stream().filter(unit -> unit instanceof TextUnit).map(unit -> (TextUnit) unit)
                            .filter(tu -> tu.getContent() != null && !tu.getContent().isBlank()).map(tu -> formatSnippet(tu.getLecture().getTitle(), tu.getName(), tu.getContent()))
                            .forEach(snippets::add);
                }
            }

            // Exercises are passed through an intermediate LLM call that extracts core challenges
            // and learning objectives, avoiding raw problem statement text in the final prompt.
            // Capped at MAX_EXERCISE_SUMMARIES to bound the number of synchronous LLM calls.
            competencyRelationApi.get().findExercisesByCompetencyIds(selectedIds).stream().filter(ex -> ex.getProblemStatement() != null && !ex.getProblemStatement().isBlank())
                    .limit(MAX_EXERCISE_SUMMARIES).map(this::summarizeExercise).filter(Objects::nonNull).forEach(snippets::add);
        }

        return snippets.stream().distinct().toList();
    }

    @Nullable
    private String summarizeExercise(Exercise exercise) {
        if (chatClient == null || templateService.isEmpty()) {
            log.debug("Skipping exercise [{}] in context: LLM client or template service not available", exercise.getId());
            return null;
        }

        String solutionSection = "";
        String solution = extractSolution(exercise);
        if (solution != null && !solution.isBlank()) {
            solutionSection = "\n### Solution\n" + solution + "\n";
        }

        String systemPrompt = templateService.get().render(PROMPT_EXTRACT_EXERCISE_INSIGHTS_SYSTEM, Map.of());
        String userPrompt = templateService.get().render(PROMPT_EXTRACT_EXERCISE_INSIGHTS_USER, Map.of("exerciseTitle", exercise.getTitle() != null ? exercise.getTitle() : "",
                "problemStatement", exercise.getProblemStatement(), "solutionSection", solutionSection));

        try {
            String insights = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
            if (insights == null || insights.isBlank()) {
                log.warn("Empty insights response for exercise [{}], omitting from context", exercise.getId());
                return null;
            }
            return formatSnippet("Exercise: " + exercise.getTitle(), insights);
        }
        catch (Exception e) {
            log.warn("Failed to extract exercise insights for exercise [{}], omitting from context: {}", exercise.getId(), e.getMessage());
            return null;
        }
    }

    private static String formatSnippet(String header, String content) {
        return "[" + header + "]\n" + content;
    }

    private static String formatSnippet(String headerPart1, String headerPart2, String content) {
        return formatSnippet(headerPart1 + " – " + headerPart2, content);
    }

    private String extractSolution(Exercise exercise) {
        if (exercise instanceof TextExercise te) {
            return te.getExampleSolution();
        }
        if (exercise instanceof FileUploadExercise fu) {
            return fu.getExampleSolution();
        }
        if (exercise instanceof ModelingExercise me) {
            return me.getExampleSolutionExplanation();
        }
        return null;
    }
}
