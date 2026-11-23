import { Component, computed, inject } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiDialogConfig, TumAetUiDialogRef, TumAetUiProgressBarComponent } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheckCircle, faExclamationTriangle, faSpinner } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { EntityDeletionProgressDialogData, EntityDeletionStatus } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';

/**
 * Shows the live progress of an exercise, exam or exercise group deletion that the server runs in the background. While the deletion runs, the dialog
 * only closes through "Continue in background", which leaves the deletion running and makes the service report its end with an alert. Once it ended,
 * the instructor has to dismiss the result, so a failure cannot be overlooked.
 * <p>
 * It is opened by {@link EntityDeletionService}, which also feeds it with the progress messages.
 */
@Component({
    selector: 'jhi-entity-deletion-progress-dialog',
    imports: [TumAetUiButtonDirective, TumAetUiProgressBarComponent, FaIconComponent, ArtemisTranslatePipe],
    templateUrl: './entity-deletion-progress-dialog.component.html',
})
export class EntityDeletionProgressDialogComponent {
    private readonly dialogRef = inject(TumAetUiDialogRef);
    private readonly dialogConfig = inject<TumAetUiDialogConfig<EntityDeletionProgressDialogData>>(TumAetUiDialogConfig);

    protected readonly faSpinner = faSpinner;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    private readonly data = this.dialogConfig.data!;

    protected readonly target = this.data.target;
    protected readonly progress = computed(() => this.data.progress());
    protected readonly isCompleted = computed(() => this.progress()?.status === EntityDeletionStatus.COMPLETED);
    protected readonly isFailed = computed(() => this.progress()?.status === EntityDeletionStatus.FAILED);
    protected readonly isRunning = computed(() => !this.isCompleted() && !this.isFailed());

    protected readonly progressPercentage = computed(() => Math.min(100, Math.round(this.progress()?.weightedProgressPercent ?? 0)));
    /** Translation key of the current step, if the server reported one. */
    protected readonly stepKey = computed(() => {
        const step = this.progress()?.currentStep;
        return step ? `artemisApp.deletionProgress.step.${step}` : undefined;
    });
    protected readonly showItems = computed(() => this.isRunning() && (this.progress()?.totalItems ?? 0) > 0);
    protected readonly showSteps = computed(() => this.isRunning() && (this.progress()?.totalSteps ?? 0) > 0);
    protected readonly stepNumber = computed(() => Math.min((this.progress()?.stepsCompleted ?? 0) + 1, this.progress()?.totalSteps ?? 0));

    /**
     * Estimated remaining time, derived from the weighted progress, which reflects the real cost of the steps better than counting them.
     */
    protected readonly remainingTime = computed<string | undefined>(() => {
        const progress = this.progress();
        if (!progress || !this.isRunning() || !progress.startedAt || progress.weightedProgressPercent <= 0 || progress.weightedProgressPercent >= 100) {
            return undefined;
        }
        const passedSeconds = dayjs().diff(progress.startedAt, 'second');
        if (passedSeconds <= 0) {
            return undefined;
        }
        const remainingSeconds = Math.round((passedSeconds / progress.weightedProgressPercent) * (100 - progress.weightedProgressPercent));
        const hours = Math.floor(remainingSeconds / 3600);
        const minutes = Math.floor((remainingSeconds % 3600) / 60);
        const seconds = remainingSeconds % 60;
        if (hours > 0) {
            return `${hours}h ${minutes}m`;
        }
        return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
    });

    /** Closes the dialog of a finished deletion. */
    protected dismiss(): void {
        this.dialogRef.close();
    }

    /** Closes the dialog while the deletion runs; the deletion itself is not affected. */
    protected continueInBackground(): void {
        this.data.continueInBackground();
        this.dialogRef.close();
    }
}
