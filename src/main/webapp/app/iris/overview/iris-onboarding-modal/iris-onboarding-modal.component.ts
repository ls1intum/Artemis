import { Component, OnDestroy, OnInit, WritableSignal, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { StepperComponent } from './stepper/stepper.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBook, faLightbulb, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

type SidebarTooltipConfig = {
    spotlight: { top: number; left: number; width: number; height: number };
    coachMarkPosition: { top: number; left: number };
    tooltipPosition: { top: number; left: number };
    descriptionTranslationKey: string;
    currentStep: 1 | 3;
};

type PromptOptionKey = 'explainConcept' | 'quizTopic' | 'studyTips';

@Component({
    selector: 'jhi-iris-onboarding-modal',
    standalone: true,
    templateUrl: './iris-onboarding-modal.component.html',
    styleUrls: ['./iris-onboarding-modal.component.scss'],
    imports: [TranslateDirective, IrisLogoComponent, ButtonComponent, StepperComponent, FaIconComponent],
})
export class IrisOnboardingModalComponent implements OnInit, OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private router = inject(Router);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly ButtonType = ButtonType;
    readonly promptOptions: ReadonlyArray<{ type: PromptOptionKey; icon: typeof faBook; translationKey: string }> = [
        { type: 'explainConcept', icon: faBook, translationKey: 'artemisApp.iris.onboarding.step4.prompts.explainConcept' },
        { type: 'quizTopic', icon: faQuestionCircle, translationKey: 'artemisApp.iris.onboarding.step4.prompts.quizTopic' },
        { type: 'studyTips', icon: faLightbulb, translationKey: 'artemisApp.iris.onboarding.step4.prompts.studyTips' },
    ];

    // Step management
    readonly step = signal(0);
    readonly totalSteps = 5;
    readonly hasAvailableExercises = signal(true);

    // Router subscription
    private routeSubscription?: Subscription;

    // Position for Step 1 tooltip (aligned with Exercises tab)
    readonly exerciseTooltipPosition = signal({ top: 172, left: 232 });
    readonly exerciseTabCoachMarkPosition = signal({ top: 152, left: 210 });
    readonly exerciseTabSpotlight = signal({ top: 130, left: 0, width: 220, height: 44 });
    readonly isStep1PositionReady = signal(false);

    // Position for Step 2 (Iris icon)
    readonly irisIconSpotlight = signal({ top: 594, left: 944, width: 48, height: 48 });
    readonly tooltipPosition = signal({ top: 680, left: 690 });
    readonly isStep2PositionReady = signal(false);

    // Position for Step 3 tooltip (aligned with Dashboard tab)
    readonly dashboardTooltipPosition = signal({ top: 80, left: 232 });
    readonly dashboardTabCoachMarkPosition = signal({ top: 80, left: 210 });
    readonly dashboardTabSpotlight = signal({ top: 58, left: 0, width: 220, height: 44 });
    readonly isStep3PositionReady = signal(false);
    readonly sidebarTooltipConfig = computed<SidebarTooltipConfig | undefined>(() => {
        if (this.step() === 1 && this.isStep1PositionReady()) {
            return {
                spotlight: this.exerciseTabSpotlight(),
                coachMarkPosition: this.exerciseTabCoachMarkPosition(),
                tooltipPosition: this.exerciseTooltipPosition(),
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step1.description',
                currentStep: 1,
            };
        }

        if (this.step() === 3 && this.isStep3PositionReady()) {
            return {
                spotlight: this.dashboardTabSpotlight(),
                coachMarkPosition: this.dashboardTabCoachMarkPosition(),
                tooltipPosition: this.dashboardTooltipPosition(),
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step3.description',
                currentStep: 3,
            };
        }

        return undefined;
    });

    ngOnInit(): void {
        // Monitor route changes to auto-advance steps
        this.routeSubscription = this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((event: NavigationEnd) => {
            this.handleNavigationChange(event.url);
        });
    }

    ngOnDestroy(): void {
        this.routeSubscription?.unsubscribe();
    }

    /**
     * Moves to the next step or closes the modal if on the last step.
     */
    next(): void {
        if (this.step() < this.totalSteps - 1) {
            this.step.update((s) => s + 1);

            if (this.step() === 3) {
                this.isStep3PositionReady.set(false);
                this.scheduleDashboardTabPositionCalculation();
            }
        } else {
            this.finish();
        }
    }

    /**
     * Starts the tour from step 1 (skips welcome).
     */
    onStartTour(): void {
        if (!this.hasAvailableExercises()) {
            this.step.set(4);
            return;
        }

        this.isStep1PositionReady.set(false);
        this.isStep2PositionReady.set(false);
        this.step.set(1);
        this.scheduleExerciseTabPositionCalculation();
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

    /**
     * Handles prompt selection from the final step modal.
     */
    selectPrompt(promptType: string): void {
        const prompts: Record<PromptOptionKey, string> = {
            explainConcept: 'Can you explain a concept from this exercise?',
            quizTopic: 'Can you quiz me on a topic from this exercise?',
            studyTips: 'Can you give me study tips for this exercise?',
        };

        const selectedPrompt = prompts[promptType as PromptOptionKey];
        this.activeModal.close({ action: 'promptSelected', prompt: selectedPrompt });
    }

    /**
     * Handles navigation changes to auto-advance steps.
     */
    private handleNavigationChange(url: string): void {
        // Step 1: User should navigate to any exercise page
        // Check for /courses/{id}/exercises pattern
        if (this.step() === 1) {
            const isExercisePage = url.match(/\/courses\/\d+\/exercises\/\d+/) !== null;
            if (isExercisePage) {
                // User navigated to exercise page, advance to Step 2
                this.isStep2PositionReady.set(false);
                this.next();
                this.scheduleIrisIconPositionCalculation();
            }
        }
        // Step 3: User should navigate back to dashboard
        // Accept both dashboard route variants:
        // /courses/{id} and /courses/{id}/dashboard
        else if (this.step() === 3) {
            const isDashboard = url.match(/^\/courses\/\d+(\/dashboard)?\/?$/) !== null || url === '/courses';
            if (isDashboard) {
                // Small handoff delay makes the transition from tooltip to modal feel smoother.
                setTimeout(() => {
                    if (this.step() === 3) {
                        this.next();
                    }
                }, 140);
            }
        }
    }

    /**
     * Calculates the position of the Iris chat button for Step 2 spotlight and tooltip.
     */
    private calculateIrisIconPosition(): boolean {
        const irisButton = document.querySelector('jhi-exercise-chatbot-button .chatbot-button');
        if (!irisButton) {
            return false;
        }

        const rect = irisButton.getBoundingClientRect();
        const tooltipRightOffset = 140;
        const highlightMargin = 8;
        const spotlightTop = Math.max(0, rect.top - highlightMargin);
        const spotlightLeft = Math.max(0, rect.left - highlightMargin);
        const spotlightRight = Math.min(window.innerWidth, rect.right + highlightMargin);
        const spotlightBottom = Math.min(window.innerHeight, rect.bottom + highlightMargin);
        const spotlight = {
            top: spotlightTop,
            left: spotlightLeft,
            width: Math.max(0, spotlightRight - spotlightLeft),
            height: Math.max(0, spotlightBottom - spotlightTop),
        };

        this.irisIconSpotlight.set(spotlight);

        // Place tooltip close to the Iris button (to the left and slightly above it).
        const tooltipWidth = 250;
        const tooltipHeightEstimate = 180;
        const viewportPadding = 12;
        const preferredTop = spotlight.top - tooltipHeightEstimate + spotlight.height / 2;
        const preferredLeft = window.innerWidth - tooltipWidth - tooltipRightOffset;
        const minTop = viewportPadding;
        const maxTop = window.innerHeight - tooltipHeightEstimate - viewportPadding;
        const minLeft = viewportPadding;
        const maxLeft = window.innerWidth - tooltipWidth - viewportPadding;
        const tooltipPos = {
            top: Math.min(Math.max(preferredTop, minTop), maxTop),
            left: Math.min(Math.max(preferredLeft, minLeft), maxLeft),
        };

        this.tooltipPosition.set(tooltipPos);
        return true;
    }

    private scheduleIrisIconPositionCalculation(): void {
        if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
            window.requestAnimationFrame(() => this.resolveStepPosition(2, this.isStep2PositionReady, () => this.calculateIrisIconPosition(), 20, 300));
            return;
        }
        setTimeout(() => this.resolveStepPosition(2, this.isStep2PositionReady, () => this.calculateIrisIconPosition(), 20, 300), 0);
    }

    /**
     * Calculates spotlight, coach mark, and tooltip positions for a sidebar tab.
     */
    private calculateSidebarTabPositions(
        selector: string,
        spotlightSignal: typeof this.exerciseTabSpotlight,
        coachMarkSignal: typeof this.exerciseTabCoachMarkPosition,
        tooltipSignal: typeof this.exerciseTooltipPosition,
    ): boolean {
        const tab = document.querySelector(selector);
        if (!tab) {
            return false;
        }

        const rect = tab.getBoundingClientRect();
        const spotlight = {
            top: rect.top + window.scrollY,
            left: rect.left + window.scrollX,
            width: rect.width,
            height: rect.height,
        };
        spotlightSignal.set(spotlight);
        coachMarkSignal.set({ top: spotlight.top + spotlight.height / 2 - 6, left: spotlight.left + spotlight.width - 16 });
        tooltipSignal.set({ top: spotlight.top - 20, left: spotlight.left + spotlight.width + 12 });
        return true;
    }

    private calculateExerciseTabCoachMarkPosition(): boolean {
        return this.calculateSidebarTabPositions(
            "jhi-course-sidebar a.nav-link-sidebar[title='Exercises']",
            this.exerciseTabSpotlight,
            this.exerciseTabCoachMarkPosition,
            this.exerciseTooltipPosition,
        );
    }

    private calculateDashboardTabCoachMarkPosition(): boolean {
        return this.calculateSidebarTabPositions(
            "jhi-course-sidebar a.nav-link-sidebar[title='Dashboard']",
            this.dashboardTabSpotlight,
            this.dashboardTabCoachMarkPosition,
            this.dashboardTooltipPosition,
        );
    }

    private scheduleExerciseTabPositionCalculation(retries = 20): void {
        this.resolveStepPosition(1, this.isStep1PositionReady, () => this.calculateExerciseTabCoachMarkPosition(), retries, 100);
    }

    private scheduleDashboardTabPositionCalculation(retries = 20): void {
        this.resolveStepPosition(3, this.isStep3PositionReady, () => this.calculateDashboardTabCoachMarkPosition(), retries, 100);
    }

    private resolveStepPosition(expectedStep: number, readinessSignal: WritableSignal<boolean>, calculatePosition: () => boolean, retries: number, retryDelayMs: number): void {
        if (this.step() !== expectedStep) {
            return;
        }

        if (calculatePosition()) {
            readinessSignal.set(true);
            return;
        }

        if (retries > 0) {
            setTimeout(() => this.resolveStepPosition(expectedStep, readinessSignal, calculatePosition, retries - 1, retryDelayMs), retryDelayMs);
            return;
        }

        // Avoid trapping onboarding on a hidden step when target elements are not available.
        readinessSignal.set(true);
    }
}
