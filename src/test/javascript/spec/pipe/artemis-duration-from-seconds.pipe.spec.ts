import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('ArtemisDurationFromSecondsPipe', () => {
    const pipe: ArtemisDurationFromSecondsPipe = new ArtemisDurationFromSecondsPipe();

    it('Should return correct format for zero seconds', () => {
        const localizedDuration = pipe.transform(0);
        expect(localizedDuration).to.be.equal('0min 0s');
    });

    it('Should return correct format for negative seconds', () => {
        const localizedDuration = pipe.transform(-42);
        expect(localizedDuration).to.be.equal('0min 0s');
    });

    describe('long format', () => {
        it('Should return correct format for only seconds', () => {
            const localizedDuration = pipe.transform(7);
            expect(localizedDuration).to.be.equal('0min 7s');
        });

        it('Should return correct format for minutes', () => {
            const localizedDuration = pipe.transform(1326);
            expect(localizedDuration).to.be.equal('22min 6s');
        });

        it('Should return correct format for hours', () => {
            const localizedDuration = pipe.transform(21922);
            expect(localizedDuration).to.be.equal('6h 5min 22s');
        });

        it('Should return correct format for days', () => {
            const localizedDuration = pipe.transform(1357800);
            expect(localizedDuration).to.be.equal('15d 17h 10min 0s');
        });

        it('Should be chosen when parameter is false', () => {
            const localizedDuration = pipe.transform(1357800, false);
            expect(localizedDuration).to.be.equal('15d 17h 10min 0s');
        });

        it('Should be chosen when parameter is undefined', () => {
            const localizedDuration = pipe.transform(1357800, undefined);
            expect(localizedDuration).to.be.equal('15d 17h 10min 0s');
        });
    });

    describe('short format', () => {
        it('Should return correct format for day', () => {
            const localizedDuration = pipe.transform(1299751, true);
            expect(localizedDuration).to.be.equal('15d 1h');
        });

        it('Should return correct format for hour', () => {
            const localizedDuration = pipe.transform(84427, true);
            expect(localizedDuration).to.be.equal('23h 27min');
        });

        it('Should return correct format for over 10 minutes', () => {
            const localizedDuration = pipe.transform(2534, true);
            expect(localizedDuration).to.be.equal('42min');
        });

        it('Should return correct format for under 10 minutes', () => {
            const localizedDuration = pipe.transform(421, true);
            expect(localizedDuration).to.be.equal('7min 1s');
        });

        it('Should return correct format for under one minute', () => {
            const localizedDuration = pipe.transform(21, true);
            expect(localizedDuration).to.be.equal('0min 21s');
        });
    });
});
