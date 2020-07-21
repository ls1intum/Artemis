import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { round } from 'app/shared/util/utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('Utilities', () => {
    describe('Round', () => {
        it('Decimal length', () => {
            expect(round(14.821354, 4)).to.be.equal(14.8214);
            expect(round(14.821354, 3)).to.be.equal(14.821);
            expect(round(14.821354, 2)).to.be.equal(14.82);
            expect(round(14.821354, 1)).to.be.equal(14.8);
            expect(round(14.821354, 0)).to.be.equal(15);
        });

        it('Turning points', () => {
            expect(round(2.4999999, 0)).to.be.equal(2);
            expect(round(2.5, 0)).to.be.equal(3);
            expect(round(2.55, 1)).to.be.equal(2.6);
            expect(round(2.555, 2)).to.be.equal(2.56);
            expect(round(2.5555, 3)).to.be.equal(2.556);
            expect(round(2.55555, 4)).to.be.equal(2.5556);
        });

        it('Other', () => {
            expect(round(9.99999999, 0)).to.be.equal(10);
            expect(round(9.99999999, 1)).to.be.equal(10);
            expect(round(5.55555555, 0)).to.be.equal(6);
            expect(round(5.55555555, 1)).to.be.equal(5.6);
            expect(round(1.00000001, 0)).to.be.equal(1);
            expect(round(1.00000001, 1)).to.be.equal(1.0);
        });
    });
});
