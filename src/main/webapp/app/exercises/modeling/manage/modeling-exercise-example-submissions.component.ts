import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

@Component({
    templateUrl: './modeling-exercise-example-submissions.component.html',
})
export class ModelingExerciseExampleSubmissionsComponent implements OnInit {
    modelingExercise: ModelingExercise;

    constructor(private jhiAlertService: JhiAlertService, private exampleSubmissionService: ExampleSubmissionService, private activatedRoute: ActivatedRoute) {}

    /**
     * Initializes all relevant data for text exercise
     */
    ngOnInit() {
        // Get the modelingExercise
        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;
        });
    }

    /**
     * Deletes example submission
     * @param index in the example submissions array
     */
    deleteExampleSubmission(index: number) {
        let submissionId = this.modelingExercise.exampleSubmissions![index].id!;
        this.exampleSubmissionService.delete(submissionId).subscribe(
            () => {
                this.modelingExercise.exampleSubmissions!.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }
}
