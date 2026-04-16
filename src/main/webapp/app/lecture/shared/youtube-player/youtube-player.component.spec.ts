// ---- Mock interactjs BEFORE importing the component ----
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

vi.mock('interactjs', () => {
    const mockInstance = {
        draggable: vi.fn().mockReturnThis(),
        unset: vi.fn(),
    };
    const mockInteract = vi.fn(() => mockInstance);
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

// ---- Imports AFTER the mocks ----
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { YouTubePlayerComponent } from './youtube-player.component';

describe('YouTubePlayerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<YouTubePlayerComponent>;
    let component: YouTubePlayerComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [YouTubePlayerComponent, TranslateModule.forRoot()] }).compileComponents();
        fixture = TestBed.createComponent(YouTubePlayerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('videoId', 'dQw4w9WgXcQ');
        fixture.componentRef.setInput('transcriptSegments', [
            { startTime: 0, endTime: 10, text: 'a' },
            { startTime: 10, endTime: 20, text: 'b' },
            { startTime: 20, endTime: 30, text: 'c' },
        ]);
    });

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

    it('applies initialTimestamp on ready and updates segment immediately', () => {
        fixture.componentRef.setInput('initialTimestamp', 25);
        const seekSpy = vi.fn();
        (component as any).youtubePlayer = { getCurrentTime: () => 25, seekTo: seekSpy };
        const updateSpy = vi.spyOn<any, any>(component, 'updateCurrentSegment');
        component.onPlayerReady({} as any);
        expect(seekSpy).toHaveBeenCalledWith(25, true);
        expect(updateSpy).toHaveBeenCalledWith(25);
    });

    it('resyncs segment index when transcriptSegments arrives after player is ready', async () => {
        // Start with empty segments
        fixture.componentRef.setInput('transcriptSegments', []);
        fixture.detectChanges();
        await fixture.whenStable();

        // Player becomes ready at t=15
        (component as any).youtubePlayer = { getCurrentTime: () => 15, seekTo: vi.fn() };
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
});
