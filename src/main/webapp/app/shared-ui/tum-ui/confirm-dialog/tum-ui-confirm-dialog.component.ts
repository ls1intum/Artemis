import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiConfirmationService } from 'app/shared-ui/tum-ui/confirm-dialog/tum-ui-confirmation.service';

// Per-instance counter for a unique message id, so `aria-describedby` never collides across dialogs on a page.
let nextConfirmDialogId = 0;

/**
 * Owned confirmation dialog, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-confirmdialog`: the display half of the confirm pattern. Place one in a
 * template and it renders whatever the paired {@link TumUiConfirmationService} has open — a modal
 * {@link TumUiDialogComponent} with an optional icon, the message, and cancel / confirm buttons. Confirming runs
 * the request's `accept`; dismissing (cancel button, Escape, ×) runs its optional `reject`. (Backdrop / mask click
 * does not dismiss — matching `p-confirmdialog`'s default.)
 *
 * Use `[key]` to scope a dialog to matching `confirm({ key })` calls when several confirm dialogs live on one page.
 * `[key]` is expected to be static; changing it while a request is open would drop the request without a callback.
 */
@Component({
    selector: 'tum-ui-confirm-dialog',
    templateUrl: './tum-ui-confirm-dialog.component.html',
    imports: [TumUiDialogComponent, TumUiButtonComponent, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiConfirmDialogComponent {
    /** Only render requests whose `key` matches this (both unset by default). Expected to be static. */
    readonly key = input<string>();

    private readonly confirmationService = inject(TumUiConfirmationService);

    /** Stable id for the message element, so the dialog can point `aria-describedby` at it. */
    protected readonly messageId = `tum-ui-confirm-dialog-message-${nextConfirmDialogId++}`;

    /** The active request for this dialog's key, or `undefined`. */
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

    /**
     * Fired when the dialog is dismissed via Escape / ×. `accept()` and `reject()` clear the request before this
     * runs, so a still-present request here means an implicit dismissal — treat it as a reject.
     */
    protected onDialogHide(): void {
        const request = this.request();
        if (request) {
            this.confirmationService.close(this.key());
            request.reject?.();
        }
    }
}
