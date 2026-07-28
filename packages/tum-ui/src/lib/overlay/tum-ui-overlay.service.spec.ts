import { TestBed } from '@angular/core/testing';
import { OverlayModule } from '@angular/cdk/overlay';
import { TumUiOverlayPlacement, TumUiOverlayService } from './tum-ui-overlay.service';

describe('TumUiOverlayService', () => {
    let service: TumUiOverlayService;
    let origin: HTMLElement;

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [OverlayModule] });
        service = TestBed.inject(TumUiOverlayService);
        origin = document.createElement('button');
        document.body.appendChild(origin);
    });

    afterEach(() => {
        origin.remove();
    });

    it('builds a position strategy for every placement', () => {
        const placements: TumUiOverlayPlacement[] = ['top', 'bottom', 'left', 'right'];
        for (const placement of placements) {
            expect(service.positionStrategy(origin, placement)).toBeTruthy();
        }
    });

    it('creates a connected overlay that can attach and dispose', () => {
        const overlayRef = service.createConnectedOverlay(origin, 'bottom');
        expect(overlayRef).toBeTruthy();
        expect(overlayRef.hasAttached()).toBe(false);
        overlayRef.dispose();
    });

    it('derives the applied placement from a resolved connection pair (drives caret flip-tracking)', () => {
        expect(service.placementFromPosition({ originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom' })).toBe('top');
        expect(service.placementFromPosition({ originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top' })).toBe('bottom');
        expect(service.placementFromPosition({ originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center' })).toBe('left');
        expect(service.placementFromPosition({ originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center' })).toBe('right');
        expect(service.placementFromPosition({ originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'bottom' })).toBe('top');
        expect(service.placementFromPosition({ originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'top' })).toBe('bottom');
        expect(service.placementFromPosition({ originX: 'end', originY: 'center', overlayX: 'end', overlayY: 'center' })).toBe('left');
        expect(service.placementFromPosition({ originX: 'start', originY: 'center', overlayX: 'start', overlayY: 'center' })).toBe('right');
    });
});
