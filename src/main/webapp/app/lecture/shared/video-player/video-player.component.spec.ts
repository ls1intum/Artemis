/**
 * video-player.component.spec.ts
 * Tests for VideoPlayerComponent (video.js + transcript sync)
 *
 * - Mocks `video.js` (works with dynamic import)
 * - Minimal template with <video #videoRef>
 * - Covers init/no-init, timeupdate syncing + scrolling, seeking, and teardown
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

// ---- Imports AFTER the mock ----
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
        vjs.__reset();
        jest.restoreAllMocks();
    });

    function setInputs(url?: string, segments: TranscriptSegment[] = []): void {
        fixture.componentRef.setInput('videoUrl', url);
        fixture.componentRef.setInput('transcriptSegments', segments);
    }

    // Waits for dynamic import + init to finish
    async function render(): Promise<void> {
        fixture.detectChanges();
        await fixture.whenStable(); // wait for microtasks from dynamic import
        await Promise.resolve(); // extra tick just in case
    }

    // Helper to read currentSegmentIndex regardless of signal/getter/number shape
    function getIndex(): number | undefined {
        const val: unknown = (component as unknown as { currentSegmentIndex: unknown }).currentSegmentIndex;
        if (typeof val === 'function') return (val as () => number)();
        if (val && typeof (val as { value: unknown }).value === 'number') return (val as { value: number }).value;
        if (typeof val === 'number') return val as number;
        return undefined;
    }

    it('does not initialize video.js when no videoUrl is provided', async () => {
        setInputs(undefined, []);
        await render();

        expect(MockHlsClass).not.toHaveBeenCalled();
        expect((component as any).hls).toBeNull();
    });

    it('initializes hls.js when videoUrl is provided and hls.js is supported', async () => {
        const url = 'https://cdn.example.com/master.m3u8';
        setInputs(url, []);
        await render();

        expect(MockHlsClass).toHaveBeenCalledOnce();
        expect(MockHlsClass).toHaveBeenCalledWith({
            enableWorker: true,
            lowLatencyMode: false,
        });
        expect(mockHls.loadSource).toHaveBeenCalledWith(url);
        expect(mockHls.attachMedia).toHaveBeenCalledWith(videoElement);
        expect(mockHls.on).toHaveBeenCalledWith('hlsManifestParsed', expect.any(Function));
        expect(mockHls.on).toHaveBeenCalledWith('hlsError', expect.any(Function));
        expect((component as any).hls).toBe(mockHls);
    });

    it('initializes video.js when videoUrl is provided', async () => {
        const url = 'https://cdn.example.com/master.m3u8';
        setInputs(url, []);
        await render();

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
        expect((component as any).player).toBe(vjs.__player);
        expect(vjs.__player.on).toHaveBeenCalledWith('timeupdate', expect.any(Function));
    });

    it('timeupdate sets active segment and scrolls the element into view', async () => {
        const segments: TranscriptSegment[] = [
            { startTime: 10, endTime: 12, text: 'A' },
            { startTime: 20, endTime: 22, text: 'B' },
        ];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

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
        if (handler) handler();

        expect(getIndex()).toBe(0);
        expect(scrollSpy).toHaveBeenCalledOnce();

        // Same time again -> index unchanged, no extra scroll
        if (handler) handler();
        expect(scrollSpy).toHaveBeenCalledOnce();

        getById.mockRestore();
    });

    it('timeupdate outside any segment leaves index at -1 and does not scroll', async () => {
        const segments: TranscriptSegment[] = [{ startTime: 10, endTime: 12, text: 'A' }];
        setInputs('https://cdn.example.com/m.m3u8', segments);
        await render();

        const el = document.createElement('div');
        const scrollSpy = jest.fn();
        Object.defineProperty(el, 'scrollIntoView', { value: scrollSpy, configurable: true });
        const getById = jest.spyOn(document, 'getElementById').mockReturnValue(el as unknown as HTMLElement);

        vjs.__player.currentTime(0); // outside any segment
        const handler = vjs.__player.__handlers.get('timeupdate') as ((...args: unknown[]) => void) | undefined;
        if (handler) handler();

        expect(getIndex()).toBe(-1);
        expect(scrollSpy).not.toHaveBeenCalled();

        getById.mockRestore();
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

        component.seekTo(42);

        expect(vjs.__player.currentTime).toHaveBeenCalledWith(42);
        expect(vjs.__player.play).toHaveBeenCalled();
    });

    it('ngOnDestroy disposes the player', async () => {
        setInputs('https://cdn.example.com/m.m3u8', []);
        await render();

        fixture.destroy();
        expect(vjs.__player.dispose).toHaveBeenCalledOnce();
    });

    // ---- Search Functionality Tests ----

    describe('Search Functionality', () => {
        const testSegments: TranscriptSegment[] = [
            { startTime: 0, endTime: 5, text: 'Hello world' },
            { startTime: 5, endTime: 10, text: 'Welcome to the lecture' },
            { startTime: 10, endTime: 15, text: 'Today we will discuss algorithms' },
            { startTime: 15, endTime: 20, text: 'Hello again, let us start' },
        ];

        it('filteredSegments returns all segments when search is empty', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            expect(component.filteredSegments()).toEqual(testSegments);
            expect(component.searchResultsCount()).toBe(4);
        });

        it('filteredSegments filters segments based on search query (case-insensitive)', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('hello');

            expect(component.filteredSegments()).toHaveLength(2);
            expect(component.filteredSegments()[0].text).toBe('Hello world');
            expect(component.filteredSegments()[1].text).toBe('Hello again, let us start');
            expect(component.searchResultsCount()).toBe(2);
        });

        it('filteredSegments returns empty array when no matches found', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('nonexistent');

            expect(component.filteredSegments()).toHaveLength(0);
            expect(component.searchResultsCount()).toBe(0);
        });

        it('onSearchQueryChange updates search query and resets to first result', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.currentSearchIndex.set(2);
            component.onSearchQueryChange('lecture');

            expect(component.searchQuery()).toBe('lecture');
            expect(component.currentSearchIndex()).toBe(0);
            expect(component.filteredSegments()).toHaveLength(1);
        });

        it('clearSearch resets search query and index', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('hello');
            expect(component.searchQuery()).toBe('hello');

            component.clearSearch();

            expect(component.searchQuery()).toBe('');
            expect(component.currentSearchIndex()).toBe(0);
            expect(component.filteredSegments()).toEqual(testSegments);
        });

        it('nextSearchResult cycles through results', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('hello'); // 2 results

            expect(component.currentSearchIndex()).toBe(0);

            component.nextSearchResult();
            expect(component.currentSearchIndex()).toBe(1);

            component.nextSearchResult(); // wraps around
            expect(component.currentSearchIndex()).toBe(0);
        });

        it('previousSearchResult cycles through results backwards', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('hello'); // 2 results
            component.currentSearchIndex.set(1);

            component.previousSearchResult();
            expect(component.currentSearchIndex()).toBe(0);

            component.previousSearchResult(); // wraps around to last
            expect(component.currentSearchIndex()).toBe(1);
        });

        it('nextSearchResult does nothing when no results', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('xyz');
            component.nextSearchResult();

            expect(component.currentSearchIndex()).toBe(0);
        });

        it('previousSearchResult does nothing when no results', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('xyz');
            component.previousSearchResult();

            expect(component.currentSearchIndex()).toBe(0);
        });

        it('highlightText wraps matches in <mark> tags', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.searchQuery.set('hello');

            const highlighted = component.highlightText('Hello world');
            expect(highlighted).toContain('mark');
            expect(highlighted).toContain('Hello');
            expect(highlighted).toContain('world');
        });

        it('highlightText is case-insensitive', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.searchQuery.set('WORLD');

            const highlighted = component.highlightText('Hello world');
            expect(highlighted).toContain('mark');
            expect(highlighted).toContain('world');
        });

        it('highlightText returns original text when no query', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            const highlighted = component.highlightText('Hello world');
            expect(highlighted).toBe('Hello world');
        });

        it('highlightText escapes regex special characters', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.searchQuery.set('(hello)');

            const highlighted = component.highlightText('(hello) world');
            expect(highlighted).toContain('mark');
            expect(highlighted).toContain('(hello)');
        });

        it('highlightText prevents XSS attacks by escaping HTML in text', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.searchQuery.set('script');

            // Malicious text with script tag
            const maliciousText = '<script>alert("XSS")</script>';
            const highlighted = component.highlightText(maliciousText);

            // Should NOT contain executable script tag
            expect(highlighted).not.toContain('<script>alert');
            // Should contain escaped HTML entities
            expect(highlighted).toContain('&lt;');
            expect(highlighted).toContain('&gt;');
        });

        it('highlightText prevents XSS attacks by escaping HTML in search query', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            // Malicious search query with script tag
            component.searchQuery.set('<img src=x onerror=alert(1)>');

            const highlighted = component.highlightText('Some text with <img src=x onerror=alert(1)> in it');

            // Should NOT contain executable HTML (unescaped angle brackets)
            expect(highlighted).not.toContain('<img');
            expect(highlighted).not.toContain('<script');
            // Should contain escaped HTML entities
            expect(highlighted).toContain('&lt;');
            expect(highlighted).toContain('&gt;');
            // The escaped version should be safe
            const highlightedStr = String(highlighted);
            expect(highlightedStr).toContain('&lt;img');
        });

        it('isCurrentSearchResult returns true for current search result segment', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            component.onSearchQueryChange('hello');
            component.currentSearchIndex.set(0);

            const filtered = component.filteredSegments();
            expect(component.isCurrentSearchResult(filtered[0])).toBeTrue();
            expect(component.isCurrentSearchResult(filtered[1])).toBeFalse();
        });

        it('isCurrentSearchResult returns false when search not active', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            expect(component.isCurrentSearchResult(testSegments[0])).toBeFalse();
        });

        it('isSearchActive returns true when query is not empty', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            expect(component.isSearchActive()).toBeFalse();

            component.searchQuery.set('hello');
            expect(component.isSearchActive()).toBeTrue();

            component.searchQuery.set('');
            expect(component.isSearchActive()).toBeFalse();
        });

        it('scrollToSearchResult scrolls element into view', async () => {
            setInputs('https://cdn.example.com/m.m3u8', testSegments);
            await render();

            const el = document.createElement('div');
            const scrollSpy = jest.fn();
            Object.defineProperty(el, 'scrollIntoView', { value: scrollSpy, configurable: true });

            const getById = jest.spyOn(document, 'getElementById').mockReturnValue(el as unknown as HTMLElement);

            component.onSearchQueryChange('hello');
            component.nextSearchResult();

            expect(scrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });

            getById.mockRestore();
        });
    });
});
