import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PdfPreviewEnlargedCanvasComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-enlarged-canvas/pdf-preview-enlarged-canvas.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { signal } from '@angular/core';
import { InteractivityChecker } from '@angular/cdk/a11y';

// Mock canvas 2d context for jsdom
function createMockContext() {
    return {
        clearRect: vi.fn(),
        drawImage: vi.fn(),
        fillRect: vi.fn(),
        fillText: vi.fn(),
        strokeRect: vi.fn(),
        getImageData: vi.fn(),
        putImageData: vi.fn(),
        save: vi.fn(),
        restore: vi.fn(),
        scale: vi.fn(),
        translate: vi.fn(),
        rotate: vi.fn(),
        transform: vi.fn(),
        setTransform: vi.fn(),
        beginPath: vi.fn(),
        closePath: vi.fn(),
        moveTo: vi.fn(),
        lineTo: vi.fn(),
        stroke: vi.fn(),
        fill: vi.fn(),
        arc: vi.fn(),
        rect: vi.fn(),
        clip: vi.fn(),
    };
}

function createMockCanvas(width = 500, height = 400): HTMLCanvasElement {
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const mockContext = createMockContext();
    vi.spyOn(canvas, 'getContext').mockReturnValue(mockContext as unknown as CanvasRenderingContext2D);
    return canvas;
}

function createMockEvent(target: Element, eventType = 'click'): MouseEvent {
    const event = new MouseEvent(eventType, {
        bubbles: true,
        cancelable: true,
    });
    Object.defineProperty(event, 'target', { value: target, writable: false });
    return event;
}

