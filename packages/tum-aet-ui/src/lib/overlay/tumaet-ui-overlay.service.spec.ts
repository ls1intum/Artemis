import { TestBed } from '@angular/core/testing';
import { OverlayModule } from '@angular/cdk/overlay';
import { DomPortal } from '@angular/cdk/portal';
import { Directionality } from '@angular/cdk/bidi';
import { vi } from 'vitest';
import { TumAetUiOverlayPlacement, TumAetUiOverlayService } from './tumaet-ui-overlay.service';

describe('TumAetUiOverlayService', () => {
    let service: TumAetUiOverlayService;
    let origin: HTMLElement;

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [OverlayModule] });
        service = TestBed.inject(TumAetUiOverlayService);
        origin = document.createElement('button');
        document.body.appendChild(origin);
    });

    afterEach(() => {
        origin.remove();
    });

    it('builds a position strategy for every placement', () => {
        const placements: TumAetUiOverlayPlacement[] = ['top', 'bottom', 'left', 'right'];
        for (const placement of placements) {
            expect(service.positionStrategy(origin, placement)).toBeTruthy();
        }
    });

    it('creates a connected overlay that can attach and dispose', () => {
        const overlayRef = service.createConnectedOverlay(origin, 'bottom');
        expect(overlayRef).toBeTruthy();
        expect(overlayRef.getConfig().panelClass).toBe('tumaet-ui-overlay');
        expect(overlayRef.hasAttached()).toBe(false);
        overlayRef.dispose();
    });

    it('can match the overlay width to its origin', () => {
        vi.spyOn(origin, 'getBoundingClientRect').mockReturnValue({ width: 288 } as DOMRect);
        const overlayRef = service.createConnectedOverlay(origin, 'bottom', { matchOriginWidth: true });

        expect(overlayRef.getConfig().width).toBe(288);
        overlayRef.dispose();
    });

    it.each(['width', 'minWidth'] as const)('tracks origin %s across attachments and cleans up on detach and disposal', (dimension) => {
        let resize!: () => void;
        const observe = vi.fn();
        const disconnect = vi.fn();
        class MockResizeObserver {
            observe = observe;
            disconnect = disconnect;
            unobserve = vi.fn();

            constructor(callback: ResizeObserverCallback) {
                resize = () => callback([], this);
            }
        }
        vi.stubGlobal('ResizeObserver', MockResizeObserver);
        let width = 288;
        vi.spyOn(origin, 'getBoundingClientRect').mockImplementation(() => ({ width }) as DOMRect);
        const content = document.createElement('div');
        document.body.appendChild(content);
        const portal = new DomPortal(content);
        const overlayRef = service.createConnectedOverlay(origin, 'bottom', {
            matchOriginWidth: dimension === 'width',
            minWidthFromOrigin: dimension === 'minWidth',
            minWidth: 192,
        });
        try {
            overlayRef.attach(portal);
            expect(observe).toHaveBeenCalledWith(origin);
            expect(overlayRef.getConfig()[dimension]).toBe(288);

            width = 320;
            resize();
            expect(overlayRef.getConfig()[dimension]).toBe(320);
            width = 120;
            resize();
            expect(overlayRef.getConfig()[dimension]).toBe(dimension === 'minWidth' ? 192 : 120);

            overlayRef.detach();
            expect(disconnect).toHaveBeenCalledOnce();
            overlayRef.attach(portal);
            expect(observe).toHaveBeenCalledTimes(2);
            overlayRef.dispose();
            expect(disconnect).toHaveBeenCalledTimes(3);
        } finally {
            overlayRef.dispose();
            content.remove();
            vi.unstubAllGlobals();
        }
    });

    it('propagates right-to-left direction while preserving physical horizontal placements', () => {
        Object.defineProperty(TestBed.inject(Directionality), 'value', { value: 'rtl' });
        const overlayRef = service.createConnectedOverlay(origin, 'left');

        expect(overlayRef.getDirection()).toBe('rtl');
        expect(service.placementFromPosition({ originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center' })).toBe('left');
        expect(service.placementFromPosition({ originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center' })).toBe('right');
        overlayRef.dispose();
    });

    it('derives the applied placement from the resolved connection pair', () => {
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
