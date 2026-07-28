import { ElementRef, Injectable, inject } from '@angular/core';
import { ConnectedPosition, FlexibleConnectedPositionStrategy, Overlay, OverlayRef } from '@angular/cdk/overlay';

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

    positionStrategy(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement): FlexibleConnectedPositionStrategy {
        return this.overlay.position().flexibleConnectedTo(origin).withPositions(POSITIONS[placement]).withFlexibleDimensions(false).withPush(true);
    }

    placementFromPosition(pos: ConnectedPosition): TumUiOverlayPlacement {
        if (pos.overlayY === 'center') {
            return pos.overlayX === 'end' ? 'left' : 'right';
        }
        return pos.overlayY === 'bottom' ? 'top' : 'bottom';
    }

    createConnectedOverlay(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement, options: { hasBackdrop?: boolean } = {}): OverlayRef {
        return this.overlay.create({
            positionStrategy: this.positionStrategy(origin, placement),
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
            hasBackdrop: options.hasBackdrop ?? false,
            backdropClass: 'cdk-overlay-transparent-backdrop',
        });
    }
}
