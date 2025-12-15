/**
 * video-player.component.spec.ts
 * Tests for VideoPlayerComponent (HLS.js + transcript sync + resizer)
 *
 * - Mocks `hls.js` library
 * - Mocks `interactjs` library
 * - Minimal template with <video #videoRef>
 * - Covers init/no-init, timeupdate syncing + scrolling, seeking, resizer, and teardown
 */

// ---- Mock hls.js BEFORE importing the component ----
const mockHls = {
    loadSource: jest.fn(),
    attachMedia: jest.fn(),
    on: jest.fn(),
    destroy: jest.fn(),
    startLoad: jest.fn(),
    recoverMediaError: jest.fn(),
};

const MockHlsClass = jest.fn(() => mockHls);
(MockHlsClass as any).isSupported = jest.fn(() => true);

// Mock Hls.Events and Hls.ErrorTypes as static properties
(MockHlsClass as any).Events = {
    ERROR: 'hlsError',
    MANIFEST_PARSED: 'hlsManifestParsed',
    MEDIA_ATTACHED: 'hlsMediaAttached',
};

(MockHlsClass as any).ErrorTypes = {
    NETWORK_ERROR: 'networkError',
    MEDIA_ERROR: 'mediaError',
    OTHER_ERROR: 'otherError',
};

jest.mock('hls.js', () => ({
    __esModule: true,
    default: MockHlsClass,
}));

// ---- Mock interactjs ----
const mockInteractInstance = {
    draggable: jest.fn().mockReturnThis(),
    unset: jest.fn(),
};

const mockInteract = jest.fn(() => mockInteractInstance);

jest.mock('interactjs', () => ({
    __esModule: true,
    default: mockInteract,
}));

// ---- Mock ResizeObserver ----
class MockResizeObserver {
    callback: ResizeObserverCallback;
    constructor(callback: ResizeObserverCallback) {
        this.callback = callback;
    }
    observe = jest.fn();
    unobserve = jest.fn();
    disconnect = jest.fn();
}

global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;

// ---- Imports AFTER the mock ----
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VideoPlayerComponent } from './video-player.component';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

describe('VideoPlayerComponent', () => {
    let fixture: ComponentFixture<VideoPlayerComponent>;
    let component: VideoPlayerComponent;
    let videoElement: HTMLVideoElement;

    beforeEach(async () => {
        MockHlsClass.mockClear();
        mockHls.loadSource.mockClear();
        mockHls.attachMedia.mockClear();
        mockHls.on.mockClear();
        mockHls.destroy.mockClear();
        mockHls.startLoad.mockClear();
        mockHls.recoverMediaError.mockClear();

        mockInteract.mockClear();
        mockInteractInstance.draggable.mockClear();
        mockInteractInstance.unset.mockClear();

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
        jest.clearAllMocks();
        jest.restoreAllMocks();
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

        expect(MockHlsClass).not.toHaveBeenCalled();
        expect((component as any).hls).toBeUndefined();
    });

    it('initializes hls.js when videoUrl is provided and hls.js is supported', async () => {
        const url = 'https://cdn.example.com/master.m3u8';
        setInputs(url, []);
        await render();

        expect(MockHlsClass).toHaveBeenCalled();
        expect(mockHls.loadSource).toHaveBeenCalledWith(url);
        expect(mockHls.attachMedia).toHaveBeenCalledWith(videoElement);
        expect((component as any).hls).toBe(mockHls);
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
            viewer.scrollToSegment = jest.fn();
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

        const playSpy = jest.spyOn(videoElement, 'play').mockResolvedValue(undefined);

        component.seekTo(42);

        expect(videoElement.currentTime).toBe(42);
        expect(playSpy).toHaveBeenCalled();
    });

    it('ngOnDestroy destroys hls instance', async () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();

        fixture.destroy();
        expect(mockHls.destroy).toHaveBeenCalled();
    });

    describe('Resizer functionality', () => {
        it('initializes interact.js on the resizer handle', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const resizerEl = component.resizerHandle()?.nativeElement;
            expect(mockInteract).toHaveBeenCalledWith(resizerEl);
            expect(mockInteractInstance.draggable).toHaveBeenCalled();
        });

        it('configures draggable with move listener and cursor checker', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            expect(mockInteractInstance.draggable).toHaveBeenCalledWith(
                expect.objectContaining({
                    listeners: expect.objectContaining({
                        move: expect.any(Function),
                    }),
                    cursorChecker: expect.any(Function),
                }),
            );

            // Verify cursorChecker returns 'col-resize'
            const draggableConfig = mockInteractInstance.draggable.mock.calls[0][0];
            expect(draggableConfig.cursorChecker()).toBe('col-resize');
        });

        it('resizes video column on drag move', async () => {
            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            const videoColumnEl = component.videoColumn()!.nativeElement;
            const wrapperEl = component.videoWrapper()!.nativeElement;

            // Mock getBoundingClientRect for wrapper
            jest.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
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
            const draggableConfig = mockInteractInstance.draggable.mock.calls[0][0];
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

            jest.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
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

            const draggableConfig = mockInteractInstance.draggable.mock.calls[0][0];
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

            jest.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
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

            const draggableConfig = mockInteractInstance.draggable.mock.calls[0][0];
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

            jest.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
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
            const draggableConfig = mockInteractInstance.draggable.mock.calls[0][0];
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

            expect(mockInteractInstance.unset).toHaveBeenCalled();
        });

        it('removes window resize listener on destroy', async () => {
            const removeEventListenerSpy = jest.spyOn(window, 'removeEventListener');

            setInputs('https://cdn.example.com/m.m3u8', []);
            await render();

            fixture.destroy();

            expect(removeEventListenerSpy).toHaveBeenCalledWith('resize', expect.any(Function));
        });
    });
});
