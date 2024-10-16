import { Component, Input, OnInit, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Exercise } from 'app/entities/exercise.model';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';

@Component({
    selector: 'jhi-example-solution',
    templateUrl: './example-solution.component.html',
})
export class ExampleSolutionComponent implements OnInit {
    private exerciseService = inject(ExerciseService);
    private route = inject(ActivatedRoute);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    private displayedExerciseId: number;
    public exercise?: Exercise;
    public exampleSolutionInfo?: ExampleSolutionInfo;

    @Input() exerciseId?: number;
    @Input() displayHeader?: boolean = true;

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const exerciseId = this.exerciseId || parseInt(params['exerciseId'], 10);

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
