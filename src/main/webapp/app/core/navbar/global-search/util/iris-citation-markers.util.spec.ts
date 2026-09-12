import { describe, expect, it } from 'vitest';
import { parseCitationNumbers, renderCitationMarkers } from './iris-citation-markers.util';

describe('renderCitationMarkers', () => {
    it('converts a single marker into a citation chip element', () => {
        const result = renderCitationMarkers('The quiz is worth 4 points.[2]', 3);
        expect(result.html).toBe('The quiz is worth 4 points.<sup class="iris-cite" data-n="2">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('groups a run of consecutive markers into one chip', () => {
        const result = renderCitationMarkers('Composition beats inheritance.[1][3]', 3);
        expect(result.html).toBe('Composition beats inheritance.<sup class="iris-cite" data-n="1 3">1,3</sup>');
        expect([...result.citedNumbers]).toEqual([1, 3]);
    });

    it('deduplicates repeated numbers inside a run', () => {
        const result = renderCitationMarkers('Claim.[1][1]', 3);
        expect(result.html).toBe('Claim.<sup class="iris-cite" data-n="1">1</sup>');
    });

    it('drops out-of-range markers and removes a run left empty', () => {
        const result = renderCitationMarkers('Wrong.[9] Right.[2]', 3);
        expect(result.html).toBe('Wrong. Right.<sup class="iris-cite" data-n="2">2</sup>');
        expect([...result.citedNumbers]).toEqual([2]);
    });

    it('keeps separate runs as separate chips', () => {
        const result = renderCitationMarkers('A.[1] B.[1]', 3);
        expect(result.html).toBe('A.<sup class="iris-cite" data-n="1">1</sup> B.<sup class="iris-cite" data-n="1">1</sup>');
    });

    it('passes a markerless answer through untouched', () => {
        const result = renderCitationMarkers('Plain answer with [brackets] but no numbers.', 3);
        expect(result.html).toBe('Plain answer with [brackets] but no numbers.');
        expect(result.citedNumbers.size).toBe(0);
    });

    it('passes undefined through untouched', () => {
        const result = renderCitationMarkers(undefined, 3);
        expect(result.html).toBeUndefined();
        expect(result.citedNumbers.size).toBe(0);
    });

    it('strips nothing when there are no sources to index into', () => {
        const result = renderCitationMarkers('Claim.[1]', 0);
        expect(result.html).toBe('Claim.[1]');
        expect(result.citedNumbers.size).toBe(0);
    });
});

describe('parseCitationNumbers', () => {
    it('parses the space-separated data attribute', () => {
        expect(parseCitationNumbers('1 3')).toEqual([1, 3]);
    });

    it('ignores garbage values', () => {
        expect(parseCitationNumbers('1 x 0 -2')).toEqual([1]);
    });

    it('returns empty for a missing attribute', () => {
        expect(parseCitationNumbers(undefined)).toEqual([]);
    });
});
