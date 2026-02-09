import { Injectable, inject } from '@angular/core';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ArtemisTextReplacementPlugin } from 'app/shared/markdown-editor/extensions/ArtemisTextReplacementPlugin';
import { escapeStringForUseInRegex } from 'app/shared/util/string-pure.utils';
import { Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { ProgrammingExercisePlantUmlService } from 'app/programming/shared/instructions-render/services/programming-exercise-plant-uml.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import DOMPurify from 'dompurify';

// This regex is the same as in the server: ProgrammingExerciseTaskService.java
const testsColorRegex = /testsColor\((\s*[^()\s]+(\([^()]*\))?)\)/g;

/**
 * Singleton service that handles rendering of PlantUML diagrams embedded in programming exercise problem statements.
 *
 * IMPORTANT - SINGLETON HAZARD IN EXAM MODE:
 * This service is providedIn: 'root', meaning there is ONE shared instance across the entire application.
 * In exam mode, multiple ProgrammingExerciseInstructionComponent instances coexist simultaneously
 * in the DOM (hidden via [hidden], NOT destroyed). They ALL share this SAME singleton.
 *
 * The rendering mechanism works as follows:
 * 1) Find embedded PlantUML diagrams via regex (@startuml...@enduml)
 * 2) Replace each diagram with a <div> container that has a unique ID
 * 3) Add test result colors (green/red/grey) to the PlantUML source
 * 4) Send PlantUML source to the server for SVG rendering (cached)
 * 5) Inject the server-rendered SVG into the DOM container via document.getElementById()
 *
 * The container IDs MUST be globally unique across all exercises on the page.
 * If two exercises generate the same ID (e.g. "plantUml-0"), document.getElementById()
 * returns the FIRST match in DOM order, which may belong to a DIFFERENT exercise.
 * This causes: (1) wrong diagram shown in one exercise, (2) no diagram in another.
 *
 * Solution: Container IDs are scoped per exercise: "plantUml-{exerciseId}-{index}".
 * - The exerciseId distinguishes exercises from each other (set via setExerciseId before each render)
 * - The index (0, 1, 2, ...) distinguishes multiple diagrams within the same exercise;
 *   it is derived from the array position in replaceText(), NOT from mutable singleton state
 *
 * HISTORY: In January 2026, a mutable plantUmlIndex counter on this singleton was reset to 0
 * before each render. Since all exam exercises share this singleton, all exercises generated
 * the same IDs (plantUml-0, plantUml-1, ...), causing cross-contamination in exams with ~500 students.
 * The fix removed the mutable counter entirely and uses the array index instead.
 * See programming-exercise-plant-uml.extension.spec.ts for regression tests.
 */
@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePlantUmlExtensionWrapper extends ArtemisTextReplacementPlugin {
    private programmingExerciseInstructionService = inject(ProgrammingExerciseInstructionService);
    private plantUmlService = inject(ProgrammingExercisePlantUmlService);

    private latestResult?: Result;
    private testCases?: ProgrammingExerciseTestCase[];
    private injectableElementsFoundSubject = new Subject<() => void>();

    // The exercise ID used to scope PlantUML container DOM IDs.
    // Combined with the per-diagram array index to form: "plantUml-{exerciseId}-{index}".
    // This guarantees globally unique IDs even when multiple exercises are on the same page (exams).
    // IMPORTANT: There is intentionally NO mutable index counter on this singleton.
    // The per-diagram index comes from the array position in replaceText() to avoid shared mutable state.
    private exerciseId?: number;

    constructor() {
        super();
    }

    /**
     * Sets latest result according to parameter.
     * @param result - either a result or undefined.
     */
    public setLatestResult(result?: Result) {
        this.latestResult = result;
    }

    public setTestCases(testCases?: ProgrammingExerciseTestCase[]) {
        this.testCases = testCases;
    }

    /**
     * Sets the exercise ID for scoping PlantUML container IDs.
     * This ensures unique DOM IDs when multiple exercises are rendered on the same page (e.g. in exams).
     * Must be called before each markdown render, in the same synchronous block as the subsequent htmlForMarkdown() call.
     * @param exerciseId the ID of the exercise being rendered
     */
    public setExerciseId(exerciseId?: number): void {
        this.exerciseId = exerciseId;
    }

    /**
     * Subscribes to injectableElementsFoundSubject.
     */
    subscribeForInjectableElementsFound() {
        return this.injectableElementsFoundSubject.asObservable();
    }

    /**
     * For the stringified plantUml provided, render the plantUml on the server and inject it into the html.
     * @param plantUml a stringified version of one plantUml.
     * @param containerId the unique DOM ID of the plantUml container
     */
    private loadAndInjectPlantUml(plantUml: string, containerId: string) {
        this.plantUmlService
            .getPlantUmlSvg(plantUml)
            .pipe(
                tap((plantUmlSvg: string) => {
                    const plantUmlHtmlContainer = document.getElementById(containerId);
                    if (plantUmlHtmlContainer) {
                        // We need to sanitize the received svg as it could contain malicious code in a script tag.
                        plantUmlHtmlContainer.innerHTML = DOMPurify.sanitize(plantUmlSvg);
                    }
                }),
            )
            .subscribe();
    }

    /**
     * The extension provides a custom rendering mechanism for embedded plantUml diagrams.
     * The mechanism works as follows:
     * 1) Find (multiple) embedded plantUml diagrams based on a regex (startuml, enduml).
     * 2) Replace the whole plantUml with a simple plantUml div container and a unique placeholder id
     * 3) Add colors for test results in the plantUml (red, green, grey)
     * 4) Send the plantUml content to the server for rendering a svg (the result will be cached for performance reasons)
     * 5) Inject the computed svg for the plantUml (from the server) into the plantUml div container based on the unique placeholder id (see step 2)
     */
    replaceText(text: string): string {
        const idPlaceholder = '%idPlaceholder%';
        // E.g. [task][Implement BubbleSort](testBubbleSort)
        const plantUmlRegex = /@startuml([^@]*)@enduml/g;
        // E.g. Implement BubbleSort, testBubbleSort
        const plantUmlContainer = `<div class="mb-4" id="${idPlaceholder}"></div>`;
        // Replace test status markers.
        const plantUmls = text.match(plantUmlRegex) ?? [];
        // Assign unique container IDs to each diagram.
        // The ID format is "plantUml-{exerciseId}-{index}" where:
        // - exerciseId: scopes IDs per exercise (prevents cross-contamination in exams)
        // - index: the array position, distinguishes multiple diagrams within the same exercise
        // Using the array index (not a mutable counter) avoids shared singleton state bugs.
        const plantUmlsIndexed = plantUmls.map((plantUml, index) => {
            const containerId = this.exerciseId != undefined ? `plantUml-${this.exerciseId}-${index}` : `plantUml-${index}`;
            return { containerId, plantUml };
        });
        // custom markdown to html rendering: replace the plantUml in the markdown with a simple <div></div> container with a unique id placeholder
        // so that we can find the plantUml later on, when it was rendered, and then inject the 'actual' inner html (actually a svg image)
        const replacedText = plantUmlsIndexed.reduce((acc: string, umlIndexed: { containerId: string; plantUml: string }): string => {
            return acc.replace(new RegExp(escapeStringForUseInRegex(umlIndexed.plantUml), 'g'), plantUmlContainer.replace(idPlaceholder, umlIndexed.containerId));
        }, text);
        // before we send the plantUml to the server for rendering, we need to inject the current test status so that the colors can be adapted
        // (green == implemented, red == not yet implemented, grey == unknown)
        const plantUmlsValidated = plantUmlsIndexed.map((plantUmlIndexed: { containerId: string; plantUml: string }) => {
            plantUmlIndexed.plantUml = plantUmlIndexed.plantUml.replace(testsColorRegex, (match: string, capture: string) => {
                const tests = this.programmingExerciseInstructionService.convertTestListToIds(capture, this.testCases);
                const { testCaseState } = this.programmingExerciseInstructionService.testStatusForTask(tests, this.latestResult);
                switch (testCaseState) {
                    case TestCaseState.SUCCESS:
                        return 'green';
                    case TestCaseState.FAIL:
                        return 'red';
                    default:
                        return 'grey';
                }
            });
            return plantUmlIndexed;
        });
        // send the adapted plantUml to the server for rendering and inject the result into the html DOM based on the unique container id
        this.injectableElementsFoundSubject.next(() => {
            plantUmlsValidated.forEach((plantUmlIndexed: { containerId: string; plantUml: string }) => {
                this.loadAndInjectPlantUml(plantUmlIndexed.plantUml, plantUmlIndexed.containerId);
            });
        });
        return replacedText;
    }
}
