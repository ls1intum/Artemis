package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.OrchestratorToolContextKeys.AppliedActionsBuffer;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyAtlasMLNotificationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyValidationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.course.domain.Course;

/** Unit tests for {@link EditorToolsService}; relocated from the former monolithic OrchestratorToolsServiceTest. */
@ExtendWith(MockitoExtension.class)
class EditorToolsServiceTest {

    private static final long COURSE_ID = 42L;

    private static final long OTHER_COURSE_ID = 99L;

    private static final String JUSTIFICATION = "Evidence test passed: solution demonstrates the competency stand-alone (band 1.0).";

    @Mock
    private CourseCompetencyRepository courseCompetencyRepository;

    @Mock
    private CourseCompetencyService courseCompetencyService;

    @Mock
    private CompetencyAtlasMLNotificationService atlasMLNotificationService;

    private final CompetencyValidationService competencyValidator = new CompetencyValidationService();

    private EditorToolsService service;

    private ToolContext toolContext;

    private List<AppliedActionDTO> appliedActions;

    private AppliedActionsBuffer appliedActionsBuffer;

    @BeforeEach
    void setUp() {
        service = new EditorToolsService(new ObjectMapper(), courseCompetencyRepository, courseCompetencyService, competencyValidator, atlasMLNotificationService);
        appliedActions = Collections.synchronizedList(new ArrayList<>());
        appliedActionsBuffer = new AppliedActionsBuffer(appliedActions);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(OrchestratorToolContextKeys.COURSE_ID_KEY, COURSE_ID);
        ctx.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, appliedActionsBuffer);
        toolContext = new ToolContext(ctx);
    }

    @Test
    void editCompetency_titleChange_updatesAndLogsActionWithJustification() {
        CourseCompetency existing = newCompetency(10L, "Old Title", "Desc", CompetencyTaxonomy.APPLY, courseWithId(COURSE_ID));
        when(courseCompetencyRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(courseCompetencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = service.editCompetency(10L, "New Title", null, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("New Title").contains("\"changed\"");
        assertThat(existing.getTitle()).isEqualTo("New Title");
        assertThat(appliedActions).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AppliedActionDTO.ActionType.EDIT);
            assertThat(a.justification()).isEqualTo(JUSTIFICATION);
        });
        // The successful edit must mirror the updated competency to AtlasML as an UPDATE.
        verify(atlasMLNotificationService).notifyAtlasML(List.of((Competency) existing), OperationTypeDTO.UPDATE, "orchestrator competency update");
    }

    @Test
    void editCompetency_missingJustification_returnsError() {
        String result = service.editCompetency(10L, "New Title", null, null, null, toolContext);

        assertThat(result).contains("justification is required");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void editCompetency_wrongCourse_returnsErrorAndDoesNotSave() {
        CourseCompetency existing = newCompetency(10L, "Old Title", "Desc", CompetencyTaxonomy.APPLY, courseWithId(OTHER_COURSE_ID));
        when(courseCompetencyRepository.findById(10L)).thenReturn(Optional.of(existing));

        String result = service.editCompetency(10L, "New", null, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("does not belong to the current course");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void editCompetency_noFieldsChanged_returnsNoopWithoutSaving() {
        CourseCompetency existing = newCompetency(10L, "Keep", "Desc", CompetencyTaxonomy.APPLY, courseWithId(COURSE_ID));
        when(courseCompetencyRepository.findById(10L)).thenReturn(Optional.of(existing));

        String result = service.editCompetency(10L, null, null, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("noop");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void editCompetency_descriptionIsTrimmed() {
        CourseCompetency existing = newCompetency(10L, "Old Title", "Original description", CompetencyTaxonomy.APPLY, courseWithId(COURSE_ID));
        when(courseCompetencyRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(courseCompetencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String result = service.editCompetency(10L, null, "  New description.  ", null, JUSTIFICATION, toolContext);

        assertThat(result).contains("description");
        assertThat(existing.getDescription()).isEqualTo("New description.");
    }

    @Test
    void editCompetency_titleTooLong_returnsErrorAndDoesNotSave() {
        String tooLong = "x".repeat(256);

        String result = service.editCompetency(10L, tooLong, null, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("title must be at most 255 characters");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void editCompetency_descriptionTooLong_returnsErrorAndDoesNotSave() {
        String tooLong = "x".repeat(10_001);

        String result = service.editCompetency(10L, null, tooLong, null, JUSTIFICATION, toolContext);

        assertThat(result).contains("description must be at most 10000 characters");
        verify(courseCompetencyRepository, never()).save(any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void deleteCompetency_validCompetency_delegatesToServiceWithJustification() {
        Course course = courseWithId(COURSE_ID);
        CourseCompetency competency = newCompetency(7L, "Remove Me", "Desc", CompetencyTaxonomy.UNDERSTAND, course);
        when(courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(7L)).thenReturn(Optional.of(competency));

        String result = service.deleteCompetency(7L, JUSTIFICATION, toolContext);

        assertThat(result).contains("\"deletedId\":7");
        verify(courseCompetencyService).deleteCourseCompetency(competency, course);
        assertThat(appliedActions).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(AppliedActionDTO.ActionType.DELETE);
            assertThat(a.justification()).isEqualTo(JUSTIFICATION);
        });
        // The successful delete must mirror the removal to AtlasML as a DELETE (production sends a detached
        // snapshot copy of the competency, so the list contents are matched by type/message, not identity).
        verify(atlasMLNotificationService).notifyAtlasML(anyList(), eq(OperationTypeDTO.DELETE), eq("orchestrator competency deletion"));
    }

    @Test
    void deleteCompetency_wrongCourse_returnsErrorWithoutDelete() {
        Course course = courseWithId(OTHER_COURSE_ID);
        CourseCompetency competency = newCompetency(7L, "Remove Me", "Desc", CompetencyTaxonomy.UNDERSTAND, course);
        when(courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(7L)).thenReturn(Optional.of(competency));

        String result = service.deleteCompetency(7L, JUSTIFICATION, toolContext);

        assertThat(result).contains("does not belong to the current course");
        verify(courseCompetencyService, never()).deleteCourseCompetency(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void deleteCompetency_missingJustification_returnsError() {
        String result = service.deleteCompetency(7L, null, toolContext);

        assertThat(result).contains("justification is required");
        verify(courseCompetencyService, never()).deleteCourseCompetency(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void writeQuotaReached_furtherWriteToolCallsReturnError() {
        for (int i = 0; i < OrchestratorToolContextKeys.MAX_WRITE_CALLS; i++) {
            assertThat(appliedActionsBuffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isTrue();
        }
        assertThat(appliedActionsBuffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isFalse();

        String editResult = service.editCompetency(10L, "Title", null, null, JUSTIFICATION, toolContext);
        String deleteResult = service.deleteCompetency(10L, JUSTIFICATION, toolContext);

        assertThat(editResult).contains("Write tool call cap (" + OrchestratorToolContextKeys.MAX_WRITE_CALLS + ")");
        assertThat(deleteResult).contains("Write tool call cap (" + OrchestratorToolContextKeys.MAX_WRITE_CALLS + ")");
        verify(courseCompetencyRepository, never()).save(any());
        verify(courseCompetencyService, never()).deleteCourseCompetency(any(), any());
    }

    private static Course courseWithId(long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    private static CourseCompetency newCompetency(long id, String title, String description, CompetencyTaxonomy taxonomy, Course course) {
        Competency competency = new Competency(title, description, null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, taxonomy, false);
        competency.setId(id);
        competency.setCourse(course);
        return competency;
    }
}
