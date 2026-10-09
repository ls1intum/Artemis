import { describe, expect, it } from 'vitest';
import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { chatAnswer, handoffTarget } from 'app/core/navbar/global-search/util/iris-chat-handoff.util';
import { CitedSources } from 'app/core/navbar/global-search/util/iris-cited-sources.util';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';

const algorithms = { id: 1, name: 'Introduction to Algorithms' };
const dataStructures = { id: 2, name: 'Data Structures' };

function slide(course: { id: number; name: string }, lecture: { id: number; name: string }, unitId: number, page: number, unitName = 'Slides'): LectureSearchResult {
    return {
        course,
        lecture,
        lectureUnit: {
            id: unitId,
            name: unitName,
            link: `/courses/${course.id}/lectures/${lecture.id}`,
            pageNumber: page,
            sourceType: 'lecture_unit_slide',
            queryParams: { unit: unitId, page },
        },
    };
}

function video(course: { id: number; name: string }, lecture: { id: number; name: string }, unitId: number, timestamp: number): LectureSearchResult {
    return {
        course,
        lecture,
        lectureUnit: {
            id: unitId,
            name: 'Recording',
            link: `/courses/${course.id}/lectures/${lecture.id}`,
            pageNumber: -1,
            sourceType: 'lecture_transcription',
            queryParams: { unit: unitId, timestamp },
        },
    };
}

function cited(sources: LectureSearchResult[], entitySources: EntitySearchSource[] = [], citationSourceTypes?: ('lecture' | 'entity')[]): CitedSources {
    return { sources, entitySources, citationSourceTypes };
}

const divideAndConquer = { id: 10, name: 'Divide and Conquer' };
const recurrences = { id: 11, name: 'Recurrences' };

describe('handoffTarget', () => {
    it('goes to the only cited lecture', () => {
        const target = handoffTarget(cited([slide(algorithms, divideAndConquer, 100, 3), slide(algorithms, divideAndConquer, 101, 7)]));

        expect(target).toEqual({ courseId: 1, name: 'Divide and Conquer', context: { mode: ChatServiceMode.LECTURE, entityId: 10 } });
    });

    it('goes to the most cited lecture', () => {
        const target = handoffTarget(cited([slide(algorithms, recurrences, 100, 1), slide(algorithms, divideAndConquer, 101, 2), slide(algorithms, divideAndConquer, 102, 3)]));

        expect(target?.name).toBe('Divide and Conquer');
    });

    it('goes to the first cited lecture when several are cited equally often', () => {
        const target = handoffTarget(cited([slide(algorithms, recurrences, 100, 1), slide(algorithms, divideAndConquer, 101, 2)]));

        expect(target?.name).toBe('Recurrences');
    });

    it('goes to the most cited course when the answer spans several courses', () => {
        const exercise: EntitySearchSource = { entityType: 'exercise', entityId: 50, course: dataStructures, title: 'Mergesort', exerciseType: 'programming' };
        const target = handoffTarget(cited([slide(algorithms, divideAndConquer, 100, 3), slide(algorithms, divideAndConquer, 101, 4)], [exercise]));

        expect(target?.courseId).toBe(1);
    });

    it('goes to an exercise with an Iris chat', () => {
        const exercise: EntitySearchSource = { entityType: 'exercise', entityId: 50, course: dataStructures, title: 'Mergesort', exerciseType: 'programming' };

        expect(handoffTarget(cited([], [exercise]))).toEqual({ courseId: 2, name: 'Mergesort', context: { mode: ChatServiceMode.PROGRAMMING_EXERCISE, entityId: 50 } });
    });

    it('goes to the course itself for exercises without an Iris chat and other course information', () => {
        const modeling: EntitySearchSource = { entityType: 'exercise', entityId: 51, course: algorithms, title: 'UML', exerciseType: 'modeling' };
        const faq: EntitySearchSource = { entityType: 'faq', entityId: 52, course: algorithms, title: 'Exam date' };

        expect(handoffTarget(cited([], [modeling, faq]))).toEqual({ courseId: 1, name: 'Introduction to Algorithms' });
    });

    it('recognises text exercises and lecture entries as places', () => {
        const textExercise: EntitySearchSource = { entityType: 'exercise', entityId: 53, course: algorithms, title: 'Essay', exerciseType: 'TEXT' };
        const lectureEntry: EntitySearchSource = { entityType: 'lecture', entityId: 10, course: algorithms, title: 'Divide and Conquer' };

        expect(handoffTarget(cited([], [textExercise]))?.context).toEqual({ mode: ChatServiceMode.TEXT_EXERCISE, entityId: 53 });
        expect(handoffTarget(cited([], [lectureEntry]))?.context).toEqual({ mode: ChatServiceMode.LECTURE, entityId: 10 });
    });

    it('has nowhere to go when no source names a course', () => {
        expect(handoffTarget(cited([], [{ entityType: 'faq', title: 'Without a course' }]))).toBeUndefined();
    });
});

describe('chatAnswer', () => {
    it('turns slide and video sources into chat citations that open the same page and position', () => {
        const answer = chatAnswer(
            'Halves the input.[1] Merges every level.[2]',
            cited([slide(algorithms, divideAndConquer, 100, 3, 'Week 4: [Sorting]'), video(algorithms, divideAndConquer, 101, 221)]),
        );

        expect(answer).toBe('Halves the input.[cite:L:100:3:::Week 4 Sorting:] Merges every level.[cite:L:101::221::Recording:]');
    });

    it('turns course information into links to the pages their chips open', () => {
        const exercise: EntitySearchSource = { entityType: 'exercise', entityId: 50, course: algorithms, title: 'Mergesort [v2]', link: '/courses/1/exercises/50' };
        const unlinked: EntitySearchSource = { entityType: 'faq', entityId: 52, course: algorithms, title: 'Exam date' };

        const answer = chatAnswer('Practice it.[1] See the FAQ.[2]', cited([], [exercise, unlinked]));

        expect(answer).toBe('Practice it. [Mergesort \\[v2\\]](</courses/1/exercises/50>) See the FAQ. (Exam date)');
    });

    it('follows the interleaved numbering of lecture and entity sources', () => {
        const exercise: EntitySearchSource = { entityType: 'exercise', entityId: 50, course: algorithms, title: 'Mergesort', link: '/courses/1/exercises/50' };

        const answer = chatAnswer('A.[1] B.[2]', cited([slide(algorithms, divideAndConquer, 100, 3)], [exercise], ['entity', 'lecture']));

        expect(answer).toBe('A. [Mergesort](</courses/1/exercises/50>) B.[cite:L:100:3:::Slides:]');
    });
});
