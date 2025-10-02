import { Component, effect, inject, input } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { HeaderExercisePageWithDetailsComponent } from '../exercise-headers/with-details/header-exercise-page-with-details.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ModelingEditorComponent } from '../../modeling/shared/modeling-editor/modeling-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-example-solution',
    templateUrl: './example-solution.component.html',
    imports: [HeaderExercisePageWithDetailsComponent, TranslateDirective, ModelingEditorComponent, ArtemisTranslatePipe, HtmlForMarkdownPipe],
})
export class ExampleSolutionComponent {
    private exerciseService = inject(ExerciseService);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    private displayedExerciseId: number;
    public exercise?: Exercise;
    public exampleSolutionInfo?: ExampleSolutionInfo;

    readonly exerciseId = input<number>();
    readonly displayHeader = input(true);

    constructor() {
        effect(() => {
            const exerciseId = this.exerciseId();
            if (exerciseId === undefined) {
                return;
            }
            const didExerciseChange = this.displayedExerciseId !== exerciseId;
            this.displayedExerciseId = exerciseId;
            if (didExerciseChange) {
                this.loadExercise();
            }
        });
    }

    loadExercise() {
        this.exercise = undefined;
        this.exerciseService.getExerciseForExampleSolution(this.displayedExerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            const newExercise = exerciseResponse.body!;
            this.exercise = newExercise;
            this.exampleSolutionInfo = ExerciseService.extractExampleSolutionInfo(newExercise, this.artemisMarkdown);
        });
    }
}
