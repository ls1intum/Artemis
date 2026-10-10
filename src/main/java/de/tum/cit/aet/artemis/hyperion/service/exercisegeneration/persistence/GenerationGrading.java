package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.GeneratedTestPlan;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationScheduleService;

/** Applies verified grading plans and restores only the grading fields Hyperion changes. */
public final class GenerationGrading {

    private static final Logger log = LoggerFactory.getLogger(GenerationGrading.class);

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService;

    GenerationGrading(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseCreationScheduleService scheduleService) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseCreationScheduleService = scheduleService;
    }

    /** Immutable grading values, detached from the entities modified during generation. */
    public record Snapshot(Map<String, Grading> tests) implements Serializable {

        public static final Snapshot EMPTY = new Snapshot(Map.of());

        public Snapshot {
            tests = Map.copyOf(tests);
        }
    }

    /** Weight and visibility are the only test-case settings generation may replace. */
    public record Grading(@Nullable Double weight, @Nullable Visibility visibility) implements Serializable {

        static Grading of(ProgrammingExerciseTestCase test) {
            return new Grading(test.getWeight(), test.getVisibility());
        }
    }

    Snapshot capture(long exerciseId) {
        Map<String, Grading> tests = new LinkedHashMap<>();
        for (ProgrammingExerciseTestCase test : testCaseRepository.findByExerciseId(exerciseId)) {
            tests.put(test.getTestName(), Grading.of(test));
        }
        return new Snapshot(tests);
    }

    boolean canRestore(long exerciseId, Snapshot previous, Snapshot saved) {
        if (previous == null || saved == null) {
            return false;
        }
        Map<String, Grading> current = capture(exerciseId).tests();
        return current.keySet().containsAll(previous.tests().keySet()) && saved.tests().entrySet().stream().allMatch(
                entry -> Objects.equals(current.get(entry.getKey()), entry.getValue()) || Objects.equals(current.get(entry.getKey()), previous.tests().get(entry.getKey())));
    }

    void restore(long exerciseId, Snapshot previous, Snapshot saved) {
        if (!canRestore(exerciseId, previous, saved)) {
            throw new IllegalStateException("Test grading changed after generation; refusing to overwrite instructor edits");
        }
        List<ProgrammingExerciseTestCase> changed = new ArrayList<>();
        for (ProgrammingExerciseTestCase test : testCaseRepository.findByExerciseId(exerciseId)) {
            Grading target = previous.tests().get(test.getTestName());
            if (target != null && !target.equals(Grading.of(test))) {
                test.setWeight(target.weight());
                test.setVisibility(target.visibility());
                changed.add(test);
            }
        }
        if (!changed.isEmpty()) {
            testCaseRepository.saveAll(changed);
        }
        if (!previous.equals(saved)) {
            programmingExerciseCreationScheduleService.scheduleOperations(exerciseId);
        }
    }

    void apply(ProgrammingExercise exercise, @Nullable String testPlanJson, Runnable beforeDurableMutation) {
        if (testPlanJson == null || testPlanJson.isBlank()) {
            return;
        }
        GeneratedTestPlan plan = GeneratedTestPlan.parse(testPlanJson);
        if (!plan.hiddenEntries().isEmpty() && exercise.getDueDate() == null) {
            throw new IllegalStateException("The verified test plan contains AFTER_DUE_DATE tests, but exercise " + exercise.getId() + " has no due date");
        }
        // Build synchronization retains replaced tests as inactive rows. Only the active suite must match the verified plan.
        Map<String, ProgrammingExerciseTestCase> byName = testCaseRepository.findByExerciseId(exercise.getId()).stream()
                .filter(testCase -> Boolean.TRUE.equals(testCase.isActive()))
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, testCase -> testCase, (first, second) -> first));
        List<String> plannedStructuralNames = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(byName::containsKey)
                .filter(name -> byName.get(name).getType() == ProgrammingExerciseTestCaseType.STRUCTURAL).sorted().toList();
        if (!plannedStructuralNames.isEmpty()) {
            throw new IllegalStateException("The verified test plan contains server-classified structural tests, which cannot carry grading decisions: " + plannedStructuralNames);
        }
        List<String> unresolvedNames = plan.tests().stream().map(GeneratedTestPlan.Entry::name).filter(name -> !byName.containsKey(name)).sorted().toList();
        List<String> unplannedNames = byName.values().stream().filter(testCase -> testCase.getType() != ProgrammingExerciseTestCaseType.STRUCTURAL)
                .map(ProgrammingExerciseTestCase::getTestName).filter(name -> plan.tests().stream().noneMatch(entry -> entry.name().equals(name))).sorted().toList();
        if (!unresolvedNames.isEmpty() || !unplannedNames.isEmpty()) {
            throw new IllegalStateException(
                    "The synchronized test cases no longer match the verified test plan. Missing saved tests: " + unresolvedNames + "; unplanned saved tests: " + unplannedNames);
        }
        List<ProgrammingExerciseTestCase> changed = new ArrayList<>();
        Map<String, Double> effectiveWeights = plan.effectiveWeightsByName();
        for (GeneratedTestPlan.Entry entry : plan.tests()) {
            ProgrammingExerciseTestCase testCase = byName.get(entry.name());
            Double weight = effectiveWeights.get(entry.name());
            Visibility visibility = "AFTER_DUE_DATE".equals(entry.visibility()) ? Visibility.AFTER_DUE_DATE : Visibility.ALWAYS;
            if (!Objects.equals(testCase.getWeight(), weight) || testCase.getVisibility() != visibility) {
                testCase.setWeight(weight);
                testCase.setVisibility(visibility);
                changed.add(testCase);
            }
        }
        byName.values().stream().filter(testCase -> testCase.getType() == ProgrammingExerciseTestCaseType.STRUCTURAL).forEach(testCase -> {
            if (!Objects.equals(testCase.getWeight(), 0.0) || testCase.getVisibility() != Visibility.ALWAYS) {
                testCase.setWeight(0.0);
                testCase.setVisibility(Visibility.ALWAYS);
                changed.add(testCase);
            }
        });
        if (!changed.isEmpty()) {
            beforeDurableMutation.run();
            testCaseRepository.saveAll(changed);
        }
        boolean scheduleDueDateRecalculation = !plan.hiddenEntries().isEmpty();
        log.info("Applied generated test plan to exercise {}: updated grading for {} tests; plan contains {} tests hidden until the due date", exercise.getId(), changed.size(),
                plan.hiddenEntries().size());
        if (scheduleDueDateRecalculation) {
            programmingExerciseCreationScheduleService.scheduleOperations(exercise.getId());
        }
    }

}
