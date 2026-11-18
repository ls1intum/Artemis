import { beforeEach, describe, expect, it } from 'vitest';
import { CropperPositionService } from 'app/shared-ui/image-cropper/services/cropper-position.service';
import { CropperPosition } from 'app/shared-ui/image-cropper/interfaces/cropper-position.interface';
import { MoveStart } from 'app/shared-ui/image-cropper/interfaces/move-start.interface';

describe('CropperPositionService', () => {
    let service: CropperPositionService;

    beforeEach(() => {
        service = new CropperPositionService();
    });

    describe('getClientX / getClientY', () => {
        it('reads the coordinates from a mouse event', () => {
            const event = new MouseEvent('mousemove', { clientX: 50, clientY: 70 });

            expect(service.getClientX(event)).toBe(50);
            expect(service.getClientY(event)).toBe(70);
        });

        it('reads the coordinates from the first touch of a touch event', () => {
            const event = { touches: [{ clientX: 12, clientY: 34 }] } as unknown as TouchEvent;

            expect(service.getClientX(event)).toBe(12);
            expect(service.getClientY(event)).toBe(34);
        });

        it('returns 0 for a touch event without active touches (e.g. touchend) instead of throwing', () => {
            // `touchend`/`touchcancel` carry an empty `touches` list; the previous implementation
            // (`event.touches?.[0].clientX`) threw on the missing first touch. It must resolve to 0 now.
            const event = { touches: [] } as unknown as TouchEvent;

            expect(() => service.getClientX(event)).not.toThrow();
            expect(service.getClientX(event)).toBe(0);
            expect(service.getClientY(event)).toBe(0);
        });

        it('falls back to 0 when the mouse coordinate is 0', () => {
            const event = new MouseEvent('mousemove', { clientX: 0, clientY: 0 });

            expect(service.getClientX(event)).toBe(0);
            expect(service.getClientY(event)).toBe(0);
        });
    });

    describe('move', () => {
        it('shifts the cropper position by the pointer delta (via getClientX/getClientY)', () => {
            const cropperPosition: CropperPosition = { x1: 10, y1: 10, x2: 20, y2: 20 };
            const moveStart = { active: true, clientX: 100, clientY: 100, x1: 10, y1: 10, x2: 20, y2: 20 } as MoveStart;
            const event = new MouseEvent('mousemove', { clientX: 130, clientY: 90 });

            service.move(event, moveStart, cropperPosition);

            expect(cropperPosition).toEqual({ x1: 40, y1: 0, x2: 50, y2: 10 });
        });

        it('shifts the cropper position from touch input', () => {
            const cropperPosition: CropperPosition = { x1: 0, y1: 0, x2: 10, y2: 10 };
            const moveStart = { active: true, clientX: 5, clientY: 5, x1: 0, y1: 0, x2: 10, y2: 10 } as MoveStart;
            const event = { touches: [{ clientX: 15, clientY: 25 }] } as unknown as TouchEvent;

            service.move(event, moveStart, cropperPosition);

            expect(cropperPosition).toEqual({ x1: 10, y1: 20, x2: 20, y2: 30 });
        });
    });
});
