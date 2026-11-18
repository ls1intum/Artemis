import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, TemplateRef, ViewContainerRef, inject, input, output, signal, viewChild } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Subscription } from 'rxjs';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { FlexibleConnectedPositionStrategy } from '@angular/cdk/overlay';
import { TumAetUiOverlayPlacement, TumAetUiOverlayService } from '../overlay/tumaet-ui-overlay.service';

/** Shorter than the way in: a closing only has to avoid snapping. */
const CLOSE_DURATION_MS = 100;

export type TumAetUiPopoverPaddingOption = 'default' | 'none';

/**
 * Anchored panel for rich or interactive content, opened via {@link TumAetUiPopoverTriggerDirective}. Use the
 * tooltip instead for a short, non-interactive hint.
 *
 * Built on the shared overlay substrate, so it inherits collision-aware positioning with a flipped fallback.
 * Closes on backdrop click and Escape, and traps then restores focus. Renders nothing inline: the projected
 * content is captured in an `ng-template` and portaled on open.
 */
@Component({
    selector: 'tumaet-ui-popover',
    templateUrl: './tumaet-ui-popover.component.html',
    styleUrl: './tumaet-ui-popover.component.scss',
    imports: [A11yModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiPopoverComponent implements OnDestroy {
    private readonly overlayService = inject(TumAetUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly document = inject(DOCUMENT);

    readonly placement = input<TumAetUiOverlayPlacement>('bottom');
    readonly padding = input<TumAetUiPopoverPaddingOption>('default');
    /** Accessible name announced for the role="dialog" panel. Required: a dialog must have a name. */
    readonly ariaLabel = input.required<string>();
    readonly openChange = output<boolean>();
    /**
     * Emits once a closed popover is fully gone: its panel has faded out, been disposed and handed focus back to the
     * element that opened it. `openChange(false)` fires earlier, when closing starts. Wait for this event to move
     * focus elsewhere, for example into an editor that a selected item acts on, because the focus restore of the
     * panel would otherwise override it.
     */
    readonly closed = output<void>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;
    private positionSub?: Subscription;
    private readonly openState = signal(false);
    private destroyed = false;
    /**
     * Where the panel ended up, which is not always where it was asked to go: CDK flips it when the preferred side
     * has no room. The opening animation grows the panel from the edge nearest its origin, so it has to follow.
     */
    protected readonly appliedPlacement = signal<TumAetUiOverlayPlacement>('bottom');
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
        const panel = closing.overlayElement?.querySelector<HTMLElement>('.tumaet-ui-popover-panel');
        const view = this.document.defaultView;
        const reducedMotion = typeof view?.matchMedia === 'function' && view.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (!panel || reducedMotion || typeof panel.animate !== 'function') {
            this.disposeAndNotify(closing);
            return;
        }
        const animation = panel.animate([{ opacity: 1 }, { opacity: 0 }], { duration: CLOSE_DURATION_MS, easing: 'ease-in', fill: 'forwards' });
        // Both arms: a cancelled fade must not leak the overlay.
        animation.finished.then(
            () => this.disposeAndNotify(closing),
            () => this.disposeAndNotify(closing),
        );
    }

    private disposeAndNotify(closing: OverlayRef): void {
        closing.dispose();
        // The component may be gone when a fade finishes late; an output cannot emit after that.
        if (!this.destroyed) {
            this.closed.emit();
        }
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
        this.destroyed = true;
        // Straight out, with no fade: there is no view left to play it in.
        this.positionSub?.unsubscribe();
        this.overlayRef?.dispose();
    }
}
