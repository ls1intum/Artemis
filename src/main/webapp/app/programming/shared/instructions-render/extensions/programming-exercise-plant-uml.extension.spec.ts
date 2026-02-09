import { TestBed } from '@angular/core/testing';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseInstructionService } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { ProgrammingExercisePlantUmlService } from 'app/programming/shared/instructions-render/services/programming-exercise-plant-uml.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import 'jest-extended';

/**
 * Tests for ProgrammingExercisePlantUmlExtensionWrapper.
 *
 * CRITICAL REGRESSION GUARD:
 * These tests protect against PlantUML diagram cross-contamination in exam mode.
 *
 * Context:
 * - The PlantUML extension is a root-level singleton (providedIn: 'root')
 * - In exam mode, multiple ProgrammingExerciseInstructionComponent instances coexist
 *   in the DOM simultaneously (hidden via [hidden], NOT destroyed)
 * - Each component shares the SAME singleton extension instance
 * - PlantUML diagrams are rendered server-side and injected into the DOM via document.getElementById()
 *
 * The bug that MUST NOT regress:
 * - If all exercises generate the same container IDs (e.g. plantUml-0, plantUml-1),
 *   document.getElementById() returns the FIRST match in DOM order, which may belong
 *   to a different exercise
 * - This causes: (1) wrong diagram shown in one exercise, (2) no diagram shown in another
 *
 * The fix:
 * - Container IDs are scoped per exercise: plantUml-{exerciseId}-{index}
 * - setExerciseId(exerciseId) MUST be called before each render
 * - The per-diagram index comes from the array position (not mutable state), so no reset is needed
 */
