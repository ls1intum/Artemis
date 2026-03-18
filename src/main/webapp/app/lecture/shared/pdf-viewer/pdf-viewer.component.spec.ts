import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { MockDirective, MockPipe } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<PdfViewerComponent>;
    let component: PdfViewerComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent, MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                {
                    provide: TranslateService,
                    useValue: {
                        get: (key: string) => of(key),
                        instant: (key: string) => key,
                        onLangChange: of({}),
                        onTranslationChange: of({}),
                        onDefaultLangChange: of({}),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('pdfUrl', 'blob:http://localhost/test.pdf');
    });

    it('should create component', () => {
        expect(component).toBeTruthy();
    });

    it('should have required inputs', () => {
        expect(component.pdfUrl()).toBe('blob:http://localhost/test.pdf');
    });

    it('should initialize with loading state', () => {
        expect(component.isLoading()).toBe(true);
        expect(component.error()).toBeUndefined();
        expect(component.totalPages()).toBe(0);
        expect(component.currentPage()).toBe(1);
        expect(component.zoomLevel()).toBe(1.0);
    });

    it('should accept optional inputs', () => {
        const uploadDate = dayjs();
        fixture.componentRef.setInput('uploadDate', uploadDate);
        fixture.componentRef.setInput('version', 2);
        fixture.componentRef.setInput('initialPage', 3);

        expect(component.uploadDate()).toBe(uploadDate);
        expect(component.version()).toBe(2);
        expect(component.initialPage()).toBe(3);
    });

    describe('zoom controls', () => {
        it('should zoom in, out and reset zoom level', () => {
            const initialZoom = component.zoomLevel();
            component.zoomIn();
            expect(component.zoomLevel()).toBe(initialZoom + 0.25);

            component.zoomOut();
            expect(component.zoomLevel()).toBe(initialZoom);

            component.zoomLevel.set(2.0);
            component.resetZoom();
            expect(component.zoomLevel()).toBe(1.0);
        });

        it.each([
            { start: 2.9, method: 'zoomIn', expected: 3.0 },
            { start: 0.6, method: 'zoomOut', expected: 0.5 },
        ])('should clamp zoom at boundaries ($method)', ({ start, method, expected }) => {
            component.zoomLevel.set(start);
            (component as any)[method]();
            expect(component.zoomLevel()).toBe(expected);
        });
    });

    describe('pdf loading events', () => {
        it('should reset state when loading starts', () => {
            component.isLoading.set(false);
            component.error.set('error');
            component.totalPages.set(5);
            component.currentPage.set(3);

            component.onPdfLoadingStarts();

            expect(component.isLoading()).toBe(true);
            expect(component.error()).toBeUndefined();
            expect(component.totalPages()).toBe(0);
            expect(component.currentPage()).toBe(1);
        });

        it('should update state when PDF loads', () => {
            component.isLoading.set(true);
            component.error.set('error');

            component.onPdfLoaded({ pagesCount: 7 });

            expect(component.isLoading()).toBe(false);
            expect(component.error()).toBeUndefined();
            expect(component.totalPages()).toBe(7);
        });

        it('should set error when loading fails', () => {
            component.isLoading.set(true);

            component.onPdfLoadingFailed();

            expect(component.isLoading()).toBe(false);
            expect(component.error()).toBe('error');
        });
    });

    it('should apply initial page after load', () => {
        fixture.componentRef.setInput('initialPage', 4);

        component.onPdfLoaded({ pagesCount: 10 });

        expect(component.currentPage()).toBe(4);
    });

    it('should clamp initial page to total pages', () => {
        fixture.componentRef.setInput('initialPage', 12);

        component.onPdfLoaded({ pagesCount: 5 });

        expect(component.currentPage()).toBe(5);
    });

    it('should update current page on page change', () => {
        component.onPageChange(3);
        expect(component.currentPage()).toBe(3);
    });

    it('should track zoom factor changes', () => {
        component.onZoomFactorChange(1.5);
        expect(component.zoomLevel()).toBe(1.5);

        component.onZoomFactorChange(10);
        expect(component.zoomLevel()).toBe(3.0);
    });

    it('should only show the toolbar after pages are loaded', () => {
        component.isLoading.set(true);
        component.totalPages.set(2);
        expect(component.showToolbar()).toBe(false);

        component.isLoading.set(false);
        expect(component.showToolbar()).toBe(true);
    });
});
