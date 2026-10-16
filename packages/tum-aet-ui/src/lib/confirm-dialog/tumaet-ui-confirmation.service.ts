import { Injectable, signal } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TumAetUiButtonSeverity } from '../button/tumaet-ui-button.variants';

/** Content and callbacks for one confirmation decision. */
export interface TumAetUiConfirmationRequest {
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
    acceptSeverity?: TumAetUiButtonSeverity;
    /** Cancel button severity (default `'secondary'`). */
    rejectSeverity?: TumAetUiButtonSeverity;
    /** Optional leading icon shown before the message. */
    icon?: IconProp;
    /** Routes the request to a dialog with the same key. */
    key?: string;
}

/** Coordinates confirmation requests with dialogs in the same injector scope. */
@Injectable()
export class TumAetUiConfirmationService {
    private readonly requests = signal<ReadonlyMap<string | undefined, TumAetUiConfirmationRequest>>(new Map());

    request(key: string | undefined): TumAetUiConfirmationRequest | undefined {
        return this.requests().get(key);
    }

    confirm(request: TumAetUiConfirmationRequest): void {
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
