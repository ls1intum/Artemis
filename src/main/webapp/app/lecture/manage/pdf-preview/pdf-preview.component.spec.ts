import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
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
    let alertService: { error: ReturnType<typeof vi.fn>; success: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        engineService = new MockPdfEngineService();
        attachmentService = { update: vi.fn(() => of({})), getAttachmentFile: vi.fn(() => of(new Blob())), delete: vi.fn(() => of({})) };
        attachmentVideoUnitService = { update: vi.fn(() => of({})), updateStudentVersion: vi.fn(() => of({})), getAttachmentFile: vi.fn(() => of(new Blob())) };
        alertService = { error: vi.fn(), success: vi.fn() };
        const route = { data: of({}), parent: { snapshot: { paramMap: { get: () => '5' } } } };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewComponent],
            providers: [
                provideHttpClient(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: AlertService, useValue: alertService },
                { provide: AttachmentService, useValue: attachmentService },
                { provide: AttachmentVideoUnitService, useValue: attachmentVideoUnitService },
                { provide: LectureUnitService, useValue: { delete: vi.fn(() => of({})) } },
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
});
