import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

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
import { TranslateService } from '@ngx-translate/core';

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
    let mockOverlay: HTMLDivElement;

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
            imports: [PdfPreviewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: routeMock },
                { provide: AttachmentService, useValue: attachmentServiceMock },
                { provide: AttachmentUnitService, useValue: attachmentUnitServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
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
        mockOverlay = document.createElement('div');
        mockOverlay.style.opacity = '0';
        mockCanvasElement.appendChild(mockOverlay);

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

    it('should call updateEnlargedCanvas when window is resized and conditions are met', () => {
        component.isEnlargedView = true;
        component.currentPage = 1;

        const canvas = document.createElement('canvas');
        const pdfContainer = document.createElement('div');
        pdfContainer.className = 'pdf-page-container';
        pdfContainer.appendChild(canvas);
        component.pdfContainer = {
            nativeElement: pdfContainer,
        } as ElementRef;

        const updateEnlargedCanvasSpy = jest.spyOn(component, 'updateEnlargedCanvas');
        const adjustCanvasSizeSpy = jest.spyOn(component, 'adjustCanvasSize');

        window.dispatchEvent(new Event('resize'));
        expect(adjustCanvasSizeSpy).toHaveBeenCalled();
        expect(updateEnlargedCanvasSpy).toHaveBeenCalledWith(canvas);
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
        const mockEvent = createMockEvent(mockEnlargedCanvas);
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
        component.redrawCanvas(mockCanvasElement);

        expect(component.enlargedCanvas.nativeElement.width).toBe(1000); // 500 * 2
        expect(component.enlargedCanvas.nativeElement.height).toBe(800); // 400 * 2

        expect(mockContext.clearRect).toHaveBeenCalledWith(0, 0, 1000, 800);
        expect(mockContext.drawImage).toHaveBeenCalledWith(mockCanvasElement, 0, 0, 1000, 800);
    });

    it('should correctly position the canvas', () => {
        const parent = document.createElement('div');
        component.pdfContainer = { nativeElement: { clientWidth: 1000, clientHeight: 800, scrollTop: 500 } } as ElementRef<HTMLDivElement>;
        const canvasElem = component.enlargedCanvas.nativeElement;
        parent.appendChild(canvasElem);
        canvasElem.width = 500;
        canvasElem.height = 400;
        component.positionCanvas();
        expect(canvasElem.style.left).toBe('250px');
        expect(canvasElem.style.top).toBe('200px');
        expect(parent.style.top).toBe('500px');
    });

    it('should create a container with correct styles and children', () => {
        const mockCanvas = document.createElement('canvas');
        mockCanvas.style.width = '600px';
        mockCanvas.style.height = '400px';

        const container = component.createContainer(mockCanvas, 1);
        expect(container.tagName).toBe('DIV');
        expect(container.classList.contains('pdf-page-container')).toBeTruthy();
        expect(container.style.position).toBe('relative');
        expect(container.style.display).toBe('inline-block');
        expect(container.style.width).toBe('600px');
        expect(container.style.height).toBe('400px');
        expect(container.style.margin).toBe('20px');
        expect(container.children).toHaveLength(2);

        expect(container.firstChild).toBe(mockCanvas);
    });

    it('should handle mouseenter and mouseleave events correctly', () => {
        const mockCanvas = document.createElement('canvas');
        const container = component.createContainer(mockCanvas, 1);
        const overlay = container.children[1] as HTMLElement;

        // Trigger mouseenter
        const mouseEnterEvent = new Event('mouseenter');
        container.dispatchEvent(mouseEnterEvent);
        expect(overlay.style.opacity).toBe('1');

        // Trigger mouseleave
        const mouseLeaveEvent = new Event('mouseleave');
        container.dispatchEvent(mouseLeaveEvent);
        expect(overlay.style.opacity).toBe('0');
    });

    it('should handle click event on overlay to trigger displayEnlargedCanvas', () => {
        jest.spyOn(component, 'displayEnlargedCanvas');
        const mockCanvas = document.createElement('canvas');
        const container = component.createContainer(mockCanvas, 1);
        const overlay = container.children[1];

        overlay.dispatchEvent(new Event('click'));
        expect(component.displayEnlargedCanvas).toHaveBeenCalledWith(mockCanvas, 1);
    });
});
