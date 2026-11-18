import { Component, OnInit, inject, input, signal } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { HeaderExercisePageWithDetailsComponent } from '../exercise-headers/with-details/header-exercise-page-with-details.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ModelingEditorComponent } from '../../modeling/shared/modeling-editor/modeling-editor.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';

@Component({
    selector: 'jhi-example-solution',
    templateUrl: './example-solution.component.html',
    imports: [HeaderExercisePageWithDetailsComponent, TranslateDirective, ModelingEditorComponent, ArtemisTranslatePipe, MarkdownDirective],
})
export class ExampleSolutionComponent implements OnInit {
    private exerciseService = inject(ExerciseService);
    private route = inject(ActivatedRoute);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    private displayedExerciseId?: number;
    public readonly exercise = signal<Exercise | undefined>(undefined);
    public readonly exampleSolutionInfo = signal<ExampleSolutionInfo | undefined>(undefined);

    readonly exerciseId = input<number>();
    readonly displayHeader = input(true);

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const exerciseId = this.exerciseId() ?? parseInt(params['exerciseId'], 10);

            const didExerciseChange = this.displayedExerciseId !== exerciseId;
            this.displayedExerciseId = exerciseId;
            if (didExerciseChange) {
                this.loadExercise();
            }
        });
    }

    loadExercise() {
        this.exercise.set(undefined);
        this.exerciseService.getExerciseForExampleSolution(this.displayedExerciseId!).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            const newExercise = exerciseResponse.body!;
            this.exercise.set(newExercise);
            this.exampleSolutionInfo.set(ExerciseService.extractExampleSolutionInfo(newExercise, this.artemisMarkdown));
        });
    }
}
