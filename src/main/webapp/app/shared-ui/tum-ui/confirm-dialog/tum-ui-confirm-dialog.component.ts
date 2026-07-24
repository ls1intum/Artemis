import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumUiDialogComponent } from 'app/shared-ui/tum-ui/dialog/tum-ui-dialog.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiConfirmationService } from 'app/shared-ui/tum-ui/confirm-dialog/tum-ui-confirmation.service';

/**
 * Owned confirmation dialog, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-confirmdialog`: the display half of the confirm pattern. Place one in a
 * template and it renders whatever the paired {@link TumUiConfirmationService} has open — a modal
 * {@link TumUiDialogComponent} with an optional icon, the message, and cancel / confirm buttons. Confirming runs
 * the request's `accept`; dismissing (cancel button, Escape, mask, ×) runs its optional `reject`.
 *
 * Use `[key]` to scope a dialog to matching `confirm({ key })` calls when several confirm dialogs live on one page.
 */
@Component({
    selector: 'tum-ui-confirm-dialog',
    templateUrl: './tum-ui-confirm-dialog.component.html',
    imports: [TumUiDialogComponent, TumUiButtonComponent, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiConfirmDialogComponent {
    /** Only render requests whose `key` matches this (both unset by default). */
    readonly key = input<string>();

    private readonly confirmationService = inject(TumUiConfirmationService);

    /** The active request for this dialog (matching `key`), or `undefined`. */
    protected readonly request = computed(() => {
        const active = this.confirmationService.activeRequest();
        if (!active) {
            return undefined;
        }
        return (active.key ?? undefined) === (this.key() ?? undefined) ? active : undefined;
    });

    protected readonly visible = computed(() => this.request() !== undefined);

    protected accept(): void {
        const request = this.request();
        // Clear before running the callback's side effects so the dialog is already closing.
        this.confirmationService.close();
        request?.accept();
    }

    protected reject(): void {
        const request = this.request();
        this.confirmationService.close();
        request?.reject?.();
    }

    /**
     * Fired when the dialog is dismissed via Escape / mask / ×. `accept()` and `reject()` clear the request before
     * this runs, so a still-present request here means an implicit dismissal — treat it as a reject.
     */
    protected onDialogHide(): void {
        const request = this.request();
        if (request) {
            this.confirmationService.close();
            request.reject?.();
        }
    }
}
