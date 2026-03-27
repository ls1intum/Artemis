import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfViewerIframeWrapperComponent } from './pdf-viewer-iframe-wrapper.component';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

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

    it('should handle ready message and dispatch pdf-viewer-ready event', () => {
        const dispatchSpy = vi.spyOn(window, 'dispatchEvent');
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        const iframe = component.pdfIframe()?.nativeElement;
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
        fixture.detectChanges();

        expect(component.iframeReady()).toBe(true);
        expect(dispatchSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'pdf-viewer-ready', detail: { pdfUrl: 'test.pdf' } }));
    });

    it('should reject messages from wrong origin or source', () => {
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: 'https://evil.com', source: null }));
        expect(component.iframeReady()).toBe(false);

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: {} as Window }));
        expect(component.iframeReady()).toBe(false);
    });

    it('should dispatch pdfLoadError custom event', () => {
        const dispatchSpy = vi.spyOn(window, 'dispatchEvent');
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        const iframe = component.pdfIframe()?.nativeElement;
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'pdfLoadError' }, origin: window.location.origin, source: iframe?.contentWindow }));

        expect(dispatchSpy).toHaveBeenCalledWith(expect.objectContaining({ type: 'pdf-load-error', detail: { pdfUrl: 'test.pdf' } }));
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

        expect(postMessageSpy).toHaveBeenCalledWith({ type: 'loadPDF', data: { url: 'test.pdf', initialPage: 5, isDarkMode: false } }, window.location.origin);
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

    it('should pass correct isDarkMode for DARK theme', async () => {
        mockThemeService.currentTheme.set(Theme.DARK);
        fixture.componentRef.setInput('pdfUrl', 'test.pdf');
        fixture.detectChanges();

        const iframe = component.pdfIframe()?.nativeElement;
        const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(postMessageSpy).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ isDarkMode: true }) }), window.location.origin);
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
