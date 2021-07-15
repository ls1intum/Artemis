import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import * as showdown from 'showdown';
import { escapeStringForUseInRegex } from 'app/shared/util/global.utils';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExercisePlantUmlService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-plant-uml.service';
import { ArtemisShowdownExtensionWrapper } from 'app/shared/markdown-editor/extensions/artemis-showdown-extension-wrapper';
import * as DOMPurify from 'dompurify';
import { Result } from 'app/entities/result.model';

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePlantUmlExtensionWrapper implements ArtemisShowdownExtensionWrapper {
    private latestResult?: Result;
    private injectableElementsFoundSubject = new Subject<() => void>();

    // unique index, even if multiple plant uml diagrams are shown from different problem statements on the same page (in different tabs)
    private plantUmlIndex = 0;
    constructor(private programmingExerciseInstructionService: ProgrammingExerciseInstructionService, private plantUmlService: ProgrammingExercisePlantUmlService) {}

    /**
     * Sets latest result according to parameter.
     * @param result - either a result or undefined.
     */
    public setLatestResult(result?: Result) {
        this.latestResult = result;
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
     * @param index the index of the plantUml in html
     */
    private loadAndInjectPlantUml(plantUml: string, index: number) {
        this.plantUmlService
            .getPlantUmlSvg(plantUml)
            .pipe(
                tap((plantUmlSvg: string) => {
                    const plantUmlHtmlContainer = document.getElementById(`plantUml-${index}`);
                    if (plantUmlHtmlContainer) {
                        // We need to sanitize the received svg as it could contain malicious code in a script tag.
                        plantUmlHtmlContainer.innerHTML = DOMPurify.sanitize(plantUmlSvg);
                    }
                }),
            )
            .subscribe();
    }

    /**
     * Creates and returns an extension to current exercise.
     * The extension provides a custom rendering mechanism for embedded plantUml diagrams.
     * The mechanism works as follows:
     * 1) Find (multiple) embedded plantUml diagrams based on a regex (startuml, enduml).
     * 2) Replace the whole plantUml with a simple plantUml div container and a unique placeholder id
     * 3) Add colors for test results in the plantUml (red, green, grey)
     * 4) Send the plantUml content to the server for rendering a svg (the result will be cached for performance reasons)
     * 5) Inject the computed svg for the plantUml (from the server) into the plantUml div container based on the unique placeholder id (see step 2)
     */
    getExtension() {
        const extension: showdown.ShowdownExtension = {
            type: 'lang',
            filter: (text: string) => {
                const idPlaceholder = '%idPlaceholder%';
                // E.g. [task][Implement BubbleSort](testBubbleSort)
                const plantUmlRegex = /@startuml([^@]*)@enduml/g;
                // E.g. Implement BubbleSort, testBubbleSort
                const plantUmlContainer = `<div class="mb-4" id="plantUml-${idPlaceholder}"></div>`;
                // Replace test status markers.
                const plantUmls = text.match(plantUmlRegex) || [];
                // Assign unique ids to uml data structure at the beginning.
                const plantUmlsIndexed = plantUmls.map((plantUml) => {
                    const nextIndex = this.plantUmlIndex;
                    // increase the global unique index so that the next plantUml gets a unique global id
                    this.plantUmlIndex++;
                    return { plantUmlId: nextIndex, plantUml };
                });
                // custom markdown to html rendering: replace the plantUml in the markdown with a simple <div></div> container with a unique id placeholder
                // with the global unique id so that we can find the plantUml later on, when it was rendered, and then inject the 'actual' inner html (actually a svg image)
                const replacedText = plantUmlsIndexed.reduce((acc: string, umlIndexed: { plantUmlId: number; plantUml: string }): string => {
                    return acc.replace(new RegExp(escapeStringForUseInRegex(umlIndexed.plantUml), 'g'), plantUmlContainer.replace(idPlaceholder, umlIndexed.plantUmlId.toString()));
                }, text);
                // before we send the plantUml to the server for rendering, we need to inject the current test status so that the colors can be adapted
                // (green == implemented, red == not yet implemented, grey == unknown)
                const plantUmlsValidated = plantUmlsIndexed.map((plantUmlIndexed: { plantUmlId: number; plantUml: string }) => {
                    plantUmlIndexed.plantUml = plantUmlIndexed.plantUml.replace(/testsColor\(((?:[^()]+\([^()]+\))*[^()]*)\)/g, (match: any, capture: string) => {
                        // split the names by "," only when there is not a closing bracket without a previous opening bracket
                        const tests = capture.split(/,(?![^(]*?\))/);
                        const { testCaseState } = this.programmingExerciseInstructionService.testStatusForTask(tests, this.latestResult);
                        return testCaseState === TestCaseState.SUCCESS ? 'green' : testCaseState === TestCaseState.FAIL ? 'red' : 'grey';
                    });
                    return plantUmlIndexed;
                });
                // send the adapted plantUml to the server for rendering and inject the result into the html DOM based on the unique plantUml id
                this.injectableElementsFoundSubject.next(() => {
                    plantUmlsValidated.forEach((plantUmlIndexed: { plantUmlId: number; plantUml: string }) => {
                        this.loadAndInjectPlantUml(plantUmlIndexed.plantUml, plantUmlIndexed.plantUmlId);
                    });
                });
                return replacedText;
            },
        };
        return extension;
    }
}
