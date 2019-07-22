import { Injectable } from '@angular/core';
import { tap } from 'rxjs/operators';
import * as showdown from 'showdown';
import { Result } from 'app/entities/result';
import { escapeStringForUseInRegex } from 'app/utils/global.utils';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';
import { ProgrammingExercisePlantUmlService } from 'app/entities/programming-exercise/instructions/programming-exercise-plant-uml.service';

export type TestsForTasks = Array<[string, string, string[]]>;

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
                const plantUmls = text.match(plantUmlRegex) || [];
                const replacedText = plantUmls.reduce(
                    (acc: string, plantUml: string, index: number): string =>
                        acc.replace(new RegExp(escapeStringForUseInRegex(plantUml), 'g'), plantUmlContainer.replace(idPlaceholder, index.toString())),
                    text,
                );
                plantUmls.forEach((plantUml, index) => {
                    this.plantUmlService
                        .getPlantUmlImage(plantUml)
                        .pipe(
                            tap((plantUmlSrcAttribute: string) => {
                                const plantUmlContainer = document.getElementById(`plantUml-${index}`);
                                if (plantUmlContainer) {
                                    plantUmlContainer.setAttribute('src', 'data:image/jpeg;base64,' + plantUmlSrcAttribute);
                                }
                            }),
                        )
                        .subscribe();
                });
                return replacedText;
            },
        };
        return extension;
    }
}
