import { appendOperator, parseOperator, searchTextOf, stripOperator } from 'app/core/navbar/global-search/models/search-operator.util';

describe('parseOperator', () => {
    it('returns undefined for plain text and empty input', () => {
        expect(parseOperator('hello')).toBeUndefined();
        expect(parseOperator('')).toBeUndefined();
    });

    it('parses a facet operator with a query', () => {
        expect(parseOperator('course:comp')).toEqual({ facet: 'course', negate: false, query: 'comp', prefix: 'course:', start: 0, text: '' });
    });

    it('parses an operator with an empty query', () => {
        expect(parseOperator('type:')).toEqual({ facet: 'type', negate: false, query: '', prefix: 'type:', start: 0, text: '' });
    });

    it('parses a negated (exclude) operator', () => {
        expect(parseOperator('-type:exam')).toEqual({ facet: 'type', negate: true, query: 'exam', prefix: '-type:', start: 0, text: '' });
    });

    it('parses a negated course (exclude) operator', () => {
        expect(parseOperator('-course:comp')).toEqual({ facet: 'course', negate: true, query: 'comp', prefix: '-course:', start: 0, text: '' });
    });

    it('is case-insensitive on the operator name', () => {
        expect(parseOperator('Course:x')?.facet).toBe('course');
    });

    it('returns undefined for an unknown operator', () => {
        expect(parseOperator('foo:bar')).toBeUndefined();
    });

    it('keeps the search text in front of the operator, so composing a filter costs nothing', () => {
        expect(parseOperator('linear regression course:deep')).toEqual({
            facet: 'course',
            negate: false,
            query: 'deep',
            prefix: 'course:',
            start: 18,
            text: 'linear regression',
        });
    });

    it('runs the value to the end of the input, so course titles keep their spaces', () => {
        expect(parseOperator('linear regression course:deep learning')?.query).toBe('deep learning');
    });

    it('takes the last operator when several are present', () => {
        const operator = parseOperator('course:1 type:lecture');
        expect(operator?.facet).toBe('type');
        expect(operator?.text).toBe('course:1');
    });

    it('ignores an unknown word ending in a colon and keeps the known operator before it', () => {
        expect(parseOperator('type:lecture foo:')?.query).toBe('lecture foo:');
    });

    it('needs a word boundary, so a colon inside a word is plain text', () => {
        expect(parseOperator('mistype:lecture')).toBeUndefined();
    });
});

describe('searchTextOf', () => {
    it('returns the whole input when there is no operator', () => {
        expect(searchTextOf('  linear regression  ')).toBe('linear regression');
    });

    it('returns only the text in front of the operator', () => {
        expect(searchTextOf('linear regression type:lec')).toBe('linear regression');
    });
});

describe('stripOperator', () => {
    it('removes the operator and the space that introduced it', () => {
        expect(stripOperator('linear regression type:lec')).toBe('linear regression');
    });

    it('leaves input without an operator untouched', () => {
        expect(stripOperator('linear regression')).toBe('linear regression');
    });

    it('empties an input that is nothing but an operator', () => {
        expect(stripOperator('type:lec')).toBe('');
    });
});

describe('appendOperator', () => {
    it('appends to existing text with a single separating space', () => {
        expect(appendOperator('linear regression ', 'type:')).toBe('linear regression type:');
    });

    it('produces just the prefix for empty input', () => {
        expect(appendOperator('', 'type:')).toBe('type:');
    });
});
