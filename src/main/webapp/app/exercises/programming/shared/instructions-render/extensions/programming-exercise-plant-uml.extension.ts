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

    constructor(private programmingExerciseInstructionService: ProgrammingExerciseInstructionService, private plantUmlService: ProgrammingExercisePlantUmlService) {}

    public setLatestResult(result: Result | null) {
        this.latestResult = result;
    }

    subscribeForInjectableElementsFound() {
        return this.injectableElementsFoundSubject.asObservable();
    }

    /**
     * For each stringified plantUml provided, render the plantUml on the server and inject it into the html.
     * @param plantUmls a stringified version of the plantUml.
     */
    private loadAndInjectPlantUmls(plantUmls: string[]) {
        plantUmls.forEach((plantUml, index) => {
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
        });
    }

    getExtension() {
        const extension: showdown.ShowdownExtension = {
            type: 'lang',
            filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
                const idPlaceholder = '%idPlaceholder%';
                // E.g. [task][Implement BubbleSort](testBubbleSort)
                const plantUmlRegex = /@startuml([^@]*)@enduml/g;
                // E.g. Implement BubbleSort, testBubbleSort
                const plantUmlContainer = `<div class="mb-4" id="plantUml-${idPlaceholder}"></div>`;
                // Replace test status markers.
                const plantUmls = text.match(plantUmlRegex) || [];
                const replacedText = plantUmls.reduce(
                    (acc: string, plantUml: string, index: number): string =>
                        acc.replace(new RegExp(escapeStringForUseInRegex(plantUml), 'g'), plantUmlContainer.replace(idPlaceholder, index.toString())),
                    text,
                );
                const plantUmlsValidated = plantUmls.map((plantUml) =>
                    plantUml.replace(/testsColor\(([^)]+)\)/g, (match: any, capture: string) => {
                        const tests = capture.split(',');
                        const { testCaseState } = this.programmingExerciseInstructionService.testStatusForTask(tests, this.latestResult);
                        return testCaseState === TestCaseState.SUCCESS ? 'green' : testCaseState === TestCaseState.FAIL ? 'red' : 'grey';
                    }),
                );
                this.injectableElementsFoundSubject.next(() => this.loadAndInjectPlantUmls(plantUmlsValidated));
                return replacedText;
            },
        };
        return extension;
    }
}
