import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

/**
 * Every course tab that renders server data must refresh when the user selects it again.
 *
 * Selecting a different tab destroys and recreates the component, so it reloads anyway; selecting the tab you are
 * already on is the case that needs wiring, and it is easy to forget when a new tab is added. This asserts the wiring
 * exists rather than driving each component, which would mean standing up a dozen unrelated test beds.
 */
describe('Course tab refresh coverage', () => {
    const wiredTabs = {
        exercises: 'app/course/overview/course-exercises/course-exercises.component.ts',
        statistics: 'app/course/overview/course-statistics/course-statistics.component.ts',
        lectures: 'app/lecture/shared/course-lectures/course-lectures.component.ts',
        exams: 'app/exam/shared/course-exams/course-exams.component.ts',
        competencies: 'app/atlas/overview/course-competencies/course-competencies.component.ts',
        'learning path': 'app/atlas/overview/learning-path-student-page/learning-path-student-page.component.ts',
        communication: 'app/communication/shared/course-conversations/course-conversations.component.ts',
        faq: 'app/communication/course-faq/course-faq.component.ts',
        'tutorial groups': 'app/tutorialgroup/overview/course-tutorial-groups/course-tutorial-groups.component.ts',
        training: 'app/quiz/overview/course-training/course-training.component.ts',
        calendar: 'app/calendar/shared/calendar-overview/calendar-overview-component.directive.ts',
    };

    it.each(Object.entries(wiredTabs))('should reload the %s tab when it is selected again', (_tab, path) => {
        const source = readFileSync(`src/main/webapp/${path}`, 'utf8');

        expect(source).toContain('CourseTabRefreshService');
        expect(source).toMatch(/\.reselections\(/);
    });

    it('should leave the settings tab alone, where a refresh would fight the pending write it debounces', () => {
        const source = readFileSync('src/main/webapp/app/course/overview/course-settings/course-settings.component.ts', 'utf8');

        expect(source).not.toContain('CourseTabRefreshService');
    });

    it('should leave the Iris tab alone, whose chat is pushed over the websocket and never goes stale', () => {
        const source = readFileSync('src/main/webapp/app/iris/overview/course-iris/course-iris.component.ts', 'utf8');

        expect(source).not.toContain('CourseTabRefreshService');
    });
});
