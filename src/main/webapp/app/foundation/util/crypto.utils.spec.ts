import { generateUuid, sha1Hex } from 'app/foundation/util/crypto.utils';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('CryptoUtils', () => {
    describe('generateUuid', () => {
        const realCrypto = window.crypto;

        afterEach(() => {
            vi.stubGlobal('crypto', realCrypto);
            vi.restoreAllMocks();
        });

        it('should use crypto.randomUUID when available', () => {
            const spy = vi.spyOn(window.crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111');
            expect(generateUuid()).toBe('11111111-1111-4111-8111-111111111111');
            expect(spy).toHaveBeenCalledOnce();
        });

        it('should return a valid v4 UUID when crypto.randomUUID is absent (insecure context, getRandomValues present)', () => {
            // Reproduces the bootstrap failure over plain-HTTP origins: randomUUID is undefined, getRandomValues remains.
            vi.stubGlobal('crypto', { getRandomValues: (array: Uint8Array) => realCrypto.getRandomValues(array) });
            expect(generateUuid()).toMatch(UUID_V4);
        });

        it('should fall back to Math.random when no Web Crypto RNG exists at all', () => {
            vi.stubGlobal('crypto', undefined);
            expect(generateUuid()).toMatch(UUID_V4);
        });
    });

    describe('sha1Hex', () => {
        it('should compute Hash for "foo"', () => {
            expect(sha1Hex('foo')).toBe('0beec7b5ea3f0fdbc95d0dd47f3c5bc275da8a33');
        });

        it('should compute Hash for "bar"', () => {
            expect(sha1Hex('bar')).toBe('62cdb7020ff920e5aa642c3d4066950dd1f01f4d');
        });

        // --- edge cases and validation ---

        it('should return correct hash for empty string', () => {
            expect(sha1Hex('')).toBe('da39a3ee5e6b4b0d3255bfef95601890afd80709');
        });

        it('should compute different hashes for similar inputs', () => {
            const hash1 = sha1Hex('test');
            const hash2 = sha1Hex('Test'); // capital T
            expect(hash1).not.toBe(hash2);
        });

        it('should throw TypeError if input is not a string', () => {
            // @ts-expect-error testing runtime behavior
            expect(() => sha1Hex(123)).toThrow(TypeError);
        });

        it('should throw RangeError if input exceeds 10000 characters', () => {
            const longString = 'a'.repeat(10001);
            expect(() => sha1Hex(longString)).toThrow(RangeError);
        });

        it('should handle exactly 10000 characters', () => {
            const maxString = 'a'.repeat(10000);
            const hash = sha1Hex(maxString);
            expect(typeof hash).toBe('string');
            expect(hash).toHaveLength(40);
        });
    });
});
