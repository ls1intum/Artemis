import { ComponentRef, Directive, ElementRef, OnDestroy, effect, inject, input } from '@angular/core';
import { ComponentPortal } from '@angular/cdk/portal';
import { FlexibleConnectedPositionStrategy, OverlayRef } from '@angular/cdk/overlay';
import { Subscription } from 'rxjs';
import { TumUiOverlayPlacement, TumUiOverlayService } from '../overlay/tum-ui-overlay.service';
import { TumUiTooltipContentComponent } from './tum-ui-tooltip-content.component';

let nextTooltipId = 0;

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
    private positionSub?: Subscription;
    private showTimer?: ReturnType<typeof setTimeout>;
    private hideTimer?: ReturnType<typeof setTimeout>;
    private readonly tooltipId = `tum-ui-tooltip-${nextTooltipId++}`;

    private hovered = false;
    private focused = false;

    constructor() {
        effect(() => {
            const text = this.content();
            if (!text) {
                this.hideNow();
            } else {
                this.contentRef?.setInput('text', text);
            }
        });
    }

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
    private scheduleHideIfInactive(): void {
        if (this.hovered || this.focused) {
            return;
        }
        this.scheduleHide();
    }

    private scheduleShow(): void {
        clearTimeout(this.hideTimer);
        clearTimeout(this.showTimer);
        if (this.overlayRef?.hasAttached() || !this.content()) {
            return;
        }
        this.showTimer = setTimeout(() => this.show(), this.showDelay());
    }

    private scheduleHide(): void {
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.hideTimer = setTimeout(() => this.hideNow(), this.hideDelay());
    }

    protected hideNow(): void {
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.removeDescribedBy();
        this.positionSub?.unsubscribe();
        this.positionSub = undefined;
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
        let appliedPlacement = this.placement();
        this.positionSub = strategy.positionChanges.subscribe((change) => {
            appliedPlacement = this.overlayService.placementFromPosition(change.connectionPair);
            this.contentRef?.setInput('placement', appliedPlacement);
        });
        this.contentRef = this.overlayRef.attach(new ComponentPortal(TumUiTooltipContentComponent));
        this.contentRef.setInput('text', this.content());
        this.contentRef.setInput('id', this.tooltipId);
        this.contentRef.setInput('placement', appliedPlacement);
        this.addDescribedBy();
    }
    private addDescribedBy(): void {
        const host = this.elementRef.nativeElement;
        const tokens = (host.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean);
        if (!tokens.includes(this.tooltipId)) {
            tokens.push(this.tooltipId);
        }
        host.setAttribute('aria-describedby', tokens.join(' '));
    }
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
