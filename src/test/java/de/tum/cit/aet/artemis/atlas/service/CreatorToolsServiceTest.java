package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import de.tum.cit.aet.artemis.atlas.service.OrchestratorToolContextKeys.AppliedActionsBuffer;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyAtlasMLNotificationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyValidationService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

/** Unit tests for {@link CreatorToolsService}; relocated from the former monolithic OrchestratorToolsServiceTest. */
@ExtendWith(MockitoExtension.class)
class CreatorToolsServiceTest {

    private static final long COURSE_ID = 42L;

    private static final String JUSTIFICATION = "Evidence test passed: solution demonstrates the competency stand-alone (band 1.0).";

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private CompetencyService competencyService;

    @Mock
    private CompetencyAtlasMLNotificationService atlasMLNotificationService;

    private final CompetencyValidationService competencyValidator = new CompetencyValidationService();

    private CreatorToolsService service;

    private ToolContext toolContext;

    private List<AppliedActionDTO> appliedActions;

    private AppliedActionsBuffer appliedActionsBuffer;

    @BeforeEach
    void setUp() {
        service = new CreatorToolsService(new ObjectMapper(), courseRepository, competencyService, competencyValidator, atlasMLNotificationService);
        appliedActions = Collections.synchronizedList(new ArrayList<>());
        appliedActionsBuffer = new AppliedActionsBuffer(appliedActions);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put(OrchestratorToolContextKeys.COURSE_ID_KEY, COURSE_ID);
        ctx.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, appliedActionsBuffer);
        toolContext = new ToolContext(ctx);
    }

    @Test
    void createCompetency_validInput_persistsAndLogsActionWithJustification() {
        Course course = new Course();
        course.setId(COURSE_ID);
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        Competency persisted = new Competency("Sorting Algorithms", "Understand sorting basics.", null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.UNDERSTAND,
                false);
        persisted.setId(101L);
        when(competencyService.createCompetencies(any(), eq(course))).thenReturn(List.of(persisted));

        String result = service.createCompetency("Sorting Algorithms", "Understand sorting basics.", "UNDERSTAND", JUSTIFICATION, toolContext);

        assertThat(result).contains("\"id\":101").contains("Sorting Algorithms").contains("UNDERSTAND");
        assertThat(appliedActions).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(AppliedActionDTO.ActionType.CREATE);
            assertThat(action.competencyId()).isEqualTo(101L);
            assertThat(action.competencyTitle()).isEqualTo("Sorting Algorithms");
            assertThat(action.justification()).isEqualTo(JUSTIFICATION);
        });
        // The successful create must mirror the new competency to AtlasML (production sends UPDATE for creation).
        verify(atlasMLNotificationService).notifyAtlasML(List.of(persisted), OperationTypeDTO.UPDATE, "orchestrator competency creation");
    }

    @Test
    void createCompetency_missingJustification_returnsError() {
        String result = service.createCompetency("Title", "Desc", "APPLY", "   ", toolContext);

        assertThat(result).contains("justification is required");
        verify(competencyService, never()).createCompetencies(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void createCompetency_invalidTaxonomy_returnsError() {
        String result = service.createCompetency("Title", "Description", "WIZARD", JUSTIFICATION, toolContext);

        assertThat(result).contains("error").contains("taxonomy");
        assertThat(appliedActions).isEmpty();
        verify(competencyService, never()).createCompetencies(any(), any());
    }

    @Test
    void createCompetency_missingCourseContext_returnsError() {
        String result = service.createCompetency("Title", "Desc", "APPLY", JUSTIFICATION, new ToolContext(Map.of()));

        assertThat(result).contains("No course context");
        verify(competencyService, never()).createCompetencies(any(), any());
    }

    @Test
    void createCompetency_justificationTooLong_returnsErrorAndDoesNotPersist() {
        String tooLong = "x".repeat(501);

        String result = service.createCompetency("Title", "Desc", "APPLY", tooLong, toolContext);

        assertThat(result).contains("justification must be at most 500 characters");
        verify(competencyService, never()).createCompetencies(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void createCompetency_titleTooLong_returnsErrorAndDoesNotPersist() {
        String tooLong = "x".repeat(256);

        String result = service.createCompetency(tooLong, "Desc", "APPLY", JUSTIFICATION, toolContext);

        assertThat(result).contains("title must be at most 255 characters");
        verify(competencyService, never()).createCompetencies(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void createCompetency_descriptionTooLong_returnsErrorAndDoesNotPersist() {
        String tooLong = "x".repeat(10_001);

        String result = service.createCompetency("Title", tooLong, "APPLY", JUSTIFICATION, toolContext);

        assertThat(result).contains("description must be at most 10000 characters");
        verify(competencyService, never()).createCompetencies(any(), any());
        assertThat(appliedActions).isEmpty();
    }

    @Test
    void writeQuotaReached_furtherCreateCallsReturnError() {
        // Saturate the shared reservation counter at the cap. A further create must short-circuit
        // before touching repositories. Going via tryReserveSlot mirrors the production write path.
        for (int i = 0; i < OrchestratorToolContextKeys.MAX_WRITE_CALLS; i++) {
            assertThat(appliedActionsBuffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isTrue();
        }
        assertThat(appliedActionsBuffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isFalse();

        String result = service.createCompetency("Title", "Desc", "APPLY", JUSTIFICATION, toolContext);

        assertThat(result).contains("Write tool call cap (" + OrchestratorToolContextKeys.MAX_WRITE_CALLS + ")");
        verify(competencyService, never()).createCompetencies(any(), any());
    }

    @Test
    void writeQuota_allowsFullCapWorthOfAppendsBeforeBlocking() {
        Course course = new Course();
        course.setId(COURSE_ID);
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        Competency created = new Competency("Created", "Desc", null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.UNDERSTAND, false);
        created.setId(900L);
        when(competencyService.createCompetencies(any(), eq(course))).thenReturn(List.of(created));

        for (int i = 0; i < OrchestratorToolContextKeys.MAX_WRITE_CALLS; i++) {
            String result = service.createCompetency("Title " + i, "Desc", "UNDERSTAND", JUSTIFICATION, toolContext);
            assertThat(result).as("call %d should succeed", i).doesNotContain("Write tool call cap");
        }

        assertThat(appliedActions).hasSize(OrchestratorToolContextKeys.MAX_WRITE_CALLS);
        String overflow = service.createCompetency("Overflow", "Desc", "UNDERSTAND", JUSTIFICATION, toolContext);
        assertThat(overflow).contains("Write tool call cap (" + OrchestratorToolContextKeys.MAX_WRITE_CALLS + ")");
    }

    @Test
    void appendAction_concurrentCallers_doNotLoseEntries() throws Exception {
        // appliedActions is a synchronized list, so N concurrent create calls must all show up.
        Course course = new Course();
        course.setId(COURSE_ID);
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(competencyService.createCompetencies(any(), eq(course))).thenAnswer(inv -> {
            Competency persisted = new Competency("Concurrent", "Desc", null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, CompetencyTaxonomy.APPLY, false);
            persisted.setId(System.nanoTime());
            return List.of(persisted);
        });

        int callers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.createCompetency("Concurrent", "Desc", "APPLY", JUSTIFICATION, toolContext);
                }));
            }
            start.countDown();
        }
        finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        // get() each future so an exception thrown inside a worker thread fails the test instead of being swallowed.
        for (Future<String> future : futures) {
            assertThat(future.get()).doesNotContain("Write tool call cap");
        }
        assertThat(appliedActions).hasSize(callers);
    }
}
