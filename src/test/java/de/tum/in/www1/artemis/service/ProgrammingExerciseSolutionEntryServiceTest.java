package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseSolutionEntryService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class ProgrammingExerciseSolutionEntryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryService programmingExerciseSolutionEntryService;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    private ProgrammingExercise programmingExercise;

    private CodeHint codeHint;

    private ProgrammingExerciseSolutionEntry validSolutionEntry;

    private ProgrammingExerciseSolutionEntry invalidSolutionEntry;

    @BeforeEach
    void init() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        this.programmingExercise = programmingExerciseRepository.findAll().get(0);

        this.codeHint = new CodeHint();
        codeHint.setTitle("Test Hint");
        codeHint.setExercise(programmingExercise);

        codeHintRepository.save(codeHint);

        validSolutionEntry = new ProgrammingExerciseSolutionEntry().file("Tester.java").previousCode(null).code("i++").previousLine(2).line(3).codeHint(codeHint);

        invalidSolutionEntry = new ProgrammingExerciseSolutionEntry();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void createValidSolutionEntry() {
        this.programmingExerciseSolutionEntryService.createProgrammingExerciseSolutionEntry(validSolutionEntry);
        assertThat(this.programmingExerciseSolutionEntryRepository.findByCodeHintId(this.codeHint.getId())).contains(validSolutionEntry);
    }

    @Test
    public void createInvalidSolutionEntry() {
        assertThrows(BadRequestAlertException.class, () -> this.programmingExerciseSolutionEntryService.createProgrammingExerciseSolutionEntry(invalidSolutionEntry));
    }

    @Test
    public void deleteSolutionEntry() {
        this.programmingExerciseSolutionEntryService.createProgrammingExerciseSolutionEntry(validSolutionEntry);
        this.programmingExerciseSolutionEntryService.deleteProgrammingExerciseSolutionEntry(validSolutionEntry.getId());
        assertThat(this.programmingExerciseSolutionEntryRepository.findByCodeHintId(this.codeHint.getId())).doesNotContain(validSolutionEntry);
    }

    @Test
    public void deleteNonExistentSolutionEntry() {
        assertThrows(EntityNotFoundException.class, () -> this.programmingExerciseSolutionEntryService.deleteProgrammingExerciseSolutionEntry(Long.MAX_VALUE));
    }

    @Test
    public void updateExistentSolutionEntry() {
        this.programmingExerciseSolutionEntryService.createProgrammingExerciseSolutionEntry(validSolutionEntry);

        var solutionEntryUpdate = validSolutionEntry.file("TesterUpdated.java");
        this.programmingExerciseSolutionEntryService.updateProgrammingExerciseSolutionEntry(solutionEntryUpdate, validSolutionEntry.getId());

        var solutionEntries = this.programmingExerciseSolutionEntryRepository.findByCodeHintId(codeHint.getId());
        assertThat(solutionEntries).hasSize(1);

        var updatedSolutionEntryOptional = this.programmingExerciseSolutionEntryRepository.findById(solutionEntryUpdate.getId());
        assertThat(updatedSolutionEntryOptional).isPresent();
        var updatedSolutionEntry = updatedSolutionEntryOptional.get();

        assertThat(updatedSolutionEntry.getFile()).isEqualTo("TesterUpdated.java");
    }

    @Test
    public void updateSolutionEntryInvalid() {
        assertThrows(BadRequestAlertException.class,
                () -> this.programmingExerciseSolutionEntryService.updateProgrammingExerciseSolutionEntry(new ProgrammingExerciseSolutionEntry(), invalidSolutionEntry.getId()));

        invalidSolutionEntry.setCodeHint(this.codeHint);
        this.programmingExerciseSolutionEntryService.createProgrammingExerciseSolutionEntry(invalidSolutionEntry);
        assertThrows(BadRequestAlertException.class,
                () -> this.programmingExerciseSolutionEntryService.updateProgrammingExerciseSolutionEntry(invalidSolutionEntry, Long.MAX_VALUE));

        invalidSolutionEntry.setCodeHint(null);
        assertThrows(BadRequestAlertException.class,
                () -> this.programmingExerciseSolutionEntryService.updateProgrammingExerciseSolutionEntry(invalidSolutionEntry, invalidSolutionEntry.getId()));
    }
}
