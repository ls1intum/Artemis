import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumAetUiDialogComponent } from '../dialog/tumaet-ui-dialog.component';
import { TumAetUiButtonComponent } from '../button/tumaet-ui-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiConfirmationService } from './tumaet-ui-confirmation.service';

let nextConfirmDialogId = 0;

/** Renders requests from the nearest `TumAetUiConfirmationService` as modal decisions. */
@Component({
    selector: 'tumaet-ui-confirm-dialog',
    templateUrl: './tumaet-ui-confirm-dialog.component.html',
    imports: [TumAetUiDialogComponent, TumAetUiButtonComponent, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiConfirmDialogComponent {
    private readonly confirmationService = inject(TumAetUiConfirmationService);

    /** Static key used to select this dialog's confirmation requests. */
    readonly key = input<string>();

    protected readonly messageId = `tumaet-ui-confirm-dialog-message-${nextConfirmDialogId++}`;

    protected readonly request = computed(() => this.confirmationService.request(this.key()));

    protected readonly visible = computed(() => this.request() !== undefined);

    protected accept(): void {
        const request = this.request();
        // Clear before running the callback's side effects so the dialog is already closing.
        this.confirmationService.close(this.key());
        request?.accept();
    }

    protected reject(): void {
        const request = this.request();
        this.confirmationService.close(this.key());
        request?.reject?.();
    }

    protected onDialogHide(): void {
        const request = this.request();
        if (request) {
            this.confirmationService.close(this.key());
            request.reject?.();
        }
    }
}
