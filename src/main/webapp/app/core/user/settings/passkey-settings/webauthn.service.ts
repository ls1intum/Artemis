import { Injectable, inject } from '@angular/core';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { decodeBase64url } from 'app/shared/util/base64.util';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/errors/invalid-credential.error';
import {
    getLoginCredentialWithGracefullyHandlingAuthenticatorIssues,
    getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues,
} from 'app/core/user/settings/passkey-settings/util/credential.util';
import { AlertService } from 'app/shared/service/alert.service';
import { User } from 'app/core/user/user.model';
import { createCredentialOptions } from 'app/core/user/settings/passkey-settings/util/credential-option.util';
import { getOS } from 'app/shared/util/os-detector.util';
import { UserAbortedPasskeyCreationError } from 'app/core/user/settings/passkey-settings/entities/errors/user-aborted-passkey-creation.error';
import { InvalidStateError } from 'app/core/user/settings/passkey-settings/entities/errors/invalid-state.error';
import { AccountService } from 'app/core/auth/account.service';
import { PasskeyAbortError } from 'app/core/user/settings/passkey-settings/entities/errors/passkey-abort.error';

/**
 * Should be aligned and a bit lower than HazelcastPublicKeyCredentialRequestOptionsRepository#AUTH_OPTIONS_TIME_TO_LIVE_SECONDS
 * As the interval is 5 minutes there currently, until the challenge expires we refresh the challenge every 4m45s.
 */
const CONDITIONAL_MEDIATION_REFRESH_INTERVAL_MS = 4 * 60 * 1000 + 45 * 1000;

@Injectable({ providedIn: 'root' })
export class WebauthnService {
    private readonly alertService = inject(AlertService);
    private readonly accountService = inject(AccountService);
    private readonly webauthnApiService = inject(WebauthnApiService);

    private authAbortController = new AbortController();

    /**
     * Tracks the in-flight credential request (HTTP fetch + navigator.credentials.get)
     * to prevent overlapping requests. Only one navigator.credentials.get() can be
     * active per origin, so we must wait for the previous one to fully resolve/reject
     * before starting a new one.
     */
    private pendingCredentialRequest: Promise<unknown> | undefined;

    /**
     * Prevents infinite retry loops when re-enabling passkey autocomplete after a user cancellation.
     */
    private isRetryingConditionalMediation = false;

    /**
     * Callback invoked once `navigator.credentials.get()` has been called for conditional
     * mediation. This allows the caller (e.g., the login component) to re-focus the username
     * field so the browser shows the passkey autofill dropdown.
     */
    private onConditionalMediationActive: (() => void) | undefined;
    private conditionalMediationActiveCallback: (() => void) | undefined;
    private conditionalMediationSuccessCallback: (() => void) | undefined;
    private conditionalMediationRefreshIntervalId: ReturnType<typeof setInterval> | undefined;

    /**
     * Aborts any pending credential request (e.g., a conditional mediation request)
     * and prepares a fresh AbortController for the next request.
     *
     * Only one `navigator.credentials.get()` call can be active at a time per origin.
     * This must be called before starting a new credential request.
     */
    abortPendingCredentialRequest(): void {
        this.authAbortController.abort(new PasskeyAbortError());
        this.authAbortController = new AbortController();
    }

    /**
     * Starts conditional mediation so that the browser shows passkey suggestions
     * in the username/password autocomplete dropdown.
     *
     * This is managed at the service level (not the component level) to avoid
     * lifecycle issues when the HomeComponent is destroyed and recreated during
     * logout navigation. The service ensures only one conditional mediation
     * request is active at a time and refreshes the background challenge before
     * the server-side challenge TTL (5 minutes) expires.
     *
     * @param onSuccess callback invoked when the user selects a passkey and login succeeds
     */
    startConditionalMediation(onSuccess: () => void, onMediationActive?: () => void): void {
        this.stopConditionalMediation();
        this.isRetryingConditionalMediation = false;
        this.conditionalMediationSuccessCallback = onSuccess;
        this.conditionalMediationActiveCallback = onMediationActive;
        this.runConditionalMediation();
        this.startConditionalMediationRefreshTimer();
    }

    /**
     * Stops any active conditional mediation request.
     * Safe to call even if no conditional mediation is active.
     */
    stopConditionalMediation(): void {
        this.stopConditionalMediationRefreshTimer();
        this.abortPendingCredentialRequest();
        this.isRetryingConditionalMediation = false;
        this.onConditionalMediationActive = undefined;
        this.conditionalMediationActiveCallback = undefined;
        this.conditionalMediationSuccessCallback = undefined;
    }

