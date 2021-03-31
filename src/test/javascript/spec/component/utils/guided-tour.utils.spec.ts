import * as chai from 'chai';
import { Orientation } from 'app/guided-tour/guided-tour.constants';
import { checkPageUrlEnding, clickOnElement, determineUrlMatching, getUrlParams } from 'app/guided-tour/guided-tour.utils';

const expect = chai.expect;

describe('GuidedTourUtils', () => {
    describe('clickOnElement', () => {
        it('should', () => {});
    });
    describe('calculateTopOffset', () => {
        it('should', () => {});
    });
    describe('calculateLeftOffset', () => {
        it('should', () => {});
    });
    describe('isElementInViewPortHorizontally', () => {
        it('should', () => {});
    });
    describe('determineUrlMatching', () => {
        it('should match with empty pageUrl', () => {
            const pageUrl = '';
            const tourStepUrl = '';
            expect(determineUrlMatching(pageUrl, tourStepUrl)).to.deep.equal(['']);
        });
        it('should match with empty pageUrl but with params', () => {
            const pageUrl = '?param=true';
            const tourStepUrl = '';
            expect(determineUrlMatching(pageUrl, tourStepUrl)).to.deep.equal(['']);
        });
        it('should match empty with pageUrl', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = '';
            expect(determineUrlMatching(pageUrl, tourStepUrl)).to.deep.equal(['']);
        });
        it('should match', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = 'is/some';
            expect(determineUrlMatching(pageUrl, tourStepUrl)).to.deep.equal(['is/some']);
        });
        it('should match more', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = 'is*';
            expect(determineUrlMatching(pageUrl, tourStepUrl)).to.deep.equal(['is']);
        });
    });
    describe('getUrlParams', () => {
        it('should get params', () => {
            expect(getUrlParams('some/url/to?withParam=true')).to.be.equal('?withParam=true');
            expect(getUrlParams('some/url/to?withParam=true&tea=yes')).to.be.equal('?withParam=true&tea=yes');
        });
        it('should get no params if not existing', () => {
            expect(getUrlParams('some/url/to')).to.be.equal('');
        });
    });
    describe('checkPageUrlEnding', () => {
        it('should checkPageUrlEnding with empty pageUrl', () => {
            const pageUrl = '';
            const matchingUrl = '';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).to.be.true;
        });
        it('should checkPageUrlEnding with empty matchingUrl', () => {
            const pageUrl = '/some/page/url?param=true';
            const matchingUrl = '';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).to.be.true;
        });
        it('should checkPageUrlEnding with identical Urls with params', () => {
            const pageUrl = '/some/page/url?param=true';
            const matchingUrl = '/some/page/url?param=true';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).to.be.false;
        });
        it('should checkPageUrlEnding with same urls without matching params', () => {
            const pageUrl = '/some/page/url?param=true';
            const matchingUrl = '/some/page/url';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).to.be.true;
        });
    });
});
