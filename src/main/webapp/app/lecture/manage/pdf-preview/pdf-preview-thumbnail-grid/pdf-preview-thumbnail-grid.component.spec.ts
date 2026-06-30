import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { OrderedPage } from 'app/lecture/manage/pdf-preview/pdf-preview.component';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';
import { MockPdfEngineService, createMockPdfDocument } from 'test/helpers/mocks/service/mock-pdf-engine.service';

describe('PdfPreviewThumbnailGridComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfPreviewThumbnailGridComponent;
    let fixture: ComponentFixture<PdfPreviewThumbnailGridComponent>;
    let engineService: MockPdfEngineService;
    let alertServiceMock: { error: ReturnType<typeof vi.fn> };

    const sourceDoc = createMockPdfDocument('source1', 3);
    // A fresh set per call: onPageDrop mutates page.order in place, so tests must not share objects.
    const makePages = (): OrderedPage[] => [
        { slideId: 'slide1', initialIndex: 1, order: 1, sourcePdfId: 'source1', sourceIndex: 0, sourceDoc },
        { slideId: 'slide2', initialIndex: 2, order: 2, sourcePdfId: 'source1', sourceIndex: 1, sourceDoc },
        { slideId: 'slide3', initialIndex: 3, order: 3, sourcePdfId: 'source1', sourceIndex: 2, sourceDoc },
    ];

    async function flushAsync(): Promise<void> {
        for (let i = 0; i < 5; i++) {
            await new Promise<void>((resolve) => setTimeout(resolve, 0));
            await fixture.whenStable();
        }
    }

    beforeEach(async () => {
        engineService = new MockPdfEngineService();
        alertServiceMock = { error: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewThumbnailGridComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: PdfEngineService, useValue: engineService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewThumbnailGridComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.restoreAllMocks());

    /** Sets the ordered pages, lets the template render the page containers, and awaits the engine render. */
    async function setPages(pages: OrderedPage[] = makePages()): Promise<void> {
        fixture.componentRef.setInput('orderedPages', pages);
        fixture.detectChanges();
        await flushAsync();
        fixture.detectChanges();
    }

    it('should create', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should render every ordered page through the engine', async () => {
        await setPages();

        expect(engineService.engine.renderPageRaw).toHaveBeenCalledTimes(3);
        // Each page is rendered from its own source document at its source index.
        expect(engineService.engine.renderPageRaw).toHaveBeenCalledWith(sourceDoc, sourceDoc.pages[0], { scaleFactor: 1 });
    });

    it('should reorder pages on drop and emit the renumbered order', () => {
        fixture.componentRef.setInput('orderedPages', makePages());
        fixture.detectChanges();
        const emitted = vi.fn();
        component.pageOrderOutput.subscribe(emitted);

        component.onPageDrop({ previousIndex: 0, currentIndex: 2 } as CdkDragDrop<OrderedPage[]>);

        expect(emitted).toHaveBeenCalledOnce();
        const newOrder: OrderedPage[] = emitted.mock.calls[0][0];
        expect(newOrder.map((page) => page.slideId)).toEqual(['slide2', 'slide3', 'slide1']);
        expect(newOrder.map((page) => page.order)).toEqual([1, 2, 3]);
    });

    it('should ignore a drop onto the same position', () => {
        fixture.componentRef.setInput('orderedPages', makePages());
        fixture.detectChanges();
        const emitted = vi.fn();
        component.pageOrderOutput.subscribe(emitted);

        component.onPageDrop({ previousIndex: 1, currentIndex: 1 } as CdkDragDrop<OrderedPage[]>);

        expect(emitted).not.toHaveBeenCalled();
    });

    it('should emit the selection when a checkbox is toggled on', () => {
        fixture.componentRef.setInput('orderedPages', makePages());
        fixture.detectChanges();
        const emitted = vi.fn();
        component.selectedPagesOutput.subscribe(emitted);

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.checked = true;
        component.togglePageSelection('slide2', { target: checkbox } as unknown as Event);

        const selection: Set<OrderedPage> = emitted.mock.calls.at(-1)![0];
        expect(Array.from(selection).map((page) => page.slideId)).toEqual(['slide2']);
    });

    it('should emit a hidden page received from the date box', () => {
        fixture.componentRef.setInput('orderedPages', makePages());
        fixture.detectChanges();
        const emitted = vi.fn();
        component.hiddenPagesOutput.subscribe(emitted);

        component.onHiddenPagesReceived({ slideId: 'slide1', date: dayjs(), exerciseId: undefined });

        expect(emitted.mock.calls.at(-1)![0]).toHaveProperty('slide1');
    });

    it('should emit hidden pages without the shown page when a page is shown again', async () => {
        await setPages();
        fixture.componentRef.setInput('hiddenPages', { slide1: { date: dayjs(), exerciseId: undefined } });
        fixture.detectChanges();
        const emitted = vi.fn();
        component.hiddenPagesOutput.subscribe(emitted);

        component.showPage('slide1');

        expect(emitted.mock.calls.at(-1)![0]).not.toHaveProperty('slide1');
    });

    it('should return the 1-based display order of a slide', () => {
        fixture.componentRef.setInput('orderedPages', makePages());
        fixture.detectChanges();

        expect(component.getPageOrder('slide2')).toBe(2);
        expect(component.getPageOrder('unknown')).toBe(-1);
    });

    it('should find a page by its slide id', () => {
        fixture.componentRef.setInput('orderedPages', makePages());
        fixture.detectChanges();

        expect(component.findPageBySlideId('slide3')?.order).toBe(3);
        expect(component.findPageBySlideId('missing')).toBeUndefined();
    });
});
