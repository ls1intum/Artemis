import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { CookieService } from 'ngx-cookie-service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { Orientation, OverlayPosition, ResetParticipation } from 'app/guided-tour/guided-tour.constants';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Authority } from 'app/shared/constants/authority.constants';

chai.use(sinonChai);

describe('GuidedTourComponent', () => {
    const tourStep = new TextTourStep({
        headlineTranslateKey: '',
        contentTranslateKey: '',
    });

    const tourStepWithPermission = new TextTourStep({
        headlineTranslateKey: '',
        contentTranslateKey: '',
        highlightPadding: 10,
        permission: [Authority.ADMIN],
    });

    const tourStepWithHighlightPadding = new TextTourStep({
        headlineTranslateKey: '',
        contentTranslateKey: '',
        highlightPadding: 10,
    });

    const courseOverviewTour: GuidedTour = {
        settingsKey: 'course_overview_tour',
        resetParticipation: ResetParticipation.EXERCISE_PARTICIPATION,
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
    let guidedTourService: GuidedTourService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                RouterTestingModule.withRoutes([
                    {
                        path: 'courses',
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
                guidedTourService = TestBed.inject(GuidedTourService);
            });
    });

    it('should subscribe to events on after init', () => {
        const currentStepSpy = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToGuidedTourCurrentStepStream');
        const resizeEventSpy = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToResizeEvent');
        const scrollEventSpy = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToScrollEvent');
        const dotNavigationSpy = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToDotChanges').mockImplementation();
        const guidedTourInitSpy = jest.spyOn(guidedTourService, 'init').mockImplementation();

        guidedTourComponent.ngAfterViewInit();

        expect(currentStepSpy).toHaveBeenCalledTimes(1);
        expect(resizeEventSpy).toHaveBeenCalledTimes(1);
        expect(scrollEventSpy).toHaveBeenCalledTimes(1);
        expect(dotNavigationSpy).toHaveBeenCalledTimes(1);
        expect(guidedTourInitSpy).toHaveBeenCalledTimes(1);
    });

    it('should handle user permissions', () => {
        guidedTourComponent.currentTourStep = tourStep;
        const permission = guidedTourComponent['hasUserPermissionForCurrentTourStep']();
        expect(permission).toEqual(true);
    });

    describe('Keydown Element', () => {
        beforeEach(async () => {
            // Prepare guided tour service
            jest.spyOn(guidedTourService, 'init').mockImplementation();
            jest.spyOn(guidedTourService, 'getLastSeenTourStepIndex').mockReturnValue(0);
            jest.spyOn<any, any>(guidedTourService, 'updateGuidedTourSettings');
            jest.spyOn<any, any>(guidedTourService, 'enableTour').mockImplementation(() => {
                guidedTourService['availableTourForComponent'] = courseOverviewTour;
                guidedTourService.currentTour = courseOverviewTour;
            });
            jest.spyOn<any, any>(guidedTourComponent, 'subscribeToDotChanges').mockImplementation();

            // Prepare guided tour component
            guidedTourComponent.ngAfterViewInit();

            // Start course overview tour
            expect(guidedTourComponent.currentTourStep).toBeUndefined();
            guidedTourService['enableTour'](courseOverviewTour, true);
            guidedTourService['startTour']();
            expect(guidedTourComponent.currentTourStep).toBeDefined();

            // Check highlight (current) dot and small dot
            guidedTourComponentFixture.detectChanges();
            const highlightDot = guidedTourComponentFixture.debugElement.query(By.css('.current'));
            expect(highlightDot).toBeDefined();
            const nSmallDot = guidedTourComponentFixture.debugElement.queryAll(By.css('.n-small'));
            expect(nSmallDot).toBeDefined();
        });

        it('should not trigger the guided tour with the right arrow key', () => {
            guidedTourComponent.currentTourStep = null;
            const nextStep = jest.spyOn(guidedTourService, 'nextStep');
            const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(nextStep).toHaveBeenCalledTimes(0);
            nextStep.mockReset();
        });

        it('should navigate next with the right arrow key', () => {
            guidedTourComponent['currentStepIndex'] = guidedTourService.currentTourStepIndex;
            guidedTourComponent['nextStepIndex'] = guidedTourService.currentTourStepIndex + 1;
            const nextStep = jest.spyOn(guidedTourService, 'nextStep');
            const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(nextStep).toHaveBeenCalledTimes(1);
            nextStep.mockReset();
        });

        it('should navigate back with the left arrow key', () => {
            const backStep = jest.spyOn(guidedTourService, 'backStep').mockImplementation();
            const nextStep = jest.spyOn(guidedTourService, 'nextStep');
            const eventMockRight = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            const eventMockLeft = new KeyboardEvent('keydown', { code: 'ArrowLeft' });

            guidedTourComponent.handleKeyboardEvent(eventMockLeft);
            expect(backStep).toHaveBeenCalledTimes(0);

            guidedTourComponent.handleKeyboardEvent(eventMockRight);

            guidedTourComponent.handleKeyboardEvent(eventMockLeft);
            expect(backStep).toHaveBeenCalledTimes(1);

            nextStep.mockReset();
            backStep.mockReset();
        });

        it('should skip the tour with the escape key', () => {
            const skipTour = jest.spyOn(guidedTourService, 'skipTour').mockImplementation();
            jest.spyOn<any, any>(guidedTourService, 'isCurrentTour').mockReturnValue(false);
            const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTour).toHaveBeenCalledTimes(1);

            // Reset component
            skipTour.mockReset();
            guidedTourComponent.currentTourStep = null;

            // Skip tour with ESC key should not be possible when the component is not active
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTour).toHaveBeenCalledTimes(0);
        });

        it('should not skip but finish the cancel tour with the escape key', () => {
            const skipTour = jest.spyOn(guidedTourService, 'skipTour').mockImplementation();
            const finishTour = jest.spyOn(guidedTourService, 'finishGuidedTour').mockImplementation();
            jest.spyOn<any, any>(guidedTourService, 'isCurrentTour').mockReturnValue(true);
            const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

            while (!guidedTourService.isOnLastStep) {
                guidedTourService.nextStep();
            }

            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTour).toHaveBeenCalledTimes(0);
            expect(finishTour).toHaveBeenCalledTimes(1);

            // Reset component
            skipTour.mockReset();
            finishTour.mockReset();
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
            expect(guidedTourComponent['isBottom']()).toEqual(false);

            guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
            expect(guidedTourComponent['isBottom']()).toEqual(true);
        });

        it('should determine the highlight padding of the tour step', () => {
            expect(guidedTourComponent['getHighlightPadding']()).toEqual(0);

            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['getHighlightPadding']()).toEqual(10);
        });

        it('should calculate the top position of the tour step', () => {
            expect(guidedTourComponent.topPosition).toEqual(0);

            setOrientation(Orientation.BOTTOM);
            expect(guidedTourComponent.topPosition).toEqual(50);
        });

        it('should calculate the left position of the tour step', () => {
            expect(guidedTourComponent.leftPosition).toEqual(-350);
        });

        it('should calculate the width of the tour step', () => {
            expect(guidedTourComponent.calculatedTourStepWidth).toEqual(500);
        });

        it('should apply the right transformation', () => {
            setOrientation(Orientation.TOP);
            expect(guidedTourComponent.transform).toEqual('translateY(-100%)');

            guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
            expect(guidedTourComponent.transform).toEqual('');
        });

        it('should calculate the right max width adjustment', () => {
            guidedTourComponent.tourStepWidth = 500;
            guidedTourComponent.minimalTourStepWidth = 400;
            expect(guidedTourComponent['maxWidthAdjustmentForTourStep']).toEqual(100);
        });

        it('should calculate the left position of the highlighted element', () => {
            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toEqual(-350);

            setOrientation(Orientation.TOPRIGHT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toEqual(-500);

            setOrientation(Orientation.TOPLEFT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toEqual(0);

            setOrientation(Orientation.LEFT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toEqual(-510);

            setOrientation(Orientation.RIGHT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toEqual(210);
        });

        it('should adjust the width for screen bound', () => {
            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['widthAdjustmentForScreenBound']).toEqual(0);

            guidedTourComponent.tourStepWidth = 1000;
            expect(guidedTourComponent['widthAdjustmentForScreenBound']).toEqual(500);
        });

        it('should calculate the right style for the overlays', () => {
            // Define expected objects
            const topStyle = { 'top.px': 0, 'left.px': 0, 'height.px': 0 };
            const bottomStyle = { 'top.px': 50 };
            const leftStyle = { 'top.px': 0, 'left.px': 0, 'height.px': 50, 'width.px': 0 };
            const rightStyle = { 'top.px': 0, 'left.px': 200, 'height.px': 50 };

            let style = guidedTourComponent.getOverlayStyle(OverlayPosition.TOP);
            expect(JSON.stringify(style)).toEqual(JSON.stringify(topStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.BOTTOM);
            expect(JSON.stringify(style)).toEqual(JSON.stringify(bottomStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.LEFT);
            expect(JSON.stringify(style)).toEqual(JSON.stringify(leftStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.RIGHT);
            expect(JSON.stringify(style)).toEqual(JSON.stringify(rightStyle));
        });

        it('should initiate flip orientation', () => {
            window.scrollTo = () => {};
            jest.useFakeTimers();
            jest.spyOn<any, any>(guidedTourComponent, 'isTourOnScreen').mockReturnValue(false);
            const flipOrientationSpy = jest.spyOn<any, any>(guidedTourComponent, 'flipOrientation').mockImplementation();
            guidedTourComponent['scrollToAndSetElement']();
            expect(flipOrientationSpy).toHaveBeenCalledTimes(0);

            jest.advanceTimersByTime(300);
            guidedTourComponent['scrollToAndSetElement']();
            expect(flipOrientationSpy).toHaveBeenCalledTimes(1);
        });

        it('should flip orientation', () => {
            setOrientation(Orientation.BOTTOMLEFT);
            guidedTourComponent['flipOrientation']();
            expect(guidedTourComponent.orientation).toEqual(Orientation.BOTTOMRIGHT);

            setOrientation(Orientation.TOPRIGHT);
            guidedTourComponent['flipOrientation']();
            expect(guidedTourComponent.orientation).toEqual(Orientation.TOPLEFT);
        });
    });
});
