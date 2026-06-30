import { Injectable } from '@angular/core';

/**
 * Type of the EmbedPDF PDFium engine, derived without a static runtime import so that the (large)
 * worker-engine module stays out of the initial bundle and is only pulled in as a lazy chunk.
 */
type WorkerPdfEngine = Awaited<ReturnType<(typeof import('@embedpdf/engines/pdfium-worker-engine'))['createPdfiumEngine']>>;

/**
 * Singleton accessor for the EmbedPDF PDFium engine (PDFium compiled to WebAssembly).
 *
 * The engine runs in a dedicated Web Worker that EmbedPDF spawns from an inlined blob (so no separate
 * worker asset is shipped), and it loads the self-hosted `pdfium.wasm` delivered by the Artemis backend
 * under `assets/embedpdf/`. This is the single PDF engine for both the lecture viewer and the instructor
 * preview/editor; it replaces the previous pdf.js engines (ngx-extended-pdf-viewer + pdfjs-dist).
 *
 * The engine module is loaded via a dynamic import, so it forms its own lazy chunk and never enters the
 * initial bundle (it is fetched during the idle preload or on first PDF use).
 */
@Injectable({ providedIn: 'root' })
export class PdfEngineService {
    private enginePromise?: Promise<WorkerPdfEngine>;

    /**
     * Returns the lazily-created, shared PDFium worker engine. Subsequent calls reuse the same instance,
     * so the worker-engine chunk and the ~2 MB WebAssembly module are loaded at most once per session.
     */
    getEngine(): Promise<WorkerPdfEngine> {
        if (!this.enginePromise) {
            this.enginePromise = this.createEngine();
        }
        return this.enginePromise;
    }

    private async createEngine(): Promise<WorkerPdfEngine> {
        const { createPdfiumEngine } = await import('@embedpdf/engines/pdfium-worker-engine');
        // Absolute URL: the engine runs in a Web Worker, which resolves relative URLs against its own
        // blob: origin, so a root-relative path would not work. document.baseURI honours the <base href>.
        const wasmUrl = new URL('assets/embedpdf/pdfium.wasm', document.baseURI).toString();
        // encoderPoolSize 2: encode rendered page images in a small pool of dedicated workers (spawned from
        // inlined blob URLs, like the main engine worker) so PNG encoding stays off the main thread. A size of
        // 0 would disable the pool and force a main-thread Canvas fallback, which janks the UI and logs a
        // warning per page. fontFallback null: never request fonts from a remote CDN (everything is self-hosted).
        return createPdfiumEngine(wasmUrl, { encoderPoolSize: 2, fontFallback: null });
    }
}
