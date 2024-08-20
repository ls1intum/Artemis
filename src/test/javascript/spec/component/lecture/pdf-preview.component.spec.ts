jest.mock('pdfjs-dist', () => {
    return {
        getDocument: jest.fn(() => ({
            promise: Promise.resolve({
                numPages: 1,
                getPage: jest.fn(() =>
                    Promise.resolve({
                        getViewport: jest.fn(() => ({ width: 600, height: 800, scale: 1 })),
                        render: jest.fn(() => ({
                            promise: Promise.resolve(),
                        })),
                    }),
                ),
            }),
        })),
    };
});

jest.mock('pdfjs-dist/build/pdf.worker', () => {
    return {};
});

function createMouseEvent(target: Element): MouseEvent {
    return new MouseEvent('click', {
        view: window,
        bubbles: true,
        cancelable: true,
        relatedTarget: target,
    });
}

function createMockEvent(target: Element, eventType = 'click'): MouseEvent {
    const event = new MouseEvent(eventType, {
        view: window,
        bubbles: true,
        cancelable: true,
    });
    Object.defineProperty(event, 'target', { value: target, writable: false });
    return event;
}

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AttachmentService } from 'app/lecture/attachment.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { PdfPreviewComponent } from 'app/lecture/pdf-preview/pdf-preview.component';
import { ElementRef } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';

