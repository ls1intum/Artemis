/**
 * Generates a temporary ID that we can assign to a DragItem or DropLocation or other quiz element
 * so that we can refer to those objects before the server has created an ID.
 */
export function generate(): number {
    return Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
}
