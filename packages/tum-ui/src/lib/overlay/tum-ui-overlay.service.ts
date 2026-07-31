import { ElementRef, Injectable, inject } from '@angular/core';
import { ConnectedPosition, FlexibleConnectedPositionStrategy, Overlay, OverlayRef } from '@angular/cdk/overlay';
import { Directionality } from '@angular/cdk/bidi';

export type TumUiOverlayPlacement = 'top' | 'bottom' | 'left' | 'right';

interface TumUiConnectedOverlayOptions {
    hasBackdrop?: boolean;
    matchOriginWidth?: boolean;
}

const OFFSET = 8;

const VERTICAL_POSITIONS: Record<'top' | 'bottom', ConnectedPosition[]> = {
    top: [
        { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -OFFSET },
        { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: OFFSET },
    ],
    bottom: [
        { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: OFFSET },
        { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -OFFSET },
    ],
};

@Injectable({ providedIn: 'root' })
export class TumUiOverlayService {
    private readonly overlay = inject(Overlay);
    private readonly directionality = inject(Directionality);

    positionStrategy(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement): FlexibleConnectedPositionStrategy {
        const positions = placement === 'top' || placement === 'bottom' ? VERTICAL_POSITIONS[placement] : this.horizontalPositions(placement);
        return this.overlay.position().flexibleConnectedTo(origin).withPositions(positions).withFlexibleDimensions(false).withPush(true);
    }

    placementFromPosition(pos: ConnectedPosition): TumUiOverlayPlacement {
        if (pos.overlayY === 'center') {
            const leftEdge = this.directionality.value === 'rtl' ? 'start' : 'end';
            return pos.overlayX === leftEdge ? 'left' : 'right';
        }
        return pos.overlayY === 'bottom' ? 'top' : 'bottom';
    }

    createConnectedOverlay(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement, options: TumUiConnectedOverlayOptions = {}): OverlayRef {
        const originElement = origin instanceof ElementRef ? origin.nativeElement : origin;
        return this.overlay.create({
            positionStrategy: this.positionStrategy(origin, placement),
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
            hasBackdrop: options.hasBackdrop ?? false,
            backdropClass: 'cdk-overlay-transparent-backdrop',
            direction: this.directionality,
            width: options.matchOriginWidth ? originElement.getBoundingClientRect().width : undefined,
        });
    }

    private horizontalPositions(placement: 'left' | 'right'): ConnectedPosition[] {
        const leftEdge = this.directionality.value === 'rtl' ? 'end' : 'start';
        const rightEdge = this.directionality.value === 'rtl' ? 'start' : 'end';
        const left: ConnectedPosition = { originX: leftEdge, originY: 'center', overlayX: rightEdge, overlayY: 'center', offsetX: -OFFSET };
        const right: ConnectedPosition = { originX: rightEdge, originY: 'center', overlayX: leftEdge, overlayY: 'center', offsetX: OFFSET };
        return placement === 'left' ? [left, right] : [right, left];
    }
}
