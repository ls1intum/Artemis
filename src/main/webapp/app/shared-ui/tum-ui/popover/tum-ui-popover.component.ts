import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, TemplateRef, ViewContainerRef, inject, input, output, signal, viewChild } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TumUiOverlayPlacement, TumUiOverlayService } from 'app/shared-ui/tum-ui/overlay/tum-ui-overlay.service';

/**
 * Owned Artemis popover on Angular CDK overlay, part of the tum-aet-ui kit.
 *
 * The medium "showcase" component of the experiment: a content-projected panel opened via
 * {@link TumUiPopoverTriggerDirective}. Rides the shared overlay substrate for collision-aware
 * positioning, closes on backdrop click + Escape, and traps/restores focus (cdkTrapFocus). Renders
 * nothing inline; its projected content is captured in an ng-template and portaled on open.
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
    /** Accessible name announced for the role="dialog" panel. Strongly recommended for screen readers. */
    readonly ariaLabel = input<string>();
    readonly openChange = output<boolean>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;
    readonly isOpen = signal(false);

    open(origin: ElementRef<HTMLElement> | HTMLElement): void {
        if (this.isOpen()) {
            return;
        }
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, this.placement(), true);
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.isOpen.set(true);
        this.openChange.emit(true);
    }

    close(): void {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
        this.openChange.emit(false);
    }

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
