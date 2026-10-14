import { describe, expect, it } from 'vitest';
import { COURSE_PAGE_TYPE, matchCoursePages } from './course-page-results.util';

describe('matchCoursePages', () => {
    const translate = (key: string) => ({ 'artemisApp.courseOverview.menu.statistics': 'Statistics', 'artemisApp.courseOverview.menu.exercises': 'Exercises' })[key] ?? key;

    it('finds the statistics page by a word for grades in English or German', () => {
        for (const query of ['grades', 'Noten', 'stat']) {
            const pages = matchCoursePages(query, 42, 'Algorithms', translate);
            expect(pages).toHaveLength(1);
            expect(pages[0]).toMatchObject({ type: COURSE_PAGE_TYPE, title: 'Statistics', metadata: { courseId: '42', courseName: 'Algorithms', link: '/courses/42/statistics' } });
        }
    });

    it('offers a guarded page only when the course has it', () => {
        expect(matchCoursePages('vorlesungen', 42, 'Algorithms', translate)).toEqual([]);
        expect(matchCoursePages('vorlesungen', 42, 'Algorithms', translate, (segment) => segment === 'lectures')[0]).toMatchObject({ metadata: { link: '/courses/42/lectures' } });
        expect(matchCoursePages('faq', 42, 'Algorithms', translate, (segment) => segment === 'lectures')).toEqual([]);
    });

    it('matches nothing for a short or unrelated query', () => {
        expect(matchCoursePages('ex', 42, 'Algorithms', translate)).toEqual([]);
        expect(matchCoursePages('dijkstra', 42, 'Algorithms', translate)).toEqual([]);
    });
});
