import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { catchError, debounceTime, map, tap } from 'rxjs/operators';
import { ExerciseSubmissionState, ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { of, Subscription } from 'rxjs';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';

/**
 * This components provides two buttons to the instructor to interact with the students' submissions:
 * - Trigger builds for all student participations
 * - Trigger builds for failed student participations
 *
 * Also shows an info section next to the buttons about the number of building and failed submissions.
 */
@Component({
    selector: 'jhi-programming-exercise-instructor-submission-state',
    templateUrl: './programmming-exercise-instructor-submission-state.component.html',
})
export class ProgrammmingExerciseInstructorSubmissionStateComponent implements OnChanges, OnInit {
    ProgrammingSubmissionState = ProgrammingSubmissionState;

    @Input() exerciseId: number;

    hasFailedSubmissions = false;
    buildingSummary: { [submissionState: string]: number };
    isBuildingFailedSubmissions = false;

    resultEtaInMs: number;

    submissionStateSubscription: Subscription;
    resultEtaSubscription: Subscription;

    constructor(private programmingSubmissionService: ProgrammingSubmissionService, private modalService: NgbModal) {}

    ngOnInit(): void {
        this.resultEtaSubscription = this.programmingSubmissionService.getResultEtaInMs().subscribe(resultEta => (this.resultEtaInMs = resultEta));
    }

    /**
     * When the selected exercise changes, create a subscription to the complete submission state of the exercise.
     *
     * @param changes only relevant for change of exerciseId.
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.exerciseId && !!changes.exerciseId.currentValue) {
            this.submissionStateSubscription = this.programmingSubmissionService
                .getSubmissionStateOfExercise(this.exerciseId)
                .pipe(
                    map(this.sumSubmissionStates),
                    // If we would update the UI with every small change, it would seem very hectic. So we always take the latest value after 1 second.
                    debounceTime(1000),
                    tap((buildingSummary: { [submissionState: string]: number }) => {
                        this.buildingSummary = buildingSummary;
                        this.hasFailedSubmissions = this.buildingSummary[ProgrammingSubmissionState.HAS_FAILED_SUBMISSION] > 0;
                    }),
                )
                .subscribe();
        }
    }

    /**
     * Opens a modal in that the user has to confirm that he wants to trigger all participations.
     * This confirmation is needed as this is a performance intensive action and puts heavy load on our build system
     * and will create new results for the students (which could be confusing to them).
     */
    openTriggerAllModal() {
        const modalRef = this.modalService.open(ProgrammingExerciseInstructorTriggerAllDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseId = this.exerciseId;
    }

    /**
     * Retrieve the participation ids that have a failed submission and retry their build.
     */
    triggerBuildOfFailedSubmissions() {
        this.isBuildingFailedSubmissions = true;
        const failedSubmissionParticipations = this.programmingSubmissionService.getSubmissionCountByType(this.exerciseId, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION);
        this.programmingSubmissionService
            .triggerInstructorBuildForParticipationsOfExercise(this.exerciseId, failedSubmissionParticipations)
            .subscribe(() => (this.isBuildingFailedSubmissions = false));
    }

    private sumSubmissionStates = (buildState: ExerciseSubmissionState) =>
        Object.values(buildState).reduce((acc: { [state: string]: number }, { submissionState }) => {
            return { ...acc, [submissionState]: (acc[submissionState] || 0) + 1 };
        }, {});
}

@Component({
    template: `
        <form name="triggerAllForm" (ngSubmit)="confirmTrigger()">
            <div class="modal-header">
                <h4 class="modal-title" jhiTranslate="artemisApp.programmingExercise.resubmitAll">Trigger all</h4>
                <button type="button" class="close" data-dismiss="modal" aria-hidden="true" (click)="cancel()">&times;</button>
            </div>
            <div class="modal-body">
                <jhi-alert-error></jhi-alert-error>
                <p jhiTranslate="artemisApp.programmingExercise.resubmitAllDialog">
                    WARNING: Triggering all participations again is a very expensive operation. This action will start a CI build for every participation in this exercise!
                </p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal" (click)="cancel()">
                    <fa-icon [icon]="'ban'"></fa-icon>&nbsp;<span jhiTranslate="entity.action.cancel">Cancel</span>
                </button>
                <button type="submit" class="btn btn-danger"><fa-icon [icon]="'times'"></fa-icon>&nbsp;<span jhiTranslate="entity.action.confirm">Confirm</span></button>
            </div>
        </form>
    `,
})
export class ProgrammingExerciseInstructorTriggerAllDialogComponent {
    @Input() exerciseId: number;

    constructor(private activeModal: NgbActiveModal, private programmingSubmissionService: ProgrammingSubmissionService) {}

    cancel() {
        this.activeModal.dismiss('cancel');
    }

    confirmTrigger() {
        this.programmingSubmissionService
            .triggerInstructorBuildForAllParticipationsOfExercise(this.exerciseId)
            .pipe(catchError(() => of(null)))
            .subscribe();

        this.cancel();
    }
}
