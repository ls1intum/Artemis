import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { catchError, tap } from 'rxjs/operators';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { of } from 'rxjs';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { hasDueDatePassed } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { BuildRunState, ProgrammingBuildRunService } from 'app/exercises/programming/participate/programming-build-run.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ButtonType } from 'app/shared/components/button.component';
import { faBan, faRedo, faTimes } from '@fortawesome/free-solid-svg-icons';

/**
 * A button that triggers the build for all participations of the given programming exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-trigger-all-button',
    template: `
        <jhi-button
            id="trigger-all-button"
            class="ms-3"
            [disabled]="disabled"
            [btnType]="ButtonType.ERROR"
            [isLoading]="isTriggeringBuildAll"
            [tooltip]="'artemisApp.programmingExercise.resubmitAllTooltip'"
            [icon]="faRedo"
            [title]="'artemisApp.programmingExercise.resubmitAll'"
            [featureToggle]="FeatureToggle.ProgrammingExercises"
            (onClick)="openTriggerAllModal()"
        />
    `,
})
export class ProgrammingExerciseTriggerAllButtonComponent implements OnInit {
    private submissionService = inject(ProgrammingSubmissionService);
    private programmingBuildRunService = inject(ProgrammingBuildRunService);
    private modalService = inject(NgbModal);

    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;
    @Input() exercise: ProgrammingExercise;
    @Input() disabled = false;
    @Output() onBuildTriggered = new EventEmitter();
    isTriggeringBuildAll = false;
    // Icons
    faRedo = faRedo;

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
        modalRef.componentInstance.exerciseId = this.exercise.id;
        modalRef.componentInstance.dueDatePassed = hasDueDatePassed(this.exercise);
        modalRef.result.then(() => {
            this.submissionService
                .triggerInstructorBuildForAllParticipationsOfExercise(this.exercise.id!)
                .pipe(catchError(() => of(undefined)))
                .subscribe(() => {
                    this.onBuildTriggered.emit();
                });
        });
    }

    private subscribeBuildRunUpdates() {
        this.programmingBuildRunService
            .getBuildRunUpdates(this.exercise.id!)
            .pipe(tap((buildRunState) => (this.isTriggeringBuildAll = buildRunState === BuildRunState.RUNNING)))
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
                <button type="button" class="btn-close" data-dismiss="modal" aria-hidden="true" (click)="cancel()"></button>
            </div>
            <div class="modal-body">
                @if (dueDatePassed) {
                    <p class="text-danger font-weight-bold" jhiTranslate="artemisApp.programmingExercise.resubmitAllConfirmAfterDueDate">
                        The due date has passed, some of the student submissions might have received manual results created by teaching assistants. Newly generated automatic
                        results would replace the manual results as the latest result for the participation.
                    </p>
                }
                <p jhiTranslate="artemisApp.programmingExercise.resubmitAllDialog">
                    WARNING: Triggering all participations again is a very expensive operation. This action will start a CI build for every participation in this exercise!
                </p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal" (click)="cancel()">
                    <fa-icon [icon]="faBan" />&nbsp;<span jhiTranslate="entity.action.cancel">Cancel</span>
                </button>
                <button type="submit" class="btn btn-danger">
                    <fa-icon [icon]="faTimes" />&nbsp;
                    <span jhiTranslate="entity.action.confirm">Confirm</span>
                </button>
            </div>
        </form>
    `,
})
export class ProgrammingExerciseInstructorTriggerAllDialogComponent {
    private activeModal = inject(NgbActiveModal);

    @Input() exerciseId: number;
    @Input() dueDatePassed: boolean;

    // Icons
    faBan = faBan;
    faTimes = faTimes;

    cancel() {
        this.activeModal.dismiss('cancel');
    }

    confirmTrigger() {
        this.activeModal.close();
    }
}
