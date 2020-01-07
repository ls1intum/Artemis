import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild, ViewEncapsulation } from '@angular/core';
import { fromEvent, Subscription } from 'rxjs';
import { take } from 'rxjs/internal/operators';

import { Orientation, OverlayPosition, UserInteractionEvent } from './guided-tour.constants';
import { GuidedTourService } from './guided-tour.service';
import { AccountService } from 'app/core/auth/account.service';
import { ImageTourStep, TextTourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';
import { cancelTour, completedTour } from 'app/guided-tour/tours/general-tour';

@Component({
    selector: 'jhi-guided-tour',
    templateUrl: './guided-tour.component.html',
    styleUrls: ['./guided-tour.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class GuidedTourComponent implements AfterViewInit, OnDestroy {
    @ViewChild('tourStep', { static: false }) public tourStep: ElementRef;

    // Used to adjust values to determine scroll. This is a blanket value to adjust for elements like nav bars.
    public topOfPageAdjustment = 0;
    // Sets the width of all tour step elements.
    // TODO automatically determine optimal width of tourstep
    public tourStepWidth = 550;
    // Sets the minimal width of all tour step elements.
    public minimalTourStepWidth = 500;
    // Sets the highlight padding around the selected .
    public highlightPadding = 4;
    public transformX: number;
    /**
     * The current tour step should be of type the TourStep subclasses or null but have to be declared as any in this case
     * since the build would fail with Property 'x' does not exist on type 'y' when accessing properties of subclasses in the html template
     * that are not available for all subclasses
     */
    public currentTourStep: any;
    public selectedElementRect: DOMRect | null;
    public startFade = false;
    public userInteractionFinished = false;

    private resizeSubscription: Subscription;
    private scrollSubscription: Subscription;

    readonly OverlayPosition = OverlayPosition;
    readonly UserInteractionEvent = UserInteractionEvent;
    readonly cancelTour = cancelTour;
    readonly completedTour = completedTour;

    constructor(public guidedTourService: GuidedTourService, public accountService: AccountService) {}

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
                    this.guidedTourService.skipTour(true, false);
                } else if (this.currentTourStep && this.guidedTourService.isOnLastStep) {
                    // The escape key event finishes the tour when the user is seeing the cancel tour step or last tour step
                    this.guidedTourService.finishGuidedTour(false);
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
    }

    /**
     * Subscribe to guidedTourCurrentStepStream and scroll to set element if the user has the right permission
     */
    public subscribeToGuidedTourCurrentStepStream() {
        this.guidedTourService.getGuidedTourCurrentStepStream().subscribe((step: TextTourStep | ImageTourStep | VideoTourStep) => {
            this.currentTourStep = step;
            if (!this.currentTourStep) {
                return;
            }
            if (this.hasUserPermissionForCurrentTourStep()) {
                this.scrollToAndSetElement();
                this.handleTransition();
                return;
            }
            this.selectedElementRect = null;
        });
        this.guidedTourService.calculateTransformValue().subscribe(transformX => {
            this.transformX = transformX;
        });
    }

    /**
     * Subscribe to userInteractionFinished to determine if the user interaction has been done
     */
    public subscribeToUserInteractionState(): void {
        // Check availability after first subscribe call since the router event been triggered already
        this.guidedTourService.userInteractionFinishedState().subscribe(isFinished => {
            this.userInteractionFinished = isFinished;
        });
    }

    /**
     * Subscribe to resize event and update step location of the selected element in the tour step
     */
    public subscribeToResizeEvent() {
        this.resizeSubscription = fromEvent(window, 'resize').subscribe(() => {
            this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(), true);
        });
    }

    /**
     * Subscribe to scroll event and update step location of the selected element in the tour step
     */
    public subscribeToScrollEvent() {
        this.scrollSubscription = fromEvent(window, 'scroll').subscribe(() => {
            this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(), true);
        });
    }

    /**
     * Check if the current user has the permission to view the tour step
     * @return true if the current user has the permission to view the tour step, otherwise false
     */
    public hasUserPermissionForCurrentTourStep(): boolean {
        if (!this.currentTourStep) {
            return false;
        }
        return !this.currentTourStep.permission || this.accountService.hasAnyAuthorityDirect(this.currentTourStep.permission);
    }

    /**
     * Scroll to and set highlighted element
     */
    public scrollToAndSetElement(): void {
        this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(), false);
        this.observeSelectedRectPosition();

        // Set timeout to allow things to render in order to scroll to the correct location
        setTimeout(() => {
            if (this.isTourOnScreen()) {
                return;
            }
            const topPosition = this.getTopScrollingPosition();
            try {
                window.scrollTo({
                    left: 0,
                    top: topPosition,
                    behavior: 'smooth',
                });
            } catch (err) {
                if (err instanceof TypeError) {
                    window.scroll(0, topPosition);
                } else {
                    throw err;
                }
            }
        }, 0);
    }

    /**
     * Check if the tour step element would be visible on screen
     * @return true if tour step is visible on screen, otherwise false
     */
    public isTourOnScreen(): boolean {
        if (!this.currentTourStep) {
            return false;
        }
        return !this.currentTourStep.highlightSelector || (this.elementInViewport(this.getSelectedElement()) && this.elementInViewport(this.tourStep.nativeElement));
    }

    /**
     * Define if HTMLElement is visible in current viewport
     * Modified from https://stackoverflow.com/questions/123999/how-to-tell-if-a-dom-element-is-visible-in-the-current-viewport
     * @param element that should be checked
     * @return true if element is in viewport, otherwise false
     */
    public elementInViewport(element: HTMLElement | null): boolean {
        if (!element) {
            return false;
        }
        let top = element.offsetTop;
        const height = element.offsetHeight;

        while (element.offsetParent) {
            element = element.offsetParent as HTMLElement;
            top += element.offsetTop;
        }

        const scrollAdjustment = this.currentTourStep && this.currentTourStep.scrollAdjustment ? this.currentTourStep.scrollAdjustment : 0;
        const stepScreenAdjustment = this.getStepScreenAdjustment();

        if (this.isBottom()) {
            return top >= window.pageYOffset + this.topOfPageAdjustment + scrollAdjustment + stepScreenAdjustment && top + height <= window.innerHeight;
        } else {
            return top >= window.pageYOffset + this.topOfPageAdjustment - stepScreenAdjustment && top + height + scrollAdjustment <= window.innerHeight;
        }
    }

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

    /**
     * Check if the current tour step has a bottom orientation
     * @return true if the current tour step orientation is bottom, otherwise false
     */
    public isBottom(): boolean {
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
    public isTop(): boolean {
        if (this.currentTourStep && this.currentTourStep.orientation) {
            return (
                this.currentTourStep.orientation === Orientation.TOP ||
                this.currentTourStep.orientation === Orientation.TOPLEFT ||
                this.currentTourStep.orientation === Orientation.TOPRIGHT
            );
        }
        return false;
    }

    /* ==========     Tour step calculation methods     ========== */

    /**
     * Calculate tour step width for tour step element
     * @return tour step width for tour-step div
     */
    public get calculatedTourStepWidth(): number | null {
        if (!this.currentTourStep || !this.selectedElementRect) {
            return null;
        }
        return this.tourStepWidth - this.widthAdjustmentForScreenBound;
    }

    /**
     * @return top position for current tour step
     */
    public get topPosition(): number | null {
        if (!this.currentTourStep || !this.selectedElementRect) {
            return null;
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
    public get leftPosition(): number | null {
        if (!this.currentTourStep || !this.selectedElementRect) {
            return null;
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
    public getTopScrollingPosition(): number {
        let topPosition = 0;
        if (this.selectedElementRect && this.currentTourStep) {
            const scrollAdjustment = this.currentTourStep.scrollAdjustment ? this.currentTourStep.scrollAdjustment : 0;
            const stepScreenAdjustment = this.getStepScreenAdjustment();
            const positionAdjustment = this.isBottom()
                ? -this.topOfPageAdjustment - scrollAdjustment + stepScreenAdjustment
                : +this.selectedElementRect.height - window.innerHeight + scrollAdjustment - stepScreenAdjustment;
            topPosition = this.isTop() ? window.scrollY + this.tourStep.nativeElement.getBoundingClientRect().top - 15 : this.selectedElementRect.top + positionAdjustment;
        }
        return topPosition;
    }

    /**
     * Gets defined padding around the highlighted rectangle
     * @return highlight padding for current tour step
     */
    public getHighlightPadding(): number {
        if (this.currentTourStep) {
            let paddingAdjustment = this.currentTourStep.highlightPadding ? this.highlightPadding : 0;
            if (this.currentTourStep.highlightPadding) {
                paddingAdjustment = this.currentTourStep.highlightPadding;
            }
            return paddingAdjustment;
        }
        return 0;
    }

    /**
     * Get overlay style for the rectangles beside the highlighted element
     * @return style object for the rectangle beside the highlighted element
     */
    public getOverlayStyle(position: OverlayPosition) {
        let style;

        if (this.selectedElementRect) {
            const selectedElementTop = this.selectedElementRect.top - this.getHighlightPadding();
            const selectedElementLeft = this.selectedElementRect.left - this.getHighlightPadding();
            const selectedElementHeight = this.selectedElementRect.height + this.getHighlightPadding() * 2;
            const selectedElementWidth = this.selectedElementRect.width + this.getHighlightPadding() * 2;

            switch (position) {
                case OverlayPosition.TOP: {
                    style = { 'top.px': 0, 'left.px': 0, 'height.px': selectedElementTop > 0 ? selectedElementTop : 0 };
                    break;
                }
                case OverlayPosition.LEFT: {
                    style = {
                        'top.px': selectedElementTop,
                        'left.px': selectedElementLeft < 0 ? selectedElementLeft : 0,
                        'height.px': selectedElementHeight,
                        'width.px': selectedElementLeft > 0 ? selectedElementLeft : 0,
                    };
                    break;
                }
                case OverlayPosition.RIGHT: {
                    style = { 'top.px': selectedElementTop, 'left.px': selectedElementLeft + selectedElementWidth, 'height.px': selectedElementHeight };
                    break;
                }
                case OverlayPosition.BOTTOM: {
                    style = { 'top.px': selectedElementTop + selectedElementHeight > 0 ? selectedElementTop + selectedElementHeight : 0 };
                    break;
                }
                case OverlayPosition.ELEMENT: {
                    style = { 'top.px': selectedElementTop, 'left.px': selectedElementLeft, 'height.px': selectedElementHeight, 'width.px': selectedElementWidth };
                }
            }
        }
        return style;
    }

    /**
     * Transform position of tour steps which are shown on top of the highlighted element
     * @return {string} '' or 'translateY(-100%)'
     */
    public get transform(): string | null {
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
     * Get Element for the current tour step hightlight selector
     * @return current selected element for the tour step or null
     */
    public getSelectedElement(): HTMLElement | null {
        if (!this.currentTourStep || !this.currentTourStep.highlightSelector) {
            return null;
        }
        const selectedElement = document.querySelector(this.currentTourStep.highlightSelector) as HTMLElement;

        // Workaround for instruction elements in the code-editor view, since the element can be in the viewport but hidden by the build output div
        const instructions = selectedElement ? selectedElement.closest('.instructions__content__markdown') : null;
        if (instructions && instructions.scrollHeight > window.innerHeight && instructions.querySelector(this.currentTourStep.highlightSelector)) {
            selectedElement.scrollIntoView({ block: 'center' });
        }
        return selectedElement;
    }

    /**
     * Get Element for the current tour step event listener selector
     * @return selected element for the event listener or null
     */
    public getClickEventListenerSelector(): HTMLElement | null {
        if (!this.currentTourStep || !this.currentTourStep.clickEventListenerSelector) {
            return null;
        }
        return document.querySelector(this.currentTourStep.clickEventListenerSelector);
    }

    /**
     * Calculate max width adjustment for tour step
     * @return {number} maxWidthAdjustmentForTourStep
     */
    public get maxWidthAdjustmentForTourStep(): number {
        return this.tourStepWidth - this.minimalTourStepWidth;
    }

    /**
     * Calculate the left position of the highlighted rectangle
     * @return left position of current tour step / highlighted element
     */
    public get calculatedHighlightLeftPosition(): number {
        if (!this.selectedElementRect || !this.currentTourStep) {
            return 0;
        }

        const paddingAdjustment = this.currentTourStep.highlightPadding ? this.currentTourStep.highlightPadding : 0;

        if (this.currentTourStep.orientation === Orientation.TOPRIGHT || this.currentTourStep.orientation === Orientation.BOTTOMRIGHT) {
            return this.selectedElementRect.right - this.tourStepWidth;
        }

        if (this.currentTourStep.orientation === Orientation.TOPLEFT || this.currentTourStep.orientation === Orientation.BOTTOMLEFT) {
            return this.selectedElementRect.left;
        }

        if (this.currentTourStep.orientation === Orientation.LEFT) {
            return this.selectedElementRect.left - this.tourStepWidth - paddingAdjustment;
        }

        if (this.currentTourStep.orientation === Orientation.RIGHT) {
            return this.selectedElementRect.left + this.selectedElementRect.width + paddingAdjustment;
        }
        return this.selectedElementRect.right - this.selectedElementRect.width / 2 - this.tourStepWidth / 2;
    }

    /**
     * Calculate width adjustment for screen bound
     * @return widthAdjustmentForScreenBound
     */
    public get widthAdjustmentForScreenBound(): number {
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
    public getStepScreenAdjustment(): number {
        if (!this.selectedElementRect || !this.currentTourStep) {
            return 0;
        }
        if (this.currentTourStep.orientation === Orientation.LEFT || this.currentTourStep.orientation === Orientation.RIGHT) {
            return 0;
        }

        const scrollAdjustment = this.currentTourStep.scrollAdjustment ? this.currentTourStep.scrollAdjustment : 0;
        const elementHeight = this.selectedElementRect.height + scrollAdjustment + this.tourStep.nativeElement.getBoundingClientRect().height;

        if (window.innerHeight - this.topOfPageAdjustment < elementHeight) {
            return elementHeight - (window.innerHeight - this.topOfPageAdjustment);
        }
        return 0;
    }

    /**
     * Update tour step location and return selected element as DOMRect
     * @param selectedElement: selected element in DOM
     * @param isResizeOrScroll: true if this method is called by a resize or scroll event listener: this method should not listen to user interactions when it is called through resizing or scrolling events
     * @return selected element as DOMRect or null
     */
    public updateStepLocation(selectedElement: HTMLElement | null, isResizeOrScroll: boolean): DOMRect | null {
        let selectedElementRect = null;
        if (selectedElement) {
            selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
            if (this.currentTourStep && this.currentTourStep.userInteractionEvent && !isResizeOrScroll) {
                const clickEventListenerElement = this.getClickEventListenerSelector();
                if (clickEventListenerElement) {
                    selectedElement = clickEventListenerElement;
                }
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
                .observeMutations(document.querySelector('.alerts'), { childList: true })
                .pipe(take(1))
                .subscribe(mutation => {
                    this.scrollToAndSetElement();
                });
        }
    }
}
