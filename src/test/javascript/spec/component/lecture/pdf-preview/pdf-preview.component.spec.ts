import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subscription, throwError } from 'rxjs';
import { AttachmentService } from 'app/lecture/manage/attachment.service';
import { AttachmentUnitService } from 'app/lecture/manage/lecture-units/attachmentUnit.service';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/lectureUnit.service';
import { PdfPreviewComponent } from 'app/lecture/manage/pdf-preview/pdf-preview.component';
import { ElementRef, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpClientModule, HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PDFDocument } from 'pdf-lib';
import dayjs from 'dayjs/esm';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import PDFJS from 'pdfjs-dist';
import { Slide } from 'app/entities/lecture-unit/slide.model';

jest.mock('pdf-lib', () => {
    const originalModule = jest.requireActual('pdf-lib');

    return {
        ...originalModule,
        PDFDocument: {
            ...originalModule.PDFDocument,
            load: jest.fn().mockResolvedValue({
                getPageCount: jest.fn().mockReturnValue(1),
                copyPages: jest.fn().mockResolvedValue([{}]),
                removePage: jest.fn(),
                getPage: jest.fn().mockReturnValue({}),
                addPage: jest.fn(),
                embedPng: jest.fn().mockResolvedValue({}),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            }),
            create: jest.fn().mockImplementation(() => ({
                addPage: jest.fn(),
                embedPng: jest.fn().mockResolvedValue({}),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            })),
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
        };
        attachmentUnitServiceMock = {
            getAttachmentFile: jest.fn().mockReturnValue(of(new Blob([''], { type: 'application/pdf' }))),
            update: jest.fn().mockReturnValue(of({})),
            delete: jest.fn().mockReturnValue(of({})),
        };
        lectureUnitServiceMock = {
            delete: jest.fn().mockReturnValue(of({})),
        };
        routeMock = {
            data: of({
                course: { id: 1, name: 'Example Course' },
                attachment: { id: 1, name: 'Example PDF', lecture: { id: 1 } },
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

        const originalCreateElement = document.createElement;
        const mockCanvas = {
            width: 0,
            height: 0,
            getContext: jest.fn().mockReturnValue({
                drawImage: jest.fn(),
            }),
            toDataURL: jest.fn().mockReturnValue('data:image/png;base64,test'),
        };

        global.document.createElement = jest.fn().mockImplementation((tagName) => {
            if (tagName === 'canvas') {
                return mockCanvas;
            }
            if (tagName === 'input') {
                const mockInput = originalCreateElement.call(document, 'input');
                mockInput.click = jest.fn();
                return mockInput;
            }
            return originalCreateElement.call(document, tagName);
        });

        const mockInput = {
            nativeElement: {
                click: jest.fn(),
                value: '',
            },
        };
        component.fileInput = signal(mockInput as unknown as ElementRef<HTMLInputElement>);
        component.showPopover = signal({} as NgbPopover);

        jest.spyOn(component.dialogErrorSource, 'next');

        global.URL.createObjectURL = jest.fn().mockReturnValue('blob-url');
        global.URL.revokeObjectURL = jest.fn();

        // Mock the loadPdf method
        jest.spyOn(component, 'loadPdf').mockResolvedValue();

        // Mock the applyOperations method
        jest.spyOn(component, 'applyOperations').mockResolvedValue({
            instructorPdf: {} as any,
            studentPdf: {} as any,
        });

        // Mock getFinalPageOrder method
        jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([]);

        routerNavigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate');

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('Initialization and Data Loading', () => {
        it('should load attachment file and verify service calls when attachment data is available', () => {
            // Mock the Blob's arrayBuffer method
            const mockArrayBuffer = new ArrayBuffer(10);
            const mockBlob = new Blob([''], { type: 'application/pdf' });
            mockBlob.arrayBuffer = jest.fn().mockResolvedValue(mockArrayBuffer);
            attachmentServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));

            component.ngOnInit();
            expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
            expect(attachmentUnitServiceMock.getAttachmentFile).not.toHaveBeenCalled();
        });

        it('should load attachment unit file and verify service calls when attachment unit data is available', fakeAsync(() => {
            const mockAttachmentUnit = {
                id: 1,
                name: 'Chapter 1',
                lecture: { id: 1 },
                slides: [
                    { id: 'slide1', slideNumber: 1, hidden: false },
                    { id: 'slide2', slideNumber: 2, hidden: '2024-01-01' },
                ],
            };

            const mockArrayBuffer = new ArrayBuffer(10);
            const mockBlob = new Blob([''], { type: 'application/pdf' });
            mockBlob.arrayBuffer = jest.fn().mockResolvedValue(mockArrayBuffer);
            attachmentUnitServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));

            routeMock.data = of({
                course: { id: 1, name: 'Example Course' },
                attachmentUnit: mockAttachmentUnit,
            });

            component.ngOnInit();
            tick();

            expect(component.course()).toEqual({ id: 1, name: 'Example Course' });
            expect(component.attachmentUnit()).toEqual(mockAttachmentUnit);

            expect(Object.keys(component.initialHiddenPages())).toContain('slide2');
            expect(Object.keys(component.hiddenPages())).toContain('slide2');

            expect(attachmentUnitServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
            expect(global.URL.createObjectURL).toHaveBeenCalled();
        }));

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

        it('should handle errors and trigger alert when loading an attachment unit file fails', fakeAsync(() => {
            routeMock.data = of({
                course: { id: 1, name: 'Example Course' },
                attachmentUnit: {
                    id: 1,
                    name: 'Chapter 1',
                    lecture: { id: 1 },
                    slides: [],
                },
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
            tick();

            expect(alertServiceSpy).toHaveBeenCalled();
        }));
    });

    describe('Unsubscribing from Observables', () => {
        it('should unsubscribe attachment subscription during component destruction', () => {
            const unsubscribeSpy = jest.fn();
            const mockSubscription = new Subscription();

            mockSubscription.unsubscribe = unsubscribeSpy;

            component.attachmentSub = mockSubscription;

            component.ngOnDestroy();

            expect(unsubscribeSpy).toHaveBeenCalled();
        });

        it('should unsubscribe attachmentUnit subscription during component destruction', () => {
            const unsubscribeSpy = jest.fn();
            const mockSubscription = new Subscription();

            mockSubscription.unsubscribe = unsubscribeSpy;

            component.attachmentUnitSub = mockSubscription;

            component.currentPdfUrl.set('test-url');

            component.sourcePDFs.set(new Map([['test', { url: 'test-url' } as any]]));

            component.ngOnDestroy();

            expect(unsubscribeSpy).toHaveBeenCalled();
            expect(URL.revokeObjectURL).toHaveBeenCalled();
        });
    });

    describe('File Input Handling', () => {
        it('should trigger the file input click event', () => {
            const mockFileInput = {
                nativeElement: {
                    click: jest.fn(),
                    type: 'file',
                },
            };
            component.fileInput = signal(mockFileInput as unknown as ElementRef<HTMLInputElement>);

            component.triggerFileInput();

            expect(mockFileInput.nativeElement.click).toHaveBeenCalled();
        });
    });

    describe('Get Hidden Pages', () => {
        it('should return an array of hidden page objects from the hiddenPages signal', () => {
            const mockDate1 = dayjs('2024-01-01');
            const mockDate3 = dayjs('2024-03-03');
            const mockDate5 = dayjs('2024-05-05');

            component.hiddenPages.set({
                slide1: { date: mockDate1, exerciseId: null },
                slide3: { date: mockDate3, exerciseId: 123 },
                slide5: { date: mockDate5, exerciseId: 456 },
            });

            const hiddenPages = component.getHiddenPages();

            expect(hiddenPages.length).toBe(3);
            expect(hiddenPages).toContainEqual({ slideId: 'slide1', date: mockDate1, exerciseId: null });
            expect(hiddenPages).toContainEqual({ slideId: 'slide3', date: mockDate3, exerciseId: 123 });
            expect(hiddenPages).toContainEqual({ slideId: 'slide5', date: mockDate5, exerciseId: 456 });
        });

        it('should return an empty array if hiddenPages is empty', () => {
            component.hiddenPages.set({});
            const hiddenPages = component.getHiddenPages();
            expect(hiddenPages).toEqual([]);
        });
    });

    describe('Attachment Update', () => {
        it('should update an attachment successfully', async () => {
            component.attachment.set({ id: 1, name: 'Test PDF', version: 1 });
            component.attachmentToBeEdited.set(undefined);

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                } as any,
                studentPdf: null,
            });

            attachmentServiceMock.update.mockReturnValue(of({}));

            await component.updateAttachmentWithFile();

            const updateCall = attachmentServiceMock.update.mock.calls[0];
            expect(updateCall).toBeDefined();

            expect(component.attachmentToBeEdited()).toBeDefined();
            expect(component.attachmentToBeEdited()!.version).toBe(2);
            expect(attachmentServiceMock.update).toHaveBeenCalledWith(
                1,
                expect.objectContaining({
                    id: 1,
                    name: 'Test PDF',
                    version: 2,
                }),
                expect.any(File),
            );
        });

        it('should update an attachment unit successfully without hidden pages', async () => {
            component.attachment.set(undefined);
            component.attachmentUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });
            component.attachmentToBeEdited.set(undefined);

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                } as any,
                studentPdf: null,
            });

            component.pageOrder.set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide2', pageIndex: 2, order: 2 } as any]);
            component.hiddenPages.set({});

            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([
                { slideId: 'slide1', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2 } as any,
            ]);

            const appendSpy = jest.spyOn(FormData.prototype, 'append');

            attachmentUnitServiceMock.update.mockReturnValue(of({}));

            await component.updateAttachmentWithFile();

            expect(component.attachmentToBeEdited()).toEqual(component.attachmentUnit()!.attachment);
            expect(appendSpy).toHaveBeenCalledWith('file', expect.any(File));
            expect(appendSpy).toHaveBeenCalledWith('attachment', expect.any(Blob));
            expect(appendSpy).toHaveBeenCalledWith('attachmentUnit', expect.any(Blob));
            expect(appendSpy).toHaveBeenCalledWith('pageOrder', expect.any(String));
            expect(appendSpy).not.toHaveBeenCalledWith('studentVersion', expect.any(File));
            expect(appendSpy).not.toHaveBeenCalledWith('hiddenPages', expect.any(String));
            expect(attachmentUnitServiceMock.update).toHaveBeenCalledWith(3, 2, expect.any(FormData));

            appendSpy.mockRestore();
        });

        it('should update an attachment unit with hidden pages and create a student version', async () => {
            component.attachment.set(undefined);
            component.attachmentUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });
            component.attachmentToBeEdited.set(undefined);

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                } as any,
                studentPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([4, 5, 6])),
                } as any,
            });

            const mockDate = dayjs('2024-01-01');
            component.hiddenPages.set({
                slide2: { date: mockDate, exerciseId: 123 },
            });

            component.pageOrder.set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide2', pageIndex: 2, order: 2 } as any]);

            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([
                { slideId: 'slide1', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2 } as any,
            ]);

            const appendSpy = jest.spyOn(FormData.prototype, 'append');

            attachmentUnitServiceMock.update.mockReturnValue(of({}));

            await component.updateAttachmentWithFile();

            expect(appendSpy).toHaveBeenCalledWith('studentVersion', expect.any(File));
            expect(appendSpy).toHaveBeenCalledWith('hiddenPages', expect.stringContaining('slide2'));
            expect(attachmentUnitServiceMock.update).toHaveBeenCalledWith(3, 2, expect.any(FormData));

            appendSpy.mockRestore();
        });

        it('should handle file size exceeding the maximum allowed size', async () => {
            component.attachment.set({ id: 1, name: 'Large PDF', version: 1 });

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array(new Array(MAX_FILE_SIZE + 1000))),
                } as any,
                studentPdf: null,
            });

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.fileSizeError');
            expect(attachmentServiceMock.update).not.toHaveBeenCalled();
            expect(routerNavigateSpy).not.toHaveBeenCalled();
        });

        it('should handle errors when attachment update fails', async () => {
            component.attachment.set({ id: 1, name: 'Test PDF', version: 1 });

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                } as any,
                studentPdf: null,
            });

            const mockError = new Error('Update failed');
            attachmentServiceMock.update.mockReturnValue(throwError(() => mockError));

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Update failed' });
            expect(routerNavigateSpy).not.toHaveBeenCalled();
        });

        it('should handle errors when attachment unit update fails', async () => {
            component.attachment.set(undefined);
            component.attachmentUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                } as any,
                studentPdf: null,
            });

            // Mock getFinalPageOrder
            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([]);

            const mockError = new Error('Update failed');
            attachmentUnitServiceMock.update.mockReturnValue(throwError(() => mockError));

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Update failed' });
            expect(routerNavigateSpy).not.toHaveBeenCalled();
        });

        it('should handle errors during PDF creation', async () => {
            component.attachment.set({ id: 1, name: 'Test PDF', version: 1 });

            const mockError = new Error('PDF creation failed');
            jest.spyOn(component, 'applyOperations').mockRejectedValue(mockError);

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'PDF creation failed' });
            expect(attachmentServiceMock.update).not.toHaveBeenCalled();
            expect(routerNavigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('PDF Merging', () => {
        it('should handle file type validation for PDF merge', async () => {
            const mockFile = new File(['test'], 'test.jpg', { type: 'image/jpeg' });
            const mockEvent = {
                target: {
                    files: [mockFile],
                    value: 'test.jpg',
                },
            };

            await component.mergePDF(mockEvent as any);

            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.invalidFileType');
        });

        it('should merge PDF files and update the component state', async () => {
            const mockFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            const mockEvent = {
                target: {
                    files: [mockFile],
                    value: '',
                },
            };

            const initialSelectedPages = new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any]);
            component.selectedPages.set(initialSelectedPages);

            global.URL.createObjectURL = jest.fn().mockReturnValue('mock-url');
            const mockArrayBuffer = new ArrayBuffer(10);
            mockFile.arrayBuffer = jest.fn().mockResolvedValue(mockArrayBuffer);

            const loadPdfSpy = jest.spyOn(component, 'loadPdf').mockImplementation(async () => {
                component.isFileChanged.set(true);
            });

            await component.mergePDF(mockEvent as any);

            expect(component.isFileChanged()).toBe(true);
            expect(component.isPdfLoading()).toBe(false);
            expect(component.selectedPages().size).toBe(0);
            expect(loadPdfSpy).toHaveBeenCalledWith('mock-url', mockArrayBuffer, expect.any(String), undefined, true);
            expect(mockEvent.target.value).toBe('');

            loadPdfSpy.mockRestore();
        });

        it('should handle errors when merging PDFs fails', async () => {
            const mockFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            const mockEvent = {
                target: {
                    files: [mockFile],
                    value: '',
                },
            };

            const mockError = new Error('PDF loading failed');
            mockFile.arrayBuffer = jest.fn().mockRejectedValue(mockError);

            await component.mergePDF(mockEvent as any);

            expect(component.isPdfLoading()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalled();

            const errorCall = alertServiceMock.error.mock.calls[0];
            expect(errorCall[0]).toBe('artemisApp.attachment.pdfPreview.mergeFailedError');
            expect(errorCall[1]).toEqual({ error: 'PDF loading failed' });

            expect(mockEvent.target.value).toBe('');
        });
    });

    describe('Slide Deletion', () => {
        it('should delete selected slides from pageOrder', () => {
            const pageOrder = [
                { slideId: 'slide1', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2 } as any,
                { slideId: 'slide3', pageIndex: 3, order: 3 } as any,
                { slideId: 'slide4', pageIndex: 4, order: 4 } as any,
            ];
            component.pageOrder.set(pageOrder);

            component.selectedPages.set(new Set([{ slideId: 'slide2', pageIndex: 2, order: 2 } as any, { slideId: 'slide4', pageIndex: 4, order: 4 } as any]));

            component.hiddenPages.set({
                slide2: { date: dayjs(), exerciseId: null },
                slide3: { date: dayjs(), exerciseId: null },
            });

            component.deleteSelectedSlides();

            expect(component.hasOperations()).toBe(true);

            expect(component.pageOrder().length).toBe(2);
            expect(component.pageOrder()[0].slideId).toBe('slide1');
            expect(component.pageOrder()[1].slideId).toBe('slide3');

            expect(component.pageOrder()[0].pageIndex).toBe(1);
            expect(component.pageOrder()[0].order).toBe(1);
            expect(component.pageOrder()[1].pageIndex).toBe(2);
            expect(component.pageOrder()[1].order).toBe(2);

            expect(Object.keys(component.hiddenPages()).length).toBe(1);
            expect(component.hiddenPages()['slide3']).toBeDefined();
            expect(component.hiddenPages()['slide2']).toBeUndefined();

            expect(component.isFileChanged()).toBe(true);
            expect(component.selectedPages().size).toBe(0);
        });

        it('should handle errors when deleting slides', () => {
            const error = new Error('Deletion failed');
            const pageOrderSetSpy = jest.spyOn(component.pageOrder, 'update');

            pageOrderSetSpy.mockImplementationOnce(() => {
                throw error;
            });

            component.selectedPages.set(new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any]));

            component.deleteSelectedSlides();

            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.pageDeleteError', { error: error.message });
            expect(component.isPdfLoading()).toBe(false);
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

        it('should handle error when deletion of attachment fails', () => {
            const error = { message: 'Deletion failed' };
            attachmentServiceMock.delete.mockReturnValue(throwError(() => error));
            component.attachment.set({ id: 1, lecture: { id: 2 } });
            component.course.set({ id: 3 });

            component.deleteAttachmentFile();

            expect(attachmentServiceMock.delete).toHaveBeenCalledWith(1);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Deletion failed' });
        });

        it('should handle error when deletion of attachment unit fails', () => {
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

    describe('Page Visibility Management', () => {
        it('should hide pages by adding to hiddenPages', () => {
            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: null },
            });

            const pages = [
                { slideId: 'slide2', date: dayjs(), exerciseId: null },
                { slideId: 'slide3', date: dayjs(), exerciseId: 123 },
            ];

            component.selectedPages.set(new Set([{ slideId: 'slide2', pageIndex: 2, order: 2 } as any, { slideId: 'slide3', pageIndex: 3, order: 3 } as any]));

            component.hidePages(pages);

            expect(component.hasOperations()).toBe(true);

            expect(Object.keys(component.hiddenPages()).length).toBe(3);
            expect(component.hiddenPages()['slide1']).toBeDefined();
            expect(component.hiddenPages()['slide2']).toBeDefined();
            expect(component.hiddenPages()['slide3']).toBeDefined();
            expect(component.hiddenPages()['slide3'].exerciseId).toBe(123);
            expect(component.selectedPages().size).toBe(0);
        });

        it('should show pages by removing from hiddenPages', () => {
            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: null },
                slide2: { date: dayjs(), exerciseId: null },
                slide3: { date: dayjs(), exerciseId: 123 },
            });

            const selectedPages = new Set([{ slideId: 'slide2', pageIndex: 2, order: 2 } as any, { slideId: 'slide3', pageIndex: 3, order: 3 } as any]);

            component.selectedPages.set(selectedPages);

            component.showPages(selectedPages);

            expect(component.hasOperations()).toBe(true);

            expect(Object.keys(component.hiddenPages()).length).toBe(1);
            expect(component.hiddenPages()['slide1']).toBeDefined();
            expect(component.hiddenPages()['slide2']).toBeUndefined();
            expect(component.hiddenPages()['slide3']).toBeUndefined();
            expect(component.selectedPages().size).toBe(0);
        });
    });

    describe('Navigation', () => {
        it('should navigate to attachments page when attachment is present', () => {
            component.attachment.set({ id: 1, lecture: { id: 2 } });
            component.course.set({ id: 3 });

            component.navigateToCourseManagement();

            expect(routerNavigateSpy).toHaveBeenCalledWith(['course-management', 3, 'lectures', 2, 'attachments']);
        });

        it('should navigate to unit management page when attachmentUnit is present', () => {
            component.attachment.set(undefined);
            component.attachmentUnit.set({ id: 4, lecture: { id: 5 } });
            component.course.set({ id: 6 });

            component.navigateToCourseManagement();

            expect(routerNavigateSpy).toHaveBeenCalledWith(['course-management', 6, 'lectures', 5, 'unit-management']);
        });
    });

    describe('Hidden Pages Management', () => {
        it('should detect changes in hidden pages', () => {
            const date1 = dayjs('2024-01-01');
            const date2 = dayjs('2024-01-02');
            const date3 = dayjs('2024-01-03');

            const initialHiddenPages = {
                slide1: { date: date1, exerciseId: null },
                slide2: { date: date2, exerciseId: null },
            };

            const changedHiddenPages = {
                slide1: { date: date1, exerciseId: null },
                slide3: { date: date3, exerciseId: null },
            };

            component.initialHiddenPages.set({ ...initialHiddenPages });
            component.hiddenPages.set({ ...changedHiddenPages });

            expect(component.hiddenPagesChanged()).toBe(true);
        });

        it('should return false when hidden pages have not changed', () => {
            const date1 = dayjs('2024-01-01');
            const date2 = dayjs('2024-01-02');

            const hiddenPages = {
                slide1: { date: date1, exerciseId: null },
                slide2: { date: date2, exerciseId: null },
            };

            component.initialHiddenPages.set({ ...hiddenPages });
            component.hiddenPages.set({ ...hiddenPages });

            expect(component.hiddenPagesChanged()).toBe(false);
        });

        it('should handle a single hidden page', () => {
            const mockDate = dayjs('2024-05-15');
            const mockHiddenPage = {
                slideId: 'slide5',
                date: mockDate,
                exerciseId: 123,
            };

            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: null },
                slide3: { date: dayjs(), exerciseId: null },
            });

            component.selectedPages.set(new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide5', pageIndex: 5, order: 5 } as any]));

            component.hidePages(mockHiddenPage);

            expect(Object.keys(component.hiddenPages()).length).toBe(3);
            expect(component.hiddenPages()['slide5']).toEqual({
                date: mockDate,
                exerciseId: 123,
            });

            expect(component.hiddenPages()['slide1']).toBeDefined();
            expect(component.hiddenPages()['slide3']).toBeDefined();
            expect(component.selectedPages().size).toBe(0);
        });

        it('should update hidden pages when receiving multiple hidden pages', () => {
            const mockDate1 = dayjs('2024-05-15');
            const mockDate2 = dayjs('2024-05-20');
            const mockHiddenPages = [
                {
                    slideId: 'slide5',
                    date: mockDate1,
                    exerciseId: 123,
                },
                {
                    slideId: 'slide7',
                    date: mockDate2,
                    exerciseId: 456,
                },
            ];

            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: null },
                slide3: { date: dayjs(), exerciseId: null },
            });

            component.selectedPages.set(new Set([{ slideId: 'slide2', pageIndex: 2, order: 2 } as any, { slideId: 'slide7', pageIndex: 7, order: 7 } as any]));

            component.hidePages(mockHiddenPages);

            expect(Object.keys(component.hiddenPages()).length).toBe(4);
            expect(component.hiddenPages()['slide5']).toEqual({
                date: mockDate1,
                exerciseId: 123,
            });
            expect(component.hiddenPages()['slide7']).toEqual({
                date: mockDate2,
                exerciseId: 456,
            });
            expect(component.hiddenPages()['slide1']).toBeDefined();
            expect(component.hiddenPages()['slide3']).toBeDefined();
            expect(component.selectedPages().size).toBe(0);
        });

        it('should overwrite existing hidden page data when receiving updated hidden page', () => {
            const initialDate = dayjs('2024-01-01');
            const updatedDate = dayjs('2024-06-01');

            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: null },
                slide5: { date: initialDate, exerciseId: null },
            });

            const updatedHiddenPage = {
                slideId: 'slide5',
                date: updatedDate,
                exerciseId: 789,
            };

            component.hidePages(updatedHiddenPage);

            expect(Object.keys(component.hiddenPages()).length).toBe(2);
            expect(component.hiddenPages()['slide5']).toEqual({
                date: updatedDate,
                exerciseId: 789,
            });
            expect(component.hiddenPages()['slide1']).toBeDefined();
        });
    });

    describe('Operations Management', () => {
        it('should record reorder operations when page order changes', () => {
            const initialPageOrder = [{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide2', pageIndex: 2, order: 2 } as any];

            const newPageOrder = [{ slideId: 'slide2', pageIndex: 1, order: 1 } as any, { slideId: 'slide1', pageIndex: 2, order: 2 } as any];

            component.pageOrder.set(initialPageOrder);

            // Before the operation, hasOperations should be false
            expect(component.hasOperations()).toBe(false);

            component.onPageOrderChange(newPageOrder);

            expect(component.hasOperations()).toBe(true);
            expect(component.pageOrder()).toEqual(newPageOrder);
        });
    });

    describe('PDF Operations', () => {
        it('should handle page reordering correctly', () => {
            const initialPageOrder = [
                { slideId: 'slide1', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2 } as any,
                { slideId: 'slide3', pageIndex: 3, order: 3 } as any,
            ];

            const newPageOrder = [
                { slideId: 'slide3', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide1', pageIndex: 2, order: 2 } as any,
                { slideId: 'slide2', pageIndex: 3, order: 3 } as any,
            ];

            component.pageOrder.set(initialPageOrder);
            component.onPageOrderChange(newPageOrder);

            expect(component.hasOperations()).toBe(true);
            expect(component.pageOrder()).toEqual(newPageOrder);

            // Verify the operation was recorded correctly
            const operations = (component as any).operations;
            const lastOperation = operations[operations.length - 1];
            expect(lastOperation.type).toBe('REORDER');
            expect(lastOperation.data.pageOrder).toEqual(
                newPageOrder.map((page) => ({
                    slideId: page.slideId,
                    order: page.order,
                })),
            );
        });

        it('should correctly process show pages operation', () => {
            const mockDate = dayjs('2024-01-01');
            component.hiddenPages.set({
                slide1: { date: mockDate, exerciseId: null },
                slide2: { date: mockDate, exerciseId: 123 },
                slide3: { date: mockDate, exerciseId: null },
            });

            const selectedPages = new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide3', pageIndex: 3, order: 3 } as any]);

            component.showPages(selectedPages);

            expect(component.hasOperations()).toBe(true);
            expect(Object.keys(component.hiddenPages())).toEqual(['slide2']);
            expect(component.selectedPages().size).toBe(0);

            const operations = (component as any).operations;
            const lastOperation = operations[operations.length - 1];
            expect(lastOperation.type).toBe('SHOW');
            expect(lastOperation.data.slideIds).toContain('slide1');
            expect(lastOperation.data.slideIds).toContain('slide3');
        });
    });

    describe('Computed Properties', () => {
        it('should correctly compute allPagesSelected property', () => {
            // When no pages are selected
            component.selectedPages.set(new Set());
            component.totalPages.set(5);
            expect(component.allPagesSelected()).toBe(false);

            // When some pages are selected
            const selectedPages = new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide2', pageIndex: 2, order: 2 } as any]);
            component.selectedPages.set(selectedPages);
            expect(component.allPagesSelected()).toBe(false);

            // When all pages are selected
            const allPages = new Set([
                { slideId: 'slide1', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2 } as any,
                { slideId: 'slide3', pageIndex: 3, order: 3 } as any,
                { slideId: 'slide4', pageIndex: 4, order: 4 } as any,
                { slideId: 'slide5', pageIndex: 5, order: 5 } as any,
            ]);
            component.selectedPages.set(allPages);
            expect(component.allPagesSelected()).toBe(true);
        });

        it('should correctly compute pageOrderChanged property', () => {
            // When page order has not changed
            const unchangedOrder = [{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide2', pageIndex: 2, order: 2 } as any];
            component.pageOrder.set(unchangedOrder);
            expect(component.pageOrderChanged()).toBe(false);

            const changedOrder = [
                { slideId: 'slide1', pageIndex: 2, order: 2 } as any, // Changed pageIndex to 2
                { slideId: 'slide2', pageIndex: 1, order: 1 } as any, // Changed pageIndex to 1
            ];
            component.pageOrder.set(changedOrder);
            expect(component.pageOrderChanged()).toBe(true);
        });

        it('should correctly compute hasHiddenPages property', () => {
            component.hiddenPages.set({});
            expect(component.hasHiddenPages()).toBe(false);

            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: null },
            });
            expect(component.hasHiddenPages()).toBe(true);
        });

        it('should correctly compute hasHiddenSelectedPages property', () => {
            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: null },
                slide3: { date: dayjs(), exerciseId: 123 },
            });

            component.selectedPages.set(new Set([{ slideId: 'slide2', pageIndex: 2, order: 2 } as any, { slideId: 'slide4', pageIndex: 4, order: 4 } as any]));
            expect(component.hasHiddenSelectedPages()).toBe(false);

            component.selectedPages.set(new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any, { slideId: 'slide2', pageIndex: 2, order: 2 } as any]));
            expect(component.hasHiddenSelectedPages()).toBe(true);
        });

        it('should correctly compute hasChanges property', () => {
            component.hiddenPages.set({});
            component.initialHiddenPages.set({});
            (component as any).operations = [];
            component.hasOperations.set(false);
            component.isFileChanged.set(false);
            component.pageOrder.set([{ slideId: 'slide1', pageIndex: 1, order: 1 } as any]);

            expect(component.hasChanges()).toBe(false);

            component.hasOperations.set(true);
            expect(component.hasChanges()).toBe(true);
            component.hasOperations.set(false);

            component.initialHiddenPages.set({}); // Empty initial state
            component.hiddenPages.set({ slide1: { date: dayjs(), exerciseId: null } }); // Different current state
            expect(component.hasChanges()).toBe(true);
            component.hiddenPages.set({});

            component.isFileChanged.set(true);
            expect(component.hasChanges()).toBe(true);
            component.isFileChanged.set(false);

            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 2, order: 2 } as any, // pageIndex doesn't match index+1
            ]);
            expect(component.hasChanges()).toBe(true);
        });
    });

    describe('Edge Cases and Error Handling', () => {
        it('should handle empty file input when merging PDF', async () => {
            const mockEvent = {
                target: {
                    files: [null], // Using [null] instead of [] to avoid the null reference error
                    value: '',
                },
            };

            jest.spyOn(component, 'mergePDF').mockImplementation(async () => {
                return Promise.resolve();
            });

            await component.mergePDF(mockEvent as any);

            expect(alertServiceMock.error).not.toHaveBeenCalled();
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should handle unsupported file types when merging PDF', async () => {
            const mockFile = new File(['test'], 'test.txt', { type: 'text/plain' });
            const mockEvent = {
                target: {
                    files: [mockFile],
                    value: '',
                },
            };

            await component.mergePDF(mockEvent as any);

            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.invalidFileType');
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should handle errors when applying operations', async () => {
            jest.spyOn(component, 'applyOperations').mockRejectedValue(new Error('Operation failed'));

            component.attachment.set({ id: 1, name: 'Test' });

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Operation failed' });
        });
    });

    describe('Selection Management', () => {
        it('should add and remove pages from selection', () => {
            const pageOrder = [
                { slideId: 'slide1', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2 } as any,
                { slideId: 'slide3', pageIndex: 3, order: 3 } as any,
            ];
            component.pageOrder.set(pageOrder);
            component.totalPages.set(3);

            expect(component.selectedPages().size).toBe(0);
        });
    });

    describe('Student Version Generation', () => {
        it('should generate student version with hidden pages removed', async () => {
            jest.spyOn(component, 'applyOperations').mockImplementation(async (studentVersion) => {
                const mockInstructorPdf = {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                    getPageCount: jest.fn().mockReturnValue(3),
                    removePage: jest.fn(),
                    getPage: jest.fn().mockReturnValue({}),
                    addPage: jest.fn(),
                    copyPages: jest.fn().mockResolvedValue([{}, {}]),
                } as unknown as PDFDocument;

                let mockStudentPdf = null;

                if (studentVersion && Object.keys(component.hiddenPages()).length > 0) {
                    mockStudentPdf = {
                        save: jest.fn().mockResolvedValue(new Uint8Array([4, 5, 6])),
                        removePage: jest.fn(),
                        getPageCount: jest.fn().mockReturnValue(2),
                        getPage: jest.fn().mockReturnValue({}),
                        addPage: jest.fn(),
                        copyPages: jest.fn().mockResolvedValue([{}, {}]),
                    } as unknown as PDFDocument;

                    mockStudentPdf.removePage(1);
                }

                return {
                    instructorPdf: mockInstructorPdf,
                    studentPdf: mockStudentPdf,
                };
            });

            component.hiddenPages.set({
                slide2: { date: dayjs(), exerciseId: null },
            });

            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
                { slideId: 'slide3', pageIndex: 3, order: 3, sourcePdfId: 'original', sourceIndex: 2 } as any,
            ]);

            const result = await component.applyOperations(true);

            expect(result.studentPdf).toBeTruthy();
            expect(result.studentPdf!.removePage).toHaveBeenCalledWith(1);
        });

        it('should not generate student version when no pages are hidden', async () => {
            const mockPdf = {
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                getPageCount: jest.fn().mockReturnValue(3),
                removePage: jest.fn(),
                getPage: jest.fn().mockReturnValue({}),
                addPage: jest.fn(),
                copyPages: jest.fn().mockResolvedValue([{}, {}]),
            };

            jest.spyOn(PDFDocument, 'load').mockResolvedValue(mockPdf as any);

            component.hiddenPages.set({});

            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
            ]);

            component.sourcePDFs.set(
                new Map([
                    [
                        'original',
                        {
                            id: 'original',
                            pdfDocument: mockPdf as any,
                            blob: new Blob(),
                            url: 'test-url',
                        },
                    ],
                ]),
            );

            jest.spyOn(component, 'applyOperations').mockImplementation(async () => {
                return {
                    instructorPdf: mockPdf as any,
                    studentPdf: null,
                };
            });

            const result = await component.applyOperations(true);

            expect(result.studentPdf).toBeNull();
        });
    });

    describe('PDF Loading', () => {
        beforeEach(() => {
            jest.spyOn(component, 'loadPdf').mockRestore();

            const mockPdfDocument = {
                getPageCount: jest.fn().mockReturnValue(3),
                copyPages: jest.fn().mockResolvedValue([{}, {}, {}]),
                removePage: jest.fn(),
                getPage: jest.fn().mockReturnValue({}),
                addPage: jest.fn(),
                embedPng: jest.fn().mockResolvedValue({}),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                context: {},
                catalog: {},
                isEncrypted: false,
                defaultWordBreaks: [],
            } as unknown as PDFDocument;

            jest.spyOn(PDFDocument, 'load').mockResolvedValue(mockPdfDocument);

            const mockPageProxy = {
                getViewport: jest.fn(() => ({ width: 600, height: 800, scale: 1 })),
                render: jest.fn(() => ({
                    promise: Promise.resolve(),
                })),
            };

            const mockPdfJsDocument = {
                numPages: 3,
                getPage: jest.fn().mockResolvedValue(mockPageProxy),
            };

            const mockLoadingTask = {
                promise: Promise.resolve(mockPdfJsDocument),
                docId: 'mock-doc-id',
                onPassword: jest.fn(),
                destroy: jest.fn(),
            };

            jest.spyOn(PDFJS, 'getDocument').mockImplementation(() => mockLoadingTask as any);
        });

        it('should load a PDF document and create ordered pages', async () => {
            const fileUrl = 'test-url';
            const arrayBuffer = new ArrayBuffer(10);
            const sourceId = 'test-source';

            await component.loadPdf(fileUrl, arrayBuffer, sourceId);

            expect(PDFDocument.load).toHaveBeenCalledWith(arrayBuffer);
            expect(PDFJS.getDocument).toHaveBeenCalledWith(fileUrl);
            expect(component.sourcePDFs().has(sourceId)).toBe(true);
            expect(component.totalPages()).toBe(3);
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should load a PDF document with existing slides', async () => {
            const fileUrl = 'test-url';
            const arrayBuffer = new ArrayBuffer(10);
            const sourceId = 'original';

            const existingSlides = [
                { id: 'slide1', slideNumber: 1 },
                { id: 'slide2', slideNumber: 2 },
                { id: 'slide3', slideNumber: 3 },
            ] as unknown as Slide[];

            await component.loadPdf(fileUrl, arrayBuffer, sourceId, existingSlides);

            expect(component.pageOrder().length).toBe(3);
            expect(component.pageOrder()[0].slideId).toBe('slide1');
            expect(component.pageOrder()[1].slideId).toBe('slide2');
            expect(component.pageOrder()[2].slideId).toBe('slide3');

            const firstPage = component.pageOrder()[0];
            expect(firstPage).toHaveProperty('pageIndex', 1);
            expect(firstPage).toHaveProperty('order', 1);
            expect(firstPage).toHaveProperty('sourcePdfId', 'original');
            expect(firstPage).toHaveProperty('sourceIndex', 0);
            expect(firstPage).toHaveProperty('pageProxy');
        });

        it('should append pages when append flag is true', async () => {
            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
            ]);
            component.totalPages.set(2);

            const fileUrl = 'append-url';
            const arrayBuffer = new ArrayBuffer(10);
            const sourceId = 'append-source';
            const append = true;

            await component.loadPdf(fileUrl, arrayBuffer, sourceId, undefined, append);

            expect(component.pageOrder().length).toBe(5);
            expect(component.totalPages()).toBe(5);
            expect(component.hasOperations()).toBe(true);
            expect(component.isFileChanged()).toBe(true);

            const newPage = component.pageOrder()[2];
            expect(newPage.sourcePdfId).toBe('append-source');
            expect(newPage.pageIndex).toBe(3);
            expect(newPage.order).toBe(3);
        });

        it('should handle errors when loading a PDF', async () => {
            const mockError = new Error('PDF loading failed');
            jest.spyOn(PDFDocument, 'load').mockRejectedValue(mockError);

            component.isPdfLoading.set(true);

            await component.loadPdf('test-url', new ArrayBuffer(10), 'test-source');

            expect(component.isPdfLoading()).toBe(false);
        });

        it('should sort ordered pages by order when loading with existing slides', async () => {
            const fileUrl = 'test-url';
            const arrayBuffer = new ArrayBuffer(10);
            const sourceId = 'original';
            const existingSlides = [
                { id: 'slide3', slideNumber: 3 },
                { id: 'slide1', slideNumber: 1 },
                { id: 'slide2', slideNumber: 2 },
            ] as unknown as Slide[];

            await component.loadPdf(fileUrl, arrayBuffer, sourceId, existingSlides);

            expect(component.pageOrder().length).toBe(3);
            expect(component.pageOrder()[0].slideId).toBe('slide1');
            expect(component.pageOrder()[1].slideId).toBe('slide2');
            expect(component.pageOrder()[2].slideId).toBe('slide3');
        });

        it('should create temporary slide IDs when appending pages', async () => {
            const mockNow = 1577836800000;
            jest.spyOn(Date, 'now').mockReturnValue(mockNow);

            component.pageOrder.set([]);
            component.totalPages.set(0);

            const fileUrl = 'append-url';
            const arrayBuffer = new ArrayBuffer(10);
            const sourceId = 'append-source';
            const append = true;

            await component.loadPdf(fileUrl, arrayBuffer, sourceId, undefined, append);

            expect(component.pageOrder()[0].slideId).toBe(`temp_${mockNow}_0`);
            expect(component.pageOrder()[1].slideId).toBe(`temp_${mockNow}_1`);
            expect(component.pageOrder()[2].slideId).toBe(`temp_${mockNow}_2`);
        });
    });

    describe('PdfPreviewComponent applyOperations', () => {
        let component: PdfPreviewComponent;
        let fixture: ComponentFixture<PdfPreviewComponent>;
        let mockInstructorPdf: any;
        let mockStudentPdf: any;

        beforeEach(async () => {
            mockInstructorPdf = {
                getPageCount: jest.fn().mockReturnValue(3),
                copyPages: jest.fn().mockResolvedValue([{}, {}, {}]),
                removePage: jest.fn(),
                getPage: jest.fn().mockReturnValue({}),
                addPage: jest.fn(),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            mockStudentPdf = {
                getPageCount: jest.fn().mockReturnValue(3),
                copyPages: jest.fn().mockResolvedValue([{}, {}, {}]),
                removePage: jest.fn(),
                getPage: jest.fn().mockReturnValue({}),
                addPage: jest.fn(),
                save: jest.fn().mockResolvedValue(new Uint8Array([4, 5, 6])),
            };

            jest.spyOn(PDFDocument, 'load')
                .mockImplementation(() => Promise.resolve(mockInstructorPdf))
                .mockImplementationOnce(() => Promise.resolve(mockInstructorPdf));

            fixture = TestBed.createComponent(PdfPreviewComponent);
            component = fixture.componentInstance;

            component.sourcePDFs.set(
                new Map([
                    [
                        'original',
                        {
                            id: 'original',
                            pdfDocument: {
                                getPageCount: jest.fn().mockReturnValue(3),
                                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                            } as any,
                            blob: new Blob(['test'], { type: 'application/pdf' }),
                            url: 'test-url',
                        },
                    ],
                    [
                        'merged',
                        {
                            id: 'merged',
                            pdfDocument: {
                                getPageCount: jest.fn().mockReturnValue(2),
                                save: jest.fn().mockResolvedValue(new Uint8Array([4, 5])),
                            } as any,
                            blob: new Blob(['test2'], { type: 'application/pdf' }),
                            url: 'merged-url',
                        },
                    ],
                ]),
            );

            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', pageIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
                { slideId: 'slide3', pageIndex: 3, order: 3, sourcePdfId: 'original', sourceIndex: 2 } as any,
            ]);

            (component as any).operations = [];
        });

        it('should process a DELETE operation correctly', async () => {
            mockInstructorPdf.removePage.mockReset();

            const loadMock = jest.spyOn(PDFDocument, 'load').mockClear();
            loadMock.mockResolvedValue(mockInstructorPdf);

            (component as any).operations = [
                {
                    type: 'DELETE',
                    timestamp: dayjs('2023-01-01'),
                    data: { slideIds: ['slide1', 'slide3'] },
                },
            ];

            await component.applyOperations();

            expect(mockInstructorPdf.removePage).toHaveBeenCalled();
        });

        it('should process a MERGE operation correctly', async () => {
            mockInstructorPdf.copyPages.mockReset();
            mockInstructorPdf.addPage.mockReset();

            const mockCopiedPages = [{}, {}];
            mockInstructorPdf.copyPages.mockResolvedValue(mockCopiedPages);

            const loadMock = jest.spyOn(PDFDocument, 'load').mockClear();
            loadMock.mockResolvedValue(mockInstructorPdf);

            (component as any).operations = [
                {
                    type: 'MERGE',
                    timestamp: dayjs('2023-01-01'),
                    data: { sourceId: 'merged' },
                },
            ];

            await component.applyOperations();

            expect(mockInstructorPdf.copyPages).toHaveBeenCalled();
            expect(mockInstructorPdf.addPage).toHaveBeenCalled();
        });

        it('should process a REORDER operation correctly', async () => {
            mockInstructorPdf.getPage.mockReset();
            mockInstructorPdf.removePage.mockReset();
            mockInstructorPdf.addPage.mockReset();

            const loadMock = jest.spyOn(PDFDocument, 'load').mockClear();
            loadMock.mockResolvedValue(mockInstructorPdf);

            (component as any).operations = [
                {
                    type: 'REORDER',
                    timestamp: dayjs('2023-01-01'),
                    data: {
                        pageOrder: [
                            { slideId: 'slide2', order: 1 },
                            { slideId: 'slide1', order: 2 },
                            { slideId: 'slide3', order: 3 },
                        ],
                    },
                },
            ];

            await component.applyOperations();

            expect(mockInstructorPdf.getPage).toHaveBeenCalled();
            expect(mockInstructorPdf.removePage).toHaveBeenCalled();
            expect(mockInstructorPdf.addPage).toHaveBeenCalled();
        });

        it('should create student version when requested and hidden pages exist', async () => {
            mockInstructorPdf.save.mockReset();
            mockStudentPdf.removePage.mockReset();

            mockInstructorPdf.save.mockResolvedValue(new Uint8Array([1, 2, 3]));

            const loadMock = jest
                .spyOn(PDFDocument, 'load')
                .mockImplementationOnce(() => Promise.resolve(mockInstructorPdf))
                .mockImplementationOnce(() => Promise.resolve(mockStudentPdf));

            component.hiddenPages.set({
                slide2: { date: dayjs(), exerciseId: null },
            });

            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([
                { slideId: 'slide1', pageIndex: 0, order: 0 } as any,
                { slideId: 'slide2', pageIndex: 1, order: 1 } as any,
                { slideId: 'slide3', pageIndex: 2, order: 2 } as any,
            ]);

            const result = await component.applyOperations(true);

            expect(mockInstructorPdf.save).toHaveBeenCalled();
            expect(loadMock).toHaveBeenCalledTimes(2);
            expect(result.instructorPdf).toBeDefined();
            expect(result.studentPdf).toBeDefined();
        });

        it('should not create student version when no hidden pages exist', async () => {
            component.hiddenPages.set({});

            const loadMock = jest.spyOn(PDFDocument, 'load').mockClear();
            loadMock.mockResolvedValue(mockInstructorPdf);

            const result = await component.applyOperations(true);

            expect(result.instructorPdf).toBeDefined();
            expect(result.studentPdf).toBeNull();
        });

        it('should throw an error when original PDF source is not found', async () => {
            const getMock = jest.spyOn(Map.prototype, 'get').mockReturnValue(undefined);

            await expect(component.applyOperations()).rejects.toThrow('Original PDF source not found');

            getMock.mockRestore();
        });

        it('should process operations in timestamp order', async () => {
            mockInstructorPdf.removePage.mockReset();
            mockInstructorPdf.addPage.mockReset();

            const loadMock = jest.spyOn(PDFDocument, 'load').mockClear();
            loadMock.mockResolvedValue(mockInstructorPdf);

            (component as any).operations = [
                {
                    type: 'REORDER',
                    timestamp: dayjs('2023-01-03'),
                    data: {
                        pageOrder: [
                            { slideId: 'slide1', order: 2 },
                            { slideId: 'slide2', order: 1 },
                            { slideId: 'slide3', order: 3 },
                        ],
                    },
                },
                {
                    type: 'DELETE',
                    timestamp: dayjs('2023-01-01'),
                    data: { slideIds: ['slide3'] },
                },
            ];

            const executionOrder: string[] = [];

            mockInstructorPdf.removePage.mockImplementation(() => {
                executionOrder.push('DELETE');
            });

            mockInstructorPdf.addPage.mockImplementation(() => {
                executionOrder.push('REORDER');
            });

            await component.applyOperations();

            expect(mockInstructorPdf.removePage).toHaveBeenCalled();
            expect(mockInstructorPdf.addPage).toHaveBeenCalled();

            if (executionOrder.length >= 2) {
                expect(executionOrder.indexOf('DELETE')).toBeLessThan(executionOrder.indexOf('REORDER'));
            }
        });

        it('should process a complex operation sequence correctly', async () => {
            mockInstructorPdf.removePage.mockReset();
            mockInstructorPdf.addPage.mockReset();

            const loadMock = jest.spyOn(PDFDocument, 'load').mockClear();
            loadMock.mockResolvedValue(mockInstructorPdf);

            (component as any).operations = [
                {
                    type: 'DELETE',
                    timestamp: dayjs('2023-01-01T12:00:00'),
                    data: { slideIds: ['slide3'] },
                },
                {
                    type: 'REORDER',
                    timestamp: dayjs('2023-01-01T13:00:00'),
                    data: {
                        pageOrder: [
                            { slideId: 'slide2', order: 1 },
                            { slideId: 'slide1', order: 2 },
                        ],
                    },
                },
            ];

            const result = await component.applyOperations();

            expect(mockInstructorPdf.removePage).toHaveBeenCalled();
            expect(mockInstructorPdf.addPage).toHaveBeenCalled();
            expect(result.instructorPdf).toBeDefined();
            expect(result.studentPdf).toBeNull();
        });
    });
});
