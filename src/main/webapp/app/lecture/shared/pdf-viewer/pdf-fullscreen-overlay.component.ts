import { Component, DestroyRef, ElementRef, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { Location } from '@angular/common';
import { PdfFullscreenOverlayService } from './pdf-fullscreen-overlay.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-pdf-fullscreen-overlay',
    standalone: true,
    imports: [ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective, SafeResourceUrlPipe, FaIconComponent],
    templateUrl: './pdf-fullscreen-overlay.component.html',
    styleUrls: ['./pdf-fullscreen-overlay.component.scss'],
})
export class PdfFullscreenOverlayComponent {
    private readonly service = inject(PdfFullscreenOverlayService);
    private readonly themeService = inject(ThemeService);
    private readonly location = inject(Location);

    readonly state = this.service.fullscreenState;
    readonly fullscreenIframe = viewChild<ElementRef<HTMLIFrameElement>>('fullscreenIframe');
    readonly iframeReady = signal(false);
    readonly iframeSrc = this.location.prepareExternalUrl('pdf-viewer-iframe');

    // Icons
    protected readonly faXmark = faXmark;

    constructor() {
        const destroyRef = inject(DestroyRef);

        // Load PDF when iframe ready
        effect(() => {
            const state = this.state();
            if (state.isOpen && this.iframeReady() && state.pdfUrl) {
                this.loadPdf(state.pdfUrl, state.currentPage ?? 1);
            }
        });

        // Sync theme changes
        effect(() => {
            const isDarkMode = this.themeService.currentTheme() === Theme.DARK;
            if (this.iframeReady()) {
                this.postMessage('themeChange', { isDarkMode });
            }
        });

        // Listen for iframe messages
        window.addEventListener('message', this.handleIframeMessage);
        destroyRef.onDestroy(() => {
            window.removeEventListener('message', this.handleIframeMessage);
        });
    }

    close(): void {
        this.service.close();
        this.iframeReady.set(false);
    }

    private handleIframeMessage = (event: MessageEvent): void => {
        if (event.origin !== window.location.origin) {
            return;
        }

        const iframe = this.fullscreenIframe()?.nativeElement;
        if (!iframe?.contentWindow || event.source !== iframe.contentWindow) {
            return;
        }

        const { type, data } = event.data;

        if (type === 'ready') {
            this.iframeReady.set(true);
        } else if (type === 'pageChange' && data?.page) {
            this.service.updateCurrentPage(data.page);
        }
    };

    private loadPdf(url: string, page: number): void {
        const isDarkMode = untracked(() => this.themeService.currentTheme() === Theme.DARK);
        this.postMessage('loadPDF', { url, initialPage: page, isDarkMode, viewerMode: 'fullscreen' });
    }

    private postMessage(type: string, data: any): void {
        const iframe = this.fullscreenIframe()?.nativeElement;
        if (iframe?.contentWindow) {
            iframe.contentWindow.postMessage({ type, data }, window.location.origin);
        }
    }
}
