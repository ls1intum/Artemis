import { Component, OnInit } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Exercise } from 'app/entities/exercise.model';
import { ExampleSolutionInfo, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-example-solution',
    templateUrl: './example-solution.component.html',
})
export class ExampleSolutionComponent implements OnInit {
    private exerciseId: number;
    public exercise?: Exercise;
    public exampleSolutionInfo?: ExampleSolutionInfo;

    constructor(private exerciseService: ExerciseService, private route: ActivatedRoute) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            const didExerciseChange = this.exerciseId !== parseInt(params['exerciseId'], 10);
            this.exerciseId = parseInt(params['exerciseId'], 10);
            if (didExerciseChange) {
                this.loadExercise();
            }
        });
    }

    loadExercise() {
        this.exercise = undefined;
        this.exerciseService.getExerciseForExampleSolution(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            const newExercise = exerciseResponse.body!;
            this.exercise = newExercise;
            this.exampleSolutionInfo = this.exerciseService.extractExampleSolutionInfo(newExercise);
        });
    }
}
