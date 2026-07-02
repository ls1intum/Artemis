import { parseJson } from 'app/foundation/util/json.util';

describe('parseJson', () => {
    it('parses a JSON object', () => {
        expect(parseJson<{ a: number; b: string }>('{"a":1,"b":"x"}')).toEqual({ a: 1, b: 'x' });
    });

    it('parses a JSON array', () => {
        expect(parseJson<number[]>('[1,2,3]')).toEqual([1, 2, 3]);
    });

    it('parses primitives', () => {
        expect(parseJson<string>('"hello"')).toBe('hello');
        expect(parseJson<number>('42')).toBe(42);
        expect(parseJson<boolean>('true')).toBe(true);
        expect(parseJson<undefined>('null')).toBeNull();
    });

    it('throws on invalid JSON, exactly like JSON.parse', () => {
        expect(() => parseJson('{ not valid')).toThrow(SyntaxError);
    });
});
