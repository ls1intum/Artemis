import { ComponentRef, Directive, ElementRef, OnDestroy, booleanAttribute, computed, effect, inject, input, numberAttribute } from '@angular/core';
import { ComponentPortal } from '@angular/cdk/portal';
import { FlexibleConnectedPositionStrategy, OverlayRef } from '@angular/cdk/overlay';
import { Subscription, fromEvent } from 'rxjs';
import { TumUiOverlayPlacement, TumUiOverlayService } from '../overlay/tum-ui-overlay.service';
import { TumUiTooltipContentComponent } from './tum-ui-tooltip-content.component';

let nextTooltipId = 0;

/**
 * How close the arrow may get to a corner of the bubble. The arrow is 8px square and rotated, so it needs about six
 * pixels of clearance, and the bubble's own rounding needs a little more.
 */
const ARROW_EDGE_INSET_PX = 12;

/** Tooltip shown on hover or focus and associated with its host through `aria-describedby`. */
@Directive({
    selector: '[tumUiTooltip]',
    host: {
        '(mouseenter)': 'onHoverStart()',
        '(mouseleave)': 'onHoverEnd()',
        '(focusin)': 'onFocusStart()',
        '(focusout)': 'onFocusEnd()',
        '(keydown.escape)': 'hideNow()',
    },
})
export class TumUiTooltipDirective implements OnDestroy {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

    /** A plain hint, or several items to render as a bulleted list. */
    readonly content = input.required<string | readonly string[]>({ alias: 'tumUiTooltip' });
    readonly placement = input<TumUiOverlayPlacement>('top', { alias: 'tumUiTooltipPlacement' });
    /**
     * Whether the tooltip adds itself to the host's `aria-describedby` while open. Turn this off on a host that is
     * already described by permanent markup carrying the same text, so the description is not announced twice.
     */
    readonly describesHost = input(true, { alias: 'tumUiTooltipDescribesHost', transform: booleanAttribute });
    readonly showDelayMs = input(150, { transform: numberAttribute });
    readonly hideDelayMs = input(100, { transform: numberAttribute });

    private readonly text = computed(() => (Array.isArray(this.content()) ? '' : (this.content() as string)));
    private readonly items = computed(() => (Array.isArray(this.content()) ? (this.content() as readonly string[]) : []));
    // An empty array is truthy, so emptiness has to be asked of the normalised forms rather than of the input.
    private readonly isEmpty = computed(() => !this.text() && this.items().length === 0);

    private overlayRef?: OverlayRef;
    private contentRef?: ComponentRef<TumUiTooltipContentComponent>;
    private positionSub?: Subscription;
    private showTimer?: ReturnType<typeof setTimeout>;
    private hideTimer?: ReturnType<typeof setTimeout>;
    private readonly tooltipId = `tum-ui-tooltip-${nextTooltipId++}`;
    private interactionSub?: Subscription;

    private appliedPlacement: TumUiOverlayPlacement = 'top';
    private triggerHovered = false;
    private tooltipHovered = false;
    private focused = false;

    constructor() {
        effect(() => {
            // Read content unconditionally so changes remain tracked while the tooltip is hidden.
            const [text, items, isEmpty] = [this.text(), this.items(), this.isEmpty()];
            if (isEmpty) {
                this.hideNow();
            } else {
                this.contentRef?.setInput('text', text);
                this.contentRef?.setInput('items', items);
            }
        });
    }

    protected onHoverStart(): void {
        this.triggerHovered = true;
        this.scheduleShow();
    }

    protected onHoverEnd(): void {
        this.triggerHovered = false;
        this.scheduleHideIfInactive();
    }

    protected onFocusStart(): void {
        this.focused = true;
        this.scheduleShow();
    }

    protected onFocusEnd(): void {
        this.focused = false;
        this.scheduleHideIfInactive();
    }
    private scheduleHideIfInactive(): void {
        if (this.triggerHovered || this.tooltipHovered || this.focused) {
            return;
        }
        this.scheduleHide();
    }

    private scheduleShow(): void {
        clearTimeout(this.hideTimer);
        clearTimeout(this.showTimer);
        if (this.overlayRef?.hasAttached() || this.isEmpty()) {
            return;
        }
        this.showTimer = setTimeout(() => this.show(), this.showDelayMs());
    }

    private scheduleHide(): void {
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.hideTimer = setTimeout(() => this.hideNow(), this.hideDelayMs());
    }

