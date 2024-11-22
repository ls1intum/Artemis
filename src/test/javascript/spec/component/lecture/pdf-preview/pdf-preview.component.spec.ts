import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AttachmentService } from 'app/lecture/attachment.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { PdfPreviewComponent } from 'app/lecture/pdf-preview/pdf-preview.component';
import { signal } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClientModule, HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PDFDocument } from 'pdf-lib';

jest.mock('pdf-lib', () => {
    const originalModule = jest.requireActual('pdf-lib');

    return {
        ...originalModule,
        PDFDocument: {
            ...originalModule.PDFDocument,
            load: jest.fn(),
            create: jest.fn(),
            prototype: {
                removePage: jest.fn(),
                save: jest.fn(),
            },
        },
    };
});

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

describe('PdfPreviewComponent', () => {
    let component: PdfPreviewComponent;
    let fixture: ComponentFixture<PdfPreviewComponent>;
    let attachmentServiceMock: any;
    let attachmentUnitServiceMock: any;
    let lectureUnitServiceMock: any;
    let alertServiceMock: any;
    let routeMock: any;
    let routerNavigateSpy: any;

    beforeEach(async () => {
        attachmentServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
            update: jest.fn().mockReturnValue(of({})),
            delete: jest.fn().mockReturnValue(of({})),
            create: jest.fn().mockReturnValue(of({})),
            getAttachmentByParentAttachmentId: jest.fn(),
        };
        attachmentUnitServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
            update: jest.fn().mockReturnValue(of({})),
            delete: jest.fn().mockReturnValue(of({})),
            getHiddenSlides: jest.fn(),
        };
        lectureUnitServiceMock = {
            delete: jest.fn().mockReturnValue(of({})),
        };
        routeMock = {
            data: of({
                course: { id: 1, name: 'Example Course' },
                attachment: { id: 1, name: 'Example PDF', lecture: { id: 1 } },
                attachmentUnit: { id: 1, name: 'Chapter 1', lecture: { id: 1 } },
            }),
        };
        alertServiceMock = {
            addAlert: jest.fn(),
            error: jest.fn(),
            success: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewComponent, HttpClientModule],
            providers: [
                { provide: ActivatedRoute, useValue: routeMock },
                { provide: AttachmentService, useValue: attachmentServiceMock },
                { provide: AttachmentUnitService, useValue: attachmentUnitServiceMock },
                { provide: LectureUnitService, useValue: lectureUnitServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewComponent);
        component = fixture.componentInstance;

        jest.spyOn(component.dialogErrorSource, 'next');

        global.URL.createObjectURL = jest.fn().mockReturnValue('blob-url');

        routerNavigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate');

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('Initialization and Data Loading', () => {
        it('should load attachment file and verify service calls when attachment data is available', () => {
            component.ngOnInit();
            expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
            expect(attachmentUnitServiceMock.getAttachmentFile).not.toHaveBeenCalled();
        });

        it('should load attachment unit file and verify service calls when attachment unit data is available', () => {
            routeMock.data = of({
                course: { id: 1, name: 'Example Course' },
                attachmentUnit: { id: 1, name: 'Chapter 1', lecture: { id: 1 } },
            });

            const mockBlob = new Blob(['mock content'], { type: 'application/pdf' });
            attachmentUnitServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));
            attachmentUnitServiceMock.getHiddenSlides = jest.fn().mockReturnValue(of([1, 2, 3]));

            component.ngOnInit();

            expect(attachmentUnitServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
            expect(attachmentUnitServiceMock.getHiddenSlides).toHaveBeenCalledWith(1, 1);
            expect(component.currentPdfBlob()).toBeDefined();
            expect(component.hiddenPages().size).toBe(3);
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

            expect(alertServiceSpy).toHaveBeenCalled();
        });

        it('should handle errors and trigger alert when loading an attachment unit file fails', () => {
            routeMock.data = of({
                course: { id: 1, name: 'Example Course' },
                attachmentUnit: { id: 1, name: 'Chapter 1', lecture: { id: 1 } },
            });

            const errorResponse = new HttpErrorResponse({
                status: 404,
                statusText: 'Not Found',
                error: 'File not found',
            });

            attachmentUnitServiceMock.getAttachmentFile.mockReturnValue(throwError(() => errorResponse));
            attachmentUnitServiceMock.getHiddenSlides.mockReturnValue(of([]));

            const alertServiceSpy = jest.spyOn(alertServiceMock, 'error');

            component.ngOnInit();
            fixture.detectChanges();

            expect(attachmentUnitServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
            expect(alertServiceSpy).toHaveBeenCalledWith('error.http.404');
        });
    });

    describe('Unsubscribing from Observables', () => {
        it('should unsubscribe attachment subscription during component destruction', () => {
            const spySub = jest.spyOn(component.attachmentSub, 'unsubscribe');
            component.ngOnDestroy();
            expect(spySub).toHaveBeenCalled();
        });
    });

    describe('Getting Hidden Pages', () => {
        it('should return an array of hidden page numbers from DOM elements with specific IDs and classes', () => {
            const mockElement1 = document.createElement('button');
            mockElement1.id = 'hide-show-button-1';
            mockElement1.className = 'hide-show-btn btn-success';

            const mockElement2 = document.createElement('button');
            mockElement2.id = 'hide-show-button-2';
            mockElement2.className = 'hide-show-btn btn-success';

            const mockElement3 = document.createElement('button');
            mockElement3.id = 'hide-show-button-3';
            mockElement3.className = 'hide-show-btn btn-success';

            document.body.appendChild(mockElement1);
            document.body.appendChild(mockElement2);
            document.body.appendChild(mockElement3);

            const hiddenPages = component.getHiddenPages();
            expect(hiddenPages).toEqual([1, 2, 3]);

            document.body.removeChild(mockElement1);
            document.body.removeChild(mockElement2);
            document.body.removeChild(mockElement3);
        });

        it('should return an empty array if no matching elements are found', () => {
            document.body.innerHTML = '';
            const hiddenPages = component.getHiddenPages();
            expect(hiddenPages).toEqual([]);
        });

        it('should ignore elements with IDs that do not match the expected pattern', () => {
            const mockElement1 = document.createElement('button');
            mockElement1.id = 'hide-show-button-1';
            mockElement1.className = 'hide-show-btn btn-success';

            const mockElement2 = document.createElement('button');
            mockElement2.id = 'non-matching-id';
            mockElement2.className = 'hide-show-btn btn-success';

            document.body.appendChild(mockElement1);
            document.body.appendChild(mockElement2);

            const hiddenPages = component.getHiddenPages();
            expect(hiddenPages).toEqual([1]);

            document.body.removeChild(mockElement1);
            document.body.removeChild(mockElement2);
        });
    });

    describe('Create Hidden Version of Attachment', () => {
        it('should create a hidden version of the PDF by removing specified pages', async () => {
            component.attachmentUnit.set({
                attachment: { name: 'example' },
            });

            const mockBlob = new Blob(['PDF content'], { type: 'application/pdf' });
            mockBlob.arrayBuffer = jest.fn().mockResolvedValue(new ArrayBuffer(8));
            component.currentPdfBlob.set(mockBlob);

            const mockPdfDoc = {
                removePage: jest.fn(),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };
            (PDFDocument.load as jest.Mock).mockResolvedValue(mockPdfDoc);

            const hiddenPages = [1, 3, 5];
            const result = await component.createHiddenVersionOfAttachment(hiddenPages);

            expect(PDFDocument.load).toHaveBeenCalledWith(await mockBlob.arrayBuffer());
            expect(mockPdfDoc.removePage).toHaveBeenCalledWith(4);
            expect(mockPdfDoc.removePage).toHaveBeenCalledWith(2);
            expect(mockPdfDoc.removePage).toHaveBeenCalledWith(0);
            expect(mockPdfDoc.removePage).toHaveBeenCalledTimes(3);
            expect(mockPdfDoc.save).toHaveBeenCalled();

            expect(result).toBeInstanceOf(File);
            expect(result?.name).toBe('example.pdf');
            expect(result?.type).toBe('application/pdf');
        });

        it('should handle errors and display an alert when PDF processing fails', async () => {
            component.attachmentUnit.set({
                attachment: { name: 'example' },
            });

            const mockBlob = new Blob(['PDF content'], { type: 'application/pdf' });
            mockBlob.arrayBuffer = jest.fn().mockResolvedValue(new ArrayBuffer(8));
            component.currentPdfBlob.set(mockBlob);

            (PDFDocument.load as jest.Mock).mockRejectedValue(new Error('PDF processing failed'));

            const alertServiceSpy = jest.spyOn(component.alertService, 'error');
            const hiddenPages = [1, 3, 5];
            const result = await component.createHiddenVersionOfAttachment(hiddenPages);

            expect(PDFDocument.load).toHaveBeenCalledWith(await mockBlob.arrayBuffer());
            expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.pageDeleteError', { error: 'PDF processing failed' });
            expect(result).toBeUndefined();
        });
    });

    describe('File Input Handling', () => {
        it('should trigger the file input click event', () => {
            const mockFileInput = document.createElement('input');
            mockFileInput.type = 'file';
            component.fileInput = signal({ nativeElement: mockFileInput });

            const clickSpy = jest.spyOn(component.fileInput()!.nativeElement, 'click');
            component.triggerFileInput();
            expect(clickSpy).toHaveBeenCalled();
        });
    });

    describe('Attachment Updating', () => {
        it('should update attachment successfully and show success alert', () => {
            component.attachment.set({ id: 1, version: 1 });
            component.updateAttachmentWithFile();

            expect(attachmentServiceMock.update).toHaveBeenCalled();
            expect(alertServiceMock.success).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
        });

        it('should not update attachment if file size exceeds the limit and show an error alert', () => {
            const oversizedData = new Uint8Array(MAX_FILE_SIZE + 1).fill(0);
            component.currentPdfBlob.set(new Blob([oversizedData], { type: 'application/pdf' }));

            component.updateAttachmentWithFile();

            expect(attachmentServiceMock.update).not.toHaveBeenCalled();
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.fileSizeError');
        });

        it('should handle errors when updating an attachment fails', () => {
            attachmentServiceMock.update.mockReturnValue(throwError(() => new Error('Update failed')));
            component.attachment.set({ id: 1, version: 1 });

            component.updateAttachmentWithFile();

            expect(attachmentServiceMock.update).toHaveBeenCalled();
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Update failed' });
        });

        it('should display success alert and navigate when finalHiddenPages is empty', async () => {
            component.attachment.set(undefined);
            component.attachmentUnit.set({ id: 1, lecture: { id: 1 }, attachment: { id: 2, name: 'Attachment Name', version: 1 } });
            component.course.set({ id: 1 });

            attachmentUnitServiceMock.update.mockReturnValue(of({}));

            const alertServiceSpy = jest.spyOn(alertServiceMock, 'success');
            const routerSpy = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
            component.updateAttachmentWithFile();

            expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
            expect(routerSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 1, 'unit-management']);
        });
    });

    describe('PDF Merging', () => {
        it('should merge PDF files correctly and update the component state', async () => {
            const mockFile = new File(['new pdf'], 'test.pdf', { type: 'application/pdf' });
            mockFile.arrayBuffer = jest.fn().mockResolvedValue(new ArrayBuffer(8));
            const mockEvent = { target: { files: [mockFile] } };

            const existingPdfDoc = {
                copyPages: jest.fn().mockResolvedValue(['page']),
                addPage: jest.fn(),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            const newPdfDoc = {
                getPageIndices: jest.fn().mockReturnValue([0]),
            };

            PDFDocument.load = jest
                .fn()
                .mockImplementationOnce(() => Promise.resolve(existingPdfDoc))
                .mockImplementationOnce(() => Promise.resolve(newPdfDoc));

            component.currentPdfBlob.set(new Blob(['existing pdf'], { type: 'application/pdf' }));
            component.currentPdfBlob()!.arrayBuffer = jest.fn().mockResolvedValue(new ArrayBuffer(8));

            component.selectedPages.set(new Set([1]));

            await component.mergePDF(mockEvent as any);

            expect(PDFDocument.load).toHaveBeenCalledTimes(2);
            expect(existingPdfDoc.copyPages).toHaveBeenCalledWith(newPdfDoc, [0]);
            expect(existingPdfDoc.addPage).toHaveBeenCalled();
            expect(existingPdfDoc.save).toHaveBeenCalled();
            expect(component.currentPdfBlob).toBeDefined();
            expect(component.selectedPages()!.size).toBe(0);
            expect(component.isPdfLoading()).toBeFalsy();
            expect(URL.createObjectURL).toHaveBeenCalledWith(new Blob([new Uint8Array([1, 2, 3])], { type: 'application/pdf' }));
        });

        it('should handle errors when merging PDFs fails', async () => {
            const mockFile = new File(['new pdf'], 'test.pdf', { type: 'application/pdf' });

            mockFile.arrayBuffer = jest.fn().mockResolvedValue(new ArrayBuffer(8));

            const mockEvent = { target: { files: [mockFile] } };
            const error = new Error('Error loading PDF');

            component.currentPdfBlob.set(new Blob(['existing pdf'], { type: 'application/pdf' }));
            component.currentPdfBlob()!.arrayBuffer = jest.fn().mockResolvedValue(new ArrayBuffer(8));

            PDFDocument.load = jest
                .fn()
                .mockImplementationOnce(() => Promise.reject(error))
                .mockImplementationOnce(() => Promise.resolve({}));

            await component.mergePDF(mockEvent as any);

            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.mergeFailedError', { error: error.message });
            expect(component.isPdfLoading()).toBeFalsy();
        });
    });

    describe('Slide Deletion', () => {
        it('should delete selected slides and update the PDF', async () => {
            const existingPdfDoc = {
                removePage: jest.fn(),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            PDFDocument.load = jest.fn().mockResolvedValue(existingPdfDoc);
            const mockArrayBuffer = new ArrayBuffer(8);

            component.currentPdfBlob.set(new Blob(['existing pdf'], { type: 'application/pdf' }));
            component.currentPdfBlob()!.arrayBuffer = jest.fn().mockResolvedValue(mockArrayBuffer);
            component.selectedPages.set(new Set([1, 2]));

            const alertServiceErrorSpy = jest.spyOn(alertServiceMock, 'error');

            await component.deleteSelectedSlides();

            expect(PDFDocument.load).toHaveBeenCalledWith(mockArrayBuffer);
            expect(existingPdfDoc.removePage).toHaveBeenCalledWith(1);
            expect(existingPdfDoc.removePage).toHaveBeenCalledWith(0);
            expect(existingPdfDoc.removePage).toHaveBeenCalledTimes(2);
            expect(existingPdfDoc.save).toHaveBeenCalled();
            expect(component.currentPdfBlob()).toEqual(new Blob([new Uint8Array([1, 2, 3])], { type: 'application/pdf' }));
            expect(component.selectedPages()!.size).toBe(0);
            expect(alertServiceErrorSpy).not.toHaveBeenCalled();
            expect(component.isPdfLoading()).toBeFalsy();
        });

        it('should handle errors when deleting slides', async () => {
            component.currentPdfBlob.set(new Blob(['existing pdf'], { type: 'application/pdf' }));
            component.currentPdfBlob()!.arrayBuffer = jest.fn().mockRejectedValue(new Error('Failed to load PDF'));

            const alertServiceErrorSpy = jest.spyOn(alertServiceMock, 'error');
            await component.deleteSelectedSlides();

            expect(alertServiceErrorSpy).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.pageDeleteError', { error: 'Failed to load PDF' });
            expect(component.isPdfLoading()).toBeFalsy();
        });
    });

    describe('Update Hidden Pages', () => {
        it('should not change hidden pages if no pages are deleted', () => {
            component.hiddenPages.set(new Set([1, 3, 5]));

            const pagesToDelete: number[] = [];
            component.updateHiddenPages(pagesToDelete);

            expect(component.hiddenPages()).toEqual(new Set([1, 3, 5]));
        });

        it('should handle an empty set of hidden pages', () => {
            component.hiddenPages.set(new Set());

            const pagesToDelete = [0, 1, 2];
            component.updateHiddenPages(pagesToDelete);

            expect(component.hiddenPages()).toEqual(new Set());
        });

        it('should not adjust hidden pages that are not affected by the deletion', () => {
            component.hiddenPages.set(new Set([4, 6, 8]));

            const pagesToDelete = [0, 1];
            component.updateHiddenPages(pagesToDelete);

            expect(component.hiddenPages()).toEqual(new Set([2, 4, 6]));
        });
    });

    describe('Attachment Deletion', () => {
        it('should delete the attachment and navigate to attachments on success', () => {
            component.attachment.set({ id: 1, lecture: { id: 2 } });
            component.course.set({ id: 3 });

            component.deleteAttachmentFile();

            expect(attachmentServiceMock.delete).toHaveBeenCalledWith(1);
            expect(routerNavigateSpy).toHaveBeenCalledWith(['course-management', 3, 'lectures', 2, 'attachments']);
            expect(component.dialogErrorSource.next).toHaveBeenCalledWith('');
        });

        it('should delete the attachment unit and navigate to unit management on success', () => {
            component.attachment.set(undefined);
            component.attachmentUnit.set({ id: 4, lecture: { id: 5 } });
            component.course.set({ id: 6 });

            component.deleteAttachmentFile();

            expect(lectureUnitServiceMock.delete).toHaveBeenCalledWith(4, 5);
            expect(routerNavigateSpy).toHaveBeenCalledWith(['course-management', 6, 'lectures', 5, 'unit-management']);
            expect(component.dialogErrorSource.next).toHaveBeenCalledWith('');
        });

        it('should handle error and call alertService.error if deletion of attachment fails', () => {
            const error = { message: 'Deletion failed' };
            attachmentServiceMock.delete.mockReturnValue(throwError(() => error));
            component.attachment.set({ id: 1, lecture: { id: 2 } });
            component.course.set({ id: 3 });

            component.deleteAttachmentFile();

            expect(attachmentServiceMock.delete).toHaveBeenCalledWith(1);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Deletion failed' });
        });

        it('should handle error and call alertService.error if deletion of attachment unit fails', () => {
            const error = { message: 'Deletion failed' };
            lectureUnitServiceMock.delete.mockReturnValue(throwError(() => error));
            component.attachment.set(undefined);
            component.attachmentUnit.set({ id: 4, lecture: { id: 5 } });
            component.course.set({ id: 6 });

            component.deleteAttachmentFile();

            expect(lectureUnitServiceMock.delete).toHaveBeenCalledWith(4, 5);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Deletion failed' });
        });
    });
});
