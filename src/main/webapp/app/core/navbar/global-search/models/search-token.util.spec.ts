import { addOrToggleToken, excludedCourseIds, expandTypeTokens, removeTokenAt, selectedCourseIds } from 'app/core/navbar/global-search/models/search-token.util';
import { FilterToken } from 'app/core/navbar/global-search/models/search-token.model';

describe('search token utilities', () => {
    const type = (value: string, negate = false): FilterToken => ({ facet: 'type', value, negate });
    const course = (value: string, negate = false): FilterToken => ({ facet: 'course', value, negate });

    describe('expandTypeTokens', () => {
        it('returns undefined when there are no type tokens', () => {
            expect(expandTypeTokens([])).toBeUndefined();
            expect(expandTypeTokens([course('5')])).toBeUndefined();
        });

        it('maps each start-page card value to its present-day server types', () => {
            expect(expandTypeTokens([type('course')])).toBe('course');
            expect(expandTypeTokens([type('exercise')])).toBe('exercise');
            expect(expandTypeTokens([type('lecture')])).toBe('lecture');
            expect(expandTypeTokens([type('faq')])).toBe('faq');
            expect(expandTypeTokens([type('exam')])).toBe('exam');
        });

        it('expands communication to channel, post, answer_post', () => {
            expect(expandTypeTokens([type('communication')])).toBe('channel,post,answer_post');
        });

        it('unions multiple positive type tokens as OR', () => {
            expect(expandTypeTokens([type('exercise'), type('exam')])).toBe('exercise,exam');
        });

        it('exclusion returns every server type except the excluded ones', () => {
            expect(expandTypeTokens([type('exam', true)])).toBe('exercise,lecture,lecture_unit,faq,channel,course,post,answer_post');
        });

        it('positive tokens win over exclusions', () => {
            expect(expandTypeTokens([type('exercise'), type('exam', true)])).toBe('exercise');
        });
    });

    describe('selectedCourseIds', () => {
        it('returns positive course ids and ignores negated and non-course tokens', () => {
            expect(selectedCourseIds([course('5'), course('7'), type('exercise'), course('9', true)])).toEqual([5, 7]);
        });
    });

    describe('excludedCourseIds', () => {
        it('returns negated course ids and ignores positive and non-course tokens', () => {
            expect(excludedCourseIds([course('5'), course('7', true), type('exam', true), course('9', true)])).toEqual([7, 9]);
        });
    });

    describe('addOrToggleToken', () => {
        it('appends a new token', () => {
            expect(addOrToggleToken([], type('exercise'))).toEqual([type('exercise')]);
        });

        it('toggles off an identical token', () => {
            expect(addOrToggleToken([type('exercise')], type('exercise'))).toEqual([]);
        });

        it('flips a value from include to exclude for the same facet (single-mode drops the include, adds the exclude)', () => {
            expect(addOrToggleToken([type('exam')], type('exam', true))).toEqual([type('exam', true)]);
        });

        it('adding a type exclusion drops existing type inclusions (single-mode)', () => {
            expect(addOrToggleToken([type('lecture'), type('exam')], type('faq', true))).toEqual([type('faq', true)]);
        });

        it('adding a type inclusion drops existing type exclusions (single-mode)', () => {
            expect(addOrToggleToken([type('exam', true)], type('lecture'))).toEqual([type('lecture')]);
        });

        it('switching type mode leaves course tokens untouched', () => {
            expect(addOrToggleToken([course('5'), type('exam')], type('faq', true))).toEqual([course('5'), type('faq', true)]);
        });

        it('adding a course exclusion drops existing course inclusions (single-mode)', () => {
            expect(addOrToggleToken([course('5'), course('7')], course('9', true))).toEqual([course('9', true)]);
        });
    });

    describe('removeTokenAt', () => {
        it('removes the token at the index', () => {
            expect(removeTokenAt([type('exercise'), course('5')], 0)).toEqual([course('5')]);
        });

        it('returns the same list for an out-of-range index', () => {
            const tokens = [type('exercise')];
            expect(removeTokenAt(tokens, 5)).toEqual(tokens);
        });
    });
});
