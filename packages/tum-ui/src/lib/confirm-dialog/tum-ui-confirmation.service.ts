import { Injectable, signal } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TumUiButtonSeverity } from '../button/tum-ui-button.variants';

/** Content and callbacks for one confirmation decision. */
export interface TumUiConfirmationRequest {
    /** Dialog title. */
    header: string;
    /** Body text shown next to the optional icon. */
    message: string;
    /** Invoked when the user confirms. */
    accept: () => void;
    /** Invoked when the user cancels or dismisses the dialog. */
    reject?: () => void;
    /** Localized confirm-button label. */
    acceptLabel: string;
    /** Localized cancel-button label. */
    rejectLabel: string;
    /** Confirm button severity (default `'primary'`). */
    acceptSeverity?: TumUiButtonSeverity;
    /** Cancel button severity (default `'secondary'`). */
    rejectSeverity?: TumUiButtonSeverity;
    /** Optional leading icon shown before the message. */
    icon?: IconProp;
    /** Routes the request to a dialog with the same key. */
    key?: string;
}

/** Coordinates confirmation requests with dialogs in the same injector scope. */
@Injectable()
export class TumUiConfirmationService {
    private readonly requests = signal<ReadonlyMap<string | undefined, TumUiConfirmationRequest>>(new Map());

    request(key: string | undefined): TumUiConfirmationRequest | undefined {
        return this.requests().get(key);
    }

    confirm(request: TumUiConfirmationRequest): void {
        const next = new Map(this.requests());
        next.set(request.key, request);
        this.requests.set(next);
    }

    close(key: string | undefined): void {
        const next = new Map(this.requests());
        next.delete(key);
        this.requests.set(next);
    }
}
