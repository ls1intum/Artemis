package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationRunStoreService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class HyperionAuthoringRunResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String PREFIX = "hypauthoringhistory";

    @Autowired
    private GenerationRunStoreService runs;

    @Autowired
    private ProgrammingExerciseUtilService programmingExercises;

    private long exerciseId;

    private String jobId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 1, 0, 2, 1);
        var course = programmingExercises.addCourseWithOneProgrammingExercise();
        userUtilService.addStudentToCourse(PREFIX + "student1", course);
        userUtilService.addEditorToCourse(PREFIX + "editor1", course);
        userUtilService.addEditorToCourse(PREFIX + "editor2", course);
        userUtilService.addInstructorToCourse(PREFIX + "instructor1", course);
        exerciseId = course.getExercises().iterator().next().getId();
        jobId = UUID.randomUUID().toString();
        var run = new AuthoringRun();
        run.setJobId(jobId);
        run.setExerciseId(exerciseId);
        run.setSourceExerciseId(exerciseId);
        run.setOwnerId(userTestRepository.findOneByLogin(PREFIX + "editor1").orElseThrow().getId());
        run.setKind(AuthoringRun.Kind.ADAPT);
        run.setStartedAt(Instant.now());
        runs.save(run);
        runs.complete(jobId, AuthoringRun.Status.CANCELLED, Instant.now(), false);
    }

    @Test
    @WithMockUser(username = PREFIX + "editor1", roles = "EDITOR")
    void ownerDiscoversRetainedHistoryWithoutBrowserStorageOrReplay() throws Exception {
        request.performMvcRequest(get("/api/hyperion/authoring-runs")).andExpect(status().isOk()).andExpect(jsonPath("$.runs[0].jobId").value(jobId))
                .andExpect(jsonPath("$.runs[0].exerciseId").value(exerciseId)).andExpect(jsonPath("$.runs[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.runs[0].running").value(false)).andExpect(jsonPath("$.runs[0].prompt").doesNotExist());
        request.performMvcRequest(get("/api/hyperion/programming-exercises/" + exerciseId + "/generation/runs/" + jobId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId)).andExpect(jsonPath("$.events[0].type").value("CANCELLED"))
                .andExpect(jsonPath("$.accountingState").value("INCOMPLETE"));
    }

    @Test
    @WithMockUser(username = PREFIX + "editor2", roles = "EDITOR")
    void anotherEditorOfTheSameCourseCannotReadTheOwnersRun() throws Exception {
        request.performMvcRequest(get("/api/hyperion/programming-exercises/" + exerciseId + "/generation/runs/" + jobId)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = PREFIX + "student1", roles = "USER")
    void studentCannotDiscoverOrInspectAuthoringJobs() throws Exception {
        request.performMvcRequest(get("/api/hyperion/authoring-runs")).andExpect(status().isForbidden());
        request.performMvcRequest(get("/api/hyperion/programming-exercises/" + exerciseId + "/generation/runs/" + jobId)).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void anonymousRequestsAreRejected() throws Exception {
        request.performMvcRequest(get("/api/hyperion/authoring-runs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = PREFIX + "editor1", roles = "EDITOR")
    void invalidCursorIsRejectedInsteadOfStartingAnUnboundedQuery() throws Exception {
        request.performMvcRequest(get("/api/hyperion/authoring-runs").param("beforeId", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = PREFIX + "editor1", roles = "EDITOR")
    void bulkAccessUsesCurrentCourseRolesAndDoesNotRevealUnknownRuns() throws Exception {
        var ids = List.of(jobId, UUID.randomUUID().toString());
        assertThat(request.postListWithResponseBody("/api/hyperion/authoring-runs/access", ids, String.class, HttpStatus.OK, null, null)).containsExactly(jobId);
        userUtilService.removeUserFromAllCourses(userTestRepository.findOneByLogin(PREFIX + "editor1").orElseThrow());
        assertThat(request.postListWithResponseBody("/api/hyperion/authoring-runs/access", ids, String.class, HttpStatus.OK, null, null)).isEmpty();
    }

    @Test
    @WithMockUser(username = PREFIX + "editor2", roles = "EDITOR")
    void bulkAccessDoesNotRevealAnotherEditorsRun() throws Exception {
        assertThat(request.postListWithResponseBody("/api/hyperion/authoring-runs/access", List.of(jobId), String.class, HttpStatus.OK, null, null)).isEmpty();
    }

    @Test
    @WithMockUser(username = PREFIX + "student1", roles = "USER")
    void studentCannotUseBulkAccess() throws Exception {
        request.postWithoutResponseBody("/api/hyperion/authoring-runs/access", List.of(jobId), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotUseBulkAccess() throws Exception {
        request.postWithoutResponseBody("/api/hyperion/authoring-runs/access", List.of(jobId), HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = PREFIX + "editor1", roles = "EDITOR")
    void bulkAccessRejectsEmptyOversizedAndMalformedBatches() throws Exception {
        for (var invalid : List.of(List.<String>of(), Collections.nCopies(501, jobId), List.of(" "), List.of("x".repeat(129)))) {
            request.postWithoutResponseBody("/api/hyperion/authoring-runs/access", invalid, HttpStatus.BAD_REQUEST);
        }
        assertThat(request.postListWithResponseBody("/api/hyperion/authoring-runs/access", Collections.nCopies(500, jobId), String.class, HttpStatus.OK, null, null))
                .containsExactly(jobId);
    }

}
