import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
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
    };

    const tourStepWithPermission: TourStep = {
        contentType: ContentType.TEXT,
        headlineTranslateKey: '',
        contentTranslateKey: '',
        highlightPadding: 10,
        permission: ['ROLE_ADMIN'],
    };

    const tourStepWithHighlightPadding: TourStep = {
        contentType: ContentType.TEXT,
        headlineTranslateKey: '',
        contentTranslateKey: '',
        highlightPadding: 10,
    };

    const courseOverviewTour: GuidedTour = {
        settingsKey: 'showCourseOverviewTour',
        preventBackdropFromAdvancing: true,
        steps: [{ ...tourStep, ...tourStepWithHighlightPadding }],
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
            guidedTourComponent.currentTourStep = tourStep;
            const permission = guidedTourComponent.hasUserPermissionForCurrentTourStep();
            expect(permission).to.be.true;
        });

        describe('Keydown Element', () => {
            beforeEach(async () => {
                // Prepare guided tour service
                spyOn(guidedTourService, 'getOverviewTour').and.returnValue(of(courseOverviewTour));
                spyOn(guidedTourService, 'updateGuidedTourSettings');

                // Prepare guided tour component
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

            beforeAll(() => {
                selectedElement = document.createElement('div') as Element;
                selectedElement.id = 'overview-menu';
                selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
                selectedElementRect.height = 50;
                selectedElementRect.width = 200;
            });

            beforeEach(() => {
                guidedTourComponent.currentTourStep = tourStep;
                guidedTourComponent.selectedElementRect = selectedElementRect;
                guidedTourComponent.tourStepWidth = 500;
            });

            afterEach(() => {
                guidedTourComponent.currentTourStep!.orientation = undefined;
            });

            it('should determine if the tour step has bottom orientation', () => {
                expect(guidedTourComponent.isBottom()).to.be.false;

                guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
                expect(guidedTourComponent.isBottom()).to.be.true;
            });

            it('should determine the highlight padding of the tour step', () => {
                expect(guidedTourComponent.getHighlightPadding()).to.equal(0);

                guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
                expect(guidedTourComponent.getHighlightPadding()).to.equal(10);
            });

            it('should determine the overlay style', () => {
                guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;

                const style = guidedTourComponent.getOverlayStyle();
                expect(style['top.px']).to.equal(-10);
                expect(style['left.px']).to.equal(-10);
                expect(style['height.px']).to.equal(70);
                expect(style['width.px']).to.equal(220);
            });

            it('should calculate the top position of the tour step', () => {
                expect(guidedTourComponent.topPosition).to.equal(0);

                guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
                expect(guidedTourComponent.topPosition).to.equal(50);
            });

            it('should calculate the left position of the tour step', () => {
                expect(guidedTourComponent.leftPosition).to.equal(-350);
            });

            it('should calculate the width of the tour step', () => {
                expect(guidedTourComponent.calculatedTourStepWidth).to.equal(500);
            });

            it('should apply the right transformation', () => {
                guidedTourComponent.currentTourStep!.orientation = Orientation.TOP;
                expect(guidedTourComponent.transform).to.equal('translateY(-100%)');

                guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
                expect(guidedTourComponent.transform).to.equal('');
            });

            it('should calculate the right max width adjustment', () => {
                guidedTourComponent.tourStepWidth = 500;
                guidedTourComponent.minimalTourStepWidth = 400;
                expect(guidedTourComponent.maxWidthAdjustmentForTourStep).to.equal(100);
            });

            it('should calculate the left position of the highlighted element', () => {
                guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
                expect(guidedTourComponent.calculatedHighlightLeftPosition).to.equal(-350);

                guidedTourComponent.currentTourStep.orientation = Orientation.TOPRIGHT;
                expect(guidedTourComponent.calculatedHighlightLeftPosition).to.equal(-500);

                guidedTourComponent.currentTourStep.orientation = Orientation.TOPLEFT;
                expect(guidedTourComponent.calculatedHighlightLeftPosition).to.equal(0);

                guidedTourComponent.currentTourStep.orientation = Orientation.LEFT;
                expect(guidedTourComponent.calculatedHighlightLeftPosition).to.equal(-510);

                guidedTourComponent.currentTourStep.orientation = Orientation.RIGHT;
                expect(guidedTourComponent.calculatedHighlightLeftPosition).to.equal(210);
            });

            it('should adjust the width for screen bound', () => {
                guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
                expect(guidedTourComponent.widthAdjustmentForScreenBound).to.equal(0);

                guidedTourComponent.tourStepWidth = 1000;
                expect(guidedTourComponent.widthAdjustmentForScreenBound).to.equal(500);
            });
        });
    });
});
