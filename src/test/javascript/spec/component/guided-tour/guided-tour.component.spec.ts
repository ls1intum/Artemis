import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed, inject, fakeAsync } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { CookieService } from 'ngx-cookie';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockCookieService, MockSyncStorage } from '../../mocks';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { Orientation, OverlayPosition } from 'app/guided-tour/guided-tour.constants';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ArtemisSharedModule } from 'app/shared';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from '../../mocks/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('GuidedTourComponent', () => {
    const tourStep = new TextTourStep({
        headlineTranslateKey: '',
        contentTranslateKey: '',
    });

    const tourStepWithPermission = new TextTourStep({
        headlineTranslateKey: '',
        contentTranslateKey: '',
        highlightPadding: 10,
        permission: ['ROLE_ADMIN'],
    });

    const tourStepWithHighlightPadding = new TextTourStep({
        headlineTranslateKey: '',
        contentTranslateKey: '',
        highlightPadding: 10,
    });

    const courseOverviewTour: GuidedTour = {
        settingsKey: 'course_overview_tour',
        steps: [
            tourStep,
            tourStepWithHighlightPadding,
            tourStepWithPermission,
            tourStep,
            tourStepWithHighlightPadding,
            tourStepWithPermission,
            tourStep,
            tourStepWithHighlightPadding,
            tourStepWithPermission,
        ],
    };

    let guidedTourComponent: GuidedTourComponent;
    let guidedTourComponentFixture: ComponentFixture<GuidedTourComponent>;
    let guidedTourDebugElement: DebugElement;
    let guidedTourService: GuidedTourService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                RouterTestingModule.withRoutes([
                    {
                        path: 'overview',
                        component: GuidedTourComponent,
                    },
                ]),
            ],
            declarations: [GuidedTourComponent],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DeviceDetectorService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
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
        const currentStepSpy = spyOn<any>(guidedTourComponent, 'subscribeToGuidedTourCurrentStepStream');
        const resizeEventSpy = spyOn<any>(guidedTourComponent, 'subscribeToResizeEvent');
        const scrollEventSpy = spyOn<any>(guidedTourComponent, 'subscribeToScrollEvent');
        const guidedTourInitSpy = spyOn(guidedTourService, 'init').and.returnValue(of());

        guidedTourComponent.ngAfterViewInit();

        expect(currentStepSpy.calls.count()).to.equal(1);
        expect(resizeEventSpy.calls.count()).to.equal(1);
        expect(scrollEventSpy.calls.count()).to.equal(1);
        expect(guidedTourInitSpy.calls.count()).to.equal(1);
    });

    it('should handle user permissions', () => {
        guidedTourComponent.currentTourStep = tourStep;
        const permission = guidedTourComponent['hasUserPermissionForCurrentTourStep']();
        expect(permission).to.be.true;
    });

    describe('Keydown Element', () => {
        beforeEach(async () => {
            // Prepare guided tour service
            spyOn(guidedTourService, 'init').and.returnValue(of());
            spyOn(guidedTourService, 'getLastSeenTourStepIndex').and.returnValue(0);
            spyOn<any>(guidedTourService, 'updateGuidedTourSettings');
            spyOn<any>(guidedTourService, 'enableTour').and.callFake(() => {
                guidedTourService['availableTourForComponent'] = courseOverviewTour;
                guidedTourService.currentTour = courseOverviewTour;
            });

            // Prepare guided tour component
            guidedTourComponent.ngAfterViewInit();

            // Start course overview tour
            expect(guidedTourComponent.currentTourStep).to.not.exist;
            guidedTourService['enableTour'](courseOverviewTour);
            guidedTourService['startTour']();
            expect(guidedTourComponent.currentTourStep).to.exist;

            // Check highlight (current) dot and small dot
            guidedTourComponentFixture.detectChanges();
            const highlightDot = guidedTourComponentFixture.debugElement.query(By.css('.current'));
            expect(highlightDot).to.exist;
            const nSmallDot = guidedTourComponentFixture.debugElement.queryAll(By.css('.n-small'));
            expect(nSmallDot).to.exist;
        });

        it('should not trigger the guided tour with the right arrow key', () => {
            guidedTourComponent.currentTourStep = null;
            const nextStep = spyOn(guidedTourService, 'nextStep');
            const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(nextStep.calls.count()).to.equal(0);
            nextStep.calls.reset();
        });

        it('should navigate next with the right arrow key', () => {
            const nextStep = spyOn(guidedTourService, 'nextStep').and.callThrough();
            const dotCalculation = spyOn<any>(guidedTourService, 'calculateAndDisplayDotNavigation');
            const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(nextStep.calls.count()).to.equal(1);
            expect(dotCalculation.calls.count()).to.equal(1);
            nextStep.calls.reset();
            dotCalculation.calls.reset();
        });

        it('should navigate back with the left arrow key', () => {
            const backStep = spyOn(guidedTourService, 'backStep').and.callThrough();
            const nextStep = spyOn(guidedTourService, 'nextStep').and.callThrough();
            const dotCalculation = spyOn<any>(guidedTourService, 'calculateAndDisplayDotNavigation');
            const eventMockRight = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            const eventMockLeft = new KeyboardEvent('keydown', { code: 'ArrowLeft' });

            guidedTourComponent.handleKeyboardEvent(eventMockLeft);
            expect(backStep.calls.count()).to.equal(0);
            expect(dotCalculation.calls.count()).to.equal(0);

            guidedTourComponent.handleKeyboardEvent(eventMockRight);
            expect(nextStep.calls.count()).to.equal(1);
            expect(dotCalculation.calls.count()).to.equal(1);

            guidedTourComponent.handleKeyboardEvent(eventMockLeft);
            expect(backStep.calls.count()).to.equal(1);
            expect(dotCalculation.calls.count()).to.equal(2);

            nextStep.calls.reset();
            backStep.calls.reset();
            dotCalculation.calls.reset();
        });

        it('should skip the tour with the escape key', () => {
            const skipTour = spyOn(guidedTourService, 'skipTour');
            spyOn<any>(guidedTourService, 'isCurrentTour').and.returnValue(false);
            const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTour.calls.count()).to.equal(1);

            // Reset component
            skipTour.calls.reset();
            guidedTourComponent.currentTourStep = null;

            // Skip tour with ESC key should not be possible when the component is not active
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTour.calls.count()).to.equal(0);
        });

        it('should not skip but finish the cancel tour with the escape key', () => {
            const skipTour = spyOn(guidedTourService, 'skipTour');
            const finishTour = spyOn(guidedTourService, 'finishGuidedTour');
            spyOn<any>(guidedTourService, 'isCurrentTour').and.returnValue(true);
            const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

            while (!guidedTourService.isOnLastStep) {
                guidedTourService.nextStep();
            }

            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTour.calls.count()).to.equal(0);
            expect(finishTour.calls.count()).to.equal(1);

            // Reset component
            skipTour.calls.reset();
            finishTour.calls.reset();
            guidedTourComponent.currentTourStep = null;
        });
    });

    describe('Guided Tour Step', () => {
        let selectedElement: Element;
        let selectedElementRect: DOMRect;

        function setOrientation(orientation: Orientation) {
            guidedTourComponent.orientation = orientation;
            guidedTourComponent.currentTourStep.orientation = orientation;
        }

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
            if (guidedTourComponent.currentTourStep) {
                guidedTourComponent.currentTourStep.orientation = undefined;
            }
        });

        it('should determine if the tour step has bottom orientation', () => {
            expect(guidedTourComponent['isBottom']()).to.be.false;

            guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
            expect(guidedTourComponent['isBottom']()).to.be.true;
        });

        it('should determine the highlight padding of the tour step', () => {
            expect(guidedTourComponent['getHighlightPadding']()).to.equal(0);

            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['getHighlightPadding']()).to.equal(10);
        });

        it('should calculate the top position of the tour step', () => {
            expect(guidedTourComponent.topPosition).to.equal(0);

            setOrientation(Orientation.BOTTOM);
            expect(guidedTourComponent.topPosition).to.equal(50);
        });

        it('should calculate the left position of the tour step', () => {
            expect(guidedTourComponent.leftPosition).to.equal(-350);
        });

        it('should calculate the width of the tour step', () => {
            expect(guidedTourComponent.calculatedTourStepWidth).to.equal(500);
        });

        it('should apply the right transformation', () => {
            setOrientation(Orientation.TOP);
            expect(guidedTourComponent.transform).to.equal('translateY(-100%)');

            guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
            expect(guidedTourComponent.transform).to.equal('');
        });

        it('should calculate the right max width adjustment', () => {
            guidedTourComponent.tourStepWidth = 500;
            guidedTourComponent.minimalTourStepWidth = 400;
            expect(guidedTourComponent['maxWidthAdjustmentForTourStep']).to.equal(100);
        });

        it('should calculate the left position of the highlighted element', () => {
            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).to.equal(-350);

            setOrientation(Orientation.TOPRIGHT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).to.equal(-500);

            setOrientation(Orientation.TOPLEFT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).to.equal(0);

            setOrientation(Orientation.LEFT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).to.equal(-510);

            setOrientation(Orientation.RIGHT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).to.equal(210);
        });

        it('should adjust the width for screen bound', () => {
            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['widthAdjustmentForScreenBound']).to.equal(0);

            guidedTourComponent.tourStepWidth = 1000;
            expect(guidedTourComponent['widthAdjustmentForScreenBound']).to.equal(500);
        });

        it('should calculate the right style for the overlays', () => {
            // Define expected objects
            const topStyle = { 'top.px': 0, 'left.px': 0, 'height.px': 0 };
            const bottomStyle = { 'top.px': 50 };
            const leftStyle = { 'top.px': 0, 'left.px': 0, 'height.px': 50, 'width.px': 0 };
            const rightStyle = { 'top.px': 0, 'left.px': 200, 'height.px': 50 };

            let style = guidedTourComponent.getOverlayStyle(OverlayPosition.TOP);
            expect(JSON.stringify(style)).to.equal(JSON.stringify(topStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.BOTTOM);
            expect(JSON.stringify(style)).to.equal(JSON.stringify(bottomStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.LEFT);
            expect(JSON.stringify(style)).to.equal(JSON.stringify(leftStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.RIGHT);
            expect(JSON.stringify(style)).to.equal(JSON.stringify(rightStyle));
        });

        it('should initiate flip orientation', () => {
            window.scrollTo = () => {};
            jest.useFakeTimers();
            spyOn<any>(guidedTourComponent, 'isTourOnScreen').and.returnValue(false);
            const flipOrientationSpy = spyOn<any>(guidedTourComponent, 'flipOrientation').and.returnValue(of());
            guidedTourComponent['scrollToAndSetElement']();
            expect(flipOrientationSpy.calls.count()).to.equal(0);

            jest.advanceTimersByTime(300);
            guidedTourComponent['scrollToAndSetElement']();
            expect(flipOrientationSpy.calls.count()).to.equal(1);
        });

        it('should flip orientation', () => {
            setOrientation(Orientation.BOTTOMLEFT);
            guidedTourComponent['flipOrientation']();
            expect(guidedTourComponent.orientation).to.equal(Orientation.BOTTOMRIGHT);

            setOrientation(Orientation.TOPRIGHT);
            guidedTourComponent['flipOrientation']();
            expect(guidedTourComponent.orientation).to.equal(Orientation.TOPLEFT);
        });
    });
});
