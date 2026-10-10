package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseTestCaseSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationVersionRecoveryService.Recovery;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

/** Restores canonical test metadata without requiring the previous draft to compile or pass its tests. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationRestoreTestCasesService {

    private final ProgrammingExerciseTestCaseRepository tests;

    public GenerationRestoreTestCasesService(ProgrammingExerciseTestCaseRepository tests) {
        this.tests = tests;
    }

    /**
     * Checks captured test fields before writes. Active additions and changed captured tests fail closed; inactive history stays untouched.
     *
     * @param exerciseId destination
     * @param pair       canonical before/after snapshots
     * @return whether restoration can preserve intervening instructor changes
     */
    public boolean canRestore(long exerciseId, Recovery pair) {
        return matches(tests.findByExerciseId(exerciseId), pair);
    }

    /**
     * Restores active/type/weight/bonus/visibility together. Generation-only rows remain inactive to preserve historical result feedback.
     *
     * @param exerciseId destination
     * @param pair       canonical before/after snapshots
     * @param ownsSlot   exact mutation ownership fence
     */
    public void restore(long exerciseId, Recovery pair, BooleanSupplier ownsSlot) {
        var current = tests.findByExerciseId(exerciseId);
        if (!matches(current, pair)) {
            throw new IllegalStateException("Test metadata was edited after authoring");
        }
        var before = byName(pair.before().programmingData().testCases());
        if (!ownsSlot.getAsBoolean()) {
            throw new IllegalStateException("Test restoration lost the exercise mutation guard");
        }
        for (var test : current) {
            var target = before.get(test.getTestName());
            if (target == null) {
                test.setActive(false);
            }
            else {
                test.setActive(target.active());
                test.setType(target.type());
                test.setWeight(target.weight());
                test.setBonusMultiplier(target.bonusMultiplier());
                test.setBonusPoints(target.bonusPoints());
                test.setVisibility(target.visibility());
            }
        }
        tests.saveAll(current);
    }

    private boolean matches(Set<ProgrammingExerciseTestCase> current, Recovery pair) {
        var before = byName(pair.before().programmingData().testCases());
        var after = byName(pair.after().programmingData().testCases());
        var currentNames = current.stream().map(ProgrammingExerciseTestCase::getTestName).collect(Collectors.toSet());
        if (!currentNames.containsAll(before.keySet()) || !currentNames.containsAll(after.keySet())) {
            return false;
        }
        return current.stream().allMatch(test -> {
            var actual = ProgrammingExerciseTestCaseSnapshotDTO.of(test);
            var previous = before.get(test.getTestName());
            var saved = after.get(test.getTestName());
            if (Objects.equals(actual, previous) || Objects.equals(actual, saved)) {
                return true;
            }
            // Inactive history from a later, already-undone run is not part of this version's grading and is left untouched.
            if (previous == null && saved == null && !test.isActive()) {
                return true;
            }
            // An interrupted restore may already have deactivated a generated-only row.
            return previous == null && saved != null && Objects.equals(actual, new ProgrammingExerciseTestCaseSnapshotDTO(saved.id(), saved.testName(), false, saved.type(),
                    saved.weight(), saved.bonusMultiplier(), saved.bonusPoints(), saved.visibility()));
        });
    }

    private static Map<String, ProgrammingExerciseTestCaseSnapshotDTO> byName(Set<ProgrammingExerciseTestCaseSnapshotDTO> snapshots) {
        return snapshots == null ? Map.of() : snapshots.stream().collect(Collectors.toMap(ProgrammingExerciseTestCaseSnapshotDTO::testName, test -> test));
    }
}
