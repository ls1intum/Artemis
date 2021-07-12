import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Exercise, getCourseFromExercise } from 'app/entities/exercise.model';

@Component({
    templateUrl: 'example-submissions.component.html',
})
export class ExampleSubmissionsComponent implements OnInit {
    exercise: Exercise;

    constructor(
        private jhiAlertService: JhiAlertService,
        private exampleSubmissionService: ExampleSubmissionService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
    ) {}

    /**
     * Initializes all relevant data for the exercise
     */
    ngOnInit() {
        // Get the exercise
        this.activatedRoute.data.subscribe(({ exercise }) => {
            exercise.course = getCourseFromExercise(exercise);
            this.exercise = exercise;
        });
    }

    /**
     * Deletes example submission
     * @param index in the example submissions array
     */
    deleteExampleSubmission(index: number) {
        const submissionId = this.exercise.exampleSubmissions![index].id!;
        this.exampleSubmissionService.delete(submissionId).subscribe(
            () => {
                this.exercise.exampleSubmissions!.splice(index, 1);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
            },
        );
    }

    /**
     * Navigates to the detail view of the example submission
     * @param id id of the submission or new for a new submission
     */
    navigateToExampleSubmission(id: number | 'new') {
        this.router.navigate([id], { relativeTo: this.activatedRoute });
    }
}
