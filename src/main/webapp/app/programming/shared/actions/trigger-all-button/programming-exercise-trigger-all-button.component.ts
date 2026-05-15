import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/buttons/button/button.component';
import { faBan, faRedo, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { BuildRunState, ProgrammingBuildRunService } from 'app/programming/shared/services/programming-build-run.service';
import { hasDueDatePassed } from 'app/programming/shared/utils/programming-exercise.utils';

/**
 * A button that triggers the build for all participations of the given programming exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-trigger-all-button',
    template: `
        <jhi-button
            id="trigger-all-button"
            [disabled]="disabled()"
            [btnSize]="btnSize()"
            [btnType]="ButtonType.ERROR"
            [isLoading]="isTriggeringBuildAll()"
            [tooltip]="'artemisApp.programmingExercise.resubmitAllTooltip'"
            [tooltipPlacement]="TooltipPlacement.BOTTOM"
            [icon]="faRedo"
            [title]="'artemisApp.programmingExercise.resubmitAll'"
            [shouldToggle]="shouldToggle()"
            [toggleBreakpoint]="toggleBreakpoint()"
            [featureToggle]="FeatureToggle.ProgrammingExercises"
            (onClick)="openTriggerAllModal()"
        />
    `,
    imports: [ButtonComponent],
})
export class ProgrammingExerciseTriggerAllButtonComponent implements OnInit {
    private submissionService = inject(ProgrammingSubmissionService);
    private programmingBuildRunService = inject(ProgrammingBuildRunService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);

    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    TooltipPlacement = TooltipPlacement;
    readonly exercise = input<ProgrammingExercise>(undefined!);
    readonly disabled = input(false);
    readonly btnSize = input(ButtonSize.MEDIUM);
    readonly shouldToggle = input(false);
    readonly toggleBreakpoint = input<'md' | 'xl'>('xl');

    readonly onBuildTriggered = output();
    readonly isTriggeringBuildAll = signal(false);
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
        const dialogRef = this.dialogService.open(ProgrammingExerciseInstructorTriggerAllDialogComponent, {
            header: this.translateService.instant('artemisApp.programmingExercise.resubmitAll'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            data: {
                exerciseId: this.exercise().id,
                dueDatePassed: hasDueDatePassed(this.exercise()),
            },
        });
        dialogRef?.onClose.subscribe((confirmed: boolean | undefined) => {
            if (!confirmed) {
                return;
            }
            this.submissionService
                .triggerInstructorBuildForAllParticipationsOfExercise(this.exercise().id!)
                .pipe(catchError(() => of(undefined)))
                .subscribe(() => {
                    this.onBuildTriggered.emit();
                });
        });
    }

    private subscribeBuildRunUpdates() {
        this.programmingBuildRunService
            .getBuildRunUpdates(this.exercise().id!)
            .pipe(tap((buildRunState) => this.isTriggeringBuildAll.set(buildRunState === BuildRunState.RUNNING)))
            .subscribe();
    }
}

/**
 * The warning modal of the trigger all button that informs the user about the cost and effects of the operation.
 */
@Component({
    template: `
        <form name="triggerAllForm" (ngSubmit)="confirmTrigger()">
            <div class="modal-body">
                @if (dueDatePassed()) {
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
                <button type="button" class="btn btn-secondary" (click)="cancel()"><fa-icon [icon]="faBan" />&nbsp;<span jhiTranslate="entity.action.cancel">Cancel</span></button>
                <button type="submit" class="btn btn-danger">
                    <fa-icon [icon]="faTimes" />&nbsp;
                    <span jhiTranslate="entity.action.confirm">Confirm</span>
                </button>
            </div>
        </form>
    `,
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class ProgrammingExerciseInstructorTriggerAllDialogComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    readonly exerciseId = signal<number | undefined>(this.dialogConfig?.data?.exerciseId);
    readonly dueDatePassed = signal<boolean | undefined>(this.dialogConfig?.data?.dueDatePassed);

    // Icons
    faBan = faBan;
    faTimes = faTimes;

    cancel() {
        this.dialogRef.close(false);
    }

    confirmTrigger() {
        this.dialogRef.close(true);
    }
}
