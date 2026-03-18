import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PdfViewerComponent>;
    let component: PdfViewerComponent;

    const setupViewerDom = ({
        containerWidth = 200,
        containerHeight = 300,
        scrollWidth = 800,
        scrollHeight = 600,
        pageWidth = 200,
    }: {
        containerWidth?: number;
        containerHeight?: number;
        scrollWidth?: number;
        scrollHeight?: number;
        pageWidth?: number;
    } = {}) => {
        const host = document.createElement('div');
        const container = document.createElement('div');
        container.id = 'viewerContainer';
        Object.defineProperty(container, 'clientWidth', { value: containerWidth, configurable: true });
        Object.defineProperty(container, 'clientHeight', { value: containerHeight, configurable: true });
        Object.defineProperty(container, 'scrollWidth', { value: scrollWidth, configurable: true });
        Object.defineProperty(container, 'scrollHeight', { value: scrollHeight, configurable: true });
        container.scrollLeft = 0;
        container.scrollTop = 0;
        host.appendChild(container);

        const page = document.createElement('div');
        page.className = 'page';
        page.getBoundingClientRect = () => ({ width: pageWidth }) as DOMRect;
        host.appendChild(page);

        (component as any).viewerHost = () => ({ nativeElement: host });

        return { host, container, page };
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: TranslateService,
                    useValue: {
                        get: (key: string) => of(key),
                        instant: (key: string) => key,
                        onLangChange: of({}),
                        onTranslationChange: of({}),
                        onDefaultLangChange: of({}),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('pdfUrl', 'blob:http://localhost/test.pdf');
        fixture.changeDetectorRef.detach();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should have required inputs', () => {
        expect(component.pdfUrl()).toBe('blob:http://localhost/test.pdf');
    });

    it('should initialize with loading state', () => {
        expect(component.isLoading()).toBe(true);
        expect(component.error()).toBeUndefined();
        expect(component.totalPages()).toBe(0);
        expect(component.currentPage()).toBe(1);
        expect(component.zoomLevel()).toBe(1.0);
    });

    it('should accept optional inputs', () => {
        const uploadDate = dayjs();
        fixture.componentRef.setInput('uploadDate', uploadDate);
        fixture.componentRef.setInput('version', 2);
        fixture.componentRef.setInput('initialPage', 3);

        expect(component.uploadDate()).toBe(uploadDate);
        expect(component.version()).toBe(2);
        expect(component.initialPage()).toBe(3);
    });

    describe('zoom controls', () => {
        it('should zoom in, out and reset zoom level', () => {
            const initialZoom = component.zoomLevel();
            component.zoomIn();
            expect(component.zoomLevel()).toBe(initialZoom + 0.25);

            component.zoomOut();
            expect(component.zoomLevel()).toBe(initialZoom);

            component.zoomLevel.set(2.0);
            component.resetZoom();
            expect(component.zoomLevel()).toBe(1.0);
        });

        it.each([
            { start: 2.9, method: 'zoomIn', expected: 3.0 },
            { start: 0.6, method: 'zoomOut', expected: 0.5 },
        ])('should clamp zoom at boundaries ($method)', ({ start, method, expected }) => {
            component.zoomLevel.set(start);
            (component as any)[method]();
            expect(component.zoomLevel()).toBe(expected);
        });
    });

    describe('pdf loading events', () => {
        it('should reset state when loading starts', () => {
            component.isLoading.set(false);
            component.error.set('error');
            component.totalPages.set(5);
            component.currentPage.set(3);

            component.onPdfLoadingStarts();

            expect(component.isLoading()).toBe(true);
            expect(component.error()).toBeUndefined();
            expect(component.totalPages()).toBe(0);
            expect(component.currentPage()).toBe(1);
        });

        it('should update state when PDF loads', () => {
            component.isLoading.set(true);
            component.error.set('error');

            component.onPdfLoaded({ pagesCount: 7 });

            expect(component.isLoading()).toBe(false);
            expect(component.error()).toBeUndefined();
            expect(component.totalPages()).toBe(7);
        });

        it('should set error when loading fails', () => {
            component.isLoading.set(true);

            component.onPdfLoadingFailed();

            expect(component.isLoading()).toBe(false);
            expect(component.error()).toBe('error');
        });
    });

    it('should apply initial page after load', () => {
        fixture.componentRef.setInput('initialPage', 4);

        component.onPdfLoaded({ pagesCount: 10 });

        expect(component.currentPage()).toBe(4);
    });

    it('should clamp initial page to total pages', () => {
        fixture.componentRef.setInput('initialPage', 12);

        component.onPdfLoaded({ pagesCount: 5 });

        expect(component.currentPage()).toBe(5);
    });

    it('should update current page on page change', () => {
        component.onPageChange(3);
        expect(component.currentPage()).toBe(3);
    });

    it('should track zoom factor changes', () => {
        component.onZoomFactorChange(1.5);
        expect(component.zoomLevel()).toBe(1.5);

        component.onZoomFactorChange(10);
        expect(component.zoomLevel()).toBe(3.0);
    });

    it('should only show the toolbar after pages are loaded', () => {
        component.isLoading.set(true);
        component.totalPages.set(2);
        expect(component.showToolbar()).toBe(false);

        component.isLoading.set(false);
        expect(component.showToolbar()).toBe(true);
    });

    it('should ignore non-finite zoom factors', () => {
        component.zoomLevel.set(1.25);
        component.onZoomFactorChange(Number.NaN);
        expect(component.zoomLevel()).toBe(1.25);
    });

    it('should update fit width zoom factor based on container width', () => {
        setupViewerDom({ containerWidth: 200, pageWidth: 200 });
        expect((component as any).ensureBasePageWidth(2)).toBe(true);
        component.fitWidthZoomFactor.set(1.0);

        (component as any).updateFitWidthZoomFactor();

        expect(component.fitWidthZoomFactor()).toBeCloseTo(2.0, 3);
    });

    it('should apply zoom anchor when fit width remains stable', () => {
        const { container } = setupViewerDom({ containerWidth: 200, containerHeight: 300, scrollWidth: 1000, scrollHeight: 800, pageWidth: 200 });
        const rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback: FrameRequestCallback) => {
            callback(0);
            return 0;
        });
        (component as any).basePageWidth = 100;
        component.fitWidthZoomFactor.set(2.0);
        (component as any).pendingZoomAnchor = { centerXRatio: 0.5, centerYRatio: 0.5 };

        (component as any).updateFitWidthZoomFactor();

        expect(container.scrollLeft).toBe(400);
        expect(container.scrollTop).toBe(250);
        expect((component as any).pendingZoomAnchor).toBeUndefined();
        rafSpy.mockRestore();
    });

    it('should schedule fit width updates on host resize', () => {
        vi.useFakeTimers();
        setupViewerDom({ containerWidth: 200, pageWidth: 200 });
        const updateSpy = vi.spyOn(component as any, 'updateFitWidthZoomFactor');

        (component as any).handleViewerHostResize([{ contentRect: { width: 0 } }] as ResizeObserverEntry[]);
        (component as any).handleViewerHostResize([{ contentRect: { width: 200 } }] as ResizeObserverEntry[]);
        (component as any).handleViewerHostResize([{ contentRect: { width: 200.5 } }] as ResizeObserverEntry[]);
        (component as any).handleViewerHostResize([{ contentRect: { width: 240 } }] as ResizeObserverEntry[]);

        vi.advanceTimersByTime(130);
        expect(updateSpy).toHaveBeenCalled();
        updateSpy.mockRestore();
        vi.useRealTimers();
    });

    it('should manage pinch zoom and viewer zoom binding', () => {
        vi.useFakeTimers();
        component.zoomLevel.set(1.2);
        component.fitWidthZoomFactor.set(1.5);
        expect(component.viewerZoomBinding()).toBe(180);

        (component as any).markPinchZoomActive();
        expect(component.viewerZoomBinding()).toBeUndefined();

        vi.advanceTimersByTime(160);
        expect(component.viewerZoomBinding()).toBe(180);
    });

    it('should complete zoom apply after timers settle', () => {
        vi.useFakeTimers();
        (component as any).beginZoomApply();
        expect((component as any).isApplyingZoom).toBe(true);

        vi.advanceTimersByTime(300);
        expect((component as any).isApplyingZoom).toBe(false);
    });

    it('should settle zoom apply early when zoom factor matches', () => {
        vi.useFakeTimers();
        (component as any).beginZoomApply();
        (component as any).finishZoomApplyIfSettled(1.0, 1.0);
        expect((component as any).isApplyingZoom).toBe(false);
    });

    it('should stop browser zoom shortcuts', () => {
        const event = {
            ctrlKey: true,
            metaKey: false,
            key: '+',
            code: 'Equal',
            stopImmediatePropagation: vi.fn(),
            stopPropagation: vi.fn(),
        } as unknown as KeyboardEvent;

        (component as any).handleBrowserZoomKeys(event);

        expect(event.stopImmediatePropagation).toHaveBeenCalled();
        expect(event.stopPropagation).toHaveBeenCalled();
    });

    it('should mark pinch zoom active on wheel and gesture', () => {
        const pinchSpy = vi.spyOn(component as any, 'markPinchZoomActive');
        const wheelEvent = { ctrlKey: true, metaKey: false } as WheelEvent;

        (component as any).handlePinchZoomWheel(wheelEvent);
        (component as any).handlePinchZoomGesture();

        expect(pinchSpy).toHaveBeenCalledTimes(2);
    });

    it('should wire and clean up viewer host observers', () => {
        const observeSpy = vi.fn();
        const disconnectSpy = vi.fn();
        const originalResizeObserver = window.ResizeObserver;
        class ResizeObserverMock {
            constructor(_callback: ResizeObserverCallback) {}
            observe = observeSpy;
            disconnect = disconnectSpy;
        }
        window.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;

        const addSpy = vi.spyOn(HTMLElement.prototype, 'addEventListener');
        const removeSpy = vi.spyOn(HTMLElement.prototype, 'removeEventListener');

        try {
            fixture.changeDetectorRef.reattach();
            fixture.detectChanges();
            fixture.destroy();

            expect(observeSpy).toHaveBeenCalled();
            expect(addSpy).toHaveBeenCalledWith('wheel', expect.any(Function), expect.objectContaining({ capture: true, passive: true }));
            expect(removeSpy).toHaveBeenCalledWith('wheel', expect.any(Function), expect.objectContaining({ capture: true }));
            expect(disconnectSpy).toHaveBeenCalled();
        } finally {
            addSpy.mockRestore();
            removeSpy.mockRestore();
            window.ResizeObserver = originalResizeObserver;
        }
    });

    it('should update fit width on first zoom factor change', () => {
        setupViewerDom({ containerWidth: 200, pageWidth: 200 });
        const updateSpy = vi.spyOn(component as any, 'updateFitWidthZoomFactor');

        component.onZoomFactorChange(2.0);

        expect(updateSpy).toHaveBeenCalled();
    });
});
