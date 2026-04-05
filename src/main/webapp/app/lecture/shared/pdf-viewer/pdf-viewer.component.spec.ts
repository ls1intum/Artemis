import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerComponent } from './pdf-viewer.component';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PdfViewerComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfViewerComponent;
    let fixture: ComponentFixture<PdfViewerComponent>;
    let mockThemeService: { currentTheme: ReturnType<typeof signal<Theme>> };
    let translateService: MockTranslateService;

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
            providers: [provideHttpClient(), { provide: ThemeService, useValue: mockThemeService }, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        translateService.use('en');
        fixture = TestBed.createComponent(PdfViewerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
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
        expect(component.isFullscreen()).toBe(false);
        expect(fixture.nativeElement.querySelector('.pdf-fullscreen-overlay')).toBeFalsy();
    });

    it('should handle ready message', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        sendIframeMessage('ready');
        fixture.detectChanges();

        expect(component.iframeReady()).toBe(true);
    });

    it('should emit events (loadError, pageRendered, downloadRequested)', () => {
        const loadErrorSpy = vi.fn();
        const pageRenderedSpy = vi.fn();
        const downloadSpy = vi.fn();

        component.loadError.subscribe(loadErrorSpy);
        component.pageRendered.subscribe(pageRenderedSpy);
        component.downloadRequested.subscribe(downloadSpy);

        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        sendIframeMessage('pdfLoadError', { url: 'failed.pdf' });
        expect(loadErrorSpy).toHaveBeenCalledWith({ pdfUrl: 'failed.pdf' });

        sendIframeMessage('pageRendered', { url: 'rendered.pdf' });
        expect(pageRenderedSpy).toHaveBeenCalledWith({ pdfUrl: 'rendered.pdf' });

        sendIframeMessage('download');
        expect(downloadSpy).toHaveBeenCalledOnce();
    });

    it('should enter fullscreen on openFullscreen message without triggering PDF reload', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        sendIframeMessage('ready');
        fixture.detectChanges();

        const iframe = component.pdfIframe()?.nativeElement;
        const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');
        postMessageSpy.mockClear();

        sendIframeMessage('openFullscreen');
        fixture.detectChanges();

        expect(component.isFullscreen()).toBe(true);
        expect(fixture.nativeElement.querySelector('.pdf-fullscreen-overlay')).toBeTruthy();
        expect(postMessageSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'viewerModeChange', data: { viewerMode: 'fullscreen' } }), window.location.origin);
        expect(postMessageSpy).not.toHaveBeenCalledWith(expect.objectContaining({ type: 'loadPDF' }), window.location.origin);
    });

    it('should close fullscreen on closeFullscreen message and sync mode back to embedded', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        sendIframeMessage('ready');
        sendIframeMessage('openFullscreen');
        fixture.detectChanges();
        expect(component.isFullscreen()).toBe(true);

        const iframe = component.pdfIframe()?.nativeElement;
        const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');
        postMessageSpy.mockClear();

        sendIframeMessage('closeFullscreen');
        fixture.detectChanges();

        expect(component.isFullscreen()).toBe(false);
        expect(postMessageSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'viewerModeChange', data: { viewerMode: 'embedded' } }), window.location.origin);
    });

    it('should keep loading spinner visible until first pageRendered', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        sendIframeMessage('ready');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.spinner-border')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('.pdf-iframe')?.classList.contains('pdf-iframe--hidden')).toBe(true);

        sendIframeMessage('pageRendered', { url: 'test.pdf' });
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.spinner-border')).toBeFalsy();
        expect(fixture.nativeElement.querySelector('.pdf-iframe')?.classList.contains('pdf-iframe--hidden')).toBe(false);
    });

    it('should reload the current PDF when the iframe sends ready again', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.componentRef.setInput('initialPage', 3);
        fixture.detectChanges();

        const iframe = component.pdfIframe()?.nativeElement;
        expect(iframe?.contentWindow).toBeTruthy();

        const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

        sendIframeMessage('ready');
        sendIframeMessage('pageChange', { page: 5 });
        postMessageSpy.mockClear();

        sendIframeMessage('ready');
        fixture.detectChanges();

        expect(postMessageSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                type: 'loadPDF',
                data: expect.objectContaining({ url: 'test.pdf', initialPage: 5, languageKey: 'en' }),
            }),
            window.location.origin,
        );
    });

    it('should sync language changes to the iframe after it is ready', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        sendIframeMessage('ready');
        fixture.detectChanges();

        const iframe = component.pdfIframe()?.nativeElement;
        const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');
        postMessageSpy.mockClear();

        translateService.use('de');
        fixture.detectChanges();

        expect(postMessageSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'languageChange', data: { languageKey: 'de' } }), window.location.origin);
    });
});
