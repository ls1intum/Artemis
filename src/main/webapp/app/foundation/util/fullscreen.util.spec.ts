import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { enterFullscreen, isFullScreen } from 'app/foundation/util/fullscreen.util';

describe('fullscreen.util', () => {
    describe('isFullScreen', () => {
        let originalDescriptor: PropertyDescriptor | undefined;

        beforeEach(() => {
            originalDescriptor = Object.getOwnPropertyDescriptor(document, 'fullscreenElement');
        });

        afterEach(() => {
            if (originalDescriptor) {
                Object.defineProperty(document, 'fullscreenElement', originalDescriptor);
            } else {
                delete (document as { fullscreenElement?: unknown }).fullscreenElement;
            }
        });

        it('returns false when no element is in fullscreen', () => {
            Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => null });
            expect(isFullScreen()).toBe(false);
        });

        it('returns a boolean true (not the element itself) when a fullscreen element is present', () => {
            const element = document.createElement('div');
            Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => element });
            const result = isFullScreen();
            // The function must coerce to a real boolean, not leak the underlying Element.
            expect(result).toBe(true);
            expect(typeof result).toBe('boolean');
        });
    });

    describe('enterFullscreen', () => {
        it('calls the standard requestFullscreen when available', () => {
            const element = document.createElement('div');
            const requestFullscreen = vi.fn();
            (element as { requestFullscreen?: () => void }).requestFullscreen = requestFullscreen;
            enterFullscreen(element);
            expect(requestFullscreen).toHaveBeenCalledOnce();
        });
    });
});
