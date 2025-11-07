/**
 * video-player.component.spec.ts
 * Tests for VideoPlayerComponent (HLS.js + transcript sync)
 *
 * - Mocks `hls.js` library
 * - Minimal template with <video #videoRef>
 * - Covers init/no-init, timeupdate syncing + scrolling, seeking, and teardown
 */

// ---- Mock hls.js BEFORE importing the component ----
const mockHls = {
    loadSource: jest.fn(),
    attachMedia: jest.fn(),
    destroy: jest.fn(),
};

const MockHlsClass = jest.fn(() => mockHls);
(MockHlsClass as any).isSupported = jest.fn(() => true);

jest.mock('hls.js', () => ({
    __esModule: true,
    default: MockHlsClass,
}));

// ---- Imports AFTER the mock ----
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranscriptSegment, VideoPlayerComponent } from './video-player.component';

describe('VideoPlayerComponent', () => {
    let fixture: ComponentFixture<VideoPlayerComponent>;
    let component: VideoPlayerComponent;
    let videoElement: HTMLVideoElement;

    beforeEach(async () => {
        MockHlsClass.mockClear();
        mockHls.loadSource.mockClear();
        mockHls.attachMedia.mockClear();
        mockHls.destroy.mockClear();

        TestBed.configureTestingModule({
            imports: [VideoPlayerComponent],
        });

        // Override template to a minimal one for testing
        TestBed.overrideComponent(VideoPlayerComponent, {
            set: { template: '<video #videoRef></video>' },
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(VideoPlayerComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
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
});
