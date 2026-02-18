import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { NavigationEnd, Router } from '@angular/router';
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
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { WritableSignal } from '@angular/core';

function createMockElement(rect: Partial<DOMRect>): Element {
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
    return el;
}

function navigateTo(subject: Subject<NavigationEnd>, url: string): void {
    subject.next(new NavigationEnd(1, url, url));
}

describe('IrisOnboardingModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisOnboardingModalComponent;
    let fixture: ComponentFixture<IrisOnboardingModalComponent>;
    let dialogRef: DynamicDialogRef;
    let routerEventsSubject: Subject<NavigationEnd>;

    async function detectChanges(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
    }

    beforeEach(async () => {
        routerEventsSubject = new Subject<NavigationEnd>();

        vi.spyOn(console, 'warn').mockImplementation(() => {});

        TestBed.configureTestingModule({
            imports: [
                IrisOnboardingModalComponent,
                MockComponent(IrisLogoComponent),
                MockComponent(ButtonComponent),
                MockComponent(StepperComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockDirective(CdkTrapFocus),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(DynamicDialogRef),
                { provide: DynamicDialogConfig, useValue: { data: { hasAvailableExercises: true } } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: { events: routerEventsSubject.asObservable() } },
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

    it('should have 5 total steps', () => {
        expect(component.totalSteps).toBe(5);
    });

    describe('next', () => {
        it('should advance to the next step', () => {
            component.step.set(1);
            component.next();
            expect(component.step()).toBe(2);
        });

        it('should call finish when on last step', () => {
            const finishSpy = vi.spyOn(component, 'finish');
            component.step.set(4);
            component.next();
            expect(finishSpy).toHaveBeenCalledOnce();
        });

        it('should not advance beyond totalSteps - 1', () => {
            component.step.set(3);
            component.next();
            expect(component.step()).toBe(4);
            // next() on step 4 calls finish, not step 5
        });
    });

    describe('onStartTour', () => {
        it('should set step to 1', () => {
            component.onStartTour();
            expect(component.step()).toBe(1);
        });

        it('should skip to step 4 when no exercises are available', () => {
            component.hasAvailableExercises.set(false);
            component.onStartTour();
            expect(component.step()).toBe(4);
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

    describe('selectPrompt', () => {
        it.each([
            ['explainConcept', 'artemisApp.iris.onboarding.step4.prompts.explainConceptStarter'],
            ['quizTopic', 'artemisApp.iris.onboarding.step4.prompts.quizTopicStarter'],
            ['studyTips', 'artemisApp.iris.onboarding.step4.prompts.studyTipsStarter'],
        ])('should close dialog with correct promptKey for %s', (promptType, expectedKey) => {
            const closeSpy = vi.spyOn(dialogRef, 'close');
            component.selectPrompt(promptType);
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                promptKey: expectedKey,
            });
        });

        it('should close dialog with finish action for unknown prompt type', () => {
            const closeSpy = vi.spyOn(dialogRef, 'close');
            component.selectPrompt('unknown');
            expect(closeSpy).toHaveBeenCalledWith({ action: 'finish' });
        });
    });

    describe('route navigation handling', () => {
        it('should advance from step 1 to step 2 on exercise page navigation', () => {
            component.step.set(1);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');
            expect(component.step()).toBe(2);
        });

        it('should not advance from step 1 on non-exercise navigation', () => {
            component.step.set(1);
            navigateTo(routerEventsSubject, '/courses/1/lectures');
            expect(component.step()).toBe(1);
        });

        it('should advance from step 3 on navigation to /courses/1/iris', () => {
            const url = '/courses/1/iris';
            vi.useFakeTimers();
            component.step.set(3);
            navigateTo(routerEventsSubject, url);
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(4);
            vi.useRealTimers();
        });

        it('should not advance from step 3 on non-Iris navigation', () => {
            vi.useFakeTimers();
            component.step.set(3);
            navigateTo(routerEventsSubject, '/courses/1/exercises/5');
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(3);
            vi.useRealTimers();
        });

        it('should not react to navigation on step 0', () => {
            component.step.set(0);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');
            expect(component.step()).toBe(0);
        });

        it('should not react to navigation on step 2', () => {
            vi.useFakeTimers();
            component.step.set(2);
            navigateTo(routerEventsSubject, '/courses/1/iris');
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(2);
            vi.useRealTimers();
        });
    });

    describe('component destroy', () => {
        it('should unsubscribe from router events on destroy', () => {
            fixture.destroy();
            // After destroy, navigation events should not affect the step
            component.step.set(1);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');
            expect(component.step()).toBe(1);
        });
    });

    describe('template rendering', () => {
        it('should render welcome modal at step 0', async () => {
            component.step.set(0);
            await detectChanges();

            const welcomeModal = fixture.nativeElement.querySelector('.iris-onboarding-modal-welcome');
            expect(welcomeModal).toBeTruthy();
        });

        it.each([
            [1, 'isStep1PositionReady', '.spotlight-backdrop', '.coach-mark'],
            [2, 'isStep2PositionReady', '.tooltip-bottom-right-arrow', '.spotlight-blocker'],
            [3, 'isStep3PositionReady', '.spotlight-backdrop', '.coach-mark'],
        ])('should render step %i elements when position is ready', async (step, readySignal, selector1, selector2) => {
            component.step.set(step as number);
            (component[readySignal as keyof typeof component] as WritableSignal<boolean>).set(true);
            await detectChanges();
            expect(fixture.nativeElement.querySelector(selector1)).toBeTruthy();
            expect(fixture.nativeElement.querySelector(selector2)).toBeTruthy();
        });

        it.each([
            [1, 'isStep1PositionReady', '.spotlight-backdrop', '.coach-mark'],
            [2, 'isStep2PositionReady', '.tooltip-bottom-right-arrow', '.spotlight-blocker'],
            [3, 'isStep3PositionReady', '.spotlight-backdrop', '.coach-mark'],
        ])('should not render step %i elements when position is not ready', async (step, readySignal, selector1, selector2) => {
            component.step.set(step as number);
            (component[readySignal as keyof typeof component] as WritableSignal<boolean>).set(false);
            await detectChanges();
            expect(fixture.nativeElement.querySelector(selector1)).toBeFalsy();
            expect(fixture.nativeElement.querySelector(selector2)).toBeFalsy();
        });

        it('should render prompt selection modal at step 4', async () => {
            component.step.set(4);
            await detectChanges();

            const promptModal = fixture.nativeElement.querySelector('.prompt-selection-modal');
            expect(promptModal).toBeTruthy();
            const promptChips = fixture.nativeElement.querySelectorAll('.prompt-chip');
            expect(promptChips).toHaveLength(3);
        });

        it('should not render any step content for invalid step', async () => {
            component.step.set(99);
            await detectChanges();

            const welcomeModal = fixture.nativeElement.querySelector('.iris-onboarding-modal-welcome');
            const spotlight = fixture.nativeElement.querySelector('.spotlight-backdrop');
            const promptModal = fixture.nativeElement.querySelector('.prompt-selection-modal');
            expect(welcomeModal).toBeFalsy();
            expect(spotlight).toBeFalsy();
            expect(promptModal).toBeFalsy();
        });
    });

    describe('sidebarTooltipConfig', () => {
        it('should return step 1 config when step is 1 and position is ready', () => {
            component.step.set(1);
            component.isStep1PositionReady.set(true);
            component.exerciseTabSpotlight.set({ top: 130, left: 0, width: 220, height: 44 });
            component.exerciseTabCoachMarkPosition.set({ top: 146, left: 204 });
            component.exerciseTooltipPosition.set({ top: 110, left: 232 });

            const config = component.sidebarTooltipConfig();
            expect(config).toEqual({
                spotlight: { top: 130, left: 0, width: 220, height: 44 },
                coachMarkPosition: { top: 146, left: 204 },
                tooltipPosition: { top: 110, left: 232 },
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step1.description',
                currentStep: 1,
            });
        });

        it('should return step 3 config when step is 3 and position is ready', () => {
            component.step.set(3);
            component.isStep3PositionReady.set(true);
            component.irisTabSpotlight.set({ top: 58, left: 0, width: 220, height: 44 });
            component.irisTabCoachMarkPosition.set({ top: 80, left: 210 });
            component.irisTabTooltipPosition.set({ top: 80, left: 232 });

            const config = component.sidebarTooltipConfig();
            expect(config).toEqual({
                spotlight: { top: 58, left: 0, width: 220, height: 44 },
                coachMarkPosition: { top: 80, left: 210 },
                tooltipPosition: { top: 80, left: 232 },
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step3.description',
                currentStep: 3,
            });
        });

        it.each([
            [1, 'isStep1PositionReady'],
            [3, 'isStep3PositionReady'],
        ])('should return undefined when step is %i but position is not ready', (step, readySignal) => {
            component.step.set(step as number);
            (component[readySignal as keyof typeof component] as WritableSignal<boolean>).set(false);
            expect(component.sidebarTooltipConfig()).toBeUndefined();
        });

        it('should return undefined for other steps', () => {
            component.step.set(2);
            expect(component.sidebarTooltipConfig()).toBeUndefined();
        });
    });

    describe('next step 3 branch', () => {
        it('should reset step 3 position readiness when advancing to step 3', () => {
            component.isStep3PositionReady.set(true);
            component.step.set(2);
            component.next();
            expect(component.step()).toBe(3);
            expect(component.isStep3PositionReady()).toBe(false);
        });
    });

    describe('onStartTour position reset', () => {
        it('should reset position readiness signals when exercises are available', () => {
            component.hasAvailableExercises.set(true);
            component.isStep1PositionReady.set(true);
            component.isStep2PositionReady.set(true);
            component.onStartTour();
            expect(component.step()).toBe(1);
            expect(component.isStep1PositionReady()).toBe(false);
            expect(component.isStep2PositionReady()).toBe(false);
        });
    });

    describe('navigation internals', () => {
        it('should reset step 2 readiness on exercise page navigation at step 1', () => {
            component.step.set(1);
            component.isStep2PositionReady.set(true);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');
            expect(component.isStep2PositionReady()).toBe(false);
        });

        it('should not advance from step 3 if step changes before setTimeout fires', () => {
            vi.useFakeTimers();
            component.step.set(3);
            navigateTo(routerEventsSubject, '/courses/1/iris');
            component.step.set(0);
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(0);
            vi.useRealTimers();
        });
    });

    describe('position calculations', () => {
        it('should calculate Iris icon position when button is found in DOM', () => {
            vi.useFakeTimers();
            let rafCallback: FrameRequestCallback | undefined;
            vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb: FrameRequestCallback) => {
                rafCallback = cb;
                return 0;
            });
            vi.spyOn(document, 'querySelector').mockImplementation((selector: string) => {
                if (selector === 'jhi-exercise-chatbot-button .chatbot-button') {
                    return createMockElement({ top: 500, left: 800, right: 850, bottom: 550, width: 50, height: 50 });
                }
                return null;
            });
            Object.defineProperty(window, 'innerWidth', { value: 1200, configurable: true });
            Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true });

            component.step.set(1);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');

            expect(component.isStep2PositionReady()).toBe(false);
            rafCallback!(0);
            expect(component.isStep2PositionReady()).toBe(true);
            const spotlight = component.irisIconSpotlight();
            expect(spotlight.top).toBe(492);
            expect(spotlight.left).toBe(792);
            expect(spotlight.width).toBe(66);
            expect(spotlight.height).toBe(66);

            vi.useRealTimers();
        });

        it('should retry and fall back when Iris button is not found in DOM', () => {
            vi.useFakeTimers();
            let rafCallback: FrameRequestCallback | undefined;
            vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb: FrameRequestCallback) => {
                rafCallback = cb;
                return 0;
            });
            const querySpy = vi.spyOn(document, 'querySelector').mockReturnValue(null);

            component.step.set(1);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');

            rafCallback!(0);
            expect(component.isStep2PositionReady()).toBe(false);
            vi.advanceTimersByTime(20 * 300);
            expect(component.isStep2PositionReady()).toBe(true);
            expect(querySpy).toHaveBeenCalled();

            vi.useRealTimers();
        });

        it('should calculate exercise tab position when sidebar link is found', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelector').mockImplementation((selector: string) => {
                if (selector === "jhi-course-sidebar a.nav-link-sidebar[data-sidebar-item$='exercises']") {
                    return createMockElement({ top: 130, left: 0, width: 220, height: 44 });
                }
                return null;
            });
            Object.defineProperty(window, 'scrollX', { value: 0, configurable: true });
            Object.defineProperty(window, 'scrollY', { value: 0, configurable: true });

            component.hasAvailableExercises.set(true);
            component.onStartTour();
            vi.advanceTimersByTime(100);

            expect(component.isStep1PositionReady()).toBe(true);
            expect(component.exerciseTabSpotlight()).toEqual({ top: 130, left: 0, width: 220, height: 44 });
            expect(component.exerciseTabCoachMarkPosition()).toEqual({ top: 146, left: 204 });
            expect(component.exerciseTooltipPosition()).toEqual({ top: 110, left: 232 });

            vi.useRealTimers();
        });

        it('should calculate Iris tab position when sidebar link is found', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelector').mockImplementation((selector: string) => {
                if (selector === "jhi-course-sidebar a.nav-link-sidebar[data-sidebar-item='iris']") {
                    return createMockElement({ top: 58, left: 0, width: 220, height: 44 });
                }
                return null;
            });
            Object.defineProperty(window, 'scrollX', { value: 0, configurable: true });
            Object.defineProperty(window, 'scrollY', { value: 0, configurable: true });

            component.step.set(2);
            component.next();
            vi.advanceTimersByTime(100);

            expect(component.step()).toBe(3);
            expect(component.isStep3PositionReady()).toBe(true);
            expect(component.irisTabSpotlight()).toEqual({ top: 58, left: 0, width: 220, height: 44 });
            expect(component.irisTabCoachMarkPosition()).toEqual({ top: 74, left: 204 });
            expect(component.irisTabTooltipPosition()).toEqual({ top: 38, left: 232 });

            vi.useRealTimers();
        });
    });

    describe('scheduleIrisIconPositionCalculation', () => {
        it('should use requestAnimationFrame when available', () => {
            const rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation(() => 0);

            component.step.set(1);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');

            expect(rafSpy).toHaveBeenCalled();
        });

        it('should fall back to setTimeout when requestAnimationFrame is not available', () => {
            vi.useFakeTimers();
            const origRAF = window.requestAnimationFrame;
            Object.defineProperty(window, 'requestAnimationFrame', { value: undefined, configurable: true });
            vi.spyOn(document, 'querySelector').mockReturnValue(createMockElement({ top: 500, left: 800, right: 850, bottom: 550, width: 50, height: 50 }));
            Object.defineProperty(window, 'innerWidth', { value: 1200, configurable: true });
            Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true });

            component.step.set(1);
            navigateTo(routerEventsSubject, '/courses/1/exercises/42');

            expect(component.isStep2PositionReady()).toBe(false);
            vi.advanceTimersByTime(0);
            expect(component.isStep2PositionReady()).toBe(true);

            Object.defineProperty(window, 'requestAnimationFrame', { value: origRAF, configurable: true });
            vi.useRealTimers();
        });
    });

    describe('resolveStepPosition', () => {
        it('should not calculate position if step has changed from expected step', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelector').mockReturnValue(null);

            component.hasAvailableExercises.set(true);
            component.onStartTour();
            component.step.set(0);
            vi.advanceTimersByTime(20 * 100);

            expect(component.isStep1PositionReady()).toBe(false);

            vi.useRealTimers();
        });

        it('should retry position calculation and fall back when element is never found', () => {
            vi.useFakeTimers();
            const querySpy = vi.spyOn(document, 'querySelector').mockReturnValue(null);

            component.hasAvailableExercises.set(true);
            component.onStartTour();
            vi.advanceTimersByTime(20 * 100);

            expect(component.isStep1PositionReady()).toBe(true);
            expect(querySpy).toHaveBeenCalled();

            vi.useRealTimers();
        });
    });
});
