import { Injectable, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';

export type OnboardingResult = { action: 'finish' } | { action: 'promptSelected'; promptKey: string };

const IRIS_ONBOARDING_COMPLETED_KEY = 'iris-onboarding-completed';

@Injectable({
    providedIn: 'root',
})
export class IrisOnboardingService {
    private modalService = inject(NgbModal);
    private modalRef: NgbModalRef | undefined;
    private pendingResult: Promise<OnboardingResult | undefined> | undefined;

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
        return localStorage.getItem(IRIS_ONBOARDING_COMPLETED_KEY) === 'true';
    }

    /**
     * Marks the onboarding as completed.
     */
    markOnboardingCompleted(): void {
        localStorage.setItem(IRIS_ONBOARDING_COMPLETED_KEY, 'true');
    }

    /**
     * Resets the onboarding state (useful for testing).
     */
    resetOnboarding(): void {
        localStorage.removeItem(IRIS_ONBOARDING_COMPLETED_KEY);
    }

    /**
     * Opens the onboarding modal if the user hasn't completed it yet.
     * @returns Promise that resolves to an OnboardingResult or undefined if dismissed
     */
    async showOnboardingIfNeeded(hasAvailableExercises = true): Promise<OnboardingResult | undefined> {
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
        if (!this.isDesktopViewport()) {
            return undefined;
        }

        // If a modal is already open, return the same pending result promise.
        // This handles the case where the chatbot component is destroyed and
        // re-created during navigation (e.g., exercises tour steps 1-3) — the
        // new component instance receives the modal result from the existing modal.
        if (this.modalRef) {
            return this.pendingResult;
        }

        this.modalRef = this.modalService.open(IrisOnboardingModalComponent, {
            centered: false,
            backdrop: false,
            keyboard: false,
            windowClass: 'iris-onboarding-modal-window',
            modalDialogClass: 'iris-onboarding-dialog',
        });
        this.modalRef.componentInstance?.hasAvailableExercises?.set(hasAvailableExercises);

        this.pendingResult = this.modalRef.result.then(
            (result) => {
                this.markOnboardingCompleted();
                if (result && typeof result === 'object' && result.action === 'promptSelected') {
                    return result as OnboardingResult;
                }
                return { action: 'finish' } as OnboardingResult;
            },
            () => {
                // Modal was dismissed (e.g., clicked X button)
                this.markOnboardingCompleted();
                return undefined;
            },
        );

        try {
            return await this.pendingResult;
        } finally {
            this.modalRef = undefined;
            this.pendingResult = undefined;
        }
    }
}
