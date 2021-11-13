import { Component, OnDestroy, OnInit } from '@angular/core';
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
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    templateUrl: 'example-submissions.component.html',
})
export class ExampleSubmissionsComponent implements OnInit, OnDestroy {
    exercise: Exercise;
    submissionSizeHint?: string;

    constructor(
        private alertService: AlertService,
        private exampleSubmissionService: ExampleSubmissionService,
        private activatedRoute: ActivatedRoute,
        private courseService: CourseManagementService,
        private modalService: NgbModal,
        private accountService: AccountService,
        private stringCountService: StringCountService,
        private artemisTranslatePipe: ArtemisTranslatePipe,
    ) {}

    /**
     * Initializes all relevant data for the exercise
     */
    ngOnInit() {
        // Get the exercise
        this.activatedRoute.data.subscribe(({ exercise }) => {
            exercise.course = getCourseFromExercise(exercise);
            this.accountService.setAccessRightsForCourse(exercise.course);
            this.exercise = exercise;
        });
        if (this.exercise.type === ExerciseType.TEXT) {
            this.submissionSizeHint = this.artemisTranslatePipe.transform('artemisApp.exampleSubmission.textSubmissionSizeHint');
        } else if (this.exercise.type === ExerciseType.MODELING) {
            this.submissionSizeHint = this.artemisTranslatePipe.transform('artemisApp.exampleSubmission.modelingSubmissionSizeHint');
        }
    }

    /**
     * Closes open modal on component destroy
     */
    ngOnDestroy() {
        if (this.modalService.hasOpenModals()) {
            this.modalService.dismissAll();
        }
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
            this.exampleSubmissionService.import(selectedSubmission.id!, this.exercise.id!).subscribe(
                () => {
                    this.alertService.success('artemisApp.exampleSubmission.submitSuccessful');
                    location.reload();
                },
                (error: HttpErrorResponse) => onError(this.alertService, error),
            );
        });
    }

    getSubmissionSize(submission?: Submission) {
        if (submission && this.exercise.type === ExerciseType.TEXT) {
            return this.stringCountService.countWords((submission as TextSubmission).text);
        } else if (submission && this.exercise.type === ExerciseType.MODELING) {
            const elements = JSON.parse((submission as ModelingSubmission).model!).elements as string[];
            return elements.length;
        }
        return 0;
    }
}
