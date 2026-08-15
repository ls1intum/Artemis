import { parseOperator } from 'app/core/navbar/global-search/models/search-operator.util';

describe('parseOperator', () => {
    it('returns undefined for plain text and empty input', () => {
        expect(parseOperator('hello')).toBeUndefined();
        expect(parseOperator('')).toBeUndefined();
    });

    it('parses a facet operator with a query', () => {
        expect(parseOperator('course:comp')).toEqual({ facet: 'course', negate: false, query: 'comp', prefix: 'course:' });
    });

    it('parses an operator with an empty query', () => {
        expect(parseOperator('type:')).toEqual({ facet: 'type', negate: false, query: '', prefix: 'type:' });
    });

    it('parses a negated (exclude) operator', () => {
        expect(parseOperator('-type:exam')).toEqual({ facet: 'type', negate: true, query: 'exam', prefix: '-type:' });
    });

    it('parses a negated course (exclude) operator', () => {
        expect(parseOperator('-course:comp')).toEqual({ facet: 'course', negate: true, query: 'comp', prefix: '-course:' });
    });

    it('is case-insensitive on the operator name', () => {
        expect(parseOperator('Course:x')?.facet).toBe('course');
    });

    it('returns undefined for an unknown operator', () => {
        expect(parseOperator('foo:bar')).toBeUndefined();
    });
});
