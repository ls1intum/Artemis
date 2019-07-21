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
    public isOrbShowing = false;

    private resizeSubscription: Subscription;
    private scrollSubscription: Subscription;

    readonly IMAGE = ContentType.IMAGE;
    readonly TEXT = ContentType.TEXT;
    readonly VIDEO = ContentType.VIDEO;

    readonly LINK = LinkType.LINK;
    readonly BUTTON = LinkType.BUTTON;

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
        this.guidedTourService.guidedTourCurrentStepStream.subscribe((step: TourStep) => {
            this.currentTourStep = step;
            if (step) {
                let hasPermission = true;
                if (step.permission) {
                    hasPermission = this.accountService.hasAnyAuthorityDirect(step.permission);
                }
                if (step.selector && hasPermission) {
                    const selectedElement = document.querySelector(step.selector);
                    if (selectedElement) {
                        this.scrollToAndSetElement();
                    } else {
                        this.selectedElementRect = null;
                    }
                } else {
                    this.selectedElementRect = null;
                }
            }
        });

        this.guidedTourService.guidedTourOrbShowingStream.subscribe((value: boolean) => {
            this.isOrbShowing = value;
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
     */
    private get maxWidthAdjustmentForTourStep(): number {
        return this.tourStepWidth - this.minimalTourStepWidth;
    }

    /**
     * Calculate width adjustment for screen bound
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
     */
    public get calculatedTourStepWidth() {
        return this.tourStepWidth - this.widthAdjustmentForScreenBound;
    }

    /**
     * Scroll to highlighted element and set tour step
     */
    public scrollToAndSetElement(): void {
        this.updateStepLocation();
        // Allow things to render to scroll to the correct location
        setTimeout(() => {
            if (!this.isOrbShowing && !this.isTourOnScreen()) {
                if (this.selectedElementRect && this.isBottom()) {
                    // Scroll so the element is on the top of the screen.
                    const topPos = this.currentTourStep
                        ? window.scrollY +
                          this.selectedElementRect.top -
                          this.topOfPageAdjustment -
                          (this.currentTourStep.scrollAdjustment ? this.currentTourStep.scrollAdjustment : 0) +
                          this.getStepScreenAdjustment()
                        : 0;
                    try {
                        window.scrollTo({
                            left: 0,
                            top: topPos,
                            behavior: 'smooth',
                        });
                    } catch (err) {
                        if (err instanceof TypeError) {
                            window.scroll(0, topPos);
                        } else {
                            throw err;
                        }
                    }
                } else {
                    // Scroll so the element is on the bottom of the screen.
                    const topPos =
                        this.selectedElementRect && this.currentTourStep
                            ? window.scrollY +
                              this.selectedElementRect.top +
                              this.selectedElementRect.height -
                              window.innerHeight +
                              (this.currentTourStep.scrollAdjustment ? this.currentTourStep.scrollAdjustment : 0) -
                              this.getStepScreenAdjustment()
                            : 0;
                    try {
                        window.scrollTo({
                            left: 0,
                            top: topPos,
                            behavior: 'smooth',
                        });
                    } catch (err) {
                        if (err instanceof TypeError) {
                            window.scroll(0, topPos);
                        } else {
                            throw err;
                        }
                    }
                }
            }
        });
    }

    /**
     * Handle orb event
     */
    public handleOrb(): void {
        this.guidedTourService.activateOrb();
        if (this.currentTourStep && this.currentTourStep.selector) {
            this.scrollToAndSetElement();
        }
    }

    /**
     * Return if tour step is visible on screen
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
     * @param element
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
     * @param event
     */
    public backdropClick(event: Event): void {
        if (this.guidedTourService.preventBackdropFromAdvancing) {
            event.stopPropagation();
        } else {
            this.guidedTourService.nextStep();
        }
    }

    /**
     * Update tour step location
     */
    public updateStepLocation(): void {
        if (this.currentTourStep && this.currentTourStep.selector) {
            const selectedElement = document.querySelector(this.currentTourStep.selector);
            if (selectedElement) {
                this.selectedElementRect = selectedElement.getBoundingClientRect() as DOMRect;
            } else {
                this.selectedElementRect = null;
            }
        } else {
            this.selectedElementRect = null;
        }
    }

    /**
     * Return true if the current tour step orientation is bottom
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
     * Get top position for current tour step
     */
    public get topPosition(): number {
        if (!this.selectedElementRect) {
            return 0;
        }
        const paddingAdjustment = this.getHighlightPadding();
        if (this.isBottom()) {
            return this.selectedElementRect.top + this.selectedElementRect.height + paddingAdjustment;
        }

        return this.selectedElementRect.top - this.getHighlightPadding();
    }

    /**
     * Get orb top position
     */
    public get orbTopPosition(): number {
        if (this.selectedElementRect && this.currentTourStep) {
            if (this.isBottom()) {
                return this.selectedElementRect.top + this.selectedElementRect.height;
            }

            if (this.currentTourStep.orientation === Orientation.Right || this.currentTourStep.orientation === Orientation.Left) {
                return this.selectedElementRect.top + this.selectedElementRect.height / 2;
            }
            return this.selectedElementRect.top;
        }
        return 0;
    }

    /**
     * Calculate left position of current tour step / highlighted element
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
     * Get left position for current tour step
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
     * Get orb left position
     */
    public get orbLeftPosition(): number {
        if (this.selectedElementRect && this.currentTourStep) {
            if (this.currentTourStep.orientation === Orientation.TopRight || this.currentTourStep.orientation === Orientation.BottomRight) {
                return this.selectedElementRect.right;
            }

            if (this.currentTourStep.orientation === Orientation.TopLeft || this.currentTourStep.orientation === Orientation.BottomLeft) {
                return this.selectedElementRect.left;
            }

            if (this.currentTourStep.orientation === Orientation.Left) {
                return this.selectedElementRect.left;
            }

            if (this.currentTourStep.orientation === Orientation.Right) {
                return this.selectedElementRect.left + this.selectedElementRect.width;
            }

            return this.selectedElementRect.right - this.selectedElementRect.width / 2;
        }
        return 0;
    }

    /**
     * Transform position of tour steps which are shown on top of the highlighted element
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
     * Transform orb position according to tour step orientation
     */
    public get orbTransform(): string {
        if (this.currentTourStep) {
            if (
                !this.currentTourStep.orientation ||
                this.currentTourStep.orientation === Orientation.Top ||
                this.currentTourStep.orientation === Orientation.Bottom ||
                this.currentTourStep.orientation === Orientation.TopLeft ||
                this.currentTourStep.orientation === Orientation.BottomLeft
            ) {
                return 'translateY(-50%)';
            }

            if (this.currentTourStep.orientation === Orientation.TopRight || this.currentTourStep.orientation === Orientation.BottomRight) {
                return 'translate(-100%, -50%)';
            }

            if (this.currentTourStep.orientation === Orientation.Right || this.currentTourStep.orientation === Orientation.Left) {
                return 'translate(-50%, -50%)';
            }
        }
        return '';
    }

    /**
     * Get overlay top position for highlighted element
     */
    public get overlayTop(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.top - this.getHighlightPadding();
        }
        return 0;
    }

    /**
     * Get overlay left position for highlighted element
     */
    public get overlayLeft(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.left - this.getHighlightPadding();
        }
        return 0;
    }

    /**
     * Get overlay height for highlighted element
     */
    public get overlayHeight(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.height + this.getHighlightPadding() * 2;
        }
        return 0;
    }

    /**
     * Get overlay width for highlighted element
     */
    public get overlayWidth(): number {
        if (this.selectedElementRect) {
            return this.selectedElementRect.width + this.getHighlightPadding() * 2;
        }
        return 0;
    }

    /* Gets defined padding around tour highlighting in pixels */
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
     */
    private getStepScreenAdjustment(): number {
        if (!this.currentTourStep) {
            return 0;
        }
        if (this.currentTourStep.orientation === Orientation.Left || this.currentTourStep.orientation === Orientation.Right) {
            return 0;
        }
        if (this.selectedElementRect) {
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
