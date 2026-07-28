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
     * Derive the placement (which side of the origin the overlay actually sits on) from a resolved CDK
     * connection pair. Callers subscribe to {@link FlexibleConnectedPositionStrategy.positionChanges} and
     * use this to keep a directional caret/arrow pointing at the anchor after CDK flips near a viewport edge.
     */
    placementFromPosition(pos: ConnectedPosition): TumUiOverlayPlacement {
        if (pos.overlayY === 'center') {
            return pos.overlayX === 'end' ? 'left' : 'right';
        }
        return pos.overlayY === 'bottom' ? 'top' : 'bottom';
    }

    /**
     * Create an overlay anchored to `origin` that repositions on scroll. The caller attaches a
     * portal and is responsible for disposing the returned ref.
     *
     * @param options.hasBackdrop render a click-catching backdrop (needed for click-outside-to-close);
     *   defaults to `false` (e.g. a tooltip, which must not steal pointer events).
     */
    createConnectedOverlay(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement, options: { hasBackdrop?: boolean } = {}): OverlayRef {
        return this.overlay.create({
            positionStrategy: this.positionStrategy(origin, placement),
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
            hasBackdrop: options.hasBackdrop ?? false,
            backdropClass: 'cdk-overlay-transparent-backdrop',
        });
    }
}
