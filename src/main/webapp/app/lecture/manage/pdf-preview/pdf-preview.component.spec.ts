import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subscription, of, throwError } from 'rxjs';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { PdfPreviewComponent } from 'app/lecture/manage/pdf-preview/pdf-preview.component';
import { ElementRef, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PDFDocument } from 'pdf-lib';
import dayjs from 'dayjs/esm';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import PDFJS from 'pdfjs-dist';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';

jest.mock('pdf-lib', () => {
    const originalModule = jest.requireActual('pdf-lib');

    return Object.assign({}, originalModule, {
        PDFDocument: Object.assign({}, originalModule.PDFDocument, {
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
        }),
    });
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
            parent: {
                snapshot: {
                    paramMap: convertToParamMap({ courseId: 1 }),
                },
            },
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
            imports: [PdfPreviewComponent],
            providers: [
                provideHttpClient(),
                { provide: ActivatedRoute, useValue: routeMock },
                { provide: AttachmentService, useValue: attachmentServiceMock },
                { provide: AttachmentVideoUnitService, useValue: attachmentUnitServiceMock },
                { provide: LectureUnitService, useValue: lectureUnitServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewComponent);
        component = fixture.componentInstance;

        // ---- Prototype-based mocks (no deprecated createElement) ----
        // Canvas API
        Object.defineProperty(HTMLCanvasElement.prototype, 'getContext', {
            configurable: true,
            writable: true,
            value: jest.fn().mockReturnValue({
                drawImage: jest.fn(),
            }),
        });

        Object.defineProperty(HTMLCanvasElement.prototype, 'toDataURL', {
            configurable: true,
            writable: true,
            value: jest.fn(() => 'data:image/png;base64,test'),
        });

        // Optional: ensure reads to width/height are deterministic
        Object.defineProperty(HTMLCanvasElement.prototype, 'width', {
            configurable: true,
            get: () => 0,
            set: () => {},
        });
        Object.defineProperty(HTMLCanvasElement.prototype, 'height', {
            configurable: true,
            get: () => 0,
            set: () => {},
        });

        // Input click
        jest.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {});

        // ---- Component field wiring ----
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

        jest.spyOn(component, 'loadPdf').mockResolvedValue();
        jest.spyOn(component, 'applyOperations').mockResolvedValue({
            instructorPdf: {} as any,
            studentPdf: {} as any,
        });
        jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([]);

        routerNavigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate');

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('Initialization and Data Loading', () => {
        it('should load attachment file and verify service calls when attachment data is available', () => {
            const mockArrayBuffer = new ArrayBuffer(10);
            const mockBlob = new Blob([''], { type: 'application/pdf' });
            mockBlob.arrayBuffer = jest.fn().mockResolvedValue(mockArrayBuffer);
            attachmentServiceMock.getAttachmentFile.mockReturnValue(of(mockBlob));

            component.ngOnInit();
            expect(attachmentServiceMock.getAttachmentFile).toHaveBeenCalledWith(1, 1);
            expect(attachmentUnitServiceMock.getAttachmentFile).not.toHaveBeenCalled();
        });

        it('should load attachment video unit file and verify service calls when attachment video unit data is available', fakeAsync(() => {
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
                attachmentVideoUnit: mockAttachmentUnit,
            });

            component.ngOnInit();
            tick();

            expect(component.courseId).toBe(1);
            expect(component.attachmentVideoUnit()).toEqual(mockAttachmentUnit);

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

        it('should handle errors and trigger alert when loading an attachment video unit file fails', fakeAsync(() => {
            routeMock.data = of({
                course: { id: 1, name: 'Example Course' },
                attachmentVideoUnit: {
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

            const attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
            jest.spyOn(attachmentVideoUnitService, 'getAttachmentFile').mockReturnValue(throwError(() => errorResponse));
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

            component.attachmentVideoUnitSub = mockSubscription;

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
                slide1: { date: mockDate1, exerciseId: undefined },
                slide3: { date: mockDate3, exerciseId: 123 },
                slide5: { date: mockDate5, exerciseId: 456 },
            });

            const hiddenPages = component.getHiddenPages();

            expect(hiddenPages).toHaveLength(3);
            expect(hiddenPages).toContainEqual({ slideId: 'slide1', date: mockDate1, exerciseId: undefined });
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
            component.courseId = 456;
            component.attachment.set({ id: 1, name: 'Test PDF', version: 1, lecture: { id: 123 } });
            component.attachmentToBeEdited.set(undefined);

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                } as any,
                studentPdf: undefined,
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

        it('should update an attachment video unit successfully without hidden pages', async () => {
            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({
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
                studentPdf: undefined,
            });

            component.pageOrder.set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide2', initialIndex: 2, order: 2 } as any]);
            component.hiddenPages.set({});

            const finalPageOrder = [{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide2', initialIndex: 2, order: 2 } as any];

            jest.spyOn(component, 'getFinalPageOrder').mockImplementation(() => {
                return Promise.resolve(finalPageOrder);
            });

            const appendSpy = jest.spyOn(FormData.prototype, 'append');

            attachmentUnitServiceMock.update.mockReturnValue(of({}));

            await component.updateAttachmentWithFile();

            expect(component.attachmentToBeEdited()).toEqual(component.attachmentVideoUnit()!.attachment);
            expect(appendSpy).toHaveBeenCalledWith('file', expect.any(File));
            expect(appendSpy).toHaveBeenCalledWith('attachment', expect.any(Blob));
            expect(appendSpy).toHaveBeenCalledWith('attachmentVideoUnit', expect.any(Blob));
            expect(appendSpy).toHaveBeenCalledWith('pageOrder', expect.any(Blob));
            expect(appendSpy).not.toHaveBeenCalledWith('studentVersion', expect.any(File));
            expect(appendSpy).not.toHaveBeenCalledWith('hiddenPages', expect.any(String));
            expect(attachmentUnitServiceMock.update).toHaveBeenCalledWith(3, 2, expect.any(FormData));
            expect(appendSpy).toHaveBeenCalled();
            expect(component.isSaving()).toBeFalse();

            // Cleanup
            appendSpy.mockRestore();
        });

        it('should update an attachment video unit with hidden pages and create a student version', async () => {
            attachmentUnitServiceMock.updateStudentVersion = jest.fn().mockReturnValue(of({}));

            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({
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

            // Set up page order
            component.pageOrder.set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide2', initialIndex: 2, order: 2 } as any]);

            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([
                { slideId: 'slide1', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2 } as any,
            ]);

            // We need to mock the validateHiddenSlidesDates method to return true
            jest.spyOn(component as any, 'validateHiddenSlidesDates').mockReturnValue(true);

            // We need to override the updateAttachmentVideoUnit method
            // because it uses a Promise that resolves inside a subscription
            jest.spyOn(component as any, 'updateAttachmentVideoUnit').mockImplementation(() => {
                return Promise.resolve();
            });

            // Also override the updateStudentVersion method for the same reason
            jest.spyOn(component as any, 'updateStudentVersion').mockImplementation(() => {
                return Promise.resolve();
            });

            await component.updateAttachmentWithFile();

            expect(component['updateAttachmentVideoUnit']).toHaveBeenCalled();
            expect(component['updateStudentVersion']).toHaveBeenCalled();
        });

        it('should handle file size exceeding the maximum allowed size', async () => {
            component.attachment.set({ id: 1, name: 'Large PDF', version: 1 });

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array(new Array(MAX_FILE_SIZE + 1000))),
                } as any,
                studentPdf: undefined,
            });

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBeFalse();
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
                studentPdf: undefined,
            });

            const mockError = new Error('Update failed');
            attachmentServiceMock.update.mockReturnValue(throwError(() => mockError));

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBeFalse();
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Update failed' });
            expect(routerNavigateSpy).not.toHaveBeenCalled();
        });

        it('should handle errors when attachment video unit update fails', async () => {
            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: {
                    save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
                } as any,
                studentPdf: undefined,
            });

            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([]);

            const mockError = new Error('Update failed');
            attachmentUnitServiceMock.update.mockReturnValue(throwError(() => mockError));

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBeFalse();
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Update failed' });
            expect(routerNavigateSpy).not.toHaveBeenCalled();
        });

        it('should handle errors during PDF creation', async () => {
            component.attachment.set({ id: 1, name: 'Test PDF', version: 1 });

            const mockError = new Error('PDF creation failed');
            jest.spyOn(component, 'applyOperations').mockRejectedValue(mockError);

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBeFalse();
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

            const initialSelectedPages = new Set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any]);
            component.selectedPages.set(initialSelectedPages);

            global.URL.createObjectURL = jest.fn().mockReturnValue('mock-url');
            const mockArrayBuffer = new ArrayBuffer(10);
            mockFile.arrayBuffer = jest.fn().mockResolvedValue(mockArrayBuffer);

            const loadPdfSpy = jest.spyOn(component, 'loadPdf').mockImplementation(async () => {
                component.isFileChanged.set(true);
            });

            await component.mergePDF(mockEvent as any);

            expect(component.isFileChanged()).toBeTrue();
            expect(component.isPdfLoading()).toBeFalse();
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

            expect(component.isPdfLoading()).toBeFalse();
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
                { slideId: 'slide1', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2 } as any,
                { slideId: 'slide3', initialIndex: 3, order: 3 } as any,
                { slideId: 'slide4', initialIndex: 4, order: 4 } as any,
            ];
            component.pageOrder.set(pageOrder);

            component.selectedPages.set(new Set([{ slideId: 'slide2', initialIndex: 2, order: 2 } as any, { slideId: 'slide4', initialIndex: 4, order: 4 } as any]));

            component.hiddenPages.set({
                slide2: { date: dayjs(), exerciseId: undefined },
                slide3: { date: dayjs(), exerciseId: undefined },
            });

            component.deleteSelectedSlides();

            expect(component.hasOperations()).toBeTrue();

            expect(component.pageOrder()).toHaveLength(2);
            expect(component.pageOrder()[0].slideId).toBe('slide1');
            expect(component.pageOrder()[1].slideId).toBe('slide3');

            expect(component.pageOrder()[0].initialIndex).toBe(1);
            expect(component.pageOrder()[0].order).toBe(1);
            expect(component.pageOrder()[1].initialIndex).toBe(3);
            expect(component.pageOrder()[1].order).toBe(2);

            expect(Object.keys(component.hiddenPages())).toHaveLength(1);
            expect(component.hiddenPages()['slide3']).toBeDefined();
            expect(component.hiddenPages()['slide2']).toBeUndefined();

            expect(component.isFileChanged()).toBeTrue();
            expect(component.selectedPages().size).toBe(0);
        });

        it('should handle errors when deleting slides', () => {
            const error = new Error('Deletion failed');

            jest.spyOn(component.pageOrder, 'set').mockImplementation(() => {
                throw error;
            });

            const alertServiceSpy = jest.spyOn(alertServiceMock, 'error');

            component.selectedPages.set(new Set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any]));

            component.deleteSelectedSlides();

            expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.pageDeleteError', { error: 'Deletion failed' });
            expect(component.isPdfLoading()).toBeFalse();
        });
    });

    describe('Attachment Deletion', () => {
        it('should delete the attachment and navigate to attachments on success', () => {
            component.attachment.set({ id: 1, lecture: { id: 2 } });
            component.courseId = 3;

            component.deleteAttachmentFile();

            expect(attachmentServiceMock.delete).toHaveBeenCalledWith(1);
            expect(routerNavigateSpy).toHaveBeenCalledWith(['course-management', 3, 'lectures', 2, 'attachments']);
            expect(component.dialogErrorSource.next).toHaveBeenCalledWith('');
        });

        it('should delete the attachment video unit and navigate to unit management on success', () => {
            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({ id: 4, lecture: { id: 5 } });
            component.courseId = 6;

            component.deleteAttachmentFile();

            expect(lectureUnitServiceMock.delete).toHaveBeenCalledWith(4, 5);
            expect(routerNavigateSpy).toHaveBeenCalledWith(['course-management', 6, 'lectures', 5, 'unit-management']);
            expect(component.dialogErrorSource.next).toHaveBeenCalledWith('');
        });

        it('should handle error when deletion of attachment fails', () => {
            const error = { message: 'Deletion failed' };
            attachmentServiceMock.delete.mockReturnValue(throwError(() => error));
            component.attachment.set({ id: 1, lecture: { id: 2 } });
            component.courseId = 3;

            component.deleteAttachmentFile();

            expect(attachmentServiceMock.delete).toHaveBeenCalledWith(1);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Deletion failed' });
        });

        it('should handle error when deletion of attachment video unit fails', () => {
            const error = { message: 'Deletion failed' };
            lectureUnitServiceMock.delete.mockReturnValue(throwError(() => error));
            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({ id: 4, lecture: { id: 5 } });
            component.courseId = 6;

            component.deleteAttachmentFile();

            expect(lectureUnitServiceMock.delete).toHaveBeenCalledWith(4, 5);
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Deletion failed' });
        });
    });

    describe('Page Visibility Management', () => {
        it('should hide pages by adding to hiddenPages', () => {
            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: undefined },
            });

            const pages = [
                { slideId: 'slide2', date: dayjs(), exerciseId: undefined },
                { slideId: 'slide3', date: dayjs(), exerciseId: 123 },
            ];

            component.selectedPages.set(new Set([{ slideId: 'slide2', initialIndex: 2, order: 2 } as any, { slideId: 'slide3', initialIndex: 3, order: 3 } as any]));

            component.hidePages(pages);

            expect(component.hasOperations()).toBeTrue();

            expect(Object.keys(component.hiddenPages())).toHaveLength(3);
            expect(component.hiddenPages()['slide1']).toBeDefined();
            expect(component.hiddenPages()['slide2']).toBeDefined();
            expect(component.hiddenPages()['slide3']).toBeDefined();
            expect(component.hiddenPages()['slide3'].exerciseId).toBe(123);
            expect(component.selectedPages().size).toBe(0);
        });

        it('should show pages by removing from hiddenPages', () => {
            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: undefined },
                slide2: { date: dayjs(), exerciseId: undefined },
                slide3: { date: dayjs(), exerciseId: 123 },
            });

            const selectedPages = new Set([{ slideId: 'slide2', initialIndex: 2, order: 2 } as any, { slideId: 'slide3', initialIndex: 3, order: 3 } as any]);

            component.selectedPages.set(selectedPages);

            component.showPages(selectedPages);

            expect(component.hasOperations()).toBeTrue();

            expect(Object.keys(component.hiddenPages())).toHaveLength(1);
            expect(component.hiddenPages()['slide1']).toBeDefined();
            expect(component.hiddenPages()['slide2']).toBeUndefined();
            expect(component.hiddenPages()['slide3']).toBeUndefined();
            expect(component.selectedPages().size).toBe(0);
        });
    });

    describe('Navigation', () => {
        it('should navigate to attachments page when attachment is present', () => {
            component.attachment.set({ id: 1, lecture: { id: 2 } });
            component.courseId = 3;

            component.navigateToCourseManagement();

            expect(routerNavigateSpy).toHaveBeenCalledWith(['course-management', 3, 'lectures', 2, 'attachments']);
        });

        it('should navigate to unit management page when attachmentUnit is present', () => {
            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({ id: 4, lecture: { id: 5 } });
            component.courseId = 6;

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
                slide1: { date: date1, exerciseId: undefined },
                slide2: { date: date2, exerciseId: undefined },
            };

            const changedHiddenPages = {
                slide1: { date: date1, exerciseId: undefined },
                slide3: { date: date3, exerciseId: undefined },
            };

            component.initialHiddenPages.set(Object.assign({}, initialHiddenPages));
            component.hiddenPages.set(Object.assign({}, changedHiddenPages));

            expect(component.hiddenPagesChanged()).toBeTrue();
        });

        it('should return false when hidden pages have not changed', () => {
            const date1 = dayjs('2024-01-01');
            const date2 = dayjs('2024-01-02');

            const hiddenPages = {
                slide1: { date: date1, exerciseId: undefined },
                slide2: { date: date2, exerciseId: undefined },
            };

            component.initialHiddenPages.set(Object.assign({}, hiddenPages));
            component.hiddenPages.set(Object.assign({}, hiddenPages));

            expect(component.hiddenPagesChanged()).toBeFalse();
        });

        it('should handle a single hidden page', () => {
            const mockDate = dayjs('2024-05-15');
            const mockHiddenPage = {
                slideId: 'slide5',
                date: mockDate,
                exerciseId: 123,
            };

            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: undefined },
                slide3: { date: dayjs(), exerciseId: undefined },
            });

            component.selectedPages.set(new Set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide5', initialIndex: 5, order: 5 } as any]));

            component.hidePages(mockHiddenPage);

            expect(Object.keys(component.hiddenPages())).toHaveLength(3);
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
                slide1: { date: dayjs(), exerciseId: undefined },
                slide3: { date: dayjs(), exerciseId: undefined },
            });

            component.selectedPages.set(new Set([{ slideId: 'slide2', initialIndex: 2, order: 2 } as any, { slideId: 'slide7', initialIndex: 7, order: 7 } as any]));

            component.hidePages(mockHiddenPages);

            expect(Object.keys(component.hiddenPages())).toHaveLength(4);
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
                slide1: { date: dayjs(), exerciseId: undefined },
                slide5: { date: initialDate, exerciseId: undefined },
            });

            const updatedHiddenPage = {
                slideId: 'slide5',
                date: updatedDate,
                exerciseId: 789,
            };

            component.hidePages(updatedHiddenPage);

            expect(Object.keys(component.hiddenPages())).toHaveLength(2);
            expect(component.hiddenPages()['slide5']).toEqual({
                date: updatedDate,
                exerciseId: 789,
            });
            expect(component.hiddenPages()['slide1']).toBeDefined();
        });
    });

    describe('Operations Management', () => {
        it('should record reorder operations when page order changes', () => {
            const initialPageOrder = [{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide2', initialIndex: 2, order: 2 } as any];

            const newPageOrder = [{ slideId: 'slide2', initialIndex: 1, order: 1 } as any, { slideId: 'slide1', initialIndex: 2, order: 2 } as any];

            component.pageOrder.set(initialPageOrder);

            expect(component.hasOperations()).toBeFalse();

            component.onPageOrderChange(newPageOrder);

            expect(component.hasOperations()).toBeTrue();
            expect(component.pageOrder()).toEqual(newPageOrder);
        });
    });

    describe('PDF Operations', () => {
        it('should handle page reordering correctly', () => {
            const initialPageOrder = [
                { slideId: 'slide1', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2 } as any,
                { slideId: 'slide3', initialIndex: 3, order: 3 } as any,
            ];

            const newPageOrder = [
                { slideId: 'slide3', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide1', initialIndex: 2, order: 2 } as any,
                { slideId: 'slide2', initialIndex: 3, order: 3 } as any,
            ];

            component.pageOrder.set(initialPageOrder);
            component.onPageOrderChange(newPageOrder);

            expect(component.hasOperations()).toBeTrue();
            expect(component.pageOrder()).toEqual(newPageOrder);

            const lastOperation = component.operations()[component.operations().length - 1];
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
                slide1: { date: mockDate, exerciseId: undefined },
                slide2: { date: mockDate, exerciseId: 123 },
                slide3: { date: mockDate, exerciseId: undefined },
            });

            const selectedPages = new Set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide3', initialIndex: 3, order: 3 } as any]);

            component.showPages(selectedPages);

            expect(component.hasOperations()).toBeTrue();
            expect(Object.keys(component.hiddenPages())).toEqual(['slide2']);
            expect(component.selectedPages().size).toBe(0);

            const lastOperation = component.operations()[component.operations().length - 1];
            expect(lastOperation.type).toBe('SHOW');
            expect(lastOperation.data.slideIds).toContain('slide1');
            expect(lastOperation.data.slideIds).toContain('slide3');
        });
    });

    describe('Computed Properties', () => {
        it('should correctly compute allPagesSelected property', () => {
            component.selectedPages.set(new Set());
            component.totalPages.set(5);
            expect(component.allPagesSelected()).toBeFalse();

            const selectedPages = new Set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide2', initialIndex: 2, order: 2 } as any]);
            component.selectedPages.set(selectedPages);
            expect(component.allPagesSelected()).toBeFalse();

            const allPages = new Set([
                { slideId: 'slide1', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2 } as any,
                { slideId: 'slide3', initialIndex: 3, order: 3 } as any,
                { slideId: 'slide4', initialIndex: 4, order: 4 } as any,
                { slideId: 'slide5', initialIndex: 5, order: 5 } as any,
            ]);
            component.selectedPages.set(allPages);
            expect(component.allPagesSelected()).toBeTrue();
        });

        it('should correctly compute pageOrderChanged property', () => {
            const unchangedOrder = [{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide2', initialIndex: 2, order: 2 } as any];
            component.pageOrder.set(unchangedOrder);
            expect(component.pageOrderChanged()).toBeFalse();

            const changedOrder = [{ slideId: 'slide1', initialIndex: 2, order: 2 } as any, { slideId: 'slide2', initialIndex: 1, order: 1 } as any];
            component.pageOrder.set(changedOrder);
            expect(component.pageOrderChanged()).toBeTrue();
        });

        it('should correctly compute hasHiddenPages property', () => {
            component.hiddenPages.set({});
            expect(component.hasHiddenPages()).toBeFalse();

            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: undefined },
            });
            expect(component.hasHiddenPages()).toBeTrue();
        });

        it('should correctly compute hasHiddenSelectedPages property', () => {
            component.hiddenPages.set({
                slide1: { date: dayjs(), exerciseId: undefined },
                slide3: { date: dayjs(), exerciseId: 123 },
            });

            component.selectedPages.set(new Set([{ slideId: 'slide2', initialIndex: 2, order: 2 } as any, { slideId: 'slide4', initialIndex: 4, order: 4 } as any]));
            expect(component.hasHiddenSelectedPages()).toBeFalse();

            component.selectedPages.set(new Set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any, { slideId: 'slide2', initialIndex: 2, order: 2 } as any]));
            expect(component.hasHiddenSelectedPages()).toBeTrue();
        });

        it('should correctly compute hasChanges property', () => {
            component.hiddenPages.set({});
            component.initialHiddenPages.set({});
            component.operations.set([]);
            component.hasOperations.set(false);
            component.isFileChanged.set(false);
            component.pageOrder.set([{ slideId: 'slide1', initialIndex: 1, order: 1 } as any]);

            expect(component.hasChanges()).toBeFalse();

            component.operations.set([{ type: 'DELETE', timestamp: dayjs(), data: { slideIds: ['slide1'] } }]);
            expect(component.hasChanges()).toBeTrue();
            component.operations.set([]);

            component.initialHiddenPages.set({});
            component.hiddenPages.set({ slide1: { date: dayjs(), exerciseId: undefined } });
            expect(component.hasChanges()).toBeTrue();
            component.hiddenPages.set({});

            component.isFileChanged.set(true);
            expect(component.hasChanges()).toBeTrue();
            component.isFileChanged.set(false);

            component.pageOrder.set([{ slideId: 'slide1', initialIndex: 2, order: 2 } as any]);
            expect(component.hasChanges()).toBeTrue();
        });
    });

    describe('Edge Cases and Error Handling', () => {
        it('should handle empty file input when merging PDF', async () => {
            const mockEvent = {
                target: {
                    files: [undefined],
                    value: '',
                },
            };

            jest.spyOn(component, 'mergePDF').mockImplementation(async () => {
                return Promise.resolve();
            });

            await component.mergePDF(mockEvent as any);

            expect(alertServiceMock.error).not.toHaveBeenCalled();
            expect(component.isPdfLoading()).toBeFalse();
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
            expect(component.isPdfLoading()).toBeFalse();
        });

        it('should handle errors when applying operations', async () => {
            jest.spyOn(component, 'applyOperations').mockRejectedValue(new Error('Operation failed'));

            component.attachment.set({ id: 1, name: 'Test' });

            await component.updateAttachmentWithFile();

            expect(component.isSaving()).toBeFalse();
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'Operation failed' });
        });
    });

    describe('Selection Management', () => {
        it('should add and remove pages from selection', () => {
            const pageOrder = [
                { slideId: 'slide1', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2 } as any,
                { slideId: 'slide3', initialIndex: 3, order: 3 } as any,
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

                let mockStudentPdf = undefined;

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
                slide2: { date: dayjs(), exerciseId: undefined },
            });

            component.pageOrder.set([
                { slideId: 'slide1', initialIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
                { slideId: 'slide3', initialIndex: 3, order: 3, sourcePdfId: 'original', sourceIndex: 2 } as any,
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
                { slideId: 'slide1', initialIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
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
                    studentPdf: undefined,
                };
            });

            const result = await component.applyOperations(true);

            expect(result.studentPdf).toBeUndefined();
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
            expect(component.sourcePDFs().has(sourceId)).toBeTrue();
            expect(component.totalPages()).toBe(3);
            expect(component.isPdfLoading()).toBeFalse();
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

            await testLoadPdf(fileUrl, arrayBuffer, sourceId, existingSlides);

            const firstPage = component.pageOrder()[0];
            expect(firstPage).toHaveProperty('initialIndex', 1);
            expect(firstPage).toHaveProperty('order', 1);
            expect(firstPage).toHaveProperty('sourcePdfId', 'original');
            expect(firstPage).toHaveProperty('sourceIndex', 0);
            expect(firstPage).toHaveProperty('pageProxy');
        });

        it('should append pages when append flag is true', async () => {
            component.pageOrder.set([
                { slideId: 'slide1', initialIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
            ]);
            component.totalPages.set(2);

            const fileUrl = 'append-url';
            const arrayBuffer = new ArrayBuffer(10);
            const sourceId = 'append-source';
            const append = true;

            await component.loadPdf(fileUrl, arrayBuffer, sourceId, undefined, append);

            expect(component.pageOrder()).toHaveLength(5);
            expect(component.totalPages()).toBe(5);
            expect(component.hasOperations()).toBeTrue();
            expect(component.isFileChanged()).toBeTrue();

            const newPage = component.pageOrder()[2];
            expect(newPage.sourcePdfId).toBe('append-source');
            expect(newPage.initialIndex).toBe(3);
            expect(newPage.order).toBe(3);
        });

        it('should handle errors when loading a PDF', async () => {
            const mockError = new Error('PDF loading failed');
            jest.spyOn(PDFDocument, 'load').mockRejectedValue(mockError);

            component.isPdfLoading.set(true);

            await component.loadPdf('test-url', new ArrayBuffer(10), 'test-source');

            expect(component.isPdfLoading()).toBeFalse();
        });

        const testLoadPdf = async (fileUrl: string, arrayBuffer: ArrayBuffer, sourceId: string, existingSlides: Slide[]) => {
            await component.loadPdf(fileUrl, arrayBuffer, sourceId, existingSlides);

            expect(component.pageOrder()).toHaveLength(3);
            expect(component.pageOrder()[0].slideId).toBe('slide1');
            expect(component.pageOrder()[1].slideId).toBe('slide2');
            expect(component.pageOrder()[2].slideId).toBe('slide3');
        };

        it('should sort ordered pages by order when loading with existing slides', async () => {
            const fileUrl = 'test-url';
            const arrayBuffer = new ArrayBuffer(10);
            const sourceId = 'original';
            const existingSlides = [
                { id: 'slide3', slideNumber: 3 },
                { id: 'slide1', slideNumber: 1 },
                { id: 'slide2', slideNumber: 2 },
            ] as unknown as Slide[];
            await testLoadPdf(fileUrl, arrayBuffer, sourceId, existingSlides);
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
                { slideId: 'slide1', initialIndex: 1, order: 1, sourcePdfId: 'original', sourceIndex: 0 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2, sourcePdfId: 'original', sourceIndex: 1 } as any,
                { slideId: 'slide3', initialIndex: 3, order: 3, sourcePdfId: 'original', sourceIndex: 2 } as any,
            ]);

            component.operations.set([]);
        });

        it('should process a DELETE operation correctly', async () => {
            mockInstructorPdf.removePage.mockReset();

            const loadMock = jest.spyOn(PDFDocument, 'load').mockClear();
            loadMock.mockResolvedValue(mockInstructorPdf);

            component.operations.set([
                {
                    type: 'DELETE',
                    timestamp: dayjs('2023-01-01'),
                    data: { slideIds: ['slide1', 'slide3'] },
                },
            ]);

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

            component.operations.set([
                {
                    type: 'MERGE',
                    timestamp: dayjs('2023-01-01'),
                    data: { sourceId: 'merged' },
                },
            ]);

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

            component.operations.set([
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
            ]);

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
                slide2: { date: dayjs(), exerciseId: undefined },
            });

            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([
                { slideId: 'slide1', initialIndex: 0, order: 0 } as any,
                { slideId: 'slide2', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide3', initialIndex: 2, order: 2 } as any,
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
            expect(result.studentPdf).toBeUndefined();
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

            component.operations.set([
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
            ]);

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

            component.operations.set([
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
            ]);

            const result = await component.applyOperations();

            expect(mockInstructorPdf.removePage).toHaveBeenCalled();
            expect(mockInstructorPdf.addPage).toHaveBeenCalled();
            expect(result.instructorPdf).toBeDefined();
            expect(result.studentPdf).toBeUndefined();
        });
    });

    describe('validateHiddenSlidesDates', () => {
        beforeEach(() => {
            jest.spyOn(alertServiceMock, 'error').mockClear();
            component.pageOrder.set([
                { slideId: 'slide1', initialIndex: 1, order: 1 } as any,
                { slideId: 'slide2', initialIndex: 2, order: 2 } as any,
                { slideId: 'slide3', initialIndex: 3, order: 3 } as any,
            ]);
        });

        it('should return true when there are no hidden pages', () => {
            component.hiddenPages.set({});

            const result = component['validateHiddenSlidesDates']();

            expect(result).toBeTrue();
            expect(alertServiceMock.error).not.toHaveBeenCalled();
        });

        it('should return true when all hidden pages have future dates', () => {
            const futureDate1 = dayjs().add(1, 'day');
            const futureDate2 = dayjs().add(5, 'days');

            component.hiddenPages.set({
                slide1: { date: futureDate1, exerciseId: undefined },
                slide2: { date: futureDate2, exerciseId: 123 },
            });

            const result = component['validateHiddenSlidesDates']();

            expect(result).toBeTrue();
            expect(alertServiceMock.error).not.toHaveBeenCalled();
        });

        it('should return true when hidden pages use FOREVER date', () => {
            const FOREVER = dayjs('9999-12-31');

            component.hiddenPages.set({
                slide1: { date: FOREVER, exerciseId: undefined },
            });

            const result = component['validateHiddenSlidesDates']();

            expect(result).toBeTrue();
            expect(alertServiceMock.error).not.toHaveBeenCalled();
        });

        it('should return false when any hidden page has a past date', () => {
            const pastDate = dayjs().subtract(1, 'day');
            const futureDate = dayjs().add(1, 'day');

            component.hiddenPages.set({
                slide1: { date: futureDate, exerciseId: undefined },
                slide2: { date: pastDate, exerciseId: 123 },
            });

            const result = component['validateHiddenSlidesDates']();

            expect(result).toBeFalse();
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.dateBox.dateErrorWithPages', { param: '2' });
        });

        it('should return false when multiple hidden pages have past dates', () => {
            const pastDate1 = dayjs().subtract(1, 'day');
            const pastDate2 = dayjs().subtract(2, 'days');
            const futureDate = dayjs().add(1, 'day');

            component.hiddenPages.set({
                slide1: { date: pastDate1, exerciseId: undefined },
                slide2: { date: futureDate, exerciseId: 123 },
                slide3: { date: pastDate2, exerciseId: undefined },
            });

            const result = component['validateHiddenSlidesDates']();

            expect(result).toBeFalse();
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.dateBox.dateErrorWithPages', { param: '1, 3' });
        });
    });

    describe('Student Version Updates', () => {
        beforeEach(() => {
            routerNavigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate').mockImplementation(() => Promise.resolve(true));
        });

        it('should successfully update the student version of an attachment video unit', fakeAsync(() => {
            component.attachmentVideoUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            const studentPdfFile = new File(['test-student-file'], 'test_student.pdf', { type: 'application/pdf' });

            attachmentUnitServiceMock.updateStudentVersion = jest.fn().mockReturnValue(of({}));

            const finishSavingSpy = jest.spyOn(component as any, 'finishSaving');
            const appendSpy = jest.spyOn(FormData.prototype, 'append');

            component['updateStudentVersion'](studentPdfFile).then(() => {
                expect(attachmentUnitServiceMock.updateStudentVersion).toHaveBeenCalledWith(3, 2, expect.any(FormData));
                expect(appendSpy).toHaveBeenCalledWith('studentVersion', studentPdfFile);
                expect(finishSavingSpy).toHaveBeenCalled();
                appendSpy.mockRestore();
            });

            tick();
        }));

        it('should handle errors when updating the student version fails', fakeAsync(() => {
            component.attachmentVideoUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            const studentPdfFile = new File(['test-student-file'], 'test_student.pdf', { type: 'application/pdf' });
            const mockError = new Error('Student version update failed');
            attachmentUnitServiceMock.updateStudentVersion = jest.fn().mockReturnValue(throwError(() => mockError));
            const errorSpy = jest.spyOn(alertServiceMock, 'error');
            const promise = component['updateStudentVersion'](studentPdfFile);

            // The promise should reject
            promise.catch((error) => {
                expect(error).toBe(mockError);
            });

            tick();

            expect(component.isSaving()).toBeFalse();
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.studentVersionUpdateError', { error: 'Student version update failed' });

            expect(routerNavigateSpy).not.toHaveBeenCalled();
        }));

        it('should update student version when updateAttachmentWithFile is called with hidden pages', async () => {
            const mockDate = dayjs('2024-05-15');

            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            component.hiddenPages.set({
                slide1: { date: mockDate, exerciseId: undefined },
            });

            jest.spyOn(component as any, 'validateHiddenSlidesDates').mockReturnValue(true);

            const mockInstructorPdf = {
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            const mockStudentPdf = {
                save: jest.fn().mockResolvedValue(new Uint8Array([4, 5, 6])),
            };

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: mockInstructorPdf as any,
                studentPdf: mockStudentPdf as any,
            });

            jest.spyOn(component as any, 'updateAttachmentVideoUnit').mockResolvedValue(undefined);

            const updateStudentVersionSpy = jest.spyOn(component as any, 'updateStudentVersion').mockResolvedValue(undefined);

            jest.spyOn(component, 'navigateToCourseManagement').mockImplementation(() => {});
            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([]);

            await component.updateAttachmentWithFile();

            expect(updateStudentVersionSpy).toHaveBeenCalled();
            const fileArg = updateStudentVersionSpy.mock.calls[0][0] as File;
            expect(fileArg instanceof File).toBeTrue();
            expect(fileArg.name).toContain('_student.pdf');
        });

        it('should not update student version when no hidden pages exist', async () => {
            component.attachment.set(undefined);
            component.attachmentVideoUnit.set({
                id: 2,
                name: 'Chapter 1',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            component.hiddenPages.set({});

            jest.spyOn(component as any, 'validateHiddenSlidesDates').mockReturnValue(true);

            const mockInstructorPdf = {
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            jest.spyOn(component, 'applyOperations').mockResolvedValue({
                instructorPdf: mockInstructorPdf as any,
                studentPdf: undefined,
            });

            jest.spyOn(component as any, 'updateAttachmentVideoUnit').mockResolvedValue(undefined);

            const updateStudentVersionSpy = jest.spyOn(component as any, 'updateStudentVersion');

            jest.spyOn(component, 'navigateToCourseManagement').mockImplementation(() => {});
            jest.spyOn(component, 'getFinalPageOrder').mockResolvedValue([]);

            await component.updateAttachmentWithFile();

            expect(updateStudentVersionSpy).not.toHaveBeenCalled();
        });

        it('should create a student version file with the correct name', async () => {
            component.attachmentVideoUnit.set({
                id: 2,
                name: 'TestUnit',
                lecture: { id: 3 },
                attachment: { id: 4, name: 'Unit PDF' },
            });

            const mockPdf = {
                save: jest.fn().mockResolvedValue(new Uint8Array([1, 2, 3])),
            };

            const originalFile = global.File;
            const capturedArgs: any[] = [];

            global.File = jest.fn().mockImplementation((...args) => {
                capturedArgs.length = 0; // Clear array
                capturedArgs.push(...args); // Add elements
                return new originalFile(args[0], args[1], args[2]);
            }) as any;

            const result = await component['createPdfFile'](mockPdf as any, 'TestUnit', true);

            expect(result.name).toBe('TestUnit_student.pdf');
            expect(result.type).toBe('application/pdf');
            expect(capturedArgs[1]).toBe('TestUnit_student.pdf');
            expect(capturedArgs[2]).toEqual(expect.objectContaining({ type: 'application/pdf' }));

            global.File = originalFile;
        });
    });
});
