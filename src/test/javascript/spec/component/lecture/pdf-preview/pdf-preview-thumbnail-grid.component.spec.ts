import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClientModule } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { getDocument } from 'pdfjs-dist';
import * as GlobalUtils from 'app/shared/util/global.utils';
import { ElementRef, Signal } from '@angular/core';

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
            addAlert: jest.fn(),
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

    it('should handle changes to inputs correctly in ngOnChanges', async () => {
        const initialHiddenPages = new Set<number>([1, 2]);
        const updatedHiddenPages = new Set<number>([3, 4]);
        const initialPdfUrl = 'old-pdf-url';
        const updatedPdfUrl = 'new-pdf-url';

        fixture.componentRef.setInput('hiddenPages', initialHiddenPages);
        fixture.componentRef.setInput('currentPdfUrl', initialPdfUrl);
        fixture.detectChanges(); // Trigger initial bindings

        const loadPdfSpy = jest.spyOn(component, 'loadPdf').mockImplementation();

        fixture.componentRef.setInput('hiddenPages', updatedHiddenPages);
        fixture.componentRef.setInput('currentPdfUrl', updatedPdfUrl);

        component.ngOnChanges({
            hiddenPages: {
                previousValue: initialHiddenPages,
                currentValue: updatedHiddenPages,
                firstChange: false,
                isFirstChange: () => false,
            },
            currentPdfUrl: {
                previousValue: initialPdfUrl,
                currentValue: updatedPdfUrl,
                firstChange: false,
                isFirstChange: () => false,
            },
        });

        expect(component.newHiddenPages()).toEqual(updatedHiddenPages); // Check newHiddenPages signal is updated
        expect(loadPdfSpy).toHaveBeenCalledWith(updatedPdfUrl, component.appendFile()); // Verify loadPdf is called with correct args
    });

    it('should load PDF and render pages', async () => {
        fixture.componentRef.setInput('currentPdfUrl', 'fake-url');
        fixture.componentRef.setInput('appendFile', false);

        const mockCanvas = document.createElement('canvas');
        jest.spyOn(component as any, 'createCanvas').mockReturnValue(mockCanvas);

        await component.loadPdf('fake-url', false);

        expect(getDocument).toHaveBeenCalledWith('fake-url');
        expect(component.totalPagesArray().size).toBe(1);
    });

    it('should create and configure a canvas element for the given viewport', () => {
        const mockViewport = {
            width: 600,
            height: 800,
        };

        const canvas = (component as any).createCanvas(mockViewport);

        expect(canvas).toBeInstanceOf(HTMLCanvasElement);
        expect(canvas.width).toBe(mockViewport.width);
        expect(canvas.height).toBe(mockViewport.height);
        expect(canvas.style.display).toBe('block');
        expect(canvas.style.width).toBe('100%');
        expect(canvas.style.height).toBe('100%');
    });

    it('should toggle visibility of a page correctly', () => {
        const hiddenPagesMock = new Set<number>([1, 2]);
        fixture.componentRef.setInput('hiddenPages', hiddenPagesMock);
        component.toggleVisibility(2, new Event('click'));

        expect(hiddenPagesMock.has(2)).toBeFalsy();
        expect(hiddenPagesMock.has(1)).toBeTruthy();
    });

    it('should toggle selection of a page correctly', () => {
        const mockEvent = { target: { checked: true } } as unknown as Event;

        const initialSelectedPages = new Set<number>();
        component.selectedPages.set(initialSelectedPages);
        fixture.detectChanges();

        component.togglePageSelection(1, mockEvent);

        expect(component.selectedPages().has(1)).toBeTruthy();

        (mockEvent.target as HTMLInputElement).checked = false;
        component.togglePageSelection(1, mockEvent);

        expect(component.selectedPages().has(1)).toBeFalsy();
    });

    it('should handle PDF load errors gracefully', async () => {
        const errorMessage = 'Error loading PDF';

        (getDocument as jest.Mock).mockReturnValue({
            promise: Promise.reject(new Error(errorMessage)),
        });

        const onErrorSpy = jest.spyOn(GlobalUtils, 'onError').mockImplementation();
        await component.loadPdf('invalid-url', false);
        expect(onErrorSpy).toHaveBeenCalledWith(alertServiceMock, new Error(errorMessage));
        onErrorSpy.mockRestore();
    });

    it('should set the selected canvas as the originalCanvas and enable enlarged view', () => {
        const mockCanvas = document.createElement('canvas');
        const mockDiv = document.createElement('div');
        mockDiv.id = 'pdf-page-1';
        mockDiv.appendChild(mockCanvas);

        const pdfContainerMock: ElementRef<HTMLDivElement> = {
            nativeElement: {
                querySelector: jest.fn((selector: string) => {
                    if (selector === '#pdf-page-1 canvas') {
                        return mockCanvas;
                    }
                    return null;
                }),
            },
        } as unknown as ElementRef<HTMLDivElement>;

        component.pdfContainer = jest.fn(() => pdfContainerMock) as unknown as Signal<ElementRef<HTMLDivElement>>;
        component.displayEnlargedCanvas(1);

        expect(pdfContainerMock.nativeElement.querySelector).toHaveBeenCalledWith('#pdf-page-1 canvas');
        expect(component.originalCanvas()).toBe(mockCanvas);
        expect(component.isEnlargedView()).toBeTrue();
    });
});
