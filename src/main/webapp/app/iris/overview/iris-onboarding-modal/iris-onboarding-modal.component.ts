import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';

export interface SpotlightConfig {
    top: number;
    left: number;
    width: number;
    height: number;
}

@Component({
    selector: 'jhi-iris-onboarding-modal',
    standalone: true,
    templateUrl: './iris-onboarding-modal.component.html',
    styleUrls: ['./iris-onboarding-modal.component.scss'],
    imports: [CommonModule, TranslateDirective, IrisLogoComponent, ButtonComponent],
})
export class IrisOnboardingModalComponent {
    private activeModal = inject(NgbActiveModal);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly ButtonType = ButtonType;

    // Step management
    readonly step = signal(0);
    readonly totalSteps = 2;

    // Spotlight configuration for step 1 (sidebar)
    readonly sidebarSpotlight: SpotlightConfig = {
        top: 0,
        left: 0,
        width: 220,
        height: window.innerHeight,
    };

    /**
     * Moves to the next step or closes the modal if on the last step.
     */
    next(): void {
        if (this.step() < this.totalSteps - 1) {
            this.step.update((s) => s + 1);
        } else {
            this.finish();
        }
    }

    /**
     * Moves to the previous step.
     */
    back(): void {
        if (this.step() > 0) {
            this.step.update((s) => s - 1);
        }
    }

    /**
     * Starts the tour from step 1 (skips welcome).
     */
    onStartTour(): void {
        this.step.set(1);
    }

    /**
     * Skips the tour and closes the modal.
     */
    onSkipTour(): void {
        this.activeModal.close('skip');
    }

    /**
     * Finishes the onboarding and closes the modal.
     */
    finish(): void {
        this.activeModal.close('finish');
    }

    /**
     * Closes the modal without completing.
     */
    close(): void {
        this.activeModal.dismiss();
    }
}
