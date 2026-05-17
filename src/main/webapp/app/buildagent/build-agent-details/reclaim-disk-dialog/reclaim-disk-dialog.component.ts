import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { BytesPipe } from 'app/shared/pipes/bytes.pipe';

/**
 * Data passed into the dialog via PrimeNG's {@code DynamicDialogConfig.data}.
 */
export interface ReclaimDiskDialogInput {
    agentName: string;
    pauseGracePeriodSeconds?: number;
    diskTotalBytes?: number;
    diskUsableBytes?: number;
    mavenCacheBytes?: number;
    gradleCacheBytes?: number;
    dockerUnusedImageBytes?: number;
    dockerUnusedImageCount?: number;
}

/**
 * Result returned via {@code DynamicDialogRef.close} when the operator confirms. {@code undefined} on cancel.
 */
export interface ReclaimDiskDialogResult {
    wipeMaven: boolean;
    wipeGradle: boolean;
    clearDocker: boolean;
}

/**
 * Confirmation dialog for the admin "Reclaim disk" action on the build-agent details page.
 *
 * The dialog spells out the pause-drain-act-resume cycle and shows the current disk and per-component sizes so
 * the operator can decide what to delete with full information. The three checkboxes are independent — any
 * subset can be selected, and the parent component dispatches one REST call per selected option. The Confirm
 * button is enabled only when (a) at least one option is selected and (b) the operator has typed the
 * confirmation word verbatim — same UX as the existing {@code BuildAgentClearDistributedDataComponent}, which
 * we mirror but rebuild on PrimeNG's {@code DialogService} per the project guideline that {@code NgbModal} is
 * deprecated.
 */
@Component({
    selector: 'jhi-reclaim-disk-dialog',
    templateUrl: './reclaim-disk-dialog.component.html',
    styleUrl: './reclaim-disk-dialog.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [CommonModule, FormsModule, TranslateDirective, ArtemisTranslatePipe, BytesPipe],
})
export class ReclaimDiskDialogComponent {
    /** Operator must type this word verbatim into the input field to enable the Confirm button. */
    public static readonly CONFIRM_WORD = 'RECLAIM';

    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    readonly data: ReclaimDiskDialogInput = this.dialogConfig.data ?? { agentName: '' };

    // Reactive checkbox state.
    readonly wipeMaven = signal(false);
    readonly wipeGradle = signal(false);
    readonly clearDocker = signal(false);

    /** The type-CONFIRM input value. */
    readonly typedConfirm = signal('');

    /** Enable Confirm only when at least one option is checked AND the confirmation word matches. */
    readonly canConfirm = computed(() => {
        const anySelected = this.wipeMaven() || this.wipeGradle() || this.clearDocker();
        return anySelected && this.typedConfirm() === ReclaimDiskDialogComponent.CONFIRM_WORD;
    });

    readonly confirmWord = ReclaimDiskDialogComponent.CONFIRM_WORD;

    cancel(): void {
        this.dialogRef.close(undefined);
    }

    confirm(): void {
        if (!this.canConfirm()) {
            return;
        }
        const result: ReclaimDiskDialogResult = {
            wipeMaven: this.wipeMaven(),
            wipeGradle: this.wipeGradle(),
            clearDocker: this.clearDocker(),
        };
        this.dialogRef.close(result);
    }
}
