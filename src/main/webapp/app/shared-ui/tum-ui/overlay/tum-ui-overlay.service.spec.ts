import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OverlayModule } from '@angular/cdk/overlay';
import { TumUiOverlayPlacement, TumUiOverlayService } from 'app/shared-ui/tum-ui/overlay/tum-ui-overlay.service';

describe('TumUiOverlayService', () => {
    setupTestBed({ zoneless: true });

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
});
