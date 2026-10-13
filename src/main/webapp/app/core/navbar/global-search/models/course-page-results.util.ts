import { GlobalSearchResult } from 'app/openapi/model/global-search-result';

/** Internal type string for a course page offered by the palette itself; never sent to or returned by the server. */
export const COURSE_PAGE_TYPE = 'course_page';

interface CoursePage {
    /** Route segment below `/courses/:courseId`. */
    segment: string;
    /** i18n key of the page's name in the course navigation. */
    labelKey: string;
    /** Lower-case words a user may type for the page, in English and German, besides its translated name. */
    aliases: string[];
}

/**
 * The course pages every course has, whatever its settings. Pages that a course can switch off (communication, Iris,
 * competencies, tutorial groups, FAQ) are left out, so a match never leads to a page the course does not offer.
 */
const COURSE_PAGES: CoursePage[] = [
    { segment: 'exercises', labelKey: 'artemisApp.courseOverview.menu.exercises', aliases: ['exercises', 'tasks', 'übungen', 'aufgaben', 'hausaufgaben'] },
    { segment: 'lectures', labelKey: 'artemisApp.courseOverview.menu.lectures', aliases: ['lectures', 'vorlesungen'] },
    {
        segment: 'statistics',
        labelKey: 'artemisApp.courseOverview.menu.statistics',
        aliases: ['statistics', 'grades', 'scores', 'points', 'progress', 'statistik', 'noten', 'punkte', 'fortschritt'],
    },
    { segment: 'calendar', labelKey: 'artemisApp.courseOverview.menu.calendar', aliases: ['calendar', 'schedule', 'dates', 'kalender', 'termine'] },
];

/** Shortest query that is matched against page names; shorter prefixes would match almost every page. */
const MIN_PAGE_QUERY_LENGTH = 3;

/**
 * Returns the pages of one course whose name or one of whose aliases starts with the query, as results that open the page.
 *
 * @param query      the typed search text
 * @param courseId   the course the search is scoped to
 * @param courseName the course's title, shown on the result
 * @param translate  resolves an i18n key to the page name in the user's language
 * @return the matching pages, in the order of the course navigation
 */
export function matchCoursePages(query: string, courseId: number, courseName: string | undefined, translate: (key: string) => string): GlobalSearchResult[] {
    const normalized = query.trim().toLowerCase();
    if (normalized.length < MIN_PAGE_QUERY_LENGTH) {
        return [];
    }
    return COURSE_PAGES.filter((page) => [translate(page.labelKey).toLowerCase(), ...page.aliases].some((name) => name.startsWith(normalized))).map((page) => ({
        id: `course-page-${courseId}-${page.segment}`,
        type: COURSE_PAGE_TYPE,
        title: translate(page.labelKey),
        badgeKey: 'page',
        metadata: { courseId: String(courseId), courseName, link: `/courses/${courseId}/${page.segment}` },
    }));
}
