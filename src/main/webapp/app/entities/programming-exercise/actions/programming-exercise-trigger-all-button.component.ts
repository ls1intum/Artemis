import { Component, Input, EventEmitter, Output } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { of } from 'rxjs';
import { NgbModal, NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-programming-exercise-trigger-all-button',
    template: `
        <button
            id="trigger-all-button"
            class="btn btn-danger ml-3"
            [disabled]="disabled || isTriggeringBuildAll"
            (click)="openTriggerAllModal()"
            [ngbTooltip]="'artemisApp.programmingExercise.resubmitAllTooltip' | translate"
        >
            <fa-icon icon="redo" [spin]="isTriggeringBuildAll"></fa-icon>
            <span jhiTranslate="artemisApp.programmingExercise.resubmitAll">Trigger all</span>
        </button>
    `,
})
export class ProgrammingExerciseTriggerAllButtonComponent {
    @Input() exerciseId: number;
    @Input() disabled = false;
    @Output() onBuildTriggered = new EventEmitter();
    isTriggeringBuildAll = false;

    constructor(private submissionService: ProgrammingSubmissionService, private modalService: NgbModal) {}

    /**
     * Opens a modal in that the user has to confirm that he wants to trigger all participations.
     * This confirmation is needed as this is a performance intensive action and puts heavy load on our build system
     * and will create new results for the students (which could be confusing to them).
     */
    openTriggerAllModal() {
        const modalRef = this.modalService.open(ProgrammingExerciseInstructorTriggerAllDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.result.then(() => {
            this.isTriggeringBuildAll = true;
            this.submissionService
                .triggerInstructorBuildForAllParticipationsOfExercise(this.exerciseId)
                .pipe(catchError(() => of(null)))
                .subscribe(() => {
                    this.isTriggeringBuildAll = false;
                    this.onBuildTriggered.emit();
                });
        });
    }
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
                <button type="submit" class="btn btn-danger">
                    <fa-icon [icon]="'times'"></fa-icon>&nbsp;
                    <span jhiTranslate="entity.action.confirm">Confirm</span>
                </button>
            </div>
        </form>
    `,
})
export class ProgrammingExerciseInstructorTriggerAllDialogComponent {
    @Input() exerciseId: number;

    constructor(private activeModal: NgbActiveModal) {}

    cancel() {
        this.activeModal.dismiss('cancel');
    }

    confirmTrigger() {
        this.activeModal.close();
    }
}