    /**
     * Retrieves a credential from the authenticator.
     *
     * Waits for any pending credential request (including `navigator.credentials.get()`)
     * to fully resolve or reject before starting a new one, because only one
     * `navigator.credentials.get()` can be active per origin.
     *
     * Captures the abort signal before any async work so that an abort during the
     * HTTP fetch still correctly cancels the subsequent `navigator.credentials.get()`.
     *
     * @param isConditional when true, uses conditional mediation (required for passkey autofill).
     *        The browser will show passkey suggestions in the username/password autocomplete dropdown
     *        and the returned promise will stay pending until the user selects a passkey.
     * @returns the credential or undefined if no credential was selected
     */
    async getCredential(isConditional: boolean = false): Promise<PublicKeyCredential | undefined> {
        // Capture the abort signal BEFORE any async work. If abortPendingCredentialRequest()
        // is called while getAuthenticationOptions() is in-flight, it replaces authAbortController
        // with a fresh one. Reading this.authAbortController.signal after the await would get the
        // new (non-aborted) signal, creating a dangling navigator.credentials.get() that nobody
        // can cancel. By capturing up front, the abort correctly propagates.
        const signal = this.authAbortController.signal;

        // Wait for any previous credential request to fully complete/abort.
        // This prevents overlapping navigator.credentials.get() calls which
        // browsers reject (only one can be active per origin).
        if (this.pendingCredentialRequest) {
            await this.pendingCredentialRequest.catch(() => {});
        }

        const credentialPromise = this.doGetCredential(signal, isConditional);
        this.pendingCredentialRequest = credentialPromise;

        try {
            return await credentialPromise;
        } finally {
            this.pendingCredentialRequest = undefined;
        }
    }

    async addNewPasskey(user: User | undefined) {
        try {
            if (!user) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new Error('User or Username is not defined');
            }
            const registrationOptions = await this.webauthnApiService.getRegistrationOptions();
            const credentialOptions = createCredentialOptions(registrationOptions, user);

            const authenticatorCredential = await navigator.credentials.create({
                publicKey: credentialOptions,
            });
            const credential = getRegistrationCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential ?? undefined);

            await this.webauthnApiService.registerPasskey({
                publicKey: {
                    credential: credential,
                    label: `${user.email} - ${getOS()}`,
                },
            });

