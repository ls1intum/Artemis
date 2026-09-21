import {
    addOrToggleToken,
    dropIncompatibleTokens,
    excludedCourseIds,
    excludedTypeTokens,
    expandTypeTokens,
    hasContentTypeToken,
    removeTokenAt,
    selectedCourseIds,
} from 'app/core/navbar/global-search/models/search-token.util';
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
            expect(expandTypeTokens([type('lecture')])).toBe('lecture,lecture_unit');
            expect(expandTypeTokens([type('faq')])).toBe('faq');
            expect(expandTypeTokens([type('exam')])).toBe('exam');
        });

        it('sends no server type for slides and videos, which is not a searchable entity', () => {
            expect(expandTypeTokens([type('lecture_content')])).toBeUndefined();
        });

        it('expands communication to channel, post, answer_post', () => {
            expect(expandTypeTokens([type('communication')])).toBe('channel,post,answer_post');
        });

        it('unions multiple positive type tokens as OR', () => {
            expect(expandTypeTokens([type('exercise'), type('exam')])).toBe('exercise,exam');
        });

        it('leaves the types unset when only exclusions are active', () => {
            // Exclusions are no longer folded in as a complement: "everything except exams" and "only exercises"
            // would otherwise reach the server as the same list, and it cannot tell those apart.
            expect(expandTypeTokens([type('exam', true)])).toBeUndefined();
        });

        it('ignores exclusions when a positive type is selected', () => {
            expect(expandTypeTokens([type('exercise'), type('exam', true)])).toBe('exercise');
        });
    });

    describe('excludedTypeTokens', () => {
        it('returns nothing when no type is excluded', () => {
            expect(excludedTypeTokens([type('exercise'), course('5')])).toBeUndefined();
        });

        it('expands an excluded facet to its server types', () => {
            expect(excludedTypeTokens([type('lecture', true)])).toBe('lecture,lecture_unit');
        });

        it('unions several exclusions', () => {
            expect(excludedTypeTokens([type('exam', true), type('faq', true)])).toBe('exam,faq');
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

        it('slides and videos replaces the entity type filters, which answer from a different collection', () => {
            expect(addOrToggleToken([type('lecture'), type('exam')], type('lecture_content'))).toEqual([type('lecture_content')]);
        });

        it('an entity type filter replaces slides and videos, for the same reason in the other direction', () => {
            expect(addOrToggleToken([type('lecture_content')], type('exercise'))).toEqual([type('exercise')]);
        });

        it('leaves the course scope alone when the collections swap', () => {
            expect(addOrToggleToken([course('5'), type('lecture')], type('lecture_content'))).toEqual([course('5'), type('lecture_content')]);
        });
    });

    describe('dropIncompatibleTokens', () => {
        it('keeps a token that is already in the list, dropping only what it rules out', () => {
            const replacement = type('lecture_content');
            expect(dropIncompatibleTokens([replacement, type('exam')], replacement)).toEqual([replacement]);
        });

        it('drops a content token when a metadata type replaces it', () => {
            const replacement = type('exam');
            expect(dropIncompatibleTokens([replacement, type('lecture_content')], replacement)).toEqual([replacement]);
        });

        it('leaves course tokens and same-collection type tokens alone', () => {
            const replacement = type('exam');
            expect(dropIncompatibleTokens([course('5'), replacement, type('faq')], replacement)).toEqual([course('5'), replacement, type('faq')]);
        });
    });

    describe('hasContentTypeToken', () => {
        it('is true only while slides and videos is positively selected', () => {
            expect(hasContentTypeToken([type('lecture_content')])).toBe(true);
            expect(hasContentTypeToken([type('lecture')])).toBe(false);
            expect(hasContentTypeToken([])).toBe(false);
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
