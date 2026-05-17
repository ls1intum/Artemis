import { BytesPipe } from './bytes.pipe';

describe('BytesPipe', () => {
    let pipe: BytesPipe;

    beforeEach(() => {
        pipe = new BytesPipe();
    });

    it('renders a dash placeholder for null/undefined/NaN', () => {
        expect(pipe.transform(null)).toBe('—');
        expect(pipe.transform(undefined)).toBe('—');
        expect(pipe.transform(Number.NaN)).toBe('—');
    });

    it('renders zero without unit clutter', () => {
        expect(pipe.transform(0)).toBe('0 B');
    });

    it('renders bytes with no fractional part for values below 1 KiB', () => {
        expect(pipe.transform(1)).toBe('1 B');
        expect(pipe.transform(512)).toBe('512 B');
        expect(pipe.transform(1023)).toBe('1023 B');
    });

    it('renders kibibytes when in the KiB range', () => {
        expect(pipe.transform(1024)).toBe('1.0 KB');
        expect(pipe.transform(1536)).toBe('1.5 KB');
    });

    it('renders mebibytes for MB-sized values', () => {
        expect(pipe.transform(1024 * 1024)).toBe('1.0 MB');
        expect(pipe.transform(1024 * 1024 * 5)).toBe('5.0 MB');
    });

    it('renders gibibytes for the Spring DataSize "3GB" default', () => {
        // 3 GiB — matches the Java-side DataSize.parse("3GB") used for the Maven cache cap.
        expect(pipe.transform(3 * 1024 * 1024 * 1024)).toBe('3.0 GB');
    });

    it('renders tebibytes for very large values', () => {
        expect(pipe.transform(2 * 1024 * 1024 * 1024 * 1024)).toBe('2.0 TB');
    });

    it('honors the optional fractionDigits parameter', () => {
        expect(pipe.transform(1024 * 1024, 2)).toBe('1.00 MB');
        expect(pipe.transform(1024 * 1024, 0)).toBe('1 MB');
    });

    it('handles negative values', () => {
        expect(pipe.transform(-1024)).toBe('-1.0 KB');
    });
});
