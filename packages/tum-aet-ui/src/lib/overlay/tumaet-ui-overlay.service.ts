import { ElementRef, Service, inject } from '@angular/core';
import { ConnectedPosition, FlexibleConnectedPositionStrategy, Overlay, OverlayRef } from '@angular/cdk/overlay';
import { Directionality } from '@angular/cdk/bidi';

export type TumAetUiOverlayPlacement = 'top' | 'bottom' | 'left' | 'right';

interface TumAetUiConnectedOverlayOptions {
    hasBackdrop?: boolean;
    matchOriginWidth?: boolean;
    /** Keep a content-sized panel at least as wide as its trigger. */
    minWidthFromOrigin?: boolean;
    /** Minimum panel width when the trigger is narrower. */
    minWidth?: number;
}

const OFFSET = 8;

const VIEWPORT_MARGIN = OFFSET;

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

@Service()
export class TumAetUiOverlayService {
    private readonly overlay = inject(Overlay);
    private readonly directionality = inject(Directionality);

    positionStrategy(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumAetUiOverlayPlacement): FlexibleConnectedPositionStrategy {
        const positions = placement === 'top' || placement === 'bottom' ? VERTICAL_POSITIONS[placement] : this.horizontalPositions(placement);
        return this.overlay.position().flexibleConnectedTo(origin).withPositions(positions).withFlexibleDimensions(false).withPush(true).withViewportMargin(VIEWPORT_MARGIN);
    }

    placementFromPosition(pos: ConnectedPosition): TumAetUiOverlayPlacement {
        if (pos.overlayY === 'center') {
            const leftEdge = this.directionality.value === 'rtl' ? 'start' : 'end';
            return pos.overlayX === leftEdge ? 'left' : 'right';
        }
        return pos.overlayY === 'bottom' ? 'top' : 'bottom';
    }

    createConnectedOverlay(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumAetUiOverlayPlacement, options: TumAetUiConnectedOverlayOptions = {}): OverlayRef {
        const originElement = origin instanceof ElementRef ? origin.nativeElement : origin;
        const originSize = () => ({
            width: options.matchOriginWidth ? originElement.getBoundingClientRect().width : undefined,
            minWidth: options.minWidthFromOrigin ? Math.max(originElement.getBoundingClientRect().width, options.minWidth ?? 0) : undefined,
        });
        const overlayRef = this.overlay.create({
            panelClass: 'tumaet-ui-overlay',
            positionStrategy: this.positionStrategy(origin, placement),
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
            hasBackdrop: options.hasBackdrop ?? false,
            backdropClass: 'cdk-overlay-transparent-backdrop',
            direction: this.directionality,
            ...originSize(),
            maxWidth: options.minWidthFromOrigin ? `calc(100vw - ${2 * VIEWPORT_MARGIN}px)` : undefined,
        });
        if ((options.matchOriginWidth || options.minWidthFromOrigin) && typeof ResizeObserver !== 'undefined') {
            const resizeObserver = new ResizeObserver(() => overlayRef.updateSize(originSize()));
            const disconnect = () => resizeObserver.disconnect();
            overlayRef.attachments().subscribe(() => {
                overlayRef.updateSize(originSize());
                resizeObserver.observe(originElement);
            });
            overlayRef.detachments().subscribe({ next: disconnect, complete: disconnect });
        }
        return overlayRef;
    }

    private horizontalPositions(placement: 'left' | 'right'): ConnectedPosition[] {
        const leftEdge = this.directionality.value === 'rtl' ? 'end' : 'start';
        const rightEdge = this.directionality.value === 'rtl' ? 'start' : 'end';
        const left: ConnectedPosition = { originX: leftEdge, originY: 'center', overlayX: rightEdge, overlayY: 'center', offsetX: -OFFSET };
        const right: ConnectedPosition = { originX: rightEdge, originY: 'center', overlayX: leftEdge, overlayY: 'center', offsetX: OFFSET };
        return placement === 'left' ? [left, right] : [right, left];
    }
}
