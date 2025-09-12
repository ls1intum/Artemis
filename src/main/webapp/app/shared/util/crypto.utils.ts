/**
 * Computes the SHA-1 digest of a UTF-8 string and returns a 40-char lowercase hex string.
 *
 * <p><b>Why sync?</b> The Web Crypto API (`crypto.subtle.digest`) is async which can be tricky to handle. For code paths
 * that must compute an ID synchronously (e.g., immediate UI updates), this minimal, dependency-free implementation is used.
 * Artemis only needs this in one place (TextBlock) and wants to avoid 3rd party dependencies.</p>
 *
 * <p><b>Constraints</b></p>
 * <ul>
 *   <li>Input must be a string (UTF-8 encoded internally).</li>
 *   <li>Input length is limited to 10 000 characters to avoid heavy CPU usage.</li>
 *   <li>Message length is written using the low 32 bits of the bit length, which is
 *       more than sufficient for the above limit.</li>
 * </ul>
 *
 * @param input The UTF-8 string to hash.
 * @returns The SHA-1 digest as a 40-character lowercase hexadecimal string.
 * @throws {TypeError}  If the input is not a string.
 * @throws {RangeError} If the input length exceeds 10 000 characters.
 */
export function sha1Hex(input: string): string {
    // ---------- input validation ----------
    if (typeof input !== 'string') {
        throw new TypeError('sha1Hex: input must be a string');
    }
    const MAX_CHARS = 10_000;
    if (input.length > MAX_CHARS) {
        throw new RangeError(`sha1Hex: input must not exceed ${MAX_CHARS} characters`);
    }

    // ---------- helpers ----------
    const rotateLeft = (value32: number, shift: number) => (value32 << shift) | (value32 >>> (32 - shift));

    const toHex32 = (value32: number) => (value32 >>> 0).toString(16).padStart(8, '0');

    // ---------- preprocess (padding + length) ----------
    const inputBytes = new TextEncoder().encode(input);
    const bitLength = inputBytes.length * 8;

    // Pad to (length ≡ 56 mod 64), then append 8 bytes of length (we set low 32 bits).
    const BLOCK_BYTES = 64;
    const MIN_PADDING_BYTES = 9; // 0x80 + 8-length-bytes
    const totalPaddedBytes = ((inputBytes.length + MIN_PADDING_BYTES + (BLOCK_BYTES - 1)) >> 6) << 6; // multiple of 64

    const paddedBytes = new Uint8Array(totalPaddedBytes);
    paddedBytes.set(inputBytes);
    paddedBytes[inputBytes.length] = 0x80;

    const dataView = new DataView(paddedBytes.buffer);
    // Write only the lower 32 bits of the bit length at the end (big-endian).
    dataView.setUint32(totalPaddedBytes - 4, bitLength);

    // ---------- initial hash state (H0..H4) ----------
    let hash0 = 0x67452301;
    let hash1 = 0xefcdab89;
    let hash2 = 0x98badcfe;
    let hash3 = 0x10325476;
    let hash4 = 0xc3d2e1f0;

    // Message schedule (80 32-bit words)
    const scheduleWords = new Int32Array(80);

    // ---------- process each 512-bit chunk ----------
    for (let chunkOffset = 0; chunkOffset < totalPaddedBytes; chunkOffset += BLOCK_BYTES) {
        // scheduleWords[0..15] from chunk (big-endian)
        for (let wordIndex = 0; wordIndex < 16; wordIndex++) {
            scheduleWords[wordIndex] = dataView.getInt32(chunkOffset + wordIndex * 4);
        }
        // scheduleWords[16..79] expansion
        for (let wordIndex = 16; wordIndex < 80; wordIndex++) {
            scheduleWords[wordIndex] = rotateLeft(scheduleWords[wordIndex - 3] ^ scheduleWords[wordIndex - 8] ^ scheduleWords[wordIndex - 14] ^ scheduleWords[wordIndex - 16], 1);
        }

        // Working variables
        let workA = hash0;
        let workB = hash1;
        let workC = hash2;
        let workD = hash3;
        let workE = hash4;

        for (let t = 0; t < 80; t++) {
            const phase = (t / 20) | 0;

            // Round function f_t
            const roundFunction =
                phase === 0 ? (workB & workC) | (~workB & workD) : phase === 1 || phase === 3 ? workB ^ workC ^ workD : (workB & workC) | (workB & workD) | (workC & workD);

            // Round constant K_t
            const roundConstant = phase === 0 ? 0x5a827999 : phase === 1 ? 0x6ed9eba1 : phase === 2 ? 0x8f1bbcdc : 0xca62c1d6;

            const temp = (rotateLeft(workA, 5) + roundFunction + workE + roundConstant + scheduleWords[t]) | 0;

            workE = workD;
            workD = workC;
            workC = rotateLeft(workB, 30);
            workB = workA;
            workA = temp;
        }

        // Add this chunk's hash to result (mod 2^32)
        hash0 = (hash0 + workA) | 0;
        hash1 = (hash1 + workB) | 0;
        hash2 = (hash2 + workC) | 0;
        hash3 = (hash3 + workD) | 0;
        hash4 = (hash4 + workE) | 0;
    }

    // ---------- produce 40-char hex digest ----------
    return toHex32(hash0) + toHex32(hash1) + toHex32(hash2) + toHex32(hash3) + toHex32(hash4);
}
