import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { StepperComponent } from './stepper/stepper.component';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { IrisOnboardingService, OnboardingResult } from './iris-onboarding.service';

// Data-attribute selectors for onboarding tooltip anchor elements.
// These elements live inside the Iris chat UI (iris-base-chatbot component).
const ONBOARDING_TARGETS = {
    suggestionChips: '[data-onboarding-target="suggestion-chips"]',
    contextSelector: '[data-onboarding-target="context-selector"]',
    infoIcon: '[data-onboarding-target="info-icon"]',
} as const;

type ArrowDirection = 'up' | 'down' | 'down-left' | 'left' | 'right';

type TooltipConfig = {
    spotlight: { top: number; left: number; width: number; height: number };
    coachMarkPosition: { top: number; left: number };
    tooltipPosition: { top: number; left: number };
    titleTranslationKey: string;
    descriptionTranslationKey: string;
    arrowDirection: ArrowDirection;
    currentStep: 1 | 2 | 3;
};

@Component({
    selector: 'jhi-iris-onboarding-modal',
    standalone: true,
    templateUrl: './iris-onboarding-modal.component.html',
    styleUrls: ['./iris-onboarding-modal.component.scss'],
    imports: [TranslateDirective, ArtemisTranslatePipe, IrisLogoComponent, ButtonComponent, StepperComponent, CdkTrapFocus],
    // Escape is handled here rather than relying on DialogService's built-in keyboard
    // handling because the dialog is opened with closable: false (to prevent accidental
    // dismissal) — this gives us explicit control over the Escape key behaviour.
    host: { '(document:keydown.escape)': 'onEscapeKey()' },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisOnboardingModalComponent {
    private dialogRef = inject(DynamicDialogRef);
    private destroyRef = inject(DestroyRef);
    private onboardingService = inject(IrisOnboardingService);

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly ButtonType = ButtonType;

    // Timer cleanup
    private readonly pendingTimers = new Set<ReturnType<typeof setTimeout>>();
    private readonly pendingRafs = new Set<number>();
    private isDestroyed = false;

    // Step management: 0 = welcome, 1-3 = tooltips
    readonly step = signal(0);
    readonly totalSteps = 4;

    // Tooltip positioning
    readonly tooltipConfig = signal<TooltipConfig | undefined>(undefined);
    readonly isStepPositionReady = signal(false);
    readonly isInteractiveStep = computed(() => this.step() >= 1 && this.step() <= 3);

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.isDestroyed = true;
            for (const timer of this.pendingTimers) {
                clearTimeout(timer);
            }
            this.pendingTimers.clear();
            for (const rafId of this.pendingRafs) {
                window.cancelAnimationFrame(rafId);
            }
            this.pendingRafs.clear();
            document.body.removeAttribute('data-onboarding-active-step');
        });

        this.onboardingService.onboardingEvent$.pipe(takeUntilDestroyed()).subscribe((event) => {
            switch (event.type) {
                case 'contextChanged':
                    if (this.step() === 1) this.next();
                    break;
                case 'chipClicked':
                    if (this.step() === 2 && event.translationKey === 'artemisApp.iris.chat.suggestions.quiz') {
                        this.next();
                    }
                    break;
                case 'aboutIrisOpened':
                    if (this.step() === 3) this.finish();
                    break;
            }
        });

        effect(() => {
            const step = this.step();
            if (step >= 1 && step <= 3) {
                document.body.setAttribute('data-onboarding-active-step', String(step));
            } else {
                document.body.removeAttribute('data-onboarding-active-step');
            }
        });
    }

    private safeTimeout(callback: () => void, delay?: number): void {
        if (this.isDestroyed) {
            return;
        }
        const timer = setTimeout(() => {
            this.pendingTimers.delete(timer);
            if (this.isDestroyed) {
                return;
            }
            callback();
        }, delay);
        this.pendingTimers.add(timer);
    }

    private safeRequestAnimationFrame(callback: FrameRequestCallback): void {
        if (this.isDestroyed) {
            return;
        }
        const rafId = window.requestAnimationFrame((time) => {
            this.pendingRafs.delete(rafId);
            if (this.isDestroyed) {
                return;
            }
            callback(time);
        });
        this.pendingRafs.add(rafId);
    }

    onEscapeKey(): void {
        this.close();
    }

    private setStep(newStep: number): void {
        this.isStepPositionReady.set(false);
        this.tooltipConfig.set(undefined);
        this.step.set(newStep);
        this.onboardingService.currentStep.set(newStep);
    }

    /**
     * Moves to the next step or closes the modal if on the last step.
     */
    next(): void {
        const nextStep = this.step() + 1;
        if (nextStep < this.totalSteps) {
            this.setStep(nextStep);
            this.schedulePositionCalculation(nextStep as 1 | 2 | 3);
        } else {
            this.finish();
        }
    }

    /**
     * Starts the tour from step 1 (skips welcome).
     */
    onStartTour(): void {
        this.setStep(1);
        this.schedulePositionCalculation(1);
    }

    /**
     * Finishes the onboarding and closes the modal.
     */
    finish(): void {
        document.body.removeAttribute('data-onboarding-active-step');
        this.dialogRef.close({ action: 'finish' } satisfies OnboardingResult);
    }

    /**
     * Closes the modal without completing.
     */
    close(): void {
        document.body.removeAttribute('data-onboarding-active-step');
        this.dialogRef.close();
    }

    /**
     * Calculates tooltip position for a given step based on the target element's position.
     */
    private calculateTooltipPosition(step: 1 | 2 | 3): boolean {
        const config = this.getStepConfig(step);
        const target = this.findVisibleElement(config.selector);
        if (!target) {
            return false;
        }

        const rect = target.getBoundingClientRect();
        const highlightMargin = 8;
        const spotlight = {
            top: Math.max(0, rect.top - highlightMargin),
            left: Math.max(0, rect.left - highlightMargin),
            width: Math.max(0, Math.min(window.innerWidth, rect.right + highlightMargin) - Math.max(0, rect.left - highlightMargin)),
            height: Math.max(0, Math.min(window.innerHeight, rect.bottom + highlightMargin) - Math.max(0, rect.top - highlightMargin)),
        };

        const tooltipWidth = 280;
        const tooltipHeight = 180;
        const gap = 16;
        const viewportPadding = 12;

        let tooltipPos: { top: number; left: number };

        switch (config.arrowDirection) {
            case 'up': {
                // Tooltip below target, arrow pointing up
                const preferredTop = spotlight.top + spotlight.height + 8;
                const preferredLeft = spotlight.left + spotlight.width / 2 - tooltipWidth / 2;
                tooltipPos = {
                    top: Math.max(viewportPadding, Math.min(preferredTop, window.innerHeight - tooltipHeight - viewportPadding)),
                    left: Math.max(viewportPadding, Math.min(preferredLeft, window.innerWidth - tooltipWidth - viewportPadding)),
                };
                break;
            }
            case 'down': {
                // Tooltip above target, arrow pointing down
                const preferredTop = spotlight.top - tooltipHeight - 8;
                const preferredLeft = spotlight.left + spotlight.width / 2 - tooltipWidth / 2;
                tooltipPos = {
                    top: Math.max(viewportPadding, preferredTop),
                    left: Math.max(viewportPadding, Math.min(preferredLeft, window.innerWidth - tooltipWidth - viewportPadding)),
                };
                break;
            }
            case 'down-left': {
                // Tooltip above and to the right, arrow pointing down-left
                const preferredTop = spotlight.top - tooltipHeight - 8;
                const preferredLeft = spotlight.left + spotlight.width / 2;
                tooltipPos = {
                    top: Math.max(viewportPadding, preferredTop),
                    left: Math.max(viewportPadding, Math.min(preferredLeft, window.innerWidth - tooltipWidth - viewportPadding)),
                };
                break;
            }
            case 'left': {
                // Tooltip to the right of target, arrow pointing left.
                // Prefer aligning near the top of the spotlight so the tooltip doesn't
                // get pushed off the bottom edge when the target is low on screen.
                const preferredTop = spotlight.top;
                const preferredLeft = spotlight.left + spotlight.width + gap;
                tooltipPos = {
                    top: Math.max(viewportPadding, Math.min(preferredTop, window.innerHeight - tooltipHeight - viewportPadding)),
                    left: Math.max(viewportPadding, Math.min(preferredLeft, window.innerWidth - tooltipWidth - viewportPadding)),
                };
                break;
            }
            case 'right': {
                // Tooltip to the left of target, arrow pointing right from the tooltip's right edge.
                const preferredTop = spotlight.top + spotlight.height / 2 - tooltipHeight / 2;
                const preferredLeft = spotlight.left - tooltipWidth - gap;
                tooltipPos = {
                    top: Math.max(viewportPadding, Math.min(preferredTop, window.innerHeight - tooltipHeight - viewportPadding)),
                    left: Math.max(viewportPadding, preferredLeft),
                };
                break;
            }
        }

        const coachMarkPos = {
            top: spotlight.top + spotlight.height / 2 - 6,
            left: spotlight.left + spotlight.width / 2 - 6,
        };

        this.tooltipConfig.set({
            spotlight,
            coachMarkPosition: coachMarkPos,
            tooltipPosition: tooltipPos,
            titleTranslationKey: config.titleKey,
            descriptionTranslationKey: config.descriptionKey,
            arrowDirection: config.arrowDirection,
            currentStep: step,
        });

        return true;
    }

    private getStepConfig(step: 1 | 2 | 3): { selector: string; arrowDirection: ArrowDirection; titleKey: string; descriptionKey: string } {
        switch (step) {
            case 1:
                return {
                    selector: ONBOARDING_TARGETS.contextSelector,
                    arrowDirection: 'right',
                    titleKey: 'artemisApp.iris.onboarding.step1.title',
                    descriptionKey: 'artemisApp.iris.onboarding.step1.description',
                };
            case 2:
                return {
                    selector: ONBOARDING_TARGETS.suggestionChips,
                    arrowDirection: 'up',
                    titleKey: 'artemisApp.iris.onboarding.step2.title',
                    descriptionKey: 'artemisApp.iris.onboarding.step2.description',
                };
            case 3:
                return {
                    selector: ONBOARDING_TARGETS.infoIcon,
                    arrowDirection: 'down',
                    titleKey: 'artemisApp.iris.onboarding.step3.title',
                    descriptionKey: 'artemisApp.iris.onboarding.step3.description',
                };
            default: {
                const exhaustive: never = step;
                throw new Error(`Unhandled onboarding step: ${exhaustive}`);
            }
        }
    }

    /**
     * Finds the first visible element matching the selector.
     * Multiple elements may match (e.g., info icon in collapsed vs expanded sidebar),
     * so we pick the one that is actually rendered on screen. Returns undefined if
     * none are visible — callers must handle that and keep the step hidden.
     */
    private findVisibleElement(selector: string): HTMLElement | undefined {
        const elements = document.querySelectorAll(selector);
        for (const el of Array.from(elements)) {
            if (el instanceof HTMLElement && el.offsetParent !== null) {
                return el;
            }
        }
        return undefined;
    }

    private schedulePositionCalculation(step: 1 | 2 | 3): void {
        if (this.isDestroyed) {
            return;
        }
        if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
            this.safeRequestAnimationFrame(() => this.resolveStepPosition(step, () => this.calculateTooltipPosition(step), 20, 200));
            return;
        }
        this.safeTimeout(() => this.resolveStepPosition(step, () => this.calculateTooltipPosition(step), 20, 200), 0);
    }

    private resolveStepPosition(expectedStep: 1 | 2 | 3, calculatePosition: () => boolean, retries: number, retryDelayMs: number): void {
        if (this.isDestroyed || this.step() !== expectedStep) {
            return;
        }

        if (calculatePosition()) {
            this.isStepPositionReady.set(true);
            return;
        }

        if (retries > 0) {
            this.safeTimeout(() => this.resolveStepPosition(expectedStep, calculatePosition, retries - 1, retryDelayMs), retryDelayMs);
            return;
        }

        // Target never appeared; leave the modal in a safe state without surfacing an error.
        this.tooltipConfig.set(undefined);
        this.isStepPositionReady.set(false);
    }
}
