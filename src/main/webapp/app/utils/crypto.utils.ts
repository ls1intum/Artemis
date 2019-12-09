import { createHash } from 'crypto';

/**
 * @param string
 */
export function sha1Hex(string: string): string {
    return createHash('sha1')
        .update(string)
        .digest('hex');
}
