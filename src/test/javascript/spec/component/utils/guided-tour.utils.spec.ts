import * as chai from 'chai';
import { Orientation } from 'app/guided-tour/guided-tour.constants';
import {
    clickOnElement,
    calculateTopOffset,
    calculateLeftOffset,
    isElementInViewPortHorizontally,
    checkPageUrlEnding,
    determineUrlMatching,
    getUrlParams,
} from 'app/guided-tour/guided-tour.utils';
import * as sinon from 'sinon';
import { stub } from 'sinon';

const expect = chai.expect;

describe('GuidedTourUtils', () => {
    describe('clickOnElement', () => {
        it('should clickOnElement', () => {
            let querySelectorStub = stub(document, 'querySelector');
            let mockElement = { click: () => {} } as HTMLElement;
            querySelectorStub.returns(mockElement);
            clickOnElement('ab');
            sinon.assert.called(querySelectorStub);
        });
    });
    describe('calculateLeftOffset', () => {
        it('should calculateLeftOffset', () => {
            let offsetParent1 = { offsetLeft: 4 } as any;
            let offsetParent2 = { offsetLeft: 2, offsetParent: offsetParent1 } as any;
            let dummyElement2 = { offsetLeft: 4, offsetParent: offsetParent2 } as HTMLElement;
            expect(calculateLeftOffset(dummyElement2)).to.be.equal(10);
        });
    });
    describe('calculateTopOffset', () => {
        it('should calculateTopOffset', () => {
            let offsetParent1 = { offsetTop: 4 } as any;
            let offsetParent2 = { offsetTop: 2, offsetParent: offsetParent1 } as any;
            let dummyElement2 = { offsetTop: 4, offsetParent: offsetParent2 } as HTMLElement;
            expect(calculateTopOffset(dummyElement2)).to.be.equal(10);
        });
    });
    describe('isElementInViewPortHorizontally', () => {
        // window.width is 1024
        it('should isElementInViewPortHorizontally', () => {
            let topleft = Orientation.TOPLEFT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).to.be.true;
            expect(isElementInViewPortHorizontally(topleft, 1000, 1000, 1000)).to.be.false;
        });
        it('should isElementInViewPortHorizontally', () => {
            let topleft = Orientation.BOTTOMLEFT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).to.be.true;
            expect(isElementInViewPortHorizontally(topleft, 1000, 1000, 1000)).to.be.false;
        });
        it('should isElementInViewPortHorizontally', () => {
            let topleft = Orientation.LEFT;
            expect(isElementInViewPortHorizontally(topleft, 100, 0, 50)).to.be.true;
            expect(isElementInViewPortHorizontally(topleft, 100, 0, 150)).to.be.false;
        });
        it('should isElementInViewPortHorizontally', () => {
            let topleft = Orientation.TOPRIGHT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).to.be.true;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 300)).to.be.false;
        });
        it('should isElementInViewPortHorizontally', () => {
            let topleft = Orientation.BOTTOMRIGHT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).to.be.true;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 300)).to.be.false;
        });
        it('should isElementInViewPortHorizontally', () => {
            let topleft = Orientation.RIGHT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).to.be.true;
            expect(isElementInViewPortHorizontally(topleft, 1000, 1000, 1000)).to.be.false;
        });
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
            const tourStepUrl = 'some';
            expect(determineUrlMatching(pageUrl, tourStepUrl)).to.deep.equal(['some']);
        });
        it('should match with params in tourStepUrl', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = 'some?param=false';
            expect(determineUrlMatching(pageUrl, tourStepUrl)).to.deep.equal(['some']);
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
