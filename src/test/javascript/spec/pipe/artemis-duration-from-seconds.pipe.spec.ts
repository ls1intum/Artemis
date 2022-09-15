import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

describe('ArtemisDurationFromSecondsPipe', () => {
    const pipe: ArtemisDurationFromSecondsPipe = new ArtemisDurationFromSecondsPipe();

    it('should return correct format for zero seconds', () => {
        const localizedDuration = pipe.transform(0);
        expect(localizedDuration).toBe('0min 0s');
    });

    it('should return correct format for negative seconds', () => {
        const localizedDuration = pipe.transform(-42);
        expect(localizedDuration).toBe('0min 0s');
    });

    describe('long format', () => {
        it('should return correct format for only seconds', () => {
            const localizedDuration = pipe.transform(7);
            expect(localizedDuration).toBe('0min 7s');
        });

        it('should return correct format for minutes', () => {
            const localizedDuration = pipe.transform(1326);
            expect(localizedDuration).toBe('22min 6s');
        });

        it('should return correct format for hours', () => {
            const localizedDuration = pipe.transform(21922);
            expect(localizedDuration).toBe('6h 5min 22s');
        });

        it('should return correct format for days', () => {
            const localizedDuration = pipe.transform(1357800);
            expect(localizedDuration).toBe('15d 17h 10min 0s');
        });

        it('should be chosen when parameter is false', () => {
            const localizedDuration = pipe.transform(1357800, false);
            expect(localizedDuration).toBe('15d 17h 10min 0s');
        });

        it('should be chosen when parameter is undefined', () => {
            const localizedDuration = pipe.transform(1357800, undefined);
            expect(localizedDuration).toBe('15d 17h 10min 0s');
        });
    });

    describe('short format', () => {
        it('should return correct format for day', () => {
            const localizedDuration = pipe.transform(1299751, true);
            expect(localizedDuration).toBe('15d 1h');
        });

        it('should return correct format for hour', () => {
            const localizedDuration = pipe.transform(84427, true);
            expect(localizedDuration).toBe('23h 27min');
        });

        it('should return correct format for over 10 minutes', () => {
            const localizedDuration = pipe.transform(2534, true);
            expect(localizedDuration).toBe('42min');
        });

        it('should return correct format for under 10 minutes', () => {
            const localizedDuration = pipe.transform(421, true);
            expect(localizedDuration).toBe('7min 1s');
        });

        it('should return correct format for under one minute', () => {
            const localizedDuration = pipe.transform(21, true);
            expect(localizedDuration).toBe('0min 21s');
        });
    });
});
