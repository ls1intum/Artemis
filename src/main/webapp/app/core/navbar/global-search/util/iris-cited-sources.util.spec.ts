import { describe, expect, it } from 'vitest';
import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { CitedSources, citedEntitySource, citedLectureSource, markerNumbersForType, resolveCitation } from 'app/core/navbar/global-search/util/iris-cited-sources.util';

const lecture = (id: number) => ({ lectureUnit: { id } }) as LectureSearchResult;
const entity = (title: string) => ({ entityType: 'faq', title }) as EntitySearchSource;

describe('iris cited sources', () => {
    const interleaved: CitedSources = { sources: [lecture(1), lecture(2)], entitySources: [entity('a')], citationSourceTypes: ['lecture', 'entity', 'lecture'] };
    const blockOrder: CitedSources = { sources: [lecture(1), lecture(2)], entitySources: [entity('a')] };

    it('numbers each type by its position in the interleaved citation order', () => {
        expect(markerNumbersForType(interleaved, 'lecture')).toEqual([1, 3]);
        expect(markerNumbersForType(interleaved, 'entity')).toEqual([2]);
    });

    it('falls back to lecture markers first, then entity markers, for an older Iris', () => {
        expect(markerNumbersForType(blockOrder, 'lecture')).toEqual([1, 2]);
        expect(markerNumbersForType(blockOrder, 'entity')).toEqual([3]);
    });

    it('resolves a marker to its source in both numberings', () => {
        expect(resolveCitation(interleaved, 3)).toEqual({ type: 'lecture', index: 1 });
        expect(citedLectureSource(interleaved, 3)?.lectureUnit.id).toBe(2);
        expect(citedEntitySource(interleaved, 2)?.title).toBe('a');
        expect(citedEntitySource(blockOrder, 3)?.title).toBe('a');
        expect(citedLectureSource(blockOrder, 3)).toBeUndefined();
    });

    it('resolves nothing for a marker beyond the citation order', () => {
        expect(resolveCitation(interleaved, 4)).toBeUndefined();
    });
});
