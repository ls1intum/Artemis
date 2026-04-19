import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { StepperComponent } from './stepper/stepper.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisOnboardingService, OnboardingEvent } from './iris-onboarding.service';
import { Subject } from 'rxjs';
import { signal } from '@angular/core';

function createMockElement(rect: Partial<DOMRect>): HTMLElement {
    const el = document.createElement('div');
    el.getBoundingClientRect = vi.fn(
        () =>
            ({
                x: 0,
                y: 0,
                width: 0,
                height: 0,
                top: 0,
                right: 0,
                bottom: 0,
                left: 0,
                toJSON: () => {},
                ...rect,
            }) as DOMRect,
    );
    // Make element pass the offsetParent visibility check
    Object.defineProperty(el, 'offsetParent', { value: document.body, configurable: true });
    return el;
}

describe('IrisOnboardingModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisOnboardingModalComponent;
    let fixture: ComponentFixture<IrisOnboardingModalComponent>;
    let dialogRef: DynamicDialogRef;
    let onboardingEventSubject: Subject<OnboardingEvent>;

    async function detectChanges(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
    }

    beforeEach(async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        onboardingEventSubject = new Subject<OnboardingEvent>();

        TestBed.configureTestingModule({
            imports: [
                IrisOnboardingModalComponent,
                MockComponent(IrisLogoComponent),
                MockComponent(ButtonComponent),
                MockComponent(StepperComponent),
                MockDirective(TranslateDirective),
                MockDirective(CdkTrapFocus),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(DynamicDialogRef),
                { provide: DynamicDialogConfig, useValue: { data: {} } },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: IrisOnboardingService,
                    useValue: {
                        onboardingEvent$: onboardingEventSubject,
                        currentStep: signal(0),
                    },
                },
            ],
        });

        fixture = TestBed.createComponent(IrisOnboardingModalComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should start at step 0', () => {
        expect(component.step()).toBe(0);
    });

    it('should have 4 total steps', () => {
        expect(component.totalSteps).toBe(4);
    });

    describe('next', () => {
        it('should advance to the next step', () => {
            component.step.set(1);
            component.next();
            expect(component.step()).toBe(2);
        });

        it('should call finish when on last step', () => {
            const finishSpy = vi.spyOn(component, 'finish');
            component.step.set(3);
            component.next();
            expect(finishSpy).toHaveBeenCalledOnce();
        });

        it('should not advance beyond totalSteps - 1', () => {
            component.step.set(2);
            component.next();
            expect(component.step()).toBe(3);
            // next() on step 3 calls finish, not step 4
        });
    });

    describe('onStartTour', () => {
        it('should set step to 1', () => {
            component.onStartTour();
            expect(component.step()).toBe(1);
        });

        it('should reset position readiness', () => {
            component.isStepPositionReady.set(true);
            component.onStartTour();
            expect(component.isStepPositionReady()).toBe(false);
        });
    });

    describe('finish', () => {
        it('should close dialog with finish result', () => {
            const closeSpy = vi.spyOn(dialogRef, 'close');
            component.finish();
            expect(closeSpy).toHaveBeenCalledWith({ action: 'finish' });
        });
    });

    describe('close', () => {
        it('should close the dialog without a result', () => {
            const closeSpy = vi.spyOn(dialogRef, 'close');
            component.close();
            expect(closeSpy).toHaveBeenCalledOnce();
        });

        it('should close the dialog on Escape key', () => {
            const closeSpy = vi.spyOn(dialogRef, 'close');
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
            expect(closeSpy).toHaveBeenCalledOnce();
        });
    });

    describe('template rendering', () => {
        it('should render welcome modal at step 0', async () => {
            component.step.set(0);
            await detectChanges();

            const welcomeModal = fixture.nativeElement.querySelector('.iris-onboarding-modal-welcome');
            expect(welcomeModal).toBeTruthy();
        });

        it('should render tooltip when step position is ready', async () => {
            component.step.set(1);
            component.tooltipConfig.set({
                spotlight: { top: 100, left: 200, width: 280, height: 36 },
                coachMarkPosition: { top: 112, left: 334 },
                tooltipPosition: { top: 50, left: 200 },
                titleTranslationKey: 'artemisApp.iris.onboarding.step1.title',
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step1.description',
                arrowDirection: 'down',
                currentStep: 1,
            });
            component.isStepPositionReady.set(true);
            await detectChanges();

            expect(fixture.nativeElement.querySelector('.onboarding-tooltip')).toBeTruthy();
            expect(fixture.nativeElement.querySelector('.coach-mark')).toBeTruthy();
            expect(fixture.nativeElement.querySelector('.spotlight-blocker')).toBeTruthy();
        });

        it('should not render tooltip when position is not ready', async () => {
            component.step.set(1);
            component.isStepPositionReady.set(false);
            await detectChanges();

            expect(fixture.nativeElement.querySelector('.onboarding-tooltip')).toBeFalsy();
        });

        it('should render tooltip with correct arrow direction class', async () => {
            component.step.set(3);
            component.tooltipConfig.set({
                spotlight: { top: 500, left: 50, width: 40, height: 40 },
                coachMarkPosition: { top: 514, left: 64 },
                tooltipPosition: { top: 320, left: 106 },
                titleTranslationKey: 'artemisApp.iris.onboarding.step3.title',
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step3.description',
                arrowDirection: 'down',
                currentStep: 3,
            });
            component.isStepPositionReady.set(true);
            await detectChanges();

            const tooltip = fixture.nativeElement.querySelector('.onboarding-tooltip');
            expect(tooltip.classList.contains('tooltip-arrow-down')).toBe(true);
        });

        it('should have correct ARIA attributes on tooltip', async () => {
            component.step.set(2);
            component.tooltipConfig.set({
                spotlight: { top: 400, left: 100, width: 24, height: 24 },
                coachMarkPosition: { top: 406, left: 106 },
                tooltipPosition: { top: 350, left: 120 },
                titleTranslationKey: 'artemisApp.iris.onboarding.step2.title',
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step2.description',
                arrowDirection: 'up',
                currentStep: 2,
            });
            component.isStepPositionReady.set(true);
            await detectChanges();

            const tooltip = fixture.nativeElement.querySelector('.onboarding-tooltip');
            expect(tooltip.getAttribute('role')).toBe('dialog');
            expect(tooltip.getAttribute('aria-modal')).toBe('true');
            expect(tooltip.getAttribute('aria-labelledby')).toBe('onboarding-tooltip-title-2');
            expect(tooltip.getAttribute('aria-describedby')).toBe('onboarding-tooltip-desc-2');
        });

        it('should not render any step content for invalid step', async () => {
            component.step.set(99);
            await detectChanges();

            const welcomeModal = fixture.nativeElement.querySelector('.iris-onboarding-modal-welcome');
            const tooltip = fixture.nativeElement.querySelector('.onboarding-tooltip');
            expect(welcomeModal).toBeFalsy();
            expect(tooltip).toBeFalsy();
        });
    });

    describe('position calculations', () => {
        it('should calculate tooltip position for step 1 (context selector)', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelectorAll').mockImplementation((selector: string) => {
                if (selector === '[data-onboarding-target="context-selector"]') {
                    return [createMockElement({ top: 500, left: 200, right: 480, bottom: 536, width: 280, height: 36 })] as unknown as NodeListOf<Element>;
                }
                return [] as unknown as NodeListOf<Element>;
            });
            Object.defineProperty(window, 'innerWidth', { value: 1200, configurable: true });
            Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true });

            component.onStartTour();
            vi.advanceTimersByTime(200);

            expect(component.isStepPositionReady()).toBe(true);
            const config = component.tooltipConfig();
            expect(config).toBeTruthy();
            expect(config!.currentStep).toBe(1);
            expect(config!.arrowDirection).toBe('down-left');
            expect(config!.titleTranslationKey).toBe('artemisApp.iris.onboarding.step1.title');

            vi.useRealTimers();
        });

        it('should calculate tooltip position for step 3 (info icon)', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelectorAll').mockImplementation((selector: string) => {
                if (selector === '[data-onboarding-target="info-icon"]') {
                    return [createMockElement({ top: 600, left: 20, right: 60, bottom: 640, width: 40, height: 40 })] as unknown as NodeListOf<Element>;
                }
                return [] as unknown as NodeListOf<Element>;
            });
            Object.defineProperty(window, 'innerWidth', { value: 1200, configurable: true });
            Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true });

            component.step.set(2);
            component.next();
            vi.advanceTimersByTime(200);

            expect(component.isStepPositionReady()).toBe(true);
            const config = component.tooltipConfig();
            expect(config).toBeTruthy();
            expect(config!.currentStep).toBe(3);
            expect(config!.arrowDirection).toBe('down');

            vi.useRealTimers();
        });

        it('should retry and fall back when target element is not found', () => {
            vi.useFakeTimers();
            let rafCallback: FrameRequestCallback | undefined;
            vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb: FrameRequestCallback) => {
                rafCallback = cb;
                return 0;
            });
            vi.spyOn(document, 'querySelectorAll').mockReturnValue([] as unknown as NodeListOf<Element>);

            component.onStartTour();
            expect(component.isStepPositionReady()).toBe(false);

            rafCallback!(0);
            vi.advanceTimersByTime(20 * 200);

            expect(component.isStepPositionReady()).toBe(true);

            vi.useRealTimers();
        });

        it('should not calculate position if step has changed', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelectorAll').mockReturnValue([] as unknown as NodeListOf<Element>);

            component.onStartTour();
            component.step.set(0);
            vi.advanceTimersByTime(20 * 200);

            expect(component.isStepPositionReady()).toBe(false);

            vi.useRealTimers();
        });
    });

    describe('component destroy', () => {
        it('should clean up timers on destroy', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelectorAll').mockReturnValue([] as unknown as NodeListOf<Element>);

            component.onStartTour();
            fixture.destroy();

            // Should not throw after destroy
            vi.advanceTimersByTime(20 * 200);
            vi.useRealTimers();
        });

        it('should remove body attribute on destroy', () => {
            document.body.setAttribute('data-onboarding-active-step', '1');
            fixture.destroy();
            expect(document.body.hasAttribute('data-onboarding-active-step')).toBe(false);
        });
    });

    describe('interactive onboarding events', () => {
        it('should advance from step 1 on contextChanged event', () => {
            component.step.set(1);
            const nextSpy = vi.spyOn(component, 'next');
            onboardingEventSubject.next({ type: 'contextChanged' });
            expect(nextSpy).toHaveBeenCalledOnce();
        });

        it('should not advance from step 2 on contextChanged event', () => {
            component.step.set(2);
            const nextSpy = vi.spyOn(component, 'next');
            onboardingEventSubject.next({ type: 'contextChanged' });
            expect(nextSpy).not.toHaveBeenCalled();
        });

        it('should advance from step 2 on quiz chip click regardless of context', () => {
            // All three contexts (course, lecture, exercise) emit the same shared
            // translationKey 'artemisApp.iris.chat.suggestions.quiz' for the Quiz chip.
            component.step.set(2);
            const nextSpy = vi.spyOn(component, 'next');
            onboardingEventSubject.next({ type: 'chipClicked', translationKey: 'artemisApp.iris.chat.suggestions.quiz' });
            expect(nextSpy).toHaveBeenCalledOnce();
        });

        it('should not advance from step 2 on non-quiz chip click', () => {
            component.step.set(2);
            const nextSpy = vi.spyOn(component, 'next');
            onboardingEventSubject.next({ type: 'chipClicked', translationKey: 'artemisApp.iris.chat.suggestions.learn' });
            expect(nextSpy).not.toHaveBeenCalled();
        });

        it('should finish from step 3 on aboutIrisOpened event', () => {
            component.step.set(3);
            const finishSpy = vi.spyOn(component, 'finish');
            onboardingEventSubject.next({ type: 'aboutIrisOpened' });
            expect(finishSpy).toHaveBeenCalledOnce();
        });

        it('should not finish from step 2 on aboutIrisOpened event', () => {
            component.step.set(2);
            const finishSpy = vi.spyOn(component, 'finish');
            onboardingEventSubject.next({ type: 'aboutIrisOpened' });
            expect(finishSpy).not.toHaveBeenCalled();
        });
    });

    describe('isInteractiveStep', () => {
        it('should return false for step 0', () => {
            component.step.set(0);
            expect(component.isInteractiveStep()).toBe(false);
        });

        it('should return true for steps 1-3', () => {
            component.step.set(1);
            expect(component.isInteractiveStep()).toBe(true);
            component.step.set(2);
            expect(component.isInteractiveStep()).toBe(true);
            component.step.set(3);
            expect(component.isInteractiveStep()).toBe(true);
        });
    });
});
