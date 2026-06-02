package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.hyperion.service.HyperionCompetencyContextService.CompetencyContext;
import de.tum.cit.aet.artemis.iris.api.IrisLectureSearchApi;
import de.tum.cit.aet.artemis.iris.dto.IrisLectureSnippetDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class HyperionCompetencyContextServiceTest {

    @Mock
    private CourseCompetencyApi courseCompetencyApi;

    @Mock
    private CompetencyRelationApi competencyRelationApi;

    @Mock
    private IrisLectureSearchApi irisLectureSearchApi;

    @Mock
    private LectureUnitApi lectureUnitApi;

    @Mock
    private ChatModel chatModel;

    private HyperionCompetencyContextService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        var templateService = new HyperionPromptTemplateService();
        ChatClient chatClient = ChatClient.create(chatModel);
        service = new HyperionCompetencyContextService(Optional.of(courseCompetencyApi), Optional.of(competencyRelationApi), Optional.of(irisLectureSearchApi),
                Optional.of(lectureUnitApi), Optional.of(templateService), chatClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void isAvailable_returnsTrueWhenAtlasEnabled() {
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_returnsFalseWhenAtlasDisabled() {
        var disabledService = new HyperionCompetencyContextService(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), null);
        assertThat(disabledService.isAvailable()).isFalse();
    }

    @Test
    void computeContext_throwsBadRequestWhenAtlasNotEnabled() {
        var disabledService = new HyperionCompetencyContextService(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), null);
        assertThatThrownBy(() -> disabledService.computeContext(1L, List.of(1L))).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("Atlas module");
    }

    @Test
    void computeContext_returnsEmptyWhenNoCompetenciesMatchIds() {
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of());
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());

        CompetencyContext context = service.computeContext(42L, List.of(999L));

        assertThat(context.competencies()).isEmpty();
        assertThat(context.lectureSnippets()).isEmpty();
    }

    @Test
    void computeContext_includesPyrisSearchResults() {
        Competency competency = makeCompetency(1L, "Data Structures");
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList()))
                .thenReturn(List.of(makeSnippet("Algorithms", "Sorting Unit", "Merge sort is divide-and-conquer.")));

        CompetencyContext context = service.computeContext(42L, List.of(1L));

        assertThat(context.lectureSnippets()).hasSize(1);
        assertThat(context.lectureSnippets().getFirst()).contains("Algorithms – Sorting Unit").contains("Merge sort");
    }

    @Test
    void computeContext_skipsPyrisWhenAllCompetencyTitlesAreNull() {
        Competency competency = makeCompetency(1L, null);
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of());

        service.computeContext(42L, List.of(1L));

        verify(irisLectureSearchApi, never()).searchLectures(anyString(), anyInt(), anyList());
    }

    @Test
    void computeContext_includesTextUnitSnippets() {
        Competency competency = makeCompetency(1L, "OOP");
        when(courseCompetencyApi.findAllForCourse(10L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(Set.of(1L))).thenReturn(Set.of(5L));
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());
        when(lectureUnitApi.findAllByIds(Set.of(5L))).thenReturn(List.of(makeTextUnit("Inheritance", "OOP Basics", "Inheritance allows one class to extend another.")));

        CompetencyContext context = service.computeContext(10L, List.of(1L));

        assertThat(context.lectureSnippets()).hasSize(1);
        assertThat(context.lectureSnippets().getFirst()).contains("OOP Basics – Inheritance").contains("Inheritance allows");
    }

    @Test
    void computeContext_skipsTextUnitWithBlankContent() {
        Competency competency = makeCompetency(1L, "OOP");
        when(courseCompetencyApi.findAllForCourse(10L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(Set.of(1L))).thenReturn(Set.of(5L));
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());
        when(lectureUnitApi.findAllByIds(Set.of(5L))).thenReturn(List.of(makeTextUnit("Empty Unit", "Lecture", "   ")));

        CompetencyContext context = service.computeContext(10L, List.of(1L));

        assertThat(context.lectureSnippets()).isEmpty();
    }

    @Test
    void computeContext_includesExerciseSummaryWhenLlmAvailable() {
        Competency competency = makeCompetency(1L, "Algorithms");
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());

        TextExercise exercise = new TextExercise();
        exercise.setId(10L);
        exercise.setTitle("Sorting Exercise");
        exercise.setProblemStatement("Implement merge sort.");
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of(exercise));
        when(chatModel.call(any(Prompt.class)))
                .thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("• Divide-and-conquer approach\n• O(n log n) time complexity")))));

        CompetencyContext context = service.computeContext(42L, List.of(1L));

        assertThat(context.lectureSnippets()).hasSize(1);
        assertThat(context.lectureSnippets().getFirst()).contains("Exercise: Sorting Exercise").contains("Divide-and-conquer");
    }

    @Test
    void computeContext_skipsExerciseSummaryWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionCompetencyContextService(Optional.of(courseCompetencyApi), Optional.of(competencyRelationApi), Optional.of(irisLectureSearchApi),
                Optional.of(lectureUnitApi), Optional.of(new HyperionPromptTemplateService()), null);

        Competency competency = makeCompetency(1L, "Algorithms");
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());

        TextExercise exercise = new TextExercise();
        exercise.setId(10L);
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Implement sort.");
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of(exercise));

        CompetencyContext context = serviceWithNullClient.computeContext(42L, List.of(1L));

        assertThat(context.lectureSnippets()).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void computeContext_gracefullyHandlesLlmFailureForExercise() {
        Competency competency = makeCompetency(1L, "Algorithms");
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());

        TextExercise exercise = new TextExercise();
        exercise.setId(10L);
        exercise.setTitle("Sorting");
        exercise.setProblemStatement("Implement sort.");
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of(exercise));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM unavailable"));

        CompetencyContext context = service.computeContext(42L, List.of(1L));

        // Exercise is silently skipped; no exception propagated to caller
        assertThat(context.lectureSnippets()).isEmpty();
    }

    @Test
    void computeContext_deduplicatesIdenticalSnippets() {
        Competency competency = makeCompetency(1L, "Algorithms");
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of(5L, 6L));
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());
        // Two text units with identical formatted output
        when(lectureUnitApi.findAllByIds(anySet())).thenReturn(
                List.of(makeTextUnit("Sorting", "Lecture: Basics", "Merge sort splits the array."), makeTextUnit("Sorting", "Lecture: Basics", "Merge sort splits the array.")));

        CompetencyContext context = service.computeContext(42L, List.of(1L));

        assertThat(context.lectureSnippets()).hasSize(1);
    }

    @Test
    void computeContext_includesTextExerciseSolutionInLlmPrompt() {
        Competency competency = makeCompetency(1L, "Writing");
        when(courseCompetencyApi.findAllForCourse(1L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());

        TextExercise exercise = new TextExercise();
        exercise.setId(1L);
        exercise.setTitle("Essay");
        exercise.setProblemStatement("Write about algorithms.");
        exercise.setExampleSolution("A good essay has an introduction, body, and conclusion.");
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of(exercise));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("• Essay structure")))));

        service.computeContext(1L, List.of(1L));

        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getInstructions().stream().map(msg -> msg.getText()).collect(Collectors.joining("\n"));
        assertThat(promptText).contains("A good essay has an introduction, body, and conclusion.");
        assertThat(promptText).contains("### Solution");
    }

    @Test
    void computeContext_includesFileUploadExerciseSolutionInLlmPrompt() {
        Competency competency = makeCompetency(1L, "Lab");
        when(courseCompetencyApi.findAllForCourse(1L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());

        FileUploadExercise exercise = new FileUploadExercise();
        exercise.setId(2L);
        exercise.setTitle("Lab Report");
        exercise.setProblemStatement("Write a lab report.");
        exercise.setExampleSolution("A lab report should include methodology and results.");
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of(exercise));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("• Lab structure")))));

        service.computeContext(1L, List.of(1L));

        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getInstructions().stream().map(msg -> msg.getText()).collect(Collectors.joining("\n"));
        assertThat(promptText).contains("A lab report should include methodology and results.");
    }

    @Test
    void computeContext_includesModelingExerciseSolutionExplanationInLlmPrompt() {
        Competency competency = makeCompetency(1L, "UML");
        when(courseCompetencyApi.findAllForCourse(1L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());

        ModelingExercise exercise = new ModelingExercise();
        exercise.setId(3L);
        exercise.setTitle("Class Diagram");
        exercise.setProblemStatement("Model a library system.");
        exercise.setExampleSolutionExplanation("The diagram should include Book, Member, and Loan classes.");
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of(exercise));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("• UML class design")))));

        service.computeContext(1L, List.of(1L));

        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getInstructions().stream().map(msg -> msg.getText()).collect(Collectors.joining("\n"));
        assertThat(promptText).contains("Book, Member, and Loan classes");
    }

    @Test
    void computeContext_skipsExerciseWithNullProblemStatement() {
        Competency competency = makeCompetency(1L, "Algorithms");
        when(courseCompetencyApi.findAllForCourse(42L)).thenReturn(Set.of(competency));
        when(competencyRelationApi.findRelationsInvolvingCompetencies(anyLong(), anySet())).thenReturn(Set.of());
        when(competencyRelationApi.findLectureUnitIdsByCompetencyIds(anySet())).thenReturn(Set.of());
        when(irisLectureSearchApi.searchLectures(anyString(), anyInt(), anyList())).thenReturn(List.of());

        TextExercise exercise = new TextExercise();
        exercise.setId(10L);
        exercise.setTitle("No Problem Statement");
        exercise.setProblemStatement(null);
        when(competencyRelationApi.findExercisesByCompetencyIds(anySet())).thenReturn(Set.of(exercise));

        CompetencyContext context = service.computeContext(42L, List.of(1L));

        assertThat(context.lectureSnippets()).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
    }

    // --- Helper methods ---

    private static Competency makeCompetency(long id, String title) {
        Competency c = new Competency();
        c.setId(id);
        c.setTitle(title);
        return c;
    }

    private static TextUnit makeTextUnit(String unitName, String lectureTitle, String content) {
        Lecture lecture = new Lecture();
        lecture.setTitle(lectureTitle);
        TextUnit unit = new TextUnit();
        unit.setName(unitName);
        unit.setLecture(lecture);
        unit.setContent(content);
        return unit;
    }

    private static IrisLectureSnippetDTO makeSnippet(String lectureName, String unitName, String snippet) {
        return new IrisLectureSnippetDTO(lectureName, unitName, snippet);
    }
}
