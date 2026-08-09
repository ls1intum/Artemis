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
        off: vi.fn(),
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
import { GocastService } from 'app/videosource/gocast/gocast.service';
import { of, throwError } from 'rxjs';
import { GocastStreamIdentity } from './video-player.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TumLiveAttributionComponent } from 'app/videosource/gocast/tum-live-attribution.component';

describe('VideoPlayerComponent', () => {
    let fixture: ComponentFixture<VideoPlayerComponent>;
    let component: VideoPlayerComponent;
    let videoElement: HTMLVideoElement;

    // Get mock references from globalThis
    const getMockHls = () => (globalThis as any).__mockHlsInstance__;
    const getMockHlsClass = () => (globalThis as any).__MockHlsClass__;

    let mockGocastService: { getPlaybackToken: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        const mockHls = getMockHls();
        const MockHlsClass = getMockHlsClass();

        if (MockHlsClass) MockHlsClass.mockClear?.();
        if (mockHls) {
            mockHls.loadSource.mockClear();
            mockHls.attachMedia.mockClear();
            mockHls.on.mockClear();
            mockHls.off.mockClear();
            mockHls.destroy.mockClear();
            mockHls.startLoad.mockClear();
            mockHls.recoverMediaError.mockClear();
        }

        mockGocastService = {
            getPlaybackToken: vi.fn(),
        };
        TestBed.configureTestingModule({
            imports: [VideoPlayerComponent],
            providers: [
                { provide: GocastService, useValue: mockGocastService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        // Override template to a minimal one for testing (includes resizer elements). Mirror the real template:
        // the resizer handle is a SIBLING of the video column (not a descendant), so the jhiResizable directive
        // resolves it through resizableHandleOutsideHost and delegates the pointerdown from the wrapper. That is
        // the exact wiring that ships in production, so the drag tests below exercise that delegated path.
        TestBed.overrideComponent(VideoPlayerComponent, {
            set: {
                imports: [ResizableDirective, TumLiveAttributionComponent],
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
                            @if (tumLiveWatchUrl()) {
                                <jhi-tum-live-attribution [watchUrl]="tumLiveWatchUrl()!" />
                            }
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

        component.seekTo(42);

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

    describe('GocastService token-based playback', () => {
        const identity: GocastStreamIdentity = { courseId: 42, streamId: 1234, slug: 'eidi' };

        function setGocastIdentity(): void {
            fixture.componentRef.setInput('transcriptSegments', []);
            fixture.componentRef.setInput('gocastIdentity', identity);
        }

        it('fetches a playback token when gocastIdentity is provided (non-public stream)', async () => {
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrl: 'https://tum.live/hls/signed.m3u8', expiresIn: 3600 }));

            setGocastIdentity();
            await render();

            expect(mockGocastService.getPlaybackToken).toHaveBeenCalledWith(42, 1234);
        });

        it('loads the signed playlist URL via HLS.js after token fetch', async () => {
            const signedUrl = 'https://tum.live/hls/signed.m3u8';
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrl: signedUrl, expiresIn: 3600 }));

            setGocastIdentity();
            await render();

            expect(getMockHls().loadSource).toHaveBeenCalledWith(signedUrl);
            expect(getMockHls().attachMedia).toHaveBeenCalled();
        });

        it('falls back to playlistUrlPres when playlistUrl is absent', async () => {
            const presUrl = 'https://tum.live/hls/pres.m3u8';
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrlPres: presUrl, expiresIn: 3600 }));

            setGocastIdentity();
            await render();

            expect(getMockHls().loadSource).toHaveBeenCalledWith(presUrl);
        });

        it('sets tumLiveWatchUrl to the correct watch-page URL', async () => {
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrl: 'https://tum.live/hls/signed.m3u8', expiresIn: 3600 }));

            setGocastIdentity();
            await render();

            expect(component.tumLiveWatchUrl()).toBe('https://tum.live/w/eidi/1234');
        });

        it('does NOT call GocastService when no gocastIdentity is provided (public path)', async () => {
            setInputs('https://cdn.example.com/master.m3u8', []);
            await render();

            expect(mockGocastService.getPlaybackToken).not.toHaveBeenCalled();
        });

        it('sets tokenError signal on service failure', async () => {
            mockGocastService.getPlaybackToken.mockReturnValue(throwError(() => new Error('network error')));

            setGocastIdentity();
            await render();

            expect(component.tokenError()).toBe(true);
        });

        it('falls back to the public videoUrl via HLS when the token fetch fails and a videoUrl is set', async () => {
            const publicUrl = 'https://cdn.example.com/public.m3u8';
            mockGocastService.getPlaybackToken.mockReturnValue(throwError(() => new Error('EP2 503')));

            // gocastIdentity present (EP2 path attempted) AND a public videoUrl fallback available.
            fixture.componentRef.setInput('transcriptSegments', []);
            fixture.componentRef.setInput('gocastIdentity', identity);
            fixture.componentRef.setInput('videoUrl', publicUrl);
            await render();

            // The public URL is loaded into HLS as a fallback...
            expect(getMockHls().loadSource).toHaveBeenCalledWith(publicUrl);
            expect(getMockHls().attachMedia).toHaveBeenCalled();
            // ...and the player does NOT get stuck in the tokenError state because the fallback succeeded.
            expect(component.tokenError()).toBe(false);
        });

        it('sets tokenError when token response has no usable URL', async () => {
            mockGocastService.getPlaybackToken.mockReturnValue(of({ expiresIn: 3600 }));

            setGocastIdentity();
            await render();

            expect(component.tokenError()).toBe(true);
        });

        it('switches to videoUrl and clears the refresh timer when a token refresh has no usable URL', async () => {
            vi.useFakeTimers();
            const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');
            const signedUrl = 'https://tum.live/hls/signed.m3u8';
            const fallbackUrl = 'https://cdn.example.com/public.m3u8';
            mockGocastService.getPlaybackToken.mockReturnValueOnce(of({ playlistUrl: signedUrl, expiresIn: 60 })).mockReturnValueOnce(of({ expiresIn: 60 }));

            fixture.componentRef.setInput('transcriptSegments', []);
            fixture.componentRef.setInput('gocastIdentity', identity);
            fixture.componentRef.setInput('videoUrl', fallbackUrl);
            await render();
            getMockHls().loadSource.mockClear();

            vi.advanceTimersByTime(31000);

            expect(component.tokenError()).toBe(false);
            expect(getMockHls().loadSource).toHaveBeenCalledWith(fallbackUrl);
            expect(clearTimeoutSpy).toHaveBeenCalled();

            vi.useRealTimers();
        });

        it('schedules a token refresh timer before expiry', async () => {
            const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrl: 'https://tum.live/hls/signed.m3u8', expiresIn: 3600 }));

            setGocastIdentity();
            await render();

            // Timer should be set for (3600 - 30) * 1000 ms = 3570000 ms
            expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 3570000);
        });

        it('calls hls.loadSource again on token refresh with the new URL', async () => {
            vi.useFakeTimers();

            const firstUrl = 'https://tum.live/hls/first.m3u8';
            const secondUrl = 'https://tum.live/hls/second.m3u8';

            mockGocastService.getPlaybackToken.mockReturnValueOnce(of({ playlistUrl: firstUrl, expiresIn: 60 })).mockReturnValueOnce(of({ playlistUrl: secondUrl, expiresIn: 60 }));

            setGocastIdentity();
            await render();

            getMockHls().loadSource.mockClear();

            // Advance timer past the refresh point: (60 - 30) * 1000 = 30000 ms
            vi.advanceTimersByTime(31000);

            expect(mockGocastService.getPlaybackToken).toHaveBeenCalledTimes(2);
            // Second call should reload the HLS source with the new URL
            expect(getMockHls().loadSource).toHaveBeenCalledWith(secondUrl);

            vi.useRealTimers();
        });

        it('preserves playback position and state when refreshing native HLS', async () => {
            vi.useFakeTimers();
            const MockHlsClass = getMockHlsClass();
            const originalIsSupported = MockHlsClass.isSupported;
            MockHlsClass.isSupported = vi.fn(() => false);
            const canPlayTypeSpy = vi.spyOn(HTMLMediaElement.prototype, 'canPlayType').mockReturnValue('probably');

            try {
                mockGocastService.getPlaybackToken
                    .mockReturnValueOnce(of({ playlistUrl: 'https://tum.live/hls/native-first.m3u8', expiresIn: 60 }))
                    .mockReturnValueOnce(of({ playlistUrl: 'https://tum.live/hls/native-second.m3u8', expiresIn: 60 }));

                setGocastIdentity();
                await render();

                videoElement.currentTime = 42;
                Object.defineProperty(videoElement, 'paused', { configurable: true, value: false });
                const playSpy = vi.spyOn(videoElement, 'play').mockResolvedValue(undefined);

                vi.advanceTimersByTime(31000);
                expect(videoElement.src).toBe('https://tum.live/hls/native-second.m3u8');

                videoElement.currentTime = 0;
                videoElement.dispatchEvent(new Event('loadedmetadata'));

                expect(videoElement.currentTime).toBe(42);
                expect(playSpy).toHaveBeenCalled();
            } finally {
                MockHlsClass.isSupported = originalIsSupported;
                canPlayTypeSpy.mockRestore();
                vi.useRealTimers();
            }
        });

        it('switches to videoUrl via HLS when a token REFRESH fails and a fallback videoUrl is set', async () => {
            vi.useFakeTimers();

            const firstUrl = 'https://tum.live/hls/first.m3u8';
            const fallbackUrl = 'https://cdn.example.com/public.m3u8';

            // First call succeeds (initial load), second call (refresh) fails
            mockGocastService.getPlaybackToken
                .mockReturnValueOnce(of({ playlistUrl: firstUrl, expiresIn: 60 }))
                .mockReturnValueOnce(throwError(() => new Error('409 binding revoked')));

            fixture.componentRef.setInput('transcriptSegments', []);
            fixture.componentRef.setInput('gocastIdentity', identity);
            fixture.componentRef.setInput('videoUrl', fallbackUrl);
            await render();

            // Clear the initial loadSource call
            getMockHls().loadSource.mockClear();

            // Trigger the refresh timer — this fires fetchAndLoadToken again which will fail
            vi.advanceTimersByTime(31000);

            // The player must switch to fallbackUrl via hls.loadSource (not tokenError)
            expect(component.tokenError()).toBe(false);
            expect(getMockHls().loadSource).toHaveBeenCalledWith(fallbackUrl);

            vi.useRealTimers();
        });

        it('restores currentTime and resumes playback when the refreshed manifest is parsed', async () => {
            vi.useFakeTimers();

            const firstUrl = 'https://tum.live/hls/first.m3u8';
            const secondUrl = 'https://tum.live/hls/second.m3u8';

            mockGocastService.getPlaybackToken.mockReturnValueOnce(of({ playlistUrl: firstUrl, expiresIn: 60 })).mockReturnValueOnce(of({ playlistUrl: secondUrl, expiresIn: 60 }));

            setGocastIdentity();
            await render();

            // Simulate playback progress: at 42s and currently playing
            videoElement.currentTime = 42;
            Object.defineProperty(videoElement, 'paused', { configurable: true, value: false });
            const playSpy = vi.spyOn(videoElement, 'play').mockResolvedValue(undefined);

            getMockHls().on.mockClear();
            getMockHls().off.mockClear();

            // Trigger the refresh: re-fetches and calls hls.loadSource(secondUrl), then registers
            // a one-shot MANIFEST_PARSED handler that should restore position + play state.
            vi.advanceTimersByTime(31000);

            // The component must have registered a MANIFEST_PARSED handler for the restoration.
            const manifestCall = getMockHls().on.mock.calls.find((call: any) => call[0] === 'hlsManifestParsed');
            expect(manifestCall).toBeDefined();
            const manifestHandler = manifestCall[1];

            // Mutate currentTime as a real HLS reload would (resets to 0) so we can verify the seek-back.
            videoElement.currentTime = 0;

            // Fire the manifest-parsed event: this is the core behavior under test.
            manifestHandler();

            // Position is restored to the pre-refresh time...
            expect(videoElement.currentTime).toBe(42);
            // ...and playback resumes because the player was playing before the refresh.
            expect(playSpy).toHaveBeenCalled();
            // ...and the one-shot handler is removed to avoid leaks / double-restore.
            expect(getMockHls().off).toHaveBeenCalledWith('hlsManifestParsed', manifestHandler);

            vi.useRealTimers();
        });

        it('does NOT resume playback on refresh when the player was paused', async () => {
            vi.useFakeTimers();

            mockGocastService.getPlaybackToken
                .mockReturnValueOnce(of({ playlistUrl: 'https://tum.live/hls/first.m3u8', expiresIn: 60 }))
                .mockReturnValueOnce(of({ playlistUrl: 'https://tum.live/hls/second.m3u8', expiresIn: 60 }));

            setGocastIdentity();
            await render();

            videoElement.currentTime = 17;
            Object.defineProperty(videoElement, 'paused', { configurable: true, value: true });
            const playSpy = vi.spyOn(videoElement, 'play').mockResolvedValue(undefined);

            getMockHls().on.mockClear();

            vi.advanceTimersByTime(31000);

            const manifestCall = getMockHls().on.mock.calls.find((call: any) => call[0] === 'hlsManifestParsed');
            const manifestHandler = manifestCall[1];

            videoElement.currentTime = 0;
            manifestHandler();

            // Position is still restored...
            expect(videoElement.currentTime).toBe(17);
            // ...but play() is NOT called because the player was paused.
            expect(playSpy).not.toHaveBeenCalled();

            vi.useRealTimers();
        });

        it('clears the refresh timer on ngOnDestroy', async () => {
            const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrl: 'https://tum.live/hls/signed.m3u8', expiresIn: 3600 }));

            setGocastIdentity();
            await render();

            fixture.destroy();

            expect(clearTimeoutSpy).toHaveBeenCalled();
        });

        it('uses minimum 5000ms delay when expiresIn is very small (≤ TOKEN_REFRESH_BUFFER_SECONDS)', async () => {
            const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
            // expiresIn = 10 → (10 - 30) * 1000 = -20000, floor → 5000
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrl: 'https://tum.live/hls/signed.m3u8', expiresIn: 10 }));

            setGocastIdentity();
            await render();

            // Timer must be set to at least 5000ms, not 0 or negative
            expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 5000);
        });

        it('uses minimum 5000ms delay when expiresIn equals TOKEN_REFRESH_BUFFER_SECONDS', async () => {
            const setTimeoutSpy = vi.spyOn(globalThis, 'setTimeout');
            // expiresIn = 30 → (30 - 30) * 1000 = 0, floor → 5000
            mockGocastService.getPlaybackToken.mockReturnValue(of({ playlistUrl: 'https://tum.live/hls/signed.m3u8', expiresIn: 30 }));

            setGocastIdentity();
            await render();

            expect(setTimeoutSpy).toHaveBeenCalledWith(expect.any(Function), 5000);
        });

        it('falls back to videoUrl via HLS when EP2 fails and videoUrl is set', async () => {
            // Fix 2: EP2 error + fallback videoUrl → player loads public HLS URL instead of setting tokenError.
            const fallbackUrl = 'https://cdn.example.com/public.m3u8';
            mockGocastService.getPlaybackToken.mockReturnValue(throwError(() => new Error('409 no binding')));

            fixture.componentRef.setInput('transcriptSegments', []);
            fixture.componentRef.setInput('gocastIdentity', identity);
            fixture.componentRef.setInput('videoUrl', fallbackUrl);
            await render();

            expect(component.tokenError()).toBe(false);
            expect(getMockHls().loadSource).toHaveBeenCalledWith(fallbackUrl);
        });

        it('sets tokenError when EP2 fails and no videoUrl fallback is available', async () => {
            // Fix 2: EP2 error + no videoUrl → tokenError is shown (existing behaviour preserved).
            mockGocastService.getPlaybackToken.mockReturnValue(throwError(() => new Error('404')));

            fixture.componentRef.setInput('transcriptSegments', []);
            fixture.componentRef.setInput('gocastIdentity', identity);
            // videoUrl not set
            await render();

            expect(component.tokenError()).toBe(true);
            expect(getMockHls().loadSource).not.toHaveBeenCalled();
        });

        it('cancels pending token fetch subscription on component destroy via takeUntilDestroyed', async () => {
            // The getPlaybackToken observable should be cancellable.
            // We verify by checking that destroying the component does not cause tokenError
            // even if the observable would have emitted an error after destroy.
            let reject: (reason?: unknown) => void;
            const neverSettles = new Promise<never>((_resolve, rej) => {
                reject = rej;
            });

            // Return an observable that never emits
            const { Observable } = await import('rxjs');
            let subscriberCompleted = false;
            mockGocastService.getPlaybackToken.mockReturnValue(
                new Observable((observer) => {
                    // Simulate a long-running HTTP request by not completing immediately
                    neverSettles.catch(() => {
                        observer.error(new Error('cancelled'));
                    });
                    return () => {
                        subscriberCompleted = true;
                    };
                }),
            );

            setGocastIdentity();
            await render();

            // Destroy the component — takeUntilDestroyed should cancel the subscription
            fixture.destroy();

            // Signal the never-settling promise to reject (simulating cleanup)
            reject!(new Error('test cleanup'));

            // The teardown should have been called (subscription was unsubscribed)
            expect(subscriberCompleted).toBe(true);
            // tokenError must not be set because the subscription was cancelled before any error
            expect(component.tokenError()).toBe(false);
        });
    });
});