describe('ProgrammingExercisePlantUmlExtensionWrapper', () => {
    let extension: ProgrammingExercisePlantUmlExtensionWrapper;

    // Problem statements with varying numbers of PlantUML diagrams for testing
    const singleDiagramText = 'Some text\n@startuml\nclass Foo {}\n@enduml\nMore text';
    const twoDiagramText = 'Text\n@startuml\nclass Foo {}\n@enduml\nMiddle\n@startuml\nclass Bar {}\n@enduml\nEnd';
    const threeDiagramText = 'A\n@startuml\nclass A {}\n@enduml\nB\n@startuml\nclass B {}\n@enduml\nC\n@startuml\nclass C {}\n@enduml\nD';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                ProgrammingExercisePlantUmlExtensionWrapper,
                ProgrammingExerciseInstructionService,
                ProgrammingExercisePlantUmlService,
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        extension = TestBed.inject(ProgrammingExercisePlantUmlExtensionWrapper);
    });

    /**
     * Helper to extract all PlantUML container IDs from rendered HTML.
     * Matches the pattern: id="plantUml-..."
     */
    function extractPlantUmlIds(html: string): string[] {
        const idRegex = /id="(plantUml-[^"]+)"/g;
        const ids: string[] = [];
        let match;
        while ((match = idRegex.exec(html)) !== null) {
            ids.push(match[1]);
        }
        return ids;
    }

    describe('single exercise with single diagram', () => {
        it('should generate exercise-scoped container ID', () => {
            extension.setExerciseId(42);
            const result = extension.replaceText(singleDiagramText);
            const ids = extractPlantUmlIds(result);

            expect(ids).toHaveLength(1);
            expect(ids[0]).toBe('plantUml-42-0');
        });
    });

    describe('single exercise with multiple diagrams', () => {
        it('should generate sequential exercise-scoped container IDs for two diagrams', () => {
            extension.setExerciseId(10);
            const result = extension.replaceText(twoDiagramText);
            const ids = extractPlantUmlIds(result);

            expect(ids).toHaveLength(2);
            expect(ids[0]).toBe('plantUml-10-0');
            expect(ids[1]).toBe('plantUml-10-1');
        });

        it('should generate sequential exercise-scoped container IDs for three diagrams', () => {
            extension.setExerciseId(99);
            const result = extension.replaceText(threeDiagramText);
            const ids = extractPlantUmlIds(result);

            expect(ids).toHaveLength(3);
            expect(ids[0]).toBe('plantUml-99-0');
            expect(ids[1]).toBe('plantUml-99-1');
            expect(ids[2]).toBe('plantUml-99-2');
        });
    });

    /**
     * CRITICAL REGRESSION TEST: Exam mode with multiple exercises.
     *
     * This simulates the exact scenario that caused the bug:
     * - 3 programming exercises rendered on the same page in an exam
     * - Each exercise has a different number of PlantUML diagrams
     * - The singleton extension is shared across all exercises
     * - setExerciseId() is called before each exercise's render
     *
     * Expected: ALL container IDs across ALL exercises must be globally unique.
     * If this test fails, diagram cross-contamination WILL occur in exams.
     */
    describe('multiple exercises rendered sequentially (exam mode)', () => {
        it('should generate globally unique IDs when multiple exercises each have multiple diagrams', () => {
            // Exercise A (id=10) has 2 PlantUML diagrams
            extension.setExerciseId(10);
            const resultA = extension.replaceText(twoDiagramText);
            const idsA = extractPlantUmlIds(resultA);

            // Exercise B (id=20) has 3 PlantUML diagrams
            extension.setExerciseId(20);
            const resultB = extension.replaceText(threeDiagramText);
            const idsB = extractPlantUmlIds(resultB);

            // Exercise C (id=30) has 1 PlantUML diagram
            extension.setExerciseId(30);
            const resultC = extension.replaceText(singleDiagramText);
            const idsC = extractPlantUmlIds(resultC);

            // Verify each exercise got the right number of diagrams
            expect(idsA).toHaveLength(2);
            expect(idsB).toHaveLength(3);
            expect(idsC).toHaveLength(1);

            // Verify exercise-scoped IDs
            expect(idsA).toEqual(['plantUml-10-0', 'plantUml-10-1']);
            expect(idsB).toEqual(['plantUml-20-0', 'plantUml-20-1', 'plantUml-20-2']);
            expect(idsC).toEqual(['plantUml-30-0']);

            // CRITICAL: Verify ALL IDs across ALL exercises are globally unique
            const allIds = [...idsA, ...idsB, ...idsC];
            const uniqueIds = new Set(allIds);
            expect(uniqueIds.size).toBe(allIds.length);
        });

        it('should NOT produce colliding IDs even when exercises have the same number of diagrams', () => {
            // Both exercises have exactly 2 diagrams - this is where the old bug would manifest
            extension.setExerciseId(10);
            const resultA = extension.replaceText(twoDiagramText);
            const idsA = extractPlantUmlIds(resultA);

            extension.setExerciseId(20);
            const resultB = extension.replaceText(twoDiagramText);
            const idsB = extractPlantUmlIds(resultB);

            // Both have 2 diagrams
            expect(idsA).toHaveLength(2);
            expect(idsB).toHaveLength(2);

            // But the IDs must be different because the exercise IDs differ
            expect(idsA[0]).not.toBe(idsB[0]);
            expect(idsA[1]).not.toBe(idsB[1]);

            // Verify exact IDs
            expect(idsA).toEqual(['plantUml-10-0', 'plantUml-10-1']);
            expect(idsB).toEqual(['plantUml-20-0', 'plantUml-20-1']);
        });
    });

    describe('re-rendering the same exercise', () => {
        it('should produce the same IDs when re-rendering, so new SVGs replace old ones', () => {
            // First render of exercise 10
            extension.setExerciseId(10);
            const firstRender = extension.replaceText(twoDiagramText);
            const firstIds = extractPlantUmlIds(firstRender);

            // Re-render of exercise 10 (e.g. after theme change)
            extension.setExerciseId(10);
            const secondRender = extension.replaceText(twoDiagramText);
            const secondIds = extractPlantUmlIds(secondRender);

            // IDs should be identical so the new SVGs overwrite the old containers
            expect(firstIds).toEqual(secondIds);
            expect(firstIds).toEqual(['plantUml-10-0', 'plantUml-10-1']);
        });

        it('should produce stable IDs even after rendering other exercises in between', () => {
            // Render exercise 10
            extension.setExerciseId(10);
            const firstRender = extension.replaceText(twoDiagramText);
            const firstIds = extractPlantUmlIds(firstRender);

            // Render exercise 20 (different exercise in between)
            extension.setExerciseId(20);
            extension.replaceText(threeDiagramText);

            // Re-render exercise 10 again
            extension.setExerciseId(10);
            const secondRender = extension.replaceText(twoDiagramText);
            const secondIds = extractPlantUmlIds(secondRender);

            // IDs for exercise 10 should be the same both times
            expect(firstIds).toEqual(secondIds);
        });
    });

    describe('callback isolation per render', () => {
        it('should emit injection callbacks with correct container IDs for each exercise', () => {
            const callbackCaptures: Array<() => void> = [];
            extension.subscribeForInjectableElementsFound().subscribe((callback) => {
                callbackCaptures.push(callback);
            });

            const injectSpy = jest.spyOn(extension as any, 'loadAndInjectPlantUml');

            // Render exercise A with 2 diagrams
            extension.setExerciseId(10);
            extension.replaceText(twoDiagramText);

            // Render exercise B with 1 diagram
            extension.setExerciseId(20);
            extension.replaceText(singleDiagramText);

            // We should have 2 callbacks (one per replaceText call)
            expect(callbackCaptures).toHaveLength(2);

            // Execute callback for exercise A
            callbackCaptures[0]();
            expect(injectSpy).toHaveBeenCalledTimes(2);
            expect(injectSpy).toHaveBeenCalledWith(expect.any(String), 'plantUml-10-0');
            expect(injectSpy).toHaveBeenCalledWith(expect.any(String), 'plantUml-10-1');

            injectSpy.mockClear();

            // Execute callback for exercise B
            callbackCaptures[1]();
            expect(injectSpy).toHaveBeenCalledOnce();
            expect(injectSpy).toHaveBeenCalledWith(expect.any(String), 'plantUml-20-0');
        });

        it('should emit callbacks with correct IDs for three exercises with multiple diagrams each', () => {
            const callbackCaptures: Array<() => void> = [];
            extension.subscribeForInjectableElementsFound().subscribe((callback) => {
                callbackCaptures.push(callback);
            });

            const injectSpy = jest.spyOn(extension as any, 'loadAndInjectPlantUml');

            // Exercise A: 2 diagrams
            extension.setExerciseId(10);
            extension.replaceText(twoDiagramText);

            // Exercise B: 3 diagrams
            extension.setExerciseId(20);
            extension.replaceText(threeDiagramText);

            // Exercise C: 1 diagram
            extension.setExerciseId(30);
            extension.replaceText(singleDiagramText);

            expect(callbackCaptures).toHaveLength(3);

            // Execute all callbacks (simulates what happens in exam mode when all exercises inject their diagrams)
            callbackCaptures.forEach((cb) => cb());

            // Verify total number of injection calls: 2 + 3 + 1 = 6
            expect(injectSpy).toHaveBeenCalledTimes(6);

            // Verify each call targeted the correct exercise-scoped container
            const calledIds = injectSpy.mock.calls.map((call) => call[1]);
            expect(calledIds).toContain('plantUml-10-0');
            expect(calledIds).toContain('plantUml-10-1');
            expect(calledIds).toContain('plantUml-20-0');
            expect(calledIds).toContain('plantUml-20-1');
            expect(calledIds).toContain('plantUml-20-2');
            expect(calledIds).toContain('plantUml-30-0');

            // CRITICAL: All IDs must be unique
            const uniqueCalledIds = new Set(calledIds);
            expect(uniqueCalledIds.size).toBe(6);
        });
    });

    describe('fallback behavior without exerciseId', () => {
        it('should fall back to non-scoped IDs when exerciseId is undefined', () => {
            extension.setExerciseId(undefined);
            const result = extension.replaceText(twoDiagramText);
            const ids = extractPlantUmlIds(result);

            expect(ids).toEqual(['plantUml-0', 'plantUml-1']);
        });

        it('should fall back to non-scoped IDs when setExerciseId is never called', () => {
            // Simulates legacy usage without calling setExerciseId
            const result = extension.replaceText(singleDiagramText);
            const ids = extractPlantUmlIds(result);

            expect(ids).toEqual(['plantUml-0']);
        });
    });

    describe('text without PlantUML diagrams', () => {
        it('should return the text unchanged and not emit any callbacks', () => {
            const callbackCaptures: Array<() => void> = [];
            extension.subscribeForInjectableElementsFound().subscribe((callback) => {
                callbackCaptures.push(callback);
            });

            extension.setExerciseId(10);
            const text = 'Some text without any diagrams';
            const result = extension.replaceText(text);

            expect(result).toBe(text);
            // A callback is still emitted, but it should be a no-op (empty forEach)
            expect(callbackCaptures).toHaveLength(1);
        });
    });
});
