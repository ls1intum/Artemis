import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TemplateRef, ViewContainerRef } from '@angular/core';

export function createPanelOverlay(
    overlay: Overlay,
    searchInput: HTMLInputElement | undefined,
    panelTemplate: TemplateRef<unknown> | undefined,
    viewContainerRef: ViewContainerRef,
): OverlayRef | undefined {
    if (!searchInput || !panelTemplate) {
        return undefined;
    }

    const positionStrategy = overlay
        .position()
        .flexibleConnectedTo(searchInput)
        .withPositions([
            {
                originX: 'start',
                originY: 'bottom',
                overlayX: 'start',
                overlayY: 'top',
            },
            {
                originX: 'start',
                originY: 'top',
                overlayX: 'start',
                overlayY: 'bottom',
            },
        ])
        .withFlexibleDimensions(false)
        .withPush(false);

    const overlayRef = overlay.create({
        positionStrategy,
        scrollStrategy: overlay.scrollStrategies.reposition(),
    });

    overlayRef.updateSize({
        width: searchInput.offsetWidth,
    });

    overlayRef.attach(new TemplatePortal(panelTemplate, viewContainerRef));

    return overlayRef;
}
