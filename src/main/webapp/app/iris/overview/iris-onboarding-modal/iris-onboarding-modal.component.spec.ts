import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { NavigationEnd, Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { StepperComponent } from './stepper/stepper.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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

describe('IrisOnboardingModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IrisOnboardingModalComponent;
    let fixture: ComponentFixture<IrisOnboardingModalComponent>;
    let activeModal: NgbActiveModal;
    let routerEventsSubject: Subject<NavigationEnd>;

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
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: { events: routerEventsSubject.asObservable() } },
            ],
        });

        fixture = TestBed.createComponent(IrisOnboardingModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
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
        it('should close modal with finish result', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.finish();
            expect(closeSpy).toHaveBeenCalledWith('finish');
        });
    });

    describe('close', () => {
        it('should dismiss the modal', () => {
            const dismissSpy = vi.spyOn(activeModal, 'dismiss');
            component.close();
            expect(dismissSpy).toHaveBeenCalledOnce();
        });

        it('should dismiss the modal on Escape key', () => {
            const dismissSpy = vi.spyOn(activeModal, 'dismiss');
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
            expect(dismissSpy).toHaveBeenCalledOnce();
        });
    });

    describe('selectPrompt', () => {
        it('should close modal with translation key for explainConcept', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('explainConcept');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                promptKey: 'artemisApp.iris.onboarding.step4.prompts.explainConceptStarter',
            });
        });

        it('should close modal with translation key for quizTopic', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('quizTopic');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                promptKey: 'artemisApp.iris.onboarding.step4.prompts.quizTopicStarter',
            });
        });

        it('should close modal with translation key for studyTips', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('studyTips');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                promptKey: 'artemisApp.iris.onboarding.step4.prompts.studyTipsStarter',
            });
        });

        it('should close modal with undefined promptKey for unknown type', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('unknown');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                promptKey: undefined,
            });
        });
    });

    describe('route navigation handling', () => {
        it('should advance from step 1 to step 2 on exercise page navigation', () => {
            component.step.set(1);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));
            expect(component.step()).toBe(2);
        });

        it('should not advance from step 1 on non-exercise navigation', () => {
            component.step.set(1);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/lectures', '/courses/1/lectures'));
            expect(component.step()).toBe(1);
        });

        it('should advance from step 3 on dashboard navigation', () => {
            vi.useFakeTimers();
            component.step.set(3);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/dashboard', '/courses/1/dashboard'));
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(4);
            vi.useRealTimers();
        });

        it('should advance from step 3 on course root navigation', () => {
            vi.useFakeTimers();
            component.step.set(3);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1', '/courses/1'));
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(4);
            vi.useRealTimers();
        });

        it('should advance from step 3 on /courses navigation', () => {
            vi.useFakeTimers();
            component.step.set(3);
            routerEventsSubject.next(new NavigationEnd(1, '/courses', '/courses'));
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(4);
            vi.useRealTimers();
        });

        it('should not advance from step 3 on non-dashboard navigation', () => {
            vi.useFakeTimers();
            component.step.set(3);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/5', '/courses/1/exercises/5'));
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(3);
            vi.useRealTimers();
        });

        it('should not react to navigation on step 0', () => {
            component.step.set(0);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));
            expect(component.step()).toBe(0);
        });

        it('should not react to navigation on step 2', () => {
            vi.useFakeTimers();
            component.step.set(2);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/dashboard', '/courses/1/dashboard'));
            vi.advanceTimersByTime(200);
            expect(component.step()).toBe(2);
            vi.useRealTimers();
        });
    });

    describe('ngOnDestroy', () => {
        it('should unsubscribe from router events', () => {
            component.ngOnDestroy();
            // After destroy, navigation events should not affect the step
            component.step.set(1);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));
            expect(component.step()).toBe(1);
        });
    });

    describe('template rendering', () => {
        it('should render welcome modal at step 0', async () => {
            component.step.set(0);
            fixture.detectChanges();
            await fixture.whenStable();

            const welcomeModal = fixture.nativeElement.querySelector('.iris-onboarding-modal-welcome');
            expect(welcomeModal).toBeTruthy();
        });

        it('should render spotlight backdrop at step 1', async () => {
            component.step.set(1);
            component.isStep1PositionReady.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const spotlight = fixture.nativeElement.querySelector('.spotlight-backdrop');
            expect(spotlight).toBeTruthy();
            const coachMark = fixture.nativeElement.querySelector('.coach-mark');
            expect(coachMark).toBeTruthy();
        });

        it('should not render step 1 spotlight when position is not ready', async () => {
            component.step.set(1);
            component.isStep1PositionReady.set(false);
            fixture.detectChanges();
            await fixture.whenStable();

            const spotlight = fixture.nativeElement.querySelector('.spotlight-backdrop');
            const coachMark = fixture.nativeElement.querySelector('.coach-mark');
            expect(spotlight).toBeFalsy();
            expect(coachMark).toBeFalsy();
        });

        it('should render iris icon tooltip at step 2', async () => {
            component.step.set(2);
            component.isStep2PositionReady.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const tooltip = fixture.nativeElement.querySelector('.tooltip-bottom-right-arrow');
            expect(tooltip).toBeTruthy();
            const blocker = fixture.nativeElement.querySelector('.spotlight-blocker');
            expect(blocker).toBeTruthy();
        });

        it('should not render iris icon tooltip at step 2 when spotlight position is not ready', async () => {
            component.step.set(2);
            component.isStep2PositionReady.set(false);
            fixture.detectChanges();
            await fixture.whenStable();

            const tooltip = fixture.nativeElement.querySelector('.tooltip-bottom-right-arrow');
            const blocker = fixture.nativeElement.querySelector('.spotlight-blocker');
            expect(tooltip).toBeFalsy();
            expect(blocker).toBeFalsy();
        });

        it('should render dashboard spotlight at step 3', async () => {
            component.step.set(3);
            component.isStep3PositionReady.set(true);
            fixture.detectChanges();
            await fixture.whenStable();

            const spotlight = fixture.nativeElement.querySelector('.spotlight-backdrop');
            expect(spotlight).toBeTruthy();
            const coachMark = fixture.nativeElement.querySelector('.coach-mark');
            expect(coachMark).toBeTruthy();
        });

        it('should not render step 3 spotlight when position is not ready', async () => {
            component.step.set(3);
            component.isStep3PositionReady.set(false);
            fixture.detectChanges();
            await fixture.whenStable();

            const spotlight = fixture.nativeElement.querySelector('.spotlight-backdrop');
            const coachMark = fixture.nativeElement.querySelector('.coach-mark');
            expect(spotlight).toBeFalsy();
            expect(coachMark).toBeFalsy();
        });

        it('should render prompt selection modal at step 4', async () => {
            component.step.set(4);
            fixture.detectChanges();
            await fixture.whenStable();

            const promptModal = fixture.nativeElement.querySelector('.prompt-selection-modal');
            expect(promptModal).toBeTruthy();
            const promptChips = fixture.nativeElement.querySelectorAll('.prompt-chip');
            expect(promptChips).toHaveLength(3);
        });

        it('should not render any step content for invalid step', async () => {
            component.step.set(99);
            fixture.detectChanges();
            await fixture.whenStable();

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
            component.dashboardTabSpotlight.set({ top: 58, left: 0, width: 220, height: 44 });
            component.dashboardTabCoachMarkPosition.set({ top: 80, left: 210 });
            component.dashboardTooltipPosition.set({ top: 80, left: 232 });

            const config = component.sidebarTooltipConfig();
            expect(config).toEqual({
                spotlight: { top: 58, left: 0, width: 220, height: 44 },
                coachMarkPosition: { top: 80, left: 210 },
                tooltipPosition: { top: 80, left: 232 },
                descriptionTranslationKey: 'artemisApp.iris.onboarding.step3.description',
                currentStep: 3,
            });
        });

        it('should return undefined when step is 1 but position is not ready', () => {
            component.step.set(1);
            component.isStep1PositionReady.set(false);
            expect(component.sidebarTooltipConfig()).toBeUndefined();
        });

        it('should return undefined when step is 3 but position is not ready', () => {
            component.step.set(3);
            component.isStep3PositionReady.set(false);
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
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));
            expect(component.isStep2PositionReady()).toBe(false);
        });

        it('should not advance from step 3 if step changes before setTimeout fires', () => {
            vi.useFakeTimers();
            component.step.set(3);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/dashboard', '/courses/1/dashboard'));
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
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));

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
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));

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
                if (selector === "jhi-course-sidebar a.nav-link-sidebar[title='Exercises']") {
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

        it('should calculate dashboard tab position when sidebar link is found', () => {
            vi.useFakeTimers();
            vi.spyOn(document, 'querySelector').mockImplementation((selector: string) => {
                if (selector === "jhi-course-sidebar a.nav-link-sidebar[title='Dashboard']") {
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
            expect(component.dashboardTabSpotlight()).toEqual({ top: 58, left: 0, width: 220, height: 44 });
            expect(component.dashboardTabCoachMarkPosition()).toEqual({ top: 74, left: 204 });
            expect(component.dashboardTooltipPosition()).toEqual({ top: 38, left: 232 });

            vi.useRealTimers();
        });
    });

    describe('scheduleIrisIconPositionCalculation', () => {
        it('should use requestAnimationFrame when available', () => {
            const rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation(() => 0);

            component.step.set(1);
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));

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
            routerEventsSubject.next(new NavigationEnd(1, '/courses/1/exercises/42', '/courses/1/exercises/42'));

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
