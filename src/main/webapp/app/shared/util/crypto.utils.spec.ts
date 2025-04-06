import { sha1Hex } from 'app/shared/util/crypto.utils';

describe('CryptoUtils', () => {
    describe('sha1Hex', () => {
        it('should compute Hash for "foo"', () => {
            const input = 'foo';
            const computedHash = sha1Hex(input);
            const correctHash = '0beec7b5ea3f0fdbc95d0dd47f3c5bc275da8a33';

            expect(computedHash).toBe(correctHash);
        });

        it('should compute Hash for "bar"', () => {
            const input = 'bar';
            const computedHash = sha1Hex(input);
            const correctHash = '62cdb7020ff920e5aa642c3d4066950dd1f01f4d';

            expect(computedHash).toBe(correctHash);
        });
    });
});
