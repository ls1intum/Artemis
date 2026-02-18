import { Injectable, inject } from '@angular/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AccountService } from 'app/core/auth/account.service';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';

export type OnboardingResult = { action: 'finish' } | { action: 'promptSelected'; promptKey: string };

const IRIS_ONBOARDING_KEY_PREFIX = 'iris-onboarding-completed';

@Injectable({
    providedIn: 'root',
})
export class IrisOnboardingService {
    private dialogService = inject(DialogService);
    private accountService = inject(AccountService);
    private dialogRef: DynamicDialogRef | undefined;
    private pendingResult: Promise<OnboardingResult | undefined> | undefined;

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
     * @returns Promise that resolves to an OnboardingResult or undefined if dismissed
     */
    async showOnboardingIfNeeded(hasAvailableExercises = true): Promise<OnboardingResult | undefined> {
        if (!this.accountService.userIdentity()?.id) {
            return undefined;
        }

        if (!this.isDesktopViewport()) {
            return undefined;
        }

        if (this.hasCompletedOnboarding()) {
            return undefined;
        }

        return this.openOnboardingModal(hasAvailableExercises);
    }

    /**
     * Forces the onboarding modal to open regardless of completion state.
     * @returns Promise that resolves to an OnboardingResult or undefined if dismissed
     */
    async openOnboardingModal(hasAvailableExercises = true): Promise<OnboardingResult | undefined> {
        if (!this.accountService.userIdentity()?.id) {
            return undefined;
        }

        if (!this.isDesktopViewport()) {
            return undefined;
        }

        // If a modal is already open, return the same pending result promise.
        // This handles the case where the chatbot component is destroyed and
        // re-created during navigation (e.g., exercises tour steps 1-3) — the
        // new component instance receives the modal result from the existing modal.
        if (this.dialogRef) {
            return this.pendingResult;
        }

        this.dialogRef =
            this.dialogService.open(IrisOnboardingModalComponent, {
                modal: false,
                closable: false,
                showHeader: false,
                styleClass: 'iris-onboarding-dialog',
                data: { hasAvailableExercises },
            }) ?? undefined;

        this.pendingResult = new Promise<OnboardingResult | undefined>((resolve) => {
            this.dialogRef!.onClose.subscribe((result: OnboardingResult | undefined) => {
                this.markOnboardingCompleted();
                if (result && typeof result === 'object' && result.action === 'promptSelected') {
                    resolve(result);
                } else if (result) {
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
