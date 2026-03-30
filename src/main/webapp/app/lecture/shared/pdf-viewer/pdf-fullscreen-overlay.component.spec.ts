import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PdfFullscreenOverlayComponent } from './pdf-fullscreen-overlay.component';
import { PdfFullscreenOverlayService } from './pdf-fullscreen-overlay.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';

describe('PdfFullscreenOverlayComponent', () => {
    setupTestBed({ zoneless: true });

    let component: PdfFullscreenOverlayComponent;
    let fixture: ComponentFixture<PdfFullscreenOverlayComponent>;
    let mockThemeService: { currentTheme: ReturnType<typeof signal<Theme>> };
    let fullscreenService: PdfFullscreenOverlayService;

    beforeEach(async () => {
        mockThemeService = { currentTheme: signal(Theme.LIGHT) };
        await TestBed.configureTestingModule({
            imports: [PdfFullscreenOverlayComponent],
            providers: [
                provideHttpClient(),
                PdfFullscreenOverlayService,
                { provide: ThemeService, useValue: mockThemeService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfFullscreenOverlayComponent);
        component = fixture.componentInstance;
        fullscreenService = TestBed.inject(PdfFullscreenOverlayService);
    });

    afterEach(() => {
        vi.clearAllMocks();
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

        const iframe = component.fullscreenIframe()?.nativeElement;
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
        fixture.detectChanges();

        expect(component.iframeReady()).toBe(true);
    });

    it('should load PDF when iframe ready', async () => {
        fullscreenService.open('test.pdf', 5, undefined, undefined);
        fixture.detectChanges();

        const iframe = component.fullscreenIframe()?.nativeElement;
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

        const iframe = component.fullscreenIframe()?.nativeElement;
        window.dispatchEvent(new MessageEvent('message', { data: { type: 'pageChange', data: { page: 10 } }, origin: window.location.origin, source: iframe?.contentWindow }));

        expect(updateSpy).toHaveBeenCalledWith(10);
    });

    it('should not reload PDF when only page changes', async () => {
        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();

        const iframe = component.fullscreenIframe()?.nativeElement;
        const postMessageSpy = vi.spyOn(iframe!.contentWindow!, 'postMessage');

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: iframe?.contentWindow }));
        fixture.detectChanges();
        await fixture.whenStable();

        // Verify initial load happened
        expect(postMessageSpy).toHaveBeenCalledWith(
            { type: 'loadPDF', data: { url: 'test.pdf', initialPage: 1, isDarkMode: false, viewerMode: 'fullscreen' } },
            window.location.origin,
        );
        postMessageSpy.mockClear();

        // Update page via service (simulates user scrolling)
        fullscreenService.updateCurrentPage(10);
        fixture.detectChanges();
        await fixture.whenStable();

        // Should NOT send loadPDF message when only page changes
        expect(postMessageSpy).not.toHaveBeenCalledWith(expect.objectContaining({ type: 'loadPDF' }), expect.anything());
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

    it('should propagate theme changes to iframe', async () => {
        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();

        const iframe = component.fullscreenIframe()?.nativeElement;
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

    it('should reject messages from wrong origin', () => {
        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: 'https://evil.com', source: null }));
        expect(component.iframeReady()).toBe(false);
    });

    it('should reject messages from wrong source', () => {
        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();

        window.dispatchEvent(new MessageEvent('message', { data: { type: 'ready' }, origin: window.location.origin, source: {} as Window }));
        expect(component.iframeReady()).toBe(false);
    });

    it('should reject messages with invalid data', () => {
        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();

        const iframe = component.fullscreenIframe()?.nativeElement;

        // Test with null data
        window.dispatchEvent(new MessageEvent('message', { data: null, origin: window.location.origin, source: iframe?.contentWindow }));
        expect(component.iframeReady()).toBe(false);

        // Test with non-object data
        window.dispatchEvent(new MessageEvent('message', { data: 'invalid', origin: window.location.origin, source: iframe?.contentWindow }));
        expect(component.iframeReady()).toBe(false);
    });

    it('should cleanup on destroy', () => {
        const removeSpy = vi.spyOn(window, 'removeEventListener');

        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();
        fixture.destroy();

        expect(removeSpy).toHaveBeenCalledWith('message', expect.any(Function));
    });

    it('should not render footer without metadata', () => {
        fullscreenService.open('test.pdf', 1, undefined, undefined);
        fixture.detectChanges();

        const footer = fixture.nativeElement.querySelector('.pdf-viewer-footer');
        expect(footer).toBeFalsy();
    });
});
