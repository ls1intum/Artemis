import { ElementRef, Injectable, inject } from '@angular/core';
import { ConnectedPosition, FlexibleConnectedPositionStrategy, Overlay, OverlayRef } from '@angular/cdk/overlay';

/**
 * Shared Angular CDK overlay substrate for the tum-aet-ui kit.
 *
 * This is the reusable "hard part" every anchored overlay (tooltip, popover, and later menu/select)
 * builds on: connected positioning with flip fallbacks + reposition-on-scroll. Generalized from the
 * ad-hoc `createPanelOverlay` in app/tutorialgroup/shared/util/search-input-overlay.ts so all panels
 * share one positioning contract.
 */
export type TumUiOverlayPlacement = 'top' | 'bottom' | 'left' | 'right';

const OFFSET = 8;

// Each placement lists its preferred position first, then the flipped fallback CDK uses on collision.
const POSITIONS: Record<TumUiOverlayPlacement, ConnectedPosition[]> = {
    top: [
        { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -OFFSET },
        { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: OFFSET },
    ],
    bottom: [
        { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: OFFSET },
        { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -OFFSET },
    ],
    left: [
        { originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center', offsetX: -OFFSET },
        { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: OFFSET },
    ],
    right: [
        { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: OFFSET },
        { originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center', offsetX: -OFFSET },
    ],
};

@Injectable({ providedIn: 'root' })
export class TumUiOverlayService {
    private readonly overlay = inject(Overlay);

    /**
     * Build a flexible connected position strategy anchored to `origin` for the given `placement`,
     * with a flipped fallback and `push` so it stays within the viewport.
     */
    positionStrategy(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement): FlexibleConnectedPositionStrategy {
        return this.overlay.position().flexibleConnectedTo(origin).withPositions(POSITIONS[placement]).withFlexibleDimensions(false).withPush(true);
    }

    /**
     * Create an overlay anchored to `origin` that repositions on scroll. The caller attaches a
     * portal and is responsible for disposing the returned ref.
     */
    createConnectedOverlay(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement, hasBackdrop = false): OverlayRef {
        return this.overlay.create({
            positionStrategy: this.positionStrategy(origin, placement),
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
            hasBackdrop,
            // The kit owns the backdrop's structural CSS (.tum-ui-overlay-backdrop in global.scss): Artemis
            // does not import the CDK overlay-prebuilt stylesheet, so the stock `cdk-overlay-transparent-backdrop`
            // class alone has no size and never intercepts outside clicks (backdropClick() would not fire).
            backdropClass: 'tum-ui-overlay-backdrop',
        });
    }
}
