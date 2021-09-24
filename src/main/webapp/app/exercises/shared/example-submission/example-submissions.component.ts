import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Exercise, getCourseFromExercise } from 'app/entities/exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    templateUrl: 'example-submissions.component.html',
})
export class ExampleSubmissionsComponent implements OnInit {
    exercise: Exercise;

    constructor(
        private alertService: AlertService,
        private exampleSubmissionService: ExampleSubmissionService,
        private activatedRoute: ActivatedRoute,
        private courseService: CourseManagementService,
    ) {}

    /**
     * Initializes all relevant data for the exercise
     */
    ngOnInit() {
        // Get the exercise
        this.activatedRoute.data.subscribe(({ exercise }) => {
            exercise.course = getCourseFromExercise(exercise);
            this.courseService.checkAndSetCourseRights(exercise.course);
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
                this.alertService.error(error.message);
            },
        );
    }

    /**
     * Navigates to the detail view of the example submission
     * @param id id of the submission or new for a new submission
     */
    getLinkToExampleSubmission(id: number | 'new') {
        if (!this.exercise.exerciseGroup) {
            return ['/course-management', this.exercise.course!.id, this.exercise.type + '-exercises', this.exercise.id, 'example-submissions', id];
        } else {
            return [
                '/course-management',
                this.exercise.course!.id,
                'exams',
                this.exercise.exerciseGroup!.exam!.id,
                'exercise-groups',
                this.exercise.exerciseGroup!.id,
                this.exercise.type + '-exercises',
                this.exercise.id,
                'example-submissions',
                id,
            ];
        }
    }
}
