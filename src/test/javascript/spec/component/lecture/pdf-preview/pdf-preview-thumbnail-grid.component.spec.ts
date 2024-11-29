import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { HttpClientModule } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';

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

    it('should load PDF and render pages', async () => {
        const spyCreateCanvas = jest.spyOn(component, 'createCanvas');
        const spyCreateCanvasContainer = jest.spyOn(component, 'createCanvasContainer');

        await component.loadOrAppendPdf('fake-url');

        expect(spyCreateCanvas).toHaveBeenCalled();
        expect(spyCreateCanvasContainer).toHaveBeenCalled();
        expect(component.totalPages()).toBe(1);
    });

    it('should toggle enlarged view state', () => {
        const mockCanvas = document.createElement('canvas');
        component.displayEnlargedCanvas(mockCanvas);
        expect(component.isEnlargedView()).toBeTruthy();

        component.isEnlargedView.set(false);
        expect(component.isEnlargedView()).toBeFalsy();
    });

    it('should handle mouseenter and mouseleave events correctly', () => {
        const mockCanvas = document.createElement('canvas');
        const container = component.createCanvasContainer(mockCanvas, 1);
        const overlay = container.querySelector('div');

        container.dispatchEvent(new Event('mouseenter'));
        expect(overlay!.style.opacity).toBe('1');

        container.dispatchEvent(new Event('mouseleave'));
        expect(overlay!.style.opacity).toBe('0');
    });

    it('should handle click event on overlay to trigger displayEnlargedCanvas', () => {
        const displayEnlargedCanvasSpy = jest.spyOn(component, 'displayEnlargedCanvas');
        const mockCanvas = document.createElement('canvas');
        const container = component.createCanvasContainer(mockCanvas, 1);
        const overlay = container.querySelector('div');

        overlay!.dispatchEvent(new Event('click'));
        expect(displayEnlargedCanvasSpy).toHaveBeenCalledWith(mockCanvas);
    });
});
