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
    /** Invoked when the user cancels / dismisses (Escape, cancel button, ×). Optional, like PrimeNG. */
    reject?: () => void;
    /** Confirm button label. Required so the caller always supplies a localized string (the kit has no i18n layer). */
    acceptLabel: string;
    /** Cancel button label. Required so the caller always supplies a localized string. */
    rejectLabel: string;
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
    // One pending request per key (unkeyed dialogs use the `undefined` key), so several keyed
    // `<tum-ui-confirm-dialog>`s sharing this service stay independent — opening one never silently
    // discards another's request (which would skip its `reject`).
    private readonly requests = signal<ReadonlyMap<string | undefined, TumUiConfirmationRequest>>(new Map());

    /** The pending request for the given key, or `undefined`. Reactive — read it from a `computed`. */
    request(key: string | undefined): TumUiConfirmationRequest | undefined {
        return this.requests().get(key);
    }

    /** Open a confirmation dialog for the given request (routed by its `key`). */
    confirm(request: TumUiConfirmationRequest): void {
        const next = new Map(this.requests());
        next.set(request.key, request);
        this.requests.set(next);
    }

    /** Clear the pending request for the given key (called by the dialog once a decision is made). */
    close(key: string | undefined): void {
        const next = new Map(this.requests());
        next.delete(key);
        this.requests.set(next);
    }
}
