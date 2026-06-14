import { downloadFile } from 'app/foundation/util/download.util';

/** UTF-8 byte-order mark so spreadsheet applications detect the encoding. */
const UTF8_BOM = '\uFEFF';
/** CSV line ending (matches the former export-to-csv dependency). */
const LINE_ENDING = '\r\n';

export type CsvCellValue = string | number | boolean | null | undefined;
export type CsvRow = Record<string, CsvCellValue>;

export interface CsvDownloadOptions {
    /** Column keys, used verbatim as the header labels and to read each row's values. */
    columnHeaders: string[];
    /** File name without extension; ".csv" is appended. */
    fileName: string;
    /** Field separator. Defaults to ';'. */
    fieldSeparator?: string;
    /** Quote every field when true; otherwise only fields containing the separator, the quote character or a line break are quoted. Defaults to true. */
    quoteStrings?: boolean;
    /** Quote character. Defaults to '"'. */
    quoteCharacter?: string;
    /** Decimal separator for non-integer numbers: 'locale' for locale-aware formatting, or a literal separator such as ',' or '.'. Defaults to '.'. */
    decimalSeparator?: string;
}

interface ResolvedOptions {
    separator: string;
    quoteCharacter: string;
    quoteStrings: boolean;
    decimalSeparator: string;
}

/**
 * Prefixes values that look like spreadsheet formulas with a quote so Excel and similar tools treat
 * them as plain text instead of executing them. Exported fields can contain user-controlled input
 * (e.g. student names), while the CSV is typically opened by instructors or admins.
 *
 * A lone leading symbol (e.g. the "-" used as an empty-value placeholder in score exports) cannot
 * form a formula, so only values with content after the leading "=", "+", "-" or "@" are guarded.
 */
function sanitizeAgainstCsvInjection(value: string): string {
    const trimmed = value.trim();
    return /^[=+\-@]/.test(trimmed) && trimmed.length > 1 ? `'${value}` : value;
}

/** Wraps a string in quotes when required, escaping embedded double quotes by doubling them. */
function quoteString(value: string, options: ResolvedOptions): string {
    const needsQuoting =
        options.quoteStrings ||
        (!!options.separator && value.includes(options.separator)) ||
        (!!options.quoteCharacter && value.includes(options.quoteCharacter)) ||
        value.includes('\n') ||
        value.includes('\r');
    if (!needsQuoting) {
        return value;
    }
    const escaped = value.includes(options.quoteCharacter) ? value.replaceAll(options.quoteCharacter, options.quoteCharacter + options.quoteCharacter) : value;
    return options.quoteCharacter + escaped + options.quoteCharacter;
}

/** Formats a number, applying the decimal separator to non-integer values (matching export-to-csv). */
function formatNumber(value: number, decimalSeparator: string): string {
    const isFractionalOrInfinite = value === value && (!isFinite(value) || Boolean(value % 1));
    if (isFractionalOrInfinite) {
        if (decimalSeparator === 'locale') {
            return value.toLocaleString();
        }
        if (decimalSeparator) {
            return value.toString().replace('.', decimalSeparator);
        }
    }
    return value.toString();
}

/** Formats a single cell: numbers/booleans are emitted unquoted, strings are injection-guarded and quoted. */
function formatCell(value: CsvCellValue, options: ResolvedOptions): string {
    if (typeof value === 'number') {
        return formatNumber(value, options.decimalSeparator);
    }
    if (typeof value === 'boolean') {
        return value ? 'TRUE' : 'FALSE';
    }
    if (value === undefined) {
        return quoteString('', options);
    }
    if (value === null) {
        return quoteString('null', options);
    }
    return quoteString(sanitizeAgainstCsvInjection(value), options);
}

/**
 * Builds a CSV from the given rows and triggers its download. Reproduces the output format of the
 * former `export-to-csv` dependency (UTF-8 BOM, CRLF line endings, a header row built from
 * `columnHeaders`, strings optionally quoted, numbers/booleans unquoted) and additionally guards
 * every string cell against CSV/formula injection.
 */
export function downloadCsv(rows: readonly CsvRow[], options: CsvDownloadOptions): void {
    const resolved: ResolvedOptions = {
        separator: options.fieldSeparator ?? ';',
        quoteCharacter: options.quoteCharacter ?? '"',
        quoteStrings: options.quoteStrings ?? true,
        decimalSeparator: options.decimalSeparator ?? '.',
    };

    const headerLine = options.columnHeaders.map((header) => quoteString(header, resolved)).join(resolved.separator);
    const dataLines = rows.map((row) => options.columnHeaders.map((key) => formatCell(row[key], resolved)).join(resolved.separator));

    const csv = UTF8_BOM + [headerLine, ...dataLines].join(LINE_ENDING) + LINE_ENDING;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    downloadFile(blob, `${options.fileName}.csv`);
}
