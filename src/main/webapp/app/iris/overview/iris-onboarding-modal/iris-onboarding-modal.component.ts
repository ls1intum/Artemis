import { Component, DestroyRef, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { StepperComponent } from './stepper/stepper.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { filter } from 'rxjs/operators';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { IRIS_PROMPT_CONFIGS } from 'app/iris/shared/iris-prompt.constants';
import { OnboardingResult } from './iris-onboarding.service';

// Sidebar selectors used by onboarding to locate navigation items.
// data-sidebar-item holds sidebarItem.routerLink (a locale-independent stable identifier).
// Exercises routerLink ends with 'exercises' (may be prefixed with courseId); Iris is always exactly 'iris'.
// If sidebar markup changes, update these selectors and the corresponding data-sidebar-item attribute binding.
const SIDEBAR_EXERCISES_SELECTOR = "jhi-course-sidebar a.nav-link-sidebar[data-sidebar-item$='exercises']";
const SIDEBAR_IRIS_SELECTOR = "jhi-course-sidebar a.nav-link-sidebar[data-sidebar-item='iris']";

type SidebarTooltipConfig = {
    spotlight: { top: number; left: number; width: number; height: number };
    coachMarkPosition: { top: number; left: number };
    tooltipPosition: { top: number; left: number };
    descriptionTranslationKey: string;
    currentStep: 1 | 3;
};

@Component({
    selector: 'jhi-iris-onboarding-modal',
    standalone: true,
    templateUrl: './iris-onboarding-modal.component.html',
    styleUrls: ['./iris-onboarding-modal.component.scss'],
    imports: [TranslateDirective, ArtemisTranslatePipe, IrisLogoComponent, ButtonComponent, StepperComponent, FaIconComponent, CdkTrapFocus],
    // Escape is handled here rather than relying on DialogService's built-in keyboard
    // handling because the dialog is opened with closable: false (to prevent accidental
    // dismissal) — this gives us explicit control over the Escape key behaviour.
    host: { '(document:keydown.escape)': 'onEscapeKey()' },
})
export class IrisOnboardingModalComponent {
    private dialogRef = inject(DynamicDialogRef);
    private dialogConfig = inject(DynamicDialogConfig);
    private router = inject(Router);
    private destroyRef = inject(DestroyRef);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly ButtonType = ButtonType;
    readonly promptOptions = IRIS_PROMPT_CONFIGS;

    // Timer cleanup
    private readonly pendingTimers = new Set<ReturnType<typeof setTimeout>>();

    // Step management
    readonly step = signal(0);
    readonly totalSteps = 5;
    readonly hasAvailableExercises = signal<boolean>(this.dialogConfig.data?.hasAvailableExercises ?? true);

    // Position for Step 1 tooltip (aligned with Exercises tab)
    readonly exerciseTooltipPosition = signal({ top: 172, left: 232 });
    readonly exerciseTabCoachMarkPosition = signal({ top: 152, left: 210 });
    readonly exerciseTabSpotlight = signal({ top: 130, left: 0, width: 220, height: 44 });
    readonly isStep1PositionReady = signal(false);

    // Position for Step 2 (Iris icon)
    readonly irisIconSpotlight = signal({ top: 594, left: 944, width: 48, height: 48 });
    readonly tooltipPosition = signal({ top: 680, right: 140 });
    readonly isStep2PositionReady = signal(false);

    // Position for Step 3 tooltip (aligned with Iris tab)
    readonly irisTabTooltipPosition = signal({ top: 80, left: 232 });
    readonly irisTabCoachMarkPosition = signal({ top: 80, left: 210 });
    readonly irisTabSpotlight = signal({ top: 58, left: 0, width: 220, height: 44 });
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
                spotlight: this.irisTabSpotlight(),
                coachMarkPosition: this.irisTabCoachMarkPosition(),
                tooltipPosition: this.irisTabTooltipPosition(),
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step3.description',
                currentStep: 3,
            };
        }

        return undefined;
    });

    constructor() {
        // Monitor route changes to auto-advance steps
        this.router.events
            .pipe(
                filter((event) => event instanceof NavigationEnd),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((event: NavigationEnd) => {
                this.handleNavigationChange(event.url);
            });

        this.destroyRef.onDestroy(() => {
            for (const timer of this.pendingTimers) {
                clearTimeout(timer);
            }
            this.pendingTimers.clear();
        });
    }

    private safeTimeout(callback: () => void, delay?: number): void {
        const timer = setTimeout(() => {
            this.pendingTimers.delete(timer);
            callback();
        }, delay);
        this.pendingTimers.add(timer);
    }

    onEscapeKey(): void {
        this.close();
    }

    /**
     * Moves to the next step or closes the modal if on the last step.
     */
    next(): void {
        if (this.step() < this.totalSteps - 1) {
            this.step.update((s) => s + 1);

            if (this.step() === 3) {
                this.isStep3PositionReady.set(false);
                this.scheduleIrisTabPositionCalculation();
            }

            if (this.step() === 2 || this.step() === 4) {
                this.moveFocusToModal();
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
            this.moveFocusToModal();
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
        this.dialogRef.close({ action: 'finish' } satisfies OnboardingResult);
    }

    /**
     * Closes the modal without completing.
     */
    close(): void {
        this.dialogRef.close();
    }

    /**
     * Handles prompt selection from the final step modal.
     */
    selectPrompt(promptType: string): void {
        const config = IRIS_PROMPT_CONFIGS.find((c) => c.type === promptType);
        if (config) {
            this.dialogRef.close({ action: 'promptSelected', promptKey: config.starterKey } satisfies OnboardingResult);
        } else {
            this.dialogRef.close({ action: 'finish' } satisfies OnboardingResult);
        }
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
        // Step 3: User should navigate back to Iris
        else if (this.step() === 3) {
            const isIrisPage = url.match(/^\/courses\/\d+\/iris\/?$/) !== null;
            if (isIrisPage) {
                // Small handoff delay makes the transition from tooltip to modal feel smoother.
                this.safeTimeout(() => {
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
        const tooltipHeightEstimate = 180;
        const tooltipWidthEstimate = 280;
        const tooltipGap = 16;
        const viewportPadding = 12;
        const preferredTop = spotlight.top - tooltipHeightEstimate + spotlight.height / 2;
        const minTop = viewportPadding;
        const maxTop = window.innerHeight - tooltipHeightEstimate - viewportPadding;
        const rightOffset = Math.max(viewportPadding, window.innerWidth - spotlight.left + tooltipGap);
        const tooltipPos = {
            top: Math.min(Math.max(preferredTop, minTop), maxTop),
            right: Math.min(rightOffset, window.innerWidth - tooltipWidthEstimate - viewportPadding),
        };

        this.tooltipPosition.set(tooltipPos);
        return true;
    }

    private scheduleIrisIconPositionCalculation(): void {
        if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
            window.requestAnimationFrame(() => this.resolveStepPosition(2, this.isStep2PositionReady, () => this.calculateIrisIconPosition(), 20, 300));
            return;
        }
        this.safeTimeout(() => this.resolveStepPosition(2, this.isStep2PositionReady, () => this.calculateIrisIconPosition(), 20, 300), 0);
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
        return this.calculateSidebarTabPositions(SIDEBAR_EXERCISES_SELECTOR, this.exerciseTabSpotlight, this.exerciseTabCoachMarkPosition, this.exerciseTooltipPosition);
    }

    private calculateIrisTabCoachMarkPosition(): boolean {
        return this.calculateSidebarTabPositions(SIDEBAR_IRIS_SELECTOR, this.irisTabSpotlight, this.irisTabCoachMarkPosition, this.irisTabTooltipPosition);
    }

    private scheduleExerciseTabPositionCalculation(retries = 20): void {
        this.resolveStepPosition(1, this.isStep1PositionReady, () => this.calculateExerciseTabCoachMarkPosition(), retries, 100);
    }

    private scheduleIrisTabPositionCalculation(retries = 20): void {
        this.resolveStepPosition(3, this.isStep3PositionReady, () => this.calculateIrisTabCoachMarkPosition(), retries, 100);
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
            this.safeTimeout(() => this.resolveStepPosition(expectedStep, readinessSignal, calculatePosition, retries - 1, retryDelayMs), retryDelayMs);
            return;
        }

        // Avoid trapping onboarding on a hidden step when target elements are not available.
        // Target element not found after all retries; fall back to default position.
        readinessSignal.set(true);
    }

    /**
     * Moves focus to the first focusable element inside the current modal step.
     */
    private moveFocusToModal(): void {
        this.safeTimeout(() => {
            const focusable = document.querySelector<HTMLElement>('.onboarding-container .close-button, .onboarding-container .prompt-chip');
            focusable?.focus();
        });
    }
}
