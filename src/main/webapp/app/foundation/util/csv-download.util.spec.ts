import { beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadCsv } from 'app/foundation/util/csv-download.util';

const { downloadFileMock } = vi.hoisted(() => ({ downloadFileMock: vi.fn() }));
vi.mock('app/foundation/util/download.util', () => ({
    downloadFile: downloadFileMock,
}));

const BOM = '\uFEFF';

/**
 * Returns the CSV text passed to downloadFile by the most recent downloadCsv call. Decodes with
 * ignoreBOM so the leading byte-order mark is preserved (Blob.text() would strip it).
 */
async function lastCsv(): Promise<{ fileName: string; content: string }> {
    expect(downloadFileMock).toHaveBeenCalledOnce();
    const [blob, fileName] = downloadFileMock.mock.calls[0] as [Blob, string];
    const buffer = await blob.arrayBuffer();
    const content = new TextDecoder('utf-8', { ignoreBOM: true }).decode(buffer);
    return { fileName, content };
}

describe('csv-download.util', () => {
    beforeEach(() => downloadFileMock.mockClear());

    it('builds a BOM-prefixed, CRLF-delimited CSV and downloads it with a .csv name', async () => {
        downloadCsv([{ a: 'x', b: 1 }], { columnHeaders: ['a', 'b'], fileName: 'report' });
        const { fileName, content } = await lastCsv();
        expect(fileName).toBe('report.csv');
        // strings quoted by default, numbers unquoted, ';' separator
        expect(content).toBe(`${BOM}"a";"b"\r\n"x";1\r\n`);
    });

    it('only quotes fields that need it when quoteStrings is false', async () => {
        downloadCsv([{ a: 'plain', b: 'has;separator' }], { columnHeaders: ['a', 'b'], fileName: 'f', quoteStrings: false });
        const { content } = await lastCsv();
        expect(content).toBe(`${BOM}a;b\r\nplain;"has;separator"\r\n`);
    });

    it('renders booleans as TRUE/FALSE without quotes', async () => {
        downloadCsv([{ flag: true, other: false }], { columnHeaders: ['flag', 'other'], fileName: 'f', quoteStrings: false });
        const { content } = await lastCsv();
        expect(content).toBe(`${BOM}flag;other\r\nTRUE;FALSE\r\n`);
    });

    it('applies a literal decimal separator to non-integer numbers but leaves integers untouched', async () => {
        downloadCsv([{ fraction: 85.5, whole: 1000 }], { columnHeaders: ['fraction', 'whole'], fileName: 'f', quoteStrings: false, decimalSeparator: ',' });
        const { content } = await lastCsv();
        expect(content).toBe(`${BOM}fraction;whole\r\n85,5;1000\r\n`);
    });

    it('supports a custom field separator', async () => {
        downloadCsv([{ a: 'x', b: 'y' }], { columnHeaders: ['a', 'b'], fileName: 'f', fieldSeparator: ',', quoteStrings: false });
        const { content } = await lastCsv();
        expect(content).toBe(`${BOM}a,b\r\nx,y\r\n`);
    });

    it('guards string cells that look like spreadsheet formulas against CSV injection', async () => {
        downloadCsv([{ name: '=HYPERLINK("https://example.com")', note: '+1', plain: ' normal' }], {
            columnHeaders: ['name', 'note', 'plain'],
            fileName: 'f',
            quoteStrings: true,
        });
        const { content } = await lastCsv();
        // formula-like values are prefixed with a single quote; embedded double quotes are doubled
        expect(content).toContain('"\'=HYPERLINK(""https://example.com"")"');
        expect(content).toContain('"\'+1"');
        // a value that does not look like a formula is left as-is (still quoted)
        expect(content).toContain('" normal"');
    });

    it('guards header cells that look like spreadsheet formulas against CSV injection', async () => {
        // headers can be built from authored titles (e.g. exercise or grading-criterion names)
        downloadCsv([{ '=HYPERLINK("https://evil.com")': 'x' }], {
            columnHeaders: ['=HYPERLINK("https://evil.com")'],
            fileName: 'f',
            quoteStrings: true,
        });
        const { content } = await lastCsv();
        // the formula-like header is prefixed with a single quote; embedded double quotes are doubled
        expect(content).toContain('"\'=HYPERLINK(""https://evil.com"")"');
    });

    it('does not treat a lone leading symbol (e.g. the "-" empty-value placeholder) as a formula', async () => {
        downloadCsv([{ score: '-', plus: '+', at: '@', equals: '=' }], { columnHeaders: ['score', 'plus', 'at', 'equals'], fileName: 'f', quoteStrings: false });
        const { content } = await lastCsv();
        expect(content).toBe(`${BOM}score;plus;at;equals\r\n-;+;@;=\r\n`);
    });

    it('emits a header row even when there are no data rows', async () => {
        downloadCsv([], { columnHeaders: ['a', 'b'], fileName: 'f', quoteStrings: false });
        const { content } = await lastCsv();
        expect(content).toBe(`${BOM}a;b\r\n`);
    });
});
