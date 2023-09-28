import { SHA1, enc } from 'crypto-js';

/**
 * Generates and returns the hash digest using 'hex' algorithm.
 * @param value The string to which the algorithm will be applied upon.
 */
export function sha1Hex(value: string): string {
    const hash = SHA1(value);
    return enc.Hex.stringify(hash);
}

export async function sha1HexFromFile(file: File): Promise<string> {
    const reader = new FileReader();

    return new Promise<string>((resolve, reject) => {
        reader.addEventListener('loadend', (event) => {
            if (!event.target || !event.target.result) {
                reject('');
            } else {
                const hash = SHA1(enc.Latin1.parse(event.target.result.toString()));
                const hashString = enc.Hex.stringify(hash);
                resolve(hashString);
            }
        });

        reader.onerror = reject;

        reader.readAsBinaryString(file);
    });
}
