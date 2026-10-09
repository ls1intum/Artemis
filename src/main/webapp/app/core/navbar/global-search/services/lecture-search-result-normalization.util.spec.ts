import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { normalizeLectureSearchResultQueryParams } from './lecture-search-result-normalization.util';

describe('normalizeLectureSearchResultQueryParams', () => {
    const result = (): LectureSearchResult => ({
        course: { id: 1, name: 'Course' },
        lecture: { id: 2, name: 'Lecture' },
        lectureUnit: {
            id: 7,
            name: 'Unit',
            link: '/courses/1/lectures/2',
            pageNumber: 4,
            sourceType: 'lecture_unit_slide',
            queryParams: { unit: '7', timestamp: ' ', page: '4', unrelated: 'kept' },
        },
        snippet: 'Content',
    });

    it('normalizes media positions without mutating the received result', () => {
        const source = result();
        const normalized = normalizeLectureSearchResultQueryParams(source);

        expect(normalized.lectureUnit.queryParams).toEqual({ unit: 7, page: 4, unrelated: 'kept' });
        expect(source.lectureUnit.queryParams).toEqual({ unit: '7', timestamp: ' ', page: '4', unrelated: 'kept' });
        expect(normalized).not.toBe(source);
        expect(normalized.lectureUnit).not.toBe(source.lectureUnit);
        expect(normalized.snippet).toBe(source.snippet);
    });

    it('accepts results whose empty query parameters were omitted from the server response', () => {
        const source = result();
        Reflect.deleteProperty(source.lectureUnit, 'queryParams');

        const normalized = normalizeLectureSearchResultQueryParams(source);

        expect(normalized.lectureUnit.queryParams).toEqual({});
        expect(normalized.lectureUnit.link).toBe(source.lectureUnit.link);
        expect(source.lectureUnit).not.toHaveProperty('queryParams');
    });
});
