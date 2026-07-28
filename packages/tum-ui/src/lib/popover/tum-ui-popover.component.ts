import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, TemplateRef, ViewContainerRef, inject, input, output, signal, viewChild } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TumUiOverlayPlacement, TumUiOverlayService } from '../overlay/tum-ui-overlay.service';

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

    readonly ariaLabel = input.required<string>();
    readonly openChange = output<boolean>();

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;
    private readonly openState = signal(false);

    readonly isOpen = this.openState.asReadonly();

    open(origin: ElementRef<HTMLElement> | HTMLElement): void {
        if (this.isOpen()) {
            return;
        }
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

    close(): void {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.openState.set(false);
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
