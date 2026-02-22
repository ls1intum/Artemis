import { describe, expect, it } from 'vitest';
import { damerauLevenshtein, stemWord, titleSimilarity, tokenizeAndStem } from './title-similarity.utils';

describe('stemWord', () => {
    it('should return short words unchanged', () => {
        expect(stemWord('a')).toBe('a');
        expect(stemWord('ab')).toBe('ab');
        expect(stemWord('abc')).toBe('abc');
    });

    it('should strip common suffixes', () => {
        expect(stemWord('algorithms')).toBe('algorithm');
        expect(stemWord('sorting')).toBe('sort');
        expect(stemWord('implementation')).toBe('implement');
        expect(stemWord('implementable')).toBe('implement');
        expect(stemWord('completeness')).toBe('complet');
    });

    it('should not strip suffix if remaining stem is too short', () => {
        expect(stemWord('sing')).toBe('sing');
        expect(stemWord('used')).toBe('used');
    });

    it('should strip longest matching suffix first', () => {
        expect(stemWord('organization')).toBe('organ');
        expect(stemWord('normalisation')).toBe('normal');
    });
});

describe('tokenizeAndStem', () => {
    it('should tokenize, lowercase, remove stop words, stem, and sort', () => {
        const result = tokenizeAndStem('The Basics of Sorting Algorithms');
        expect(result).toEqual(['algorithm', 'basic', 'sort']);
    });

    it('should handle empty string', () => {
        expect(tokenizeAndStem('')).toEqual([]);
    });

    it('should handle string with only stop words', () => {
        expect(tokenizeAndStem('a the and or')).toEqual([]);
    });

    it('should handle mixed-case input', () => {
        const result = tokenizeAndStem('DATA Structures And ALGORITHMS');
        expect(result).toEqual(['algorithm', 'data', 'structur']);
    });

    it('should handle multiple spaces', () => {
        const result = tokenizeAndStem('  hello   world  ');
        expect(result).toEqual(['hello', 'world']);
    });
});

describe('damerauLevenshtein', () => {
    it('should return 0 for identical strings', () => {
        expect(damerauLevenshtein('hello', 'hello')).toBe(0);
    });

    it('should return length of other string when one is empty', () => {
        expect(damerauLevenshtein('', 'abc')).toBe(3);
        expect(damerauLevenshtein('abc', '')).toBe(3);
    });

    it('should count single substitution', () => {
        expect(damerauLevenshtein('cat', 'car')).toBe(1);
    });

    it('should count single insertion', () => {
        expect(damerauLevenshtein('cat', 'cats')).toBe(1);
    });

    it('should count single deletion', () => {
        expect(damerauLevenshtein('cats', 'cat')).toBe(1);
    });

    it('should count transposition of adjacent characters', () => {
        expect(damerauLevenshtein('ab', 'ba')).toBe(1);
    });

    it('should handle completely different strings', () => {
        expect(damerauLevenshtein('abc', 'xyz')).toBe(3);
    });
});

describe('titleSimilarity', () => {
    it('should return 0 for empty strings', () => {
        expect(titleSimilarity('', 'hello')).toBe(0);
        expect(titleSimilarity('hello', '')).toBe(0);
    });

    it('should return 1.0 for identical strings', () => {
        expect(titleSimilarity('Sorting Algorithms', 'Sorting Algorithms')).toBe(1.0);
    });

    it('should return 0.85 when one contains the other', () => {
        expect(titleSimilarity('sorting', 'sorting algorithms')).toBe(0.85);
    });

    it('should return 1.0 for strings that are identical after stemming', () => {
        expect(titleSimilarity('sorting algorithms', 'sorting algorithm')).toBe(1.0);
    });

    it('should return high similarity for reordered words', () => {
        const score = titleSimilarity('data structures', 'structures data');
        expect(score).toBe(1.0); // sorted tokens are identical
    });

    it('should return low similarity for unrelated strings', () => {
        const score = titleSimilarity('machine learning', 'database design');
        expect(score).toBeLessThan(0.5);
    });

    it('should handle stop-word-only differences', () => {
        const score = titleSimilarity('basics of sorting', 'basics sorting');
        expect(score).toBe(1.0); // "of" is a stop word
    });

    it('should handle case differences', () => {
        const score = titleSimilarity('DATA STRUCTURES', 'data structures');
        expect(score).toBe(1.0);
    });
});
