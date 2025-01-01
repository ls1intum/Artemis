import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClientModule } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { ElementRef, InputSignal, Signal, SimpleChanges } from '@angular/core';
import dayjs from 'dayjs/esm';

interface HiddenPageMap {
    [pageIndex: number]: dayjs.Dayjs;
}

interface HiddenPage {
    pageIndex: number;
    date: dayjs.Dayjs;
}

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

describe('PdfPreviewThumbnailGridComponent', () => {
    let component: PdfPreviewThumbnailGridComponent;
    let fixture: ComponentFixture<PdfPreviewThumbnailGridComponent>;
    let alertServiceMock: any;

    beforeEach(async () => {
        alertServiceMock = {
            error: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewThumbnailGridComponent, HttpClientModule],
            providers: [
                { provide: ActivatedRoute, useValue: { data: of({}) } },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewThumbnailGridComponent);
        component = fixture.componentInstance;

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should update newHiddenPages when hiddenPages changes', () => {
        const updatedHiddenPages: HiddenPageMap = { 4: dayjs(), 5: dayjs(), 6: dayjs() };

        fixture.componentRef.setInput('hiddenPages', updatedHiddenPages);

        fixture.detectChanges();

        expect(component.newHiddenPages()).toEqual(updatedHiddenPages);
    });

    it('should load the PDF when currentPdfUrl changes', async () => {
        const mockLoadPdf = jest.spyOn(component, 'loadPdf').mockResolvedValue();
        const initialPdfUrl = 'initial.pdf';
        const updatedPdfUrl = 'updated.pdf';

        let currentPdfUrlValue = initialPdfUrl;
        component.currentPdfUrl = jest.fn(() => currentPdfUrlValue) as unknown as InputSignal<string>;
        component.appendFile = jest.fn(() => false) as unknown as InputSignal<boolean>;

        currentPdfUrlValue = updatedPdfUrl;
        const changes: SimpleChanges = {
            currentPdfUrl: {
                currentValue: updatedPdfUrl,
                previousValue: initialPdfUrl,
                firstChange: false,
                isFirstChange: () => false,
            },
        };
        await component.ngOnChanges(changes);

        expect(mockLoadPdf).toHaveBeenCalledWith(updatedPdfUrl, false);
    });

    it('should load PDF and render pages', async () => {
        const spyCreateCanvas = jest.spyOn(component as any, 'createCanvas');

        await component.loadPdf('fake-url', false);

        expect(spyCreateCanvas).toHaveBeenCalled();
        expect(component.totalPagesArray().size).toBe(1);
    });

    it('should toggle enlarged view state', () => {
        const mockCanvas = document.createElement('canvas');
        component.originalCanvas.set(mockCanvas);
        component.isEnlargedView.set(true);
        expect(component.isEnlargedView()).toBeTruthy();

        component.isEnlargedView.set(false);
        expect(component.isEnlargedView()).toBeFalsy();
    });

    it('should toggle visibility of a page', () => {
        const mockButton = document.createElement('button');
        const mockEvent = new MouseEvent('click', {
            bubbles: true,
            cancelable: true,
        });

        Object.defineProperty(mockEvent, 'target', {
            value: mockButton,
            enumerable: true,
        });

        const initialHiddenPages = { 1: dayjs() };
        fixture.componentRef.setInput('hiddenPages', initialHiddenPages);

        component.toggleVisibility(1, mockEvent);
        expect(component.activeButtonIndex()).toBe(1);
        expect(mockButton.style.opacity).toBe('1');

        component.showPage(1);
        expect(component.newHiddenPages()[1]).toBeUndefined();
    });

    it('should select and deselect pages', () => {
        component.togglePageSelection(1, { target: { checked: true } } as unknown as Event);
        expect(component.selectedPages().has(1)).toBeTruthy();

        component.togglePageSelection(1, { target: { checked: false } } as unknown as Event);
        expect(component.selectedPages().has(1)).toBeFalsy();
    });

    it('should handle enlarged view correctly for a specific page', () => {
        const mockCanvas = document.createElement('canvas');
        const container = document.createElement('div');
        container.id = 'pdf-page-1';
        container.appendChild(mockCanvas);

        component.pdfContainer = jest.fn(() => ({
            nativeElement: {
                querySelector: jest.fn((selector: string) => {
                    if (selector === '#pdf-page-1 canvas') {
                        return mockCanvas;
                    }
                    return null;
                }),
            },
        })) as unknown as Signal<ElementRef<HTMLDivElement>>;

        component.displayEnlargedCanvas(1);

        expect(component.originalCanvas()).toBe(mockCanvas);
        expect(component.isEnlargedView()).toBeTruthy();
    });

    it('should update newHiddenPages and emit the change', () => {
        const emitSpy = jest.spyOn(component.newHiddenPagesOutput, 'emit');

        const hiddenPage: HiddenPage = {
            pageIndex: 1,
            date: dayjs('2024-01-01'),
        };

        component.onHiddenPageChange(hiddenPage);

        expect(component.newHiddenPages()[hiddenPage.pageIndex]).toBeDefined();
        expect(component.newHiddenPages()[hiddenPage.pageIndex].isSame(dayjs(hiddenPage.date))).toBeTruthy();
        expect(emitSpy).toHaveBeenCalledWith(component.newHiddenPages());
    });

    it('should remove the page from newHiddenPages and hide the action button', () => {
        const initialPages = { 1: dayjs() };
        fixture.componentRef.setInput('hiddenPages', initialPages);
        fixture.detectChanges();

        const hideButtonSpy = jest.spyOn(component, 'hideActionButton');

        component.showPage(1);

        expect(component.newHiddenPages()[1]).toBeUndefined();
        expect(hideButtonSpy).toHaveBeenCalledWith(1);
    });

    it('should handle showing a page that is not hidden', () => {
        fixture.componentRef.setInput('hiddenPages', {});
        fixture.detectChanges();

        const hideButtonSpy = jest.spyOn(component, 'hideActionButton');

        component.showPage(1);

        expect(hideButtonSpy).toHaveBeenCalledWith(1);
    });

    it('should set opacity to 0 for existing button', () => {
        const mockButton = document.createElement('button');
        mockButton.id = 'hide-show-button-1';
        document.body.appendChild(mockButton);

        component.hideActionButton(1);

        expect(mockButton.style.opacity).toBe('0');

        document.body.removeChild(mockButton);
    });

    it('should handle non-existent button gracefully', () => {
        component.hideActionButton(999);

        expect(document.getElementById('hide-show-button-999')).toBeNull();
    });
});
