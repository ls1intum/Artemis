import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, TemplateRef, ViewContainerRef, inject, input, output, signal, viewChild } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Subscription } from 'rxjs';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { FlexibleConnectedPositionStrategy } from '@angular/cdk/overlay';
import { TumUiOverlayPlacement, TumUiOverlayService } from '../overlay/tum-ui-overlay.service';

/** Shorter than the way in: a closing only has to avoid snapping. */
const CLOSE_DURATION_MS = 100;

/**
 * Anchored panel for rich or interactive content, opened via {@link TumUiPopoverTriggerDirective}. Use the
 * tooltip instead for a short, non-interactive hint.
 *
 * Built on the shared overlay substrate, so it inherits collision-aware positioning with a flipped fallback.
 * Closes on backdrop click and Escape, and traps then restores focus. Renders nothing inline: the projected
 * content is captured in an `ng-template` and portaled on open.
 */
@Component({
    selector: 'tum-ui-popover',
    templateUrl: './tum-ui-popover.component.html',
    styleUrl: './tum-ui-popover.component.scss',
    imports: [A11yModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPopoverComponent implements OnDestroy {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly document = inject(DOCUMENT);

    readonly placement = input<TumUiOverlayPlacement>('bottom');
    /** Accessible name announced for the role="dialog" panel. Required: a dialog must have a name. */
    readonly ariaLabel = input.required<string>();
    readonly openChange = output<boolean>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;
    private positionSub?: Subscription;
    private readonly openState = signal(false);
    /**
     * Where the panel ended up, which is not always where it was asked to go: CDK flips it when the preferred side
     * has no room. The opening animation grows the panel from the edge nearest its origin, so it has to follow.
     */
    protected readonly appliedPlacement = signal<TumUiOverlayPlacement>('bottom');
    /** Whether the popover is currently open. Read-only: drive it through open() / close() / toggle(). */
    readonly isOpen = this.openState.asReadonly();

    /** Open the popover anchored to `origin`. No-op if already open. */
    open(origin: ElementRef<HTMLElement> | HTMLElement): void {
        if (this.isOpen()) {
            return;
        }
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, this.placement(), { hasBackdrop: true });
        this.appliedPlacement.set(this.placement());
        // CDK may emit the flipped position synchronously while attaching, so this is subscribed before the portal.
        const strategy = this.overlayRef.getConfig().positionStrategy as FlexibleConnectedPositionStrategy;
        this.positionSub = strategy.positionChanges.subscribe((change) => this.appliedPlacement.set(this.overlayService.placementFromPosition(change.connectionPair)));
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.openState.set(true);
        this.openChange.emit(true);
    }

    /**
     * Close the popover and dispose its overlay. No-op if already closed.
     *
     * Closed straight away, though the panel fades out first: callers drive their own state off `isOpen`/`openChange`.
     */
    close(): void {
        if (!this.isOpen()) {
            return;
        }
        this.positionSub?.unsubscribe();
        this.positionSub = undefined;
        const closing = this.overlayRef;
        this.overlayRef = undefined;
        this.openState.set(false);
        this.openChange.emit(false);
        this.fadeOutAndDispose(closing);
    }

    /** Fades the panel out, then disposes. Disposes at once under reduced motion or without the animation API. */
    private fadeOutAndDispose(closing?: OverlayRef): void {
        if (!closing) {
            return;
        }
        const panel = closing.overlayElement?.querySelector<HTMLElement>('.tum-ui-popover-panel');
        const view = this.document.defaultView;
        const reducedMotion = typeof view?.matchMedia === 'function' && view.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (!panel || reducedMotion || typeof panel.animate !== 'function') {
            closing.dispose();
            return;
        }
        const animation = panel.animate([{ opacity: 1 }, { opacity: 0 }], { duration: CLOSE_DURATION_MS, easing: 'ease-in', fill: 'forwards' });
        // Both arms: a cancelled fade must not leak the overlay.
        animation.finished.then(
            () => closing.dispose(),
            () => closing.dispose(),
        );
    }

    /** Open the popover if closed, or close it if open. */
    toggle(origin: ElementRef<HTMLElement> | HTMLElement): void {
        if (this.isOpen()) {
            this.close();
        } else {
            this.open(origin);
        }
    }

    ngOnDestroy(): void {
        // Straight out, with no fade: there is no view left to play it in.
        this.positionSub?.unsubscribe();
        this.overlayRef?.dispose();
    }
}
