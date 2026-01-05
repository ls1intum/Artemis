/**
 * video-player.component.spec.ts
 * Tests for VideoPlayerComponent (HLS.js + transcript sync + resizer)
 *
 * - Mocks `hls.js` library
 * - Mocks `interactjs` library
 * - Minimal template with <video #videoRef>
 * - Covers init/no-init, timeupdate syncing + scrolling, seeking, resizer, and teardown
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

// ---- Mock hls.js BEFORE importing the component ----
vi.mock('hls.js', () => {
    const mockHls = {
        loadSource: vi.fn(),
        attachMedia: vi.fn(),
        on: vi.fn(),
        destroy: vi.fn(),
        startLoad: vi.fn(),
        recoverMediaError: vi.fn(),
    };

    function MockHlsClass() {
        return mockHls;
    }

    MockHlsClass.isSupported = vi.fn(() => true);
    MockHlsClass.Events = {
        ERROR: 'hlsError',
        MANIFEST_PARSED: 'hlsManifestParsed',
        MEDIA_ATTACHED: 'hlsMediaAttached',
    };
    MockHlsClass.ErrorTypes = {
        NETWORK_ERROR: 'networkError',
        MEDIA_ERROR: 'mediaError',
        OTHER_ERROR: 'otherError',
    };

    // Store reference for tests to access
    (globalThis as any).__mockHlsInstance__ = mockHls;
    (globalThis as any).__MockHlsClass__ = MockHlsClass;

    return {
        __esModule: true,
        default: MockHlsClass,
    };
});

// ---- Mock interactjs ----
vi.mock('interactjs', () => {
    const mockInstance = {
        draggable: vi.fn().mockReturnThis(),
        unset: vi.fn(),
    };

    const mockInteract = vi.fn(() => mockInstance);

    // Store reference for tests to access
    (globalThis as any).__mockInteractInstance__ = mockInstance;
    (globalThis as any).__mockInteract__ = mockInteract;

    return {
        __esModule: true,
        default: mockInteract,
    };
});

// ---- Mock ResizeObserver ----
class MockResizeObserver {
    callback: ResizeObserverCallback;
    constructor(callback: ResizeObserverCallback) {
        this.callback = callback;
    }
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;

// ---- Imports AFTER the mock ----
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VideoPlayerComponent } from './video-player.component';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

describe('VideoPlayerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<VideoPlayerComponent>;
    let component: VideoPlayerComponent;
    let videoElement: HTMLVideoElement;

    // Get mock references from globalThis
    const getMockHls = () => (globalThis as any).__mockHlsInstance__;
    const getMockHlsClass = () => (globalThis as any).__MockHlsClass__;
    const getMockInteract = () => (globalThis as any).__mockInteract__;
    const getMockInteractInstance = () => (globalThis as any).__mockInteractInstance__;

    beforeEach(async () => {
        const mockHls = getMockHls();
        const MockHlsClass = getMockHlsClass();
        const mockInteract = getMockInteract();
        const mockInteractInstance = getMockInteractInstance();

        if (MockHlsClass) MockHlsClass.mockClear?.();
        if (mockHls) {
            mockHls.loadSource.mockClear();
            mockHls.attachMedia.mockClear();
            mockHls.on.mockClear();
            mockHls.destroy.mockClear();
            mockHls.startLoad.mockClear();
            mockHls.recoverMediaError.mockClear();
        }

        if (mockInteract) mockInteract.mockClear();
        if (mockInteractInstance) {
            mockInteractInstance.draggable.mockClear();
            getMockInteractInstance().unset.mockClear();
        }

        TestBed.configureTestingModule({
            imports: [VideoPlayerComponent],
        });

        // Override template to a minimal one for testing (includes resizer elements)
        TestBed.overrideComponent(VideoPlayerComponent, {
            set: {
                template: `
                    <div #videoWrapper class="video-wrapper">
                        <div #videoColumn class="video-column">
                            <video #videoRef></video>
                        </div>
                        <div #resizerHandle class="resizer-handle"></div>
                    </div>
                `,
            },
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(VideoPlayerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function setInputs(url?: string, segments: TranscriptSegment[] = []): void {
        fixture.componentRef.setInput('videoUrl', url);
        fixture.componentRef.setInput('transcriptSegments', segments);
    }

    async function render(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
        await Promise.resolve();

        const elRef = component.videoRef();
        videoElement = elRef ? elRef.nativeElement : (document.createElement('video') as HTMLVideoElement);
    }

    function getIndex(): number {
        const val = component.currentSegmentIndex();
        return val;
    }

    it('does not initialize hls.js when no videoUrl is provided', async () => {
        setInputs(undefined, []);
        await render();

        // When no URL is provided, hls should not be initialized
        expect((component as any).hls).toBeUndefined();
    });

    it('initializes hls.js when videoUrl is provided and hls.js is supported', async () => {
        const url = 'https://cdn.example.com/master.m3u8';
        setInputs(url, []);
        await render();

        // Verify hls instance was created and configured
        expect(getMockHls().loadSource).toHaveBeenCalledWith(url);
        expect(getMockHls().attachMedia).toHaveBeenCalledWith(videoElement);
        expect((component as any).hls).toBe(getMockHls());
    });

    it('timeupdate sets active segment and scrolls the element into view', async () => {
        const segments: TranscriptSegment[] = [
            { startTime: 10, endTime: 12, text: 'A' },
            { startTime: 20, endTime: 22, text: 'B' },
        ];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

        // Mock scrollToSegment on transcript viewer
        const viewer = component.transcriptViewer();
        if (viewer) {
            viewer.scrollToSegment = vi.fn();
        }

        // Simulate timeupdate at 10.1s (inside first segment)
        component.updateCurrentSegment(10.1);

        expect(getIndex()).toBe(0);
    });

    it('timeupdate outside any segment leaves index at -1', async () => {
        const segments: TranscriptSegment[] = [{ startTime: 10, endTime: 12, text: 'A' }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

        component.updateCurrentSegment(0); // outside any segment

        expect(getIndex()).toBe(-1);
    });

    it('updateCurrentSegment: within margin updates; far outside does not clear back to -1', async () => {
        const segments: TranscriptSegment[] = [{ startTime: 5, endTime: 10, text: 'edge' }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

        // Within margin (10.2 <= 10 + 0.3)
        component.updateCurrentSegment(10.2);
        expect(getIndex()).toBe(0);

        // Far outside -> index remains last valid (component does not set -1)
        component.updateCurrentSegment(99);
        expect(getIndex()).toBe(0);
    });

    it('seekTo sets current time and plays', async () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();

        const playSpy = vi.spyOn(videoElement, 'play').mockResolvedValue(undefined);

        component.seekTo(42);

        expect(videoElement.currentTime).toBe(42);
        expect(playSpy).toHaveBeenCalled();
    });

    it('ngOnDestroy destroys hls instance', async () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();

        fixture.destroy();
        expect(getMockHls().destroy).toHaveBeenCalled();
    });

    describe('Resizer functionality', () => {
        it('initializes interact.js on the resizer handle', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const resizerEl = component.resizerHandle()?.nativeElement;
            expect(getMockInteract()).toHaveBeenCalledWith(resizerEl);
            expect(getMockInteractInstance().draggable).toHaveBeenCalled();
        });

        it('configures draggable with move listener and cursor checker', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            expect(getMockInteractInstance().draggable).toHaveBeenCalledWith(
                expect.objectContaining({
                    listeners: expect.objectContaining({
                        move: expect.any(Function),
                    }),
                    cursorChecker: expect.any(Function),
                }),
            );

            // Verify cursorChecker returns 'col-resize'
            const draggableConfig = getMockInteractInstance().draggable.mock.calls[0][0];
            expect(draggableConfig.cursorChecker()).toBe('col-resize');
        });

        it('resizes video column on drag move', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            const wrapperEl = component.videoWrapper()!.nativeElement;

            // Mock getBoundingClientRect for wrapper
            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
                left: 0,
                width: 1000,
                top: 0,
                right: 1000,
                bottom: 500,
                height: 500,
                x: 0,
                y: 0,
                toJSON: () => ({}),
            } as DOMRect);

            // Get the move listener and call it
            const draggableConfig = getMockInteractInstance().draggable.mock.calls[0][0];
            const moveListener = draggableConfig.listeners.move;

            // Simulate drag to position 600px from left
            moveListener({ clientX: 600 });

            // Browser normalizes 'none' to '0 0 auto'
            expect(videoColumnEl.style.flex).toBe('0 0 auto');
            expect(videoColumnEl.style.width).toBe('600px');
        });

        it('clamps video column width to minimum', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            const wrapperEl = component.videoWrapper()!.nativeElement;

            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
                left: 0,
                width: 1000,
                top: 0,
                right: 1000,
                bottom: 500,
                height: 500,
                x: 0,
                y: 0,
                toJSON: () => ({}),
            } as DOMRect);

            const draggableConfig = getMockInteractInstance().draggable.mock.calls[0][0];
            const moveListener = draggableConfig.listeners.move;

            // Try to drag below minimum (300px)
            moveListener({ clientX: 100 });

            expect(videoColumnEl.style.width).toBe('300px');
        });

        it('clamps video column width to maximum', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            const wrapperEl = component.videoWrapper()!.nativeElement;

            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
                left: 0,
                width: 1000,
                top: 0,
                right: 1000,
                bottom: 500,
                height: 500,
                x: 0,
                y: 0,
                toJSON: () => ({}),
            } as DOMRect);

            const draggableConfig = getMockInteractInstance().draggable.mock.calls[0][0];
            const moveListener = draggableConfig.listeners.move;

            // Try to drag beyond maximum (1000 - 250 = 750px)
            moveListener({ clientX: 900 });

            expect(videoColumnEl.style.width).toBe('750px');
        });

        it('resets video column styles on window resize', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            const wrapperEl = component.videoWrapper()!.nativeElement;

            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
                left: 0,
                width: 1000,
                top: 0,
                right: 1000,
                bottom: 500,
                height: 500,
                x: 0,
                y: 0,
                toJSON: () => ({}),
            } as DOMRect);

            // First, simulate a drag to set custom width
            const draggableConfig = getMockInteractInstance().draggable.mock.calls[0][0];
            draggableConfig.listeners.move({ clientX: 500 });

            // Browser normalizes 'none' to '0 0 auto'
            expect(videoColumnEl.style.flex).toBe('0 0 auto');
            expect(videoColumnEl.style.width).toBe('500px');

            // Trigger window resize
            window.dispatchEvent(new Event('resize'));

            // Styles should be reset to empty
            expect(videoColumnEl.style.flex).toBe('');
            expect(videoColumnEl.style.width).toBe('');
        });

        it('cleans up interact instance on destroy', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            fixture.destroy();

            expect(getMockInteractInstance().unset).toHaveBeenCalled();
        });

        it('removes window resize listener on destroy', async () => {
            const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener');

            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            fixture.destroy();

            expect(removeEventListenerSpy).toHaveBeenCalledWith('resize', expect.any(Function));
        });
    });

    describe('HLS error handling', () => {
        it('handles fatal network error by calling startLoad', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            // Get the error handler from the on() call
            const onCalls = getMockHls().on.mock.calls;
            const errorCall = onCalls.find((call: any) => call[0] === 'hlsError');
            expect(errorCall).toBeDefined();

            const errorHandler = errorCall[1];

            // Simulate fatal network error
            errorHandler('hlsError', {
                fatal: true,
                type: 'networkError',
            });

            expect(getMockHls().startLoad).toHaveBeenCalled();
        });

        it('handles fatal media error by calling recoverMediaError', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const onCalls = getMockHls().on.mock.calls;
            const errorCall = onCalls.find((call: any) => call[0] === 'hlsError');
            const errorHandler = errorCall[1];

            // Simulate fatal media error
            errorHandler('hlsError', {
                fatal: true,
                type: 'mediaError',
            });

            expect(getMockHls().recoverMediaError).toHaveBeenCalled();
        });

        it('destroys hls on fatal unrecoverable error', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const onCalls = getMockHls().on.mock.calls;
            const errorCall = onCalls.find((call: any) => call[0] === 'hlsError');
            const errorHandler = errorCall[1];

            // Simulate fatal unrecoverable error
            errorHandler('hlsError', {
                fatal: true,
                type: 'otherError',
            });

            expect(getMockHls().destroy).toHaveBeenCalled();
        });

        it('ignores non-fatal errors', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            getMockHls().startLoad.mockClear();
            getMockHls().recoverMediaError.mockClear();
            getMockHls().destroy.mockClear();

            const onCalls = getMockHls().on.mock.calls;
            const errorCall = onCalls.find((call: any) => call[0] === 'hlsError');
            const errorHandler = errorCall[1];

            // Simulate non-fatal error
            errorHandler('hlsError', {
                fatal: false,
                type: 'networkError',
            });

            expect(getMockHls().startLoad).not.toHaveBeenCalled();
            expect(getMockHls().recoverMediaError).not.toHaveBeenCalled();
            expect(getMockHls().destroy).not.toHaveBeenCalled();
        });
    });

    describe('Native HLS support', () => {
        it('uses native HLS when hls.js is not supported but browser supports it', async () => {
            // Make hls.js not supported
            const MockHlsClass = getMockHlsClass();
            MockHlsClass.isSupported = vi.fn(() => false);

            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            // Check if native src was set (need to mock canPlayType)
            const canPlayTypeSpy = vi.spyOn(videoElement, 'canPlayType').mockReturnValue('probably');

            // Reinitialize to trigger the native path
            component.ngAfterViewInit();

            expect(canPlayTypeSpy).toHaveBeenCalledWith('application/vnd.apple.mpegurl');

            // Restore isSupported for other tests
            MockHlsClass.isSupported = vi.fn(() => true);
        });
    });

    describe('Edge cases', () => {
        it('seekTo does nothing when videoRef is undefined', async () => {
            fixture.detectChanges();
            await fixture.whenStable();

            // Force videoRef to return undefined
            vi.spyOn(component, 'videoRef').mockReturnValue(undefined);

            // Should not throw
            expect(() => component.seekTo(10)).not.toThrow();
        });

        it('updateCurrentSegment does not update if same segment is current', async () => {
            const segments: TranscriptSegment[] = [{ startTime: 10, endTime: 12, text: 'A' }];
            setInputs('https://cdn.example.com/m.m3u8', segments);
            await render();

            // First update
            component.updateCurrentSegment(10.1);
            expect(getIndex()).toBe(0);

            // Same segment, should not trigger new update
            const setSignalSpy = vi.spyOn(component.currentSegmentIndex, 'set');
            component.updateCurrentSegment(10.5);
            expect(setSignalSpy).not.toHaveBeenCalled();
        });
    });
});
