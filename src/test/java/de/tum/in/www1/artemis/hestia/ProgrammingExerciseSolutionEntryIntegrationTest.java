package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;

public class ProgrammingExerciseSolutionEntryIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    private ProgrammingExercise programmingExercise;

    private CodeHint codeHint;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        programmingExercise = exerciseRepository.findAll().get(0);
        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId());

        codeHint = new CodeHint();
        codeHint.setExercise(programmingExercise);
        codeHint.setTitle("Code Hint Title");
        codeHint.setContent("Code Hint Content");
        codeHintRepository.save(codeHint);

        for (ProgrammingExerciseTestCase testCase : testCases) {
            var solutionEntry = new ProgrammingExerciseSolutionEntry();
            solutionEntry.setTestCase(testCase);
            solutionEntry.setPreviousCode("No code");
            solutionEntry.setCode("Some code");
            solutionEntry.setPreviousLine(1);
            solutionEntry.setCodeHint(codeHint);
            solutionEntry.setLine(1);
            solutionEntry.setFilePath("code.java");
            programmingExerciseSolutionEntryRepository.save(solutionEntry);
        }
        ProgrammingExerciseTask task = new ProgrammingExerciseTask();
        task.setExercise(programmingExercise);
        task.setTaskName("Task");
        task.setTestCases(new HashSet<>(testCases));
        task = programmingExerciseTaskRepository.save(task);
        codeHint.setProgrammingExerciseTask(task);
        codeHintRepository.save(codeHint);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetSolutionEntryById() throws Exception {
        Long entryId = programmingExerciseSolutionEntryRepository.findAll().get(0).getId();
        ProgrammingExerciseSolutionEntry expectedSolutionEntry = programmingExerciseSolutionEntryRepository.findByIdWithTestCaseAndProgrammingExerciseElseThrow(entryId);
        final var actualSolutionEntry = request.get("/api/programming-exercises/" + programmingExercise.getId() + "/solution-entries/" + expectedSolutionEntry.getId(),
                HttpStatus.OK, ProgrammingExerciseSolutionEntry.class);
        assertThat(actualSolutionEntry).isEqualTo(expectedSolutionEntry);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetSolutionEntryByIdAsStudent() throws Exception {
        Long entryId = programmingExerciseSolutionEntryRepository.findAll().get(0).getId();
        ProgrammingExerciseSolutionEntry expectedSolutionEntry = programmingExerciseSolutionEntryRepository.findByIdWithTestCaseAndProgrammingExerciseElseThrow(entryId);
        request.get("/api/programming-exercises/" + programmingExercise.getId() + "/solution-entries/" + expectedSolutionEntry.getId(), HttpStatus.FORBIDDEN,
                ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetSolutionEntryByIdWithInvalidExerciseIdAsStudent() throws Exception {
        Long entryId = programmingExerciseSolutionEntryRepository.findAll().get(0).getId();
        request.get("/api/programming-exercises/" + Long.MAX_VALUE + "/solution-entries/" + entryId, HttpStatus.FORBIDDEN, ProgrammingExerciseSolutionEntry.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetSolutionEntriesByCodeHintId() throws Exception {
        final Set<ProgrammingExerciseSolutionEntry> solutionEntries = new HashSet<>(
                request.getList("/api/programming-exercises/" + programmingExercise.getId() + "/code-hints/" + codeHint.getId() + "/solution-entries", HttpStatus.OK,
                        ProgrammingExerciseSolutionEntry.class));
        assertThat(solutionEntries).isEqualTo(programmingExerciseSolutionEntryRepository.findByCodeHintId(codeHint.getId()));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetSolutionEntriesByTestCaseId() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        final var solutionEntries = new HashSet<>(
                request.getList("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries", HttpStatus.OK,
                        ProgrammingExerciseSolutionEntry.class));
        assertThat(solutionEntries).isEqualTo(testCase.getSolutionEntries());
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testDeleteSolutionEntry() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        Long entryId = testCase.getSolutionEntries().stream().findFirst().orElseThrow().getId();
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries/" + entryId, HttpStatus.NO_CONTENT);
        assertThat(programmingExerciseSolutionEntryRepository.findById(entryId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testDeleteSolutionEntryAsStudent() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        Long entryId = testCase.getSolutionEntries().stream().findFirst().orElseThrow().getId();
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries/" + entryId, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteSolutionEntryAsTutor() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        Long entryId = testCase.getSolutionEntries().stream().findFirst().orElseThrow().getId();
        request.delete("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries/" + entryId, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testUpdateSolutionEntry() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        ProgrammingExerciseSolutionEntry entry = testCase.getSolutionEntries().stream().findFirst().orElseThrow();
        Long entryId = entry.getId();
        String updatedFilePath = "NewPath.java";
        entry.setFilePath(updatedFilePath);

        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries/" + entryId, entry, HttpStatus.OK);
        Optional<ProgrammingExerciseSolutionEntry> entryAfterUpdate = programmingExerciseSolutionEntryRepository.findById(entryId);
        assertThat(entryAfterUpdate).isPresent();
        assertThat(entryAfterUpdate.get().getFilePath()).isEqualTo(updatedFilePath);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testUpdateSolutionEntryWithInvalidId() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        ProgrammingExerciseSolutionEntry entry = testCase.getSolutionEntries().stream().findFirst().orElseThrow();
        Long entryId = entry.getId();
        String updatedFilePath = "NewPath.java";
        entry.setFilePath(updatedFilePath);

        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries/" + entryId, entry, HttpStatus.OK);
        Optional<ProgrammingExerciseSolutionEntry> entryAfterUpdate = programmingExerciseSolutionEntryRepository.findById(entryId);
        assertThat(entryAfterUpdate).isPresent();
        assertThat(entryAfterUpdate.get().getFilePath()).isEqualTo(updatedFilePath);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testUpdateSolutionEntryAsStudent() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        ProgrammingExerciseSolutionEntry entry = testCase.getSolutionEntries().stream().findFirst().orElseThrow();
        Long entryId = entry.getId();

        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries/" + entryId, entry, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testUpdateSolutionEntryAsTutor() throws Exception {
        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByExerciseIdWithSolutionEntries(programmingExercise.getId()).stream().findFirst()
                .orElseThrow();
        ProgrammingExerciseSolutionEntry entry = testCase.getSolutionEntries().stream().findFirst().orElseThrow();
        Long entryId = entry.getId();

        request.put("/api/programming-exercises/" + programmingExercise.getId() + "/test-cases/" + testCase.getId() + "/solution-entries/" + entryId, entry, HttpStatus.FORBIDDEN);
    }
}
