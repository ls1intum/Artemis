import { createHash } from 'crypto';

/**
 * Generates and returns the hash digest using 'hex' algorithm.
 * @param string The string to which the algorithm will be applied upon.
 */
export function sha1Hex(string: string): string {
    return createHash('sha1').update(string).digest('hex');
}