            this.accountService.userIdentity.set({
                ...this.accountService.userIdentity(),
                askToSetupPasskey: false,
                internal: this.accountService.userIdentity()?.internal ?? false,
            });
        } catch (error) {
            const userPressedCancelInPasskeyCreationDialog = error.name === UserAbortedPasskeyCreationError.name && error.code === UserAbortedPasskeyCreationError.code;
            if (userPressedCancelInPasskeyCreationDialog) {
                return;
            }

            if (error instanceof InvalidCredentialError) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            } else if (error.name === InvalidStateError.name && error.code === InvalidStateError.authenticatorCredentialAlreadyRegisteredWithRelyingPartyCode) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.passkeyAlreadyRegistered');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.registration');
            }

            throw error;
        }
    }

    /**
     * Logs in a user using passkey authentication.
     *
     * @param isConditional when true, uses conditional mediation for passkey autofill
     * @throws InvalidCredentialError if the credential is invalid
     * @throws Error for other authentication errors
     */
    async loginWithPasskey(isConditional: boolean = false) {
        try {
            if (!isConditional) {
                this.stopConditionalMediation();
            }
            const authenticatorCredential = await this.getCredential(isConditional);

            if (!authenticatorCredential || authenticatorCredential.type !== 'public-key') {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            const credential = getLoginCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential) as unknown as PublicKeyCredential;
            if (!credential) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            await this.webauthnApiService.loginWithPasskey(credential);
            await this.accountService.identity(true);
        } catch (error) {
            if (isConditional && this.isConditionalMediationAbortError(error)) {
                // Abort/cancel errors during conditional mediation are expected
                // (e.g., user dismissed autofill, or we aborted to start a modal request).
                // Let the caller decide how to handle them.
                throw error;
            }
            if (error instanceof InvalidCredentialError) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            } else if (error.status === 403) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.loginDeactivated');
            } else if (error.status === 404) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.noPasskeyFound');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.login');
            }
            // eslint-disable-next-line no-undef
            console.error(error);
            throw error;
        }
    }

    /**
     * Runs the conditional mediation flow: calls loginWithPasskey(true) and handles
     * all expected errors (aborts, user cancellations) internally.
     * Retries once after user cancellation (NotAllowedError) so the browser
     * re-shows passkey suggestions.
     */
    private async runConditionalMediation(): Promise<void> {
        const onSuccess = this.conditionalMediationSuccessCallback;
        if (!onSuccess) {
            return;
        }

        this.onConditionalMediationActive = this.conditionalMediationActiveCallback;

        try {
            await this.loginWithPasskey(true);
            onSuccess();
            this.isRetryingConditionalMediation = false;
        } catch (error) {
            this.handleConditionalMediationError(error);
        }
    }

    /**
     * Handles errors from the conditional mediation passkey flow.
     * Silently ignores abort errors from our own AbortController.
     * Re-enables autocomplete after user cancellation (once, to prevent infinite loops).
     */
    private handleConditionalMediationError(error: unknown): void {
        if (error instanceof PasskeyAbortError) {
            return;
        }

        if (error instanceof DOMException && error.name === 'AbortError') {
            return;
        }

        if (this.isUserCancelledPasskeyError(error) && !this.isRetryingConditionalMediation) {
            this.isRetryingConditionalMediation = true;
            this.runConditionalMediation();
            return;
        }

        // eslint-disable-next-line no-undef
        console.warn('Passkey autocomplete error:', error);
    }

    private isUserCancelledPasskeyError(error: unknown): boolean {
        return error instanceof DOMException && error.name === 'NotAllowedError';
    }

    /**
     * Checks whether the error is an expected abort or cancellation during conditional mediation.
     * These errors should not trigger user-visible error alerts.
     */
    private isConditionalMediationAbortError(error: unknown): boolean {
        if (error instanceof PasskeyAbortError) {
            return true;
        }
        if (error instanceof DOMException) {
            return error.name === 'AbortError' || error.name === 'NotAllowedError';
        }
        return false;
    }

    /**
     * Refreshes conditional mediation before the 5-minute challenge expires by
     * aborting the current background request and starting a fresh one.
     */
    private refreshConditionalMediation(): void {
        if (!this.conditionalMediationSuccessCallback) {
            return;
        }

        this.isRetryingConditionalMediation = false;
        this.abortPendingCredentialRequest();
        this.runConditionalMediation();
    }

    private startConditionalMediationRefreshTimer(): void {
        this.stopConditionalMediationRefreshTimer();
        this.conditionalMediationRefreshIntervalId = setInterval(() => {
            this.refreshConditionalMediation();
        }, CONDITIONAL_MEDIATION_REFRESH_INTERVAL_MS);
    }

    private stopConditionalMediationRefreshTimer(): void {
        if (!this.conditionalMediationRefreshIntervalId) {
            return;
        }

        clearInterval(this.conditionalMediationRefreshIntervalId);
        this.conditionalMediationRefreshIntervalId = undefined;
    }

    /**
     * Performs the actual credential retrieval (HTTP fetch + navigator.credentials.get).
     * Separated from getCredential() so the full promise can be tracked.
     */
    private async doGetCredential(signal: AbortSignal, isConditional: boolean): Promise<PublicKeyCredential | undefined> {
        const publicKeyCredentialOptions = await this.webauthnApiService.getAuthenticationOptions();

        const assertionOptions: PublicKeyCredentialRequestOptions = {
            challenge: decodeBase64url(publicKeyCredentialOptions.challenge),
            timeout: publicKeyCredentialOptions.timeout,
            rpId: publicKeyCredentialOptions.rpId,
            allowCredentials: publicKeyCredentialOptions.allowCredentials
                ? publicKeyCredentialOptions.allowCredentials.map((credential) => {
                      return {
                          type: credential.type,
                          id: decodeBase64url(credential.id),
                          transports: credential.transports,
                      };
                  })
                : undefined,
            userVerification: publicKeyCredentialOptions.userVerification,
            extensions: publicKeyCredentialOptions.extensions,
        };

        const credentialRequestOptions: CredentialRequestOptions = {
            publicKey: assertionOptions,
            signal,
            ...(isConditional && { mediation: 'conditional' as CredentialMediationRequirement }),
        };

        const credentialPromise = navigator.credentials.get(credentialRequestOptions);

        // Notify that conditional mediation is now active so the caller can
        // re-trigger the autofill UI (e.g., by re-focusing the username field).
        if (isConditional && this.onConditionalMediationActive) {
            const callback = this.onConditionalMediationActive;
            this.onConditionalMediationActive = undefined;
            callback();
        }

        const credential = (await credentialPromise) ?? undefined;
        return credential as PublicKeyCredential | undefined;
    }
}
