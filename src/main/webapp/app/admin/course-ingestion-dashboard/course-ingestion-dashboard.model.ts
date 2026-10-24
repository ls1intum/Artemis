/**
 * What the browser's detail pane is currently showing. A discriminated union rather than a parsed string, so the detail
 * pane switches on `kind` and cannot misread one id as another.
 */
export type BrowserSelection =
    { kind: 'type'; type: string } | { kind: 'lecture'; lectureId: number } | { kind: 'unit'; unitId: number } | { kind: 'collection'; unitId: number; key: string };

/** Stable string form of a selection, used to track expansion and to mark nodes in the DOM. */
export function selectionKey(selection: BrowserSelection): string {
    switch (selection.kind) {
        case 'type':
            return `type:${selection.type}`;
        case 'lecture':
            return `lecture:${selection.lectureId}`;
        case 'unit':
            return `unit:${selection.unitId}`;
        case 'collection':
            return `coll:${selection.unitId}:${selection.key}`;
    }
}
