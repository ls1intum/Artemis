import { vi } from 'vitest';

/** Minimal stand-in for an EmbedPDF `Task<R>`: only `toPromise()` is exercised by the components. */
function task<R>(value: R): { toPromise: () => Promise<R>; wait: (onResolved: (v: R) => void) => void; abort: () => void } {
    return {
        toPromise: () => Promise.resolve(value),
        wait: (onResolved: (v: R) => void) => onResolved(value),
        abort: () => {},
    };
}

/** Builds a fake `PdfDocumentObject` with the given number of A4-sized pages. */
export function createMockPdfDocument(id: string, pageCount: number): any {
    return {
        id,
        pageCount,
        pages: Array.from({ length: pageCount }, (_, index) => ({ index, size: { width: 595, height: 842 }, rotation: 0 })),
        isEncrypted: false,
    };
}

/**
 * Test double for {@link PdfEngineService}. `getEngine()` resolves to a fake PDFium engine whose methods
 * return resolved `Task`s, so components can be unit-tested without the real WebAssembly worker engine
 * (which cannot run under jsdom). Individual methods are `vi.fn()`s and can be overridden per test.
 */
export class MockPdfEngineService {
    readonly engine = {
        openDocumentBuffer: vi.fn((file: { id: string }) => task(createMockPdfDocument(file.id, 3))),
        openDocumentUrl: vi.fn((file: { id: string }) => task(createMockPdfDocument(file.id, 3))),
        renderPage: vi.fn(() => task(new Blob(['pdf-page'], { type: 'image/png' }))),
        renderPageRaw: vi.fn(() => task({ data: new Uint8ClampedArray(2 * 2 * 4), width: 2, height: 2 })),
        searchAllPages: vi.fn(() => task({ results: [], total: 0 })),
        createDocument: vi.fn((id: string) => task(createMockPdfDocument(id, 0))),
        importPages: vi.fn(() => task([])),
        deletePage: vi.fn(() => task(true)),
        extractPages: vi.fn(() => task(new ArrayBuffer(8))),
        mergePages: vi.fn((configs: Array<{ docId: string; pageIndices: number[] }>) => task({ id: 'merged', content: new ArrayBuffer(8), name: 'merged.pdf' })),
        saveAsCopy: vi.fn(() => task(new ArrayBuffer(8))),
        closeDocument: vi.fn(() => task(true)),
    };

    getEngine = vi.fn(() => Promise.resolve(this.engine));
}
