import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PdfFullscreenOverlayService } from './pdf-fullscreen-overlay.service';
import dayjs from 'dayjs/esm';

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfViewerComponent;
    let fixture: ComponentFixture<PdfViewerComponent>;
    let mockThemeService: { currentTheme: ReturnType<typeof signal<Theme>> };
    let fullscreenService: PdfFullscreenOverlayService;

    function sendIframeMessage(type: string, data?: any) {
        const iframe = component.pdfIframe()?.nativeElement;
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { type, data },
                origin: window.location.origin,
                source: iframe?.contentWindow,
            }),
        );
    }

    beforeEach(async () => {
        mockThemeService = { currentTheme: signal(Theme.LIGHT) };
        await TestBed.configureTestingModule({
            imports: [PdfViewerComponent],
            providers: [
                provideHttpClient(),
                PdfFullscreenOverlayService,
                { provide: ThemeService, useValue: mockThemeService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;
        fullscreenService = TestBed.inject(PdfFullscreenOverlayService);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    describe.each([
        { mode: 'embedded' as const, setup: () => fixture.componentRef.setInput('mode', 'embedded') },
        { mode: 'fullscreen' as const, setup: () => fullscreenService.open('test.pdf', 1, undefined, undefined) },
    ])('$mode Mode', ({ mode, setup }) => {
        beforeEach(() => {
            if (mode === 'embedded') {
                fixture.componentRef.setInput('mode', 'embedded');
            } else {
                fixture.componentRef.setInput('mode', 'fullscreen');
            }
        });

        it('should create and accept inputs', () => {
            if (mode === 'embedded') {
                fixture.componentRef.setInput('pdfUrl', 'test.pdf');
                fixture.componentRef.setInput('version', 3);
                fixture.componentRef.setInput('initialPage', 5);
                fixture.detectChanges();

                expect(component).toBeTruthy();
                expect(component.pdfUrl()).toBe('test.pdf');
                expect(component.version()).toBe(3);
                expect(component.initialPage()).toBe(5);
                expect(component.iframeSrc()).toBe('/pdf-viewer-iframe');
            } else {
                fullscreenService.open('test.pdf', 1, undefined, undefined);
                fixture.detectChanges();

                const overlay = fixture.nativeElement.querySelector('.pdf-fullscreen-overlay');
                const iframe = fixture.nativeElement.querySelector('iframe');
                expect(overlay).toBeTruthy();
                expect(iframe).toBeTruthy();
            }
        });

        it('should handle ready message', () => {
            setup();
            fixture.detectChanges();

            sendIframeMessage('ready');
            fixture.detectChanges();

            expect(component.iframeReady()).toBe(true);
        });

        it('should render footer when metadata provided', () => {
            if (mode === 'embedded') {
                fixture.componentRef.setInput('pdfUrl', 'test.pdf');
                fixture.detectChanges();
                expect(fixture.nativeElement.querySelector('.pdf-viewer-footer')).toBeFalsy();

                fixture.componentRef.setInput('version', 1);
                fixture.detectChanges();
                expect(fixture.nativeElement.querySelector('.pdf-viewer-footer')).toBeTruthy();
            } else {
                const uploadDate = dayjs();
                fullscreenService.open('test.pdf', 1, uploadDate, 3);
                fixture.detectChanges();

                // Footer should be hidden while loading
                expect(fixture.nativeElement.querySelector('.pdf-viewer-footer')).toBeFalsy();

                // Simulate PDF loading completion
                sendIframeMessage('ready');
                fixture.detectChanges();
                sendIframeMessage('pagesLoaded', { pagesCount: 10, url: 'test.pdf' });
                fixture.detectChanges();

                // Footer should now be visible
                const footer = fixture.nativeElement.querySelector('.pdf-viewer-footer');
                expect(footer).toBeTruthy();
                expect(footer.textContent).toContain('3');
            }
        });
    });

    describe('Embedded Mode Specific', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('mode', 'embedded');
        });

        it('should emit events (loadError, pagesLoaded, downloadRequested)', () => {
            const loadErrorSpy = vi.fn();
            const pagesLoadedSpy = vi.fn();
            const downloadSpy = vi.fn();

            component.loadError.subscribe(loadErrorSpy);
            component.pagesLoaded.subscribe(pagesLoadedSpy);
            component.downloadRequested.subscribe(downloadSpy);

            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();

            sendIframeMessage('pdfLoadError', { url: 'failed.pdf' });
            expect(loadErrorSpy).toHaveBeenCalledWith({ pdfUrl: 'failed.pdf' });

            sendIframeMessage('pagesLoaded', { pagesCount: 12, url: 'loaded.pdf' });
            expect(pagesLoadedSpy).toHaveBeenCalledWith({ pdfUrl: 'loaded.pdf', pagesCount: 12 });

            sendIframeMessage('download');
            expect(downloadSpy).toHaveBeenCalledOnce();
        });

        it('should call fullscreen service when openFullscreen message received', () => {
            const openSpy = vi.spyOn(fullscreenService, 'open');
            const uploadDate = dayjs();

            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.componentRef.setInput('uploadDate', uploadDate);
            fixture.componentRef.setInput('version', 2);
            fixture.detectChanges();

            sendIframeMessage('ready');
            sendIframeMessage('pageChange', { page: 6 });
            sendIframeMessage('openFullscreen');
            fixture.detectChanges();

            expect(openSpy).toHaveBeenCalledWith('test.pdf', 6, uploadDate, 2, expect.any(Function));
        });

        it('should use initial page when opening fullscreen before first pageChange', () => {
            const openSpy = vi.spyOn(fullscreenService, 'open');

            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.componentRef.setInput('initialPage', 7);
            fixture.detectChanges();

            sendIframeMessage('ready');
            fixture.detectChanges();
            sendIframeMessage('openFullscreen');

            expect(openSpy).toHaveBeenCalledWith('test.pdf', 7, undefined, undefined, expect.any(Function));
        });
    });

    describe('Fullscreen Mode Specific', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('mode', 'fullscreen');
        });

        it('should update page on message', () => {
            const updateSpy = vi.spyOn(fullscreenService, 'updateCurrentPage');
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            sendIframeMessage('pageChange', { page: 10 });

            expect(updateSpy).toHaveBeenCalledWith(10);
        });

        it('should keep loading spinner visible until PDF is loaded in fullscreen mode', () => {
            // Open fullscreen → spinner visible
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.spinner-border')).toBeTruthy();

            // Iframe sends 'ready' → spinner should STILL be visible
            sendIframeMessage('ready');
            fixture.detectChanges();

            expect(component.iframeReady()).toBe(true);
            expect(fixture.nativeElement.querySelector('.spinner-border')).toBeTruthy();

            // PDF loads, iframe sends 'pagesLoaded' → NOW spinner should hide
            sendIframeMessage('pagesLoaded', { pagesCount: 10, url: 'test.pdf' });
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('.spinner-border')).toBeFalsy();
        });
    });
});