describe('PdfPreviewEnlargedCanvasComponent', () => {
    let component: PdfPreviewEnlargedCanvasComponent;
    let fixture: ComponentFixture<PdfPreviewEnlargedCanvasComponent>;
    let mockCanvasElement: HTMLCanvasElement;
    let mockEnlargedCanvas: HTMLCanvasElement;
    let mockContainer: HTMLDivElement;

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({
            imports: [PdfPreviewEnlargedCanvasComponent],
            providers: [
                { provide: ActivatedRoute, useValue: { data: of({}) } },
                { provide: AlertService, useValue: { error: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        // jsdom has no layout, so CDK's real visibility check would reject every focusable element.
        vi.spyOn(TestBed.inject(InteractivityChecker), 'isVisible').mockReturnValue(true);
        fixture = TestBed.createComponent(PdfPreviewEnlargedCanvasComponent);
        component = fixture.componentInstance;

        mockEnlargedCanvas = createMockCanvas();
        component.enlargedCanvas = signal({ nativeElement: mockEnlargedCanvas });

        mockContainer = document.createElement('div');
        fixture.componentRef.setInput('pdfContainer', mockContainer);

        mockCanvasElement = createMockCanvas();

        const mockOriginalCanvas = createMockCanvas();
        mockOriginalCanvas.id = 'canvas-3';
        fixture.componentRef.setInput('originalCanvas', mockOriginalCanvas);

        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        vi.clearAllTimers();
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    describe('Keyboard Navigation', () => {
        it('moves keyboard focus to the close button when the preview opens', async () => {
            await fixture.whenStable();

            expect(document.activeElement).toBe(fixture.nativeElement.querySelector('button[aria-label="entity.action.close"]'));
        });

        it.each([
            { label: 'Next page', boundary: 3, returnKey: 'ArrowLeft' },
            { label: 'Previous page', boundary: 1, returnKey: 'ArrowRight' },
        ])('keeps keyboard focus in the dialog when $label disappears', async ({ label, boundary, returnKey }) => {
            fixture.componentRef.setInput('totalPages', 3);
            component.currentPage.set(2);
            for (let page = 0; page < 3; page++) {
                const container = document.createElement('div');
                container.className = 'pdf-canvas-container';
                container.append(createMockCanvas());
                mockContainer.append(container);
            }
            fixture.detectChanges();
            await fixture.whenStable();
            const navigation = fixture.nativeElement.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement;
            navigation.focus();
            expect(document.activeElement).toBe(navigation);

            navigation.click();
            fixture.detectChanges();

            expect(component.currentPage()).toBe(boundary);
            expect(navigation.isConnected).toBe(false);
            const close = fixture.nativeElement.querySelector('button[aria-label="entity.action.close"]') as HTMLButtonElement;
            expect(document.activeElement).toBe(close);
            close.dispatchEvent(new KeyboardEvent('keydown', { key: returnKey, bubbles: true }));
            expect(component.currentPage()).toBe(2);
            const closed = vi.fn();
            component.isEnlargedViewOutput.subscribe(closed);
            close.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
            expect(closed).toHaveBeenCalledExactlyOnceWith(false);
        });

        it('closes the enlarged preview with Escape and restores scrolling', () => {
            const closed = vi.fn();
            component.isEnlargedViewOutput.subscribe(closed);
            const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });

            fixture.nativeElement.querySelector('[role="dialog"]').dispatchEvent(event);

            expect(closed).toHaveBeenCalledExactlyOnceWith(false);
            expect(mockContainer.style.overflow).toBe('auto');
            expect(event.defaultPrevented).toBe(true);
        });

        describe('boundary navigation by keyboard', () => {
            it.each([
                { buttonLabel: 'Next page', initialPage: 4, targetPage: 5, key: 'ArrowRight' },
                { buttonLabel: 'Previous page', initialPage: 2, targetPage: 1, key: 'ArrowLeft' },
            ])('keeps focus in the dialog when $buttonLabel disappears', async ({ buttonLabel, initialPage, targetPage, key }) => {
                fixture.componentRef.setInput('totalPages', 5);
                component.currentPage.set(initialPage);
                fixture.detectChanges();
                await fixture.whenStable();
                const host: HTMLElement = fixture.nativeElement;
                const navigationButton = host.querySelector<HTMLButtonElement>(`button[aria-label="${buttonLabel}"]`)!;
                const closeButton = host.querySelector<HTMLButtonElement>('button[aria-label="entity.action.close"]')!;
                navigationButton.focus();
                expect(document.activeElement).toBe(navigationButton);
                const closed = vi.fn();
                component.isEnlargedViewOutput.subscribe(closed);

                navigationButton.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
                fixture.detectChanges();

                expect(component.currentPage()).toBe(targetPage);
                expect(host.contains(navigationButton)).toBe(false);
                expect(document.activeElement).toBe(closeButton);
                document.activeElement?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
                expect(closed).toHaveBeenCalledExactlyOnceWith(false);
                expect(mockContainer.style.overflow).toBe('auto');
            });
        });

        it.each([
            { buttonLabel: 'Previous page', initialPage: 4, targetPage: 5, key: 'ArrowRight' },
            { buttonLabel: 'Next page', initialPage: 2, targetPage: 1, key: 'ArrowLeft' },
        ])('preserves focus on $buttonLabel when it remains available at the boundary', async ({ buttonLabel, initialPage, targetPage, key }) => {
            fixture.componentRef.setInput('totalPages', 5);
            component.currentPage.set(initialPage);
            fixture.detectChanges();
            await fixture.whenStable();
            const host: HTMLElement = fixture.nativeElement;
            const navigationButton = host.querySelector<HTMLButtonElement>(`button[aria-label="${buttonLabel}"]`)!;
            navigationButton.focus();

            navigationButton.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
            fixture.detectChanges();

            expect(component.currentPage()).toBe(targetPage);
            expect(host.contains(navigationButton)).toBe(true);
            expect(document.activeElement).toBe(navigationButton);
        });

        it('keeps page navigation inside the preview instead of handling keys elsewhere on the page', () => {
            fixture.componentRef.setInput('totalPages', 5);
            component.currentPage.set(3);

            document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));

            expect(component.currentPage()).toBe(3);
        });

        it('should navigate through pages using keyboard in enlarged view', () => {
            component.isEnlargedViewOutput.emit(true);
            const mockCanvas = createMockCanvas();
            mockCanvas.id = 'canvas-3';
            fixture.componentRef.setInput('originalCanvas', mockCanvas);
            fixture.componentRef.setInput('totalPages', 5);
            component.currentPage.set(3);

            const eventRight = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            const eventLeft = new KeyboardEvent('keydown', { key: 'ArrowLeft' });

            component.handleKeyboardEvents(eventRight);
            expect(component.currentPage()).toBe(4);

            component.handleKeyboardEvents(eventLeft);
            expect(component.currentPage()).toBe(3);
        });

        it('should prevent navigation beyond last page', () => {
            component.currentPage.set(5);
            fixture.componentRef.setInput('totalPages', 5);
            component.handleKeyboardEvents(new KeyboardEvent('keydown', { key: 'ArrowRight' }));

            expect(component.currentPage()).toBe(5);
        });

        it('should prevent navigation before first page', () => {
            component.currentPage.set(1);
            component.handleKeyboardEvents(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));

            expect(component.currentPage()).toBe(1);
        });

        it('should stop event propagation and navigate pages', () => {
            const navigateSpy = vi.spyOn(component, 'navigatePages');
            const eventMock = { stopPropagation: vi.fn() } as unknown as MouseEvent;

            component.handleNavigation('next', eventMock);

            expect(eventMock.stopPropagation).toHaveBeenCalled();
            expect(navigateSpy).toHaveBeenCalledWith('next');
        });
    });

    describe('Canvas Rendering', () => {
        it.each([
            { width: 500, height: 400, renderedWidth: 1000, containerHeight: '60vh' },
            { width: 400, height: 500, renderedWidth: 640, containerHeight: '800px' },
        ])('renders a $width by $height page after opening and clears the loading indicator', ({ width, height, renderedWidth, containerHeight }) => {
            const original = component.originalCanvas()!;
            original.width = width;
            original.height = height;
            Object.defineProperty(mockContainer, 'clientWidth', { value: 1000, configurable: true });
            Object.defineProperty(mockContainer, 'clientHeight', { value: 800, configurable: true });
            const context = mockEnlargedCanvas.getContext('2d')!;
            expect(component.isEnlargedCanvasLoading()).toBe(true);
            expect(mockContainer.style.overflow).toBe('hidden');

            vi.advanceTimersByTime(516);
            fixture.detectChanges();

            expect(mockEnlargedCanvas.width).toBe(renderedWidth);
            expect(mockEnlargedCanvas.height).toBe(800);
            expect(context.drawImage).toHaveBeenLastCalledWith(original, 0, 0, renderedWidth, 800);
            expect(mockContainer.style.height).toBe(containerHeight);
            expect(component.isEnlargedCanvasLoading()).toBe(false);
            expect(fixture.nativeElement.querySelector('tumaet-ui-progress-spinner')).toBeNull();
        });

        it('redraws the current page when the browser window is resized', () => {
            vi.clearAllTimers();
            Object.defineProperty(mockContainer, 'clientWidth', { value: 750, configurable: true });
            Object.defineProperty(mockContainer, 'clientHeight', { value: 600, configurable: true });
            const pages = [createMockCanvas(), createMockCanvas(), createMockCanvas()];
            for (const page of pages) {
                const pageContainer = document.createElement('div');
                pageContainer.className = 'pdf-canvas-container';
                pageContainer.append(page);
                mockContainer.append(pageContainer);
            }
            component.currentPage.set(3);
            const context = mockEnlargedCanvas.getContext('2d')!;

            window.dispatchEvent(new Event('resize'));
            vi.advanceTimersByTime(16);

            expect(context.drawImage).toHaveBeenLastCalledWith(pages[2], 0, 0, 750, 600);
        });

        it('should calculate the correct scale factor for horizontal slides', () => {
            // Mock container dimensions
            Object.defineProperty(mockContainer, 'clientWidth', { value: 1000, configurable: true });
            Object.defineProperty(mockContainer, 'clientHeight', { value: 800, configurable: true });

            // Mock a horizontal canvas (width > height)
            mockCanvasElement.width = 500;
            mockCanvasElement.height = 400;
            const scaleFactor = component.calculateScaleFactor(mockCanvasElement);

            expect(scaleFactor).toBe(2); // Min of 1000/500 (scaleX) and 800/400 (scaleY)
        });

        it('should calculate the correct scale factor for vertical slides', () => {
            Object.defineProperty(mockContainer, 'clientWidth', { value: 1000, configurable: true });
            Object.defineProperty(mockContainer, 'clientHeight', { value: 800, configurable: true });

            // Mock a vertical canvas (height > width)
            mockCanvasElement.width = 400;
            mockCanvasElement.height = 500;
            const scaleFactor = component.calculateScaleFactor(mockCanvasElement);

            expect(scaleFactor).toBe(1.6); // Min of 1.6 (scaleY) and 2.5 (scaleX)
        });

        it('should resize the canvas based on the given scale factor', () => {
            mockCanvasElement.width = 500;
            mockCanvasElement.height = 400;
            component.resizeCanvas(mockCanvasElement, 2);

            expect(mockEnlargedCanvas.width).toBe(1000);
            expect(mockEnlargedCanvas.height).toBe(800);
        });

        it('should clear and redraw the canvas with the new dimensions', () => {
            mockCanvasElement.width = 500;
            mockCanvasElement.height = 400;

            const mockContext = mockEnlargedCanvas.getContext('2d')!;
            vi.spyOn(mockContext, 'clearRect');
            vi.spyOn(mockContext, 'drawImage');

            component.resizeCanvas(mockCanvasElement, 2);
            component.redrawCanvas(mockCanvasElement);

            expect(mockEnlargedCanvas.width).toBe(1000); // 500 * 2
            expect(mockEnlargedCanvas.height).toBe(800); // 400 * 2
            expect(mockContext.clearRect).toHaveBeenCalledWith(0, 0, 1000, 800);
            expect(mockContext.drawImage).toHaveBeenCalledWith(mockCanvasElement, 0, 0, 1000, 800);
        });
    });

    describe('Layout', () => {
        it('should prevent scrolling when enlarged view is active', () => {
            component.toggleBodyScroll(true);
            expect(mockContainer.style.overflow).toBe('hidden');

            component.toggleBodyScroll(false);
            expect(mockContainer.style.overflow).toBe('auto');
        });

        it('should not update canvas size if not in enlarged view', () => {
            component.isEnlargedViewOutput.emit(false);
            component.currentPage.set(3);

            const spy = vi.spyOn(component, 'updateEnlargedCanvas');
            component.adjustCanvasSize();

            expect(spy).not.toHaveBeenCalled();
        });
    });

    describe('Enlarged View Management', () => {
        it('should close the enlarged view if click is outside the canvas within the enlarged container', () => {
            const target = document.createElement('div');
            target.classList.add('enlarged-container');
            const mockEvent = createMockEvent(target);

            const closeSpy = vi.fn();
            component.isEnlargedViewOutput.subscribe(closeSpy);

            component.closeIfOutside(mockEvent);

            expect(closeSpy).toHaveBeenCalledWith(false);
        });

        it('should not close the enlarged view if the click is on the canvas itself', () => {
            const mockEvent = createMockEvent(mockEnlargedCanvas);
            component.isEnlargedViewOutput.emit(true);

            const closeSpy = vi.spyOn(component, 'closeEnlargedView');

            component.closeIfOutside(mockEvent as unknown as MouseEvent);
            expect(closeSpy).not.toHaveBeenCalled();
        });
    });
});
