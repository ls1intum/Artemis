/**
 * The one place on the client that builds the path a drag item picture is served under.
 *
 * Every other served file reference reaches the client as a ready-made path: the server assembles it from a hardcoded template plus the owning entity (see `PublicFileUrl` and
 * `ServedFileUrl` on the server) and the client only prepends `api/core/files/`. A drag item is the exception, because it is not an entity of its own. It lives inside its
 * question's JSON content, its id is only unique within that question, and it holds no reference back to it, so the server cannot assemble the URL from the item alone and sends
 * the bare filename instead.
 *
 * The template below therefore mirrors `PublicFileUrl.DragItem` on the server and the two have to be renamed together. It exists once so that the mirror is a single place rather
 * than a comment in each component that happens to need it.
 */

/**
 * Builds the question-scoped path a drag item picture is served under.
 *
 * The result is relative to `api/core/files/`, which is the same shape the server sends for every other file reference (`PublicFileUrl#clientPath`), so a caller passes it
 * through `addPublicFilePrefix` exactly as it would a value it received.
 *
 * @param questionId the id of the owning drag and drop question
 * @param dragItemId the question-scoped id of the drag item
 * @param storedValue the drag item's `pictureFilePath`, which is a filename but may still be a whole path on an item saved before the server stopped storing one
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
