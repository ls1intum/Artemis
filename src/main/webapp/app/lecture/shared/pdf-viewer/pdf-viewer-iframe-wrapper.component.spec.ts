import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerIframeWrapperComponent } from './pdf-viewer-iframe-wrapper.component';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PdfFullscreenOverlayService } from './pdf-fullscreen-overlay.service';
import dayjs from 'dayjs/esm';

describe('PdfViewerIframeWrapperComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfViewerIframeWrapperComponent;
    let fixture: ComponentFixture<PdfViewerIframeWrapperComponent>;
    let mockThemeService: { currentTheme: ReturnType<typeof signal<Theme>> };

    beforeEach(async () => {
        mockThemeService = { currentTheme: signal(Theme.LIGHT) };
        await TestBed.configureTestingModule({
            imports: [PdfViewerIframeWrapperComponent],
            providers: [provideHttpClient(), { provide: ThemeService, useValue: mockThemeService }, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfViewerIframeWrapperComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.clearAllMocks();
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

    it('should reject messages from wrong origin or source', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: 'https://evil.com', source: null }));
        expect(component.iframeReady()).toBe(false);

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: {} as Window }));
        expect(component.iframeReady()).toBe(false);
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
        const fullscreenService = TestBed.inject(PdfFullscreenOverlayService);
        const openSpy = vi.spyOn(fullscreenService, 'open');
        const uploadDate = dayjs();

        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.componentRef.setInput('uploadDate', uploadDate);
        fixture.componentRef.setInput('version', 2);
        fixture.detectChanges();

        const inlineIframe = component.pdfIframe()?.nativeElement;
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: inlineIframe?.contentWindow }));
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'pageChange', data: { page: 6 } }, origin: window.location.origin, source: inlineIframe?.contentWindow }));
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'openFullscreen' }, origin: window.location.origin, source: inlineIframe?.contentWindow }));
        fixture.detectChanges();

        expect(openSpy).toHaveBeenCalledWith('test.pdf', 6, uploadDate, 2);
    });

    it('should reload inline iframe when fullscreen closes', async () => {
        const fullscreenService = TestBed.inject(PdfFullscreenOverlayService);

        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        const inlineIframe = component.pdfIframe()?.nativeElement;
        const inlinePostMessageSpy = vi.spyOn(inlineIframe!.contentWindow!, 'postMessage');
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: inlineIframe?.contentWindow }));
        fixture.detectChanges();
        await fixture.whenStable();
        inlinePostMessageSpy.mockClear();

        // Simulate fullscreen opening
        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();
        await fixture.whenStable();

        // Simulate page change in fullscreen (now uses separate signal)
        fullscreenService.updateCurrentPage(8);
        fixture.detectChanges();
        await fixture.whenStable();

        // Simulate fullscreen closing
        fullscreenService.close();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(inlinePostMessageSpy).toHaveBeenCalledWith(
            { type: 'loadPDF', data: { url: 'test.pdf', initialPage: 8, isDarkMode: false, viewerMode: 'embedded' } },
            window.location.origin,
        );
    });

    it('should render footer when uploadDate or version provided', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.pdf-viewer-footer')).toBeFalsy();

        fixture.componentRef.setInput('version', 1);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.pdf-viewer-footer')).toBeTruthy();
    });

    it('should cleanup on destroy', () => {
        const removeSpy = vi.spyOn(window, 'removeEventListener');

        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();
        fixture.destroy();

        expect(removeSpy).toHaveBeenCalledWith('message', expect.any(Function));
    });
});
