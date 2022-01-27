package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.CodeHintService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class CodeHintServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private CodeHintService codeHintService;

    @Autowired
    private CodeHintRepository codeHintRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseTask programmingExerciseTask;

    @BeforeEach
    void init() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        this.programmingExercise = programmingExerciseRepository.findAll().get(0);
        programmingExerciseTask = new ProgrammingExerciseTask().taskName("Test Hint").exercise(programmingExercise);
        programmingExerciseTaskRepository.save(programmingExerciseTask);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void createValidCodeHint() {
        var codeHint = new CodeHint().programmingExerciseTask(programmingExerciseTask);
        codeHint.setTitle("Test Hint");
        codeHint.setExercise(programmingExercise);
        this.codeHintService.createCodeHint(codeHint);
        var hints = this.codeHintRepository.findByExerciseId(programmingExercise.getId());

        assertThat(hints).contains(codeHint);
    }

    @Test
    public void createInvalidHint() {
        var invalidHint1 = new CodeHint();
        assertThrows(BadRequestAlertException.class, () -> this.codeHintService.createCodeHint(invalidHint1));

        this.programmingExercise.setId(Long.MAX_VALUE);
        var invalidHint2 = new CodeHint();
        invalidHint2.setExercise(programmingExercise);

        assertThrows(EntityNotFoundException.class, () -> this.codeHintService.createCodeHint(invalidHint2));
    }

    @Test
    public void deleteHint() {
        var hint = new CodeHint();
        hint.setTitle("Test Hint");
        hint.setExercise(this.programmingExercise);

        this.codeHintService.createCodeHint(hint);
        this.codeHintService.deleteCodeHint(hint.getId());
        assertThat(this.codeHintRepository.findByExerciseId(this.programmingExercise.getId())).doesNotContain(hint);
    }

    @Test
    public void deleteNonExistentHint() {
        assertThrows(EntityNotFoundException.class, () -> this.codeHintService.deleteCodeHint(Long.MAX_VALUE));
    }

    @Test
    public void updateExistentHint() {
        var hint = new CodeHint();
        hint.setTitle("Test Hint");
        hint.setExercise(this.programmingExercise);
        this.codeHintService.createCodeHint(hint);

        hint.setTitle("Updated Test Hint");
        hint.setSolutionEntries(new HashSet<>());
        this.codeHintService.updateCodeHint(hint, hint.getId());

        var hints = this.codeHintRepository.findByExerciseId(programmingExercise.getId());
        assertThat(hints).hasSize(1);

        var updatedHintOptional = this.codeHintRepository.findById(hint.getId());
        assertThat(updatedHintOptional).isPresent();
        var updatedHint = updatedHintOptional.get();

        assertThat(updatedHint.getTitle()).isEqualTo("Updated Test Hint");
    }

    @Test
    public void updateHintInvalid() {
        var hint = new CodeHint();
        assertThrows(BadRequestAlertException.class, () -> this.codeHintService.updateCodeHint(new CodeHint(), hint.getId()));

        hint.setExercise(this.programmingExercise);
        this.codeHintService.createCodeHint(hint);
        assertThrows(BadRequestAlertException.class, () -> this.codeHintService.updateCodeHint(hint, Long.MAX_VALUE));

        hint.setExercise(null);
        assertThrows(BadRequestAlertException.class, () -> this.codeHintService.updateCodeHint(hint, hint.getId()));
    }
}
