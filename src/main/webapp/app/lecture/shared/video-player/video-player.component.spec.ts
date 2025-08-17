/**
 * video-player.component.spec.ts
 * Tests for VideoPlayerComponent (video.js + transcript sync)
 *
 * - Mocks `video.js`
 * - Minimal template with <video #videoRef>
 * - Covers init/no-init, timeupdate syncing + scrolling, seeking, and teardown
 * - Defines per-element scrollIntoView mock (no global polyfill)
 */

// ---- Mock video.js BEFORE importing the component ----
let __currentTime = 0;
const __handlers = new Map<string, (...args: unknown[]) => void>();

const mockPlayer = {
    on: jest.fn((evt: string, cb: (...args: unknown[]) => void): void => {
        __handlers.set(evt, cb);
    }),
    currentTime: jest.fn((t?: number): number | void => {
        if (typeof t === 'number') {
            __currentTime = t;
            return;
        }
        return __currentTime;
    }),
    play: jest.fn((): void => {}),
    dispose: jest.fn((): void => {}),
    __handlers,
};

jest.mock('video.js', () => {
    const fn = jest.fn(() => mockPlayer);
    (fn as unknown as { __player: typeof mockPlayer }).__player = mockPlayer;
    (fn as unknown as { __reset: () => void }).__reset = () => {
        __currentTime = 0;
        __handlers.clear();
        mockPlayer.on.mockClear();
        mockPlayer.currentTime.mockClear();
        mockPlayer.play.mockClear();
        mockPlayer.dispose.mockClear();
        (fn as jest.Mock).mockClear();
    };
    return { __esModule: true, default: fn };
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import videojs from 'video.js';
import { TranscriptSegment, VideoPlayerComponent } from './video-player.component';

describe('VideoPlayerComponent', () => {
    let fixture: ComponentFixture<VideoPlayerComponent>;
    let component: VideoPlayerComponent;

    const vjs = videojs as unknown as jest.Mock & {
        __player: typeof mockPlayer;
        __reset: () => void;
    };

    beforeEach(async () => {
        vjs.__reset();

        // Step 1: configure
        TestBed.configureTestingModule({
            imports: [VideoPlayerComponent],
        });

        // Step 2: override template (no chaining)
        TestBed.overrideComponent(VideoPlayerComponent, {
            set: { template: '<video #videoRef></video>' },
        });

        // Step 3: compile
        await TestBed.compileComponents();

        fixture = TestBed.createComponent(VideoPlayerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vjs.__reset();
        jest.restoreAllMocks();
    });

    function setInputs(url?: string, segments: TranscriptSegment[] = []): void {
        fixture.componentRef.setInput('videoUrl', url);
        fixture.componentRef.setInput('transcriptSegments', segments);
    }

    // Helper to read the current index regardless of signal/getter/number shape
    function getIndex(): number | undefined {
        const val: unknown = (component as unknown as { currentSegmentIndex: unknown }).currentSegmentIndex;
        if (typeof val === 'function') return (val as () => number)();
        if (val && typeof (val as { value: unknown }).value === 'number') return (val as { value: number }).value;
        if (typeof val === 'number') return val as number;
        return undefined;
    }

    it('does not initialize video.js when no videoUrl is provided', () => {
        setInputs(undefined, []);
        fixture.detectChanges();

        expect(vjs).not.toHaveBeenCalled();
        expect(component.player).toBeNull();
    });

    it('initializes video.js when videoUrl is provided', () => {
        const url = 'https://cdn.example.com/master.m3u8';
        setInputs(url, []);
        fixture.detectChanges();

        expect(vjs).toHaveBeenCalledOnce();
        const [el, options] = (vjs as jest.Mock).mock.calls[0];
        expect(el).toBeInstanceOf(HTMLVideoElement);
        expect(options).toEqual(
            expect.objectContaining({
                controls: true,
                preload: 'auto',
                sources: [
                    expect.objectContaining({
                        src: url,
                        type: 'application/x-mpegURL',
                    }),
                ],
            }),
        );
        expect(component.player).toBe(vjs.__player);
        expect(vjs.__player.on).toHaveBeenCalledWith('timeupdate', expect.any(Function));
    });

    it('timeupdate sets active segment and scrolls the element into view', () => {
        const segments: TranscriptSegment[] = [
            { startTime: 10, endTime: 12, text: 'A' },
            { startTime: 20, endTime: 22, text: 'B' },
        ];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        fixture.detectChanges();

        // Return a dummy element for the id the component will look up
        const el = document.createElement('div');
        const scrollSpy = jest.fn();
        Object.defineProperty(el, 'scrollIntoView', { value: scrollSpy, configurable: true });

        const getById = jest
            .spyOn(document, 'getElementById')
            .mockImplementation((id: string): HTMLElement | null => (id === `segment-${segments[0].startTime}` ? (el as unknown as HTMLElement) : null));

        // Simulate timeupdate at 10.1s (inside first segment)
        vjs.__player.currentTime(10.1);
        const handler = vjs.__player.__handlers.get('timeupdate') as ((...args: unknown[]) => void) | undefined;

        expect(typeof handler).toBe('function');
        if (handler) {
            handler();
        }

        expect(getIndex()).toBe(0);
        expect(scrollSpy).toHaveBeenCalledOnce();

        // Same time again -> index unchanged, no extra scroll
        if (handler) {
            handler();
        }
        expect(scrollSpy).toHaveBeenCalledOnce();

        getById.mockRestore();
    });

    it('timeupdate outside any segment leaves index at -1 and does not scroll', () => {
        const segments: TranscriptSegment[] = [{ startTime: 10, endTime: 12, text: 'A' }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        fixture.detectChanges();

        const el = document.createElement('div');
        const scrollSpy = jest.fn();
        Object.defineProperty(el, 'scrollIntoView', { value: scrollSpy, configurable: true });
        const getById = jest.spyOn(document, 'getElementById').mockReturnValue(el as unknown as HTMLElement);

        vjs.__player.currentTime(0); // outside any segment
        const handler = vjs.__player.__handlers.get('timeupdate') as ((...args: unknown[]) => void) | undefined;

        if (handler) {
            handler();
        }

        expect(getIndex()).toBe(-1);
        expect(scrollSpy).not.toHaveBeenCalled();

        getById.mockRestore();
    });

    it('updateCurrentSegment: within margin updates; far outside does not clear back to -1', () => {
        const segments: TranscriptSegment[] = [{ startTime: 5, endTime: 10, text: 'edge' }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        fixture.detectChanges();

        // Within margin (10.2 <= 10 + 0.3)
        component.updateCurrentSegment(10.2);
        expect(getIndex()).toBe(0);

        // Far outside -> index remains last valid (component does not set -1)
        component.updateCurrentSegment(99);
        expect(getIndex()).toBe(0);
    });

    it('seekTo sets current time and plays', () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        fixture.detectChanges();

        component.seekTo(42);

        expect(vjs.__player.currentTime).toHaveBeenCalledWith(42);
        expect(vjs.__player.play).toHaveBeenCalled();
    });

    it('ngOnDestroy disposes the player', () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        fixture.detectChanges();

        fixture.destroy();
        expect(vjs.__player.dispose).toHaveBeenCalledOnce();
    });
});
