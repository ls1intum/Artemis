import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { round, stringifyIgnoringFields } from 'app/shared/util/utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('Utilities', () => {
    void it('Decimal length', () => {
        expect(round(14.821354, 4)).to.be.equal(14.8214);
        expect(round(14.821354, 3)).to.be.equal(14.821);
        expect(round(14.821354, 2)).to.be.equal(14.82);
        expect(round(14.821354, 1)).to.be.equal(14.8);
        expect(round(14.821354, 0)).to.be.equal(15);
    });

    void it('Turning points', () => {
        expect(round(2.4999999, 0)).to.be.equal(2);
        expect(round(2.5, 0)).to.be.equal(3);
        expect(round(2.55, 1)).to.be.equal(2.6);
        expect(round(2.555, 2)).to.be.equal(2.56);
        expect(round(2.5555, 3)).to.be.equal(2.556);
        expect(round(2.55555, 4)).to.be.equal(2.5556);
    });

    void it('Other', () => {
        expect(round(9.99999999, 0)).to.be.equal(10);
        expect(round(9.99999999, 1)).to.be.equal(10);
        expect(round(5.55555555, 0)).to.be.equal(6);
        expect(round(5.55555555, 1)).to.be.equal(5.6);
        expect(round(1.00000001, 0)).to.be.equal(1);
        expect(round(1.00000001, 1)).to.be.equal(1.0);
    });

    void it('should return NaN', () => {
        expect(round(Number.NaN, 2)).to.be.NaN;
        expect(round(Number.NaN, 1)).to.be.NaN;
        expect(round(9.9999, 0.5)).to.NaN;
    });
});

describe('stringifyIgnoringFields', () => {
    it('should ignore nothing', () => {
        expect(stringifyIgnoringFields({})).to.be.equal(JSON.stringify({}));
        expect(stringifyIgnoringFields({}, 'a', 'b')).to.be.equal(JSON.stringify({}));
        expect(stringifyIgnoringFields({ a: 'a' }, 'b')).to.be.equal(JSON.stringify({ a: 'a' }));
        expect(stringifyIgnoringFields({ a: 'a' })).to.be.equal(JSON.stringify({ a: 'a' }));
    });

    it('should ignore fields', () => {
        expect(stringifyIgnoringFields({ a: 1 }, 'a')).to.be.equal(JSON.stringify({}));
        expect(stringifyIgnoringFields({ a: 1, c: 2, b: 3 }, 'a', 'b')).to.be.equal(JSON.stringify({ c: 2 }));
        expect(stringifyIgnoringFields({ b: 1, c: 3 }, 'c', 'b')).to.be.equal(JSON.stringify({}));
    });
});
