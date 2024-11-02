import { PdfPreviewEnlargedCanvasComponent } from 'app/lecture/pdf-preview/pdf-preview-enlarged-canvas/pdf-preview-enlarged-canvas.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientModule } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { signal } from '@angular/core';

function createMockEvent(target: Element, eventType = 'click'): MouseEvent {
    const event = new MouseEvent(eventType, {
        view: window,
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
        await TestBed.configureTestingModule({
            imports: [PdfPreviewEnlargedCanvasComponent, HttpClientModule],
            providers: [
                { provide: ActivatedRoute, useValue: { data: of({}) } },
                { provide: AlertService, useValue: { error: jest.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewEnlargedCanvasComponent);
        component = fixture.componentInstance;

        mockEnlargedCanvas = document.createElement('canvas');
        component.enlargedCanvas = signal({ nativeElement: mockEnlargedCanvas });

        mockContainer = document.createElement('div');
        fixture.componentRef.setInput('pdfContainer', mockContainer);

        mockCanvasElement = document.createElement('canvas');

        const mockOriginalCanvas = document.createElement('canvas');
        mockOriginalCanvas.id = 'canvas-3';
        fixture.componentRef.setInput('originalCanvas', mockOriginalCanvas);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('Keyboard Navigation', () => {
        it('should navigate through pages using keyboard in enlarged view', () => {
            component.isEnlargedViewOutput.emit(true);
            const mockCanvas = document.createElement('canvas');
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
            const navigateSpy = jest.spyOn(component, 'navigatePages');
            const eventMock = { stopPropagation: jest.fn() } as unknown as MouseEvent;

            component.handleNavigation('next', eventMock);

            expect(eventMock.stopPropagation).toHaveBeenCalled();
            expect(navigateSpy).toHaveBeenCalledWith('next');
        });
    });

    describe('Canvas Rendering', () => {
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
            jest.spyOn(mockContext, 'clearRect');
            jest.spyOn(mockContext, 'drawImage');

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

            const spy = jest.spyOn(component, 'updateEnlargedCanvas');
            component.adjustCanvasSize();

            expect(spy).not.toHaveBeenCalled();
        });
    });

    describe('Enlarged View Management', () => {
        it('should close the enlarged view if click is outside the canvas within the enlarged container', () => {
            const target = document.createElement('div');
            target.classList.add('enlarged-container');
            const mockEvent = createMockEvent(target);

            const closeSpy = jest.fn();
            component.isEnlargedViewOutput.subscribe(closeSpy);

            component.closeIfOutside(mockEvent);

            expect(closeSpy).toHaveBeenCalledWith(false);
        });

        it('should not close the enlarged view if the click is on the canvas itself', () => {
            const mockEvent = createMockEvent(mockEnlargedCanvas);
            component.isEnlargedViewOutput.emit(true);

            const closeSpy = jest.spyOn(component, 'closeEnlargedView');

            component.closeIfOutside(mockEvent as unknown as MouseEvent);
            expect(closeSpy).not.toHaveBeenCalled();
        });
    });
});
