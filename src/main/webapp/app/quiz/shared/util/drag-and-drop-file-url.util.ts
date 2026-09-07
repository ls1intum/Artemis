/**
 * Rebuilds the question-scoped path a drag item picture is served under, from a value that may or may not already carry it.
 *
 * The server sends the whole path (`DragItemDTO` fills it in from the owning question, mirroring `PublicFileUrl.DragItem`), so on a current response this is the identity: the
 * value is reduced to its filename and the same path is assembled again. It stays because a client can still hold a value that does not carry the path: a response from a node
 * running the previous release during a rolling deployment, a locally edited item, or anything cached in between. Such a value is not reachable on its own, because the URL that
 * serves a drag item picture is scoped by its question. Renaming the route means renaming it here too.
 *
 * The result is relative to `api/core/files/`, the same shape the server sends for every other file reference (`PublicFileUrl#clientPath`), so a caller passes it through
 * `addPublicFilePrefix` exactly as it would a value it received.
 *
 * @param questionId the id of the owning drag and drop question
 * @param dragItemId the question-scoped id of the drag item
 * @param storedValue the drag item's `pictureFilePath`, which is the served path on a current response and a bare filename on an older one
 * @returns the served path, relative to `api/core/files/`
 */
export function dragItemPicturePath(questionId: number, dragItemId: number, storedValue: string): string {
    return `drag-and-drop/questions/${questionId}/drag-items/${dragItemId}/${filenameOf(storedValue)}`;
}

/**
 * Reduces a stored file reference to its filename, mirroring `FileSystemLocation#filenameOf` on the server.
 *
 * @param storedValue a filename, or a whole path ending in one
 * @returns the last segment of the value
 */
function filenameOf(storedValue: string): string {
    return storedValue.substring(storedValue.lastIndexOf('/') + 1);
}
