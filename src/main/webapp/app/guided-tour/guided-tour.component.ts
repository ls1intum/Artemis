import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, QueryList, Renderer2, ViewChild, ViewChildren, ViewEncapsulation } from '@angular/core';
import { Subscription, fromEvent } from 'rxjs';
import { take, tap } from 'rxjs/operators';
import { Direction, Orientation, OverlayPosition, UserInteractionEvent } from './guided-tour.constants';
import { GuidedTourService } from './guided-tour.service';
import { AccountService } from 'app/core/auth/account.service';
import { ImageTourStep, TextTourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';
import { cancelTour, completedTour } from 'app/guided-tour/tours/general-tour';
import { calculateLeftOffset, calculateTopOffset, isElementInViewPortHorizontally } from 'app/guided-tour/guided-tour.utils';
import {
    faArrowsAlt,
    faCheck,
    faChevronLeft,
    faChevronRight,
    faCircleNotch,
    faClipboardList,
    faEdit,
    faHandPointUp,
    faICursor,
    faInfoCircle,
    faPlayCircle,
    faVideo,
} from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-guided-tour',
    templateUrl: './guided-tour.component.html',
    styleUrls: ['./guided-tour.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class GuidedTourComponent implements AfterViewInit, OnDestroy {
    @ViewChild('tourStep', { static: false }) private tourStep: ElementRef;
    @ViewChild('dotNavigation', { static: false }) private dotNavigation: ElementRef;
    @ViewChildren('dotElements') private dotElements: QueryList<ElementRef>;

    // TODO automatically determine optimal width of tour step
    // Sets the width of all tour step elements.
    public tourStepWidth = 550;
    // Sets the minimal width of all tour step elements.
    public minimalTourStepWidth = 500;
    public orientation: Orientation;
    public transformX: number;
    /**
     * The current tour step should be of type the TourStep subclasses or undefined but have to be declared as any in this case
     * since the build would fail with Property 'x' does not exist on type 'y' when accessing properties of subclasses in the html template
     * that are not available for all subclasses
     */
    public currentTourStep: any;
    public selectedElementRect?: DOMRect;
    public startFade = false;
    public userInteractionFinished = false;

    private resizeSubscription: Subscription;
    private scrollSubscription: Subscription;
    private clickSubscription: Subscription;

    readonly OverlayPosition = OverlayPosition;
    readonly UserInteractionEvent = UserInteractionEvent;
    readonly cancelTour = cancelTour;
    readonly completedTour = completedTour;

    /** Variables for the dot navigation */
    public maxDots = 10;
    private transformCount = 0;
    private transformXIntervalNext = -26;
    private transformXIntervalPrev = 26;
    private dotArray: Array<ElementRef>;
    public currentStepIndex?: number;
    public nextStepIndex?: number;

    // Icons
    faEdit = faEdit;
    faCheck = faCheck;
    faVideo = faVideo;
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faCircleNotch = faCircleNotch;
    faInfoCircle = faInfoCircle;
    faArrowsAlt = faArrowsAlt;
    faClipboardList = faClipboardList;
    faICursor = faICursor;
    faHandPointUp = faHandPointUp;
    faPlayCircle = faPlayCircle;

    constructor(public guidedTourService: GuidedTourService, private accountService: AccountService, private renderer: Renderer2) {}

    /**
     * Enable tour navigation with left and right keyboard arrows and escape key
     * @param event: keyboard keydown event
     */
    @HostListener('document:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        if (this.guidedTourService.isOnResizeMessage) {
            return;
        }
        switch (event.code) {
            case 'ArrowRight': {
                /**
                 * Check if the currentTourStep is defined so that the guided tour cannot be started by pressing the right arrow
                 */
                if (this.currentTourStep && this.guidedTourService.currentTourStepDisplay <= this.guidedTourService.currentTourStepCount) {
                    /** If the user interaction is enabled, then the user has can only move to the next step after finishing the interaction */
                    if (!this.currentTourStep.userInteractionEvent || (this.currentTourStep.userInteractionEvent && this.userInteractionFinished)) {
                        this.guidedTourService.nextStep();
                    }
                }
                break;
            }
            case 'ArrowLeft': {
                if (this.currentTourStep && this.guidedTourService.currentTourStepDisplay > 1) {
                    this.guidedTourService.backStep();
                }
                break;
            }
            case 'Escape': {
                if (this.currentTourStep && !this.guidedTourService.isCurrentTour(cancelTour) && !this.guidedTourService.isCurrentTour(completedTour)) {
                    this.guidedTourService.skipTour();
                } else if (this.currentTourStep && this.guidedTourService.isOnLastStep) {
                    // The escape key event finishes the tour when the user is seeing the cancel tour step or last tour step
                    this.guidedTourService.finishGuidedTour();
                }
                break;
            }
        }
    }

    /**
     * Initial subscriptions for GuidedTourCurrentStepStream, resize event and scroll event
     */
    public ngAfterViewInit(): void {
        this.guidedTourService.init();
        this.subscribeToGuidedTourCurrentStepStream();
        this.subscribeToUserInteractionState();
        this.subscribeToResizeEvent();
        this.subscribeToScrollEvent();
        this.subscribeToClickEvent();
        this.subscribeToDotChanges();
    }

    /**
     * Remove subscriptions on destroy
     */
    public ngOnDestroy(): void {
        if (this.resizeSubscription) {
            this.resizeSubscription.unsubscribe();
        }
        if (this.resizeSubscription) {
            this.scrollSubscription.unsubscribe();
        }
        if (this.clickSubscription) {
            this.clickSubscription.unsubscribe();
        }
    }

    private subscribeToDotChanges() {
        this.dotElements.changes.subscribe((elements: QueryList<ElementRef>) => {
            this.dotArray = elements.toArray();
        });

        this.guidedTourService.currentDotSubject.pipe(tap((currentIndex) => (this.currentStepIndex = currentIndex))).subscribe();
        this.guidedTourService.nextDotSubject.pipe(tap((currentIndex) => (this.nextStepIndex = currentIndex))).subscribe();
    }

    /**
     * Subscribe to guidedTourCurrentStepStream and scroll to set element if the user has the right permission
     */
    private subscribeToGuidedTourCurrentStepStream() {
        this.guidedTourService.getGuidedTourCurrentStepStream().subscribe((step: TextTourStep | ImageTourStep | VideoTourStep) => {
            this.currentTourStep = step;
            if (!this.currentTourStep) {
                this.transformCount = 0;
                this.currentStepIndex = undefined;
                this.nextStepIndex = undefined;
                this.calculateTranslateValue();
                return;
            }

            if (this.currentTourStep.orientation) {
                this.orientation = this.currentTourStep.orientation;
            }

            if (this.hasUserPermissionForCurrentTourStep()) {
                this.scrollToAndSetElement();
                this.handleTransition();
                this.guidedTourService.isBackPageNavigation.next(false);
                if (this.currentStepIndex !== undefined && this.nextStepIndex !== undefined) {
                    setTimeout(() => {
                        this.calculateAndDisplayDotNavigation(this.currentStepIndex!, this.nextStepIndex!);
                    }, 0);
                } else {
                    this.calculateTranslateValue();
                }
                return;
            }
            this.selectedElementRect = undefined;
        });
    }

    /**
     * Subscribe to userInteractionFinished to determine if the user interaction has been executed
     */
    private subscribeToUserInteractionState(): void {
        // Check availability after first subscribe call since the router event been triggered already
        this.guidedTourService.userInteractionFinishedState().subscribe((isFinished) => {
            this.userInteractionFinished = isFinished;
        });
    }

    /**
     * Subscribe to resize event and update step location of the selected element in the tour step
     */
    private subscribeToResizeEvent() {
        this.resizeSubscription = fromEvent(window, 'resize').subscribe(() => {
            if (this.getSelectedElement()) {
                this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(), true);
            }
        });
    }

    /**
     * Subscribe to scroll event and update step location of the selected element in the tour step
     */
    private subscribeToScrollEvent() {
        this.scrollSubscription = fromEvent(window, 'scroll').subscribe(() => {
            if (this.getSelectedElement()) {
                this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(), true);
            }
        });
    }

    /**
     * Subscribe to click event and update step location of the selected element in the tour step
     */
    private subscribeToClickEvent() {
        this.clickSubscription = fromEvent(window, 'click').subscribe(() => {
            if (this.getSelectedElement()) {
                this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(), true);
            }
        });
    }

    /**
     * Check if the current user has the permission to view the tour step
     * @return true if the current user has the permission to view the tour step, otherwise false
     */
    private hasUserPermissionForCurrentTourStep(): boolean {
        if (!this.currentTourStep) {
            return false;
        }
        return !this.currentTourStep.permission || this.accountService.hasAnyAuthorityDirect(this.currentTourStep.permission);
    }

    /**
     * Scroll to and set highlighted element
     */
    private scrollToAndSetElement(): void {
        this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(), false);
        this.observeSelectedRectPosition();

        // Set timeout to allow things to render in order to scroll to the correct location
        setTimeout(() => {
            if (this.isTourOnScreen(Direction.VERTICAL) && this.isTourOnScreen(Direction.HORIZONTAL)) {
                return;
            }
            if (!this.isTourOnScreen(Direction.VERTICAL)) {
                const topPosition = this.getTopScrollingPosition();
                try {
                    window.scrollTo({ left: 0, top: topPosition, behavior: 'smooth' });
                } catch (err) {
                    if (err instanceof TypeError) {
                        window.scroll(0, topPosition);
                    } else {
                        throw err;
                    }
                }
            }
            if (!this.isTourOnScreen(Direction.HORIZONTAL)) {
                this.flipOrientation();
            }
        }, 0);
    }

    /**
     * Check if the current tour step has a bottom orientation
     * @return true if the current tour step orientation is bottom, otherwise false
     */
    private isBottom(): boolean {
        if (this.currentTourStep && this.currentTourStep.orientation) {
            return (
                this.currentTourStep.orientation === Orientation.BOTTOM ||
                this.currentTourStep.orientation === Orientation.BOTTOMLEFT ||
                this.currentTourStep.orientation === Orientation.BOTTOMRIGHT
            );
        }
        return false;
    }

    /**
     * Check if the current tour step has a top orientation
     * @return true if the current tour step orientation is bottom, otherwise false
     */
    private isTop(): boolean {
        if (this.currentTourStep && this.currentTourStep.orientation) {
            return (
                this.currentTourStep.orientation === Orientation.TOP ||
                this.currentTourStep.orientation === Orientation.TOPLEFT ||
                this.currentTourStep.orientation === Orientation.TOPRIGHT
            );
        }
        return false;
    }

    /**
     * Check if the current tour step has a left orientation
     * @return true if the current tour step orientation is left, otherwise false
     */
    private isLeft(): boolean {
        if (this.currentTourStep && this.currentTourStep.orientation) {
            return (
                this.currentTourStep.orientation === Orientation.LEFT ||
                this.currentTourStep.orientation === Orientation.TOPLEFT ||
                this.currentTourStep.orientation === Orientation.BOTTOMLEFT
            );
        }
        return false;
    }

    /**
     * Check if the current tour step has a right orientation
     * @return true if the current tour step orientation is right, otherwise false
     */
    private isRight(): boolean {
        if (this.currentTourStep && this.currentTourStep.orientation) {
            return (
                this.currentTourStep.orientation === Orientation.RIGHT ||
                this.currentTourStep.orientation === Orientation.TOPRIGHT ||
                this.currentTourStep.orientation === Orientation.BOTTOMRIGHT
            );
        }
        return false;
    }

    /**
     * Check if the tour step element would be visible on screen
     * @return true if tour step is visible on screen, otherwise false
     */
    private isTourOnScreen(direction: Direction): boolean {
        if (!this.currentTourStep) {
            return false;
        }
        return !this.currentTourStep.highlightSelector || this.elementInViewport(this.getSelectedElement(), direction);
    }

    /**
     * Define if HTMLElement is visible in current viewport
     * Modified from https://stackoverflow.com/questions/123999/how-to-tell-if-a-dom-element-is-visible-in-the-current-viewport
     * @param element that should be checked
     * @param direction it should be checked if the tour step is horizontally or vertically in the viewport
     * @return true if element is in viewport, otherwise false
     */
    private elementInViewport(element: HTMLElement | undefined, direction: Direction): boolean {
        if (!element) {
            return false;
        }

        let elementInViewPort = true;

        switch (direction) {
            case Direction.HORIZONTAL: {
                const width = element.offsetWidth;
                const left = calculateLeftOffset(element);
                const tourStepWidth = this.tourStep.nativeElement.offsetWidth;
                elementInViewPort = isElementInViewPortHorizontally(this.currentTourStep.orientation, left, width, tourStepWidth);
                break;
            }
            case Direction.VERTICAL: {
                const stepScreenAdjustment = this.isBottom() ? this.getStepScreenAdjustment() : -this.getStepScreenAdjustment();
                const top = calculateTopOffset(element);
                const height = element.offsetHeight;
                const tourStep = this.tourStep.nativeElement.getBoundingClientRect();
                const tourStepPosition = tourStep.top + tourStep.height;
                const windowHeight = window.innerHeight + window.scrollY;
                elementInViewPort = top >= window.scrollY - stepScreenAdjustment && top + height + 10 <= windowHeight && tourStepPosition <= windowHeight;
                break;
            }
        }
        return elementInViewPort;
    }

    /**
     * Flips the orientation of the current tour step horizontally
     */
    private flipOrientation(): void {
        if (this.isLeft()) {
            switch (this.currentTourStep.orientation) {
                case Orientation.LEFT: {
                    this.orientation = Orientation.RIGHT;
                    break;
                }
                case Orientation.TOPLEFT: {
                    this.orientation = Orientation.TOPRIGHT;
                    break;
                }
                case Orientation.BOTTOMLEFT: {
                    this.orientation = Orientation.BOTTOMRIGHT;
                    break;
                }
            }
        } else if (this.isRight()) {
            switch (this.currentTourStep.orientation) {
                case Orientation.RIGHT: {
                    this.orientation = Orientation.LEFT;
                    break;
                }
                case Orientation.TOPRIGHT: {
                    this.orientation = Orientation.TOPLEFT;
                    break;
                }
                case Orientation.BOTTOMRIGHT: {
                    this.orientation = Orientation.BOTTOMLEFT;
                    break;
                }
            }
        }
    }

    /**
     * Returns true if the current tour step is an instance of VideoTourStep
     */
    public isVideoTourStep(): boolean {
        return this.currentTourStep instanceof VideoTourStep;
    }

    /**
     * Handle backdrop clicking event of the user
     * @param event: event payload
     */
    public backdropClick(event: Event): void {
        if (this.guidedTourService.preventBackdropFromAdvancing) {
            event.stopPropagation();
        } else {
            this.guidedTourService.nextStep();
        }
        // When the user clicks on the backdrop or tour step while seeing the cancel tour step, the cancel tour will be finished automatically
        if (this.guidedTourService.isCurrentTour(cancelTour)) {
            this.guidedTourService.finishGuidedTour();
        }
    }

    /* ==========     Tour step calculation methods     ========== */

    /**
     * Calculate tour step width for tour step element
     * @return tour step width for tour-step div
     */
    public get calculatedTourStepWidth(): number | undefined {
        if (!this.currentTourStep || !this.selectedElementRect) {
            return undefined;
        }
        return this.tourStepWidth - this.widthAdjustmentForScreenBound;
    }

    /**
     * @return top position for current tour step
     */
    public get topPosition(): number | undefined {
        if (!this.currentTourStep || !this.selectedElementRect) {
            return undefined;
        }
        if (this.isBottom()) {
            const paddingAdjustment = this.getHighlightPadding();
            return this.selectedElementRect.top + this.selectedElementRect.height + paddingAdjustment;
        }
        return this.selectedElementRect.top - this.getHighlightPadding();
    }

    /**
     * @return left position for current tour step
     */
    public get leftPosition(): number | undefined {
        if (!this.currentTourStep || !this.selectedElementRect) {
            return undefined;
        }
        if (this.calculatedHighlightLeftPosition === 0) {
            return 5;
        }
        if (this.calculatedHighlightLeftPosition > 0) {
            return this.calculatedHighlightLeftPosition;
        }
        const adjustment = Math.max(0, -this.calculatedHighlightLeftPosition);
        const maxAdjustment = Math.min(this.maxWidthAdjustmentForTourStep, adjustment);
        return this.calculatedHighlightLeftPosition + maxAdjustment;
    }

    /**
     * Get top position for selected element for scrolling
     */
    private getTopScrollingPosition(): number {
        let topPosition = 0;
        let positionAdjustment = 0;
        if (this.selectedElementRect && this.currentTourStep) {
            const stepScreenAdjustment = this.getStepScreenAdjustment();
            const tourStep = this.tourStep.nativeElement.getBoundingClientRect();
            const totalStepHeight = this.selectedElementRect.height > tourStep.height ? this.selectedElementRect.height : tourStep.height;

            if (this.isBottom()) {
                positionAdjustment = stepScreenAdjustment;
            } else {
                positionAdjustment = totalStepHeight - window.innerHeight - stepScreenAdjustment;
            }

            if (this.isTop()) {
                // Scroll to 15px above the tour step
                topPosition = window.scrollY + tourStep.top - 15;
            } else {
                topPosition = window.scrollY + this.selectedElementRect.top + positionAdjustment;
                topPosition = topPosition < 15 ? 0 : topPosition + 15;
            }
        }
        return topPosition;
    }

    /**
     * Gets defined padding around the highlighted rectangle
     * @return highlight padding for current tour step
     */
    private getHighlightPadding(): number {
        if (!this.currentTourStep) {
            return 0;
        }
        return this.currentTourStep.highlightPadding ? this.currentTourStep.highlightPadding : 0;
    }

    /**
     * Get overlay style for the rectangles beside the highlighted element
     * @return style object for the rectangle beside the highlighted element
     */
    public getOverlayStyle(position: OverlayPosition) {
        if (this.selectedElementRect) {
            const selectedElementTop = this.selectedElementRect.top - this.getHighlightPadding();
            const selectedElementLeft = this.selectedElementRect.left - this.getHighlightPadding();
            const selectedElementHeight = this.selectedElementRect.height + this.getHighlightPadding() * 2;
            const selectedElementWidth = this.selectedElementRect.width + this.getHighlightPadding() * 2;

            switch (position) {
                case OverlayPosition.TOP:
                    return { 'top.px': 0, 'left.px': 0, 'height.px': selectedElementTop > 0 ? selectedElementTop : 0 };
                case OverlayPosition.LEFT:
                    return {
                        'top.px': selectedElementTop,
                        'left.px': selectedElementLeft < 0 ? selectedElementLeft : 0,
                        'height.px': selectedElementHeight,
                        'width.px': selectedElementLeft > 0 ? selectedElementLeft : 0,
                    };
                case OverlayPosition.RIGHT:
                    return { 'top.px': selectedElementTop, 'left.px': selectedElementLeft + selectedElementWidth, 'height.px': selectedElementHeight };
                case OverlayPosition.BOTTOM:
                    return { 'top.px': selectedElementTop + selectedElementHeight > 0 ? selectedElementTop + selectedElementHeight : 0 };
                case OverlayPosition.ELEMENT:
                    return { 'top.px': selectedElementTop, 'left.px': selectedElementLeft, 'height.px': selectedElementHeight, 'width.px': selectedElementWidth };
            }
        }
        return undefined;
    }

    /**
     * Transform position of tour steps which are shown on top of the highlighted element
     * @return {string} '' or 'translateY(-100%)'
     */
    public get transform(): string | undefined {
        if (
            this.currentTourStep &&
            ((!this.currentTourStep.orientation && this.currentTourStep.highlightSelector) ||
                this.currentTourStep.orientation === Orientation.TOP ||
                this.currentTourStep.orientation === Orientation.TOPRIGHT ||
                this.currentTourStep.orientation === Orientation.TOPLEFT)
        ) {
            return 'translateY(-100%)';
        }
        return '';
    }

    /**
     * Get element for the current tour step highlight selector
     * @return current selected element for the tour step or undefined
     */
    private getSelectedElement(): HTMLElement | undefined {
        if (!this.currentTourStep || !this.currentTourStep.highlightSelector) {
            return undefined;
        }
        const selectedElement = document.querySelector(this.currentTourStep.highlightSelector) as HTMLElement;

        // Workaround for instruction elements in the code-editor view, since the element can be in the viewport but hidden by the build output div
        const instructions = selectedElement ? selectedElement.closest('.instructions__content__markdown') : undefined;
        if (instructions && instructions.scrollHeight > window.innerHeight && instructions.querySelector(this.currentTourStep.highlightSelector)) {
            selectedElement.scrollIntoView({ block: 'center' });
        }
        return selectedElement;
    }

    /**
     * Calculate max width adjustment for tour step
     * @return {number} maxWidthAdjustmentForTourStep
     */
    private get maxWidthAdjustmentForTourStep(): number {
        return this.tourStepWidth - this.minimalTourStepWidth;
    }

    /**
     * Calculate the left position of the highlighted rectangle
     * @return left position of current tour step / highlighted element
     */
    private get calculatedHighlightLeftPosition(): number {
        if (!this.selectedElementRect || !this.currentTourStep) {
            return 0;
        }

        const paddingAdjustment = this.currentTourStep.highlightPadding ? this.currentTourStep.highlightPadding : 0;

        if (this.orientation === Orientation.TOPRIGHT || this.orientation === Orientation.BOTTOMRIGHT) {
            return this.selectedElementRect.right - this.tourStepWidth;
        }

        if (this.orientation === Orientation.TOPLEFT || this.orientation === Orientation.BOTTOMLEFT) {
            return this.selectedElementRect.left;
        }

        if (this.orientation === Orientation.LEFT) {
            return this.selectedElementRect.left - this.tourStepWidth - paddingAdjustment;
        }

        if (this.orientation === Orientation.RIGHT) {
            return this.selectedElementRect.left + this.selectedElementRect.width + paddingAdjustment;
        }
        return this.selectedElementRect.right - this.selectedElementRect.width / 2 - this.tourStepWidth / 2;
    }

    /**
     * Calculate width adjustment for screen bound
     * @return width adjustment for screen bound
     */
    private get widthAdjustmentForScreenBound(): number {
        let adjustment = 0;

        if (this.calculatedHighlightLeftPosition < 0) {
            adjustment = -this.calculatedHighlightLeftPosition;
        }
        if (this.calculatedHighlightLeftPosition > window.innerWidth - this.tourStepWidth) {
            adjustment = this.calculatedHighlightLeftPosition - (window.innerWidth - this.tourStepWidth);
        }
        return Math.min(this.maxWidthAdjustmentForTourStep, adjustment);
    }

    /**
     * Calculate a value to add or subtract so the step should not be off screen.
     * @return step screen adjustment
     */
    private getStepScreenAdjustment(): number {
        if (!this.selectedElementRect || !this.currentTourStep) {
            return 0;
        }
        if (this.orientation === Orientation.LEFT || this.orientation === Orientation.RIGHT) {
            return 0;
        }

        const elementHeight = this.selectedElementRect.height + this.tourStep.nativeElement.getBoundingClientRect().height;

        if (window.innerHeight < elementHeight) {
            return elementHeight - window.innerHeight;
        }
        return 0;
    }

    /**
     * Update tour step location and return selected element as DOMRect
     * @param selectedElement: selected element in DOM
     * @param isResizeOrScroll: true if this method is called by a resize or scroll event listener:
     *                          this method should not listen to user interactions when it is called through resizing or scrolling events
     * @return selected element as DOMRect or undefined
     */
    private updateStepLocation(selectedElement: HTMLElement | undefined, isResizeOrScroll: boolean): DOMRect | undefined {
        let selectedElementRect: DOMRect | undefined;
        if (selectedElement) {
            selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
            if (this.currentTourStep && this.currentTourStep.userInteractionEvent && !isResizeOrScroll) {
                if (this.currentTourStep.modelingTask) {
                    this.guidedTourService.enableUserInteraction(selectedElement, this.currentTourStep.userInteractionEvent, this.currentTourStep.modelingTask.umlName);
                } else {
                    this.guidedTourService.enableUserInteraction(selectedElement, this.currentTourStep.userInteractionEvent);
                }
            }
        }
        return selectedElementRect;
    }

    /**
     * Sets the startFade class for the tour step div to ease the transition between tour steps
     */
    private handleTransition() {
        this.startFade = true;
        setTimeout(() => {
            this.startFade = false;
        }, 1000);
    }

    /**
     * Observe and change position of highlight element
     */
    private observeSelectedRectPosition() {
        const selectedElement = this.getSelectedElement();
        const alertElement = document.querySelector('.alerts');

        // Observe alerts and update the highlight element position if necessary
        if (selectedElement && alertElement) {
            this.guidedTourService
                .observeMutations(alertElement, { childList: true })
                .pipe(take(1))
                .subscribe(() => {
                    if (this.getSelectedElement()) {
                        this.scrollToAndSetElement();
                    }
                });
        }
    }

    /**
     * Display only as many dots as defined in GuidedTourComponent.maxDots
     * @param currentIndex index of the current step
     * @param nextIndex index of the next step, this should (current step -/+ 1) depending on whether the user navigates forwards or backwards
     */
    private calculateAndDisplayDotNavigation(currentIndex: number, nextIndex: number) {
        const isForwardNavigation = nextIndex > currentIndex;
        const nextPlusOneIndex = isForwardNavigation ? nextIndex + 1 : nextIndex - 1;
        const nextDot = this.dotArray[nextIndex];
        const nextPlusOneDot = this.dotArray[nextPlusOneIndex];

        if (!nextDot || !nextPlusOneDot) {
            return;
        }

        if (nextIndex < this.maxDots) {
            this.transformX = 0;
        }

        if (isForwardNavigation) {
            this.handleForwardNavigation(nextDot.nativeElement, nextPlusOneDot.nativeElement, nextIndex);
        } else {
            this.handleBackwardNavigation(nextDot.nativeElement, nextPlusOneDot.nativeElement, nextIndex);
        }
    }

    private handleForwardNavigation(next: HTMLElement, nextPlusOne: HTMLElement, nextIndex: number) {
        // Moves the n-small and p-small class one dot further
        const lastDot = this.dotElements.last.nativeElement;
        if (!lastDot.classList.contains('n-small')) {
            this.dotArray.forEach((node: ElementRef, index: number) => {
                node.nativeElement.classList.remove('p-small');
                if (index === nextIndex - (this.maxDots - 2)) {
                    node.nativeElement.classList.add('p-small');
                }
            });
        }
        if (next && next.classList.contains('n-small') && lastDot && !lastDot.classList.contains('n-small')) {
            this.transformCount += this.transformXIntervalNext;
            this.renderer.setStyle(this.dotNavigation.nativeElement, 'transform', 'translateX(' + this.transformCount + 'px)');
            next.classList.remove('n-small');
            nextPlusOne.classList.add('n-small');
        }
    }

    private handleBackwardNavigation(next: HTMLElement, nextPlusOne: HTMLElement, nextIndex: number) {
        // Handles backwards navigation
        const firstDot = this.dotElements.first.nativeElement;
        this.dotArray.forEach((node: ElementRef, index: number) => {
            node.nativeElement.classList.remove('n-small');
            if ((this.transformCount !== 0 && index === nextIndex + 1) || (this.transformCount === 0 && index === this.maxDots - 1)) {
                node.nativeElement.classList.add('n-small');
            }
        });
        if (next && firstDot && !firstDot.classList.contains('p-small') && nextIndex > this.maxDots - 2) {
            this.transformCount += this.transformXIntervalPrev;
            if (this.transformCount !== 0) {
                this.transformX = this.transformCount;
            }
            if (next.classList.contains('p-small')) {
                next.classList.remove('p-small');
                nextPlusOne.classList.add('p-small');
            }
        }
    }

    /**
     * Defines the translateX value for the <ul> transform style
     */
    private calculateTranslateValue(): void {
        const currentTourStep = this.guidedTourService.currentTourStepIndex + 1;
        if (currentTourStep >= this.maxDots) {
            this.transformCount = ((currentTourStep % this.maxDots) + 1) * this.transformXIntervalNext;
        }
        this.transformX = this.transformCount;
    }

    /**
     * Defines if an <li> item should have the 'n-small' class
     * @param stepNumber tour step number of the <li> item
     */
    public calculateNSmallDot(stepNumber: number): boolean {
        if (this.guidedTourService.currentTourStepIndex < this.maxDots) {
            return stepNumber === this.maxDots;
        }
        if (this.nextStepIndex) {
            return stepNumber === this.nextStepIndex + 1;
        }
        return false;
    }

    /**
     * Defines if an <li> item should have the 'p-small' class
     * @param stepNumber tour step number of the <li> item
     */
    public calculatePSmallDot(stepNumber: number): boolean {
        if (
            this.guidedTourService.currentTourStepIndex > this.maxDots &&
            this.guidedTourService.currentTourStepIndex < this.guidedTourService.getFilteredTourSteps().length &&
            this.nextStepIndex
        ) {
            return this.nextStepIndex + 1 - stepNumber === 8;
        }
        return false;
    }
}
