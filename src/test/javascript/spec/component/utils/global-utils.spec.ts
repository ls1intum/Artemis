import * as chai from 'chai';
import { getStringSegmentPositions } from '../../../../../main/webapp/app/utils/global.utils';

const expect = chai.expect;

describe('GlobalUtils - getStringSegmentPositions ', () => {
    it('should return correct segments of provided string and single character delimiter', () => {
        const testString = 'word1,word2,word3';
        const delimiter = ',';
        const expectedResult = [{ start: 0, end: 4, word: 'word1' }, { start: 6, end: 10, word: 'word2' }, { start: 12, end: 16, word: 'word3' }];

        const segments = getStringSegmentPositions(testString, delimiter);
        expect(segments).to.deep.equal(expectedResult);
    });

    it('should return correct segments of provided string and multiple character delimiter', () => {
        const testString = 'word1 - word2 - word3';
        const delimiter = ' - ';
        const expectedResult = [{ start: 0, end: 4, word: 'word1' }, { start: 8, end: 12, word: 'word2' }, { start: 16, end: 20, word: 'word3' }];

        const segments = getStringSegmentPositions(testString, delimiter);
        expect(segments).to.deep.equal(expectedResult);
    });

    it('should return the string as a single segment if it does not contain the delimiter', () => {
        const testString = 'word1word2word3';
        const delimiter = 'x';
        const expectedResult = [{ start: 0, end: 14, word: 'word1word2word3' }];

        const segments = getStringSegmentPositions(testString, delimiter);
        expect(segments).to.deep.equal(expectedResult);
    });

    it('should return a single segment for the empty string', () => {
        const testString = '';
        const delimiter = 'x';
        const expectedResult = [{ start: 0, end: 0, word: '' }];

        const segments = getStringSegmentPositions(testString, delimiter);
        expect(segments).to.deep.equal(expectedResult);
    });
});
