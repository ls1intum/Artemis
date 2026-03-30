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

    describe('Embedded Mode', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('mode', 'embedded');
        });

        it('should create and accept inputs', () => {
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.componentRef.setInput('version', 3);
            fixture.componentRef.setInput('initialPage', 5);
            fixture.detectChanges();

            expect(component).toBeTruthy();
            expect(component.pdfUrl()).toBe('test.pdf');
            expect(component.version()).toBe(3);
            expect(component.initialPage()).toBe(5);
            expect(component.iframeSrc()).toBe('/pdf-viewer-iframe');
        });

        it('should handle ready message', () => {
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
            fixture.detectChanges();

            expect(component.iframeReady()).toBe(true);
        });

        it('should emit loadError event', () => {
            const loadErrorSpy = vi.fn();
            const loadErrorSubscription = component.loadError.subscribe(loadErrorSpy);
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(
                new MessageEvent('message', { data: { type: 'pdfLoadError', data: { url: 'failed.pdf' } }, origin: window.location.origin, source: iframe?.contentWindow }),
            );

            expect(loadErrorSpy).toHaveBeenCalledWith({ pdfUrl: 'failed.pdf' });
            loadErrorSubscription.unsubscribe();
        });

        it('should emit pagesLoaded event', () => {
            const pagesLoadedSpy = vi.fn();
            const pagesLoadedSubscription = component.pagesLoaded.subscribe(pagesLoadedSpy);
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(
                new MessageEvent('message', {
                    data: { type: 'pagesLoaded', data: { pagesCount: 12, url: 'loaded.pdf' } },
                    origin: window.location.origin,
                    source: iframe?.contentWindow,
                }),
            );

            expect(pagesLoadedSpy).toHaveBeenCalledWith({ pdfUrl: 'loaded.pdf', pagesCount: 12 });
            pagesLoadedSubscription.unsubscribe();
        });

        it('should emit downloadRequested event', () => {
            const downloadSpy = vi.fn();
            const downloadSubscription = component.downloadRequested.subscribe(downloadSpy);
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(
                new MessageEvent('message', {
                    data: { type: 'download' },
                    origin: window.location.origin,
                    source: iframe?.contentWindow,
                }),
            );

            expect(downloadSpy).toHaveBeenCalledOnce();
            downloadSubscription.unsubscribe();
        });

        it('should load PDF when iframe ready', async () => {
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.componentRef.setInput('initialPage', 5);
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(postMessageSpy).toHaveBeenCalledWith(
                { type: 'loadPDF', data: { url: 'test.pdf', initialPage: 5, isDarkMode: false, viewerMode: 'embedded' } },
                window.location.origin,
            );
        });

        it('should reload PDF when initialPage changes', async () => {
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.componentRef.setInput('initialPage', 1);
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
            fixture.detectChanges();
            await fixture.whenStable();

            postMessageSpy.mockClear();

            fixture.componentRef.setInput('initialPage', 7);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(postMessageSpy).toHaveBeenCalledWith(
                { type: 'loadPDF', data: { url: 'test.pdf', initialPage: 7, isDarkMode: false, viewerMode: 'embedded' } },
                window.location.origin,
            );
        });

        it('should notify iframe on theme change', async () => {
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

            component.iframeReady.set(true);
            fixture.detectChanges();
            await fixture.whenStable();
            postMessageSpy.mockClear();

            mockThemeService.currentTheme.set(Theme.DARK);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(postMessageSpy).toHaveBeenCalledWith({ type: 'themeChange', data: { isDarkMode: true } }, window.location.origin);
        });

        it('should call fullscreen service when openFullscreen message received', async () => {
            const openSpy = vi.spyOn(fullscreenService, 'open');
            const uploadDate = dayjs();

            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.componentRef.setInput('uploadDate', uploadDate);
            fixture.componentRef.setInput('version', 2);
            fixture.detectChanges();

            const inlineIframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: inlineIframe?.contentWindow }));
            window.dispatchEvent(
                new MessageEvent('message', { data: { type: 'pageChange', data: { page: 6 } }, origin: window.location.origin, source: inlineIframe?.contentWindow }),
            );
            window.dispatchEvent(new MessageEvent('message', { data: { type: 'openFullscreen' }, origin: window.location.origin, source: inlineIframe?.contentWindow }));
            fixture.detectChanges();

            expect(openSpy).toHaveBeenCalledWith('test.pdf', 6, uploadDate, 2);
        });

        it('should reject invalid pageChange values', () => {
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;

            // Test page = 0 (not > 0)
            window.dispatchEvent(
                new MessageEvent('message', {
                    data: { type: 'pageChange', data: { page: 0 } },
                    origin: window.location.origin,
                    source: iframe?.contentWindow,
                }),
            );
            expect(component['currentPage']()).toBe(1); // Should remain at default

            // Test negative page
            window.dispatchEvent(
                new MessageEvent('message', {
                    data: { type: 'pageChange', data: { page: -5 } },
                    origin: window.location.origin,
                    source: iframe?.contentWindow,
                }),
            );
            expect(component['currentPage']()).toBe(1);
        });

        it('should render footer when uploadDate or version provided', () => {
            fixture.componentRef.setInput('pdfUrl', 'test.pdf');
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.pdf-viewer-footer')).toBeFalsy();

            fixture.componentRef.setInput('version', 1);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.pdf-viewer-footer')).toBeTruthy();
        });
    });

    describe('Fullscreen Mode', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('mode', 'fullscreen');
        });

        it('should not render when closed', () => {
            fullscreenService.close();
            fixture.detectChanges();

            const overlay = fixture.nativeElement.querySelector('.pdf-fullscreen-overlay');
            expect(overlay).toBeFalsy();
        });

        it('should render when open', () => {
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const overlay = fixture.nativeElement.querySelector('.pdf-fullscreen-overlay');
            const iframe = fixture.nativeElement.querySelector('iframe');
            expect(overlay).toBeTruthy();
            expect(iframe).toBeTruthy();
        });

        it('should handle ready message', () => {
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
            fixture.detectChanges();

            expect(component.iframeReady()).toBe(true);
        });

        it('should load PDF when iframe ready', async () => {
            fullscreenService.open('test.pdf', 5, undefined, undefined);
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(postMessageSpy).toHaveBeenCalledWith(
                { type: 'loadPDF', data: { url: 'test.pdf', initialPage: 5, isDarkMode: false, viewerMode: 'fullscreen' } },
                window.location.origin,
            );
        });

        it('should update page on message', () => {
            const updateSpy = vi.spyOn(fullscreenService, 'updateCurrentPage');
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(new MessageEvent('message', { data: { type: 'pageChange', data: { page: 10 } }, origin: window.location.origin, source: iframe?.contentWindow }));

            expect(updateSpy).toHaveBeenCalledWith(10);
        });

        it('should close via button', () => {
            const closeSpy = vi.spyOn(fullscreenService, 'close');
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const closeButton = fixture.nativeElement.querySelector('.pdf-fullscreen-close') as HTMLButtonElement;
            closeButton.click();

            expect(closeSpy).toHaveBeenCalled();
            expect(component.iframeReady()).toBe(false);
        });

        it('should close via background click', () => {
            const closeSpy = vi.spyOn(fullscreenService, 'close');
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const overlay = fixture.nativeElement.querySelector('.pdf-fullscreen-overlay') as HTMLElement;
            overlay.click();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should stop propagation on window click', () => {
            const closeSpy = vi.spyOn(fullscreenService, 'close');
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const window = fixture.nativeElement.querySelector('.pdf-fullscreen-window') as HTMLElement;
            window.click();

            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should reset iframeReady when closing', () => {
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
            expect(component.iframeReady()).toBe(true);

            component.close();
            expect(component.iframeReady()).toBe(false);
        });

        it('should propagate theme changes to iframe', async () => {
            fullscreenService.open('test.pdf', 1, undefined, undefined);
            fixture.detectChanges();

            const iframe = component.pdfIframe()?.nativeElement;
            const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

            window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
            fixture.detectChanges();
            await fixture.whenStable();
            postMessageSpy.mockClear();

            mockThemeService.currentTheme.set(Theme.DARK);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(postMessageSpy).toHaveBeenCalledWith({ type: 'themeChange', data: { isDarkMode: true } }, window.location.origin);
        });

        it('should render footer with metadata', () => {
            const uploadDate = dayjs();
            fullscreenService.open('test.pdf', 1, uploadDate, 3);
            fixture.detectChanges();

            const footer = fixture.nativeElement.querySelector('.pdf-viewer-footer');
            expect(footer).toBeTruthy();
            expect(footer.textContent).toContain('3');
        });
    });
});
