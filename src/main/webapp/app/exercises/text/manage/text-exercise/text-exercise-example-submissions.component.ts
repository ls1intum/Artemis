import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';

@Component({
    templateUrl: './text-exercise-example-submissions.component.html',
})
export class TextExerciseExampleSubmissionsComponent implements OnInit {
    textExercise: TextExercise;

    constructor(private jhiAlertService: JhiAlertService, private exampleSubmissionService: ExampleSubmissionService, private activatedRoute: ActivatedRoute) {}

    /**
     * Initializes all relevant data for text exercise
     */
    ngOnInit() {
        // Get the textExercise
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;
        });
    }

    /**
     * Deletes example submission
     * @param index in the example submissions array
     */
    deleteExampleSubmission(index: number) {
        let submissionId = this.textExercise.exampleSubmissions![index].id!;
        this.exampleSubmissionService.delete(submissionId).subscribe(
            () => {
                this.textExercise.exampleSubmissions!.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }
}
