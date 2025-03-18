import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpClientModule } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { ElementRef, InputSignal, Signal, SimpleChanges } from '@angular/core';

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
        const initialHiddenPages = new Set<number>([1, 2, 3]);
        const updatedHiddenPages = new Set<number>([4, 5, 6]);

        component.hiddenPages = jest.fn(() => updatedHiddenPages) as unknown as InputSignal<Set<number>>;

        const changes: SimpleChanges = {
            hiddenPages: {
                currentValue: updatedHiddenPages,
                previousValue: initialHiddenPages,
                firstChange: false,
                isFirstChange: () => false,
            },
        };
        component.ngOnChanges(changes);

        expect(component.newHiddenPages()).toEqual(new Set(updatedHiddenPages));
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
        fixture.componentRef.setInput('hiddenPages', new Set([1]));
        component.toggleVisibility(1, new MouseEvent('click'));
        expect(component.hiddenPages()!.has(1)).toBeFalse();

        component.toggleVisibility(2, new MouseEvent('click'));
        expect(component.hiddenPages()!.has(2)).toBeTrue();
    });

    it('should select and deselect pages', () => {
        component.togglePageSelection(1, { target: { checked: true } } as unknown as Event);
        expect(component.selectedPages().has(1)).toBeTrue();

        component.togglePageSelection(1, { target: { checked: false } } as unknown as Event);
        expect(component.selectedPages().has(1)).toBeFalse();
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
});
