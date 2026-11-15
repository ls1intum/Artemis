/** Splits a CSV file into rows of cells, honoring quoted cells; the separator is the one, of comma, semicolon and tab, that the header line uses most. */
export function parseCsv(csv: string): string[][] {
    const text = csv.replace(/^\uFEFF/, '');
    const header = text.split(/\r?\n/, 1)[0];
    const separator = [',', ';', '\t'].reduce((best, candidate) => (header.split(candidate).length > header.split(best).length ? candidate : best), ',');
    const rows: string[][] = [];
    let row: string[] = [];
    let cell = '';
    let quoted = false;
    for (let i = 0; i < text.length; i++) {
        const char = text[i];
        if (quoted) {
            if (char === '"' && text[i + 1] === '"') {
                cell += '"';
                i++;
            } else if (char === '"') {
                quoted = false;
            } else {
                cell += char;
            }
        } else if (char === '"') {
            quoted = true;
        } else if (char === separator) {
            row.push(cell);
            cell = '';
        } else if (char === '\n' || char === '\r') {
            if (char === '\r' && text[i + 1] === '\n') {
                i++;
            }
            row.push(cell);
            cell = '';
            if (row.some((value) => value !== '')) {
                rows.push(row);
            }
            row = [];
        } else {
            cell += char;
        }
    }
    if (cell !== '' || row.length > 0) {
        row.push(cell);
        rows.push(row);
    }
    return rows;
}
