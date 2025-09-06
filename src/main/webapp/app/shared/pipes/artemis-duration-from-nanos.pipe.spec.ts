import { ArtemisDurationFromNanosPipe } from 'app/shared/pipes/artemis-duration-from-nanos.pipe';

describe('ArtemisDurationFromNanosPipe', () => {
    const pipe: ArtemisDurationFromNanosPipe = new ArtemisDurationFromNanosPipe();

    it('should show zero on zero', () => {
        const transformed = pipe.transform(0);
        expect(transformed).toBe('0ns');
    });

    it('should show nanos only', () => {
        const transformed = pipe.transform(42);
        expect(transformed).toBe('42ns');
    });

    it('should show micros and nanos', () => {
        const transformed = pipe.transform(42_123);
        expect(transformed).toBe('42μs 123ns');
    });

    it('should show millis and micros', () => {
        const transformed = pipe.transform(42_123_456);
        expect(transformed).toBe('42ms 123μs');
    });

    it('should show seconds and millis', () => {
        const transformed = pipe.transform(20_000_999_000);
        expect(transformed).toBe('20s 0ms');
    });

    it('should show minutes and seconds', () => {
        const transformed = pipe.transform(800_000_000_000);
        expect(transformed).toBe('13min 20s');
    });

    it('should show hours and minutes', () => {
        const transformed = pipe.transform(30_100_000_000_000);
        expect(transformed).toBe('8h 21min');
    });

    it('should show days and hours', () => {
        const transformed = pipe.transform(500_100_000_000_000);
        expect(transformed).toBe('5d 18h');
    });

    it.each([-123, 0, 1, 123, 100_000_000, 999_999_999_999, 400_000_000_000_000_000])('should treat negative inputs like positives', (nanos) => {
        const transformedPositive = pipe.transform(nanos);
        const transformedNegative = pipe.transform(-nanos);
        expect(transformedPositive).toBe(transformedNegative);
    });
});
