// Note: these utility functions were taken from https://github.com/nolanlawson/blob-util because it was not maintained any more since May 2018
// All functions were converted into the appropriate TypeScript syntax. Unused functions are commented out
// Removed legacy code as Artemis only supports browsers that support the intended functionality

/**
 * Shim for
 * [`new Blob()`](https://developer.mozilla.org/en-US/docs/Web/API/Blob.Blob)
 *
 * Example:
 *
 * ```js
 * var myBlob = blobUtil.createBlob(['hello world'], {type: 'text/plain'});
 * ```
 *
 * @param parts - content of the Blob
 * @param properties - usually `{type: myContentType}`, you can also pass a string for the content type
 * @returns Blob
 */
export function createBlob(parts: BlobPart[], properties?: BlobPropertyBag | string): Blob {
    parts = parts || [];
    properties = properties || {};
    if (typeof properties === 'string') {
        properties = { type: properties }; // infer content type
    }
    return new Blob(parts, properties);
}

/**
 * Convert any object into a blob of a JSON representation of the object.
 *
 * @param obj - the object to convert to a blob
 */
export function objectToJsonBlob(obj: object) {
    return createBlob([JSON.stringify(obj)], { type: 'application/json' });
}

/**
 * Convert a `Blob` to a binary string.
 *
 * Example:
 *
 * ```js
 * blobUtil.blobToBinaryString(blob).then((binaryString) => {
 *   // success
 * }).catch((err) => {
 *   // error
 * });
 * ```
 *
 * @param blob
 * @returns Promise that resolves with the binary string
 */
export function blobToBinaryString(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => {
            return resolve((reader.result as string) || '');
        };
        reader.onerror = reject;
        reader.readAsBinaryString(blob);
    });
}

/**
 * Convert a base64-encoded string to a `Blob`.
 *
 * Example:
 *
 * ```js
 * var blob = blobUtil.base64StringToBlob(base64String);
 * ```
 * @param base64 - base64-encoded string
 * @param type - the content type (optional)
 * @returns Blob
 */
export function base64StringToBlob(base64: string, type?: string): Blob {
    const parts = [binaryStringToArrayBuffer(window.atob(base64))];
    return type ? createBlob(parts, { type }) : createBlob(parts);
}

/**
 * Convert a `Blob` to a binary string.
 *
 * Example:
 *
 * ```js
 * blobUtil.blobToBase64String(blob).then((base64String) => {
 *   // success
 * }).catch((err) => {
 *   // error
 * });
 * ```
 *
 * @param blob
 * @returns Promise that resolves with the binary string
 */
export function blobToBase64String(blob: Blob): Promise<string> {
    return blobToBinaryString(blob).then((binaryString) => window.btoa(binaryString));
}

/**
 * Convert an `ArrayBuffer` to a binary string.
 *
 * Example:
 *
 * ```js
 * var myString = blobUtil.arrayBufferToBinaryString(arrayBuff)
 * ```
 *
 * @param buffer - array buffer
 * @returns binary string
 */
export function arrayBufferToBinaryString(buffer: ArrayBuffer): string {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    const length = bytes.byteLength;
    let i = -1;
    while (++i < length) {
        binary += String.fromCharCode(bytes[i]);
    }
    return binary;
}

/**
 * Convert a binary string to an `ArrayBuffer`.
 *
 * ```js
 * var myBuffer = blobUtil.binaryStringToArrayBuffer(binaryString)
 * ```
 *
 * @param binary - binary string
 * @returns array buffer
 */
export function binaryStringToArrayBuffer(binary: string): ArrayBuffer {
    const length = binary.length;
    const buf = new ArrayBuffer(length);
    const arr = new Uint8Array(buf);
    let i = -1;
    while (++i < length) {
        arr[i] = binary.charCodeAt(i);
    }
    return buf;
}
