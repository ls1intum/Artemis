/**
 * video-player.component.spec.ts
 * Tests for VideoPlayerComponent (HLS.js + transcript sync + resizer)
 *
 * - Mocks `hls.js` library
 * - Minimal template with <video #videoRef>
 * - Covers init/no-init, timeupdate syncing + scrolling, seeking, resizer, and teardown
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

describe('VideoPlayerComponent', () => {
    let fixture: ComponentFixture<VideoPlayerComponent>;
    let component: VideoPlayerComponent;
    let videoElement: HTMLVideoElement;

    // Get mock references from globalThis
    const getMockHls = () => (globalThis as any).__mockHlsInstance__;
    const getMockHlsClass = () => (globalThis as any).__MockHlsClass__;

    beforeEach(async () => {
        const mockHls = getMockHls();
        const MockHlsClass = getMockHlsClass();

        if (MockHlsClass) MockHlsClass.mockClear?.();
        if (mockHls) {
            mockHls.loadSource.mockClear();
            mockHls.attachMedia.mockClear();
            mockHls.on.mockClear();
            mockHls.destroy.mockClear();
            mockHls.startLoad.mockClear();
            mockHls.recoverMediaError.mockClear();
        }

        TestBed.configureTestingModule({
            imports: [VideoPlayerComponent],
        });

        // Override template to a minimal one for testing (includes resizer elements). Mirror the real template:
        // the resizer handle is a SIBLING of the video column (not a descendant), so the jhiResizable directive
        // resolves it through resizableHandleOutsideHost and delegates the pointerdown from the wrapper. That is
        // the exact wiring that ships in production, so the drag tests below exercise that delegated path.
        TestBed.overrideComponent(VideoPlayerComponent, {
            set: {
                imports: [ResizableDirective],
                template: `
                    <div #videoWrapper class="video-wrapper">
                        <div
                            #videoColumn
                            class="video-column"
                            jhiResizable
                            [resizableEdges]="{ right: '.resizer-handle' }"
                            [resizableConstraints]="resizableConstraints"
                            [resizableApplyInlineSize]="false"
                            [resizableHandleOutsideHost]="true"
                            [class.is-resizing]="isResizing()"
                            (resizeStart)="onResizeStart()"
                            (resizeMove)="onVideoColumnResize($event)"
                            (resizeEnd)="onResizeEnd()"
                        >
                            <video #videoRef></video>
                        </div>
                        <button #resizerHandle type="button" class="resizer-handle"></button>
                        <div class="transcript-column"></div>
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

    function setInputs(url?: string, segments: TranscriptSegment[] = [], initialTimestamp?: number): void {
        fixture.componentRef.setInput('videoUrl', url);
        fixture.componentRef.setInput('transcriptSegments', segments);
        fixture.componentRef.setInput('initialTimestamp', initialTimestamp);
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
            { startTime: 10, endTime: 12, text: 'A', slideNumber: 7 },
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
        expect(component.getCurrentSlideNumber()).toBe(7);
    });

    it('timeupdate outside any segment leaves index at -1', async () => {
        const segments: TranscriptSegment[] = [{ startTime: 10, endTime: 12, text: 'A' }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

        component.updateCurrentSegment(0); // outside any segment

        expect(getIndex()).toBe(-1);
    });

    it('updateCurrentSegment: within margin updates; moving outside clears index and slide number', async () => {
        const segments: TranscriptSegment[] = [{ startTime: 5, endTime: 10, text: 'edge', slideNumber: 3 }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

        // Within margin (10.2 <= 10 + 0.3)
        component.updateCurrentSegment(10.2);
        expect(getIndex()).toBe(0);
        expect(component.getCurrentSlideNumber()).toBe(3);

        // Far outside all segments — index and slide number must be cleared
        component.updateCurrentSegment(99);
        expect(getIndex()).toBe(-1);
        expect(component.getCurrentSlideNumber()).toBeUndefined();
    });

    it('getCurrentSlideNumber returns undefined after playback leaves all transcript segments', async () => {
        const segments: TranscriptSegment[] = [{ startTime: 10, endTime: 15, text: 'Slide 5', slideNumber: 5 }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

        component.updateCurrentSegment(12); // inside segment
        expect(component.getCurrentSlideNumber()).toBe(5);

        component.updateCurrentSegment(50); // gap after last segment
        expect(component.getCurrentSlideNumber()).toBeUndefined();
    });

    it('seekTo sets current time and plays', async () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();

        const playSpy = vi.spyOn(videoElement, 'play').mockResolvedValue(undefined);

        expect(component.seekTo(42)).toBe(true);

        expect(videoElement.currentTime).toBe(42);
        expect(playSpy).toHaveBeenCalled();
    });

    it('seekTo can update the position without resuming playback', async () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();

        const playSpy = vi.spyOn(videoElement, 'play').mockResolvedValue(undefined);

        component.seekTo(21, false);

        expect(videoElement.currentTime).toBe(21);
        expect(playSpy).not.toHaveBeenCalled();
    });

    it('reports seekability only once the video has a length', async () => {
        // Before metadata arrives the duration is NaN, which is nothing to judge a target against: the element takes
        // any of them and reports it back unchanged. The seek is still carried out — the browser applies it once the
        // resource has loaded — but a caller that must state whether it really got there waits for this signal
        // instead of trusting the seek's own answer.
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();
        // Stated explicitly rather than relying on what jsdom happens to report for a media element without a resource.
        Object.defineProperty(videoElement, 'duration', { value: NaN, configurable: true });

        expect(component.isSeekable()).toBe(false);
        expect(component.seekTo(42, false)).toBe(true);
        expect(videoElement.currentTime).toBe(42);

        // Metadata arriving is exactly what gives the element its duration, so a real one comes with it — asserting
        // seekability off readyState alone would describe a state the element never actually reaches.
        Object.defineProperty(videoElement, 'readyState', { value: 1, configurable: true });
        Object.defineProperty(videoElement, 'duration', { value: 300, configurable: true });
        videoElement.dispatchEvent(new Event('loadedmetadata'));

        expect(component.isSeekable()).toBe(true);
    });

    it('never reports an unbounded stream as seekable', async () => {
        // Live media has its metadata and still no length: seekTo has nothing to reject a target against there and
        // would take any of them, so a caller that must state whether it really got there must not be let through.
        setInputs('https://cdn.example.com/live.m3u8', []);
        await render();
        Object.defineProperty(videoElement, 'readyState', { value: 1, configurable: true });
        Object.defineProperty(videoElement, 'duration', { value: Infinity, configurable: true });

        videoElement.dispatchEvent(new Event('loadedmetadata'));

        expect(component.isSeekable()).toBe(false);
        // Stated separately from "not seekable yet", so a caller waiting on seekability can stop rather than wait out
        // a budget that will never be met.
        expect(component.isUnbounded()).toBe(true);
    });

    it('emits playerFailed when the media element reports an error', async () => {
        // A video that cannot load will never state a length, so waiting callers need to be told to stop waiting.
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');

        videoElement.dispatchEvent(new Event('error'));

        expect(emitSpy).toHaveBeenCalled();
    });

    it('refuses a target outside the video instead of letting it clamp to the end', async () => {
        // Iris proposes the timestamp, so it can name one the video does not have. The browser would clamp such a seek
        // to the end, which is not the position that was asked for — reporting it as applied would leave a point-out
        // chip pointing at a place nobody was taken to.
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();
        Object.defineProperty(videoElement, 'duration', { value: 300, configurable: true });

        expect(component.seekTo(301, false)).toBe(false);
        expect(component.seekTo(-1, false)).toBe(false);
        expect(videoElement.currentTime).toBe(0);

        expect(component.seekTo(300, false)).toBe(true);
        expect(videoElement.currentTime).toBe(300);
    });

    it('applies initial timestamp after metadata is available', async () => {
        setInputs('https://cdn.example.com/m.m3u8', [], 12.5);
        await render();

        videoElement.dispatchEvent(new Event('loadedmetadata'));

        expect(videoElement.currentTime).toBe(12.5);
        expect(component.currentSegmentIndex()).toBe(-1);
    });

    it('ngOnDestroy destroys hls instance', async () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();

        fixture.destroy();
        expect(getMockHls().destroy).toHaveBeenCalled();
    });

    describe('Resizer functionality', () => {
        function makeRect(width: number): DOMRect {
            return { left: 0, width, top: 0, right: width, bottom: 500, height: 500, x: 0, y: 0, toJSON: () => ({}) } as DOMRect;
        }

        /**
         * Drives a full pointer drag of the right-edge handle from `startWidth` (the video column's
         * current width) to a target pointer X, with the wrapper reporting `wrapperWidth`.
         * jsdom has no PointerEvent constructor; a MouseEvent carries clientX/button, which is all the
         * jhiResizable directive reads. A change-detection pass after mocking the wrapper rect refreshes
         * the [resizableConstraints] binding before the drag starts.
         */
        function dragHandleTo(startWidth: number, targetClientX: number, wrapperWidth: number): HTMLDivElement {
            const videoColumnEl = component.videoColumn()!.nativeElement;
            const wrapperEl = component.videoWrapper()!.nativeElement;
            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue(makeRect(wrapperWidth));
            vi.spyOn(videoColumnEl, 'getBoundingClientRect').mockReturnValue(makeRect(startWidth));
            // Re-run change detection so the getter-bound [resizableConstraints] input picks up the mocked
            // wrapper width before the drag starts.
            fixture.componentRef.changeDetectorRef.markForCheck();
            fixture.detectChanges();

            const handleEl = component.resizerHandle()!.nativeElement;
            handleEl.dispatchEvent(new MouseEvent('pointerdown', { clientX: startWidth, button: 0, bubbles: true }));
            videoColumnEl.dispatchEvent(new MouseEvent('pointermove', { clientX: targetClientX, bubbles: true }));
            videoColumnEl.dispatchEvent(new MouseEvent('pointerup', { clientX: targetClientX, bubbles: true }));
            return videoColumnEl;
        }

        it('toggles isResizing across a drag so the video iframe stops swallowing pointer moves (the #12601 fix)', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            const wrapperEl = component.videoWrapper()!.nativeElement;
            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue(makeRect(1000));
            vi.spyOn(videoColumnEl, 'getBoundingClientRect').mockReturnValue(makeRect(500));
            fixture.detectChanges();

            // isResizing is a protected signal; read it via index access (like the YouTube spec) so tsc's
            // protected-member check (compile:tests) passes - dot access fails TypeScript compilation (TS2445).
            expect(component['isResizing']()).toBe(false);

            const handleEl = component.resizerHandle()!.nativeElement;
            handleEl.dispatchEvent(new MouseEvent('pointerdown', { clientX: 500, button: 0, bubbles: true }));
            expect(component['isResizing']()).toBe(true);

            videoColumnEl.dispatchEvent(new MouseEvent('pointerup', { clientX: 600, bubbles: true }));
            expect(component['isResizing']()).toBe(false);
        });

        it('exposes width constraints derived from the live wrapper width', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const wrapperEl = component.videoWrapper()!.nativeElement;
            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue(makeRect(1000));

            // maxWidth = wrapperWidth (1000) - minTranscript (250)
            expect((component as any).resizableConstraints).toEqual({ minWidth: 300, maxWidth: 750 });
        });

        it('pins maxWidth to the minimum when the wrapper is too narrow for both columns', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const wrapperEl = component.videoWrapper()!.nativeElement;
            vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue(makeRect(540));

            // 540 - 250 = 290 < 300 minimum, so maxWidth is clamped up to the minimum
            expect((component as any).resizableConstraints).toEqual({ minWidth: 300, maxWidth: 300 });
        });

        it('resizes the video column via the jhiResizable handle', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            // Start at 500px, drag the right edge to 600px (within [300, 750]). The column is flex-based
            // (flex: 3), so the resize must be applied as a percentage flex-basis, not an inline width that a
            // flex item ignores: 600 / 1000 = 60%.
            const videoColumnEl = dragHandleTo(500, 600, 1000);

            expect(videoColumnEl.style.flex).toBe('0 0 60%');
            expect(videoColumnEl.style.width).toBe('');
        });

        it('clamps the video column width to the minimum', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            // Drag well below the minimum → clamped to 300px → 300 / 1000 = 30%
            const videoColumnEl = dragHandleTo(500, 100, 1000);

            expect(videoColumnEl.style.flex).toBe('0 0 30%');
        });

        it('clamps the video column width to the maximum', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            // Drag beyond the maximum (1000 - 250 = 750px) → clamped to 750px → 750 / 1000 = 75%
            const videoColumnEl = dragHandleTo(500, 900, 1000);

            expect(videoColumnEl.style.flex).toBe('0 0 75%');
        });

        it('resetSplitRatio clears custom sizing on the video column', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            videoColumnEl.style.flex = '0 0 65%';
            videoColumnEl.style.width = '650px';

            component.resetSplitRatio();

            expect(videoColumnEl.style.flex).toBe('');
            expect(videoColumnEl.style.width).toBe('');
        });

        it('syncTranscriptHeight enforces the minimum transcript height', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            const transcriptColumnEl = fixture.nativeElement.querySelector('.transcript-column') as HTMLElement;
            Object.defineProperty(videoColumnEl, 'offsetHeight', { configurable: true, value: 320 });

            component['syncTranscriptHeight']();

            expect(transcriptColumnEl.style.maxHeight).toBe('500px');
        });

        it('disconnects the ResizeObserver on destroy', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const observer = (component as any).resizeObserver as MockResizeObserver;
            expect(observer).toBeDefined();

            fixture.destroy();

            expect(observer.disconnect).toHaveBeenCalled();
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
            const MockHlsClass = getMockHlsClass();
            const originalIsSupported = MockHlsClass.isSupported;

            try {
                // Make hls.js not supported
                MockHlsClass.isSupported = vi.fn(() => false);

                setInputs('https://cdn.example.com/m.m3u8', []);
                fixture.detectChanges();
                await fixture.whenStable();
                await Promise.resolve();

                const elRef = component.videoRef();
                videoElement = elRef ? elRef.nativeElement : (document.createElement('video') as HTMLVideoElement);

                // Set up canPlayType spy before ngAfterViewInit is called
                const canPlayTypeSpy = vi.spyOn(videoElement, 'canPlayType').mockReturnValue('probably');

                // Trigger the native HLS path
                component.ngAfterViewInit();

                expect(canPlayTypeSpy).toHaveBeenCalledWith('application/vnd.apple.mpegurl');
            } finally {
                // Restore isSupported for other tests
                MockHlsClass.isSupported = originalIsSupported;
            }
        });
    });

    describe('Edge cases', () => {
        it('seekTo does nothing and reports no seek when videoRef is undefined', async () => {
            fixture.detectChanges();
            await fixture.whenStable();

            // Force videoRef to return undefined
            vi.spyOn(component, 'videoRef').mockReturnValue(undefined);

            // Should not throw, and must not claim a seek nobody took
            expect(component.seekTo(10)).toBe(false);
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

    describe('Context provider methods', () => {
        it('getCurrentTime: returns current time from video element', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            videoElement.currentTime = 42.5;

            const currentTime = component.getCurrentTime();

            expect(currentTime).toBe(42.5);
        });

        it('getCurrentTime: returns undefined when videoRef is not available', async () => {
            fixture.detectChanges();
            await fixture.whenStable();

            vi.spyOn(component, 'videoRef').mockReturnValue(undefined);

            const currentTime = component.getCurrentTime();

            expect(currentTime).toBeUndefined();
        });

        it('hasBeenPlayed: returns true when video has played ranges', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            // Mock played TimeRanges
            Object.defineProperty(videoElement, 'played', {
                value: {
                    length: 1,
                    start: (index: number) => 0,
                    end: (index: number) => 10,
                },
                configurable: true,
            });

            const hasPlayed = component.hasBeenPlayed();

            expect(hasPlayed).toBe(true);
        });

        it('hasBeenPlayed: returns false when video has not been played', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            // Mock empty played TimeRanges
            Object.defineProperty(videoElement, 'played', {
                value: {
                    length: 0,
                    start: (index: number) => 0,
                    end: (index: number) => 0,
                },
                configurable: true,
            });

            const hasPlayed = component.hasBeenPlayed();

            expect(hasPlayed).toBe(false);
        });

        it('hasBeenPlayed: returns false when videoRef is not available', async () => {
            fixture.detectChanges();
            await fixture.whenStable();

            vi.spyOn(component, 'videoRef').mockReturnValue(undefined);

            const hasPlayed = component.hasBeenPlayed();

            expect(hasPlayed).toBe(false);
        });
    });
});
