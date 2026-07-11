import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';
import { MockPdfEngineService } from 'test/helpers/mocks/service/mock-pdf-engine.service';
import { OrderedPage, PdfPreviewComponent } from 'app/lecture/manage/pdf-preview/pdf-preview.component';

describe('PdfPreviewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfPreviewComponent;
    let fixture: ComponentFixture<PdfPreviewComponent>;
    let engineService: MockPdfEngineService;
    let attachmentService: { update: ReturnType<typeof vi.fn>; getAttachmentFile: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };
    let attachmentVideoUnitService: { update: ReturnType<typeof vi.fn>; updateStudentVersion: ReturnType<typeof vi.fn>; getAttachmentFile: ReturnType<typeof vi.fn> };
    let lectureUnitService: { delete: ReturnType<typeof vi.fn> };
    let alertService: { error: ReturnType<typeof vi.fn>; success: ReturnType<typeof vi.fn>; addAlert: ReturnType<typeof vi.fn> };
    let router: { navigate: ReturnType<typeof vi.fn> };
    // Mutable route data so ngOnInit-driven tests can inject an attachment / attachmentVideoUnit resolver payload.
    let routeData: any;

    beforeEach(async () => {
        engineService = new MockPdfEngineService();
        attachmentService = { update: vi.fn(() => of({})), getAttachmentFile: vi.fn(() => of(new Blob())), delete: vi.fn(() => of({})) };
        attachmentVideoUnitService = { update: vi.fn(() => of({})), updateStudentVersion: vi.fn(() => of({})), getAttachmentFile: vi.fn(() => of(new Blob())) };
        lectureUnitService = { delete: vi.fn(() => of({})) };
        alertService = { error: vi.fn(), success: vi.fn(), addAlert: vi.fn() };
        router = { navigate: vi.fn() };
        routeData = {};
        // `data` is read lazily inside ngOnInit, so wrapping the current `routeData` in a getter lets each
        // test set the resolver payload before it manually calls ngOnInit().
        const route = {
            get data() {
                return of(routeData);
            },
            parent: { snapshot: { paramMap: { get: () => '5' } } },
        };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewComponent],
            providers: [
                provideHttpClient(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                { provide: AlertService, useValue: alertService },
                { provide: AttachmentService, useValue: attachmentService },
                { provide: AttachmentVideoUnitService, useValue: attachmentVideoUnitService },
                { provide: LectureUnitService, useValue: lectureUnitService },
                { provide: PdfEngineService, useValue: engineService },
                { provide: DialogService, useValue: { open: vi.fn() } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.restoreAllMocks());

    /** Loads the original 3-page document into the component via the engine. */
    async function loadOriginal(pageCount = 3): Promise<void> {
        engineService.engine.openDocumentBuffer.mockReturnValueOnce({
            toPromise: () =>
                Promise.resolve({ id: 'original', pageCount, pages: Array.from({ length: pageCount }, (_, i) => ({ index: i, size: { width: 595, height: 842 }, rotation: 0 })) }),
            wait: () => {},
            abort: () => {},
        } as any);
        await component.loadPdf('blob:original', new ArrayBuffer(8), 'original');
    }

    /** Makes the next `openDocumentBuffer` call resolve to a document with the given id and page count. */
    function mockNextOpen(id: string, pageCount: number): void {
        engineService.engine.openDocumentBuffer.mockReturnValueOnce({
            toPromise: () => Promise.resolve({ id, pageCount, pages: Array.from({ length: pageCount }, (_, i) => ({ index: i, size: { width: 595, height: 842 }, rotation: 0 })) }),
            wait: () => {},
            abort: () => {},
        } as any);
    }

    /** Builds a File-like object whose `arrayBuffer()` resolves, so mergePDF can be exercised without a real file picker. */
    function pdfFileEvent(type = 'application/pdf'): Event {
        const file = { type, arrayBuffer: () => Promise.resolve(new ArrayBuffer(8)) } as unknown as File;
        return { target: { files: [file], value: 'x' } } as unknown as Event;
    }

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open the PDF through the engine and build the page order', async () => {
        await loadOriginal(3);

        expect(engineService.engine.openDocumentBuffer).toHaveBeenCalledWith({ id: 'original', content: expect.any(ArrayBuffer) });
        expect(component.totalPages()).toBe(3);
        expect(component.pageOrder().length).toBe(3);
        expect(component.pageOrder()[0]).toMatchObject({ sourcePdfId: 'original', sourceIndex: 0, order: 1 });
    });

    it('should record a DELETE (and renumbering REORDER) operation and drop the page from the order', async () => {
        await loadOriginal(3);
        const toDelete = component.pageOrder()[1];
        component.selectedPages.set(new Set([toDelete]));

        component.deleteSelectedSlides();

        expect(component.pageOrder().map((p) => p.slideId)).not.toContain(toDelete.slideId);
        expect(component.pageOrder().length).toBe(2);
        expect(component.operations().map((op) => op.type)).toEqual(expect.arrayContaining(['DELETE', 'REORDER']));
    });

    it('should surface an error and stop loading when deleting selected slides throws', async () => {
        await loadOriginal(3);
        // A selected page whose slideId getter throws makes the delete pipeline throw, hitting the catch branch.
        const brokenPage = {
            get slideId(): string {
                throw new Error('delete boom');
            },
        } as unknown as OrderedPage;
        component.selectedPages.set(new Set([brokenPage]));

        component.deleteSelectedSlides();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.pageDeleteError', { error: 'delete boom' });
        expect(component.isPdfLoading()).toBe(false);
    });

    it('should record a REORDER operation when the page order changes', async () => {
        await loadOriginal(3);
        const reversed: OrderedPage[] = [...component.pageOrder()].reverse().map((p, i) => ({ ...p, order: i + 1 }));

        component.onPageOrderChange(reversed);

        expect(component.pageOrder()[0].sourceIndex).toBe(2);
        expect(component.operations().some((op) => op.type === 'REORDER')).toBe(true);
    });

    it('should hide and show pages, updating the hidden-pages map', async () => {
        await loadOriginal(3);
        const page = component.pageOrder()[0];

        component.hidePages({ slideId: page.slideId, date: dayjs().add(1, 'day'), exerciseId: undefined });
        expect(component.hiddenPages()[page.slideId]).toBeDefined();

        component.showPages(new Set([page]));
        expect(component.hiddenPages()[page.slideId]).toBeUndefined();
    });

    it('should build the instructor PDF from the final page order via mergePages', async () => {
        await loadOriginal(3);

        const { instructorBytes, studentBytes } = await component.applyOperations(true);

        expect(instructorBytes).toBeInstanceOf(ArrayBuffer);
        expect(studentBytes).toBeUndefined();
        expect(engineService.engine.mergePages).toHaveBeenCalledOnce();
        expect(engineService.engine.mergePages).toHaveBeenCalledWith([
            { docId: 'original', pageIndices: [0] },
            { docId: 'original', pageIndices: [1] },
            { docId: 'original', pageIndices: [2] },
        ]);
    });

    it('should additionally build a student PDF without the hidden pages', async () => {
        await loadOriginal(3);
        const hidden = component.pageOrder()[1];
        component.hidePages({ slideId: hidden.slideId, date: dayjs().add(1, 'day'), exerciseId: undefined });

        const { studentBytes } = await component.applyOperations(true);

        expect(studentBytes).toBeInstanceOf(ArrayBuffer);
        // Instructor merge (all 3 pages) + student merge (2 visible pages).
        expect(engineService.engine.mergePages).toHaveBeenCalledTimes(2);
        expect(engineService.engine.mergePages).toHaveBeenLastCalledWith([
            { docId: 'original', pageIndices: [0] },
            { docId: 'original', pageIndices: [2] },
        ]);
    });

    it('should reflect deletes and reorders in the final page order', async () => {
        await loadOriginal(3);
        component.selectedPages.set(new Set([component.pageOrder()[0]]));
        component.deleteSelectedSlides();

        const finalOrder = await component.getFinalPageOrder();

        expect(finalOrder.length).toBe(2);
        expect(finalOrder.map((p) => p.order)).toEqual([1, 2]);
    });

    it('should save a regular attachment through the attachment service', async () => {
        await loadOriginal(2);
        component.attachment.set({ id: 7, version: 1, lecture: { id: 3 } } as any);

        await component.updateAttachmentWithFile();

        expect(attachmentService.update).toHaveBeenCalledOnce();
        expect(alertService.success).toHaveBeenCalled();
    });

    it('should save an attachment video unit with both instructor and student versions', async () => {
        await loadOriginal(2);
        component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 }, attachment: { id: 11, version: 1 } } as any);
        const hidden = component.pageOrder()[0];
        component.hidePages({ slideId: hidden.slideId, date: dayjs().add(1, 'day'), exerciseId: undefined });

        await component.updateAttachmentWithFile();

        expect(attachmentVideoUnitService.update).toHaveBeenCalledOnce();
        expect(attachmentVideoUnitService.updateStudentVersion).toHaveBeenCalledOnce();
        expect(alertService.success).toHaveBeenCalled();
    });

    it('should abort saving when a hidden page has a past release date', async () => {
        await loadOriginal(2);
        component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 }, attachment: {} } as any);
        const page = component.pageOrder()[0];
        component.hidePages({ slideId: page.slideId, date: dayjs().subtract(1, 'day'), exerciseId: undefined });

        await component.updateAttachmentWithFile();

        expect(attachmentVideoUnitService.update).not.toHaveBeenCalled();
        expect(alertService.error).toHaveBeenCalled();
    });

    it('should reject building a student version when every page is hidden', async () => {
        await loadOriginal(2);
        component.pageOrder().forEach((page) => component.hidePages({ slideId: page.slideId, date: dayjs().add(1, 'day'), exerciseId: undefined }));

        await expect(component.applyOperations(true)).rejects.toThrow(/no visible pages/);
    });

    it('should close opened engine documents on destroy', async () => {
        await loadOriginal(3);

        fixture.destroy();
        await new Promise<void>((resolve) => setTimeout(resolve, 0));

        expect(engineService.engine.closeDocument).toHaveBeenCalled();
    });

    describe('ngOnInit', () => {
        it('should resolve the courseId from the parent route and fetch the attachment PDF', async () => {
            routeData = { attachment: { id: 42, lecture: { id: 3 } } };

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.courseId()).toBe(5);
            expect(component.attachment()).toEqual({ id: 42, lecture: { id: 3 } });
            expect(attachmentService.getAttachmentFile).toHaveBeenCalledWith(5, 42);
            expect(component.currentPdfUrl()).toBe('blob:mock-url');
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should build the initial hidden-pages map from the attachment video unit slides', async () => {
            const hiddenDate = new Date('2099-01-01');
            routeData = {
                attachmentVideoUnit: {
                    id: 9,
                    lecture: { id: 4 },
                    slides: [
                        { id: 1, slideNumber: 1, hidden: undefined },
                        { id: 2, slideNumber: 2, hidden: hiddenDate, exercise: { id: 77 } },
                        { id: 3, slideNumber: 3, hidden: undefined },
                    ],
                },
            };
            // The video unit resolver path builds page order from the existing slides (existingSlides branch).
            mockNextOpen('original', 3);

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.attachmentVideoUnit()!.id).toBe(9);
            expect(attachmentVideoUnitService.getAttachmentFile).toHaveBeenCalledWith(5, 9);
            // Slide 2 is hidden and carries an exercise id.
            expect(component.initialHiddenPages()['2']).toBeDefined();
            expect(component.initialHiddenPages()['2'].exerciseId).toBe(77);
            expect(component.hiddenPages()['2']).toBeDefined();
            // Page order is rebuilt from the persisted slides (slideId = slide.id).
            expect(component.pageOrder().map((p) => p.slideId)).toEqual(['1', '2', '3']);
            expect(component.pageOrder()[1].sourceIndex).toBe(1);
        });

        it('should stop loading when the route resolves neither an attachment nor a video unit', async () => {
            routeData = {};

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.isPdfLoading()).toBe(false);
            expect(attachmentService.getAttachmentFile).not.toHaveBeenCalled();
            expect(attachmentVideoUnitService.getAttachmentFile).not.toHaveBeenCalled();
        });

        it('should surface an error when fetching the attachment file fails', async () => {
            attachmentService.getAttachmentFile.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
            routeData = { attachment: { id: 42, lecture: { id: 3 } } };

            component.ngOnInit();
            await fixture.whenStable();

            expect(alertService.error).toHaveBeenCalledWith('error.http.404');
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should surface an error when the video unit file fetch fails', async () => {
            attachmentVideoUnitService.getAttachmentFile.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400 })));
            routeData = { attachmentVideoUnit: { id: 9, lecture: { id: 4 }, slides: [] } };

            component.ngOnInit();
            await fixture.whenStable();

            expect(alertService.error).toHaveBeenCalledWith('error.http.400');
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should surface an error when the blob cannot be converted to an array buffer', async () => {
            const badBlob = { arrayBuffer: () => Promise.reject(new Error('boom')) } as unknown as Blob;
            attachmentService.getAttachmentFile.mockReturnValueOnce(of(badBlob));
            routeData = { attachment: { id: 42, lecture: { id: 3 } } };

            component.ngOnInit();
            await fixture.whenStable();
            // Allow the rejected arrayBuffer() microtask chain to settle.
            await new Promise<void>((resolve) => setTimeout(resolve, 0));

            // A plain Error routes through onError's default branch -> alertService.addAlert.
            expect(alertService.addAlert).toHaveBeenCalled();
            expect(component.isPdfLoading()).toBe(false);
        });
    });

    describe('computed properties', () => {
        it('should expose allPagesSelected, pageOrderChanged, hidden-page and sorted-hidden computed state', async () => {
            await loadOriginal(3);
            const [p0, p1] = component.pageOrder();

            expect(component.allPagesSelected()).toBe(false);
            component.selectedPages.set(new Set(component.pageOrder()));
            expect(component.allPagesSelected()).toBe(true);

            expect(component.pageOrderChanged()).toBe(false);
            component.onPageOrderChange([...component.pageOrder()].reverse().map((p, i) => ({ ...p, order: i + 1 })));
            expect(component.pageOrderChanged()).toBe(true);

            expect(component.hasHiddenPages()).toBe(false);
            component.hidePages([
                { slideId: p0.slideId, date: dayjs().add(1, 'day'), exerciseId: undefined },
                { slideId: p1.slideId, date: dayjs().add(2, 'day'), exerciseId: undefined },
            ]);
            expect(component.hasHiddenPages()).toBe(true);

            // Select the two hidden pages in reverse `order` to prove sortedHiddenSelectedPages sorts ascending by order.
            const highOrder = { ...p0, order: 5 };
            const lowOrder = { ...p1, order: 2 };
            component.selectedPages.set(new Set([highOrder, lowOrder]));
            expect(component.hasHiddenSelectedPages()).toBe(true);
            // sortedHiddenSelectedPages returns the hidden selected pages ordered by their `order` (ascending).
            const sorted = component.sortedHiddenSelectedPages();
            expect(sorted.map((p) => p.slideId)).toEqual([p1.slideId, p0.slideId]);
            expect(sorted[0].order).toBeLessThan(sorted[1].order);
        });
    });

    describe('loadPdf append (MERGE)', () => {
        it('should append pages, record a MERGE operation and mark the file changed', async () => {
            await loadOriginal(3);
            mockNextOpen('merge_1', 2);

            await component.loadPdf('blob:merge', new ArrayBuffer(8), 'merge_1', undefined, true);

            expect(component.totalPages()).toBe(5);
            expect(component.pageOrder().length).toBe(5);
            expect(component.pageOrder()[3]).toMatchObject({ sourcePdfId: 'merge_1', sourceIndex: 0, order: 4 });
            expect(component.operations().some((op) => op.type === 'MERGE')).toBe(true);
            expect(component.hasOperations()).toBe(true);
            expect(component.isFileChanged()).toBe(true);
            expect(component.sourcePDFs().get('merge_1')).toBeDefined();
        });

        it('should surface an error when the engine fails to open the document', async () => {
            engineService.engine.openDocumentBuffer.mockReturnValueOnce({
                toPromise: () => Promise.reject(new Error('cannot open')),
                wait: () => {},
                abort: () => {},
            } as any);

            await component.loadPdf('blob:bad', new ArrayBuffer(8), 'original');

            // openDocumentBuffer rejects with a plain Error -> onError default branch -> addAlert.
            expect(alertService.addAlert).toHaveBeenCalled();
            expect(component.isPdfLoading()).toBe(false);
        });
    });

    describe('mergePDF', () => {
        it('should reject a non-PDF file and reset the input', async () => {
            await loadOriginal(2);
            const event = pdfFileEvent('image/png');

            await component.mergePDF(event);

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.invalidFileType');
            expect((event.target as HTMLInputElement).value).toBe('');
            expect(component.operations().some((op) => op.type === 'MERGE')).toBe(false);
        });

        it('should append the selected PDF and clear the selection on success', async () => {
            await loadOriginal(2);
            component.selectedPages.set(new Set(component.pageOrder()));
            mockNextOpen('merge_x', 2);

            await component.mergePDF(pdfFileEvent());

            expect(component.totalPages()).toBe(4);
            expect(component.operations().some((op) => op.type === 'MERGE')).toBe(true);
            expect(component.selectedPages().size).toBe(0);
            expect(component.isAppendingFile()).toBe(true);
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should surface an error when reading the merge file fails (before an object URL is created)', async () => {
            await loadOriginal(2);
            // URL.revokeObjectURL is a shared global mock; clear it so we assert only this test's calls.
            (URL.revokeObjectURL as any).mockClear();
            const event = { target: { files: [{ type: 'application/pdf', arrayBuffer: () => Promise.reject(new Error('read failed')) }], value: 'x' } } as unknown as Event;

            await component.mergePDF(event);

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.mergeFailedError', { error: 'read failed' });
            // The object URL is created only after arrayBuffer() resolves, so nothing needs revoking here.
            expect(URL.revokeObjectURL).not.toHaveBeenCalled();
            expect(component.isPdfLoading()).toBe(false);
        });

        it('should surface an error and revoke the created object URL when loading the merged PDF fails', async () => {
            await loadOriginal(2);
            (URL.revokeObjectURL as any).mockClear();
            vi.spyOn(component, 'loadPdf').mockRejectedValueOnce(new Error('load failed'));

            await component.mergePDF(pdfFileEvent());

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.mergeFailedError', { error: 'load failed' });
            expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
        });
    });

    describe('applyOperations guards', () => {
        it('should throw when the original PDF source is missing', async () => {
            await expect(component.applyOperations()).rejects.toThrow(/Original PDF source not found/);
        });

        it('should throw when no pages remain to save', async () => {
            await loadOriginal(2);
            component.selectedPages.set(new Set(component.pageOrder()));
            component.deleteSelectedSlides();

            await expect(component.applyOperations()).rejects.toThrow(/without pages/);
        });
    });

    describe('save flow edge cases', () => {
        it('should abort the save when the produced instructor file exceeds the max file size', async () => {
            await loadOriginal(2);
            component.attachment.set({ id: 7, version: 1, lecture: { id: 3 } } as any);
            // Force a huge merged document so bytesToFile > MAX_FILE_SIZE.
            engineService.engine.mergePages.mockReturnValueOnce({
                toPromise: () => Promise.resolve({ id: 'merged', content: new ArrayBuffer(200 * 1024 * 1024), name: 'merged.pdf' }),
                wait: () => {},
                abort: () => {},
            } as any);

            await component.updateAttachmentWithFile();

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.fileSizeError');
            expect(attachmentService.update).not.toHaveBeenCalled();
            expect(component.isSaving()).toBe(false);
        });

        it('should finish saving a video unit without a student version when no pages are hidden', async () => {
            await loadOriginal(2);
            component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 }, attachment: { id: 11, version: 1 } } as any);

            await component.updateAttachmentWithFile();

            expect(attachmentVideoUnitService.update).toHaveBeenCalledOnce();
            expect(attachmentVideoUnitService.updateStudentVersion).not.toHaveBeenCalled();
            expect(alertService.success).toHaveBeenCalled();
            expect(component.isFileChanged()).toBe(false);
            expect(component.hasOperations()).toBe(false);
        });

        it('should surface an error and stop saving when applyOperations fails', async () => {
            await loadOriginal(2);
            component.attachment.set({ id: 7, version: 1, lecture: { id: 3 } } as any);
            engineService.engine.mergePages.mockReturnValueOnce({
                toPromise: () => Promise.reject(new Error('merge boom')),
                wait: () => {},
                abort: () => {},
            } as any);

            await component.updateAttachmentWithFile();

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'merge boom' });
            expect(component.isSaving()).toBe(false);
        });

        it('should surface an error when the attachment update service call fails', async () => {
            await loadOriginal(2);
            component.attachment.set({ id: 7, version: 1, lecture: { id: 3 } } as any);
            attachmentService.update.mockReturnValueOnce(throwError(() => new Error('update failed')));

            await component.updateAttachmentWithFile();

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'update failed' });
            expect(component.isSaving()).toBe(false);
        });

        it('should surface an error when the video unit update service call fails', async () => {
            await loadOriginal(2);
            component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 }, attachment: { id: 11, version: 1 } } as any);
            attachmentVideoUnitService.update.mockReturnValueOnce(throwError(() => new Error('unit update failed')));

            await component.updateAttachmentWithFile();

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'unit update failed' });
            expect(component.isSaving()).toBe(false);
        });

        it('should surface an error when the student version update fails', async () => {
            await loadOriginal(2);
            component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 }, attachment: { id: 11, version: 1 } } as any);
            component.hidePages({ slideId: component.pageOrder()[0].slideId, date: dayjs().add(1, 'day'), exerciseId: undefined });
            attachmentVideoUnitService.updateStudentVersion.mockReturnValueOnce(throwError(() => new Error('student failed')));

            await component.updateAttachmentWithFile();

            expect(attachmentVideoUnitService.update).toHaveBeenCalledOnce();
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.studentVersionUpdateError', { error: 'student failed' });
            expect(component.isSaving()).toBe(false);
        });

        it('should send the final page order (with slideId/order) to the video unit update endpoint', async () => {
            await loadOriginal(2);
            component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 }, attachment: { id: 11, version: 1 } } as any);

            await component.updateAttachmentWithFile();

            const formData = attachmentVideoUnitService.update.mock.calls[0][2] as FormData;
            expect(attachmentVideoUnitService.update).toHaveBeenCalledWith(4, 9, expect.any(FormData));
            expect(formData.get('file')).toBeInstanceOf(File);
            expect(formData.get('pageOrder')).toBeInstanceOf(Blob);
        });
    });

    describe('deleteAttachmentFile', () => {
        it('should delete an attachment and navigate back', async () => {
            component.courseId.set(5);
            component.attachment.set({ id: 7, lecture: { id: 3 } } as any);

            await component.deleteAttachmentFile();

            expect(attachmentService.delete).toHaveBeenCalledWith(7);
            expect(router.navigate).toHaveBeenCalledWith(['course-management', 5, 'lectures', 3, 'attachments']);
        });

        it('should surface an error when deleting an attachment fails', async () => {
            component.attachment.set({ id: 7, lecture: { id: 3 } } as any);
            attachmentService.delete.mockReturnValueOnce(throwError(() => new Error('delete failed')));

            await component.deleteAttachmentFile();

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'delete failed' });
        });

        it('should delete an attachment video unit and navigate back', async () => {
            component.courseId.set(5);
            component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 } } as any);

            await component.deleteAttachmentFile();

            expect(lectureUnitService.delete).toHaveBeenCalledWith(9, 4);
            expect(router.navigate).toHaveBeenCalledWith(['course-management', 5, 'lectures', 4, 'unit-management']);
        });

        it('should surface an error when deleting an attachment video unit fails', async () => {
            component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 } } as any);
            lectureUnitService.delete.mockReturnValueOnce(throwError(() => new Error('unit delete failed')));

            await component.deleteAttachmentFile();

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: 'unit delete failed' });
        });
    });

    describe('validateHiddenSlidesDates via updateAttachmentWithFile', () => {
        it('should list the affected page order in the past-date error message', async () => {
            await loadOriginal(3);
            component.attachmentVideoUnit.set({ id: 9, lecture: { id: 4 }, attachment: {} } as any);
            // Hide pages 1 and 3 with a past date -> the error should list orders "1, 3" sorted ascending.
            component.hidePages([
                { slideId: component.pageOrder()[2].slideId, date: dayjs().subtract(2, 'day'), exerciseId: undefined },
                { slideId: component.pageOrder()[0].slideId, date: dayjs().subtract(1, 'day'), exerciseId: undefined },
            ]);

            await component.updateAttachmentWithFile();

            expect(alertService.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.dateBox.dateErrorWithPages', { param: '1, 3' });
            expect(attachmentVideoUnitService.update).not.toHaveBeenCalled();
        });
    });

    describe('misc helpers', () => {
        it('should trigger the hidden file input', () => {
            const click = vi.fn();
            vi.spyOn(component, 'fileInput').mockReturnValue({ nativeElement: { click } } as any);

            component.triggerFileInput();

            expect(click).toHaveBeenCalledOnce();
        });

        it('should report hidden-page changes through hiddenPagesChanged()', async () => {
            await loadOriginal(2);
            expect(component.hiddenPagesChanged()).toBe(false);

            component.hidePages({ slideId: component.pageOrder()[0].slideId, date: dayjs().add(1, 'day'), exerciseId: undefined });

            expect(component.hiddenPagesChanged()).toBe(true);
        });

        it('should expose the current hidden pages as an array via getHiddenPages()', async () => {
            await loadOriginal(2);
            const page = component.pageOrder()[0];
            const date = dayjs().add(1, 'day');
            component.hidePages({ slideId: page.slideId, date, exerciseId: 55 });

            const hidden = component.getHiddenPages();

            expect(hidden).toHaveLength(1);
            expect(hidden[0]).toMatchObject({ slideId: page.slideId, exerciseId: 55 });
            expect(hidden[0].date.valueOf()).toBe(date.valueOf());
        });

        it('should record a SHOW operation when showing pages', async () => {
            await loadOriginal(2);
            const page = component.pageOrder()[0];
            component.hidePages({ slideId: page.slideId, date: dayjs().add(1, 'day'), exerciseId: undefined });

            component.showPages(new Set([page]));

            expect(component.operations().some((op) => op.type === 'SHOW')).toBe(true);
            expect(component.hiddenPages()[page.slideId]).toBeUndefined();
        });

        it('should aggregate hasChanges across operations, hidden pages, order and file changes', async () => {
            await loadOriginal(2);
            expect(component.hasChanges()).toBe(false);

            component.hidePages({ slideId: component.pageOrder()[0].slideId, date: dayjs().add(1, 'day'), exerciseId: undefined });

            expect(component.hasChanges()).toBe(true);
        });
    });
});
