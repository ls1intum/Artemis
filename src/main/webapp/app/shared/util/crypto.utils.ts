import { SHA1, enc } from 'crypto-js';

/**
 * Generates and returns the hash digest using 'hex' algorithm.
 * @param value The string to which the algorithm will be applied upon.
 */
export function sha1Hex(value: string): string {
    const hash = SHA1(value);
    return enc.Hex.stringify(hash);
}
