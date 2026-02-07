import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { NavigationEnd, Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { StepperComponent } from './stepper/stepper.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
    });

    describe('selectPrompt', () => {
        it('should close modal with selected prompt for explainConcept', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('explainConcept');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                prompt: 'Can you explain a concept from this exercise?',
            });
        });

        it('should close modal with selected prompt for quizTopic', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('quizTopic');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                prompt: 'Can you quiz me on a topic from this exercise?',
            });
        });

        it('should close modal with selected prompt for studyTips', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('studyTips');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                prompt: 'Can you give me study tips for this exercise?',
            });
        });

        it('should close modal with undefined prompt for unknown type', () => {
            const closeSpy = vi.spyOn(activeModal, 'close');
            component.selectPrompt('unknown');
            expect(closeSpy).toHaveBeenCalledWith({
                action: 'promptSelected',
                prompt: undefined,
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
            fixture.detectChanges();
            await fixture.whenStable();

            const spotlight = fixture.nativeElement.querySelector('.spotlight-backdrop');
            expect(spotlight).toBeTruthy();
            const coachMark = fixture.nativeElement.querySelector('.coach-mark');
            expect(coachMark).toBeTruthy();
        });

        it('should render iris icon tooltip at step 2', async () => {
            component.step.set(2);
            fixture.detectChanges();
            await fixture.whenStable();

            const tooltip = fixture.nativeElement.querySelector('.tooltip-bottom-right-arrow');
            expect(tooltip).toBeTruthy();
            const blocker = fixture.nativeElement.querySelector('.spotlight-blocker');
            expect(blocker).toBeTruthy();
        });

        it('should render dashboard spotlight at step 3', async () => {
            component.step.set(3);
            fixture.detectChanges();
            await fixture.whenStable();

            const spotlight = fixture.nativeElement.querySelector('.spotlight-backdrop');
            expect(spotlight).toBeTruthy();
            const coachMark = fixture.nativeElement.querySelector('.coach-mark');
            expect(coachMark).toBeTruthy();
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
});
