import { NO_ERRORS_SCHEMA, DebugElement, ElementRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { CookieService } from 'ngx-cookie';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';

import { ArTEMiSTestModule } from '../../test.module';
import { MockCookieService, MockSyncStorage } from '../../mocks';
import { NavbarComponent } from 'app/layouts';
import { TourStep } from 'app/guided-tour/guided-tour-step.model';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { ContentType, Orientation } from 'app/guided-tour/guided-tour.constants';

chai.use(sinonChai);
const expect = chai.expect;

describe('Component Tests', () => {
    const tourStep: TourStep = {
        contentType: ContentType.TEXT,
        headlineTranslateKey: '',
        contentTranslateKey: '',
        orientation: Orientation.Left,
    };

    const tourStepWithSelector: TourStep = {
        contentType: ContentType.TEXT,
        selector: '#overview-menu',
        headlineTranslateKey: '',
        contentTranslateKey: '',
        highlightPadding: 10,
        orientation: Orientation.BottomRight,
    };

    const courseOverviewTour: GuidedTour = {
        settingsId: 'showCourseOverviewTour',
        preventBackdropFromAdvancing: true,
        steps: [{ ...tourStep, ...tourStep }],
    };

    describe('Guided Tour Component', () => {
        let guidedTourComponent: GuidedTourComponent;
        let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
        let guidedTourDebugElement: DebugElement;
        let guidedTourService: GuidedTourService;
        let router: Router;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArTEMiSTestModule,
                    RouterTestingModule.withRoutes([
                        {
                            path: 'overview',
                            component: NavbarComponent,
                        },
                    ]),
                ],
                declarations: [GuidedTourComponent, NavbarComponent],
                schemas: [NO_ERRORS_SCHEMA],
                providers: [
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: CookieService, useClass: MockCookieService },
                ],
            })
                .overrideTemplate(NavbarComponent, '')
                .compileComponents()
                .then(() => {
                    guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                    guidedTourComponent = guidedTourComponentFixture.componentInstance;
                    guidedTourDebugElement = guidedTourComponentFixture.debugElement;
                    guidedTourService = TestBed.get(GuidedTourService);
                    router = TestBed.get(Router);
                });
        });

        it('should subscribe to events on after init', () => {
            const currentStepSpy = spyOn(guidedTourComponent, 'subscribeToGuidedTourCurrentStepStream');
            const resizeEventSpy = spyOn(guidedTourComponent, 'subscribeToResizeEvent');
            const scrollEventSpy = spyOn(guidedTourComponent, 'subscribeToScrollEvent');

            guidedTourComponent.ngAfterViewInit();

            expect(currentStepSpy.calls.count()).to.equal(1);
            expect(resizeEventSpy.calls.count()).to.equal(1);
            expect(scrollEventSpy.calls.count()).to.equal(1);
        });

        it('should handle user permissions', () => {
            const permission = guidedTourComponent.hasUserPermissionForTourStep(tourStep);
            expect(permission).to.be.true;
        });

        describe('Keydown Element', () => {
            beforeEach(async () => {
                // Prepare GuidedTourService and GuidedTourComponent
                spyOn(guidedTourService, 'getOverviewTour').and.returnValue(of(courseOverviewTour));
                spyOn(guidedTourService, 'updateGuidedTourSettings');
                guidedTourComponent.ngAfterViewInit();

                await guidedTourComponentFixture.ngZone!.run(() => {
                    router.navigateByUrl('/overview');
                });

                // Start course overview tour
                expect(guidedTourComponent.currentTourStep).to.not.exist;
                guidedTourService.startGuidedTourForCurrentRoute();
                guidedTourComponentFixture.detectChanges();
                expect(guidedTourComponent.currentTourStep).to.exist;
            });

            it('should navigate next with the right arrow key', () => {
                const nextStep = spyOn(guidedTourService, 'nextStep');
                const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
                guidedTourComponent.handleKeyboardEvent(eventMock);
                expect(nextStep.calls.count()).to.equal(1);
                nextStep.calls.reset();
            });

            it('should navigate back with the left arrow key', () => {
                const backStep = spyOn(guidedTourService, 'backStep');
                const nextStep = spyOn(guidedTourService, 'nextStep');
                const eventMockRight = new KeyboardEvent('keydown', { code: 'ArrowRight' });
                const eventMockLeft = new KeyboardEvent('keydown', { code: 'ArrowLeft' });

                guidedTourComponent.handleKeyboardEvent(eventMockLeft);
                expect(backStep.calls.count()).to.equal(0);

                guidedTourComponent.handleKeyboardEvent(eventMockRight);
                expect(nextStep.calls.count()).to.equal(1);

                guidedTourComponent.handleKeyboardEvent(eventMockLeft);
                expect(nextStep.calls.count()).to.equal(1);

                nextStep.calls.reset();
                backStep.calls.reset();
            });

            it('should skip the tour with the escape key', () => {
                const skipTour = spyOn(guidedTourService, 'skipTour');
                const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

                guidedTourComponent.handleKeyboardEvent(eventMock);
                expect(skipTour.calls.count()).to.equal(1);

                skipTour.calls.reset();
            });
        });

        describe('Guided Tour Step', () => {
            let selectedElement: Element;
            let selectedElementRect: DOMRect;
            let elementRef = ElementRef;

            beforeAll(() => {
                selectedElement = document.createElement('div') as Element;
                selectedElement.id = 'selector';
                selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
                selectedElementRect.height = 50;
                selectedElementRect.width = 200;
            });

            it('should determine if the tour step has bottom orientation', () => {
                expect(guidedTourComponent.isBottom(tourStep)).to.be.false;
                expect(guidedTourComponent.isBottom(tourStepWithSelector)).to.be.true;
            });

            it('should determine the highlight padding of the tour step', () => {
                expect(guidedTourComponent.getHighlightPadding(tourStepWithSelector)).to.equal(10);
            });

            it('should determine the overlay style', () => {
                const style = guidedTourComponent.getOverlayStyle(selectedElementRect, tourStepWithSelector);
                expect(style['top.px']).to.equal(-10);
                expect(style['left.px']).to.equal(-10);
                expect(style['height.px']).to.equal(70);
                expect(style['width.px']).to.equal(220);
            });

            it('should calculate the top position of the tour step', () => {
                let topPosition = guidedTourComponent.getTopPosition(selectedElementRect, tourStep);
                expect(topPosition).to.equal(0);

                topPosition = guidedTourComponent.getTopPosition(selectedElementRect, tourStepWithSelector);
                expect(topPosition).to.equal(60);
            });

            it('should calculate the left position of the tour step', () => {
                let topPosition = guidedTourComponent.getLeftPosition(selectedElementRect, tourStep, 0, 0);
                expect(topPosition).to.equal(5);

                topPosition = guidedTourComponent.getLeftPosition(selectedElementRect, tourStep, 500, 500);
                expect(topPosition).to.equal(-500);
            });

            it('should calculate the width of the tour step', () => {
                let calculatedWidth = guidedTourComponent.getCalculatedTourStepWidth(null, tourStep, 0, 500);
                expect(calculatedWidth).to.equal(500);
            });
        });
    });
});
