import { Injectable, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';

interface IdleWindow {
    requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => void;
}

/**
 * Warms the PDFium engine (downloads + compiles the ~2 MB WebAssembly module in its worker, gzipped over
 * the wire and cached by the service worker) during browser idle time shortly after first render, so the
 * first lecture-PDF open is instant. Best-effort: if it never runs, the engine still initializes lazily on
 * first use. Gated to authenticated users so the login page does not pay the download.
 */
@Injectable({ providedIn: 'root' })
export class EmbedPdfPreloadService {
    private readonly pdfEngineService = inject(PdfEngineService);
    private readonly accountService = inject(AccountService);
    private scheduled = false;

    /** Schedules a one-time idle preload of the PDFium engine. Safe to call multiple times. */
    schedulePreload(): void {
        if (this.scheduled || typeof window === 'undefined') {
            return;
        }
        this.scheduled = true;
        const warm = () => {
            if (this.accountService.isAuthenticated()) {
                // Best-effort warm-up: swallow failures (e.g. a WASM fetch error) so the engine still
                // initializes lazily on first real use without an unhandled promise rejection here.
                this.pdfEngineService.getEngine().catch(() => {});
            }
        };
        const idleWindow = window as IdleWindow;
        if (idleWindow.requestIdleCallback) {
            idleWindow.requestIdleCallback(warm, { timeout: 5000 });
        } else {
            window.setTimeout(warm, 3000);
        }
    }
}