    protected hideNow(): void {
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.removeDescribedBy();
        this.positionSub?.unsubscribe();
        this.positionSub = undefined;
        this.interactionSub?.unsubscribe();
        this.interactionSub = undefined;
        this.tooltipHovered = false;
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.contentRef = undefined;
    }

    private show(): void {
        if (this.overlayRef?.hasAttached()) {
            return;
        }
        this.overlayRef = this.overlayService.createConnectedOverlay(this.elementRef, this.placement());
        const strategy = this.overlayRef.getConfig().positionStrategy as FlexibleConnectedPositionStrategy;
        this.appliedPlacement = this.placement();
        // CDK may emit the initial flipped position synchronously during attachment.
        this.positionSub = strategy.positionChanges.subscribe((change) => {
            this.appliedPlacement = this.overlayService.placementFromPosition(change.connectionPair);
            this.contentRef?.setInput('placement', this.appliedPlacement);
            this.updateArrowOffset();
        });
        this.contentRef = this.overlayRef.attach(new ComponentPortal(TumUiTooltipContentComponent));
        this.contentRef.setInput('text', this.text());
        this.contentRef.setInput('items', this.items());
        this.contentRef.setInput('id', this.tooltipId);
        this.contentRef.setInput('placement', this.appliedPlacement);
        this.updateArrowOffset();
        const contentElement = this.contentRef.location.nativeElement as HTMLElement;
        this.interactionSub = new Subscription();
        this.interactionSub.add(
            fromEvent(contentElement, 'mouseenter').subscribe(() => {
                this.tooltipHovered = true;
                clearTimeout(this.hideTimer);
            }),
        );
        this.interactionSub.add(
            fromEvent(contentElement, 'mouseleave').subscribe(() => {
                this.tooltipHovered = false;
                this.scheduleHideIfInactive();
            }),
        );
        this.interactionSub.add(
            this.overlayRef.keydownEvents().subscribe((event) => {
                if (event.key === 'Escape') {
                    this.hideNow();
                }
            }),
        );
        this.addDescribedBy();
    }

    /**
     * Points the arrow at the host rather than at the middle of the bubble.
     *
     * The overlay is created with `withPush`, so a bubble that would leave the viewport is shoved sideways: an arrow
     * centred on the bubble then points at empty space beside the host. Measure where the host sits along the bubble's
     * edge and report that instead, clamped so the arrow stays clear of the rounded corners.
     */
    private updateArrowOffset(): void {
        const bubble = this.contentRef?.location.nativeElement as HTMLElement | undefined;
        if (!bubble) {
            return;
        }
        const hostRect = this.elementRef.nativeElement.getBoundingClientRect();
        const bubbleRect = bubble.getBoundingClientRect();
        const horizontal = this.appliedPlacement === 'top' || this.appliedPlacement === 'bottom';
        const hostCentre = horizontal ? hostRect.left + hostRect.width / 2 : hostRect.top + hostRect.height / 2;
        const bubbleStart = horizontal ? bubbleRect.left : bubbleRect.top;
        const bubbleLength = horizontal ? bubbleRect.width : bubbleRect.height;
        // A zero-sized bubble means it has not been laid out yet; the next position change will measure it properly.
        if (bubbleLength === 0) {
            return;
        }
        const offset = Math.min(Math.max(hostCentre - bubbleStart, ARROW_EDGE_INSET_PX), bubbleLength - ARROW_EDGE_INSET_PX);
        this.contentRef?.setInput('arrowOffsetPx', offset);
    }

    private addDescribedBy(): void {
        if (!this.describesHost()) {
            return;
        }
        const host = this.elementRef.nativeElement;
        const tokens = (host.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean);
        if (!tokens.includes(this.tooltipId)) {
            tokens.push(this.tooltipId);
        }
        host.setAttribute('aria-describedby', tokens.join(' '));
    }
    private removeDescribedBy(): void {
        if (!this.describesHost()) {
            return;
        }
        const host = this.elementRef.nativeElement;
        const tokens = (host.getAttribute('aria-describedby') ?? '').split(' ').filter((token) => token && token !== this.tooltipId);
        if (tokens.length > 0) {
            host.setAttribute('aria-describedby', tokens.join(' '));
        } else {
            host.removeAttribute('aria-describedby');
        }
    }

    ngOnDestroy(): void {
        this.hideNow();
    }
}
