import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumUiDialogComponent } from '../dialog/tum-ui-dialog.component';
import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiConfirmationService } from './tum-ui-confirmation.service';

let nextConfirmDialogId = 0;

/** Renders requests from the nearest `TumUiConfirmationService` as modal decisions. */
@Component({
    selector: 'tum-ui-confirm-dialog',
    host: { '[attr.data-slot]': '"confirm-dialog"' },
    templateUrl: './tum-ui-confirm-dialog.component.html',
    imports: [TumUiDialogComponent, TumUiButtonComponent, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiConfirmDialogComponent {
    /** Static key used to select this dialog's confirmation requests. */
    readonly key = input<string>();

    private readonly confirmationService = inject(TumUiConfirmationService);

    protected readonly messageId = `tum-ui-confirm-dialog-message-${nextConfirmDialogId++}`;

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
