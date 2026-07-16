import { beforeEach, describe, expect, it, vi } from 'vitest';

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

// ---- Imports AFTER the mocks ----
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { YouTubePlayerComponent } from './youtube-player.component';

describe('YouTubePlayerComponent', () => {
    let fixture: ComponentFixture<YouTubePlayerComponent>;
    let component: YouTubePlayerComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [YouTubePlayerComponent], providers: [provideTranslateService()] }).compileComponents();
        fixture = TestBed.createComponent(YouTubePlayerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('videoId', 'dQw4w9WgXcQ');
        fixture.componentRef.setInput('transcriptSegments', [
            { startTime: 0, endTime: 10, text: 'a' },
            { startTime: 10, endTime: 20, text: 'b' },
            { startTime: 20, endTime: 30, text: 'c' },
        ]);
    });

    async function render(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable();
    }

    it('starts polling on PLAYING state', () => {
        vi.useFakeTimers();
        const spy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        // Simulate player ready + stub getCurrentTime()
        (component as any).youtubePlayer = { getCurrentTime: () => 15, seekTo: vi.fn() };
        component.onStateChange({ data: 1 /* PLAYING */ } as any);
        vi.advanceTimersByTime(300);
        expect(spy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('stops polling on PAUSED and updates segment once', () => {
        vi.useFakeTimers();
        (component as any).youtubePlayer = { getCurrentTime: () => 25, seekTo: vi.fn() };
        const spy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        component.onStateChange({ data: 1 } as any); // start polling
        component.onStateChange({ data: 2 /* PAUSED */ } as any);
        vi.advanceTimersByTime(2000);
        // Polling stopped → spy called once from the PAUSED-branch update only (plus any tick before the pause)
        expect(spy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('seekTo calls player.seekTo and updates segment immediately', () => {
        const seekSpy = vi.fn();
        (component as any).youtubePlayer = { getCurrentTime: () => 15, seekTo: seekSpy };
        const updateSpy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        component.seekTo(12);
        expect(seekSpy).toHaveBeenCalledWith(12, true);
        expect(updateSpy).toHaveBeenCalledWith(12);
    });

    it('emits playerFailed when readiness timeout elapses without onPlayerReady', () => {
        vi.useFakeTimers();
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.ngAfterViewInit();
        vi.advanceTimersByTime(11_000);
        expect(emitSpy).toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('emits playerFailed on YT error event', () => {
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.onPlayerError({ data: 100 } as any);
        expect(emitSpy).toHaveBeenCalled();
    });

    it('clears readiness timeout on successful onPlayerReady', () => {
        vi.useFakeTimers();
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.ngAfterViewInit();
        vi.advanceTimersByTime(5_000);
        (component as any).youtubePlayer = { getCurrentTime: () => 0, seekTo: vi.fn() };
        component.onPlayerReady({} as any);
        vi.advanceTimersByTime(10_000);
        expect(emitSpy).not.toHaveBeenCalled();
        vi.useRealTimers();
    });

    it('prefers the Angular playerComponent viewChild over the event target on ready', () => {
        const viewChildPlayer = { getCurrentTime: () => 15, seekTo: vi.fn() };
        const eventPlayer = { getCurrentTime: () => 0, seekTo: vi.fn() };

        (component as any).playerComponent = () => viewChildPlayer as any;
        component.onPlayerReady({ target: eventPlayer } as any);

        expect((component as any).youtubePlayer).toBe(viewChildPlayer);
        expect(component['currentSegmentIndex']()).toBe(1);
        expect(component.getCurrentSlideNumber()).toBeUndefined();
    });

    it('tracks the active slide number from transcript segments', () => {
        fixture.componentRef.setInput('transcriptSegments', [
            { startTime: 0, endTime: 10, text: 'a', slideNumber: 3 },
            { startTime: 10, endTime: 20, text: 'b', slideNumber: 4 },
        ]);
        (component as any).updateCurrentSegment(15);

        expect(component.getCurrentSlideNumber()).toBe(4);
    });

    it('applies initialTimestamp on ready and updates segment immediately', () => {
        fixture.componentRef.setInput('initialTimestamp', 25);
        const updateSpy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        component.onPlayerReady({} as any);
        expect(updateSpy).toHaveBeenCalledWith(25);
    });

    it('seeks when initialTimestamp arrives after the player component exists', async () => {
        const seekSpy = vi.fn();
        fixture.detectChanges();
        await fixture.whenStable();

        (component as any).playerComponent = () => ({ seekTo: seekSpy }) as any;

        fixture.componentRef.setInput('initialTimestamp', 60);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(seekSpy).toHaveBeenCalledWith(60, true);
    });

    it('seeks again when the deeplink timestamp changes', async () => {
        const seekSpy = vi.fn();
        fixture.detectChanges();
        await fixture.whenStable();

        (component as any).playerComponent = () => ({ seekTo: seekSpy }) as any;

        fixture.componentRef.setInput('initialTimestamp', 30);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.componentRef.setInput('initialTimestamp', 60);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(seekSpy).toHaveBeenNthCalledWith(1, 30, true);
        expect(seekSpy).toHaveBeenNthCalledWith(2, 60, true);
    });

    it('resyncs segment index when transcriptSegments arrives after player is ready', async () => {
        // Start with empty segments
        fixture.componentRef.setInput('transcriptSegments', []);
        fixture.detectChanges();
        await fixture.whenStable();

        // Player becomes ready at t=15
        (component as any).playerComponent = () => ({ getCurrentTime: () => 15, seekTo: vi.fn() }) as any;
        component.onPlayerReady({} as any);
        expect(component['currentSegmentIndex']()).toBe(-1); // no segments yet

        // Now segments arrive asynchronously
        fixture.componentRef.setInput('transcriptSegments', [
            { startTime: 0, endTime: 10, text: 'a' },
            { startTime: 10, endTime: 20, text: 'b' },
            { startTime: 20, endTime: 30, text: 'c' },
        ]);
        fixture.detectChanges();
        await fixture.whenStable();

        // Effect should have resynced — player time 15 falls in segment index 1
        expect(component['currentSegmentIndex']()).toBe(1);
    });

    it('guards segment update before ready', () => {
        (component as any).youtubePlayer = null;
        expect(() => component.seekTo(5)).not.toThrow();
    });

    it.each([0, 3])('stops polling on state %s and updates the segment once more', (state) => {
        vi.useFakeTimers();
        const updateSpy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        (component as any).youtubePlayer = { getCurrentTime: () => 25, seekTo: vi.fn() };

        component.onStateChange({ data: 1 } as any);
        vi.advanceTimersByTime(250);
        updateSpy.mockClear();

        component.onStateChange({ data: state } as any);
        vi.advanceTimersByTime(1_000);

        expect(updateSpy).toHaveBeenCalledTimes(1);
        expect(updateSpy).toHaveBeenCalledWith(25);
    });

    it('resetSplitRatio clears custom sizing on the video column', async () => {
        await render();

        const videoColumnEl = component.videoColumn()!.nativeElement;
        videoColumnEl.style.flex = '0 0 65%';
        videoColumnEl.style.width = '650px';

        component.resetSplitRatio();

        expect(videoColumnEl.style.flex).toBe('');
        expect(videoColumnEl.style.width).toBe('');
    });

    it('resizes the video column by dragging the sibling divider handle', async () => {
        await render();

        // The real template renders the handle as a sibling of the video column (resizableHandleOutsideHost), so
        // the directive delegates the pointerdown from the wrapper. This is the production wiring; drive it end to
        // end so the percent -> flex translation is covered (not just resetSplitRatio / the constraints getter).
        const makeRect = (width: number) => ({ left: 0, width, top: 0, right: width, bottom: 500, height: 500, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect;
        const videoColumnEl = component.videoColumn()!.nativeElement;
        const wrapperEl = component.videoWrapper()!.nativeElement;
        vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue(makeRect(1000));
        vi.spyOn(videoColumnEl, 'getBoundingClientRect').mockReturnValue(makeRect(500));
        fixture.detectChanges();

        // Drag the right edge from 500px to 600px within a 1000px wrapper (inside [300, 750]) -> 600 / 1000 = 60%.
        const handleEl = component.resizerHandle()!.nativeElement;
        handleEl.dispatchEvent(new MouseEvent('pointerdown', { clientX: 500, button: 0, bubbles: true }));
        videoColumnEl.dispatchEvent(new MouseEvent('pointermove', { clientX: 600, bubbles: true }));
        videoColumnEl.dispatchEvent(new MouseEvent('pointerup', { clientX: 600, bubbles: true }));

        expect(videoColumnEl.style.flex).toBe('0 0 60%');
        expect(videoColumnEl.style.width).toBe('');
    });

    it('toggles isResizing while the divider is dragged', async () => {
        await render();

        expect(component['isResizing']()).toBe(false);

        component['onResizeStart']();
        expect(component['isResizing']()).toBe(true);

        component['onResizeEnd']();
        expect(component['isResizing']()).toBe(false);
    });

    it('exposes width constraints derived from the live wrapper width', async () => {
        await render();

        const wrapperEl = component.videoWrapper()!.nativeElement;
        vi.spyOn(wrapperEl, 'getBoundingClientRect').mockReturnValue({
            left: 0,
            width: 1000,
            top: 0,
            right: 1000,
            bottom: 0,
            height: 0,
            x: 0,
            y: 0,
            toJSON: () => ({}),
        } as DOMRect);

        // maxWidth = wrapperWidth (1000) - minTranscript (250)
        expect((component as any).resizableConstraints).toEqual({ minWidth: 300, maxWidth: 750 });
    });

    it('disconnects the ResizeObserver and resets isResizing on destroy', async () => {
        await render();

        const observer = (component as any).resizeObserver as MockResizeObserver;
        expect(observer).toBeDefined();
        component['onResizeStart']();

        component.ngOnDestroy();

        expect(observer.disconnect).toHaveBeenCalled();
        expect(component['isResizing']()).toBe(false);
    });

    it('clears timeout on destroy', () => {
        vi.useFakeTimers();
        const emitSpy = vi.spyOn(component.playerFailed, 'emit');
        component.ngAfterViewInit();
        vi.advanceTimersByTime(3_000);
        component.ngOnDestroy();
        vi.advanceTimersByTime(10_000);
        expect(emitSpy).not.toHaveBeenCalled();
        vi.useRealTimers();
    });

    describe('Context provider methods', () => {
        it('getCurrentTime: returns current time from YouTube player', () => {
            const mockPlayer = {
                getCurrentTime: vi.fn().mockReturnValue(125.5),
                seekTo: vi.fn(),
            };
            (component as any).youtubePlayer = mockPlayer;

            const currentTime = component.getCurrentTime();

            expect(currentTime).toBe(125.5);
            expect(mockPlayer.getCurrentTime).toHaveBeenCalledTimes(1);
        });

        it('getCurrentTime: returns undefined when player is not ready', () => {
            (component as any).youtubePlayer = null;

            const currentTime = component.getCurrentTime();

            expect(currentTime).toBeUndefined();
        });

        it('hasBeenPlayed: returns false initially', () => {
            const hasPlayed = component.hasBeenPlayed();

            expect(hasPlayed).toBe(false);
        });

        it('hasBeenPlayed: returns true after video has been played', () => {
            // Simulate onPlayerReady
            const mockPlayer = {
                getCurrentTime: vi.fn().mockReturnValue(0),
                seekTo: vi.fn(),
            };
            component.onPlayerReady({ target: mockPlayer } as any);

            // Simulate onStateChange with PLAYING state
            component.onStateChange({ data: 1 } as any); // YT_STATE_PLAYING = 1

            const hasPlayed = component.hasBeenPlayed();

            expect(hasPlayed).toBe(true);
        });

        it('hasBeenPlayed: resets when new video is loaded', () => {
            // First video plays
            const mockPlayer1 = {
                getCurrentTime: vi.fn().mockReturnValue(0),
                seekTo: vi.fn(),
            };
            component.onPlayerReady({ target: mockPlayer1 } as any);
            component.onStateChange({ data: 1 } as any); // PLAYING

            expect(component.hasBeenPlayed()).toBe(true);

            // New video loads
            const mockPlayer2 = {
                getCurrentTime: vi.fn().mockReturnValue(0),
                seekTo: vi.fn(),
            };
            component.onPlayerReady({ target: mockPlayer2 } as any);

            // Should be reset to false
            expect(component.hasBeenPlayed()).toBe(false);
        });
    });
});
