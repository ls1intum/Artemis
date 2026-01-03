import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { ExampleParticipationService } from 'app/assessment/shared/services/example-participation.service';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExampleSubmissionImportComponent } from 'app/exercise/example-submission/example-submission-import/example-submission-import.component';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { onError } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { faExclamationTriangle, faFont, faPlus, faQuestionCircle, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ResultComponent } from '../result/result.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExampleParticipation } from 'app/exercise/shared/entities/participation/example-participation.model';

@Component({
    templateUrl: 'example-submissions.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, NgbTooltip, ResultComponent, ArtemisTranslatePipe],
})
export class ExampleSubmissionsComponent implements OnInit, OnDestroy {
    private alertService = inject(AlertService);
    private exampleParticipationService = inject(ExampleParticipationService);
    private activatedRoute = inject(ActivatedRoute);
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
                this.exercise.exampleParticipations?.map(
                    (exampleParticipation) => this.getSubmission(exampleParticipation)?.results?.some((result) => result.exampleResult) ?? false,
                ) ?? [];
        });
        this.exercise?.exampleParticipations?.forEach((exampleParticipation) => {
            const submission = this.getSubmission(exampleParticipation);
            if (submission) {
                submission.submissionSize = this.exampleParticipationService.getSubmissionSize(submission, this.exercise);
            }
        });
    }

    /**
     * Gets the first submission from an example participation.
     */
    getSubmission(exampleParticipation: ExampleParticipation): Submission | undefined {
        return this.exampleParticipationService.getSubmission(exampleParticipation);
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
     * Deletes example participation
     * @param index in the example participations array
     */
    deleteExampleParticipation(index: number) {
        const participationId = this.exercise.exampleParticipations![index].id!;
        this.exampleParticipationService.delete(participationId).subscribe({
            next: () => {
                this.exercise.exampleParticipations!.splice(index, 1);
                this.createdExampleAssessment.splice(index, 1);
            },
            error: (error: HttpErrorResponse) => {
                this.alertService.error(error.message);
            },
        });
    }

    /**
     * Navigates to the detail view of the example submission
     * @param exampleParticipationId id of the participation or new for a new one
     */
    getLinkToExampleSubmission(exampleParticipationId: number | 'new') {
        if (!this.exercise.exerciseGroup) {
            return ['/course-management', this.exercise.course!.id, this.exercise.type + '-exercises', this.exercise.id, 'example-submissions', exampleParticipationId];
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
                exampleParticipationId,
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
            this.exampleParticipationService.import(selectedSubmission.id!, this.exercise.id!).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.exampleSubmission.submitSuccessful');
                    location.reload();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        });
    }
}
