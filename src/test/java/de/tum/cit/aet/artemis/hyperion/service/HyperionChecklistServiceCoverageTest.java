package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.StandardizedCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.hyperion.domain.ChecklistSection;
import de.tum.cit.aet.artemis.hyperion.domain.DifficultyDelta;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistActionRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ChecklistAnalysisResponseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import io.micrometer.observation.ObservationRegistry;

class HyperionChecklistServiceCoverageTest {

    private final ChatModel model = mock(ChatModel.class);

    private final HyperionPromptTemplateService templates = mock(HyperionPromptTemplateService.class);

    private final ProgrammingExerciseTaskTestRepository tasks = mock(ProgrammingExerciseTaskTestRepository.class);

    private final ProgrammingExerciseTestRepository exercises = mock(ProgrammingExerciseTestRepository.class);

    private final StandardizedCompetencyApi catalog = mock(StandardizedCompetencyApi.class);

    private final CourseCompetencyApi courseCompetencies = mock(CourseCompetencyApi.class);

    private final Map<String, Map<String, String>> inputs = new ConcurrentHashMap<>();

    private final Map<String, String> responses = new ConcurrentHashMap<>();

    private HyperionChecklistService service;

    @BeforeEach
    void setup() {
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(templates.render(anyString(), anyMap())).thenAnswer(invocation -> {
            String template = invocation.getArgument(0);
            inputs.put(template, new HashMap<>(invocation.getArgument(1)));
            return template;
        });
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String text = responses.getOrDefault(prompt.getUserMessage().getText(), "{}");
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        });
        when(catalog.getAllForTreeView()).thenReturn(List.of());
        when(courseCompetencies.findAllForCourse(7L)).thenReturn(Set.of());
        service = createService(true);
    }

    private HyperionChecklistService createService(boolean atlasEnabled) {
        return new HyperionChecklistService(ChatClient.create(model), templates, ObservationRegistry.create(), atlasEnabled ? Optional.of(catalog) : Optional.empty(),
                atlasEnabled ? Optional.of(courseCompetencies) : Optional.empty(), tasks, exercises, new JsonMapper(), mock(LLMTokenUsageService.class),
                mock(UserTestRepository.class));
    }

    private ChecklistAnalysisResponseDTO section(ChecklistSection section, String response, String difficulty) throws Exception {
        responses.put("/prompts/hyperion/checklist_" + section.name().toLowerCase(java.util.Locale.ROOT) + ".st", response);
        return service.analyzeSection(new ChecklistAnalysisRequestDTO("Implement sorting", difficulty, null, null), section, 7L).get(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({ "EASY,EASY,MATCH,true", "EASY,HARD,HIGHER,false", "HARD,EASY,LOWER,false", "MEDIUM,UNKNOWN,UNKNOWN,false", "invalid,MEDIUM,UNKNOWN,false",
            "MEDIUM,invalid,UNKNOWN,false", "MEDIUM,,UNKNOWN,false", ",MEDIUM,UNKNOWN,false" })
    void difficultyComparesDeclaredAndSuggestedAndDefaultsCounts(String declared, String suggested, DifficultyDelta delta, boolean matches) throws Exception {
        String json = new JsonMapper().writeValueAsString(Map.of("suggested", suggested == null ? "" : suggested, "reasoning", "Counted the tasks", "confidence", 0.8));
        var result = section(ChecklistSection.DIFFICULTY, json, declared);
        assertThat(result.difficultyAssessment().delta()).isEqualTo(delta);
        assertThat(result.difficultyAssessment().matchesDeclared()).isEqualTo(matches);
        assertThat(result.difficultyAssessment().taskCount()).isZero();
        assertThat(result.difficultyAssessment().testCount()).isZero();
        assertThat(result.difficultyAssessment().reasoning()).isEqualTo("Counted the tasks");
        assertThat(result.inferredCompetencies()).isNull();
        assertThat(inputs.get("/prompts/hyperion/checklist_difficulty.st")).containsEntry("language", "JAVA").containsEntry("competency_catalog", "[]");
        verify(catalog, never()).getAllForTreeView();
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "garbage", "{", "```json\n{\"suggested\":\"HARD\",\"taskCount\":3,\"testCount\":8}\n```",
            "Explanation {\"suggested\":\"HARD\",\"taskCount\":3,\"testCount\":8} done" })
    void difficultyExtractsJsonAndReturnsDocumentedFallback(String json) throws Exception {
        var result = section(ChecklistSection.DIFFICULTY, json, "MEDIUM").difficultyAssessment();
        if (json.contains("HARD")) {
            assertThat(result.delta()).isEqualTo(DifficultyDelta.HIGHER);
            assertThat(result.taskCount()).isEqualTo(3);
            assertThat(result.testCount()).isEqualTo(8);
        }
        else {
            assertThat(result.delta()).isEqualTo(DifficultyDelta.UNKNOWN);
            assertThat(result.reasoning()).isIn("Analysis failed", "AI returned no response");
        }
    }

    @Test
    void competenciesNormalizeWeightsAndIgnoreUnknownTaxonomies() throws Exception {
        var result = section(ChecklistSection.COMPETENCIES, """
                {"competencies":[
                  {"competencyTitle":"Loops","taxonomyLevel":"apply","confidence":0.5},
                  {"competencyTitle":"Explain","taxonomyLevel":"UNDERSTAND"},
                  {"taxonomyLevel":"invalid","confidence":1},
                  {"taxonomyLevel":""},{"confidence":0.8}]}
                """, null);
        assertThat(result.inferredCompetencies()).hasSize(5);
        assertThat(result.inferredCompetencies().getFirst().relatedTaskNames()).isEmpty();
        assertThat(result.bloomRadar().apply()).isEqualTo(0.5);
        assertThat(result.bloomRadar().understand()).isEqualTo(0.5);
        assertThat(result.bloomRadar().remember()).isZero();
        assertThat(inputs.get("/prompts/hyperion/checklist_competencies.st")).containsEntry("task_names", "(no tasks detected)").containsEntry("declared_difficulty",
                "NOT_DECLARED");
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "{}", "{\"competencies\":null}", "{\"competencies\":[]}", "{\"competencies\":[{\"taxonomyLevel\":\"APPLY\",\"confidence\":0}]}" })
    void emptyAndZeroWeightCompetenciesProduceZeroRadar(String response) throws Exception {
        var result = section(ChecklistSection.COMPETENCIES, response, "");
        assertThat(result.bloomRadar().apply()).isZero();
        assertThat(result.bloomRadar().understand()).isZero();
    }

    @Test
    void qualityMapsLocationsAndUnknownEnumsWithoutInventingValues() throws Exception {
        var result = section(ChecklistSection.QUALITY, """
                {"issues":[{"category":"unknown","severity":"unknown","description":"Ambiguous","location":{"startLine":2,"endLine":4}},
                {"category":null,"severity":"","description":"Missing context"}]}
                """, "MEDIUM");
        assertThat(result.qualityIssues()).hasSize(2);
        assertThat(result.qualityIssues().getFirst().category()).isNull();
        assertThat(result.qualityIssues().getFirst().severity()).isNull();
        assertThat(result.qualityIssues().getFirst().location().startLine()).isEqualTo(2);
        assertThat(result.qualityIssues().getFirst().location().endLine()).isEqualTo(4);
        assertThat(result.qualityIssues().get(1).location()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "{}", "{\"issues\":null}", "not json" })
    void qualityRejectsUnusableResponses(String response) throws Exception {
        assertThat(section(ChecklistSection.QUALITY, response, null).qualityIssues()).isEmpty();
    }

    @Test
    void fullAnalysisCombinesIndependentFallbacksAndLoadsLanguageAndTaskNames() throws Exception {
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        when(exercises.findById(4L)).thenReturn(Optional.of(exercise));
        var task = new ProgrammingExerciseTask();
        task.setTaskName("Sorting");
        var empty = new ProgrammingExerciseTask();
        empty.setTaskName(" ");
        when(tasks.findByExerciseIdWithTestCases(4L)).thenReturn(Set.of(task, empty, new ProgrammingExerciseTask()));
        responses.put("/prompts/hyperion/checklist_difficulty.st", "{\"suggested\":\"EASY\"}");
        responses.put("/prompts/hyperion/checklist_quality.st", "broken");
        var result = service.analyzeChecklist(new ChecklistAnalysisRequestDTO("Sort", null, null, 4L), 7L).get(10, TimeUnit.SECONDS);
        assertThat(result.difficultyAssessment().suggested().name()).isEqualTo("EASY");
        assertThat(result.qualityIssues()).isEmpty();
        assertThat(result.inferredCompetencies()).isEmpty();
        assertThat(inputs.get("/prompts/hyperion/checklist_competencies.st")).containsEntry("language", "PYTHON").containsEntry("task_names", "Sorting");
    }

    @Test
    void contextFailureReturnsEmptyAnalysisForBothEntryPoints() throws Exception {
        when(exercises.findById(4L)).thenThrow(new IllegalStateException("unavailable"));
        var request = new ChecklistAnalysisRequestDTO("Sort", null, null, 4L);
        assertThat(service.analyzeChecklist(request, 7L).get()).isEqualTo(ChecklistAnalysisResponseDTO.empty());
        assertThat(service.analyzeSection(request, ChecklistSection.QUALITY, 7L).get()).isEqualTo(ChecklistAnalysisResponseDTO.empty());
    }

    @Test
    void catalogsAreSerializedAndReusedAcrossAnalyses() throws Exception {
        KnowledgeArea root = new KnowledgeArea();
        root.setTitle("Root");
        root.setShortTitle("R");
        KnowledgeArea child = new KnowledgeArea();
        child.setTitle("Child");
        StandardizedCompetency competency = new StandardizedCompetency();
        competency.setTitle("Recursion");
        competency.setTaxonomy(CompetencyTaxonomy.APPLY);
        child.setCompetencies(Set.of(competency));
        root.setChildren(Set.of(child));
        when(catalog.getAllForTreeView()).thenReturn(List.of(root));
        section(ChecklistSection.COMPETENCIES, "{}", null);
        section(ChecklistSection.COMPETENCIES, "{}", null);
        assertThat(inputs.get("/prompts/hyperion/checklist_competencies.st").get("competency_catalog")).contains("Recursion", "APPLY");
        verify(catalog, times(1)).getAllForTreeView();
        verify(courseCompetencies, times(1)).findAllForCourse(7L);
    }

    @Test
    void absentAtlasAndCourseLookupFailuresStillAllowAnalysis() throws Exception {
        service = createService(false);
        section(ChecklistSection.COMPETENCIES, "{}", null);
        assertThat(inputs.get("/prompts/hyperion/checklist_competencies.st")).containsEntry("competency_catalog", "[]").containsEntry("course_competencies", "[]");
        service = createService(true);
        when(courseCompetencies.findAllForCourse(7L)).thenThrow(new IllegalStateException("offline"));
        assertThat(section(ChecklistSection.COMPETENCIES, "{}", null).inferredCompetencies()).isEmpty();
        assertThat(inputs.get("/prompts/hyperion/checklist_competencies.st")).containsEntry("course_competencies", "[]");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ChecklistActionRequestDTO.ActionType.class)
    void actionBuildsInstructionsAndReportsChangedText(ChecklistActionRequestDTO.ActionType type) throws Exception {
        responses.put("/prompts/hyperion/checklist_action.st", "1: Updated statement");
        var context = new HashMap<String, String>();
        context.put("category", "CLARITY");
        context.put("suggestedFix", "say ```exactly```");
        context.put("reasoning", "clearer");
        context.put("targetDifficulty", "HARD");
        context.put(null, "discard");
        context.put(" ", "discard");
        context.put("x".repeat(101), "v".repeat(10001));
        context.put("nullable", null);
        var result = service.applyChecklistAction(new ChecklistActionRequestDTO(type, "Original", context), 7L).get(10, TimeUnit.SECONDS);
        assertThat(result.applied()).isTrue();
        assertThat(result.updatedProblemStatement()).isEqualTo("Updated statement");
        assertThat(result.summary()).isEqualTo(switch (type) {
            case FIX_QUALITY_ISSUE -> "Fixed quality issue: CLARITY";
            case FIX_ALL_QUALITY_ISSUES -> "Fixed all quality issues";
            case ADAPT_DIFFICULTY -> "Adapted difficulty to HARD";
        });
        String instructions = inputs.get("/prompts/hyperion/checklist_action.st").get("instructions");
        assertThat(instructions).isNotBlank();
        if (type == ChecklistActionRequestDTO.ActionType.FIX_QUALITY_ISSUE) {
            assertThat(instructions).contains("CLARITY", "``\\`exactly``\\`");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "Original" })
    void actionPreservesOriginalWhenResponseMissingOrUnchanged(String response) throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(response == null ? new ChatResponse(List.of()) : new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));
        var result = service.applyChecklistAction(new ChecklistActionRequestDTO(ChecklistActionRequestDTO.ActionType.FIX_QUALITY_ISSUE, "Original", null), 7L).get(10,
                TimeUnit.SECONDS);
        assertThat(result.applied()).isFalse();
        assertThat(result.updatedProblemStatement()).isEqualTo("Original");
    }

    @Test
    void actionFailureReturnsOriginalAndDefaultInstructionsContainPlaceholders() throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("offline"));
        var result = service.applyChecklistAction(new ChecklistActionRequestDTO(ChecklistActionRequestDTO.ActionType.ADAPT_DIFFICULTY, "Original", Map.of()), 7L).get();
        assertThat(result.updatedProblemStatement()).isEqualTo("Original");
        assertThat(result.applied()).isFalse();
        assertThat(result.summary()).isEqualTo("Action could not be applied");
        assertThat(inputs.get("/prompts/hyperion/checklist_action.st").get("instructions")).contains("MEDIUM", "unknown");
    }
}
