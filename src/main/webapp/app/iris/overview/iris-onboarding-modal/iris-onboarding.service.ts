import { Injectable, inject, signal } from '@angular/core';
import { Subject, firstValueFrom } from 'rxjs';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AccountService } from 'app/core/auth/account.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';

export type OnboardingResult = { action: 'finish' };

export type OnboardingEvent = { type: 'contextChanged' } | { type: 'chipClicked'; translationKey: string } | { type: 'aboutIrisOpened' };

const IRIS_ONBOARDING_KEY_PREFIX = 'iris-onboarding-completed';

@Injectable({
    providedIn: 'root',
})
export class IrisOnboardingService {
    private dialogService = inject(DialogService);
    private accountService = inject(AccountService);
    private chatHttpService = inject(IrisChatHttpService);
    private dialogRef: DynamicDialogRef | undefined;
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

        // Server-side gate: skip onboarding for students who have already used Iris in any
        // course. Fail-closed on error so a transient 500 never re-shows the tour to a
        // returning student. Runs last so earlier cheap gates can short-circuit the request.
        if (await this.hasExistingIrisSessions()) {
            return undefined;
        }

        // Re-check after the async gate: chat may have received a message while we were
        // waiting on the server, in which case opening the tour now would be jarring.
        if (!isEmptyState()) {
            return undefined;
        }

        return this.openOnboardingModal();
    }

    private async hasExistingIrisSessions(): Promise<boolean> {
        try {
            const counts = await firstValueFrom(this.chatHttpService.getSessionAndMessageCount());
            return (counts?.sessions ?? 0) > 0 || (counts?.messages ?? 0) > 0;
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

        this.dialogRef =
            this.dialogService.open(IrisOnboardingModalComponent, {
                modal: false,
                closable: false,
                showHeader: false,
                styleClass: 'iris-onboarding-dialog',
                maskStyleClass: 'iris-onboarding-mask',
            }) ?? undefined;

        if (!this.dialogRef) {
            return undefined;
        }

        this.pendingResult = new Promise<OnboardingResult | undefined>((resolve) => {
            this.dialogRef!.onClose.subscribe((result: OnboardingResult | undefined) => {
                this.currentStep.set(0);
                if (result) {
                    this.markOnboardingCompleted();
                    resolve({ action: 'finish' });
                } else {
                    // Modal was dismissed (e.g., clicked X button)
                    resolve(undefined);
                }
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
