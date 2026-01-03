import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { convertRenderedSVGToPNG } from './svg-renderer';
import { SVG } from '@ls1intum/apollon';

describe('SVG Renderer', () => {
    let mockImageInstance: {
        width: number;
        height: number;
        src: string;
        onload: (() => void) | null;
        onerror: ((error: Event | string) => void) | null;
    };
    let mockCanvas: HTMLCanvasElement;
    let mockContext: CanvasRenderingContext2D;

    beforeEach(() => {
        // Mock Canvas context
        mockContext = {
            scale: vi.fn(),
            drawImage: vi.fn(),
        } as unknown as CanvasRenderingContext2D;

        // Mock Canvas
        mockCanvas = {
            style: { width: '', height: '' },
            width: 0,
            height: 0,
            getContext: vi.fn().mockReturnValue(mockContext),
            toBlob: vi.fn((callback: (blob: Blob | null) => void) => {
                callback(new Blob(['test'], { type: 'image/png' }));
            }),
        } as unknown as HTMLCanvasElement;

        // Mock document.createElement
        vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
            if (tagName === 'canvas') {
                return mockCanvas;
            }
            return document.createElementNS('http://www.w3.org/1999/xhtml', tagName);
        });

        // Create a mock image instance that will capture onload/onerror
        mockImageInstance = {
            width: 0,
            height: 0,
            src: '',
            onload: null,
            onerror: null,
        };

        // Mock Image constructor using a class
        vi.stubGlobal(
            'Image',
            class MockImage {
                width = 0;
                height = 0;
                src = '';
                onload: (() => void) | null = null;
                onerror: ((error: Event | string) => void) | null = null;

                constructor() {
                    // Copy reference to the shared instance for test access
                    Object.assign(mockImageInstance, this);
                    // Keep sync between mockImageInstance and this instance using arrow functions
                    // Arrow functions capture 'this' lexically from the enclosing scope
                    Object.defineProperty(mockImageInstance, 'onload', {
                        get: () => this.onload,
                        set: (value) => {
                            this.onload = value;
                        },
                        configurable: true,
                    });
                    Object.defineProperty(mockImageInstance, 'onerror', {
                        get: () => this.onerror,
                        set: (value) => {
                            this.onerror = value;
                        },
                        configurable: true,
                    });
                    Object.defineProperty(mockImageInstance, 'src', {
                        get: () => this.src,
                        set: (value) => {
                            this.src = value;
                        },
                        configurable: true,
                    });
                    Object.defineProperty(mockImageInstance, 'width', {
                        get: () => this.width,
                        set: (value) => {
                            this.width = value;
                        },
                        configurable: true,
                    });
                    Object.defineProperty(mockImageInstance, 'height', {
                        get: () => this.height,
                        set: (value) => {
                            this.height = value;
                        },
                        configurable: true,
                    });
                }
            },
        );

        // Mock URL.createObjectURL
        vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:test-url');
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('should convert SVG to PNG using toBlob', async () => {
        const renderedSVG: SVG = {
            svg: '<svg></svg>',
            clip: { width: 100, height: 200, x: 0, y: 0 },
        };

        const promise = convertRenderedSVGToPNG(renderedSVG);

        // Wait for the src to be set, then trigger onload
        await vi.waitFor(() => {
            expect(mockImageInstance.src).toBe('blob:test-url');
        });

        // Simulate image load
        mockImageInstance.onload!();

        const result = await promise;

        expect(result).toBeInstanceOf(Blob);
        expect(mockCanvas.getContext).toHaveBeenCalledWith('2d');
        expect(mockContext.scale).toHaveBeenCalledWith(1.5, 1.5);
        expect(mockContext.drawImage).toHaveBeenCalled();
    });

    it('should set image dimensions from SVG clip', async () => {
        const renderedSVG: SVG = {
            svg: '<svg width="300" height="400"></svg>',
            clip: { width: 300, height: 400, x: 0, y: 0 },
        };

        const promise = convertRenderedSVGToPNG(renderedSVG);

        await vi.waitFor(() => {
            expect(mockImageInstance.src).toBeTruthy();
        });

        mockImageInstance.onload!();

        await promise;

        expect(mockImageInstance.width).toBe(300);
        expect(mockImageInstance.height).toBe(400);
    });

    it('should set canvas dimensions with scale factor', async () => {
        const renderedSVG: SVG = {
            svg: '<svg></svg>',
            clip: { width: 100, height: 200, x: 0, y: 0 },
        };

        const promise = convertRenderedSVGToPNG(renderedSVG);

        await vi.waitFor(() => {
            expect(mockImageInstance.src).toBeTruthy();
        });

        mockImageInstance.onload!();

        await promise;

        expect(mockCanvas.width).toBe(150); // 100 * 1.5
        expect(mockCanvas.height).toBe(300); // 200 * 1.5
    });

    it('should reject on image load error', async () => {
        const renderedSVG: SVG = {
            svg: '<svg></svg>',
            clip: { width: 100, height: 200, x: 0, y: 0 },
        };

        const promise = convertRenderedSVGToPNG(renderedSVG);

        await vi.waitFor(() => {
            expect(mockImageInstance.src).toBeTruthy();
        });

        // Simulate image load error
        const errorEvent = new Error('Image load failed');
        mockImageInstance.onerror!(errorEvent as unknown as Event);

        await expect(promise).rejects.toEqual(errorEvent);
    });

    it('should create blob URL from SVG content', async () => {
        const svgContent = '<svg xmlns="http://www.w3.org/2000/svg"><rect/></svg>';
        const renderedSVG: SVG = {
            svg: svgContent,
            clip: { width: 100, height: 100, x: 0, y: 0 },
        };

        const promise = convertRenderedSVGToPNG(renderedSVG);

        await vi.waitFor(() => {
            expect(mockImageInstance.src).toBeTruthy();
        });

        mockImageInstance.onload!();

        await promise;

        expect(URL.createObjectURL).toHaveBeenCalled();
        expect(mockImageInstance.src).toBe('blob:test-url');
    });

    it('should fall back to toDataURL when toBlob is not available', async () => {
        vi.useFakeTimers();

        // Override canvas mock to not have toBlob
        const canvasWithoutToBlob = {
            ...mockCanvas,
            toBlob: undefined,
            toDataURL: vi.fn().mockReturnValue('data:image/png;base64,dGVzdA=='), // 'test' in base64
        } as unknown as HTMLCanvasElement;

        vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
            if (tagName === 'canvas') {
                return canvasWithoutToBlob;
            }
            return document.createElementNS('http://www.w3.org/1999/xhtml', tagName);
        });

        const renderedSVG: SVG = {
            svg: '<svg></svg>',
            clip: { width: 100, height: 200, x: 0, y: 0 },
        };

        const promise = convertRenderedSVGToPNG(renderedSVG);

        // Wait for src to be set
        await vi.waitFor(() => {
            expect(mockImageInstance.src).toBeTruthy();
        });

        mockImageInstance.onload!();

        // Advance timers for the setTimeout in the fallback
        await vi.runAllTimersAsync();

        const result = await promise;

        expect(result).toBeInstanceOf(Blob);
        expect(canvasWithoutToBlob.toDataURL).toHaveBeenCalled();

        vi.useRealTimers();
    });
});
