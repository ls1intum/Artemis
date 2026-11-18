import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, TemplateRef, ViewContainerRef, inject, input, output, signal, viewChild } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Subscription } from 'rxjs';
import { A11yModule, InteractivityChecker } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { FlexibleConnectedPositionStrategy } from '@angular/cdk/overlay';
import { TumAetUiOverlayAlignment, TumAetUiOverlayPlacement, TumAetUiOverlayService } from '../overlay/tumaet-ui-overlay.service';

/** Shorter than the way in: a closing only has to avoid snapping. */
const CLOSE_DURATION_MS = 100;

/**
 * How long a hover popover stays up after the pointer or the focus left it: long enough to cross the gap between
 * the trigger and the panel, short enough not to linger.
 */
const HOVER_CLOSE_DELAY_MS = 200;

export type TumAetUiPopoverPaddingOption = 'default' | 'none';

/** What opens a popover through its trigger: a click, or hovering or focusing the trigger. */
export type TumAetUiPopoverOpenOn = 'click' | 'hover';

/**
 * Anchored panel for rich or interactive content, opened via {@link TumAetUiPopoverTriggerDirective}. Use the
 * tooltip instead for a short, non-interactive hint.
 *
 * Built on the shared overlay substrate, so it inherits collision-aware positioning with a flipped fallback.
 * Renders nothing inline: the projected content is captured in an `ng-template` and portaled on open.
 *
 * With `openOn="click"` (the default) the popover is modal: it closes on backdrop click and Escape, and traps then
 * restores focus.
 *
 * With `openOn="hover"` it is a non-modal companion of its trigger, for a few secondary controls next to an element
 * that keeps its own click action. It opens when the pointer enters the trigger or the trigger receives focus, and
 * stays open while the pointer or the focus is on the trigger or the panel. It closes shortly after both left, on
 * Escape and on a click anywhere else. Opened by touch, it stays until one of the latter two. There is no backdrop and no focus trap: Tab moves from the trigger into the
 * panel, and Tab at the end of the panel continues with the element after the trigger. A touch device has no hover,
 * so there the popover opens once the trigger is tapped and thereby focused; make sure the trigger is focusable.
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
    private readonly interactivityChecker = inject(InteractivityChecker);

    readonly placement = input<TumAetUiOverlayPlacement>('bottom');
    /** Lines a popover above or below its trigger up with the trigger's centre, start edge or end edge. */
    readonly alignment = input<TumAetUiOverlayAlignment>('center');
    readonly padding = input<TumAetUiPopoverPaddingOption>('default');
    /** What opens the popover through {@link TumAetUiPopoverTriggerDirective}; see the class description. */
    readonly openOn = input<TumAetUiPopoverOpenOn>('click');
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
    private originElement?: HTMLElement;
    private hoverCloseTimer?: ReturnType<typeof setTimeout>;
    private pointerOverPanel = false;
    private pointerOverTrigger = false;
    /** Set once a tap on the trigger opened or kept a hover popover: it then waits for an explicit dismissal. */
    private keptUntilDismissed = false;
    /** Set while this popover hands focus back to its trigger, which must not count as focusing it anew. */
    private returningFocus = false;
    /** Whether the open popover blocks the page behind it. Fixed while open, so the panel does not change under the user. */
    protected readonly isModal = signal(true);
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
        const modal = this.openOn() === 'click';
        this.isModal.set(modal);
        this.originElement = origin instanceof ElementRef ? origin.nativeElement : origin;
        this.pointerOverPanel = false;
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, this.placement(), { hasBackdrop: modal, alignment: this.alignment() });
        this.appliedPlacement.set(this.placement());
        // CDK may emit the flipped position synchronously while attaching, so this is subscribed before the portal.
        const strategy = this.overlayRef.getConfig().positionStrategy as FlexibleConnectedPositionStrategy;
        this.positionSub = strategy.positionChanges.subscribe((change) => this.appliedPlacement.set(this.overlayService.placementFromPosition(change.connectionPair)));
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                if (!modal && this.panelElement()?.contains(this.document.activeElement)) {
                    // Without a focus trap nothing hands the focus back, and it must not vanish with the panel.
                    this.focusOrigin();
                }
                this.close();
            }
        });
        if (!modal) {
            // There is no backdrop to catch a click elsewhere. A click on the trigger belongs to the trigger.
            this.overlayRef.outsidePointerEvents().subscribe((event) => {
                if (event.type === 'click' && !(event.target instanceof Node && this.originElement?.contains(event.target))) {
                    this.close();
                }
            });
        }
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
        this.cancelHoverClose();
        // Nothing reports a pointer that leaves a trigger which is removed or covered meanwhile, so do not carry it over.
        this.pointerOverTrigger = false;
        this.keptUntilDismissed = false;
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

    /**
     * @internal The pointer entered the trigger of a hover popover, or the trigger received focus.
     * @param keepUntilDismissed set for a tap, which cannot hover: the popover then ignores the pointer and the
     *     focus leaving, and closes on a tap elsewhere, on Escape or when Tab leaves the panel.
     */
    openFromTrigger(origin: ElementRef<HTMLElement>, keepUntilDismissed = false): void {
        if (this.returningFocus) {
            return;
        }
        this.cancelHoverClose();
        // Before opening: an `openChange` listener may close the popover at once, and only closing clears the flag.
        this.keptUntilDismissed ||= keepUntilDismissed;
        this.open(origin);
    }

    /** @internal The pointer entered or left the trigger of a hover popover. */
    setPointerOverTrigger(over: boolean): void {
        this.pointerOverTrigger = over;
    }

    /**
     * @internal The pointer or the focus left the trigger or the panel of a hover popover. It closes after a short
     * delay, unless the pointer or the keyboard focus is on one of the two by then; whichever leaves last asks again.
     */
    scheduleHoverClose(): void {
        this.cancelHoverClose();
        if (this.isOpen() && !this.keptUntilDismissed) {
            this.hoverCloseTimer = setTimeout(() => {
                if (!this.pointerOverTrigger && !this.pointerOverPanel && !this.triggerHoldsKeyboardFocus() && !this.panelHoldsFocus()) {
                    this.close();
                }
            }, HOVER_CLOSE_DELAY_MS);
        }
    }

    /**
     * @internal Moves focus from the trigger of an open hover popover to the first control of the panel.
     * @returns whether focus moved; `false` when the panel has nothing to tab to, so Tab keeps its meaning.
     */
    focusFirstControl(): boolean {
        const first = this.tabbableElements()[0];
        first?.focus();
        return !!first;
    }

    protected onPanelPointerEnter(): void {
        this.pointerOverPanel = true;
        this.cancelHoverClose();
    }

    protected onPanelPointerLeave(): void {
        this.pointerOverPanel = false;
        if (!this.isModal()) {
            this.scheduleHoverClose();
        }
    }

    protected onPanelFocusIn(): void {
        this.cancelHoverClose();
    }

    protected onPanelFocusOut(): void {
        if (!this.isModal()) {
            this.scheduleHoverClose();
        }
    }

    protected onPanelKeydown(event: KeyboardEvent): void {
        if (this.isModal() || event.key !== 'Tab' || event.altKey || event.ctrlKey || event.metaKey) {
            return;
        }
        // The overlay sits at the end of the document, so tabbing out of the panel would leave the page. Focus goes
        // back to the trigger instead: Shift+Tab stops there, Tab continues from it to the element that follows.
        const tabbable = this.tabbableElements();
        const edge = event.shiftKey ? tabbable[0] : tabbable[tabbable.length - 1];
        if (this.document.activeElement === edge) {
            this.focusOrigin();
            if (event.shiftKey) {
                event.preventDefault();
            }
            this.close();
        }
    }

    private panelElement(): HTMLElement | null {
        return this.overlayRef?.overlayElement.querySelector<HTMLElement>('.tumaet-ui-popover-panel') ?? null;
    }

    private tabbableElements(): HTMLElement[] {
        const candidates = Array.from(this.panelElement()?.querySelectorAll<HTMLElement>('*') ?? []);
        // Visibility is asked of the browser: the CDK check needs layout, which a test environment does not provide.
        return candidates.filter(
            (element) =>
                this.interactivityChecker.isFocusable(element, { ignoreVisibility: true }) &&
                this.interactivityChecker.isTabbable(element) &&
                (typeof element.checkVisibility !== 'function' || element.checkVisibility({ visibilityProperty: true })),
        );
    }

    private focusOrigin(): void {
        this.returningFocus = true;
        this.originElement?.focus();
        this.returningFocus = false;
    }

    /**
     * Whether the trigger is focused from the keyboard. A trigger that was clicked keeps the focus as well, but its
     * popover must still go once the pointer has left, so only a focus the browser shows a focus ring for counts.
     */
    private triggerHoldsKeyboardFocus(): boolean {
        const active = this.document.activeElement;
        if (!active || !this.originElement?.contains(active)) {
            return false;
        }
        try {
            return active.matches(':focus-visible');
        } catch {
            // An environment without the selector cannot tell the two apart; keeping the popover is the safe side.
            return true;
        }
    }

    private panelHoldsFocus(): boolean {
        return !!this.panelElement()?.contains(this.document.activeElement);
    }

    private cancelHoverClose(): void {
        clearTimeout(this.hoverCloseTimer);
        this.hoverCloseTimer = undefined;
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.cancelHoverClose();
        // Straight out, with no fade: there is no view left to play it in.
        this.positionSub?.unsubscribe();
        this.overlayRef?.dispose();
    }
}
