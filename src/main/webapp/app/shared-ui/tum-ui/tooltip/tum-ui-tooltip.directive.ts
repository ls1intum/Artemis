import { ComponentRef, Directive, ElementRef, OnDestroy, inject, input } from '@angular/core';
import { ComponentPortal } from '@angular/cdk/portal';
import { OverlayRef } from '@angular/cdk/overlay';
import { TumUiOverlayPlacement, TumUiOverlayService } from 'app/shared-ui/tum-ui/overlay/tum-ui-overlay.service';
import { TumUiTooltipContentComponent } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip-content.component';

let nextTooltipId = 0;

/**
 * Owned Artemis tooltip directive on Angular CDK overlay, part of the tum-aet-ui kit.
 *
 * Drop-in replacement for PrimeNG `pTooltip`: hover + focus triggers with show/hide delays, viewport
 * collision handling (via the shared overlay substrate), and `aria-describedby` wiring so the bubble
 * is announced. Delays default to the PrimeNG-like values so the swap feels identical.
 */
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

    readonly content = input.required<string>({ alias: 'tumUiTooltip' });
    readonly placement = input<TumUiOverlayPlacement>('top', { alias: 'tumUiTooltipPlacement' });
    readonly showDelay = input(150);
    readonly hideDelay = input(100);

    private overlayRef?: OverlayRef;
    private contentRef?: ComponentRef<TumUiTooltipContentComponent>;
    private showTimer?: ReturnType<typeof setTimeout>;
    private hideTimer?: ReturnType<typeof setTimeout>;
    private readonly tooltipId = `tum-ui-tooltip-${nextTooltipId++}`;

    // Hover and keyboard focus are tracked independently: the tooltip stays visible as long as either
    // trigger is active, and only hides once neither remains (e.g. mouse leaving a still-focused link
    // must not hide the tooltip). Without this, a single deactivation event would hide it prematurely.
    private hovered = false;
    private focused = false;

    protected onHoverStart(): void {
        this.hovered = true;
        this.scheduleShow();
    }

    protected onHoverEnd(): void {
        this.hovered = false;
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

    /** Schedule a hide only once neither hover nor keyboard focus keeps the tooltip alive. */
    private scheduleHideIfInactive(): void {
        if (this.hovered || this.focused) {
            return;
        }
        this.scheduleHide();
    }

    private scheduleShow(): void {
        // Clear BOTH timers: a second trigger (e.g. focusin after mouseenter) within the show delay
        // must not orphan the first pending show timer, or two overlays would be created and one leaked.
        clearTimeout(this.hideTimer);
        clearTimeout(this.showTimer);
        if (this.overlayRef?.hasAttached() || !this.content()) {
            return;
        }
        this.showTimer = setTimeout(() => this.show(), this.showDelay());
    }

    private scheduleHide(): void {
        // Clear BOTH timers (mirror of scheduleShow): a re-hover/focus that arrives during the hide
        // delay must not leave an orphaned hide timer that later hides a re-shown tooltip.
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.hideTimer = setTimeout(() => this.hideNow(), this.hideDelay());
    }

    protected hideNow(): void {
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.removeDescribedBy();
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.contentRef = undefined;
    }

    private show(): void {
        // Idempotency guard: a stale/duplicate timer must not create a second overlay.
        if (this.overlayRef?.hasAttached()) {
            return;
        }
        this.overlayRef = this.overlayService.createConnectedOverlay(this.elementRef, this.placement());
        this.contentRef = this.overlayRef.attach(new ComponentPortal(TumUiTooltipContentComponent));
        this.contentRef.setInput('text', this.content());
        this.contentRef.setInput('id', this.tooltipId);
        this.addDescribedBy();
    }

    /** Append our tooltip id to the host's aria-describedby, preserving any existing tokens. */
    private addDescribedBy(): void {
        const host = this.elementRef.nativeElement;
        const tokens = (host.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean);
        if (!tokens.includes(this.tooltipId)) {
            tokens.push(this.tooltipId);
        }
        host.setAttribute('aria-describedby', tokens.join(' '));
    }

    /** Remove only our tooltip id from the host's aria-describedby, leaving any other tokens intact. */
    private removeDescribedBy(): void {
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
