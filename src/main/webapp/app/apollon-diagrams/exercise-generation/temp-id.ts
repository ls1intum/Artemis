/**
 * Generates a temporary ID that we can assign to a DragItem or DropLocation
 * so that we can refer to those objects before the server has created an ID.
 */
export function generate() {
    return Math.floor(Math.random() * Number.MAX_SAFE_INTEGER);
}
