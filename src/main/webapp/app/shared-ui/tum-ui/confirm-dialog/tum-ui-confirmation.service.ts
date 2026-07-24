import { Injectable, signal } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TumUiButtonSeverity } from 'app/shared-ui/tum-ui/button/tum-ui-button.variants';

/**
 * A single confirmation request handed to {@link TumUiConfirmationService.confirm}. Drop-in for the subset of
 * PrimeNG's `Confirmation` that Artemis uses, normalized to one shape (PrimeNG exposes both the legacy
 * `acceptLabel` / `acceptButtonStyleClass` and the newer `acceptButtonProps` styles — here it is just `*Label`
 * + `*Severity`).
 */
export interface TumUiConfirmationRequest {
    /** Dialog title. */
    header: string;
    /** Body text shown next to the optional icon. */
    message: string;
    /** Invoked when the user confirms. */
    accept: () => void;
    /** Invoked when the user cancels / dismisses (Escape, mask click, cancel button). Optional, like PrimeNG. */
    reject?: () => void;
    /** Confirm button label (default `'Yes'`). */
    acceptLabel?: string;
    /** Cancel button label (default `'No'`). */
    rejectLabel?: string;
    /** Confirm button severity (default `'primary'`). */
    acceptSeverity?: TumUiButtonSeverity;
    /** Cancel button severity (default `'secondary'`). */
    rejectSeverity?: TumUiButtonSeverity;
    /** Optional leading icon shown before the message. */
    icon?: IconProp;
    /** Scopes the request to a `<tum-ui-confirm-dialog [key]>` with the same key (parity with `[key]`). */
    key?: string;
}

/**
 * Owned confirmation service, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `ConfirmationService`: call {@link confirm} with a request and the paired
 * {@link TumUiConfirmDialogComponent} (`<tum-ui-confirm-dialog>`) placed in the same template renders it. Provide
 * it per component (like PrimeNG) so each `<tum-ui-confirm-dialog>` has its own request stream.
 */
@Injectable()
export class TumUiConfirmationService {
    private readonly request = signal<TumUiConfirmationRequest | undefined>(undefined);
    /** The pending confirmation request, or `undefined` when none is open. Read by the confirm-dialog component. */
    readonly activeRequest = this.request.asReadonly();

    /** Open a confirmation dialog for the given request. */
    confirm(request: TumUiConfirmationRequest): void {
        this.request.set(request);
    }

    /** Clear the pending request (called by the dialog once a decision is made). */
    close(): void {
        this.request.set(undefined);
    }
}
