import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, ViewEncapsulation, HostListener } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { fromEvent, Subscription } from 'rxjs';

import { LinkType, Orientation } from './guided-tour.constants';
import { GuidedTourService } from './guided-tour.service';
import { AccountService } from 'app/core';
import { ImageTourStep, TextLinkTourStep, TextTourStep, TourStep, VideoTourStep } from 'app/guided-tour/guided-tour-step.model';

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
    public minimalTourStepWidth = 500;
    // Sets the highlight padding around the selected .
    public highlightPadding = 4;

    public currentTourStep: TourStep | TextTourStep | TextLinkTourStep | ImageTourStep | VideoTourStep | null;
    public selectedElementRect: DOMRect | null;

    private resizeSubscription: Subscription;
    private scrollSubscription: Subscription;

    readonly LinkType = LinkType;

    constructor(public sanitizer: DomSanitizer, public guidedTourService: GuidedTourService, public accountService: AccountService) {}

    /**
     * Enable tour navigation with left and right keyboard arrows and escape key
     * @param event: keyboard keydown event
     */
    @HostListener('document:keydown', ['$event'])
    handleKeyboardEvent(event: KeyboardEvent) {
        switch (event.code) {
            case 'ArrowRight': {
                if (this.guidedTourService.currentTourStepDisplay <= this.guidedTourService.currentTourStepCount) {
                    this.guidedTourService.nextStep();
                }
                break;
            }
            case 'ArrowLeft': {
                if (this.guidedTourService.currentTourStepDisplay > 1) {
                    this.guidedTourService.backStep();
                }
                break;
            }
            case 'Escape': {
                this.guidedTourService.skipTour();
                break;
            }
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
            if (this.hasUserPermissionForCurrentTourStep()) {
                this.scrollToAndSetElement();
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
            this.selectedElementRect = this.updateStepLocation(this.getSelectedElement());
        });
    }

    /**
     * Subscribe to scroll event and update step location of the selected element in the tour step
     */
    public subscribeToScrollEvent() {
        this.scrollSubscription = fromEvent(window, 'scroll').subscribe(() => {
            this.selectedElementRect = this.updateStepLocation(this.getSelectedElement());
        });
    }

    /**
     * Check if the current user has the permission to view the tour step
     * @return {boolean} if the current user has the permission to view the tour step
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
        this.selectedElementRect = this.updateStepLocation(this.getSelectedElement());

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
        });
    }

    /**
     * Check if the tour step element would be visible on screen
     * @return {boolean} if tour step is visible on screen
     */
    public isTourOnScreen(): boolean {
        if (!this.currentTourStep) {
            return false;
        }
        return !this.currentTourStep.selector || (this.tourStep && this.elementInViewport(this.getSelectedElement()) && this.elementInViewport(this.tourStep.nativeElement));
    }

    /**
     * Define if HTMLElement is visible in current viewport
     * Modified from https://stackoverflow.com/questions/123999/how-to-tell-if-a-dom-element-is-visible-in-the-current-viewport
     * @param element: HTMLElement
     * @return {boolean} if element is in viewport
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
     * @return {boolean} if the current tour step orientation is bottom
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
     * @return {number} top position for current tour step
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
     * @return {number} left position for current tour step
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
            topPosition = window.scrollY + this.selectedElementRect.top + positionAdjustment;
        }
        return topPosition;
    }

    /**
     * Gets defined padding around the highlighted rectangle
     * @return {number} highlight padding
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
     * Get overlay style for the highlighted rectangle of the selected element
     * @return style object for the highlighted element
     */
    public getOverlayStyle() {
        let top = 0;
        let left = 0;
        let height = 0;
        let width = 0;

        if (this.selectedElementRect) {
            top = this.selectedElementRect.top - this.getHighlightPadding();
            left = this.selectedElementRect.left - this.getHighlightPadding();
            height = this.selectedElementRect.height + this.getHighlightPadding() * 2;
            width = this.selectedElementRect.width + this.getHighlightPadding() * 2;
        }

        return { 'top.px': top, 'left.px': left, 'height.px': height, 'width.px': width };
    }

    /**
     * Transform position of tour steps which are shown on top of the highlighted element
     * @return {string} '' or 'translateY(-100%)'
     */
    public get transform(): string | null {
        if (
            this.currentTourStep &&
            ((!this.currentTourStep.orientation && this.currentTourStep.selector) ||
                this.currentTourStep.orientation === Orientation.TOP ||
                this.currentTourStep.orientation === Orientation.TOPRIGHT ||
                this.currentTourStep.orientation === Orientation.TOPLEFT)
        ) {
            return 'translateY(-100%)';
        }
        return '';
    }

    /**
     * Get Element for the current tour step selector
     * @return {HTMLElement} current selected element for the tour step or null
     */
    public getSelectedElement(): HTMLElement | null {
        if (!this.currentTourStep || !this.currentTourStep.selector) {
            return null;
        }
        return document.querySelector(this.currentTourStep.selector);
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
     * @return {number} left position of current tour step / highlighted element
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
     * @return {number} widthAdjustmentForScreenBound
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
     * @return {number} step screen adjustment
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
     * @return {selectedElementRect} selected element as DOMRect or null
     */
    public updateStepLocation(selectedElement: HTMLElement | null): DOMRect | null {
        let selectedElementRect = null;

        if (selectedElement) {
            selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
        }
        return selectedElementRect;
    }
}
