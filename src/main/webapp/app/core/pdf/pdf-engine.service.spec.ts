import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';

// The real worker-engine module spawns a Web Worker at import time, which cannot run under jsdom,
// so the dynamic import is replaced with a factory that records how the engine is configured.
const createPdfiumEngine = vi.fn();
vi.mock('@embedpdf/engines/pdfium-worker-engine', () => ({
    createPdfiumEngine: (...args: unknown[]) => createPdfiumEngine(...args),
}));

describe('PdfEngineService', () => {
    setupTestBed({ zoneless: true });

    let service: PdfEngineService;
    const fakeEngine = { id: 'pdfium-engine' };

    beforeEach(() => {
        createPdfiumEngine.mockReset();
        createPdfiumEngine.mockResolvedValue(fakeEngine);
        TestBed.configureTestingModule({ providers: [PdfEngineService] });
        service = TestBed.inject(PdfEngineService);
    });

    afterEach(() => vi.restoreAllMocks());

    it('should create the PDFium worker engine pointed at the self-hosted wasm with an encoder pool', async () => {
        const engine = await service.getEngine();

        expect(engine).toBe(fakeEngine);
        expect(createPdfiumEngine).toHaveBeenCalledOnce();
        const [wasmUrl, options] = createPdfiumEngine.mock.calls[0];
        expect(wasmUrl).toContain('assets/embedpdf/pdfium.wasm');
        expect(options).toMatchObject({ encoderPoolSize: 2, fontFallback: null });
    });

    it('should memoize the engine so it is created at most once per session', async () => {
        const first = service.getEngine();
        const second = service.getEngine();

        expect(first).toBe(second);
        await Promise.all([first, second]);
        expect(createPdfiumEngine).toHaveBeenCalledOnce();
    });

    it('should retry engine creation after a failure instead of caching the rejection', async () => {
        createPdfiumEngine.mockRejectedValueOnce(new Error('wasm failed'));

        await expect(service.getEngine()).rejects.toThrow('wasm failed');

        // The rejected promise must not be cached, so a later call retries and resolves.
        const engine = await service.getEngine();
        expect(engine).toBe(fakeEngine);
        expect(createPdfiumEngine).toHaveBeenCalledTimes(2);
    });
});
