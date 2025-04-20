/**
 * Decode a Base64URL‐encoded payload into raw bytes.
 *
 * Base64URL is a URL‐safe variant of Base64:
 *  - '-' replaces '+'
 *  - '_' replaces '/'
 *  - padding ('=') may be omitted
 *
 * @param input - Base64URL data as a string or any BufferSource.
 * @returns An ArrayBuffer containing the decoded bytes.
 * @throws TypeError if `input` is neither string nor BufferSource.
 */
export function decodeBase64url(input: string | BufferSource): ArrayBuffer {
    // 1) Turn BufferSource into string
    let b64url: string;
    if (typeof input === 'string') {
        b64url = input;
    } else if (ArrayBuffer.isView(input) || input instanceof ArrayBuffer) {
        b64url = new TextDecoder().decode(input as ArrayBuffer);
    } else {
        throw new TypeError(`Expected string or BufferSource, but got ${typeof input}`);
    }

    // 2) Normalize to standard Base64
    //    - swap URL chars back
    //    - pad with '=' to a multiple of 4
    const b64 = b64url
        .replace(/-/g, '+')
        .replace(/_/g, '/')
        .padEnd(Math.ceil(b64url.length / 4) * 4, '=');

    // 3) Decode to binary string
    const binary = window.atob(b64);

    // 4) Build ArrayBuffer from char codes
    const len = binary.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
        bytes[i] = binary.charCodeAt(i);
    }

    return bytes.buffer;
}
