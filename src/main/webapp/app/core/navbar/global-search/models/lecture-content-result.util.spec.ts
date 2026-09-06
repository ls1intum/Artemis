import { describe, expect, it } from 'vitest';
import { LECTURE_CONTENT_TYPE, mapLectureContentResult } from 'app/core/navbar/global-search/models/lecture-content-result.util';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

describe('mapLectureContentResult', () => {
    it('maps a slide hit (pageNumber 4) to id/type/title/description and all metadata keys', () => {
        const result: LectureSearchResult = {
            course: { id: 10, name: 'Advanced Web Development' },
            lecture: { id: 20, name: 'Angular Basics' },
            lectureUnit: {
                id: 30,
                name: 'Introduction to Signals',
                link: '/courses/10/lectures/20/units/30',
                pageNumber: 4,
                sourceType: 'lecture_unit_slide',
                queryParams: { unit: 30, page: 4 },
                displayMeta: 'Slide 4',
            },
            snippet: 'Signals are a reactive primitive...',
        };

        const mapped = mapLectureContentResult(result);

        expect(mapped.id).toBe('lecture-content-/courses/10/lectures/20/units/30?page=4&unit=30');
        expect(mapped.type).toBe(LECTURE_CONTENT_TYPE);
        expect(mapped.title).toBe('Introduction to Signals');
        expect(mapped.description).toBe('Signals are a reactive primitive...');
        expect(mapped.badge).toBeUndefined();
        expect(mapped.metadata).toEqual({
            courseId: '10',
            courseName: 'Advanced Web Development',
            lectureId: '20',
            lectureName: 'Angular Basics',
            pageNumber: 4,
            sourceType: 'lecture_unit_slide',
            link: '/courses/10/lectures/20/units/30',
            queryParams: { unit: 30, page: 4 },
            displayMeta: 'Slide 4',
        });
    });

    it('preserves pageNumber -1 and the displayMeta timestamp for a video hit', () => {
        const result: LectureSearchResult = {
            course: { id: 11, name: 'Databases' },
            lecture: { id: 21, name: 'Indexing' },
            lectureUnit: {
                id: 31,
                name: 'B-Trees Explained',
                link: '/courses/11/lectures/21/units/31',
                pageNumber: -1,
                sourceType: 'lecture_transcription',
                queryParams: { unit: 31, timestamp: 221 },
                displayMeta: '3:41',
            },
            snippet: 'A B-tree is a self-balancing search tree...',
        };

        const mapped = mapLectureContentResult(result);

        // The id keys on the deep-link destination (path + query params carrying the timestamp).
        expect(mapped.id).toBe('lecture-content-/courses/11/lectures/21/units/31?timestamp=221&unit=31');
        expect(mapped.metadata?.['pageNumber']).toBe(-1);
        expect(mapped.metadata?.['displayMeta']).toBe('3:41');
    });

    it('gives two video hits from the same unit distinct ids based on their location query params', () => {
        const base = {
            course: { id: 11, name: 'Databases' },
            lecture: { id: 21, name: 'Indexing' },
        };
        const makeVideoHit = (queryParams: Record<string, string | number>, displayMeta: string): LectureSearchResult => ({
            ...base,
            lectureUnit: {
                id: 31,
                name: 'B-Trees Explained',
                link: '/courses/11/lectures/21/units/31',
                pageNumber: -1,
                sourceType: 'lecture_transcription',
                queryParams,
                displayMeta,
            },
        });

        const firstTimestamp = mapLectureContentResult(makeVideoHit({ unit: 31, timestamp: 0 }, '0:00'));
        const secondTimestamp = mapLectureContentResult(makeVideoHit({ unit: 31, timestamp: 571 }, '9:31'));

        expect(firstTimestamp.id).toBe('lecture-content-/courses/11/lectures/21/units/31?timestamp=0&unit=31');
        expect(secondTimestamp.id).toBe('lecture-content-/courses/11/lectures/21/units/31?timestamp=571&unit=31');
        expect(firstTimestamp.id).not.toBe(secondTimestamp.id);
    });

    it('leaves description undefined when the snippet is missing, without crashing', () => {
        const result: LectureSearchResult = {
            course: { id: 1, name: 'Course' },
            lecture: { id: 2, name: 'Lecture' },
            lectureUnit: {
                id: 3,
                name: 'Unit',
                link: '/courses/1/lectures/2/units/3',
                pageNumber: 0,
                sourceType: 'lecture_unit_slide',
                queryParams: {},
            },
        };

        const mapped = mapLectureContentResult(result);

        expect(mapped.description).toBeUndefined();
        expect(mapped.metadata?.['displayMeta']).toBeUndefined();
    });
});
