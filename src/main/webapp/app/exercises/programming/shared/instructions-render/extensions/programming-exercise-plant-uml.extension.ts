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
    private latestResult: Result | null = null;
    private injectableElementsFoundSubject = new Subject<() => void>();

    // unique index, even if multiple plant uml diagrams are shown from different problem statements on the same page (in different tabs)
    private plantUmlIndex = 0;
    constructor(private programmingExerciseInstructionService: ProgrammingExerciseInstructionService, private plantUmlService: ProgrammingExercisePlantUmlService) {}

    /**
     * Sets latest result according to parameter.
     * @param result - either a result or null.
     */
    public setLatestResult(result: Result | null) {
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
                    this.plantUmlIndex++;
                    return { id: nextIndex, plantUml };
                });
                const replacedText = plantUmlsIndexed.reduce((acc: string, umlIndexed: { id: number; plantUml: string }): string => {
                    return acc.replace(new RegExp(escapeStringForUseInRegex(umlIndexed.plantUml), 'g'), plantUmlContainer.replace(idPlaceholder, umlIndexed.id.toString()));
                }, text);
                const plantUmlsValidated = plantUmlsIndexed.map((plantUmlIndexed: { id: number; plantUml: string }) => {
                    plantUmlIndexed.plantUml = plantUmlIndexed.plantUml.replace(/testsColor\(([^)]+)\)/g, (match: any, capture: string) => {
                        const tests = capture.split(',');
                        const { testCaseState } = this.programmingExerciseInstructionService.testStatusForTask(tests, this.latestResult);
                        return testCaseState === TestCaseState.SUCCESS ? 'green' : testCaseState === TestCaseState.FAIL ? 'red' : 'grey';
                    });
                    return plantUmlIndexed;
                });
                this.injectableElementsFoundSubject.next(() => {
                    plantUmlsValidated.forEach((plantUmlIndexed: { id: number; plantUml: string }) => {
                        this.loadAndInjectPlantUml(plantUmlIndexed.plantUml, plantUmlIndexed.id);
                    });
                });
                return replacedText;
            },
        };
        return extension;
    }
}
