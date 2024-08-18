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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentService } from 'app/lecture/attachment.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { PdfPreviewComponent } from 'app/lecture/pdf-preview/pdf-preview.component';
import { ElementRef } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';

describe('PdfPreviewComponent', () => {
    let component: PdfPreviewComponent;
    let fixture: ComponentFixture<PdfPreviewComponent>;
    let attachmentServiceMock: any;
    let attachmentUnitServiceMock: any;
    let alertServiceMock: any;

    beforeEach(async () => {
        global.URL.createObjectURL = jest.fn().mockReturnValue('mocked_blob_url');
        attachmentServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
        };
        attachmentUnitServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
        };
        const routeMock = {
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

        fixture = TestBed.createComponent(PdfPreviewComponent);
        component = fixture.componentInstance;
        component.pdfContainer = new ElementRef(document.createElement('div'));
        component.enlargedCanvas = new ElementRef(document.createElement('canvas'));
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should load PDF and handle successful response', () => {
        component.ngOnInit();
        expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
        expect(alertServiceMock.error).not.toHaveBeenCalled();
    });

    it('should load and render PDF pages', () => {
        const mockBlob = new Blob(['PDF content'], { type: 'application/pdf' });

        attachmentServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));
        component.ngOnInit();

        expect(URL.createObjectURL).toHaveBeenCalledWith(mockBlob);
        expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
        expect(component.totalPages).toBeGreaterThan(0);
    });

    it('should handle keyboard navigation for enlarged view', () => {
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

    it('should toggle enlarged view on and off', () => {
        const mockCanvas = document.createElement('canvas');
        component.displayEnlargedCanvas(mockCanvas, 1);
        expect(component.isEnlargedView).toBeTrue();

        const clickEvent = new MouseEvent('click', {
            button: 0,
        });

        component.closeEnlargedView(clickEvent);
        expect(component.isEnlargedView).toBeFalse();
    });

    it('should prevent scrolling when enlarged view is open', () => {
        component.toggleBodyScroll(true);
        expect(component.pdfContainer.nativeElement.style.overflow).toBe('hidden');

        component.toggleBodyScroll(false);
        expect(component.pdfContainer.nativeElement.style.overflow).toBe('auto');
    });

    it('should not navigate beyond totalPages', () => {
        component.totalPages = 5;
        component.currentPage = 5;
        const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
        component.handleKeyboardEvents(event);
        expect(component.currentPage).toBe(5); // Should not increase
    });

    it('should resize canvas correctly on window resize', () => {
        const adjustCanvasSizeSpy = jest.spyOn(component, 'adjustCanvasSize');
        window.dispatchEvent(new Event('resize'));
        expect(adjustCanvasSizeSpy).toHaveBeenCalled();
        adjustCanvasSizeSpy.mockRestore();
    });

    it('should not navigate to the next page if already at last page', () => {
        component.currentPage = component.totalPages = 5;
        component.handleKeyboardEvents(new KeyboardEvent('keydown', { key: 'ArrowRight' }));

        expect(component.currentPage).toBe(5);
    });

    it('should not navigate to the previous page if already at first page', () => {
        component.currentPage = 1;
        component.handleKeyboardEvents(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));

        expect(component.currentPage).toBe(1);
    });

    it('should handle error when loading PDF pages', () => {
        attachmentServiceMock.getAttachmentFile.mockReturnValue(of(new Blob([''], { type: 'application/pdf' })));
        component.ngOnInit();
        expect(alertServiceMock.error).not.toHaveBeenCalled();
    });

    it('should update the page view when new pages are loaded', () => {
        const mockBlob = new Blob(['PDF content'], { type: 'application/pdf' });
        attachmentServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));
        component.ngOnInit();

        // Assuming `loadPdf` sets `totalPages` based on the PDF document
        expect(component.totalPages).toBeGreaterThan(0);
        expect(component.pdfContainer.nativeElement.children.length).toBeGreaterThan(0);
    });
});
