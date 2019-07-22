import { Injectable } from '@angular/core';
import { tap } from 'rxjs/operators';
import * as showdown from 'showdown';
import { Result } from 'app/entities/result';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';
import { ProgrammingExercisePlantUmlService } from 'app/entities/programming-exercise/instructions/programming-exercise-plant-uml.service';
import { TestCaseState } from 'app/entities/programming-exercise';

@Injectable()
export class ProgrammingExercisePlantUmlExtensionFactory {
    private latestResult: Result | null = null;

    constructor(private programmingExerciseInstructionService: ProgrammingExerciseInstructionService, private plantUmlService: ProgrammingExercisePlantUmlService) {}

    public setLatestResult(result: Result | null) {
        this.latestResult = result;
    }

    getExtension() {
        const extension: showdown.ShowdownExtension = {
            type: 'lang',
            filter: (text: string, converter: showdown.Converter, options: showdown.ConverterOptions) => {
                const idPlaceholder = '%idPlaceholder%';
                // E.g. [task][Implement BubbleSort](testBubbleSort)
                const plantUmlRegex = /@startuml([^@]*)@enduml/g;
                // E.g. Implement BubbleSort, testBubbleSort
                const plantUmlContainer = `<img id="plantUml-${idPlaceholder}"/>`;
                // Replace test status markers.
                const plantUmls = text.match(plantUmlRegex) || [];
                const replacedText = plantUmls.reduce(
                    (acc: string, plantUml: string, index: number): string =>
                        acc.replace(new RegExp(escapeStringForUseInRegex(plantUml), 'g'), plantUmlContainer.replace(idPlaceholder, index.toString())),
                    text,
                );
                const plantUmlsValidated = plantUmls.map(plantUml =>
                    plantUml.replace(/testsColor\(([^)]+)\)/g, (match: any, capture: string) => {
                        const tests = capture.split(',');
                        const [done] = this.programmingExerciseInstructionService.statusForTests(tests, this.latestResult);
                        return done === TestCaseState.SUCCESS ? 'green' : done === TestCaseState.FAIL ? 'red' : 'grey';
                    }),
                );
                setTimeout(
                    () =>
                        plantUmlsValidated.forEach((plantUml, index) => {
                            this.plantUmlService
                                .getPlantUmlImage(plantUml)
                                .pipe(
                                    tap((plantUmlSrcAttribute: string) => {
                                        const plantUmlHtmlContainer = document.getElementById(`plantUml-${index}`);
                                        if (plantUmlHtmlContainer) {
                                            plantUmlHtmlContainer.setAttribute('src', 'data:image/jpeg;base64,' + plantUmlSrcAttribute);
                                        }
                                    }),
                                )
                                .subscribe();
                        }),
                    0,
                );
                return replacedText;
            },
        };
        return extension;
    }
}
