import { decodeBase64url } from 'app/shared/util/base64.util';

describe('decodeBase64url', () => {
    const textDecoder = new TextDecoder();

    it('decodes standard base64url without padding', () => {
        const input = 'SGVsbG8td29ybGQ'; // "Hello-world"
        const output = decodeBase64url(input);
        expect(textDecoder.decode(output)).toBe('Hello-world');
    });

    it('decodes base64url with missing padding', () => {
        const input = 'Zm9vYmFy'; // "foobar"
        const output = decodeBase64url(input);
        expect(textDecoder.decode(output)).toBe('foobar');
    });

    it('decodes padded base64url strings', () => {
        expect(textDecoder.decode(decodeBase64url('TQ=='))).toBe('M');
        expect(textDecoder.decode(decodeBase64url('TWE='))).toBe('Ma');
    });

    it('decodes URL-safe characters', () => {
        const input = 'YWJjMTIzLV8'; // "abc123-_"
        const output = decodeBase64url(input);
        expect(textDecoder.decode(output)).toBe('abc123-_');
    });

    it('decodes ArrayBuffer input', () => {
        const encoder = new TextEncoder();
        const arr = encoder.encode('U29tZS1kYXRh'); // "Some-data"
        const output = decodeBase64url(arr);
        expect(textDecoder.decode(output)).toBe('Some-data');
    });

    it('decodes Uint8Array view input', () => {
        const encoder = new TextEncoder();
        const uint8 = encoder.encode('U29tZS1kYXRh'); // "Some-data"
        const view = new Uint8Array(uint8.buffer);
        const output = decodeBase64url(view);
        expect(textDecoder.decode(output)).toBe('Some-data');
    });

    it('returns correct ArrayBuffer length', () => {
        const input = 'U3BlY2lhbC1sZW5ndGg='; // "Special-length"
        const output = decodeBase64url(input);
        expect(output.byteLength).toBe('Special-length'.length);
    });

    it('handles empty string input', () => {
        const output = decodeBase64url('');
        expect(output.byteLength).toBe(0);
    });

    it('decodes minimal input without padding', () => {
        // "M" => "TQ"
        const output = decodeBase64url('TQ');
        expect(textDecoder.decode(output)).toBe('M');
    });

    it('throws on non-string, non-BufferSource inputs', () => {
        // @ts-ignore
        expect(() => decodeBase64url(123)).toThrow(TypeError);
        // @ts-ignore
        expect(() => decodeBase64url({})).toThrow(TypeError);
        // @ts-ignore
        expect(() => decodeBase64url(null)).toThrow(TypeError);
    });

    it('throws on invalid base64url strings', () => {
        const invalidInputs = ['!!!!', 'abcd*efg'];
        invalidInputs.forEach((input) => {
            expect(() => decodeBase64url(input)).toThrow();
        });
    });

    it('decodes DataView input', () => {
        const encoder = new TextEncoder();
        const bytes = encoder.encode('U2FtcGxlLWRhdGE'); // "Sample-data"
        const dataView = new DataView(bytes.buffer);
        const output = decodeBase64url(dataView);
        expect(new TextDecoder().decode(output)).toBe('Sample-data');
    });

    it('throws on inputs with length mod 4 == 1', () => {
        // Base64URL lengths of 1 are invalid
        expect(() => decodeBase64url('A')).toThrow();
        expect(() => decodeBase64url('ABCD1')).toThrow();
    });

    it('throws on improper padding positions', () => {
        const invalid = ['SG=V', 'SGVsbG8=V29ybGQ='];
        invalid.forEach((input) => {
            expect(() => decodeBase64url(input)).toThrow();
        });
    });
});
