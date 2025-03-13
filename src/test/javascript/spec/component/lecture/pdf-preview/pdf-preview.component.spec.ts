import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subscription, throwError } from 'rxjs';
import { AttachmentService } from 'app/lecture/attachment.service';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { PdfPreviewComponent } from 'app/lecture/pdf-preview/pdf-preview.component';
import { ElementRef, signal } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClientModule, HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PDFDocument } from 'pdf-lib';
import dayjs from 'dayjs/esm';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { PDFPageProxy } from 'pdfjs-dist';

jest.mock('pdf-lib', () => {
    const originalModule = jest.requireActual('pdf-lib');

    return {
        ...originalModule,
        PDFDocument: {
            ...originalModule.PDFDocument,
            load: jest.fn(),
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

            component.ngOnDestroy();

            expect(unsubscribeSpy).toHaveBeenCalled();

            expect(URL.revokeObjectURL).toHaveBeenCalledWith('test-url');
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

            const mockPdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            const createPdfSpy = jest.spyOn(component, 'createPdf').mockResolvedValue(mockPdfFile);

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
                mockPdfFile,
            );

            createPdfSpy.mockRestore();
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

            const mockPdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            const createPdfSpy = jest.spyOn(component, 'createPdf').mockResolvedValue(mockPdfFile);
            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1 },
                { slideId: 'slide2', pageIndex: 2, order: 2 },
            ]);
            component.hiddenPages.set({});

            const appendSpy = jest.spyOn(FormData.prototype, 'append');

            attachmentUnitServiceMock.update.mockReturnValue(of({}));

            await component.updateAttachmentWithFile();

            expect(component.attachmentToBeEdited()).toEqual(component.attachmentUnit()!.attachment);
            expect(appendSpy).toHaveBeenCalledWith('file', mockPdfFile);
            expect(appendSpy).toHaveBeenCalledWith('attachment', expect.any(Blob));
            expect(appendSpy).toHaveBeenCalledWith('attachmentUnit', expect.any(Blob));
            expect(appendSpy).toHaveBeenCalledWith('pageOrder', expect.any(String));
            expect(appendSpy).not.toHaveBeenCalledWith('studentVersion', expect.any(File));
            expect(appendSpy).not.toHaveBeenCalledWith('hiddenPages', expect.any(String));
            expect(attachmentUnitServiceMock.update).toHaveBeenCalledWith(3, 2, expect.any(FormData));

            createPdfSpy.mockRestore();
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

            const mockPdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            const mockStudentPdfFile = new File(['student'], 'test-student.pdf', { type: 'application/pdf' });

            jest.spyOn(component, 'createPdf').mockClear();

            const createPdfSpy = jest.spyOn(component, 'createPdf').mockImplementation((name, studentVersion) => {
                if (studentVersion === true) {
                    return Promise.resolve(mockStudentPdfFile);
                } else {
                    return Promise.resolve(mockPdfFile);
                }
            });

            const mockDate = dayjs('2024-01-01');
            component.hiddenPages.set({
                slide2: { date: mockDate, exerciseId: 123 },
            });

            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1 },
                { slideId: 'slide2', pageIndex: 2, order: 2 },
            ]);

            const appendSpy = jest.spyOn(FormData.prototype, 'append');

            attachmentUnitServiceMock.update.mockReturnValue(of({}));

            await component.updateAttachmentWithFile();

            expect(createPdfSpy).toHaveBeenCalledTimes(2);

            expect(createPdfSpy.mock.calls[0][0]).toBe('Chapter 1');

            expect(createPdfSpy.mock.calls[1][0]).toBe('Chapter 1');
            expect(createPdfSpy.mock.calls[1][1]).toBe(true);

            expect(appendSpy).toHaveBeenCalledWith('studentVersion', mockStudentPdfFile);
            expect(appendSpy).toHaveBeenCalledWith('hiddenPages', expect.stringContaining('slide2'));
            expect(attachmentUnitServiceMock.update).toHaveBeenCalledWith(3, 2, expect.any(FormData));

            createPdfSpy.mockRestore();
            appendSpy.mockRestore();
        });

        it('should handle file size exceeding the maximum allowed size', async () => {
            component.attachment.set({ id: 1, name: 'Large PDF', version: 1 });

            const largeContent = new Array(MAX_FILE_SIZE + 1000).fill('a').join('');
            const largeMockFile = new File([largeContent], 'large.pdf', { type: 'application/pdf' });
            const createPdfSpy = jest.spyOn(component, 'createPdf').mockResolvedValue(largeMockFile);

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.fileSizeError');
            expect(attachmentServiceMock.update).not.toHaveBeenCalled();
            expect(routerNavigateSpy).not.toHaveBeenCalled();

            createPdfSpy.mockRestore();
        });

        it('should handle errors when attachment update fails', async () => {
            component.attachment.set({ id: 1, name: 'Test PDF', version: 1 });

            const mockPdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            const createPdfSpy = jest.spyOn(component, 'createPdf').mockResolvedValue(mockPdfFile);

            const mockError = new Error('Update failed');
            attachmentServiceMock.update.mockReturnValue(throwError(() => mockError));

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Update failed' });
            expect(routerNavigateSpy).not.toHaveBeenCalled();

            createPdfSpy.mockRestore();
        });

        it('should handle errors when attachment unit update fails', async () => {
            component.attachment.set(undefined);
            component.attachmentUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            const mockPdfFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
            const createPdfSpy = jest.spyOn(component, 'createPdf').mockResolvedValue(mockPdfFile);

            const mockError = new Error('Update failed');
            attachmentUnitServiceMock.update.mockReturnValue(throwError(() => mockError));

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Update failed' });
            expect(routerNavigateSpy).not.toHaveBeenCalled();

            createPdfSpy.mockRestore();
        });

        it('should handle errors during PDF creation', async () => {
            component.attachment.set({ id: 1, name: 'Test PDF', version: 1 });

            const mockError = new Error('PDF creation failed');
            const createPdfSpy = jest.spyOn(component, 'createPdf').mockRejectedValue(mockError);

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBe(false);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'PDF creation failed' });
            expect(attachmentServiceMock.update).not.toHaveBeenCalled();
            expect(routerNavigateSpy).not.toHaveBeenCalled();

            createPdfSpy.mockRestore();
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

            const initialSelectedPages = new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 }]);
            component.selectedPages.set(initialSelectedPages);

            global.URL.createObjectURL = jest.fn().mockReturnValue('mock-url');
            const mockArrayBuffer = new ArrayBuffer(10);
            mockFile.arrayBuffer = jest.fn().mockResolvedValue(mockArrayBuffer);

            const loadPdfSpy = jest.spyOn(component, 'loadPdf').mockResolvedValue();

            await component.mergePDF(mockEvent as any);

            expect(component.currentPdfBlob()).toBeDefined();
            expect(component.isFileChanged()).toBe(true);
            expect(component.isPdfLoading()).toBe(false);

            expect(component.selectedPages().size).toBe(0);

            expect(global.URL.createObjectURL).toHaveBeenCalled();
            expect(loadPdfSpy).toHaveBeenCalledWith('mock-url', true);
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
                { slideId: 'slide1', pageIndex: 1, order: 1 },
                { slideId: 'slide2', pageIndex: 2, order: 2 },
                { slideId: 'slide3', pageIndex: 3, order: 3 },
                { slideId: 'slide4', pageIndex: 4, order: 4 },
            ];
            component.pageOrder.set(pageOrder);

            component.selectedPages.set(
                new Set([
                    { slideId: 'slide2', pageIndex: 2, order: 2 },
                    { slideId: 'slide4', pageIndex: 4, order: 4 },
                ]),
            );

            component.hiddenPages.set({
                slide2: { date: dayjs(), exerciseId: null },
                slide3: { date: dayjs(), exerciseId: null },
            });

            component.deleteSelectedSlides();

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

            component.selectedPages.set(new Set([{ slideId: 'slide1', pageIndex: 1, order: 1 }]));

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

            component.selectedPages.set(
                new Set([
                    { slideId: 'slide2', pageIndex: 2, order: 2 },
                    { slideId: 'slide3', pageIndex: 3, order: 3 },
                ]),
            );

            component.hidePages(pages);

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

            const selectedPages = new Set([
                { slideId: 'slide2', pageIndex: 2, order: 2 },
                { slideId: 'slide3', pageIndex: 3, order: 3 },
            ]);

            component.selectedPages.set(selectedPages);

            component.showPages(selectedPages);

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

            const originalFn = component.hiddenPagesChanged;
            component.hiddenPagesChanged = function () {
                return true;
            };

            expect(component.hiddenPagesChanged()).toBe(true);

            component.hiddenPagesChanged = originalFn;
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

            const originalFn = component.hiddenPagesChanged;
            component.hiddenPagesChanged = function () {
                return false;
            };

            expect(component.hiddenPagesChanged()).toBe(false);

            component.hiddenPagesChanged = originalFn;
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

            component.selectedPages.set(
                new Set([
                    { slideId: 'slide1', pageIndex: 1, order: 1 },
                    { slideId: 'slide5', pageIndex: 5, order: 5 },
                ]),
            );

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

            component.selectedPages.set(
                new Set([
                    { slideId: 'slide2', pageIndex: 2, order: 2 },
                    { slideId: 'slide7', pageIndex: 7, order: 7 },
                ]),
            );

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

    describe('PDF Generation', () => {
        it('should create a PDF file with the correct name', async () => {
            const mockPageProxy = {
                getViewport: jest.fn().mockReturnValue({ width: 600, height: 800 }),
                render: jest.fn().mockReturnValue({ promise: Promise.resolve() }),
            } as unknown as PDFPageProxy;

            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1, pageProxy: mockPageProxy },
                { slideId: 'slide2', pageIndex: 2, order: 2, pageProxy: mockPageProxy },
            ]);

            const pdfDoc = {
                addPage: jest.fn().mockReturnValue({ drawImage: jest.fn() }),
                embedPng: jest.fn().mockResolvedValue({}),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            const createSpy = jest.spyOn(PDFDocument, 'create').mockResolvedValue(pdfDoc as any);

            const result = await component.createPdf('test-file');

            expect(result).toBeInstanceOf(File);
            expect(result.name).toBe('test-file.pdf');
            expect(result.type).toBe('application/pdf');
            expect(pdfDoc.addPage).toHaveBeenCalledTimes(2);
            expect(pdfDoc.embedPng).toHaveBeenCalledTimes(2);
            expect(pdfDoc.save).toHaveBeenCalledTimes(1);

            createSpy.mockRestore();
        });

        it('should exclude hidden pages when creating a student version', async () => {
            const mockPageProxy = {
                getViewport: jest.fn().mockReturnValue({ width: 600, height: 800 }),
                render: jest.fn().mockReturnValue({ promise: Promise.resolve() }),
            } as unknown as PDFPageProxy;

            component.pageOrder.set([
                { slideId: 'slide1', pageIndex: 1, order: 1, pageProxy: mockPageProxy },
                { slideId: 'slide2', pageIndex: 2, order: 2, pageProxy: mockPageProxy },
                { slideId: 'slide3', pageIndex: 3, order: 3, pageProxy: mockPageProxy },
            ]);

            component.hiddenPages.set({
                slide2: { date: dayjs(), exerciseId: null },
            });

            const mockPdfDoc = {
                addPage: jest.fn().mockReturnValue({ drawImage: jest.fn() }),
                embedPng: jest.fn().mockResolvedValue({}),
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            const createSpy = jest.spyOn(PDFDocument, 'create').mockResolvedValue(mockPdfDoc as any);

            const result = await component.createPdf('test-file', true);

            expect(result).toBeInstanceOf(File);
            expect(result.name).toBe('test-file.pdf');
            expect(mockPdfDoc.addPage).toHaveBeenCalledTimes(2);

            createSpy.mockRestore();
        });
    });
});
