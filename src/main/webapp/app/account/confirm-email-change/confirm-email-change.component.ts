import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TumAetUiButtonComponent, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ConfirmEmailChangeResponse, ConfirmEmailChangeService } from './confirm-email-change.service';

/**
 * What the page currently shows.
 * - `idle`: nothing was sent yet, the page explains the change and offers the confirm button
 * - `running`: the confirmation request is on its way
 * - `completed`: both addresses are confirmed and the email address was changed
 * - `waitingForOtherAddress`: this address is confirmed, the other one is not yet
 * - `invalid`: the link is unknown, expired or was used already
 * - `emailAlreadyUsed`: the new address was taken in the meantime, the request was dropped
 * - `error`: any other failure, the button stays available to try again
 */
export type ConfirmEmailChangeState = 'idle' | 'running' | 'completed' | 'waitingForOtherAddress' | 'invalid' | 'emailAlreadyUsed' | 'error';

/**
 * Landing page of the links in the email change confirmation emails.
 * The page never confirms by itself when it opens: mail scanners and link previews open links automatically
 * and must not be able to confirm a change. The user confirms with a click on the button.
 */
@Component({
    selector: 'jhi-confirm-email-change',
    templateUrl: './confirm-email-change.component.html',
    imports: [TranslateDirective, RouterLink, TumAetUiButtonComponent, TumAetUiMessageComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmEmailChangeComponent implements OnInit {
    private readonly confirmEmailChangeService = inject(ConfirmEmailChangeService);
    private readonly accountService = inject(AccountService);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);

    /** The key id extracted from the URL query parameters */
    readonly keyId = signal('');
    /** The key secret extracted from the URL query parameters */
    readonly keySecret = signal('');
    /** Indicates the component has finished reading the key from the URL */
    readonly initialized = signal(false);
    /** The current state of the page */
    readonly state = signal<ConfirmEmailChangeState>('idle');
    /** Whether the link contains both parts of the key */
    readonly hasKey = computed(() => !!this.keyId() && !!this.keySecret());
    /** The confirm button is offered until the key was used, and not while a request is running */
    readonly canConfirm = computed(() => this.hasKey() && (this.state() === 'idle' || this.state() === 'error'));

    /**
     * Reads the key from the URL query parameters. Sends nothing to the server.
     */
    ngOnInit() {
        this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this.keyId.set(params['keyId'] ?? '');
            this.keySecret.set(params['keySecret'] ?? '');
            this.initialized.set(true);
        });
    }

    /**
     * Confirms the address the link belongs to. Runs when the user clicks the button.
     */
    confirm() {
        if (!this.canConfirm()) {
            return;
        }
        this.state.set('running');
        this.confirmEmailChangeService.confirmEmailChange(this.keyId(), this.keySecret()).subscribe({
            next: (response) => this.handleConfirmed(response),
            error: (error: HttpErrorResponse) => this.handleError(error),
        });
    }

    /**
     * Shows whether the other address still needs confirmation and refreshes the signed-in identity after completion.
     * @param response the server result for the explicitly confirmed link
     */
    private handleConfirmed(response: ConfirmEmailChangeResponse): void {
        if (!response.completed) {
            this.state.set('waitingForOtherAddress');
            return;
        }
        this.state.set('completed');
        // A signed-in user would otherwise keep seeing the old email address until the next reload.
        if (this.accountService.isAuthenticated()) {
            void this.accountService.identity(true);
        }
    }

    /**
     * Maps terminal link/address errors to their page states and keeps unexpected failures retryable.
     * @param error the failed confirmation response
     */
    private handleError(error: HttpErrorResponse): void {
        const errorKey = error.error?.errorKey;
        if (error.status === 400 && errorKey === 'invalidEmailChangeKey') {
            this.state.set('invalid');
        } else if (error.status === 400 && (errorKey === 'emailAlreadyUsed' || error.error?.type === 'https://www.jhipster.tech/problem/email-already-used')) {
            this.state.set('emailAlreadyUsed');
        } else {
            this.state.set('error');
        }
    }
}
