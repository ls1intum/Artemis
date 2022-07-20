import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { TextTourStep } from 'app/guided-tour/guided-tour-step.model';
import { GuidedTour } from 'app/guided-tour/guided-tour.model';
import { GuidedTourComponent } from 'app/guided-tour/guided-tour.component';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { Orientation, OverlayPosition, ResetParticipation } from 'app/guided-tour/guided-tour.constants';
import { By } from '@angular/platform-browser';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { JhiConnectionWarningComponent } from 'app/shared/connection-warning/connection-warning.component';

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
            imports: [ArtemisTestModule],
            declarations: [GuidedTourComponent, MockDirective(TranslateDirective), TranslatePipeMock, MockPipe(SafeResourceUrlPipe), MockComponent(JhiConnectionWarningComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                guidedTourComponentFixture = TestBed.createComponent(GuidedTourComponent);
                guidedTourComponent = guidedTourComponentFixture.componentInstance;
                guidedTourService = TestBed.inject(GuidedTourService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should subscribe to events on after init', () => {
        const currentStepSpy = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToGuidedTourCurrentStepStream');
        const resizeEventSpy = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToResizeEvent');
        const scrollEventSpy = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToScrollEvent');
        const dotNavigationStub = jest.spyOn<any, any>(guidedTourComponent, 'subscribeToDotChanges').mockImplementation();
        const guidedTourInitStub = jest.spyOn(guidedTourService, 'init').mockImplementation();

        guidedTourComponent.ngAfterViewInit();

        expect(currentStepSpy).toHaveBeenCalledOnce();
        expect(resizeEventSpy).toHaveBeenCalledOnce();
        expect(scrollEventSpy).toHaveBeenCalledOnce();
        expect(dotNavigationStub).toHaveBeenCalledOnce();
        expect(guidedTourInitStub).toHaveBeenCalledOnce();
    });

    it('should handle user permissions', () => {
        guidedTourComponent.currentTourStep = tourStep;
        const permission = guidedTourComponent['hasUserPermissionForCurrentTourStep']();
        expect(permission).toBeTrue();
    });

    describe('Keydown Element', () => {
        beforeEach(() => {
            // Prepare guided tour service
            jest.spyOn(guidedTourService, 'init').mockImplementation();
            jest.spyOn(guidedTourService, 'getLastSeenTourStepIndex').mockReturnValue(0);
            // jest.spyOn<any, any>(guidedTourService, 'updateGuidedTourSettings');
            jest.spyOn<any, any>(guidedTourService, 'enableTour').mockImplementation(() => {
                guidedTourService['availableTourForComponent'] = courseOverviewTour;
                guidedTourService.currentTour = courseOverviewTour;
            });
            jest.spyOn<any, any>(guidedTourComponent, 'subscribeToDotChanges').mockImplementation();

            // Prepare guided tour component
            guidedTourComponent.ngAfterViewInit();

            // Start course overview tour
            guidedTourService['enableTour'](courseOverviewTour, true);
            guidedTourService['startTour']();

            guidedTourComponentFixture.detectChanges();
        });

        it('should highlight dots', () => {
            const highlightDot = guidedTourComponentFixture.debugElement.query(By.css('.current'));
            expect(highlightDot).not.toBe(null);
            const nSmallDot = guidedTourComponentFixture.debugElement.queryAll(By.css('.n-small'));
            expect(nSmallDot).not.toBe(null);
        });

        it('should not trigger the guided tour with the right arrow key', () => {
            guidedTourComponent.currentTourStep = undefined;
            const nextStepSpy = jest.spyOn(guidedTourService, 'nextStep');
            const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(nextStepSpy).not.toHaveBeenCalled();
        });

        it('should navigate next with the right arrow key', () => {
            guidedTourComponent['currentStepIndex'] = guidedTourService.currentTourStepIndex;
            guidedTourComponent['nextStepIndex'] = guidedTourService.currentTourStepIndex + 1;
            const nextStepSpy = jest.spyOn(guidedTourService, 'nextStep');
            const eventMock = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(nextStepSpy).toHaveBeenCalledOnce();
        });

        it('should navigate back with the left arrow key', () => {
            const backStepStub = jest.spyOn(guidedTourService, 'backStep').mockImplementation();
            const eventMockRight = new KeyboardEvent('keydown', { code: 'ArrowRight' });
            const eventMockLeft = new KeyboardEvent('keydown', { code: 'ArrowLeft' });

            guidedTourComponent.handleKeyboardEvent(eventMockLeft);
            expect(backStepStub).not.toHaveBeenCalled();

            guidedTourComponent.handleKeyboardEvent(eventMockRight);

            guidedTourComponent.handleKeyboardEvent(eventMockLeft);
            expect(backStepStub).toHaveBeenCalledOnce();
        });

        it('should skip the tour with the escape key', () => {
            const skipTourStub = jest.spyOn(guidedTourService, 'skipTour').mockImplementation();
            jest.spyOn<any, any>(guidedTourService, 'isCurrentTour').mockReturnValue(false);
            const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTourStub).toHaveBeenCalledOnce();

            // Reset component
            skipTourStub.mockReset();
            guidedTourComponent.currentTourStep = undefined;

            // Skip tour with ESC key should not be possible when the component is not active
            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTourStub).not.toHaveBeenCalled();
        });

        it('should not skip but finish the cancel tour with the escape key', () => {
            const skipTourStub = jest.spyOn(guidedTourService, 'skipTour').mockImplementation();
            const finishTourStub = jest.spyOn(guidedTourService, 'finishGuidedTour').mockImplementation();
            jest.spyOn<any, any>(guidedTourService, 'isCurrentTour').mockReturnValue(true);
            const eventMock = new KeyboardEvent('keydown', { code: 'Escape' });

            while (!guidedTourService.isOnLastStep) {
                guidedTourService.nextStep();
            }

            guidedTourComponent.handleKeyboardEvent(eventMock);
            expect(skipTourStub).not.toHaveBeenCalled();
            expect(finishTourStub).toHaveBeenCalledOnce();
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
            expect(guidedTourComponent['isBottom']()).toBeFalse();

            guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
            expect(guidedTourComponent['isBottom']()).toBeTrue();
        });

        it('should determine the highlight padding of the tour step', () => {
            expect(guidedTourComponent['getHighlightPadding']()).toBe(0);

            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['getHighlightPadding']()).toBe(10);
        });

        it('should calculate the top position of the tour step', () => {
            expect(guidedTourComponent.topPosition).toBe(0);

            setOrientation(Orientation.BOTTOM);
            expect(guidedTourComponent.topPosition).toBe(50);
        });

        it('should calculate the left position of the tour step', () => {
            expect(guidedTourComponent.leftPosition).toBe(-350);
        });

        it('should calculate the width of the tour step', () => {
            expect(guidedTourComponent.calculatedTourStepWidth).toBe(500);
        });

        it('should apply the right transformation', () => {
            setOrientation(Orientation.TOP);
            expect(guidedTourComponent.transform).toBe('translateY(-100%)');

            guidedTourComponent.currentTourStep!.orientation = Orientation.BOTTOM;
            expect(guidedTourComponent.transform).toBe('');
        });

        it('should calculate the right max width adjustment', () => {
            guidedTourComponent.tourStepWidth = 500;
            guidedTourComponent.minimalTourStepWidth = 400;
            expect(guidedTourComponent['maxWidthAdjustmentForTourStep']).toBe(100);
        });

        it('should calculate the left position of the highlighted element', () => {
            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toBe(-350);

            setOrientation(Orientation.TOPRIGHT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toBe(-500);

            setOrientation(Orientation.TOPLEFT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toBe(0);

            setOrientation(Orientation.LEFT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toBe(-510);

            setOrientation(Orientation.RIGHT);
            expect(guidedTourComponent['calculatedHighlightLeftPosition']).toBe(210);
        });

        it('should adjust the width for screen bound', () => {
            guidedTourComponent.currentTourStep = tourStepWithHighlightPadding;
            expect(guidedTourComponent['widthAdjustmentForScreenBound']).toBe(0);

            guidedTourComponent.tourStepWidth = 1000;
            expect(guidedTourComponent['widthAdjustmentForScreenBound']).toBe(500);
        });

        it('should calculate the right style for the overlays', () => {
            // Define expected objects
            const topStyle = { 'top.px': 0, 'left.px': 0, 'height.px': 0 };
            const bottomStyle = { 'top.px': 50 };
            const leftStyle = { 'top.px': 0, 'left.px': 0, 'height.px': 50, 'width.px': 0 };
            const rightStyle = { 'top.px': 0, 'left.px': 200, 'height.px': 50 };

            let style = guidedTourComponent.getOverlayStyle(OverlayPosition.TOP);
            expect(JSON.stringify(style)).toBe(JSON.stringify(topStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.BOTTOM);
            expect(JSON.stringify(style)).toBe(JSON.stringify(bottomStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.LEFT);
            expect(JSON.stringify(style)).toBe(JSON.stringify(leftStyle));
            style = guidedTourComponent.getOverlayStyle(OverlayPosition.RIGHT);
            expect(JSON.stringify(style)).toBe(JSON.stringify(rightStyle));
        });

        it('should initiate flip orientation', () => {
            window.scrollTo = () => {};
            jest.useFakeTimers();
            jest.spyOn<any, any>(guidedTourComponent, 'isTourOnScreen').mockReturnValue(false);
            const flipOrientationStub = jest.spyOn<any, any>(guidedTourComponent, 'flipOrientation').mockImplementation();
            guidedTourComponent['scrollToAndSetElement']();
            expect(flipOrientationStub).not.toHaveBeenCalled();

            jest.advanceTimersByTime(300);
            guidedTourComponent['scrollToAndSetElement']();
            expect(flipOrientationStub).toHaveBeenCalledOnce();
        });

        it('should flip orientation', () => {
            setOrientation(Orientation.BOTTOMLEFT);
            guidedTourComponent['flipOrientation']();
            expect(guidedTourComponent.orientation).toBe(Orientation.BOTTOMRIGHT);

            setOrientation(Orientation.TOPRIGHT);
            guidedTourComponent['flipOrientation']();
            expect(guidedTourComponent.orientation).toBe(Orientation.TOPLEFT);
        });
    });
});
