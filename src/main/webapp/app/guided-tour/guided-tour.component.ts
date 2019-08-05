import { AfterViewInit, Component, ElementRef, Input, OnDestroy, ViewChild, ViewEncapsulation, HostListener } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { fromEvent, Subscription } from 'rxjs';

import { ContentType, LinkType, Orientation, TourStep } from './guided-tour.constants';
import { GuidedTourService } from './guided-tour.service';
import { AccountService } from 'app/core';

@Component({
    selector: 'jhi-guided-tour',
    templateUrl: './guided-tour.component.html',
    styleUrls: ['./guided-tour.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class GuidedTourComponent implements AfterViewInit, OnDestroy {
    @Input() public topOfPageAdjustment = 0;
    @Input() public tourStepWidth = 500;
    @Input() public minimalTourStepWidth = 400;
    @ViewChild('tourStep', { static: false }) public tourStep: ElementRef;
    public highlightPadding = 4;
    public currentTourStep: TourStep | null;
    public selectedElementRect: DOMRect | null;

    private resizeSubscription: Subscription;
    private scrollSubscription: Subscription;

    readonly ContentType = ContentType;
    readonly LinkType = LinkType;

    constructor(public sanitizer: DomSanitizer, public guidedTourService: GuidedTourService, public accountService: AccountService) {}

    /* Enable tour navigation with keyboard arrows */
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
     * Subscribe to guidedTourCurrentStepStream and set tour step if the user has the right permission
     */
    public ngAfterViewInit(): void {
        this.guidedTourService.getGuidedTourCurrentStepStream().subscribe((step: TourStep) => {
            this.currentTourStep = step;
            if (!step) {
                return;
            }
            const hasPermission = !step.permission || this.accountService.hasAnyAuthorityDirect(step.permission);
            if (step.selector && hasPermission) {
                const selectedElement = document.querySelector(step.selector);
                if (selectedElement) {
                    this.scrollToAndSetElement();
                    return;
                }
            }
            this.selectedElementRect = null;
        });

        this.resizeSubscription = fromEvent(window, 'resize').subscribe(() => {
            this.updateStepLocation();
        });

        this.scrollSubscription = fromEvent(window, 'scroll').subscribe(() => {
            this.updateStepLocation();
        });
    }

    /**
     * Remove subscriptions on destroy
     */
    public ngOnDestroy(): void {
        this.resizeSubscription.unsubscribe();
        this.scrollSubscription.unsubscribe();
    }

    /**
     * Calculate max width adjustment for tour step
     * @return {number} maxWidthAdjustmentForTourStep
     */
    private get maxWidthAdjustmentForTourStep(): number {
        return this.tourStepWidth - this.minimalTourStepWidth;
    }

    /**
     * Calculate width adjustment for screen bound
     * @return {number} widthAdjustmentForScreenBound
     */
    private get widthAdjustmentForScreenBound(): number {
        if (!this.tourStep) {
            return 0;
        }
        let adjustment = 0;
        if (this.calculatedLeftPosition < 0) {
            adjustment = -this.calculatedLeftPosition;
        }
        if (this.calculatedLeftPosition > window.innerWidth - this.tourStepWidth) {
            adjustment = this.calculatedLeftPosition - (window.innerWidth - this.tourStepWidth);
        }

        return Math.min(this.maxWidthAdjustmentForTourStep, adjustment);
    }

    /**
     * Calculate tour step width for tour-step div
     * @return tour step width for tour-step div
     */
    public get calculatedTourStepWidth() {
        return this.tourStepWidth - this.widthAdjustmentForScreenBound;
    }

    /**
     * Scroll to highlighted element and set tour step
     */
    public scrollToAndSetElement(): void {
        this.updateStepLocation();
        if (this.isTourOnScreen()) {
            return;
        }
        // Allow things to render to scroll to the correct location
        setTimeout(() => {
            let topPosition = 0;
            if (this.selectedElementRect && this.currentTourStep) {
                const scrollAdjustment = this.currentTourStep.scrollAdjustment ? this.currentTourStep.scrollAdjustment : 0;
                const positionAdjustment = this.isBottom()
                    ? -this.topOfPageAdjustment - scrollAdjustment + this.getStepScreenAdjustment()
                    : +this.selectedElementRect.height - window.innerHeight + scrollAdjustment - this.getStepScreenAdjustment();
                topPosition = window.scrollY + this.selectedElementRect.top + positionAdjustment;
            }
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
     * @return {boolean} if tour step is visible on screen
     */
    private isTourOnScreen(): boolean {
        if (!this.currentTourStep && !this.currentTourStep!.selector) {
            return false;
        }
        return this.tourStep && this.elementInViewport(document.querySelector(this.currentTourStep!.selector!)) && this.elementInViewport(this.tourStep.nativeElement);
    }

    /**
     * Define if HTMLElement is visible in current viewport
     * Modified from https://stackoverflow.com/questions/123999/how-to-tell-if-a-dom-element-is-visible-in-the-current-viewport
     * @param {element} HTMLElement
     * @return {boolean} if element is in viewport
     */
    private elementInViewport(element: HTMLElement | null): boolean {
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

        if (this.isBottom()) {
            return (
                top >= window.pageYOffset + this.topOfPageAdjustment + scrollAdjustment + this.getStepScreenAdjustment() && top + height <= window.pageYOffset + window.innerHeight
            );
        } else {
            return (
                top >= window.pageYOffset + this.topOfPageAdjustment - this.getStepScreenAdjustment() && top + height + scrollAdjustment <= window.pageYOffset + window.innerHeight
            );
        }
    }

    /**
     * Handle backdrop clicking event
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
     * Update tour step location by calling getBoundingClientRect()
     */
    public updateStepLocation(): void {
        this.selectedElementRect = null;
        if (this.currentTourStep && this.currentTourStep.selector) {
            const selectedElement = document.querySelector(this.currentTourStep.selector);
            if (selectedElement) {
                this.selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
            }
        }
    }

    /**
     * @return {boolean} if the current tour step orientation is bottom
     */
    private isBottom(): boolean {
        if (this.currentTourStep && this.currentTourStep.orientation) {
            return (
                this.currentTourStep.orientation === Orientation.Bottom ||
                this.currentTourStep.orientation === Orientation.BottomLeft ||
                this.currentTourStep.orientation === Orientation.BottomRight
            );
        }
        return false;
    }

    /**
     * @return {number} top position for current tour step
     */
    public get topPosition(): number {
        if (!this.selectedElementRect) {
            return 0;
        }
        if (this.isBottom()) {
            const paddingAdjustment = this.getHighlightPadding();
            return this.selectedElementRect.top + this.selectedElementRect.height + paddingAdjustment;
        }

        return this.selectedElementRect.top - this.getHighlightPadding();
    }

    /**
     * @return {number} left position of current tour step / highlighted element
     */
    private get calculatedLeftPosition(): number {
        if (this.selectedElementRect && this.currentTourStep) {
            const paddingAdjustment = this.getHighlightPadding();

            if (this.currentTourStep.orientation === Orientation.TopRight || this.currentTourStep.orientation === Orientation.BottomRight) {
                return this.selectedElementRect.right - this.tourStepWidth;
            }

            if (this.currentTourStep.orientation === Orientation.TopLeft || this.currentTourStep.orientation === Orientation.BottomLeft) {
                return this.selectedElementRect.left;
            }

            if (this.currentTourStep.orientation === Orientation.Left) {
                return this.selectedElementRect.left - this.tourStepWidth - paddingAdjustment;
            }

            if (this.currentTourStep.orientation === Orientation.Right) {
                return this.selectedElementRect.left + this.selectedElementRect.width + paddingAdjustment;
            }

            return this.selectedElementRect.right - this.selectedElementRect.width / 2 - this.tourStepWidth / 2;
        }
        return 0;
    }

    /**
     * @return {number} left position for current tour step
     */
    public get leftPosition(): number {
        if (this.calculatedLeftPosition === 0) {
            return 5;
        }
        if (this.calculatedLeftPosition > 0) {
            return this.calculatedLeftPosition;
        }
        const adjustment = Math.max(0, -this.calculatedLeftPosition);
        const maxAdjustment = Math.min(this.maxWidthAdjustmentForTourStep, adjustment);
        return this.calculatedLeftPosition + maxAdjustment;
    }

    /**
     * Transform position of tour steps which are shown on top of the highlighted element
     * @return {string} '' or 'translateY(-100%)'
     */
    public get transform(): string {
        if (this.currentTourStep) {
            if (
                !this.currentTourStep.orientation ||
                this.currentTourStep.orientation === Orientation.Top ||
                this.currentTourStep.orientation === Orientation.TopRight ||
                this.currentTourStep.orientation === Orientation.TopLeft
            ) {
                return 'translateY(-100%)';
            }
        }
        return '';
    }

    /**
     * @return {number} overlay top position for highlighted element
     */
    public get overlayTop(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.top - this.getHighlightPadding();
        }
        return 0;
    }

    /**
     * Get overlay left position for highlighted element
     * @return {number} overlay left position
     */
    public get overlayLeft(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.left - this.getHighlightPadding();
        }
        return 0;
    }

    /**
     * Get overlay height for highlighted element
     * @return {number} overlay height
     */
    public get overlayHeight(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.height + this.getHighlightPadding() * 2;
        }
        return 0;
    }

    /**
     * Get overlay width for highlighted element
     * @return {number} overlay width
     */
    public get overlayWidth(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.width + this.getHighlightPadding() * 2;
        }
        return 0;
    }

    /**
     *  Gets defined padding around tour highlighting in pixels
     *  @return {number} highlight padding
     */
    private getHighlightPadding(): number {
        if (!this.currentTourStep) {
            return 0;
        }
        let paddingAdjustment = this.currentTourStep.useHighlightPadding ? this.highlightPadding : 0;
        if (this.currentTourStep.highlightPadding) {
            paddingAdjustment = this.currentTourStep.highlightPadding;
        }
        return paddingAdjustment;
    }

    /**
     * Calculate a value to add or subtract so the step should not be off screen.
     * @return {number} step screen adjustment
     */
    private getStepScreenAdjustment(): number {
        if (!this.currentTourStep) {
            return 0;
        }
        if (this.currentTourStep.orientation === Orientation.Left || this.currentTourStep.orientation === Orientation.Right) {
            return 0;
        }
        if (this.selectedElementRect && this.tourStep) {
            const elementHeight =
                this.selectedElementRect.height +
                (this.currentTourStep.scrollAdjustment ? this.currentTourStep.scrollAdjustment : 0) +
                this.tourStep.nativeElement.getBoundingClientRect().height;

            if (window.innerHeight - this.topOfPageAdjustment < elementHeight) {
                return elementHeight - (window.innerHeight - this.topOfPageAdjustment);
            }
        }
        return 0;
    }
}
