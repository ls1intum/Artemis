import { TextChange } from './text-change.model';

type Annotation = { row: number; column: number; text: string; type: string; ts: number };

export class AnnotationArray extends Array<Annotation> {
    /**
     * Recalculates the position of annotations according to changes in the editor.
     * Annotations are affected by changes in previous rows for row updates,
     * in the same row and previous columns for column updates.
     * Similar to map/reduce doesn't mutate the data structure but returns a new one.
     * @param change
     */
    update(change: TextChange): AnnotationArray {
        const {
            start: { row: rowStart, column: columnStart },
            end: { row: rowEnd, column: columnEnd },
            action
        } = change;
        if (action === 'remove' || action === 'insert') {
            const sign = action === 'remove' ? -1 : 1;
            const updateRowDiff = sign * (rowEnd - rowStart);
            const updateColDiff = sign * (columnEnd - columnStart);
            const updatedAnnotations = this.map(({ row, column, ...rest }) => {
                return {
                    ...rest,
                    row: row > rowStart ? row + updateRowDiff : row,
                    column: column > columnStart && row === rowStart && row === rowEnd ? column + updateColDiff : column
                };
            });
            return new AnnotationArray(...updatedAnnotations);
        }
    }
}
