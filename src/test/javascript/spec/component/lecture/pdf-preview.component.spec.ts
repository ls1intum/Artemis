jest.mock('pdfjs-dist', () => {
    return {
        GlobalWorkerOptions: { workerSrc: '' },
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
    let routeMock: any;
    let alertServiceMock: any;

    beforeEach(async () => {
        global.URL.createObjectURL = jest.fn().mockReturnValue('mocked_blob_url');
        attachmentServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
        };
        attachmentUnitServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
        };
        alertServiceMock = {
            error: jest.fn(),
        };
        routeMock = {
            data: of({
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

    it('should load PDF', async () => {
        component.ngOnInit();
        expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1);
        expect(alertServiceMock.error).not.toHaveBeenCalled();
    });

    it('should display error alert when an invalid attachment ID is provided', async () => {
        routeMock.data = of({ attachment: { id: null, name: 'Invalid PDF' } });
        component.ngOnInit();
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentIDError');
    });

    it('should display error alert when an invalid attachmentUnit ID is provided', async () => {
        routeMock.data = of({ attachmentUnit: { id: null, name: 'Invalid PDF' } });
        component.ngOnInit();
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUnitIDError');
    });

    it('should load and render PDF pages', async () => {
        const mockBlob = new Blob(['PDF content'], { type: 'application/pdf' });

        attachmentServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));
        component.ngOnInit();

        expect(URL.createObjectURL).toHaveBeenCalledWith(mockBlob);
        expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1);
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

        component.closeEnlargedView();
        expect(component.isEnlargedView).toBeFalse();
    });

    it('should prevent scrolling when enlarged view is open', () => {
        component.toggleBodyScroll(true);
        expect(component.pdfContainer.nativeElement.style.overflow).toBe('hidden');

        component.toggleBodyScroll(false);
        expect(component.pdfContainer.nativeElement.style.overflow).toBe('auto');
    });
});
