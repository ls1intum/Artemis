import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, ViewEncapsulation, HostListener } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { fromEvent, Subscription } from 'rxjs';

import { ContentType, LinkType, Orientation } from './guided-tour.constants';
import { GuidedTourService } from './guided-tour.service';
import { AccountService } from 'app/core';
import { TourStep } from 'app/guided-tour/guided-tour-step.model';

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
    public tourStepWidth = 500;
    // Sets the minimal width of all tour step elements.
    public minimalTourStepWidth = 400;
    // Sets the highlight padding around the selected .
    public highlightPadding = 4;

    public currentTourStep: TourStep | null;
    public selectedElementRect: DOMRect | null;

    private resizeSubscription: Subscription;
    private scrollSubscription: Subscription;

    readonly ContentType = ContentType;
    readonly LinkType = LinkType;

    constructor(public sanitizer: DomSanitizer, public guidedTourService: GuidedTourService, public accountService: AccountService) {}

    /**
     * Enable tour navigation with left and right keyboard arrows and escape key
     * @param event: keyboard keydown event
     */
    @HostListener('document:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        if (event.code === 'ArrowRight' && this.guidedTourService.currentTourStepDisplay <= this.guidedTourService.currentTourStepCount) {
            this.guidedTourService.nextStep();
        }
        if (event.code === 'ArrowLeft' && this.guidedTourService.currentTourStepDisplay > 1) {
            this.guidedTourService.backStep();
        }
        if (event.code === 'Escape') {
            this.guidedTourService.skipTour();
        }
    }

    /**
     * Initial subscriptions for GuidedTourCurrentStepStream, resize event and scroll event
     */
    public ngAfterViewInit(): void {
        this.subscribeToGuidedTourCurrentStepStream();
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
        this.guidedTourService.getGuidedTourCurrentStepStream().subscribe((step: TourStep) => {
            this.currentTourStep = step;
            if (!this.currentTourStep) {
                return;
            }
            if (this.hasUserPermissionForTourStep(this.currentTourStep)) {
                this.scrollToAndSetElement(this.currentTourStep, this.tourStep, this.isBottom(this.currentTourStep));
                return;
            }
            this.selectedElementRect = null;
        });
    }

    /**
     * Subscribe to resize event and update step location of the selected element in the tour step
     */
    public subscribeToResizeEvent() {
        this.resizeSubscription = fromEvent(window, 'resize').subscribe(() => {
            this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(this.currentTourStep));
        });
    }

    /**
     * Subscribe to scroll event and update step location of the selected element in the tour step
     */
    public subscribeToScrollEvent() {
        this.scrollSubscription = fromEvent(window, 'scroll').subscribe(() => {
            this.selectedElementRect = this.updateStepLocation(this.getSelectedElement(this.currentTourStep));
        });
    }

    /**
     * Check if the current user has the permission to view the tour step
     * @param currentTourStep: current tour step of guided tour
     * @return {boolean} if the current user has the permission to view the tour step
     */
    public hasUserPermissionForTourStep(currentTourStep: TourStep): boolean {
        return !currentTourStep.permission || this.accountService.hasAnyAuthorityDirect(currentTourStep.permission);
    }

    /**
     * Scroll to and set highlighted element
     * @param currentTourStep: current tour step of the guided tour
     * @param tourStep: current tour step element
     * @param isBottomElement: boolean if the current tour step has a bottom orientation
     */
    public scrollToAndSetElement(currentTourStep: TourStep | null, tourStep: ElementRef, isBottomElement: boolean): void {
        const selectedElement = this.getSelectedElement(currentTourStep);
        this.selectedElementRect = this.updateStepLocation(selectedElement);
        if (!selectedElement || !this.isTourOnScreen(this.currentTourStep, tourStep, isBottomElement)) {
            return;
        }
        // Set timeout to allow things to render in order to scroll to the correct location
        setTimeout(() => {
            const topPosition = this.getTopScrollingPosition(this.selectedElementRect, currentTourStep, tourStep, this.isBottom(currentTourStep), this.topOfPageAdjustment);
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
        });
    }

    /**
     * Check if the tour step element would be visible on screen
     * @param currentTourStep: current tour step of the guided tour
     * @param tourStep: current tour step element
     * @param isBottomElement: boolean if the current tour step has a bottom orientation
     * @return {boolean} if tour step is visible on screen
     */
    public isTourOnScreen(currentTourStep: TourStep | null, tourStep: ElementRef, isBottomElement: boolean): boolean {
        if (currentTourStep && currentTourStep.selector) {
            return (
                tourStep &&
                this.elementInViewport(document.querySelector(currentTourStep.selector), isBottomElement) &&
                this.elementInViewport(tourStep.nativeElement, isBottomElement)
            );
        }
        return false;
    }

    /**
     * Define if HTMLElement is visible in current viewport
     * Modified from https://stackoverflow.com/questions/123999/how-to-tell-if-a-dom-element-is-visible-in-the-current-viewport
     * @param element: HTMLElement
     * @param isBottomElement: boolean if the current tour step has a bottom orientation
     * @return {boolean} if element is in viewport
     */
    public elementInViewport(element: HTMLElement | null, isBottomElement: boolean): boolean {
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
        const stepScreenAdjustment = this.getStepScreenAdjustment(this.selectedElementRect, this.currentTourStep, this.tourStep, this.topOfPageAdjustment);

        if (isBottomElement) {
            return top >= window.pageYOffset + this.topOfPageAdjustment + scrollAdjustment + stepScreenAdjustment && top + height <= window.pageYOffset + window.innerHeight;
        } else {
            return top >= window.pageYOffset + this.topOfPageAdjustment - stepScreenAdjustment && top + height + scrollAdjustment <= window.pageYOffset + window.innerHeight;
        }
    }

    /**
     * Handle backdrop clicking event of the user
     * @param {event} event payload
     */
    public backdropClick(event: Event): void {
        if (this.guidedTourService.preventBackdropFromAdvancing) {
            event.stopPropagation();
        } else {
            this.guidedTourService.nextStep();
        }
    }

    /**
     * Check if the current tour step has a bottom orientation
     * @param currentTourStep: current tour step of guided tour
     * @return {boolean} if the current tour step orientation is bottom
     */
    public isBottom(currentTourStep: TourStep | null): boolean {
        if (currentTourStep && currentTourStep.orientation) {
            return (
                currentTourStep.orientation === Orientation.Bottom ||
                currentTourStep.orientation === Orientation.BottomLeft ||
                currentTourStep.orientation === Orientation.BottomRight
            );
        }
        return false;
    }

    /* ==========     Tour step calculation methods     ========== */

    /**
     * Calculate tour step width for tour step element
     * @param selectedElementRect: DOMRect of the selected element
     * @param currentTourStep: current tour step of guided tour
     * @param tourStepWidth: defined width for tour step
     * @param minimalTourStepWidth: defined minimal width for tour step
     * @return tour step width for tour-step div
     */
    public getCalculatedTourStepWidth(selectedElementRect: DOMRect | null, currentTourStep: TourStep, tourStepWidth: number, minimalTourStepWidth: number): number {
        const calculatedLeftPosition = this.getCalculatedLeftPosition(selectedElementRect, currentTourStep, tourStepWidth);
        const maxWidthAdjustmentForTourStep = this.maxWidthAdjustmentForTourStep(tourStepWidth, minimalTourStepWidth);
        const widthAdjustmentForScreenBound = this.widthAdjustmentForScreenBound(tourStepWidth, calculatedLeftPosition, maxWidthAdjustmentForTourStep);
        return tourStepWidth - widthAdjustmentForScreenBound;
    }

    /**
     * @return {number} top position for current tour step
     */
    public getTopPosition(selectedElementRect: DOMRect | null, currentTourStep: TourStep | null, isBottomElement: boolean): number | null {
        if (!selectedElementRect || !currentTourStep) {
            return null;
        }
        if (isBottomElement) {
            const paddingAdjustment = this.getHighlightPadding(currentTourStep);
            return selectedElementRect.top + selectedElementRect.height + paddingAdjustment;
        }
        return selectedElementRect.top - this.getHighlightPadding(this.currentTourStep);
    }

    /**
     * @param selectedElementRect: DOMRect of the selected element
     * @param currentTourStep: current tour step of guided tour
     * @param tourStepWidth: defined width for tour step
     * @param minimalTourStepWidth: defined minimal width for tour step
     * @return {number} left position for current tour step
     */
    public getLeftPosition(selectedElementRect: DOMRect | null, currentTourStep: TourStep | null, tourStepWidth: number, minimalTourStepWidth: number): number | null {
        if (!selectedElementRect || !currentTourStep) {
            return null;
        }
        const calculatedLeftPosition = this.getCalculatedLeftPosition(selectedElementRect, currentTourStep, tourStepWidth);

        if (calculatedLeftPosition === 0) {
            return 5;
        }
        if (calculatedLeftPosition > 0) {
            return calculatedLeftPosition;
        }
        const adjustment = Math.max(0, -calculatedLeftPosition);
        const maxAdjustment = Math.min(this.maxWidthAdjustmentForTourStep(tourStepWidth, minimalTourStepWidth), adjustment);
        return calculatedLeftPosition + maxAdjustment;
    }

    /**
     * Get top position for selected element for scrolling
     * @param selectedElementRect: DOMRect of the selected element
     * @param currentTourStep: current tour step of guided tour
     * @param tourStep: current tour step element
     * @param isBottomElement: boolean if the current tour step has a bottom orientation
     * @param topOfPageAdjustment: top page adjustment for scrolling
     */
    public getTopScrollingPosition(
        selectedElementRect: DOMRect | null,
        currentTourStep: TourStep | null,
        tourStep: ElementRef,
        isBottomElement: boolean,
        topOfPageAdjustment: number,
    ): number {
        let topPosition = 0;
        if (selectedElementRect && currentTourStep) {
            const scrollAdjustment = currentTourStep.scrollAdjustment ? currentTourStep.scrollAdjustment : 0;
            const stepScreenAdjustment = this.getStepScreenAdjustment(selectedElementRect, currentTourStep, tourStep, topOfPageAdjustment);

            if (selectedElementRect) {
                const positionAdjustment = isBottomElement
                    ? -topOfPageAdjustment - scrollAdjustment + stepScreenAdjustment
                    : +selectedElementRect.height - window.innerHeight + scrollAdjustment - stepScreenAdjustment;
                topPosition = window.scrollY + selectedElementRect.top + positionAdjustment;
            }
        }
        return topPosition;
    }

    /**
     * Gets defined padding around the highlighted rectangle
     * @param currentTourStep: current tour step of guided tour
     * @return {number} highlight padding
     */
    public getHighlightPadding(currentTourStep: TourStep | null): number {
        if (currentTourStep) {
            let paddingAdjustment = currentTourStep.useHighlightPadding ? this.highlightPadding : 0;
            if (currentTourStep.highlightPadding) {
                paddingAdjustment = currentTourStep.highlightPadding;
            }
            return paddingAdjustment;
        }
        return 0;
    }

    /**
     * Get overlay style for the highlighted rectangle of the selected element
     * @param selectedElementRect: DOMRect of the selected element
     * @param currentTourStep: current tour step of guided tour
     * @return style object for the highlighted element
     */
    public getOverlayStyle(selectedElementRect: DOMRect | null, currentTourStep: TourStep) {
        let top = 0;
        let left = 0;
        let height = 0;
        let width = 0;

        if (selectedElementRect) {
            top = selectedElementRect.top - this.getHighlightPadding(currentTourStep);
            left = selectedElementRect.left - this.getHighlightPadding(currentTourStep);
            height = selectedElementRect.height + this.getHighlightPadding(currentTourStep) * 2;
            width = selectedElementRect.width + this.getHighlightPadding(currentTourStep) * 2;
        }

        return { 'top.px': top, 'left.px': left, 'height.px': height, 'width.px': width };
    }

    /**
     * Transform position of tour steps which are shown on top of the highlighted element
     * @param selectedElementRect: DOMRect of the selected element
     * @param currentTourStep: current tour step of guided tour
     * @return {string} '' or 'translateY(-100%)'
     */
    public getTransform(selectedElementRect: DOMRect | null, currentTourStep: TourStep): string | null {
        if (!selectedElementRect || !currentTourStep.selector) {
            return null;
        }
        if (
            currentTourStep &&
            (!currentTourStep.orientation ||
                currentTourStep.orientation === Orientation.Top ||
                currentTourStep.orientation === Orientation.TopRight ||
                currentTourStep.orientation === Orientation.TopLeft)
        ) {
            return 'translateY(-100%)';
        }
        return '';
    }

    /**
     * Get Element for the current tour step selector
     * @param currentTourStep: current tour step of guided tour
     * @return {Element} current selected element for the tour step or null
     */
    public getSelectedElement(currentTourStep: TourStep | null): Element | null {
        if (!currentTourStep || !currentTourStep.selector) {
            return null;
        }
        return document.querySelector(currentTourStep.selector);
    }

    /**
     * Calculate max width adjustment for tour step
     * @param tourStepWidth: defined width for tour step
     * @param minimalTourStepWidth: defined minimal width for tour step
     * @return {number} maxWidthAdjustmentForTourStep
     */
    public maxWidthAdjustmentForTourStep(tourStepWidth: number, minimalTourStepWidth: number) {
        return tourStepWidth - minimalTourStepWidth;
    }

    /**
     *
     * @param selectedElementRect: DOMRect of the selected element: DOMRect of the selected element
     * @param currentTourStep: current tour step of guided tour
     * @param tourStepWidth: defined width for tour step
     * @return {number} left position of current tour step / highlighted element
     */
    public getCalculatedLeftPosition(selectedElementRect: DOMRect | null, currentTourStep: TourStep | null, tourStepWidth: number) {
        if (!selectedElementRect || !currentTourStep) {
            return 0;
        }

        const paddingAdjustment = currentTourStep.highlightPadding ? currentTourStep.highlightPadding : 0;

        if (currentTourStep.orientation === Orientation.TopRight || currentTourStep.orientation === Orientation.BottomRight) {
            return selectedElementRect.right - tourStepWidth;
        }

        if (currentTourStep.orientation === Orientation.TopLeft || currentTourStep.orientation === Orientation.BottomLeft) {
            return selectedElementRect.left;
        }

        if (currentTourStep.orientation === Orientation.Left) {
            return selectedElementRect.left - tourStepWidth - paddingAdjustment;
        }

        if (currentTourStep.orientation === Orientation.Right) {
            return selectedElementRect.left + selectedElementRect.width + paddingAdjustment;
        }
        return selectedElementRect.right - selectedElementRect.width / 2 - tourStepWidth / 2;
    }

    /**
     * Calculate width adjustment for screen bound
     * @param tourStepWidth: defined width for tour step
     * @param calculatedLeftPosition
     * @param maxWidthAdjustmentForTourStep
     * @return {number} widthAdjustmentForScreenBound
     */
    public widthAdjustmentForScreenBound(tourStepWidth: number, calculatedLeftPosition: number, maxWidthAdjustmentForTourStep: number) {
        let adjustment = 0;

        if (calculatedLeftPosition < 0) {
            adjustment = -calculatedLeftPosition;
        }
        if (calculatedLeftPosition > window.innerWidth - tourStepWidth) {
            adjustment = calculatedLeftPosition - (window.innerWidth - tourStepWidth);
        }
        return Math.min(maxWidthAdjustmentForTourStep, adjustment);
    }

    /**
     * Calculate a value to add or subtract so the step should not be off screen.
     * @param selectedElementRect: DOMRect of the selected element
     * @param currentTourStep: current tour step of guided tour
     * @param tourStep: current tour step element
     * @param topOfPageAdjustment: top page adjustment for scrolling
     * @return {number} step screen adjustment
     */
    public getStepScreenAdjustment(selectedElementRect: DOMRect | null, currentTourStep: TourStep | null, tourStep: ElementRef, topOfPageAdjustment: number): number {
        if (!selectedElementRect || !currentTourStep) {
            return 0;
        }
        if (currentTourStep.orientation === Orientation.Left || currentTourStep.orientation === Orientation.Right) {
            return 0;
        }

        const scrollAdjustment = currentTourStep.scrollAdjustment ? currentTourStep.scrollAdjustment : 0;
        const elementHeight = selectedElementRect.height + scrollAdjustment + tourStep.nativeElement.getBoundingClientRect().height;

        if (window.innerHeight - topOfPageAdjustment < elementHeight) {
            return elementHeight - (window.innerHeight - topOfPageAdjustment);
        }
        return 0;
    }

    /**
     * Update tour step location and return selected element as DOMRect
     * @param selectedElement: selected element in DOM
     * @return {selectedElementRect} selected element as DOMRect or null
     */
    public updateStepLocation(selectedElement: Element | null): DOMRect | null {
        let selectedElementRect = null;

        if (selectedElement) {
            selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
        }
        return selectedElementRect;
    }
}
