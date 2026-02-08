import { Injectable, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';

const IRIS_ONBOARDING_COMPLETED_KEY = 'iris-onboarding-completed';

@Injectable({
    providedIn: 'root',
})
export class IrisOnboardingService {
    private modalService = inject(NgbModal);
    private modalRef: NgbModalRef | undefined;

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
     * @returns Promise that resolves to 'start' if user clicks "Let's get started",
     *          'skip' if user clicks "Skip tour", or undefined if dismissed
     */
    async showOnboardingIfNeeded(hasAvailableExercises = true): Promise<'start' | 'skip' | undefined> {
        if (!this.isDesktopViewport()) {
            return undefined;
        }

        // TODO: Re-enable this check after development is complete
        // if (this.hasCompletedOnboarding()) {
        //     return undefined;
        // }

        return this.openOnboardingModal(hasAvailableExercises);
    }

    /**
     * Forces the onboarding modal to open regardless of completion state.
     * @returns Promise that resolves to 'start' if user clicks "Let's get started",
     *          'skip' if user clicks "Skip tour", or undefined if dismissed
     */
    async openOnboardingModal(hasAvailableExercises = true): Promise<'start' | 'skip' | undefined> {
        if (!this.isDesktopViewport()) {
            return undefined;
        }

        if (this.modalRef) {
            return undefined;
        }

        this.modalRef = this.modalService.open(IrisOnboardingModalComponent, {
            centered: false,
            backdrop: false,
            keyboard: false,
            windowClass: 'iris-onboarding-modal-window',
            modalDialogClass: 'iris-onboarding-dialog',
        });
        this.modalRef.componentInstance?.hasAvailableExercises?.set(hasAvailableExercises);

        try {
            const result = await this.modalRef.result;
            // TODO: Re-enable after development is complete
            // this.markOnboardingCompleted();
            return result as 'start' | 'skip';
        } catch {
            // Modal was dismissed (e.g., clicked X button)
            // TODO: Re-enable after development is complete
            // this.markOnboardingCompleted();
            return undefined;
        } finally {
            this.modalRef = undefined;
        }
    }
}
