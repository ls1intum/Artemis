import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, TemplateRef, ViewContainerRef, inject, input, output, signal, viewChild } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TumUiOverlayPlacement, TumUiOverlayService } from '../overlay/tum-ui-overlay.service';

/**
 * Anchored panel for rich or interactive content, opened via {@link TumUiPopoverTriggerDirective}. Use the
 * tooltip instead for a short, non-interactive hint.
 *
 * Built on the shared overlay substrate, so it inherits collision-aware positioning with a flipped fallback.
 * Closes on backdrop click and Escape, and traps focus while open. Renders nothing inline: the projected content
 * is captured in an `ng-template` and portaled on open.
 *
 * Two accessibility decisions worth stating, because both are easy to get wrong in the same direction:
 *
 * - **Focus returns to whatever had it when the popover opened.** Disposing the overlay drops focus to `<body>`,
 *   which sends a keyboard user back to the top of the document — a popover opened from the last control on a
 *   page would cost them the whole page to get back.
 * - **It is not `aria-modal`.** The backdrop is transparent and the page behind it is not inert, so claiming the
 *   rest of the page is unavailable would be a lie a screen-reader user has no way to check.
 */
@Component({
    selector: 'tum-ui-popover',
    templateUrl: './tum-ui-popover.component.html',
    imports: [A11yModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiPopoverComponent implements OnDestroy {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);

    readonly placement = input<TumUiOverlayPlacement>('bottom');
    /** Accessible name announced for the role="dialog" panel. Required: a dialog must have a name. */
    readonly ariaLabel = input.required<string>();
    readonly openChange = output<boolean>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;
    private previouslyFocused?: HTMLElement;
    private readonly openState = signal(false);
    /** Whether the popover is currently open. Read-only: drive it through open() / close() / toggle(). */
    readonly isOpen = this.openState.asReadonly();

    /** Open the popover anchored to `origin`. No-op if already open. */
    open(origin: ElementRef<HTMLElement> | HTMLElement): void {
        if (this.isOpen()) {
            return;
        }
        // Captured before the overlay attaches and steals focus, so the element remembered is the one the user was
        // actually on rather than the panel itself.
        const active = document.activeElement;
        this.previouslyFocused = active instanceof HTMLElement ? active : undefined;
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, this.placement(), { hasBackdrop: true });
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

    /** Close the popover and dispose its overlay. No-op if already closed. */
    close(): void {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.openState.set(false);
        // Restored after disposal, because the panel still holds focus until the overlay is gone.
        this.previouslyFocused?.focus();
        this.previouslyFocused = undefined;
        this.openChange.emit(false);
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
        this.overlayRef?.dispose();
    }
}
