import { Service, inject, signal } from '@angular/core';
import { Subject, firstValueFrom } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisOnboardingRef } from './iris-onboarding-ref';
import { IrisOnboardingTourHost } from './iris-onboarding-tour-host.service';

export type OnboardingResult = { action: 'finish' };

export type OnboardingEvent = { type: 'contextChanged' } | { type: 'chipClicked'; translationKey: string } | { type: 'aboutIrisOpened' };

const IRIS_ONBOARDING_KEY_PREFIX = 'iris-onboarding-completed';

@Service()
export class IrisOnboardingService {
    private tourHost = inject(IrisOnboardingTourHost);
    private accountService = inject(AccountService);
    private chatHttpService = inject(IrisChatHttpService);
    private dialogRef: IrisOnboardingRef | undefined;
    private pendingResult: Promise<OnboardingResult | undefined> | undefined;

    readonly onboardingEvent$ = new Subject<OnboardingEvent>();
    readonly currentStep = signal(0);

    private getStorageKey(): string {
        const userId = this.accountService.userIdentity()?.id;
        return userId ? `${IRIS_ONBOARDING_KEY_PREFIX}-${userId}` : IRIS_ONBOARDING_KEY_PREFIX;
    }

    private isDesktopViewport(): boolean {
        if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
            return false;
        }
        return window.matchMedia('(min-width: 992px)').matches;
    }

    /**
     * Checks if the user has completed the Iris onboarding.
     * @returns true if onboarding was completed, false otherwise
     */
    hasCompletedOnboarding(): boolean {
        try {
            return localStorage.getItem(this.getStorageKey()) === 'true';
        } catch {
            return false;
        }
    }

    /**
     * Marks the onboarding as completed.
     */
    markOnboardingCompleted(): void {
        try {
            localStorage.setItem(this.getStorageKey(), 'true');
        } catch {
            // Storage unavailable; onboarding state is lost after the session.
        }
    }

    /**
     * Resets the onboarding state (useful for testing).
     */
    resetOnboarding(): void {
        try {
            localStorage.removeItem(this.getStorageKey());
        } catch {
            // Storage unavailable; nothing to remove.
        }
    }

    /**
     * Opens the onboarding modal if the user hasn't completed it yet.
     * @param isEmptyState callback evaluated synchronously each time the empty-state must be
     *     checked; re-invoked after the async server gate so a chat that became non-empty
     *     in the meantime no longer triggers the tour.
     * @returns Promise that resolves to an OnboardingResult or undefined if dismissed
     */
    async showOnboardingIfNeeded(isEmptyState: () => boolean): Promise<OnboardingResult | undefined> {
        if (!this.accountService.userIdentity()?.id) {
            return undefined;
        }

        if (!this.isDesktopViewport()) {
            return undefined;
        }

        if (this.hasCompletedOnboarding()) {
            return undefined;
        }

        if (!isEmptyState()) {
            return undefined;
        }

        // Server-side gate: skip onboarding for students who have already exchanged messages
        // with Iris in any course. We gate on message count rather than session count because
        // opening the chat auto-creates an empty session server-side, which would otherwise
        // suppress the tour for the very new users it targets. Fail-closed on error so a
        // transient 500 never re-shows the tour to a returning student. Runs last so earlier
        // cheap gates can short-circuit the request.
        if (await this.hasExistingIrisMessages()) {
            return undefined;
        }

        // Re-check after the async gate: chat may have received a message while we were
        // waiting on the server, in which case opening the tour now would be jarring.
        if (!isEmptyState()) {
            return undefined;
        }

        return this.openOnboardingModal();
    }

    private async hasExistingIrisMessages(): Promise<boolean> {
        try {
            const counts = await firstValueFrom(this.chatHttpService.getSessionAndMessageCount());
            return (counts?.messages ?? 0) > 0;
        } catch {
            return true;
        }
    }

    /**
     * Forces the onboarding modal to open regardless of completion state.
     * @returns Promise that resolves to an OnboardingResult or undefined if dismissed
     */
    async openOnboardingModal(): Promise<OnboardingResult | undefined> {
        if (!this.accountService.userIdentity()?.id) {
            return undefined;
        }

        if (!this.isDesktopViewport()) {
            return undefined;
        }

        // If a modal is already open, return the same pending result promise.
        if (this.dialogRef) {
            return this.pendingResult;
        }

        this.currentStep.set(0);

        this.dialogRef = this.tourHost.open();

        // Mark on open, not on close: a tour that never reports a result (the page reloads, or the
        // browser closes mid-tour) would otherwise never write the flag, and the next mount would
        // show the tour again.
        this.markOnboardingCompleted();

        this.pendingResult = new Promise<OnboardingResult | undefined>((resolve) => {
            this.dialogRef!.onClose.subscribe((result) => {
                this.currentStep.set(0);
                resolve(result ? { action: 'finish' } : undefined);
            });
        });

        try {
            return await this.pendingResult;
        } finally {
            this.dialogRef = undefined;
            this.pendingResult = undefined;
        }
    }
}
