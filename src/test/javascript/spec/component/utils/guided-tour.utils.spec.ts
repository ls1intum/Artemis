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

describe('GuidedTourUtils', () => {
    describe('clickOnElement', () => {
        it('should clickOnElement', () => {
            const querySelectorStub = jest.spyOn(document, 'querySelector');
            const mockElement = { click: () => {} } as HTMLElement;
            querySelectorStub.mockReturnValue(mockElement);
            clickOnElement('ab');
            expect(querySelectorStub).toHaveBeenCalled();
        });
    });
    describe('calculateLeftOffset', () => {
        it('should calculateLeftOffset', () => {
            const offsetParent1 = { offsetLeft: 4 } as any;
            const offsetParent2 = { offsetLeft: 2, offsetParent: offsetParent1 } as any;
            const dummyElement2 = { offsetLeft: 4, offsetParent: offsetParent2 } as HTMLElement;
            expect(calculateLeftOffset(dummyElement2)).toEqual(10);
        });
    });
    describe('calculateTopOffset', () => {
        it('should calculateTopOffset', () => {
            const offsetParent1 = { offsetTop: 4 } as any;
            const offsetParent2 = { offsetTop: 2, offsetParent: offsetParent1 } as any;
            const dummyElement2 = { offsetTop: 4, offsetParent: offsetParent2 } as HTMLElement;
            expect(calculateTopOffset(dummyElement2)).toEqual(10);
        });
    });
    describe('isElementInViewPortHorizontally', () => {
        // window.width is 1024
        it('should isElementInViewPortHorizontally', () => {
            const topleft = Orientation.TOPLEFT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).toBeTrue();
            expect(isElementInViewPortHorizontally(topleft, 1000, 1000, 1000)).toBeFalse();
        });
        it('should isElementInViewPortHorizontally for bottom left', () => {
            const topleft = Orientation.BOTTOMLEFT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).toBeTrue();
            expect(isElementInViewPortHorizontally(topleft, 1000, 1000, 1000)).toBeFalse();
        });
        it('should isElementInViewPortHorizontally for left', () => {
            const topleft = Orientation.LEFT;
            expect(isElementInViewPortHorizontally(topleft, 100, 0, 50)).toBeTrue();
            expect(isElementInViewPortHorizontally(topleft, 100, 0, 150)).toBeFalse();
        });
        it('should isElementInViewPortHorizontally for top right', () => {
            const topleft = Orientation.TOPRIGHT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).toBeTrue();
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 300)).toBeFalse();
        });
        it('should isElementInViewPortHorizontally for bottom right', () => {
            const topleft = Orientation.BOTTOMRIGHT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).toBeTrue();
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 300)).toBeFalse();
        });
        it('should isElementInViewPortHorizontally for right', () => {
            const topleft = Orientation.RIGHT;
            expect(isElementInViewPortHorizontally(topleft, 100, 100, 100)).toBeTrue();
            expect(isElementInViewPortHorizontally(topleft, 1000, 1000, 1000)).toBeFalse();
        });
    });
    describe('determineUrlMatching', () => {
        it('should match with empty pageUrl', () => {
            const pageUrl = '';
            const tourStepUrl = '';
            expect(determineUrlMatching(pageUrl, tourStepUrl)!.toString()).toBe('');
        });
        it('should match with empty pageUrl but with params', () => {
            const pageUrl = '?param=true';
            const tourStepUrl = '';
            expect(determineUrlMatching(pageUrl, tourStepUrl)!.toString()).toBe('');
        });
        it('should match empty with pageUrl', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = '';
            expect(determineUrlMatching(pageUrl, tourStepUrl)!.toString()).toBe('');
        });
        it('should match', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = 'is/some';
            expect(determineUrlMatching(pageUrl, tourStepUrl)!.toString()).toBe('is/some');
        });
        it('should match more', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = 'some';
            expect(determineUrlMatching(pageUrl, tourStepUrl)!.toString()).toBe('some');
        });
        it('should match with params in tourStepUrl', () => {
            const pageUrl = '/this/is/some/guided/tour/url?param=true';
            const tourStepUrl = 'some?param=false';
            expect(determineUrlMatching(pageUrl, tourStepUrl)!.toString()).toBe('some');
        });
    });
    describe('getUrlParams', () => {
        it('should get params', () => {
            expect(getUrlParams('some/url/to?withParam=true')).toBe('?withParam=true');
            expect(getUrlParams('some/url/to?withParam=true&tea=yes')).toBe('?withParam=true&tea=yes');
        });
        it('should get no params if not existing', () => {
            expect(getUrlParams('some/url/to')).toBe('');
        });
    });
    describe('checkPageUrlEnding', () => {
        it('should checkPageUrlEnding with empty pageUrl', () => {
            const pageUrl = '';
            const matchingUrl = '';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).toBeTrue();
        });
        it('should checkPageUrlEnding with empty matchingUrl', () => {
            const pageUrl = '/some/page/url?param=true';
            const matchingUrl = '';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).toBeTrue();
        });
        it('should checkPageUrlEnding with identical Urls with params', () => {
            const pageUrl = '/some/page/url?param=true';
            const matchingUrl = '/some/page/url?param=true';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).toBeFalse();
        });
        it('should checkPageUrlEnding with same urls without matching params', () => {
            const pageUrl = '/some/page/url?param=true';
            const matchingUrl = '/some/page/url';
            expect(checkPageUrlEnding(pageUrl, matchingUrl)).toBeTrue();
        });
    });
});
