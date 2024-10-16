import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExampleSubmissionImportComponent } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import.component';
import { Submission } from 'app/entities/submission.model';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { faExclamationTriangle, faFont, faPlus, faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    templateUrl: 'example-submissions.component.html',
})
export class ExampleSubmissionsComponent implements OnInit, OnDestroy {
    private alertService = inject(AlertService);
    private exampleSubmissionService = inject(ExampleSubmissionService);
    private activatedRoute = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private modalService = inject(NgbModal);
    private accountService = inject(AccountService);

    exercise: Exercise;
    readonly exerciseType = ExerciseType;
    createdExampleAssessment: boolean[];

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFont = faFont;
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Initializes all relevant data for the exercise
     */
    ngOnInit() {
        // Get the exercise
        this.activatedRoute.data.subscribe(({ exercise }) => {
            exercise.course = getCourseFromExercise(exercise);
            this.accountService.setAccessRightsForCourse(exercise.course);
            this.exercise = exercise;

            this.createdExampleAssessment =
                this.exercise.exampleSubmissions?.map((exampleSubmission) => exampleSubmission.submission?.results?.some((result) => result.exampleResult) ?? false) ?? [];
        });
        this.exercise?.exampleSubmissions?.forEach((exampleSubmission) => {
            if (exampleSubmission.submission) {
                exampleSubmission.submission.submissionSize = this.exampleSubmissionService.getSubmissionSize(exampleSubmission.submission, this.exercise);
            }
        });
    }

    /**
     * Closes open modal on component destroy
     */
    ngOnDestroy() {
        if (this.modalService?.hasOpenModals()) {
            this.modalService.dismissAll();
        }
    }

    /**
     * Deletes example submission
     * @param index in the example submissions array
     */
    deleteExampleSubmission(index: number) {
        const submissionId = this.exercise.exampleSubmissions![index].id!;
        this.exampleSubmissionService.delete(submissionId).subscribe({
            next: () => {
                this.exercise.exampleSubmissions!.splice(index, 1);
                this.createdExampleAssessment.splice(index, 1);
            },
            error: (error: HttpErrorResponse) => {
                this.alertService.error(error.message);
            },
        });
    }

    /**
     * Navigates to the detail view of the example submission
     * @param exampleSubmissionId id of the submission or new for a new submission
     */
    getLinkToExampleSubmission(exampleSubmissionId: number | 'new') {
        if (!this.exercise.exerciseGroup) {
            return ['/course-management', this.exercise.course!.id, this.exercise.type + '-exercises', this.exercise.id, 'example-submissions', exampleSubmissionId];
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
                exampleSubmissionId,
            ];
        }
    }

    /**
     * Opens the import module for example submission
     * Then invokes import api for selected submission
     */
    openImportModal() {
        const exampleSubmissionImportModalRef = this.modalService.open(ExampleSubmissionImportComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        exampleSubmissionImportModalRef.componentInstance.exercise = this.exercise;
        exampleSubmissionImportModalRef.result.then((selectedSubmission: Submission) => {
            this.exampleSubmissionService.import(selectedSubmission.id!, this.exercise.id!).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.exampleSubmission.submitSuccessful');
                    location.reload();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        });
    }
}
