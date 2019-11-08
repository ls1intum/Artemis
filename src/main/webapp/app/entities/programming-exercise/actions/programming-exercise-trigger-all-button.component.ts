import { Component, Input, EventEmitter, Output, OnChanges, OnInit } from '@angular/core';
import { catchError, filter, take, tap } from 'rxjs/operators';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { of } from 'rxjs';
import { NgbModal, NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonType } from 'app/shared/components';
import { ProgrammingExerciseWebsocketService } from 'app/entities/programming-exercise/services/programming-exercise-websocket.service';
import { ProgrammingBuildRunService } from 'app/programming-submission/programming-build-run.service';

/**
 * A button that triggers the build for all participations of the given programming exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-trigger-all-button',
    template: `
        <jhi-button
            id="trigger-all-button"
            class="ml-3"
            [disabled]="disabled"
            [btnType]="ButtonType.ERROR"
            [isLoading]="isTriggeringBuildAll"
            [tooltip]="'artemisApp.programmingExercise.resubmitAllTooltip'"
            [icon]="'redo'"
            [title]="'artemisApp.programmingExercise.resubmitAll'"
            (onClick)="openTriggerAllModal()"
        >
        </jhi-button>
    `,
})
export class ProgrammingExerciseTriggerAllButtonComponent implements OnInit {
    ButtonType = ButtonType;
    @Input() exerciseId: number;
    @Input() disabled = false;
    @Output() onBuildTriggered = new EventEmitter();
    isTriggeringBuildAll = false;

    constructor(private submissionService: ProgrammingSubmissionService, private programmingBuildRunService: ProgrammingBuildRunService, private modalService: NgbModal) {}

    ngOnInit() {
        // The info that the builds were triggered comes from a websocket channel.
        this.subscribeBuildRunUpdates();
    }

    /**
     * Opens a modal in that the user has to confirm to trigger all participations.
     * This confirmation is needed as this is a performance intensive action and puts heavy load on our build system
     * and will create new results for the students (which could be confusing to them).
     */
    openTriggerAllModal() {
        const modalRef = this.modalService.open(ProgrammingExerciseInstructorTriggerAllDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseId = this.exerciseId;
        modalRef.result.then(() => {
            this.programmingBuildRunService.emitBuildRunUpdate(this.exerciseId, true);
            this.submissionService
                .triggerInstructorBuildForAllParticipationsOfExercise(this.exerciseId)
                .pipe(catchError(() => of(null)))
                .subscribe(() => {
                    this.onBuildTriggered.emit();
                });
        });
    }

    private subscribeBuildRunUpdates() {
        this.programmingBuildRunService
            .getBuildRunUpdates(this.exerciseId)
            .pipe(tap(buildIsRunning => (this.isTriggeringBuildAll = buildIsRunning)))
            .subscribe();
    }
}

/**
 * The warning modal of the trigger all button that informs the user about the cost and effects of the operation.
 */
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