describe('PdfPreviewComponent', () => {
    let component: PdfPreviewComponent;
    let fixture: ComponentFixture<PdfPreviewComponent>;
    let attachmentServiceMock: any;
    let attachmentUnitServiceMock: any;
    let alertServiceMock: any;
    let routeMock: any;
    let mockCanvasElement: HTMLCanvasElement;
    let mockEnlargedCanvas: HTMLCanvasElement;
    let mockContext: any;

    beforeEach(async () => {
        global.URL.createObjectURL = jest.fn().mockReturnValue('mocked_blob_url');
        attachmentServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
        };
        attachmentUnitServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
        };
        routeMock = {
            data: of({
                course: { id: 1, name: 'Example Course' },
                attachment: { id: 1, name: 'Example PDF' },
                attachmentUnit: { id: 1, name: 'Chapter 1' },
            }),
        };
        alertServiceMock = {
            addAlert: jest.fn(),
            error: jest.fn(),
        };

        await TestBed.configureTestingModule({
            declarations: [PdfPreviewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: routeMock },
                { provide: AttachmentService, useValue: attachmentServiceMock },
                { provide: AttachmentUnitService, useValue: attachmentUnitServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        }).compileComponents();

        const pdfContainerElement = document.createElement('div');
        Object.defineProperty(pdfContainerElement, 'clientWidth', { value: 800 });
        Object.defineProperty(pdfContainerElement, 'clientHeight', { value: 600 });

        fixture = TestBed.createComponent(PdfPreviewComponent);
        component = fixture.componentInstance;

        mockCanvasElement = document.createElement('canvas');
        mockCanvasElement.width = 800;
        mockCanvasElement.height = 600;

        jest.spyOn(component, 'updateEnlargedCanvas').mockImplementation(() => {
            component.enlargedCanvas.nativeElement = mockCanvasElement;
        });

        mockEnlargedCanvas = document.createElement('canvas');
        mockEnlargedCanvas.classList.add('enlarged-canvas');
        component.enlargedCanvas = new ElementRef(mockEnlargedCanvas);

        mockContext = {
            clearRect: jest.fn(),
            drawImage: jest.fn(),
        } as unknown as CanvasRenderingContext2D;
        jest.spyOn(mockCanvasElement, 'getContext').mockReturnValue(mockContext);

        jest.spyOn(window, 'requestAnimationFrame').mockImplementation((cb: FrameRequestCallback) => {
            cb(0);
            return 0;
        });

        fixture.detectChanges();

        component.pdfContainer = new ElementRef(document.createElement('div'));
        component.enlargedCanvas = new ElementRef(document.createElement('canvas'));
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should load attachment file and verify service calls when attachment data is available', () => {
        component.ngOnInit();
        expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
        expect(attachmentUnitServiceMock.getAttachmentFile).not.toHaveBeenCalled();
    });

    it('should load attachment unit file and verify service calls when attachment unit data is available', () => {
        routeMock.data = of({
            course: { id: 1, name: 'Example Course' },
            attachmentUnit: { id: 1, name: 'Chapter 1' },
        });
        component.ngOnInit();
        expect(attachmentUnitServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
        expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalled();
    });

    it('should handle errors and trigger alert when loading an attachment file fails', () => {
        const errorResponse = new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
            error: 'File not found',
        });

        const attachmentService = TestBed.inject(AttachmentService);
        jest.spyOn(attachmentService, 'getAttachmentFile').mockReturnValue(throwError(() => errorResponse));
        const alertServiceSpy = jest.spyOn(alertServiceMock, 'error');

        component.ngOnInit();
        fixture.detectChanges();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should handle errors and trigger alert when loading an attachment unit file fails', () => {
        routeMock.data = of({
            course: { id: 1, name: 'Example Course' },
            attachmentUnit: { id: 1, name: 'Chapter 1' },
        });
        const errorResponse = new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
            error: 'File not found',
        });

        const attachmentUnitService = TestBed.inject(AttachmentUnitService);
        jest.spyOn(attachmentUnitService, 'getAttachmentFile').mockReturnValue(throwError(() => errorResponse));
        const alertServiceSpy = jest.spyOn(alertServiceMock, 'error');

        component.ngOnInit();
        fixture.detectChanges();

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should load PDF and verify rendering of pages', () => {
        const mockBlob = new Blob(['PDF content'], { type: 'application/pdf' });

        attachmentServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));
        component.ngOnInit();

        expect(URL.createObjectURL).toHaveBeenCalledWith(mockBlob);
        expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
        expect(component.totalPages).toBeGreaterThan(0);
    });

    it('should navigate through pages using keyboard in enlarged view', () => {
        component.isEnlargedView = true;
        component.totalPages = 5;
        component.currentPage = 3;

        const eventRight = new KeyboardEvent('keydown', { key: 'ArrowRight' });
        const eventLeft = new KeyboardEvent('keydown', { key: 'ArrowLeft' });

        component.handleKeyboardEvents(eventRight);
        expect(component.currentPage).toBe(4);

        component.handleKeyboardEvents(eventLeft);
        expect(component.currentPage).toBe(3);
    });

    it('should toggle enlarged view state', () => {
        const mockCanvas = document.createElement('canvas');
        component.displayEnlargedCanvas(mockCanvas, 1);
        expect(component.isEnlargedView).toBeTrue();

        const clickEvent = new MouseEvent('click', {
            button: 0,
        });

        component.closeEnlargedView(clickEvent);
        expect(component.isEnlargedView).toBeFalse();
    });

    it('should prevent scrolling when enlarged view is active', () => {
        component.toggleBodyScroll(true);
        expect(component.pdfContainer.nativeElement.style.overflow).toBe('hidden');

        component.toggleBodyScroll(false);
        expect(component.pdfContainer.nativeElement.style.overflow).toBe('auto');
    });

    it('should not update canvas size if not in enlarged view', () => {
        component.isEnlargedView = false;
        component.currentPage = 3;

        const spy = jest.spyOn(component, 'updateEnlargedCanvas');
        component.adjustCanvasSize();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should not update canvas size if the current page canvas does not exist', () => {
        component.isEnlargedView = true;
        component.currentPage = 10;

        const spy = jest.spyOn(component, 'updateEnlargedCanvas');
        component.adjustCanvasSize();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should prevent navigation beyond last page', () => {
        component.currentPage = component.totalPages = 5;
        component.handleKeyboardEvents(new KeyboardEvent('keydown', { key: 'ArrowRight' }));

        expect(component.currentPage).toBe(5);
    });

    it('should prevent navigation before first page', () => {
        component.currentPage = 1;
        component.handleKeyboardEvents(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));

        expect(component.currentPage).toBe(1);
    });

    it('should unsubscribe attachment subscription during component destruction', () => {
        const spySub = jest.spyOn(component.attachmentSub, 'unsubscribe');
        component.ngOnDestroy();
        expect(spySub).toHaveBeenCalled();
    });

    it('should unsubscribe attachmentUnit subscription during component destruction', () => {
        routeMock.data = of({
            course: { id: 1, name: 'Example Course' },
            attachmentUnit: { id: 1, name: 'Chapter 1' },
        });
        component.ngOnInit();
        fixture.detectChanges();
        expect(component.attachmentUnitSub).toBeDefined();
        const spySub = jest.spyOn(component.attachmentUnitSub, 'unsubscribe');
        component.ngOnDestroy();
        expect(spySub).toHaveBeenCalled();
    });

    it('should stop event propagation and navigate pages', () => {
        const navigateSpy = jest.spyOn(component, 'navigatePages');
        const eventMock = { stopPropagation: jest.fn() } as unknown as MouseEvent;

        component.handleNavigation('next', eventMock);

        expect(eventMock.stopPropagation).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalledWith('next');
    });

    it('should call adjustCanvasSize when window is resized', () => {
        const adjustCanvasSizeSpy = jest.spyOn(component, 'adjustCanvasSize');
        window.dispatchEvent(new Event('resize'));
        expect(adjustCanvasSizeSpy).toHaveBeenCalled();
    });

    it('should close the enlarged view if click is outside the canvas within the enlarged container', () => {
        const target = document.createElement('div');
        target.classList.add('enlarged-container');
        const mockEvent = createMockEvent(target);

        component.isEnlargedView = true;
        const closeSpy = jest.spyOn(component, 'closeEnlargedView');

        component.closeIfOutside(mockEvent);

        expect(closeSpy).toHaveBeenCalled();
        expect(component.isEnlargedView).toBeFalse();
    });

    it('should not close the enlarged view if the click is on the canvas itself', () => {
        const mockEvent = createMouseEvent(mockEnlargedCanvas);
        Object.defineProperty(mockEvent, 'target', { value: mockEnlargedCanvas, writable: false });

        component.isEnlargedView = true;

        const closeSpy = jest.spyOn(component, 'closeEnlargedView');

        component.closeIfOutside(mockEvent as unknown as MouseEvent);

        expect(closeSpy).not.toHaveBeenCalled();
    });

    it('should calculate the correct scale factor based on container and canvas dimensions', () => {
        Object.defineProperty(component.pdfContainer.nativeElement, 'clientWidth', { value: 1000, configurable: true });
        Object.defineProperty(component.pdfContainer.nativeElement, 'clientHeight', { value: 800, configurable: true });

        mockCanvasElement.width = 500;
        mockCanvasElement.height = 400;

        const scaleFactor = component.calculateScaleFactor(mockCanvasElement);
        expect(scaleFactor).toBe(2);
    });

    it('should resize the canvas based on the given scale factor', () => {
        mockCanvasElement.width = 500;
        mockCanvasElement.height = 400;
        component.resizeCanvas(mockCanvasElement, 2);

        expect(component.enlargedCanvas.nativeElement.width).toBe(1000);
        expect(component.enlargedCanvas.nativeElement.height).toBe(800);
    });

    it('should clear and redraw the canvas with the new dimensions', () => {
        mockCanvasElement.width = 500;
        mockCanvasElement.height = 400;

        jest.spyOn(mockContext, 'clearRect');
        jest.spyOn(mockContext, 'drawImage');

        component.resizeCanvas(mockCanvasElement, 2);
        component.redrawCanvas(mockCanvasElement, 2);

        expect(component.enlargedCanvas.nativeElement.width).toBe(1000); // 500 * 2
        expect(component.enlargedCanvas.nativeElement.height).toBe(800); // 400 * 2

        expect(mockContext.clearRect).toHaveBeenCalledWith(0, 0, 1000, 800);
        expect(mockContext.drawImage).toHaveBeenCalledWith(mockCanvasElement, 0, 0, 1000, 800);
    });
});
